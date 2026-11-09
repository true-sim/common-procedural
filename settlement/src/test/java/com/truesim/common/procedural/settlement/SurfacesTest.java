package com.truesim.common.procedural.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.settlement.Settlement.Building;
import com.truesim.common.procedural.settlement.Settlement.Plot;
import com.truesim.common.procedural.settlement.Settlement.Road;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a plan gives the terrain and the renderer: switchbacks on steep ground, road meshes, the clearings and the
 * grading; and recipes.
 */
class SurfacesTest {

    private static final Settlement VILLAGE = SettlementTest.village != null ? SettlementTest.village
        : Settlements.plan(SettlementParameters.DEFAULTS, Valleys.ecology(Valleys.valley()), -60, 0, 7);

    @Test
    void roadsClimbSteepGroundInSwitchbacks() {
        // a 22 degree slope rising north: a lane (12 %) straight up it would climb at 40 %
        EcologyTerrain slope = (x, z) -> 1000 - Maths.tan(Maths.toRadians(22)) * z;
        Ecology ecology = Valleys.ecology(slope);
        SettlementParameters p = SettlementParameters.DEFAULTS.withGrid(new SettlementParameters.Grid(512, 4, 1));
        Field field = new Field(ecology, 0, 0, p);
        Router router = new Router(field, p.network());
        int from = field.node(0, 120);
        int to = field.node(0, -120);
        boolean[] none = new boolean[field.n * field.n];
        int[] path = router.route(p.lane(), new int[] {from}, to, none, none);
        assertThat(path).isNotNull();
        double rise = field.height[to] - field.height[from];
        double length = 0;
        int reversals = 0;
        int previous = 0;
        for (int k = 1; k < path.length; k++) {
            double dx = field.x(path[k] % field.n) - field.x(path[k - 1] % field.n);
            double dz = field.z(path[k] / field.n) - field.z(path[k - 1] / field.n);
            length += Math.sqrt(dx * dx + dz * dz);
            // across the slope: east or west; a switchback turns from one to the other
            int across = dx > 0 ? 1 : dx < 0 ? -1 : 0;
            if (across != 0 && previous != 0 && across != previous) {
                reversals++;
            }
            if (across != 0) {
                previous = across;
            }
            double grade = Math.abs(field.height[path[k]] - field.height[path[k - 1]]) / Math.sqrt(dx * dx + dz * dz);
            assertThat(grade).isLessThanOrEqualTo(p.lane().maxGrade());
        }
        assertThat(length).isGreaterThan(rise / p.lane().maxGrade());
        assertThat(reversals).as("switchbacks").isGreaterThanOrEqualTo(2);
        // nothing passable: no way
        boolean[] all = new boolean[field.n * field.n];
        java.util.Arrays.fill(all, true);
        all[from] = false;
        assertThat(router.route(p.lane(), new int[] {from}, to, none, all)).isNull();
    }

    @Test
    void roadMeshesCoverTheRoadsInTilesWithTheirSlots() {
        List<RoadMeshes.Tile> tiles = RoadMeshes.build(VILLAGE);
        assertThat(tiles).isNotEmpty();
        int[] triangles = new int[RoadMeshes.SLOTS];
        double size = VILLAGE.parameters().surface().tile();
        for (RoadMeshes.Tile tile : tiles) {
            ProceduralMesh mesh = tile.mesh();
            assertThat(mesh.submeshCount()).isEqualTo(RoadMeshes.SLOTS);
            assertThat(tile.x()).isEqualTo(tile.i() * size);
            assertThat(tile.z()).isEqualTo(tile.j() * size);
            for (int s = 0; s < RoadMeshes.SLOTS; s++) {
                triangles[s] += (mesh.submeshEnd(s) - mesh.submeshStart(s)) / 3;
            }
            float[] positions = mesh.positions();
            for (int v = 0; v < positions.length; v += 3) {
                assertThat(positions[v]).isBetween(-4f, (float) size + 4);
                assertThat(positions[v + 1]).isBetween(-1f, 400f);
                assertThat(positions[v + 2]).isBetween(-4f, (float) size + 4);
            }
        }
        for (int s = 0; s < RoadMeshes.SLOTS; s++) {
            assertThat(triangles[s]).as("slot %s", RoadMeshes.SLOT_NAMES.get(s)).isGreaterThan(0);
        }
        // the same plan, the same meshes
        List<RoadMeshes.Tile> again = RoadMeshes.build(VILLAGE);
        assertThat(again).hasSameSizeAs(tiles);
        assertThat(again.getFirst().mesh().positions()).isEqualTo(tiles.getFirst().mesh().positions());
    }

