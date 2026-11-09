package com.truesim.common.procedural.settlement.terrain;

import static org.assertj.core.api.Assertions.assertThat;

import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.terrain.TerrainEcology;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.settlement.RoadMeshes;
import com.truesim.common.procedural.settlement.Settlement;
import com.truesim.common.procedural.settlement.SettlementParameters;
import com.truesim.common.procedural.settlement.Settlements;
import com.truesim.effects.terrain.data.DemRaster;
import com.truesim.effects.terrain.data.LocalFrame;
import com.truesim.effects.terrain.data.SurfaceParameters;
import com.truesim.effects.terrain.data.TerrainModifier;
import com.truesim.effects.terrain.data.TerrainModifiers;
import com.truesim.effects.terrain.data.TerrainSite;
import com.truesim.effects.terrain.data.TerrainSurface;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A village's roads through effects-terrain's modifier hook: the modified surface meets the road meshes' edges, its
 * cuts and fills ease into the natural ground, its verges are laid into the material.
 */
class SettlementTerrainTest {

    private static final LocalFrame FRAME = new LocalFrame(46.5937, 7.9087);
    private static TerrainSurface natural;
    private static Settlement village;
    private static TerrainSurface modified;

    /** A valley floor 1 arc second a sample, falling north, its sides rising east and west. */
    private static TerrainSite valley(int size) {
        double step = 1 / 3600.0;
        float[] heights = new float[size * size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                double x = (i - size / 2) * 21.4;
                double z = (j - size / 2) * 30.9;
                double ax = Math.abs(x);
                heights[j * size + i] = (float) (900 + 0.03 * Math.min(ax, 200) + 0.4 * Math.max(0, ax - 200)
                    + 0.01 * z);
            }
        }
        DemRaster raster = new DemRaster(DemRaster.GEOGRAPHIC, FRAME.longitude() - step * (size / 2),
            FRAME.latitude() + step * (size / 2), step, step, size, size, heights);
        return new TerrainSite("valley", "a synthetic valley", FRAME,
            List.of(new TerrainSite.Source("valley", raster, 0, "synthetic")));
    }

    @BeforeAll
    static void plan() {
        natural = new TerrainSurface(valley(96), new SurfaceParameters(2.5, 64, 6, 1.5, false));
        Ecology ecology = new Ecology(TerrainEcology.of(natural, 1), EcologyParameters.ALPS
            .withAnalysis(new EcologyParameters.Analysis(8, 512, 512, 150, 40, 32))
            .withLandUse(EcologyParameters.LandUse.MEADOWS).withSeed(3), AlpineSpecies.ALL);
        SettlementParameters p = SettlementParameters.DEFAULTS.withGrid(new SettlementParameters.Grid(512, 4, 1));
        village = Settlements.plan(p, ecology, -40, 0, 5);
        modified = natural.withModifiers(TerrainModifiers.of(SettlementTerrain.of(village)));
    }

    @Test
    void theTerrainMeetsTheRoadMeshesEdges() {
        // every surface vertex near the paved edge: the modified ground there within a couple of centimetres of it
        int checked = 0;
        for (RoadMeshes.Tile tile : village.roadMeshes()) {
            ProceduralMesh mesh = tile.mesh();
            float[] p = mesh.positions();
            for (int s = 0; s <= RoadMeshes.DIRT; s++) {
                for (int t = mesh.submeshStart(s); t < mesh.submeshEnd(s); t++) {
                    int v = mesh.indices()[t];
                    double x = tile.x() + p[3 * v];
                    double y = tile.y() + p[3 * v + 1];
                    double z = tile.z() + p[3 * v + 2];
                    if (Math.abs(village.grading().distance(x, z)) < 0.05 && t % 7 == 0) {
                        assertThat(modified.height(x, z, 0.5) - y).as("at (%.2f, %.2f)", x, z).isBetween(-0.02,
                            0.02);
                        checked++;
                    }
                }
            }
        }
        assertThat(checked).isGreaterThan(50);
    }

    @Test
    void underEachRoadItsOwnMaterial() {
        TerrainModifier roads = SettlementTerrain.of(village);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Settlement.Road r : village.roads()) {
            int mid = r.count() / 2;
            double[] cover = new double[TerrainModifier.MATERIALS];
            if (village.grading().surface(r.x()[mid], r.z()[mid]) != r.surface()) {
                continue;
            }
            assertThat(roads.materials(r.x()[mid], r.z()[mid], 1, cover)).isEqualTo(1);
            int expected = switch (r.surface()) {
                case COBBLES -> TerrainModifier.COBBLES;
                case GRAVEL -> TerrainModifier.GRAVEL;
                case DIRT -> TerrainModifier.DIRT;
            };
            assertThat(cover[expected]).isEqualTo(1);
            seen.add(expected);
        }
        assertThat(seen).contains(TerrainModifier.COBBLES, TerrainModifier.GRAVEL, TerrainModifier.DIRT);
    }

    @Test
    void theModifierGradesWithinItsBoundsAndLaysTheVerges() {
        TerrainModifier roads = SettlementTerrain.of(village);
        TerrainModifier.Bounds b = roads.bounds();
        assertThat(b.x0()).isLessThan(village.originX() - 256);
        assertThat(b.x1()).isGreaterThan(village.originX() + 256);
        Settlement.Road road = village.roads().getFirst();
        int mid = road.count() / 2;
        double x = road.x()[mid];
        double z = road.z()[mid];
        // under the road: its material, the ground at its crown less the sink
        double[] cover = new double[TerrainModifier.MATERIALS];
        assertThat(roads.materials(x, z, 1, cover)).isEqualTo(1);
        assertThat(cover[TerrainModifier.GRAVEL] + cover[TerrainModifier.COBBLES]).isEqualTo(1);
        assertThat(roads.height(x, z, 1, natural.height(x, z, 1))).isBetween(road.y()[mid] - 0.2,
            road.y()[mid] + 0.01);
        // beside it: a verge, fading at coarse spacings; far off: nothing
        double nx = road.z()[mid + 1] - road.z()[mid - 1];
        double nz = -(road.x()[mid + 1] - road.x()[mid - 1]);
        double l = Math.hypot(nx, nz);
        double off = road.width() / 2 + 0.4;
        double vx = x + nx / l * off;
        double vz = z + nz / l * off;
        double[] near = new double[TerrainModifier.MATERIALS];
        double total = roads.materials(vx, vz, 0.5, near);
        assertThat(total).isGreaterThan(0).isLessThanOrEqualTo(1);
        double[] coarse = new double[TerrainModifier.MATERIALS];
        assertThat(roads.materials(vx, vz, 32, coarse)).isLessThan(total);
        double[] far = new double[TerrainModifier.MATERIALS];
        assertThat(roads.materials(x + 400, z + 400, 1, far)).isZero();
        assertThat(roads.height(x + 400, z + 400, 1, 123)).isEqualTo(123);
        // the cut or fill eases out: the modified ground is continuous beside the road
        double previous = modified.height(x, z, 0.5);
        for (double o = 0; o < 12; o += 0.25) {
            double h = modified.height(x + nx / l * o, z + nz / l * o, 0.5);
            assertThat(Math.abs(h - previous)).isLessThan(0.3);
            previous = h;
        }
    }
}
