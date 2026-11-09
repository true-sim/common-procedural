package com.truesim.common.procedural.settlement;

import com.truesim.common.math.geometry.Polygons;

import com.truesim.common.math.Maths;

import com.truesim.common.math.curve.Polyline2;

import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.settlement.Settlement.Road;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A settlement's road network as meshes, watertight: one surface over every road, junction and the square, kerbs on
 * its edge in the core, stone steps where footpaths are steep, in world-aligned square tiles for streaming.
 *
 * <p><b>The surface</b> is the paved region of the network's distance field ({@link RoadField}): merged at junctions
 * (rounded by the classes' fillets: kerb radii in the core, aprons where tracks join), its height the roads' crowns
 * and camber blended across junctions. It is triangulated by marching triangles over a world-aligned grid of
 * {@link SettlementParameters.Surface#cell}: every cell split along one diagonal, each triangle clipped to the paved
 * side. A vertex is a grid node or a crossing on a grid edge, computed from that edge alone (its nodes' field values,
 * in a fixed order), so neighbouring cells and tiles make it bit for bit the same: no cracks, no T-junctions, no
 * overlaps. Field values within a twentieth of a cell of zero are pushed off it, so crossings never fall on a node
 * (no slivers).
 *
 * <p><b>Kerbs</b> stand on the surface's edge where it is cobbled: their inner foot is the surface's own edge, their
 * face, top and outer face follow it round every rounded corner, out along the field's gradient; where the cobbles end
 * (a road leaving the core) a kerb is closed by an end. <b>Steps</b> meet the surface where it is cut square across a
 * footpath: their first and last risers stand on the surface's own edge vertices there.
 *
 * <p>The terrain meets the surface's edge: {@link Grading#height} is the surface's height there. Positions are
 * relative to each tile's corner; submeshes are the five material slots, always present. Texture coordinates are
 * planar: from above on the surface and treads, along and up on the kerbs' faces and the risers.
 */
public final class RoadMeshes {

    /** The cobbles' slot: the square, the core's main road and lanes. */
    public static final int COBBLES = 0;
    /** The gravel's slot: the main road and lanes outside the core, farm tracks. */
    public static final int GRAVEL = 1;
    /** Beaten earth's slot: footpaths. */
    public static final int DIRT = 2;
    /** The kerbs' slot: granite. */
    public static final int KERB = 3;
    /** The steps' slot: stone. */
    public static final int STEPS = 4;
    /** How many slots. */
    public static final int SLOTS = 5;
    /** The slots' names, as recipes' material keys name them. */
    public static final List<String> SLOT_NAMES = List.of("cobbles", "gravel", "dirt", "kerb", "steps");

    /** Over how many metres inside its edge the surface rises to its lift. */
    private static final double LIFT_RAMP = 0.3;
    /** How far below the ground a kerb's and a step's outer face reach, metres. */
    private static final double FOOT = 0.25;
    /** Field values nearer zero than this share of a cell are pushed off it. */
    private static final double NUDGE = 0.05;
    /** The offset of grid indices, keeping keys positive. */
    private static final long OFFSET = 1L << 30;

    /**
     * A tile's mesh: tile ({@code i}, {@code j}) covers x from {@code i * tile}, z from {@code j * tile}; its mesh's
     * positions are relative to ({@code x}, {@code y}, {@code z}).
     *
     * @param i the tile's column
     * @param j the tile's row
     * @param x its origin, east (the tile's corner)
     * @param y its origin's height (the lowest point's, whole metres)
     * @param z its origin, south
     * @param mesh its surfaces, a submesh per slot
     */
    public record Tile(int i, int j, double x, double y, double z, ProceduralMesh mesh) {
    }

    /** A tile's geometry: vertices (world positions and attributes) and triangles per slot. */
    private static final class Part {
        final int i;
        final int j;
        final double x0;
        final double z0;
        double[] v = new double[11 * 256];
        int vertices;
        final int[][] triangles = new int[SLOTS][];
        final int[] counts = new int[SLOTS];
        /** Surface vertices by grid key. */
        final Map<Long, Integer> keyed = new HashMap<>();
        /** The surface's edge, where cobbled: pairs of surface vertex keys, inside on the left going a to b. */
        final List<long[]> edges = new ArrayList<>();
        double minY = Double.MAX_VALUE;

        Part(int i, int j, double tile) {
            this.i = i;
            this.j = j;
            x0 = i * tile;
            z0 = j * tile;
            for (int s = 0; s < SLOTS; s++) {
                triangles[s] = new int[3 * 64];
            }
        }

        int vertex(double px, double py, double pz, double nx, double ny, double nz, double tx, double ty, double tz,
            double u, double w) {
            if (11 * (vertices + 1) > v.length) {
                v = Arrays.copyOf(v, v.length * 2);
            }
            int o = 11 * vertices;
            v[o] = px;
            v[o + 1] = py;
            v[o + 2] = pz;
            v[o + 3] = nx;
            v[o + 4] = ny;
            v[o + 5] = nz;
            v[o + 6] = tx;
            v[o + 7] = ty;
            v[o + 8] = tz;
            v[o + 9] = u;
            v[o + 10] = w;
            minY = Math.min(minY, py);
            return vertices++;
        }

        void triangle(int slot, int a, int b, int c) {
            int[] t = triangles[slot];
            if (3 * (counts[slot] + 1) > t.length) {
                triangles[slot] = t = Arrays.copyOf(t, t.length * 2);
            }
            t[3 * counts[slot]] = a;
            t[3 * counts[slot] + 1] = b;
            t[3 * counts[slot] + 2] = c;
            counts[slot]++;
        }

        double x(int k) {
            return v[11 * k];
        }

        double y(int k) {
            return v[11 * k + 1];
        }

        double z(int k) {
            return v[11 * k + 2];
        }
    }

    private final Settlement settlement;
    private final SettlementParameters.Surface surface;
    private final RoadField field;
    private final double cell;
    private final int perTile;
    private final double tile;
    private final RoadField.Sample sample = new RoadField.Sample();
    private final Map<Long, Part> parts = new TreeMap<>();

    private RoadMeshes(Settlement settlement) {
        this.settlement = settlement;
        this.surface = settlement.parameters().surface();
        this.field = settlement.grading().field();
        this.cell = surface.cell();
        this.tile = surface.tile();
        this.perTile = (int) Math.round(tile / cell);
    }

    /** The road meshes of {@code settlement}, by tile, in row-major order of their tiles. */
    public static List<Tile> build(Settlement settlement) {
        RoadMeshes meshes = new RoadMeshes(settlement);
        meshes.surface();
        meshes.kerbs();
        for (Road r : settlement.roads()) {
            meshes.steps(r);
        }
        return meshes.write();
    }

    // -------------------------------------------------------------------------------------------------- the surface

    /** The cells the paved region may reach, by tile. */
    private TreeMap<Long, TreeSet<Long>> cells() {
        TreeMap<Long, TreeSet<Long>> byTile = new TreeMap<>();
        for (Road r : settlement.roads()) {
            double reach = r.width() / 2 + field.fillet(r.id()) + 2 * cell;
            for (int i = 1; i < r.count(); i++) {
                if (field.steep(r.id(), i - 1)) {
                    continue;
                }
                add(byTile, Math.min(r.x()[i - 1], r.x()[i]) - reach, Math.max(r.x()[i - 1], r.x()[i]) + reach,
                    Math.min(r.z()[i - 1], r.z()[i]) - reach, Math.max(r.z()[i - 1], r.z()[i]) + reach);
            }
        }
        double[] c = settlement.square().corners();
        double reach = settlement.parameters().lane().fillet() + 2 * cell;
        add(byTile, Math.min(Math.min(c[0], c[2]), Math.min(c[4], c[6])) - reach,
            Math.max(Math.max(c[0], c[2]), Math.max(c[4], c[6])) + reach,
            Math.min(Math.min(c[1], c[3]), Math.min(c[5], c[7])) - reach,
            Math.max(Math.max(c[1], c[3]), Math.max(c[5], c[7])) + reach);
        return byTile;
    }

    private void add(TreeMap<Long, TreeSet<Long>> byTile, double minX, double maxX, double minZ, double maxZ) {
        int i0 = (int) Math.floor(minX / cell);
        int i1 = (int) Math.floor(maxX / cell);
        int j0 = (int) Math.floor(minZ / cell);
        int j1 = (int) Math.floor(maxZ / cell);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                long t = key(Math.floorDiv(i, perTile), Math.floorDiv(j, perTile));
                byTile.computeIfAbsent(t, k -> new TreeSet<>()).add(key(i, j));
            }
        }
    }

    private static long key(int i, int j) {
        return (j + OFFSET) << 31 | (i + OFFSET);
    }

    private static int keyI(long key) {
        return (int) ((key & ((1L << 31) - 1)) - OFFSET);
    }

    private static int keyJ(long key) {
        return (int) ((key >>> 31) - OFFSET);
    }

    /** The paved field at grid node (i, j), pushed off zero. */
    private double value(int i, int j, Map<Long, Double> cache) {
        long k = key(i, j);
        Double v = cache.get(k);
        if (v == null) {
            field.sample(i * cell, j * cell, sample);
            double d = sample.paved;
            double nudge = NUDGE * cell;
            if (Math.abs(d) < nudge) {
                d = d < 0 ? -nudge : nudge;
            }
            v = d;
            cache.put(k, v);
        }
        return v;
    }

    private void surface() {
        Map<Long, Double> cache = new HashMap<>();
        for (Map.Entry<Long, TreeSet<Long>> e : cells().entrySet()) {
            Part part = part(keyI(e.getKey()), keyJ(e.getKey()));
            for (long c : e.getValue()) {
                int i = keyI(c);
                int j = keyJ(c);
                // the cell's nodes: 0 (i, j), 1 (i + 1, j), 2 (i + 1, j + 1), 3 (i, j + 1); split along 0-2
                int[][] n = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                double[] d = new double[4];
                for (int k = 0; k < 4; k++) {
                    d[k] = value(n[k][0], n[k][1], cache);
                }
                clip(part, n, d, 0, 1, 2);
                clip(part, n, d, 0, 2, 3);
            }
        }
    }

    private Part part(int i, int j) {
        return parts.computeIfAbsent(key(i, j), k -> new Part(i, j, tile));
    }

    /**
     * Triangle (a, b, c) of a cell (counter-clockwise from above as listed: x east, z south, y up, so a b c turns
     * from +x toward +z) clipped to the paved side, triangulated, its edge on the field's zero recorded.
     */
    private void clip(Part part, int[][] n, double[] d, int a, int b, int c) {
        int[] corner = {a, b, c};
        List<Long> poly = new ArrayList<>(4);
        List<Boolean> onEdge = new ArrayList<>(4);
        for (int k = 0; k < 3; k++) {
            int p = corner[k];
            int q = corner[(k + 1) % 3];
            if (d[p] < 0) {
                poly.add(nodeVertex(part, n[p][0], n[p][1]));
                onEdge.add(false);
            }
            if (d[p] < 0 != d[q] < 0) {
                poly.add(edgeVertex(part, n[p][0], n[p][1], d[p], n[q][0], n[q][1], d[q]));
                onEdge.add(true);
            }
        }
        if (poly.size() < 3) {
            return;
        }
        // its material: the nearest road's at its middle
        double mx = 0;
        double mz = 0;
        for (long k : poly) {
            int v = part.keyed.get(k);
            mx += part.x(v);
            mz += part.z(v);
        }
        field.sample(mx / poly.size(), mz / poly.size(), sample);
        int slot = slot(sample.surface);
        int v0 = part.keyed.get(poly.get(0));
        for (int k = 1; k + 1 < poly.size(); k++) {
            triangleUp(part, slot, v0, part.keyed.get(poly.get(k)), part.keyed.get(poly.get(k + 1)));
        }
        // the edge: between two crossings next to each other round the polygon (inside on the left going round)
        for (int k = 0; k < poly.size(); k++) {
            int next = (k + 1) % poly.size();
            if (onEdge.get(k) && onEdge.get(next)) {
                part.edges.add(new long[] {poly.get(k), poly.get(next)});
            }
        }
    }

    private static int slot(RoadSurface s) {
        if (s == null) {
            return GRAVEL;
        }
        return switch (s) {
            case COBBLES -> COBBLES;
            case GRAVEL -> GRAVEL;
            case DIRT -> DIRT;
        };
    }

    /** A triangle wound counter-clockwise seen from above. */
    private static void triangleUp(Part part, int slot, int a, int b, int c) {
        double cross = (part.z(b) - part.z(a)) * (part.x(c) - part.x(a))
            - (part.x(b) - part.x(a)) * (part.z(c) - part.z(a));
        if (cross >= 0) {
            part.triangle(slot, a, b, c);
        } else {
            part.triangle(slot, a, c, b);
        }
    }

    private long nodeVertex(Part part, int i, int j) {
        long k = key(i, j) << 2 | 3;
        if (!part.keyed.containsKey(k)) {
            part.keyed.put(k, surfaceVertex(part, i * cell, j * cell));
        }
        return k;
    }

    /** The crossing on the grid edge between nodes p and q: computed from the edge alone, in its nodes' order. */
    private long edgeVertex(Part part, int pi, int pj, double dp, int qi, int qj, double dq) {
        boolean swap = qj < pj || qj == pj && qi < pi;
        if (swap) {
            int ti = pi;
            int tj = pj;
            double td = dp;
            pi = qi;
            pj = qj;
            dp = dq;
            qi = ti;
            qj = tj;
            dq = td;
        }
        int dir = qj == pj ? 0 : qi == pi ? 1 : 2;
        long k = key(pi, pj) << 2 | dir;
        if (!part.keyed.containsKey(k)) {
            double t = crossing(pi * cell, pj * cell, dp, qi * cell, qj * cell, dq);
            double x = (pi + t * (qi - pi)) * cell;
            double z = (pj + t * (qj - pj)) * cell;
            part.keyed.put(k, surfaceVertex(part, x, z));
        }
        return k;
    }

    /**
     * Where along the grid edge from p to q the paved field crosses zero: by bisection of the field itself (the
     * edge's nodes' values only start it), so the surface's edge lies on the field's (and the terrain's) edge; kept a
     * twentieth of a cell off either node (no slivers). The edge alone decides it: the same from either cell or tile.
     */
    private double crossing(double px, double pz, double dp, double qx, double qz, double dq) {
        double lo = 0;
        double hi = 1;
        boolean insideAtLo = dp < 0;
        for (int it = 0; it < 12; it++) {
            double m = (lo + hi) / 2;
            field.sample(px + m * (qx - px), pz + m * (qz - pz), sample);
            if (sample.paved < 0 == insideAtLo) {
                lo = m;
            } else {
                hi = m;
            }
        }
        return Math.clamp((lo + hi) / 2, NUDGE, 1 - NUDGE);
    }

    /** A vertex of the surface at (x, z): its height the field's, its normal from the field's slope. */
    private int surfaceVertex(Part part, double x, double z) {
        double y = height(x, z);
        // lifted off the terrain inside the edge (no shared depth with it), on it at the edge
        y += surface.lift() * Maths.smoothstep(0, 1, -sample.paved / LIFT_RAMP);
        double e = 0.2;
        double gx = (height(x + e, z) - height(x - e, z)) / (2 * e);
        double gz = (height(x, z + e) - height(x, z - e)) / (2 * e);
        double l = Math.sqrt(gx * gx + gz * gz + 1);
        double nx = -gx / l;
        double ny = 1 / l;
        double nz = -gz / l;
        // the tangent: +x in the surface (u along x, v along -z)
        double tx = 1 - nx * nx;
        double ty = -nx * ny;
        double tz = -nx * nz;
        double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double texture = surface.texture();
        return part.vertex(x, y, z, nx, ny, nz, tx / tl, ty / tl, tz / tl, (x - part.x0) / texture,
            -(z - part.z0) / texture);
    }

    private double height(double x, double z) {
        field.sample(x, z, sample);
        return sample.height;
    }

    // ----------------------------------------------------------------------------------------------------- kerbs

    /** Kerbs along the surface's edge where it is cobbled, in each tile, closed where the cobbles end. */
    private void kerbs() {
        for (Part part : parts.values()) {
            // the edge's cobbled pieces, and how many each vertex has (1: a kerb ends there)
            List<long[]> kerbed = new ArrayList<>();
            Map<Long, Integer> ends = new HashMap<>();
            for (long[] e : part.edges) {
                int a = part.keyed.get(e[0]);
                int b = part.keyed.get(e[1]);
                double mx = (part.x(a) + part.x(b)) / 2;
                double mz = (part.z(a) + part.z(b)) / 2;
                double[] n = outward(mx, mz);
                field.sample(mx - n[0] * 0.15, mz - n[1] * 0.15, sample);
                if (sample.surface == RoadSurface.COBBLES) {
                    kerbed.add(e);
                    ends.merge(e[0], 1, Integer::sum);
                    ends.merge(e[1], 1, Integer::sum);
                }
            }
            for (long[] e : kerbed) {
                kerbPiece(part, part.keyed.get(e[0]), part.keyed.get(e[1]));
            }
            for (Map.Entry<Long, Integer> end : ends.entrySet()) {
                int v = part.keyed.get(end.getKey());
                if (end.getValue() == 1 && !onBorder(part, part.x(v), part.z(v))) {
                    kerbEnd(part, v, kerbed, end.getKey());
                }
            }
        }
    }

    /** Whether ({@code x}, {@code z}) lies on its tile's border (where a kerb goes on in the next tile). */
    private boolean onBorder(Part part, double x, double z) {
        double eps = 1e-9;
        return Math.abs(x - part.x0) < eps || Math.abs(x - part.x0 - tile) < eps || Math.abs(z - part.z0) < eps
            || Math.abs(z - part.z0 - tile) < eps;
    }

    /** The unit outward direction of the paved field at ({@code x}, {@code z}) (its gradient), x then z. */
    private double[] outward(double x, double z) {
        double e = 0.05;
        field.sample(x + e, z, sample);
        double px = sample.paved;
        field.sample(x - e, z, sample);
        double mx = sample.paved;
        field.sample(x, z + e, sample);
        double pz = sample.paved;
        field.sample(x, z - e, sample);
        double mz = sample.paved;
        double gx = px - mx;
        double gz = pz - mz;
        double l = Math.sqrt(gx * gx + gz * gz);
        return l > 0 ? new double[] {gx / l, gz / l} : new double[] {1, 0};
    }

    /** A kerb's profile at surface vertex v: inner foot (v), inner top, outer top, outer foot. */
    private double[][] profile(Part part, int v) {
        double x = part.x(v);
        double y = part.y(v);
        double z = part.z(v);
        double[] n = outward(x, z);
        double kw = surface.kerbWidth();
        double kh = surface.kerbHeight();
        return new double[][] {{x, y, z}, {x, y + kh, z}, {x + n[0] * kw, y + kh, z + n[1] * kw},
            {x + n[0] * kw, y - FOOT, z + n[1] * kw}, {n[0], 0, n[1]}};
    }

    private void kerbPiece(Part part, int a, int b) {
        double[][] pa = profile(part, a);
        double[][] pb = profile(part, b);
        double[] out = {(pa[4][0] + pb[4][0]) / 2, 0, (pa[4][1] + pb[4][1]) / 2};
        // inner face (toward the road), top, outer face
        quad(part, KERB, pa[0], pb[0], pb[1], pa[1], -out[0], 0, -out[2]);
        quad(part, KERB, pa[1], pb[1], pb[2], pa[2], 0, 1, 0);
        quad(part, KERB, pa[2], pb[2], pb[3], pa[3], out[0], 0, out[2]);
    }

    /** Closes a kerb's end at surface vertex v, facing away along the edge from its kerbed piece. */
    private void kerbEnd(Part part, int v, List<long[]> kerbed, long key) {
        double[][] p = profile(part, v);
        int other = -1;
        for (long[] e : kerbed) {
            if (e[0] == key) {
                other = part.keyed.get(e[1]);
            } else if (e[1] == key) {
                other = part.keyed.get(e[0]);
            }
        }
        double fx = part.x(v) - part.x(other);
        double fz = part.z(v) - part.z(other);
        quad(part, KERB, p[0], p[1], p[2], p[3], fx, 0, fz);
    }

    // ----------------------------------------------------------------------------------------------------- steps

    /**
     * Stone steps up each flight of a footpath: a tread per rise of {@link SettlementParameters.Surface#stepRise},
     * risers between, sides down into the ground. The first and last risers stand on the surface's own vertices
     * where it is cut square across the path; the treads' cross-sections take the bottom cut's vertices, then (from
     * the flight's middle tread, which joins the two) the top cut's, so every edge is shared.
     */
    private void steps(Road r) {
        int m = r.count();
        int i = 0;
        while (i < m - 1) {
            if (!field.steep(r.id(), i)) {
                i++;
                continue;
            }
            int a = i;
            while (i < m - 1 && field.steep(r.id(), i)) {
                i++;
            }
            flight(r, a, i);
        }
    }

    private void flight(Road r, int a, int b) {
        double hw = r.width() / 2;
        // the cuts: the surface's edge vertices on the line square across the paved segment beside the flight
        List<double[]> bottom = cut(r, a, a - 1);
        List<double[]> top = cut(r, b, b + 1);
        if (bottom.size() < 2 || top.size() < 2) {
            return;
        }
        double ya = r.y()[a];
        double yb = r.y()[b];
        double rise = yb - ya;
        int count = Math.max(2, (int) Math.ceil(Math.abs(rise) / surface.stepRise()));
        Polyline2 line = new Polyline2(Arrays.copyOfRange(r.x(), a, b + 1), Arrays.copyOfRange(r.z(), a, b + 1));
        double length = line.length();
        // tread k at height h[k]: up, each a rise above the last; down, the first level with the path
        double[] h = new double[count];
        for (int k = 0; k < count; k++) {
            h[k] = ya + rise * (k + (rise > 0 ? 1 : 0)) / count;
        }
        int middle = count / 2;
        // boundary k's cross-section: the bottom cut's across-offsets up to the middle tread, the top cut's after
        double[][][] at = new double[count + 1][][];
        double[] p = new double[4];
        for (int k = 0; k <= count; k++) {
            List<double[]> set = k <= middle ? bottom : top;
            if (k == 0) {
                at[k] = positions(bottom);
            } else if (k == count) {
                at[k] = positions(top);
            } else {
                line.at(length * k / count, p);
                at[k] = new double[set.size()][];
                for (int q = 0; q < set.size(); q++) {
                    double across = set.get(q)[3];
                    at[k][q] = new double[] {p[0] + p[3] * across, 0, p[1] - p[2] * across};
                }
            }
        }
        Part part = part(Math.floorDiv((int) Math.floor(r.x()[a] / cell), perTile),
            Math.floorDiv((int) Math.floor(r.z()[a] / cell), perTile));
        double foot = Math.min(ya, yb) - surface.stepRise() - FOOT;
        double[] fwd = {r.x()[b] - r.x()[a], 0, r.z()[b] - r.z()[a]};
        for (int k = 0; k <= count; k++) {
            // the riser at boundary k: from the height before it (the cut's own, at the bottom) to the one after
            double[][] row = at[k];
            double[] lo = new double[row.length];
            double[] hi = new double[row.length];
            for (int q = 0; q < row.length; q++) {
                lo[q] = k == 0 ? row[q][1] : h[k - 1];
                hi[q] = k == count ? row[q][1] : h[k];
            }
            for (int q = 0; q + 1 < row.length; q++) {
                double[] l0 = {row[q][0], lo[q], row[q][2]};
                double[] l1 = {row[q + 1][0], lo[q + 1], row[q + 1][2]};
                double[] h1 = {row[q + 1][0], hi[q + 1], row[q + 1][2]};
                double[] h0 = {row[q][0], hi[q], row[q][2]};
                double dir = Math.signum(average(lo) - average(hi));
                if (Math.abs(average(lo) - average(hi)) > 1e-6) {
                    quad(part, STEPS, l0, l1, h1, h0, dir * fwd[0], 0, dir * fwd[2]);
                }
            }
            if (k < count) {
                // the tread from boundary k to k + 1 at h[k]: zipped between their cross-sections
                double y = h[k];
                zip(part, at[k], at[k + 1], y);
                side(part, at, k, h, foot, 0, hw);
                side(part, at, k, h, foot, at[k].length - 1, hw);
            }
        }
    }

    /**
     * The surface's edge on the cut at point {@code at} of road r (square across the paved segment toward
     * {@code beside}), left to right: the chain of the surface's edge vertices from the one nearest the cut's middle,
     * both ways, while they stay within a cell of the cut's line (its corners included), so the steps' riser
     * shares every vertex and edge of it.
     */
    private List<double[]> cut(Road r, int at, int beside) {
        double px = r.x()[at];
        double pz = r.z()[at];
        double dx = r.x()[at] - r.x()[beside];
        double dz = r.z()[at] - r.z()[beside];
        double l = Math.sqrt(dx * dx + dz * dz);
        dx /= l;
        dz /= l;
        double hw = r.width() / 2;
        double near = hw + 2 * cell;
        // the edge's segments about the cut, as world positions (the same doubles in every tile)
        Map<String, List<double[]>> next = new HashMap<>();
        for (Part part : parts.values()) {
            if (px < part.x0 - near || px > part.x0 + tile + near || pz < part.z0 - near
                || pz > part.z0 + tile + near) {
                continue;
            }
            for (long[] e : part.edges) {
                int va = part.keyed.get(e[0]);
                int vb = part.keyed.get(e[1]);
                double[] pa = {part.x(va), part.y(va), part.z(va)};
                double[] pb = {part.x(vb), part.y(vb), part.z(vb)};
                if (Polygons.length(pa[0] - px, pa[2] - pz) > near && Polygons.length(pb[0] - px, pb[2] - pz) > near) {
                    continue;
                }
                next.computeIfAbsent(name(pa), k -> new ArrayList<>()).add(pb);
                next.computeIfAbsent(name(pb), k -> new ArrayList<>()).add(pa);
            }
        }
        double band = cell;
        double[] start = null;
        double best = Double.MAX_VALUE;
        for (List<double[]> ends : next.values()) {
            for (double[] q : ends) {
                double along = (q[0] - px) * dx + (q[2] - pz) * dz;
                double across = (q[0] - px) * dz - (q[2] - pz) * dx;
                if (Math.abs(along) < band && Math.abs(across) < best) {
                    best = Math.abs(across);
                    start = q;
                }
            }
        }
        List<double[]> chain = new ArrayList<>();
        if (start == null) {
            return chain;
        }
        chain.add(start);
        for (int side = 0; side < 2; side++) {
            double[] previous = null;
            double[] current = start;
            while (true) {
                double[] step = null;
                for (double[] q : next.getOrDefault(name(current), List.of())) {
                    if (previous != null && same(q, previous) || contains(chain, q[0], q[2])) {
                        continue;
                    }
                    double along = (q[0] - px) * dx + (q[2] - pz) * dz;
                    double across = (q[0] - px) * dz - (q[2] - pz) * dx;
                    double from = (current[0] - px) * dz - (current[2] - pz) * dx;
                    // outward across the path (a corner's included), never along its side
                    boolean outward = side == 0 ? across > from + 1e-3 : across < from - 1e-3;
                    if (Math.abs(along) < band && Math.abs(across) <= hw + cell && outward) {
                        step = q;
                        break;
                    }
                }
                if (step == null) {
                    break;
                }
                if (side == 0) {
                    chain.add(step);
                } else {
                    chain.addFirst(step);
                }
                previous = current;
                current = step;
            }
        }
        // left (+across) first
        List<double[]> out = new ArrayList<>();
        for (double[] q : chain) {
            out.add(new double[] {q[0], q[1], q[2], (q[0] - px) * dz - (q[2] - pz) * dx});
        }
        if (out.size() > 1 && out.getFirst()[3] < out.getLast()[3]) {
            java.util.Collections.reverse(out);
        }
        return out;
    }

    private static String name(double[] q) {
        return Double.doubleToLongBits(q[0]) + ":" + Double.doubleToLongBits(q[2]);
    }

    private static boolean same(double[] a, double[] b) {
        return a[0] == b[0] && a[2] == b[2];
    }

    private static boolean contains(List<double[]> list, double x, double z) {
        for (double[] q : list) {
            if (q[0] == x && q[2] == z) {
                return true;
            }
        }
        return false;
    }

    private static double[][] positions(List<double[]> set) {
        double[][] out = new double[set.size()][];
        for (int q = 0; q < set.size(); q++) {
            out[q] = new double[] {set.get(q)[0], set.get(q)[1], set.get(q)[2]};
        }
        return out;
    }

    private static double average(double[] values) {
        double s = 0;
        for (double v : values) {
            s += v;
        }
        return s / values.length;
    }

    /** A tread at height y between cross-sections a and b (left to right each), triangulated by zipping them. */
    private void zip(Part part, double[][] a, double[][] b, double y) {
        int i = 0;
        int j = 0;
        while (i < a.length - 1 || j < b.length - 1) {
            double ta = i < a.length - 1 ? (double) (i + 1) / (a.length - 1) : 2;
            double tb = j < b.length - 1 ? (double) (j + 1) / (b.length - 1) : 2;
            double[] p0 = {a[i][0], y, a[i][2]};
            double[] p1 = {b[j][0], y, b[j][2]};
            if (ta <= tb) {
                double[] p2 = {a[i + 1][0], y, a[i + 1][2]};
                triangle(part, STEPS, p0, p1, p2, 0, 1, 0);
                i++;
            } else {
                double[] p2 = {b[j + 1][0], y, b[j + 1][2]};
                triangle(part, STEPS, p0, p1, p2, 0, 1, 0);
                j++;
            }
        }
    }

    /**
     * Tread k's side (q: its first or last cross-section point), from its height down to {@code foot}: a column
     * holding every height at its two ends (the risers' ends), fanned from its middle.
     */
    private void side(Part part, double[][][] at, int k, double[] h, double foot, int q, double hw) {
        int count = h.length;
        double[] l = at[k][q == 0 ? 0 : at[k].length - 1];
        double[] r = at[k + 1][q == 0 ? 0 : at[k + 1].length - 1];
        double top = h[k];
        // heights on each end: the foot, the tread, and the neighbouring tread or cut where lower than this one
        List<double[]> ring = new ArrayList<>();
        ring.add(new double[] {l[0], foot, l[2]});
        double below = k == 0 ? l[1] : h[k - 1];
        if (below > foot && below < top) {
            ring.add(new double[] {l[0], below, l[2]});
        }
        ring.add(new double[] {l[0], top, l[2]});
        ring.add(new double[] {r[0], top, r[2]});
        double after = k + 1 == count ? r[1] : h[k + 1];
        if (after > foot && after < top) {
            ring.add(new double[] {r[0], after, r[2]});
        }
        ring.add(new double[] {r[0], foot, r[2]});
        double cx = 0;
        double cy = 0;
        double cz = 0;
        for (double[] p : ring) {
            cx += p[0];
            cy += p[1];
            cz += p[2];
        }
        double[] c = {cx / ring.size(), cy / ring.size(), cz / ring.size()};
        // outward: across the path, away from its middle
        double[] o = at[k][at[k].length / 2];
        double ox = l[0] - o[0];
        double oz = l[2] - o[2];
        if (Math.abs(ox) + Math.abs(oz) < 1e-9) {
            ox = 1;
        }
        for (int s = 0; s < ring.size(); s++) {
            triangle(part, STEPS, ring.get(s), ring.get((s + 1) % ring.size()), c, ox, 0, oz);
        }
    }

    // ------------------------------------------------------------------------------------------------- primitives

    private void quad(Part part, int slot, double[] a, double[] b, double[] c, double[] d, double hx, double hy,
        double hz) {
        triangle(part, slot, a, b, c, hx, hy, hz);
        triangle(part, slot, a, c, d, hx, hy, hz);
    }

    /**
     * A flat triangle into {@code part}'s {@code slot}, wound to face along ({@code hx}, {@code hy}, {@code hz});
     * degenerate ones left out. Its own vertices (its normal is its own), at the given positions exactly.
     */
    private void triangle(Part part, int slot, double[] a, double[] b, double[] c, double hx, double hy, double hz) {
        double ux = b[0] - a[0];
        double uy = b[1] - a[1];
        double uz = b[2] - a[2];
        double vx = c[0] - a[0];
        double vy = c[1] - a[1];
        double vz = c[2] - a[2];
        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        // degenerate (two corners as one, as where a riser has no height at one end): nothing to draw
        if (l < 1e-12 || close(a, b) || close(b, c) || close(a, c)) {
            return;
        }
        nx /= l;
        ny /= l;
        nz /= l;
        boolean flip = nx * hx + ny * hy + nz * hz < 0;
        if (flip) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        double texture = surface.texture();
        double tx;
        double ty;
        double tz;
        int[] index = new int[3];
        double[][] corners = {a, b, c};
        if (ny > 0.5) {
            tx = 1 - nx * nx;
            ty = -nx * ny;
            tz = -nx * nz;
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            for (int k = 0; k < 3; k++) {
                double[] p = corners[k];
                index[k] = part.vertex(p[0], p[1], p[2], nx, ny, nz, tx / tl, ty / tl, tz / tl,
                    (p[0] - part.x0) / texture, -(p[2] - part.z0) / texture);
            }
        } else {
            tx = nz;
            tz = -nx;
            double tl = Math.sqrt(tx * tx + tz * tz);
            if (tl < 1e-9) {
                tx = 1;
                tz = 0;
                tl = 1;
            }
            double base = Math.floor(a[1]);
            for (int k = 0; k < 3; k++) {
                double[] p = corners[k];
                double u = ((p[0] - part.x0) * tx + (p[2] - part.z0) * tz) / tl / texture;
                index[k] = part.vertex(p[0], p[1], p[2], nx, ny, nz, tx / tl, 0, tz / tl, u, (p[1] - base) / texture);
            }
        }
        if (flip) {
            part.triangle(slot, index[0], index[2], index[1]);
        } else {
            part.triangle(slot, index[0], index[1], index[2]);
        }
    }

    private static boolean close(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < 3e-4 && Math.abs(a[1] - b[1]) < 3e-4 && Math.abs(a[2] - b[2]) < 3e-4;
    }

    private List<Tile> write() {
        List<Tile> tiles = new ArrayList<>();
        MeshWriter writer = new MeshWriter();
        for (Part part : parts.values()) {
            int total = 0;
            for (int s = 0; s < SLOTS; s++) {
                total += part.counts[s];
            }
            if (total == 0) {
                continue;
            }
            double y0 = Math.floor(part.minY);
            writer.reset();
            for (int k = 0; k < part.vertices; k++) {
                int o = 11 * k;
                writer.vertex(part.v[o] - part.x0, part.v[o + 1] - y0, part.v[o + 2] - part.z0, part.v[o + 3],
                    part.v[o + 4], part.v[o + 5], part.v[o + 6], part.v[o + 7], part.v[o + 8], part.v[o + 9],
                    part.v[o + 10]);
            }
            for (int s = 0; s < SLOTS; s++) {
                if (s > 0) {
                    writer.nextSubmesh();
                }
                int[] t = part.triangles[s];
                for (int k = 0; k < part.counts[s]; k++) {
                    writer.triangle(t[3 * k], t[3 * k + 1], t[3 * k + 2]);
                }
            }
            tiles.add(new Tile(part.i, part.j, part.x0, y0, part.z0, writer.build(0)));
        }
        return tiles;
    }
}
