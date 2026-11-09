package com.truesim.common.procedural.building;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Aabb;
import com.truesim.common.math.geometry.Meshes;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Buildings on the CPU: closed shells, foundations meeting the ground on slopes, openings cut through the walls,
 * determinism, texture coordinates, levels and fittings.
 */
class BuildingTest {

    /** A slope of 25 % falling to the front-right, turned, off the origin: the hard case for foundations. */
    static final GroundHeight SLOPE = (x, z) -> 400 + 0.25 * (x - 30) - 0.12 * (z + 20)
        + 0.3 * Math.sin(x * 0.4) * Math.cos(z * 0.3);

    static Stream<Arguments> kindsAndGrounds() {
        List<Arguments> out = new ArrayList<>();
        for (BuildingKind kind : BuildingKind.values()) {
            out.add(Arguments.of(kind, "flat"));
            out.add(Arguments.of(kind, "slope"));
        }
        return out.stream();
    }

    static Building build(BuildingKind kind, String ground, BuildingGenerator generator, long seed) {
        Footprint f = new Footprint(30, -20, 0.6, kind.width(), kind.depth());
        GroundHeight g = "flat".equals(ground) ? GroundHeight.flat(12) : SLOPE;
        return generator.generate(BuildingSpec.of(kind, f, seed), g, BuildingOptions.DEFAULTS);
    }

