package com.truesim.common.procedural.settlement;

import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.settlement.Settlement.Road;
import com.truesim.common.procedural.settlement.Settlement.Square;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A settlement's road surfaces as meshes, in world-aligned square tiles for streaming: each road's surface swept along
 * its graded centre line (its crown, a camber falling to both edges, a short skirt into the ground), the square's
 * cobbles on its plane, kerbs along the cobbled roads and round the square (open where roads meet them), and stone
 * steps where a footpath is steeper than {@link SettlementParameters.Surface#stepGrade}. The surfaces follow the same
 * profile as the {@link Grading}, so they lie on a terrain that takes it.
 *
 * <p>Each tile's mesh is in its own frame: positions relative to the tile's ({@code x}, {@code y}, {@code z}), so
 * float positions and half-float texture coordinates stay precise. Submeshes are material slots, always all five:
 * {@link #COBBLES}, {@link #GRAVEL}, {@link #DIRT}, {@link #KERB}, {@link #STEPS} (empty ones have no triangles).
 * Texture coordinates are planar, a tile every {@link SettlementParameters.Surface#texture} metres: from above on
 * the surfaces and treads, along and up on kerbs' faces and risers.
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

    /** How far a road's skirt reaches below its edge, metres. */
    private static final double SKIRT = 0.3;

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

    /** Triangles collected for one tile, per slot: world positions, normals, tangents, texture coordinates. */
    private static final class Batch {
        final int i;
        final int j;
        final double x0;
        final double z0;
        final Part[] parts = new Part[SLOTS];
        double minY = Double.MAX_VALUE;

        Batch(int i, int j, double tile) {
            this.i = i;
            this.j = j;
            x0 = i * tile;
            z0 = j * tile;
            for (int s = 0; s < SLOTS; s++) {
                parts[s] = new Part();
            }
        }
    }

    /** One slot's vertices (11 doubles each: position, normal, tangent, uv) and triangles. */
    private static final class Part {
        double[] v = new double[11 * 64];
        int vertices;
        int[] t = new int[3 * 64];
        int triangles;

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
            return vertices++;
        }

        void triangle(int a, int b, int c) {
            if (3 * (triangles + 1) > t.length) {
                t = Arrays.copyOf(t, t.length * 2);
            }
            t[3 * triangles] = a;
            t[3 * triangles + 1] = b;
            t[3 * triangles + 2] = c;
            triangles++;
        }
    }

    private final Settlement settlement;
    private final SettlementParameters.Surface surface;
    private final double tile;
    private final Map<Long, Batch> batches = new TreeMap<>();

    private RoadMeshes(Settlement settlement) {
        this.settlement = settlement;
        this.surface = settlement.parameters().surface();
        this.tile = surface.tile();
    }

    /** The road meshes of {@code settlement}, by tile, in row-major order of their tiles. */
    public static List<Tile> build(Settlement settlement) {
        RoadMeshes meshes = new RoadMeshes(settlement);
        for (Road r : settlement.roads()) {
            meshes.road(r);
        }
        meshes.square(settlement.square());
        return meshes.write();
    }

    private List<Tile> write() {
        List<Tile> tiles = new ArrayList<>();
        MeshWriter writer = new MeshWriter();
        for (Batch b : batches.values()) {
            double y0 = Math.floor(b.minY);
            writer.reset();
            for (int s = 0; s < SLOTS; s++) {
                if (s > 0) {
                    writer.nextSubmesh();
                }
                Part part = b.parts[s];
                int base = writer.vertexCount();
                for (int k = 0; k < part.vertices; k++) {
                    int o = 11 * k;
                    writer.vertex(part.v[o] - b.x0, part.v[o + 1] - y0, part.v[o + 2] - b.z0, part.v[o + 3],
                        part.v[o + 4], part.v[o + 5], part.v[o + 6], part.v[o + 7], part.v[o + 8], part.v[o + 9],
                        part.v[o + 10]);
                }
                for (int k = 0; k < part.triangles; k++) {
                    writer.triangle(base + part.t[3 * k], base + part.t[3 * k + 1], base + part.t[3 * k + 2]);
                }
            }
            tiles.add(new Tile(b.i, b.j, b.x0, y0, b.z0, writer.build(0)));
        }
        return tiles;
    }

    private Batch batch(double x, double z) {
        int i = (int) Math.floor(x / tile);
        int j = (int) Math.floor(z / tile);
        long key = ((long) j << 32) ^ (i & 0xffffffffL);
        return batches.computeIfAbsent(key, k -> new Batch(i, j, tile));
    }

    // ---------------------------------------------------------------------------------------------------------- roads

    private static int slot(RoadSurface s) {
        return switch (s) {
            case COBBLES -> COBBLES;
            case GRAVEL -> GRAVEL;
            case DIRT -> DIRT;
        };
    }

    /** One road: its surface strips (steps where steep), skirts or kerbs along its edges. */
    private void road(Road r) {
        int m = r.count();
        if (m < 2) {
            return;
        }
        double hw = r.width() / 2;
        int strips = r.width() >= 3 ? 4 : 2;
        // each point's horizontal tangent and left normal
        double[] tx = new double[m];
        double[] tz = new double[m];
        for (int i = 0; i < m; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(m - 1, i + 1);
            double dx = r.x()[b] - r.x()[a];
            double dz = r.z()[b] - r.z()[a];
            double l = Geometry.length(dx, dz);
            tx[i] = l > 0 ? dx / l : 1;
            tz[i] = l > 0 ? dz / l : 0;
        }
        // where the steps are: footpath segments steeper than the step grade
        boolean[] steep = new boolean[m - 1];
        if (r.kind() == RoadKind.FOOTPATH) {
            for (int i = 0; i + 1 < m; i++) {
                double run = Geometry.length(r.x()[i + 1] - r.x()[i], r.z()[i + 1] - r.z()[i]);
                steep[i] = run > 0 && Math.abs(r.y()[i + 1] - r.y()[i]) / run > surface.stepGrade();
            }
        }
        int slot = slot(r.surface());
        double camber = surface.camber();
        double lift = surface.lift();
        double[][] pos = new double[strips + 1][3];
        double[][] next = new double[strips + 1][3];
        for (int i = 0; i + 1 < m; i++) {
            if (steep[i]) {
                continue;
            }
            for (int k = 0; k <= strips; k++) {
                double o = -hw + 2 * hw * k / strips;
                section(r, i, tx, tz, o, camber, lift, pos[k]);
                section(r, i + 1, tx, tz, o, camber, lift, next[k]);
            }
            double cx = (r.x()[i] + r.x()[i + 1]) / 2;
            double cz = (r.z()[i] + r.z()[i + 1]) / 2;
            if (settlement.square().outside(cx, cz) < 0) {
                continue; // the square's own cobbles
            }
            Batch b = batch(cx, cz);
            for (int k = 0; k < strips; k++) {
                quad(b, slot, pos[k], next[k], next[k + 1], pos[k + 1], 0, 1, 0);
            }
            boolean kerbs = r.surface() == RoadSurface.COBBLES;
            for (int side : new int[] {-1, 1}) {
                double[] e0 = side < 0 ? pos[0] : pos[strips];
                double[] e1 = side < 0 ? next[0] : next[strips];
                double ox = side * tz[i];
                double oz = -side * tx[i];
                if (kerbs) {
                    kerb(b, r, i, e0, e1, side, tx, tz);
                } else {
                    // a skirt a little out and down, so no gap shows where the ground is not graded
                    double[] s0 = {e0[0] + side * tz[i] * 0.15, e0[1] - SKIRT, e0[2] - side * tx[i] * 0.15};
                    double[] s1 = {e1[0] + side * tz[i + 1] * 0.15, e1[1] - SKIRT, e1[2] - side * tx[i + 1] * 0.15};
                    quad(b, slot, e0, e1, s1, s0, ox, 1, oz);
                }
            }
        }
        if (r.kind() == RoadKind.FOOTPATH) {
            steps(r, steep, tx, tz);
        }
    }

    /** The surface's point at offset {@code o} (left positive) across point {@code i}: crown less the camber. */
    private static void section(Road r, int i, double[] tx, double[] tz, double o, double camber, double lift,
        double[] out) {
        out[0] = r.x()[i] + tz[i] * o;
        out[1] = r.y()[i] - camber * Math.abs(o) + lift;
        out[2] = r.z()[i] - tx[i] * o;
    }

    /**
     * A kerb along one edge of a segment: its inner face up from the cobbles, its top, its outer face down into the
     * ground; left out where another road's surface covers it (a junction's mouth).
     */
    private void kerb(Batch b, Road r, int i, double[] e0, double[] e1, int side, double[] tx, double[] tz) {
        double kw = surface.kerbWidth();
        double kh = surface.kerbHeight();
        double mx = (e0[0] + e1[0]) / 2 + side * tz[i] * kw / 2;
        double mz = (e0[2] + e1[2]) / 2 - side * tx[i] * kw / 2;
        for (Road other : settlement.roads()) {
            if (other != r && other.distance(mx, mz) < other.width() / 2 - 0.05) {
                return;
            }
        }
        if (settlement.square().outside(mx, mz) < kw) {
            return;
        }
        double[] in0 = e0;
        double[] in1 = e1;
        double[] top0 = {e0[0], e0[1] + kh, e0[2]};
        double[] top1 = {e1[0], e1[1] + kh, e1[2]};
        double[] out0 = {e0[0] + side * tz[i] * kw, e0[1] + kh, e0[2] - side * tx[i] * kw};
        double[] out1 = {e1[0] + side * tz[i + 1] * kw, e1[1] + kh, e1[2] - side * tx[i + 1] * kw};
        double[] low0 = {out0[0], e0[1] - SKIRT, out0[2]};
        double[] low1 = {out1[0], e1[1] - SKIRT, out1[2]};
        double nx = side * tz[i];
        double nz = -side * tx[i];
        quad(b, KERB, in0, in1, top1, top0, -nx, 0, -nz);
        quad(b, KERB, top0, top1, out1, out0, 0, 1, 0);
        quad(b, KERB, out0, out1, low1, low0, nx, 0, nz);
    }

    /**
     * Stone steps along each flight of steep segments: a tread per rise of {@link SettlementParameters.Surface#stepRise}
     * spanning the flight's length evenly, risers between, and the treads' sides down into the ground.
     */
    private void steps(Road r, boolean[] steep, double[] tx, double[] tz) {
        int m = r.count();
        double hw = r.width() / 2;
        double[] s = new double[m];
        for (int i = 1; i < m; i++) {
            s[i] = s[i - 1] + Geometry.length(r.x()[i] - r.x()[i - 1], r.z()[i] - r.z()[i - 1]);
        }
        Line line = new Line(r.x(), r.z());
        int i = 0;
        while (i < m - 1) {
            if (!steep[i]) {
                i++;
                continue;
            }
            int a = i;
            while (i < m - 1 && steep[i]) {
                i++;
            }
            int bEnd = i;
            double sa = s[a];
            double sb = s[bEnd];
            double ya = r.y()[a];
            double yb = r.y()[bEnd];
            double rise = yb - ya;
            int count = Math.max(1, (int) Math.ceil(Math.abs(rise) / surface.stepRise()));
            double[] h = new double[count + 2]; // h[0] before the flight, h[1..count] the treads, h[count + 1] after
            h[0] = ya;
            for (int k = 0; k < count; k++) {
                h[k + 1] = ya + rise * (k + (rise > 0 ? 1 : 0)) / count;
            }
            h[count + 1] = yb;
            double[] p = new double[4];
            double[][] left = new double[count + 1][];
            double[][] right = new double[count + 1][];
            double[] tangentX = new double[count + 1];
            double[] tangentZ = new double[count + 1];
            for (int k = 0; k <= count; k++) {
                line.at(sa + (sb - sa) * k / count, p);
                left[k] = new double[] {p[0] + p[3] * hw, 0, p[1] - p[2] * hw};
                right[k] = new double[] {p[0] - p[3] * hw, 0, p[1] + p[2] * hw};
                tangentX[k] = p[2];
                tangentZ[k] = p[3];
            }
            double lift = surface.lift() + surface.stepRise() / 2;
            for (int k = 0; k <= count; k++) {
                double tX = tangentX[k];
                double tZ = tangentZ[k];
                double[] l = {left[k][0], 0, left[k][2]};
                double[] rr = {right[k][0], 0, right[k][2]};
                Batch b = batch((l[0] + rr[0]) / 2, (l[2] + rr[2]) / 2);
                // the riser at boundary k: from the height before it to the one after, facing the lower side
                double before = h[k] + (k == 0 ? surface.lift() : lift);
                double after = h[k + 1] + (k == count ? surface.lift() : lift);
                if (Math.abs(after - before) > 1e-4) {
                    double lo = Math.min(before, after);
                    double hi = Math.max(before, after);
                    double sign = after > before ? -1 : 1;
                    quad(b, STEPS, new double[] {l[0], lo, l[2]}, new double[] {rr[0], lo, rr[2]},
                        new double[] {rr[0], hi, rr[2]}, new double[] {l[0], hi, l[2]}, sign * tX, 0, sign * tZ);
                }
                if (k < count) {
                    // the tread from boundary k to k + 1, and its sides down past the step below
                    double y = h[k + 1] + lift;
                    double[] l1 = {left[k + 1][0], y, left[k + 1][2]};
                    double[] r1 = {right[k + 1][0], y, right[k + 1][2]};
                    double[] l0 = {l[0], y, l[2]};
                    double[] r0 = {rr[0], y, rr[2]};
                    quad(b, STEPS, l0, l1, r1, r0, 0, 1, 0);
                    double bottom = y - surface.stepRise() - 0.25;
                    quad(b, STEPS, l0, l1, new double[] {l1[0], bottom, l1[2]}, new double[] {l0[0], bottom, l0[2]},
                        tZ, 0, -tX);
                    quad(b, STEPS, r0, r1, new double[] {r1[0], bottom, r1[2]}, new double[] {r0[0], bottom, r0[2]},
                        -tZ, 0, tX);
                }
            }
        }
    }

    // --------------------------------------------------------------------------------------------------------- square

    /** The square's cobbles on its plane, a quad a metre, and kerbs round its edges where no road comes in. */
    private void square(Square sq) {
        double lift = surface.lift();
        double[] q = new double[2];
        int nu = (int) Math.ceil(2 * sq.halfLength());
        int nv = (int) Math.ceil(2 * sq.halfWidth());
        double[][] grid = new double[(nu + 1) * (nv + 1)][];
        for (int v = 0; v <= nv; v++) {
            for (int u = 0; u <= nu; u++) {
                double along = -sq.halfLength() + 2 * sq.halfLength() * u / nu;
                double across = -sq.halfWidth() + 2 * sq.halfWidth() * v / nv;
                sq.point(along, across, q);
                grid[v * (nu + 1) + u] = new double[] {q[0], sq.height(q[0], q[1]) + lift, q[1]};
            }
        }
        for (int v = 0; v < nv; v++) {
            for (int u = 0; u < nu; u++) {
                double[] a = grid[v * (nu + 1) + u];
                double[] b = grid[v * (nu + 1) + u + 1];
                double[] c = grid[(v + 1) * (nu + 1) + u + 1];
                double[] d = grid[(v + 1) * (nu + 1) + u];
                quad(batch((a[0] + c[0]) / 2, (a[2] + c[2]) / 2), COBBLES, a, b, c, d, 0, 1, 0);
            }
        }
        // kerbs: a stone a metre along each edge, outside it
        double kw = surface.kerbWidth();
        double kh = surface.kerbHeight();
        double[][] edges = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (double[] e : edges) {
            boolean alongEdge = e[1] != 0; // the edge runs along the length
            double half = alongEdge ? sq.halfLength() : sq.halfWidth();
            int count = (int) Math.ceil(2 * half);
            for (int k = 0; k < count; k++) {
                double t0 = -half + 2 * half * k / count;
                double t1 = -half + 2 * half * (k + 1) / count;
                double[][] c = new double[4][];
                double[] off = {0, kw};
                int n = 0;
                for (double o : off) {
                    for (double t : new double[] {t0, t1}) {
                        double along = alongEdge ? t : e[0] * (sq.halfLength() + o);
                        double across = alongEdge ? e[1] * (sq.halfWidth() + o) : t;
                        sq.point(along, across, q);
                        c[n++] = new double[] {q[0], sq.height(q[0], q[1]) + lift + kh, q[1]};
                    }
                }
                double mx = (c[0][0] + c[3][0]) / 2;
                double mz = (c[0][2] + c[3][2]) / 2;
                if (covered(mx, mz)) {
                    continue;
                }
                double[] outward = new double[2];
                sq.point(alongEdge ? 0 : e[0], alongEdge ? e[1] : 0, outward);
                double ox = outward[0] - sq.x();
                double oz = outward[1] - sq.z();
                Batch b = batch(mx, mz);
                double[] in0 = {c[0][0], c[0][1] - kh, c[0][2]};
                double[] in1 = {c[1][0], c[1][1] - kh, c[1][2]};
                double[] lo2 = {c[2][0], c[2][1] - kh - SKIRT, c[2][2]};
                double[] lo3 = {c[3][0], c[3][1] - kh - SKIRT, c[3][2]};
                quad(b, KERB, in0, in1, c[1], c[0], -ox, 0, -oz);
                quad(b, KERB, c[0], c[1], c[3], c[2], 0, 1, 0);
                quad(b, KERB, c[2], c[3], lo3, lo2, ox, 0, oz);
            }
        }
    }

    /** Whether a road's surface covers ({@code x}, {@code z}). */
    private boolean covered(double x, double z) {
        for (Road r : settlement.roads()) {
            if (r.distance(x, z) < r.width() / 2 + 0.2) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------------------------------------------- primitives

    /**
     * A quad a, b, c, d (in order round it) into {@code b}'s {@code slot}: wound so its face's normal points along
     * ({@code hx}, {@code hy}, {@code hz}); flat normals, planar texture coordinates (from above where it faces up,
     * along and up where it stands).
     */
    private void quad(Batch batch, int slot, double[] a, double[] b, double[] c, double[] d, double hx, double hy,
        double hz) {
        double ux = c[0] - a[0];
        double uy = c[1] - a[1];
        double uz = c[2] - a[2];
        double vx = d[0] - b[0];
        double vy = d[1] - b[1];
        double vz = d[2] - b[2];
        // the normal of a quad: across its diagonals
        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-12) {
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
        Part part = batch.parts[slot];
        int[] index = new int[4];
        double[][] corners = {a, b, c, d};
        if (ny > 0.5) {
            // the tangent: +x in the surface (u along x, v along -z)
            tx = 1 - nx * nx;
            ty = -nx * ny;
            tz = -nx * nz;
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            tx /= tl;
            ty /= tl;
            tz /= tl;
            for (int k = 0; k < 4; k++) {
                double[] p = corners[k];
                index[k] = part.vertex(p[0], p[1], p[2], nx, ny, nz, tx, ty, tz, (p[0] - batch.x0) / texture,
                    -(p[2] - batch.z0) / texture);
                batch.minY = Math.min(batch.minY, p[1]);
            }
        } else {
            // standing: the tangent up x the normal, horizontal; v up
            tx = nz;
            ty = 0;
            tz = -nx;
            double tl = Math.sqrt(tx * tx + tz * tz);
            if (tl < 1e-9) {
                tx = 1;
                tz = 0;
            } else {
                tx /= tl;
                tz /= tl;
            }
            double base = Math.floor(a[1]);
            for (int k = 0; k < 4; k++) {
                double[] p = corners[k];
                double u = ((p[0] - batch.x0) * tx + (p[2] - batch.z0) * tz) / texture;
                index[k] = part.vertex(p[0], p[1], p[2], nx, ny, nz, tx, ty, tz, u, (p[1] - base) / texture);
                batch.minY = Math.min(batch.minY, p[1]);
            }
        }
        // counter-clockwise seen from the normal's side
        double gx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
        double gy = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
        double gz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        if (gx * nx + gy * ny + gz * nz >= 0) {
            part.triangle(index[0], index[1], index[2]);
            part.triangle(index[0], index[2], index[3]);
        } else {
            part.triangle(index[0], index[2], index[1]);
            part.triangle(index[0], index[3], index[2]);
        }
    }
}