    @Test
    void theRoadsSurfacesLieOnTheGradedGround() {
        Grading grading = VILLAGE.grading();
        EcologyTerrain terrain = Valleys.valley();
        for (Road r : VILLAGE.roads()) {
            for (int i = 0; i < r.count(); i += 5) {
                double x = r.x()[i];
                double z = r.z()[i];
                if (VILLAGE.square().outside(x, z) < 1) {
                    continue;
                }
                assertThat(grading.graded(x, z)).isTrue();
                // the crown, between raster samples a metre apart
                assertThat(grading.height(x, z, terrain.height(x, z))).as("road %d at %d", r.id(), i)
                    .isCloseTo(r.y()[i], org.assertj.core.data.Offset.offset(0.25));
            }
        }
        Settlement.Square sq = VILLAGE.square();
        assertThat(grading.height(sq.x(), sq.z(), 0)).isCloseTo(sq.y(), org.assertj.core.data.Offset.offset(1e-3));
        // far from everything the terrain keeps its height
        assertThat(grading.height(VILLAGE.originX() + 5000, VILLAGE.originZ(), 123)).isEqualTo(123);
        assertThat(grading.samples()).isGreaterThan(1000);
        assertThat(grading.cell()).isEqualTo(1);
        assertThat(Float.isNaN(grading.sample(0, 0))).isTrue();
        assertThat(grading.x0()).isLessThan(VILLAGE.originX());
        assertThat(grading.z0()).isLessThan(VILLAGE.originZ());
    }

    @Test
    void theClearingsKeepTreesOffAndGrassOffWhatIsPaved() {
        Clearing clearing = VILLAGE.clearing();
        for (Road r : VILLAGE.roads()) {
            for (int i = 0; i < r.count(); i += 3) {
                assertThat(clearing.trees(r.x()[i], r.z()[i])).isZero();
                // a footpath is about as wide as the raster's samples are apart
                assertThat(clearing.grass(r.x()[i], r.z()[i])).isLessThanOrEqualTo(r.width() > 2 ? 0 : 0.5);
            }
        }
        for (Plot plot : VILLAGE.plots()) {
            double cx = (plot.x()[0] + plot.x()[1] + plot.x()[2] + plot.x()[3]) / 4;
            double cz = (plot.z()[0] + plot.z()[1] + plot.z()[2] + plot.z()[3]) / 4;
            assertThat(clearing.trees(cx, cz)).isZero();
        }
        for (Building b : VILLAGE.buildings()) {
            assertThat(clearing.grass(b.x(), b.z())).isZero();
            assertThat(clearing.trees(b.x(), b.z())).isZero();
        }
        Settlement.Square sq = VILLAGE.square();
        assertThat(clearing.trees(sq.x(), sq.z())).isZero();
        assertThat(clearing.grass(sq.x(), sq.z())).isZero();
        // in a garden, away from its house, grass grows
        int gardens = 0;
        for (Plot plot : VILLAGE.plots()) {
            double x = 0.8 * plot.x()[2] + 0.2 * plot.x()[0];
            double z = 0.8 * plot.z()[2] + 0.2 * plot.z()[0];
            if (VILLAGE.building(x, z) == null && clearing.grass(x, z) == 1) {
                gardens++;
            }
        }
        assertThat(gardens).isGreaterThan(VILLAGE.plots().size() / 2);
        // out on the plan's edge, and beyond it, everything grows
        double edge = VILLAGE.parameters().grid().extent() / 2 - 5;
        int open = 0;
        for (int k = 0; k < 16; k++) {
            double a = 2 * Math.PI * k / 16;
            double x = VILLAGE.originX() + edge * Math.cos(a) * 0.98;
            double z = VILLAGE.originZ() + edge * Math.sin(a) * 0.98;
            if (clearing.trees(x, z) == 1 && clearing.grass(x, z) == 1) {
                open++;
            }
        }
        assertThat(open).isGreaterThan(8);
        assertThat(clearing.trees(VILLAGE.originX() + 5000, 0)).isEqualTo(1);
        assertThat(clearing.samples()).isEqualTo(VILLAGE.grading().samples());
        assertThat(clearing.grassSample(0, 0)).isEqualTo(255);
        assertThat(clearing.treeSample(0, 0)).isEqualTo(255);
        assertThat(clearing.cell()).isEqualTo(1);
        assertThat(clearing.x0()).isEqualTo(VILLAGE.grading().x0());
        assertThat(clearing.z0()).isEqualTo(VILLAGE.grading().z0());
    }