    @ParameterizedTest
    @MethodSource("kindsAndGrounds")
    void everyPartOfEveryLevelIsAClosedShell(BuildingKind kind, String ground) {
        BuildingGenerator generator = new BuildingGenerator();
        for (long seed = 1; seed <= 3; seed++) {
            Building b = build(kind, ground, generator, seed);
            for (int level = 0; level < b.lods().size(); level++) {
                ProceduralMesh mesh = b.lods().get(level);
                int[] parts = generator.parts(level);
                assertThat(parts).hasSize(mesh.triangleCount());
                float[] positions = mesh.positions();
                int[] canonical = new int[mesh.vertexCount()];
                Meshes.weld(positions, mesh.vertexCount(), canonical);
                int partCount = Arrays.stream(parts).max().orElse(-1) + 1;
                int[][] byPart = new int[partCount][];
                int[] counts = new int[partCount];
                for (int part : parts) {
                    if (part >= 0) { // flora sheets (-1) are single sheets, not shells
                        counts[part]++;
                    }
                }
                for (int p = 0; p < partCount; p++) {
                    byPart[p] = new int[3 * counts[p]];
                    counts[p] = 0;
                }
                int[] indices = mesh.indices();
                for (int t = 0; t < parts.length; t++) {
                    int p = parts[t];
                    if (p < 0) {
                        continue;
                    }
                    System.arraycopy(indices, 3 * t, byPart[p], 3 * counts[p]++, 3);
                }
                for (int p = 0; p < partCount; p++) {
                    assertThat(Meshes.openEdges(byPart[p], byPart[p].length, canonical))
                        .as("%s on %s, seed %d, level %d, part %d of %d", kind, ground, seed, level, p, partCount)
                        .isZero();
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(BuildingKind.class)
    void theFoundationMeetsTheGroundAllRoundOnASlope(BuildingKind kind) {
        BuildingGenerator generator = new BuildingGenerator();
        Building b = build(kind, "slope", generator, 4);
        Footprint f = b.spec().footprint();
        double hw = f.width() / 2;
        double hd = f.depth() / 2;
        // the floor stands its clearance above the highest ground under the footprint
        double highest = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 20; i++) {
            for (int j = 0; j <= 20; j++) {
                double lx = -hw + f.width() * i / 20;
                double lz = -hd + f.depth() * j / 20;
                highest = Math.max(highest, SLOPE.height(f.worldX(lx, lz), f.worldZ(lx, lz)));
            }
        }
        if (kind == BuildingKind.HAY_BARN) {
            // dug into the slope: the uphill ground below the loft's floor
            assertThat(b.floor() - highest).isBetween(-(kind.groundStorey() - 0.45) + 0.25, 0.32);
        } else {
            assertThat(b.floor() - highest).isBetween(0.25, 0.32);
        }
        // the church's nave ends where its tower begins
        double front = kind == BuildingKind.CHURCH ? hd - Math.clamp(f.width() * 0.45, 3.2, 4.6) + 0.35 : hd;
        for (int level = 0; level < b.lods().size(); level++) {
            ProceduralMesh mesh = b.lods().get(level);
            int checked = 0;
            for (double s = 0.02; s < 1; s += 0.03) {
                double[][] points = {{-hw + f.width() * s, front - 0.2}, {-hw + f.width() * s, -hd + 0.2},
                    {hw - 0.2, -hd + (front + hd) * s}, {-hw + 0.2, -hd + (front + hd) * s}};
                for (double[] q : points) {
                    double ground = SLOPE.height(f.worldX(q[0], q[1]), f.worldZ(q[0], q[1])) - b.floor();
                    double[] span = verticalSpan(mesh, q[0], q[1], BuildingSlot.STONE);
                    assertThat(span).as("%s level %d: stone under (%.2f, %.2f)", kind, level, q[0], q[1]).isNotNull();
                    assertThat(span[0]).as("%s level %d: the foundation reaches below the ground at (%.2f, %.2f)",
                        kind, level, q[0], q[1]).isLessThan(ground - 0.3);
                    assertThat(span[1]).as("%s level %d: and up to the floor", kind, level).isGreaterThan(-0.01);
                    checked++;
                }
            }
            assertThat(checked).isGreaterThan(100);
        }
    }

    @ParameterizedTest
    @EnumSource(BuildingKind.class)
    void openingsAreCutThroughTheWalls(BuildingKind kind) {
        Set<BuildingSlot> walls = EnumSet.of(BuildingSlot.STONE, BuildingSlot.PLASTER, BuildingSlot.TIMBER,
            BuildingSlot.INTERIOR, BuildingSlot.SHINGLES, BuildingSlot.SLATES, BuildingSlot.HEARTH);
        BuildingGenerator generator = new BuildingGenerator();
        for (String ground : List.of("flat", "slope")) {
            Building b = build(kind, ground, generator, 5);
            if (kind != BuildingKind.WOODSHED) {
                assertThat(b.fittings().openings()).isNotEmpty();
            }
            for (int level = 0; level <= 1; level++) {
                ProceduralMesh mesh = b.lods().get(level);
                for (Opening o : b.fittings().openings()) {
                    Vec3 along = o.along();
                    for (double[] at : new double[][] {{0.25, 0.5}, {0.75, 0.17}, {0.3, 0.83}}) {
                        Vec3 c = o.center().add(along.scale((at[0] - 0.5) * o.width()))
                            .add(0, (at[1] - 0.5) * o.height(), 0);
                        Vec3 from = c.add(o.outward().scale(0.2));
                        Vec3 to = c.sub(o.outward().scale(o.depth() + 0.02));
                        Set<BuildingSlot> hit = hits(mesh, from, to);
                        Set<BuildingSlot> blocked = EnumSet.noneOf(BuildingSlot.class);
                        blocked.addAll(hit);
                        blocked.retainAll(walls);
                        assertThat(blocked).as("%s %s level %d: %s at %s through %s", kind, ground, level, o.kind(),
                            at, c).isEmpty();
                        if (o.kind() == Opening.Kind.WINDOW) {
                            assertThat(hit).as("%s: a pane in the window", kind).contains(BuildingSlot.GLASS);
                        }
                    }
                }
            }
            // the far levels close them
            ProceduralMesh far = b.lods().get(2);
            for (Opening o : b.fittings().openings()) {
                Vec3 from = o.center().add(o.outward().scale(0.2)).add(o.along().scale(0.25 * o.width()));
                Vec3 to = from.sub(o.outward().scale(o.depth() + 0.25));
                Set<BuildingSlot> hit = hits(far, from, to);
                hit.retainAll(walls);
                assertThat(hit).as("%s level 2 closed at %s", kind, o.kind()).isNotEmpty();
            }
        }
    }

    @Test
    void theSameSpecGivesBitIdenticalBuildings() throws Exception {
        for (BuildingKind kind : BuildingKind.values()) {
            BuildingGenerator generator = new BuildingGenerator();
            Building a = build(kind, "slope", generator, 9);
            build(kind, "flat", generator, 10);
            Building b = CompletableFuture.supplyAsync(() -> Buildings.generate(BuildingSpec.of(kind,
                new Footprint(30, -20, 0.6, kind.width(), kind.depth()), 9), SLOPE)).get();
            assertThat(b.lods()).hasSameSizeAs(a.lods());
            for (int l = 0; l < a.lods().size(); l++) {
                assertThat(bytes(b.lods().get(l))).isEqualTo(bytes(a.lods().get(l)));
                assertThat(b.lods().get(l).indices()).isEqualTo(a.lods().get(l).indices());
                assertThat(b.lods().get(l).submeshes()).isEqualTo(a.lods().get(l).submeshes());
            }
            assertThat(b.fittings()).isEqualTo(a.fittings());
            assertThat(b.floor()).isEqualTo(a.floor());
            Building other = build(kind, "slope", generator, 11);
            assertThat(bytes(other.lods().getFirst())).isNotEqualTo(bytes(a.lods().getFirst()));
            // the layout alone has the same fittings and floor
            Building layout = Buildings.layout(a.spec(), SLOPE);
            assertThat(layout.fittings()).isEqualTo(a.fittings());
            assertThat(layout.floor()).isEqualTo(a.floor());
            assertThat(layout.lods()).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(BuildingKind.class)
    void levelsGetCoarserAndKeepTheirSlots(BuildingKind kind) {
        Building b = Buildings.generate(BuildingSpec.of(kind, 3), SLOPE);
        assertThat(b.lods()).hasSize(4);
        int previous = Integer.MAX_VALUE;
        double error = -1;
        for (ProceduralMesh mesh : b.lods()) {
            assertThat(mesh.submeshCount()).isEqualTo(BuildingSlot.all().size());
            assertThat(mesh.triangleCount()).isLessThan(previous).isPositive();
            assertThat(mesh.error()).isGreaterThan(error);
            previous = mesh.triangleCount();
            error = mesh.error();
        }
        assertThat(b.lods().getLast().triangleCount()).isLessThan(b.lods().getFirst().triangleCount() / 4);
        // levels 0 and 1 have glass, 2 and 3 dark panes
        if (!b.fittings().openings(Opening.Kind.WINDOW).isEmpty()) {
            assertThat(triangles(b.lods().get(0), BuildingSlot.GLASS)).isPositive();
            assertThat(triangles(b.lods().get(2), BuildingSlot.GLASS)).isZero();
            assertThat(triangles(b.lods().get(2), BuildingSlot.WINDOW)).isPositive();
        }
    }

    @ParameterizedTest
    @EnumSource(BuildingKind.class)
    void textureCoordinatesStaySmallAndFramesAreOrthonormal(BuildingKind kind) {
        BuildingOptions options = BuildingOptions.DEFAULTS;
        Building b = Buildings.generate(BuildingSpec.of(kind, 6), SLOPE, options);
        for (ProceduralMesh mesh : b.lods()) {
            Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
            Vec3Attribute normal = mesh.vertices().attribute(ProceduralVertex.NORMAL);
            Vec3Attribute tangent = mesh.vertices().attribute(ProceduralVertex.TANGENT);
            for (BuildingSlot slot : BuildingSlot.all()) {
                double limit = 1 + options.cell() / options.repeat(slot) + 0.01;
                for (int i = mesh.submeshStart(slot.ordinal()); i < mesh.submeshEnd(slot.ordinal()); i++) {
                    int v = mesh.indices()[i];
                    assertThat(uv.x(v)).as("%s %s u", kind, slot).isBetween(-0.01, limit);
                    assertThat(uv.y(v)).as("%s %s v", kind, slot).isBetween(-0.01, limit);
                    Vec3 n = new Vec3(normal.x(v), normal.y(v), normal.z(v));
                    Vec3 t = new Vec3(tangent.x(v), tangent.y(v), tangent.z(v));
                    assertThat(n.length()).isCloseTo(1, org.assertj.core.data.Offset.offset(0.01));
                    assertThat(Math.abs(n.dot(t))).isLessThan(0.02);
                }
            }
        }
    }

    @Test
    void verticalFacesRunTheTextureUpright() {
        // on a wall, v runs down: the bitangent cross(normal, tangent) points down
        Building b = Buildings.generate(BuildingSpec.of(BuildingKind.CHALET, 1));
        ProceduralMesh mesh = b.lods().getFirst();
        Vec3Attribute normal = mesh.vertices().attribute(ProceduralVertex.NORMAL);
        Vec3Attribute tangent = mesh.vertices().attribute(ProceduralVertex.TANGENT);
        int s = BuildingSlot.STONE.ordinal();
        int checked = 0;
        for (int i = mesh.submeshStart(s); i < mesh.submeshEnd(s); i++) {
            int v = mesh.indices()[i];
            Vec3 n = new Vec3(normal.x(v), normal.y(v), normal.z(v));
            if (Math.abs(n.y()) > 0.1) {
                continue;
            }
            Vec3 t = new Vec3(tangent.x(v), tangent.y(v), tangent.z(v));
            assertThat(n.cross(t).y()).isLessThan(-0.99);
            checked++;
        }
        assertThat(checked).isGreaterThan(100);
    }

    @Test
    void fittingsAreWhereTheyBelong() {
        for (BuildingKind kind : BuildingKind.values()) {
            Building b = Buildings.generate(BuildingSpec.of(kind, new Footprint(5, 5, 1.0, kind.width(),
                kind.depth()), 2), SLOPE);
            Fittings f = b.fittings();
            Aabb bounds = b.lods().getFirst().bounds();
            for (Anchor a : f.anchors()) {
                assertThat(bounds.expand(4).contains(a.position())).as("%s %s", kind, a).isTrue();
                if (a.room() >= 0) {
                    Aabb room = f.rooms().get(a.room()).bounds();
                    assertThat(room.expand(0.05).contains(a.position())).as("%s %s in %s", kind, a,
                        f.rooms().get(a.room())).isTrue();
                }
            }
            switch (kind) {
                case CHALET, INN, FARMHOUSE -> {
                    assertThat(f.first(Anchor.Kind.FIRE)).isNotNull();
                    assertThat(f.first(Anchor.Kind.CHIMNEY).position().y()).isGreaterThan(bounds.max().y() - 1.2);
                    assertThat(f.all(Anchor.Kind.CEILING_LAMP)).hasSize(f.rooms().size());
                    assertThat(f.first(Anchor.Kind.DOOR)).isNotNull();
                    assertThat(f.all(Anchor.Kind.LANTERN)).isNotEmpty();
                    assertThat(f.first(Anchor.Kind.WOODPILE)).isNotNull();
                    assertThat(f.rooms().getFirst().floor()).isZero();
                }
                case CHURCH -> {
                    assertThat(f.first(Anchor.Kind.BELL)).isNotNull();
                    assertThat(f.all(Anchor.Kind.CLOCK)).hasSize(4);
                    assertThat(f.all(Anchor.Kind.PEW)).isNotEmpty();
                    assertThat(f.first(Anchor.Kind.FIRE)).isNull();
                    assertThat(f.openings(Opening.Kind.BELFRY)).hasSize(4);
                }
                case HAY_BARN -> {
                    assertThat(f.rooms()).isEmpty();
                    assertThat(f.openings(Opening.Kind.HAY_DOOR)).isNotEmpty();
                    assertThat(f.first(Anchor.Kind.CHIMNEY)).isNull();
                }
                case WOODSHED -> assertThat(f.first(Anchor.Kind.WOODPILE)).isNotNull();
                default -> {
                }
            }
            // the world placement: the footprint's turn and the floor
            Vec3 door = b.world(f.anchors().getFirst().position());
            assertThat(door.y()).isCloseTo(b.floor() + f.anchors().getFirst().position().y(),
                org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    @Test
    void aSteepSlopeGetsACellarDoorAndSteps() {
        GroundHeight steep = GroundHeight.plane(0, 0, 0.35); // rising to the front: the back stands tall
        Building b = Buildings.generate(BuildingSpec.of(BuildingKind.CHALET, 8), steep);
        assertThat(b.fittings().first(Anchor.Kind.CELLAR_DOOR)).isNotNull();
        GroundHeight down = GroundHeight.plane(0, 0, -0.3); // falling to the front: steps down from the door
        Building c = Buildings.generate(BuildingSpec.of(BuildingKind.CHALET, 8), down);
        ProceduralMesh mesh = c.lods().getFirst();
        Anchor door = c.fittings().first(Anchor.Kind.DOOR);
        // a landing before the door, then a stair along the wall: treads below the threshold beside the landing
        assertThat(verticalSpan(mesh, door.position().x(), door.position().z() + 0.9, BuildingSlot.STONE)[1])
            .isCloseTo(0, org.assertj.core.data.Offset.offset(0.01));
        boolean tread = false;
        for (double u : new double[] {-1.3, 1.3}) {
            double[] span = verticalSpan(mesh, door.position().x() + u, door.position().z() + 0.6, BuildingSlot.STONE);
            tread |= span != null && span[1] < -0.1 && span[1] > -0.5;
        }
        assertThat(tread).isTrue();
        // a gentle slope: a few steps straight out
        Building gentle = Buildings.generate(BuildingSpec.of(BuildingKind.CHALET, 8), GroundHeight.plane(0, 0, -0.02));
        Anchor d = gentle.fittings().first(Anchor.Kind.DOOR);
        double[] step = verticalSpan(gentle.lods().getFirst(), d.position().x(), d.position().z() + 0.85,
            BuildingSlot.STONE);
        assertThat(step).isNotNull();
        assertThat(step[1]).isLessThan(-0.1);
    }

    @Test
    void roofsAndOptionsChangeWhatIsBuilt() {
        BuildingSpec spec = BuildingSpec.of(BuildingKind.CHALET, 4);
        Building stones = Buildings.generate(spec.withRoof(RoofCover.STONE_WEIGHTED));
        Building slates = Buildings.generate(spec.withRoof(RoofCover.SLATES));
        assertThat(stones.spec().pitch()).isLessThanOrEqualTo(BuildingSpec.STONE_ROOF_PITCH);
        assertThat(triangles(slates.lods().getFirst(), BuildingSlot.SLATES)).isPositive();
        assertThat(triangles(slates.lods().getFirst(), BuildingSlot.SHINGLES)).isZero();
        assertThat(triangles(stones.lods().getFirst(), BuildingSlot.STONE))
            .isGreaterThan(triangles(slates.lods().getFirst(), BuildingSlot.STONE) + 500);
        Building bare = Buildings.generate(spec, GroundHeight.flat(0), BuildingOptions.DEFAULTS.withInteriors(false));
        Building full = Buildings.generate(spec);
        assertThat(triangles(bare.lods().getFirst(), BuildingSlot.FLOOR)).isZero();
        assertThat(triangles(full.lods().getFirst(), BuildingSlot.FLOOR)).isPositive();
        assertThat(triangles(full.lods().getFirst(), BuildingSlot.HEARTH)).isPositive();
        // finer cells: more triangles, same building
        Building fine = Buildings.generate(spec, GroundHeight.flat(0), BuildingOptions.DEFAULTS.withCell(0.5));
        assertThat(fine.lods().getFirst().triangleCount()).isGreaterThan(full.lods().getFirst().triangleCount());
        assertThat(fine.fittings()).isEqualTo(full.fittings());
        // three floors make a taller house
        Building tall = Buildings.generate(spec.withFloors(3));
        assertThat(tall.lods().getFirst().bounds().max().y()).isGreaterThan(full.lods().getFirst().bounds().max().y()
            + 2);
        assertThat(spec.withSeed(9).seed()).isEqualTo(9);
        assertThat(spec.withPitch(30).pitch()).isEqualTo(30);
        assertThat(spec.withFootprint(Footprint.of(8, 9)).footprint().width()).isEqualTo(8);
    }

    @Test
    void specsAndOptionsAreChecked() {
        assertThatThrownBy(() -> Footprint.of(1, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BuildingSpec.of(BuildingKind.CHALET, 1).withFloors(5))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BuildingSpec.of(BuildingKind.CHURCH, 1).withFloors(2))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BuildingSpec.of(BuildingKind.CHALET, 1).withPitch(70))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BuildingSpec(null, Footprint.of(5, 5), 1, 0, null, Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BuildingOptions.DEFAULTS.withCell(0.01)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BuildingOptions.DEFAULTS.withRepeat(BuildingSlot.STONE, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(BuildingOptions.DEFAULTS.withRepeat(BuildingSlot.STONE, 4).repeat(BuildingSlot.STONE)).isEqualTo(4);
        assertThat(BuildingOptions.DEFAULTS.withClearance(0.5).withEmbed(1).withStep(2).clearance()).isEqualTo(0.5);
        assertThat(BuildingKind.of("hay-barn")).isEqualTo(BuildingKind.HAY_BARN);
        assertThat(BuildingKind.of("INN")).isEqualTo(BuildingKind.INN);
        assertThatThrownBy(() -> BuildingKind.of("castle")).isInstanceOf(IllegalArgumentException.class);
        assertThat(RoofCover.of("stone-weighted")).isEqualTo(RoofCover.STONE_WEIGHTED);
        assertThatThrownBy(() -> RoofCover.of("thatch")).isInstanceOf(IllegalArgumentException.class);
        assertThat(BuildingSlot.names()).hasSize(BuildingSlot.all().size()).contains("stone", "flowers");
        Footprint f = new Footprint(10, 20, Math.PI / 2, 8, 6);
        assertThat(f.worldX(0, 1)).isCloseTo(11, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(f.at(0, 0, 0).worldZ(0, 1)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    private static byte[] bytes(ProceduralMesh mesh) {
        return mesh.vertices().segment().toArray(ValueLayout.JAVA_BYTE);
    }

    static int triangles(ProceduralMesh mesh, BuildingSlot slot) {
        return (mesh.submeshEnd(slot.ordinal()) - mesh.submeshStart(slot.ordinal())) / 3;
    }

    /** The slots of the triangles the segment from {@code a} to {@code b} crosses. */
    static Set<BuildingSlot> hits(ProceduralMesh mesh, Vec3 a, Vec3 b) {
        Set<BuildingSlot> out = EnumSet.noneOf(BuildingSlot.class);
        float[] p = mesh.positions();
        int[] idx = mesh.indices();
        double[] d = {b.x() - a.x(), b.y() - a.y(), b.z() - a.z()};
        for (BuildingSlot slot : BuildingSlot.all()) {
            for (int i = mesh.submeshStart(slot.ordinal()); i < mesh.submeshEnd(slot.ordinal()); i += 3) {
                double t = intersect(p, idx[i], idx[i + 1], idx[i + 2], a.x(), a.y(), a.z(), d);
                if (t >= 0 && t <= 1) {
                    out.add(slot);
                    break;
                }
            }
        }
        return out;
    }

    /** The lowest and highest y where a vertical line at (x, z) crosses {@code slot}'s triangles, or null. */
    static double[] verticalSpan(ProceduralMesh mesh, double x, double z, BuildingSlot slot) {
        float[] p = mesh.positions();
        int[] idx = mesh.indices();
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        double[] d = {0, 2000, 0};
        for (int i = mesh.submeshStart(slot.ordinal()); i < mesh.submeshEnd(slot.ordinal()); i += 3) {
            double t = intersect(p, idx[i], idx[i + 1], idx[i + 2], x, -1000, z, d);
            if (t >= 0 && t <= 1) {
                double y = -1000 + 2000 * t;
                low = Math.min(low, y);
                high = Math.max(high, y);
            }
        }
        return low <= high ? new double[] {low, high} : null;
    }

    /** Moeller-Trumbore: the parameter along d where the ray from o meets the triangle, or -1. */
    private static double intersect(float[] p, int i0, int i1, int i2, double ox, double oy, double oz, double[] d) {
        double ax = p[3 * i0];
        double ay = p[3 * i0 + 1];
        double az = p[3 * i0 + 2];
        double e1x = p[3 * i1] - ax;
        double e1y = p[3 * i1 + 1] - ay;
        double e1z = p[3 * i1 + 2] - az;
        double e2x = p[3 * i2] - ax;
        double e2y = p[3 * i2 + 1] - ay;
        double e2z = p[3 * i2 + 2] - az;
        double px = d[1] * e2z - d[2] * e2y;
        double py = d[2] * e2x - d[0] * e2z;
        double pz = d[0] * e2y - d[1] * e2x;
        double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < 1e-12) {
            return -1;
        }
        double inv = 1 / det;
        double tx = ox - ax;
        double ty = oy - ay;
        double tz = oz - az;
        double u = (tx * px + ty * py + tz * pz) * inv;
        if (u < 0 || u > 1) {
            return -1;
        }
        double qx = ty * e1z - tz * e1y;
        double qy = tz * e1x - tx * e1z;
        double qz = tx * e1y - ty * e1x;
        double v = (d[0] * qx + d[1] * qy + d[2] * qz) * inv;
        if (v < 0 || u + v > 1) {
            return -1;
        }
        return (e2x * qx + e2y * qy + e2z * qz) * inv;
    }
}
