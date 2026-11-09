package com.truesim.common.procedural.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.settlement.Settlement.Road;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The road meshes are watertight: surface, junctions, kerbs and steps share their vertices (within and across tiles),
 * no edge is used by more than two triangles, no vertex lies inside another triangle's edge, no triangle is a sliver,
 * and every open edge lies on or under the terrain the grading makes: the terrain meets the road's edge.
 */
class RoadMeshTest {

    /** Welding tolerance: tiles store float positions relative to their corners. */
    private static final double WELD = 2e-4;

    private static Settlement village;
    private static List<RoadMeshes.Tile> tiles;
    private static final EcologyTerrain TERRAIN = Valleys.valley();

    /** The triangles of every tile in world positions, with their slot. */
    private static final List<double[]> TRIANGLES = new ArrayList<>();

    @BeforeAll
    static void build() {
        village = SettlementTest.village != null ? SettlementTest.village
            : Settlements.plan(SettlementParameters.DEFAULTS, Valleys.ecology(TERRAIN), -60, 0, 7);
        tiles = RoadMeshes.build(village);
        for (RoadMeshes.Tile tile : tiles) {
            ProceduralMesh mesh = tile.mesh();
            float[] p = mesh.positions();
            for (int s = 0; s < RoadMeshes.SLOTS; s++) {
                for (int t = mesh.submeshStart(s); t < mesh.submeshEnd(s); t += 3) {
                    double[] tri = new double[10];
                    for (int k = 0; k < 3; k++) {
                        int v = mesh.indices()[t + k];
                        tri[3 * k] = tile.x() + p[3 * v];
                        tri[3 * k + 1] = tile.y() + p[3 * v + 1];
                        tri[3 * k + 2] = tile.z() + p[3 * v + 2];
                    }
                    tri[9] = s;
                    TRIANGLES.add(tri);
                }
            }
        }
    }

    /** Welds positions within {@link #WELD} into ids. */
    private static final class Welder {
        final Map<Long, List<Integer>> grid = new HashMap<>();
        final List<double[]> points = new ArrayList<>();

        int id(double x, double y, double z) {
            long gx = (long) Math.floor(x / (4 * WELD));
            long gz = (long) Math.floor(z / (4 * WELD));
            for (long i = gx - 1; i <= gx + 1; i++) {
                for (long j = gz - 1; j <= gz + 1; j++) {
                    for (int id : grid.getOrDefault(i * 73856093L ^ j, List.of())) {
                        double[] q = points.get(id);
                        if (Math.abs(q[0] - x) < WELD && Math.abs(q[1] - y) < WELD && Math.abs(q[2] - z) < WELD) {
                            return id;
                        }
                    }
                }
            }
            points.add(new double[] {x, y, z});
            grid.computeIfAbsent(gx * 73856093L ^ gz, k -> new ArrayList<>()).add(points.size() - 1);
            return points.size() - 1;
        }
    }

    @Test
    void theNetworkIsOneWatertightSurfaceOverTheTerrain() {
        Welder w = new Welder();
        Map<Long, Integer> uses = new HashMap<>();
        for (double[] t : TRIANGLES) {
            int[] id = {w.id(t[0], t[1], t[2]), w.id(t[3], t[4], t[5]), w.id(t[6], t[7], t[8])};
            assertThat(id[0] != id[1] && id[1] != id[2] && id[0] != id[2]).as("a triangle with distinct corners")
                .isTrue();
            for (int k = 0; k < 3; k++) {
                int a = Math.min(id[k], id[(k + 1) % 3]);
                int b = Math.max(id[k], id[(k + 1) % 3]);
                uses.merge((long) a << 32 | b, 1, Integer::sum);
            }
        }
        Grading grading = village.grading();
        int open = 0;
        for (Map.Entry<Long, Integer> e : uses.entrySet()) {
            // no overlaps: an edge between at most two triangles
            assertThat(e.getValue()).isLessThanOrEqualTo(2);
            if (e.getValue() == 1) {
                // an open edge lies on the terrain or under it: no crack shows
                double[] a = w.points.get((int) (e.getKey() >>> 32));
                double[] b = w.points.get((int) (long) e.getKey());
                for (double[] p : new double[][] {a, b}) {
                    double ground = grading.height(p[0], p[2], TERRAIN.height(p[0], p[2]));
                    assertThat(p[1]).as("an open edge (%.3f, %.3f, %.3f)-(%.3f, %.3f, %.3f) above the ground",
                        a[0], a[1], a[2], b[0], b[1], b[2]).isLessThanOrEqualTo(ground + 0.01);
                }
                open++;
            }
        }
        assertThat(open).isGreaterThan(100);
    }

