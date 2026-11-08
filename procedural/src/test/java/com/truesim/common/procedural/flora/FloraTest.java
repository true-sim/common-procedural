package com.truesim.common.procedural.flora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Meshes;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.procedural.flora.FlowerShape.Form;
import com.truesim.common.procedural.flora.FlowerShape.Tip;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phenology, palettes and the shapes of flowers and fruits. */
class FloraTest {

    private static final Phenology ROWAN = new Phenology(14, 145, 175, 10, 215, 240, 18, 275, 400);

    @Test
    void aFlowerGoesFromBudToFlowerToFruitToNothing() {
        FloralState s = new FloralState();
        // opens on day 145 + 0.5 * 20 = 155, lasts 10 days
        assertThat(ROWAN.state(130, 0.5, 0.5, 0.5, true, s).stage()).isEqualTo(FloralState.Stage.NONE);
        ROWAN.state(148, 0.5, 0.5, 0.5, true, s);
        assertThat(s.stage()).isEqualTo(FloralState.Stage.BUD);
        assertThat(s.open()).isZero();
        assertThat(s.size()).isBetween(0.5, 1.0);
        assertThat(s.age()).isLessThan(0.5);
        ROWAN.state(158, 0.5, 0.5, 0.5, true, s);
        assertThat(s.stage()).isEqualTo(FloralState.Stage.FLOWER);
        assertThat(s.open()).isEqualTo(1);
        assertThat(s.age()).isBetween(0.5, 1.0);
        ROWAN.state(170, 0.5, 0.5, 0.5, true, s);
        assertThat(s.stage()).isEqualTo(FloralState.Stage.FRUIT);
        assertThat(s.ripeness()).isZero();
        assertThat(s.size()).isLessThan(1);
        // ripening from day 227.5 over 18 days
        assertThat(ROWAN.state(236, 0.5, 0.5, 0.5, true, s).ripeness()).isBetween(0.3, 0.7);
        assertThat(ROWAN.state(250, 0.5, 0.5, 0.5, true, s).ripeness()).isEqualTo(1);
        assertThat(s.size()).isEqualTo(1);
        // hanging into the next year until its day (337.5 + 365 wraps to day 337.5... here 275 + 0.5 * 125)
        assertThat(ROWAN.state(330, 0.5, 0.5, 0.5, true, s).stage()).isEqualTo(FloralState.Stage.FRUIT);
        assertThat(ROWAN.state(345, 0.5, 0.5, 0.5, true, s).stage()).isEqualTo(FloralState.Stage.NONE);
        // an early dropper's fruit still hangs in January when its day is late
        assertThat(ROWAN.state(20, 0.5, 0.5, 0.95, true, s).stage()).isEqualTo(FloralState.Stage.FRUIT);
        assertThat(ROWAN.state(20, 0.5, 0.5, 0.1, true, s).stage()).isEqualTo(FloralState.Stage.NONE);
        // a flower setting no fruit is gone after flowering
        assertThat(ROWAN.state(170, 0.5, 0.5, 0.5, false, s).stage()).isEqualTo(FloralState.Stage.NONE);
        assertThat(s.shown()).isFalse();
        assertThat(ROWAN.peakBloom()).isEqualTo(160);
        assertThat(ROWAN.ripe()).isEqualTo(249);
        assertThat(Phenology.smooth(0, 1, 0.5)).isEqualTo(0.5);
        assertThatThrownBy(() -> new Phenology(1, 200, 100, 1, 300, 300, 1, 300, 300))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void palettesEncodeTheirRampsInTexelsAndCoordinates() {
        Palette palette = Palette.DEFAULT.with(Part.FRUIT, new Ramp(new Albedo(0.1, 0.2, 0.05),
            new Albedo(0.5, 0.2, 0.02), new Albedo(0.5, 0.05, 0.02)));
        assertThat(palette.albedo(Part.FRUIT, 1).g()).isCloseTo(0.05, within(1e-12));
        assertThat(palette.albedo(Part.FRUIT, 0.25).r()).isCloseTo(0.3, within(1e-12));
        for (Part part : Part.values()) {
            assertThat(Palette.part(Palette.v(part))).isEqualTo(part);
        }
        for (double t = 0; t <= 1; t += 0.125) {
            assertThat(Palette.t(Palette.u(t))).isCloseTo(t, within(1e-12));
        }
        byte[] texels = palette.texels();
        assertThat(texels).hasSize(4 * palette.width() * palette.height());
        // the fruit row's last texel: the ripe color, sRGB-encoded
        int row = Part.FRUIT.ordinal() * Palette.ROW;
        int o = 4 * (row * palette.width() + palette.width() - 1);
        assertThat(texels[o] & 255).isEqualTo(Albedo.encode(0.5));
        assertThat(texels[o + 3] & 255).isEqualTo(255);
        assertThat(Albedo.decode(Albedo.encode(0.21))).isCloseTo(0.21, within(0.005));
        assertThat(Albedo.srgb(0xffffff)).isEqualTo(new Albedo(1, 1, 1));
        assertThat(new Albedo(0.5, 0.5, 0.5).toSrgb()).isEqualTo(Albedo.encode(0.5) * 0x010101);
        assertThat(Ramp.of(new Albedo(0.2, 0.2, 0.2)).at(0.7)).isEqualTo(new Albedo(0.2, 0.2, 0.2));
        assertThatThrownBy(() -> new Albedo(1.2, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Palette(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void presetAlbedosArePhysical() {
        for (var species : com.truesim.common.procedural.tree.TreeSpecies.PRESETS) {
            if (species.fruiting() != null) {
                assertPhysical(species.fruiting().palette());
            }
        }
        for (var species : com.truesim.common.procedural.plant.PlantSpecies.PRESETS) {
            assertPhysical(species.palette());
        }
    }

    private static void assertPhysical(Palette palette) {
        for (Part part : Part.values()) {
            for (double t = 0; t <= 1; t += 0.25) {
                Albedo a = palette.albedo(part, t);
                // natural surfaces: darker than fresh snow, not black
                assertThat(Math.max(a.r(), Math.max(a.g(), a.b()))).isBetween(0.02, 0.85);
            }
        }
    }

    @Test
    void outlinesStayWithinTheirWidth() {
        for (Outline outline : Outline.values()) {
            for (double s = 0; s <= 1; s += 0.05) {
                assertThat(outline.width(s)).isBetween(0.0, 1.0 + 1e-9);
            }
            assertThat(Math.abs(outline.tip())).isLessThan(0.2);
        }
        for (Tip tip : Tip.values()) {
            assertThat(Outline.of(tip).name()).isEqualTo(tip.name());
        }
    }

    @Test
    void fruitsAreClosedSurfacesAtEveryDetail() {
        MeshWriter writer = new MeshWriter();
        FloraWriter flora = new FloraWriter().target(writer);
        for (FruitShape.Form form : FruitShape.Form.values()) {
            for (int detail = 0; detail < 3; detail++) {
                for (double radius : new double[] {0.003, 0.005, 0.01, 0.036}) {
                    writer.reset();
                    FruitShape shape = new FruitShape(form, radius, 1.2, 0.1, 0, form == FruitShape.Form.CONE ? 9 : 0,
                        form == FruitShape.Form.POME ? 0.2 : 0, 0.5);
                    flora.frame(new Vec3(1, 2, 3), new Vec3(0.2, -1, 0.1), Vec3.UNIT_X)
                        .fruit(shape, 1, 0.7, Part.FRUIT, detail, 5);
                    ProceduralMesh mesh = writer.build(0);
                    int[] canonical = new int[mesh.vertexCount()];
                    Meshes.weld(mesh.positions(), mesh.vertexCount(), canonical);
                    assertThat(Meshes.openEdges(mesh.indices(), mesh.indices().length, canonical)).isZero();
                    // within its size of its stalk end, colored as asked
                    Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
                    Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
                    for (int i = 0; i < mesh.vertexCount(); i++) {
                        assertThat(position.get(i).distance(new Vec3(1, 2, 3)))
                            .isLessThan(2.5 * shape.halfLength() + radius);
                        assertThat(Palette.part(uv.y(i))).isEqualTo(Part.FRUIT);
                        assertThat(Palette.t(uv.x(i))).isCloseTo(0.7, within(0.01));
                    }
                }
            }
        }
    }

    @Test
    void flowersOpenFromBudsAndTheirPetalsFlutter() {
        MeshWriter writer = new MeshWriter();
        FloraWriter flora = new FloraWriter().target(writer);
        FloralState state = new FloralState();
        List<FlowerShape> shapes = List.of(
            new FlowerShape(Form.SIMPLE, 5, 0.022, 0.024, Tip.NOTCHED, 0.2, 0, 5, 0.018, 0.006, 0.003, 0),
            new FlowerShape(Form.BELL, 5, 0.045, 0.017, Tip.POINTED, 0.55, 0.3, 0, 0.01, 0, 0, 0),
            new FlowerShape(Form.COMPOSITE, 13, 0.025, 0.007, Tip.TOOTHED, -0.35, 0, 10, 0.01, 0.009, 0.005, 0),
            new FlowerShape(Form.GLOBE, 9, 0.012, 0.012, Tip.ROUND, 0, 0, 2, 0.012, 0, 0, 0),
            new FlowerShape(Form.STAR, 8, 0.022, 0.007, Tip.POINTED, 0.15, 0, 0, 0.005, 0.0035, 0.003, 6));
        for (FlowerShape shape : shapes) {
            int previous = Integer.MAX_VALUE;
            for (int detail = 0; detail < 3; detail++) {
                double[] extent = new double[2];
                for (int k = 0; k < 2; k++) {
                    writer.reset();
                    state.set(k == 0 ? FloralState.Stage.BUD : FloralState.Stage.FLOWER, k, 1, 0.3 + 0.4 * k, 0);
                    flora.frame(Vec3.ZERO, Vec3.UP, Vec3.UNIT_X).flower(shape, state, Palette.DEFAULT, 1, detail, 0.6,
                        7);
                    ProceduralMesh mesh = writer.build(0);
                    assertThat(mesh.triangleCount()).isPositive();
                    Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
                    var wind = mesh.vertices().attribute(ProceduralVertex.WIND);
                    double flutter = 0;
                    for (int i = 0; i < mesh.vertexCount(); i++) {
                        Vec3 p = position.get(i);
                        extent[k] = Math.max(extent[k], Math.hypot(p.x(), p.z()));
                        assertThat(p.length()).isLessThan(1.6 * shape.reach() + 0.02);
                        flutter = Math.max(flutter, ProceduralVertex.leaf(wind.z(i)));
                    }
                    assertThat(flutter).isBetween(0.0, 0.6 + 1 / 30.0);
                    if (k == 1 && detail == 0) {
                        assertThat(mesh.triangleCount()).isLessThanOrEqualTo(previous);
                        previous = mesh.triangleCount();
                    }
                }
                // an open flower spreads wider than its bud (a globe stays round)
                if (shape.form() != Form.GLOBE) {
                    assertThat(extent[1]).isGreaterThan(extent[0]);
                }
            }
        }
        // nothing for no flower, or a fruit
        writer.reset();
        flora.flower(shapes.getFirst(), state.none(), Palette.DEFAULT, 1, 0, 0.5, 1);
        flora.flower(shapes.getFirst(), state.set(FloralState.Stage.FRUIT, 0, 1, 1, 1), Palette.DEFAULT, 1, 0, 0.5, 1);
        assertThat(writer.triangleCount()).isZero();
    }

    @Test
    void stripsAndTubesCarryTheirWind() {
        MeshWriter writer = new MeshWriter();
        FloraWriter flora = new FloraWriter().target(writer);
        writer.wind(0, 0, 0, 0.25, 0, 0.5);
        flora.strip(Vec3.ZERO, Vec3.UP, Vec3.UNIT_Z, 0.3, 0.01, Outline.BLADE, -1, 0.3, 4, Part.BLADE, 0.2, 0, 0.5,
            0, 0.8);
        double[] points = {0, 0, 0, 0, 0.1, 0, 0.01, 0.2, 0};
        flora.tube(points, 3, 0.002, 0.001, 4, Part.LEAF, 0, new double[] {0, 0.3, 0.6});
        flora.stalk(Vec3.ZERO, new Vec3(0, 0.05, 0), 0.001, 3, 1);
        flora.stalk(Vec3.ZERO, Vec3.ZERO, 0.001, 3, 1); // nothing: no length
        ProceduralMesh mesh = writer.build(0);
        var wind = mesh.vertices().attribute(ProceduralVertex.WIND);
        var position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        double maxBranch = 0;
        for (int i = 0; i < mesh.vertexCount(); i++) {
            maxBranch = Math.max(maxBranch, wind.y(i));
            assertThat(ProceduralVertex.phase(wind.z(i))).isEqualTo(0.25);
            assertThat(position.get(i).length()).isLessThan(0.31);
        }
        assertThat(maxBranch).isCloseTo(0.8, within(0.01));
        assertThat(FloraWriter.sink(Vec3.UNIT_X, Math.PI / 2).y()).isCloseTo(-1, within(1e-9));
        assertThat(FloraWriter.turn(Vec3.UP, Vec3.UP, 1)).isEqualTo(Vec3.UP);
        assertThat(FloraWriter.around(Vec3.UP, 0).dot(Vec3.UP)).isCloseTo(0, within(1e-12));
    }

    @Test
    void summariesWeighAlbedosByArea() {
        FloraSummary.Builder builder = new FloraSummary.Builder();
        assertThat(builder.build()).isEqualTo(FloraSummary.NONE);
        builder.add(FloralState.Stage.FLOWER, new Albedo(0.8, 0.8, 0.8), 3);
        builder.add(FloralState.Stage.FRUIT, new Albedo(0.4, 0, 0), 1);
        builder.add(FloralState.Stage.BUD, new Albedo(0, 0, 0), 0);
        builder.add(FloralState.Stage.NONE, new Albedo(1, 1, 1), 5);
        FloraSummary summary = builder.build();
        assertThat(summary.shown()).isEqualTo(3);
        assertThat(summary.albedo().r()).isCloseTo(0.7, within(1e-12));
        assertThat(summary.area()).isEqualTo(4);
    }

    @Test
    void badShapesAreRefused() {
        assertThatThrownBy(() -> new FlowerShape(Form.SIMPLE, 5, 0, 1, Tip.ROUND, 0, 0, 0, 0, 0, 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FruitShape(FruitShape.Form.BERRY, 0.01, 1, 0, 0, 0, 0, 2))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Inflorescence(Inflorescence.Arrangement.UMBEL, 3, 2, 0, 0, 0, 0, 0, 0, 1))
            .isInstanceOf(IllegalArgumentException.class);
        Inflorescence one = Inflorescence.solitary(0.01, 0, 0, 0);
        FruitShape fruit = new FruitShape(FruitShape.Form.CONE, 0.01, 2, 0, 0, 5, 0, 0);
        assertThatThrownBy(() -> new Fruiting(Fruiting.Placement.TIPS, 1, -1, 0, 0, one, null, fruit, ROWAN, 1, 0,
            Palette.DEFAULT)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Fruiting(Fruiting.Placement.TIPS, 1, 1, 0, 0, one, null, fruit, ROWAN, 1, 0, Palette.DEFAULT)
            .cones()).isTrue();
        assertThat(fruit.halfLength()).isCloseTo(0.02, within(1e-12));
    }
}