    @Test
    void lampsFencesAndPropsStandOnTheGroundOffTheRoads() {
        for (Settlement.Lamp l : VILLAGE.lamps()) {
            for (Road r : VILLAGE.roads()) {
                assertThat(r.distance(l.x(), l.z())).isGreaterThan(r.width() / 2);
            }
        }
        for (Settlement.Fence f : VILLAGE.fences()) {
            assertThat(f.length()).isGreaterThanOrEqualTo(VILLAGE.parameters().furniture().minFence());
            assertThat(f.x()).hasSameSizeAs(f.y()).hasSameSizeAs(f.z());
            assertThat(f.line()).hasSize(2 * f.x().length).startsWith(f.x()[0], f.z()[0]);
            if (f.kind() == Settlement.FenceKind.PASTURE) {
                for (int i = 0; i < f.x().length; i++) {
                    for (Road r : VILLAGE.roads()) {
                        assertThat(r.distance(f.x()[i], f.z()[i])).isGreaterThan(r.width() / 2);
                    }
                }
            }
        }
        for (Settlement.Prop prop : VILLAGE.props()) {
            assertThat(Double.isFinite(prop.y())).isTrue();
        }
        Building b = VILLAGE.buildings().getFirst();
        assertThat(VILLAGE.building(b.x(), b.z())).isEqualTo(b);
        assertThat(VILLAGE.building(VILLAGE.originX() + 5000, 0)).isNull();
        assertThat(VILLAGE.bytes()).isGreaterThan(1_000_000);
        assertThat(VILLAGE.order(0, 0)).isLessThan(VILLAGE.order(2, VILLAGE.buildings().size() - 1));
        assertThat(VILLAGE.order(1, 0)).isGreaterThanOrEqualTo(0);
        assertThatThrownBy(() -> VILLAGE.order(3, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(VILLAGE.seed()).isEqualTo(7);
    }

    @Test
    void aRecipeGivesTheSamePlanAsTheDirectApi() {
        SettlementRecipe recipe = SettlementRecipe.parse("""
            # a valley village
            origin = -60 0
            seed = 7
            extent = 1024
            material.cobbles = game://materials/cobbles.material
            material.steps = game://materials/granite.material
            """);
        assertThat(recipe.originX()).isEqualTo(-60);
        assertThat(recipe.seed()).isEqualTo(7);
        assertThat(recipe.materials()).containsExactly("game://materials/cobbles.material", "", "", "",
            "game://materials/granite.material");
        assertThat(recipe.parameters(SettlementParameters.DEFAULTS)).isEqualTo(SettlementParameters.DEFAULTS);
        Settlement planned = recipe.plan(Valleys.ecology(Valleys.valley()), SettlementParameters.DEFAULTS);
        SettlementTest.assertSame(VILLAGE, planned);
        SettlementRecipe small = SettlementRecipe.parse("origin = 0 0\nextent = 512\n");
        assertThat(small.parameters(SettlementParameters.DEFAULTS).grid().extent()).isEqualTo(512);
        assertThat(SettlementRecipe.parse("origin = 1 2").extent()).isNaN();
        assertThatThrownBy(() -> SettlementRecipe.parse("seed = 1")).hasMessageContaining("origin");
        assertThatThrownBy(() -> SettlementRecipe.parse("origin 1 2")).hasMessageContaining("key = value");
        assertThatThrownBy(() -> SettlementRecipe.parse("origin = 1")).hasMessageContaining("'x z'");
        assertThatThrownBy(() -> SettlementRecipe.parse("origin = 1 2\ncolour = red")).hasMessageContaining(
            "unknown key");
        assertThatThrownBy(() -> SettlementRecipe.parse("origin = 1 2\nmaterial.marble = x")).hasMessageContaining(
            "unknown key");
        assertThatThrownBy(() -> new SettlementRecipe(0, 0, 0, 1, List.of())).isInstanceOf(
            IllegalArgumentException.class);
    }

    @Test
    void onLevelGroundHousesFaceTheSun() {
        // nearly level: falling 2 % to the south
        Ecology flat = Valleys.ecology((x, z) -> 900 - 0.02 * z);
        SettlementParameters p = SettlementParameters.DEFAULTS.withGrid(new SettlementParameters.Grid(512, 4, 1))
            .withRoad(RoadKind.FOOTPATH, new SettlementParameters.RoadClass(1.4, 0.35, 38, 0, 200));
        Settlement s = Settlements.plan(p, flat, 0, 0, 3);
        SettlementTest.assertGrades(s);
        SettlementTest.assertPlots(s);
        SettlementTest.assertReachable(s);
        int south = 0;
        for (Building b : s.buildings()) {
            if (b.plot() >= 0 && s.plots().get(b.plot()).zone() == Settlement.Zone.VILLAGE
                && Math.abs(b.yaw()) < 1e-6) {
                south++;
            }
        }
        assertThat(south).isGreaterThan(0);
        // no footpaths asked for: only the barns'
        assertThat(s.roads()).filteredOn(r -> r.kind() == RoadKind.FOOTPATH).allMatch(r -> r.to() == Settlement.NONE);
    }
}