    @Test
    void theTerrainMeetsTheRoadsEdge() {
        // the surface's open edges (no kerb on them): their ends at the terrain's height, within a centimetre
        Welder w = new Welder();
        Map<Long, Integer> uses = new HashMap<>();
        Map<Long, Boolean> surface = new HashMap<>();
        for (double[] t : TRIANGLES) {
            int[] id = {w.id(t[0], t[1], t[2]), w.id(t[3], t[4], t[5]), w.id(t[6], t[7], t[8])};
            for (int k = 0; k < 3; k++) {
                int a = Math.min(id[k], id[(k + 1) % 3]);
                int b = Math.max(id[k], id[(k + 1) % 3]);
                long key = (long) a << 32 | b;
                uses.merge(key, 1, Integer::sum);
                surface.merge(key, t[9] <= RoadMeshes.DIRT, Boolean::logicalAnd);
            }
        }
        Grading grading = village.grading();
        int checked = 0;
        for (Map.Entry<Long, Integer> e : uses.entrySet()) {
            if (e.getValue() != 1 || !surface.get(e.getKey())) {
                continue;
            }
            for (int id : new int[] {(int) (e.getKey() >>> 32), (int) (long) e.getKey()}) {
                double[] p = w.points.get(id);
                if (grading.distance(p[0], p[2]) > -0.5) {
                    double ground = grading.height(p[0], p[2], TERRAIN.height(p[0], p[2]));
                    assertThat(p[1] - ground).as("the edge at (%.2f, %.2f)", p[0], p[2]).isBetween(-0.01, 0.01);
                    checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
    }

    @Test
    void noVertexLiesInsideAnotherEdgeAndNoTriangleIsASliver() {
        // over the core (its junctions, kerbs and the square) and a tile border
        double x0 = village.square().x() - 70;
        double z0 = village.square().z() - 70;
        double size = 140;
        List<double[]> local = new ArrayList<>();
        for (double[] t : TRIANGLES) {
            if (t[0] > x0 && t[0] < x0 + size && t[2] > z0 && t[2] < z0 + size) {
                local.add(t);
            }
        }
        assertThat(local.size()).isGreaterThan(1000);
        Map<Long, List<double[]>> edges = new HashMap<>();
        double cellSize = 1;
        for (double[] t : local) {
            // no sliver: a triangle's smallest height above its longest side
            double area = area(t);
            double longest = 0;
            for (int k = 0; k < 3; k++) {
                int m = (k + 1) % 3;
                longest = Math.max(longest, Math.sqrt(sq(t[3 * m] - t[3 * k]) + sq(t[3 * m + 1] - t[3 * k + 1])
                    + sq(t[3 * m + 2] - t[3 * k + 2])));
                double[] e = {t[3 * k], t[3 * k + 1], t[3 * k + 2], t[3 * m], t[3 * m + 1], t[3 * m + 2]};
                long cx = (long) Math.floor((e[0] + e[3]) / 2 / cellSize);
                long cz = (long) Math.floor((e[2] + e[5]) / 2 / cellSize);
                edges.computeIfAbsent(cx * 73856093L ^ cz, q -> new ArrayList<>()).add(e);
            }
            assertThat(2 * area / longest).as("a triangle's height").isGreaterThan(1e-3);
        }
        for (double[] t : local) {
            for (int k = 0; k < 3; k++) {
                double px = t[3 * k];
                double py = t[3 * k + 1];
                double pz = t[3 * k + 2];
                long cx = (long) Math.floor(px / cellSize);
                long cz = (long) Math.floor(pz / cellSize);
                for (long i = cx - 2; i <= cx + 2; i++) {
                    for (long j = cz - 2; j <= cz + 2; j++) {
                        for (double[] e : edges.getOrDefault(i * 73856093L ^ j, List.of())) {
                            assertThat(insideEdge(px, py, pz, e)).as("a T-junction at (%.3f, %.3f, %.3f)", px, py, pz)
                                .isFalse();
                        }
                    }
                }
            }
        }
    }

    /** Whether p lies strictly inside edge e (not at its ends). */
    private static boolean insideEdge(double px, double py, double pz, double[] e) {
        double ex = e[3] - e[0];
        double ey = e[4] - e[1];
        double ez = e[5] - e[2];
        double l2 = ex * ex + ey * ey + ez * ez;
        if (l2 < 1e-12) {
            return false;
        }
        double t = ((px - e[0]) * ex + (py - e[1]) * ey + (pz - e[2]) * ez) / l2;
        double l = Math.sqrt(l2);
        if (t * l < WELD || (1 - t) * l < WELD) {
            return false;
        }
        double dx = e[0] + t * ex - px;
        double dy = e[1] + t * ey - py;
        double dz = e[2] + t * ez - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) < 1e-5;
    }

    private static double area(double[] t) {
        double ux = t[3] - t[0];
        double uy = t[4] - t[1];
        double uz = t[5] - t[2];
        double vx = t[6] - t[0];
        double vy = t[7] - t[1];
        double vz = t[8] - t[2];
        return 0.5 * Math.sqrt(sq(uy * vz - uz * vy) + sq(uz * vx - ux * vz) + sq(ux * vy - uy * vx));
    }

    private static double sq(double v) {
        return v * v;
    }

    @Test
    void verticesAcrossATilesBorderAreTheSame() {
        // a tile's surface vertices on its east border are its neighbour's on its west border
        int matched = 0;
        Map<Long, RoadMeshes.Tile> byKey = new HashMap<>();
        for (RoadMeshes.Tile t : tiles) {
            byKey.put((long) t.i() << 32 ^ (t.j() & 0xffffffffL), t);
        }
        double size = village.parameters().surface().tile();
        for (RoadMeshes.Tile a : tiles) {
            RoadMeshes.Tile b = byKey.get((long) (a.i() + 1) << 32 ^ (a.j() & 0xffffffffL));
            if (b == null) {
                continue;
            }
            List<double[]> mine = border(a, a.x() + size);
            List<double[]> theirs = border(b, b.x());
            assertThat(mine.size()).isEqualTo(theirs.size());
            for (double[] p : mine) {
                boolean found = false;
                for (double[] q : theirs) {
                    found |= Math.abs(p[1] - q[1]) < WELD && Math.abs(p[2] - q[2]) < WELD;
                }
                assertThat(found).isTrue();
                matched++;
            }
        }
        assertThat(matched).isGreaterThan(10);
    }

    /** A tile's distinct surface vertex positions on the line x = {@code x}. */
    private static List<double[]> border(RoadMeshes.Tile tile, double x) {
        List<double[]> out = new ArrayList<>();
        ProceduralMesh mesh = tile.mesh();
        float[] p = mesh.positions();
        for (int s = 0; s <= RoadMeshes.DIRT; s++) {
            for (int t = mesh.submeshStart(s); t < mesh.submeshEnd(s); t++) {
                int v = mesh.indices()[t];
                double wx = tile.x() + p[3 * v];
                if (Math.abs(wx - x) < WELD) {
                    double[] q = {wx, tile.y() + p[3 * v + 1], tile.z() + p[3 * v + 2]};
                    boolean seen = false;
                    for (double[] o : out) {
                        seen |= Math.abs(o[1] - q[1]) < WELD && Math.abs(o[2] - q[2]) < WELD;
                    }
                    if (!seen) {
                        out.add(q);
                    }
                }
            }
        }
        return out;
    }

    @Test
    void junctionsAreRoundedAndTheirHeightsBlend() {
        // a lane meeting a main road at right angles, both level at 100 m and 101 m where they meet
        SettlementParameters p = SettlementParameters.DEFAULTS;
        Road main = new Road(0, RoadKind.MAIN, RoadSurface.COBBLES, 6, new double[] {-50, 0, 50},
            new double[] {100, 100, 100}, new double[] {0, 0, 0}, Settlement.NONE, Settlement.NONE);
        Road lane = new Road(1, RoadKind.LANE, RoadSurface.COBBLES, 4, new double[] {0, 0, 0},
            new double[] {100, 100.5, 101}, new double[] {0, 20, 40}, 0, Settlement.NONE);
        Settlement.Square far = new Settlement.Square(5000, 0, 5000, 0, 10, 10, 0, 0);
        RoadField field = new RoadField(List.of(main, lane), far, p, -100, -100, 200);
        RoadField.Sample s = new RoadField.Sample();
        // the inner corner: 1 m beyond both roads' edges
        field.sample(4, 4, s);
        double plain = Math.min(Math.abs(4) - 2, 4 - 3);
        assertThat(s.paved).as("a filleted corner").isLessThan(plain - 0.5);
        // far from the junction, the plain distance
        field.sample(30, 10, s);
        assertThat(s.paved).isCloseTo(7, org.assertj.core.data.Offset.offset(1e-9));
        // heights: the main road's crown (with its camber) along it, the lane's climbing away, continuous between
        field.sample(-30, 1, s);
        assertThat(s.height).isCloseTo(100 - p.surface().camber() * 1, org.assertj.core.data.Offset.offset(1e-6));
        double previous = Double.NaN;
        for (double z = 0; z <= 20; z += 0.25) {
            field.sample(0.5, z, s);
            if (!Double.isNaN(previous)) {
                assertThat(Math.abs(s.height - previous)).isLessThan(0.05);
            }
            previous = s.height;
        }
        // the junction's mesh: rounded kerbs follow the corner (kerb vertices beside the filleted edge)
        assertThat(TRIANGLES.stream().filter(t -> t[9] == RoadMeshes.KERB).count()).isGreaterThan(100);
        assertThat(TRIANGLES.stream().filter(t -> t[9] == RoadMeshes.STEPS).count()).isGreaterThan(10);
        assertThat(RoadField.smin(1, 5, 0)).isEqualTo(1);
        assertThat(field.reach()).isGreaterThan(5);
    }
}
