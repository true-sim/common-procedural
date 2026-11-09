package com.truesim.common.procedural.building;

import static org.assertj.core.api.Assertions.assertThat;

import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;

/** The flower boxes' geraniums: budgets per level, seeded variation and fullness, colors, hanging over the edge. */
class GeraniumsTest {

    /** The most triangles a metre of box may have at levels 0 to 3. */
    static final int[] BUDGET = Geraniums.BUDGET;

    @Test
    void everyLevelKeepsItsBudgetAndFarLevelsAreCardsOrNothing() {
        Geraniums g = new Geraniums();
        for (long seed = 0; seed < 40; seed++) {
            for (boolean tall : new boolean[] {false, true}) {
                double length = 0.6 + 0.02 * seed;
                int previous = Integer.MAX_VALUE;
                for (int level = 0; level < 4; level++) {
                    ProceduralMesh m = g.box(length, 0.24, level, seed, tall);
                    assertThat(m.triangleCount()).as("seed %d level %d", seed, level)
                        .isLessThanOrEqualTo((int) Math.ceil(BUDGET[level] * Math.max(1, length)))
                        .isLessThanOrEqualTo(previous);
                    if (level < 3) {
                        assertThat(m.submeshEnd(1) - m.submeshStart(1)).as("flowers at level %d", level).isPositive();
                        assertThat(m.submeshEnd(0) - m.submeshStart(0)).as("leaves at level %d", level).isPositive();
                    }
                    previous = m.triangleCount();
                }
                assertThat(g.box(length, 0.24, 2, seed, tall).triangleCount()).isEqualTo(4); // two cards
            }
        }
    }

    @Test
    void seedsVaryColourAndFullnessAndTheSameSeedRepeats() {
        Geraniums g = new Geraniums();
        byte[] a = bytes(g.box(1, 0.24, 0, 3, true));
        g.box(1, 0.24, 0, 4, true);
        assertThat(bytes(new Geraniums().box(1, 0.24, 0, 3, true))).isEqualTo(a);
        int smallest = Integer.MAX_VALUE;
        int largest = 0;
        boolean red = false;
        boolean pink = false;
        for (long seed = 0; seed < 30; seed++) {
            ProceduralMesh m = g.box(1, 0.24, 0, seed, false);
            smallest = Math.min(smallest, m.triangleCount());
            largest = Math.max(largest, m.triangleCount());
            Vec2Attribute uv = m.vertices().attribute(ProceduralVertex.UV);
            for (int i = m.submeshStart(1); i < m.submeshEnd(1); i++) {
                int v = m.indices()[i];
                if (Palette.part(uv.y(v)) == Part.PETAL) {
                    double t = Palette.t(uv.x(v));
                    red |= t < 0.15;
                    pink |= t > 0.35 && t < 0.65;
                }
            }
        }
        assertThat(largest).as("fuller boxes").isGreaterThan(smallest * 13 / 10);
        assertThat(red).as("scarlet boxes").isTrue();
        assertThat(pink).as("pink boxes").isTrue();
    }

    @Test
    void ivyLeafGeraniumsHangOverTheFrontEdge() {
        ProceduralMesh m = new Geraniums().box(1, 0.24, 0, 7, true);
        assertThat(m.bounds().max().z()).as("past the box's front").isGreaterThan(0.15);
        assertThat(m.bounds().min().y()).as("hanging below its rim").isLessThan(-0.2);
        assertThat(m.bounds().max().y()).as("umbels standing above it").isGreaterThan(0.15);
        // the palette: scarlet, pink, cerise petals; green leaves
        Palette palette = Geraniums.palette();
        assertThat(palette.albedo(Part.PETAL, 0).r()).isGreaterThan(5 * palette.albedo(Part.PETAL, 0).g());
        assertThat(palette.albedo(Part.LEAF, 0).g()).isGreaterThan(palette.albedo(Part.LEAF, 0).r());
    }

    @Test
    void buildingsCarryThemOnTheNearLevels() {
        Building b = Buildings.generate(BuildingSpec.of(BuildingKind.INN, 4));
        int[] flowers = new int[4];
        for (int l = 0; l < 4; l++) {
            flowers[l] = BuildingTest.triangles(b.lods().get(l), BuildingSlot.FLOWERS);
        }
        assertThat(flowers[0]).isGreaterThan(flowers[1]);
        assertThat(flowers[1]).isGreaterThan(flowers[2]);
        assertThat(flowers[2]).isPositive();
        assertThat(flowers[3]).isZero();
        // an inn full of overflowing boxes: its geraniums within their budget at level 0, and at level 1 under a
        // fifth of its nearest level
        assertThat(flowers[1] + BuildingTest.triangles(b.lods().get(1), BuildingSlot.FOLIAGE))
            .isLessThan(b.lods().getFirst().triangleCount() / 5);
    }

    private static byte[] bytes(ProceduralMesh m) {
        return m.vertices().segment().toArray(ValueLayout.JAVA_BYTE);
    }
}
