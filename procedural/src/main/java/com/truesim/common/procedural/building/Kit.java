package com.truesim.common.procedural.building;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.Arrays;
import java.util.List;

/**
 * Builds a building's mesh from closed solids, each its own part: boxes and beams (parallelepipeds), strips (a profile
 * between two polylines, extruded), walls (slabs with rectangular openings cut through), spires, logs and blobs. Every
 * face of a solid is cut on one grid shared by all its faces, so the solid's vertices meet bit for bit (closed when
 * welded by position), and no cell is longer than the options' cell: each cell's texture coordinates start at its own
 * repeat's fraction (see {@link BuildingSlot}). Faces are flat: normal per face, tangent along +u (horizontal on
 * vertical faces), bitangent {@code cross(normal, tangent)} along +v, which runs down the face.
 *
 * <p>Vectors are {@code double[3]}; the building's frame is y up.
 */
@AllowAllocation("generating, at load time")
final class Kit {

    private static final double[] Y = {0, 1, 0};

    private final double cell;
    private final double[] repeat;
    private final Buffer[] buffers;
    private int part = -1;

    Kit(BuildingOptions options) {
        this.cell = options.cell();
        List<BuildingSlot> slots = BuildingSlot.all();
        repeat = new double[slots.size()];
        buffers = new Buffer[slots.size()];
        for (int s = 0; s < slots.size(); s++) {
            repeat[s] = options.repeat(slots.get(s));
            buffers[s] = new Buffer();
        }
    }

    /** Starts the next part: a closed solid. */
    private void begin() {
        part++;
    }

    /** How many parts so far. */
    int parts() {
        return part + 1;
    }

    // ---------------------------------------------------------------- solids

    /** An axis-aligned box from (x0, y0, z0) to (x1, y1, z1), every face of {@code slot}. */
    void box(BuildingSlot slot, double x0, double y0, double z0, double x1, double y1, double z1) {
        beam(new double[] {x0, y0, z0}, new double[] {x1 - x0, 0, 0}, new double[] {0, y1 - y0, 0},
            new double[] {0, 0, z1 - z0}, slot, slot, slot, slot, slot, slot);
    }

    /** A parallelepiped from corner {@code o} along edges {@code a}, {@code b}, {@code c}, every face {@code slot}. */
    void beam(double[] o, double[] a, double[] b, double[] c, BuildingSlot slot) {
        beam(o, a, b, c, slot, slot, slot, slot, slot, slot);
    }

    /**
     * A parallelepiped from corner {@code o} along edges {@code a}, {@code b}, {@code c}, its faces' slots: the face at
     * -a, at +a, at -b, at +b, at -c, at +c.
     */
    void beam(double[] o, double[] a, double[] b, double[] c, BuildingSlot minusA, BuildingSlot plusA,
        BuildingSlot minusB, BuildingSlot plusB, BuildingSlot minusC, BuildingSlot plusC) {
        double la = length(a);
        double lb = length(b);
        double lc = length(c);
        if (la < 1e-6 || lb < 1e-6 || lc < 1e-6) {
            return;
        }
        begin();
        int na = cells(la);
        int nb = cells(lb);
        int nc = cells(lc);
        double[][][][] p = new double[na + 1][nb + 1][nc + 1][];
        for (int i = 0; i <= na; i++) {
            for (int j = 0; j <= nb; j++) {
                for (int k = 0; k <= nc; k++) {
                    double fi = i / (double) na;
                    double fj = j / (double) nb;
                    double fk = k / (double) nc;
                    p[i][j][k] = new double[] {o[0] + a[0] * fi + b[0] * fj + c[0] * fk,
                        o[1] + a[1] * fi + b[1] * fj + c[1] * fk, o[2] + a[2] * fi + b[2] * fj + c[2] * fk};
                }
            }
        }
        double[] na3 = scale(a, -1);
        double[] nb3 = scale(b, -1);
        double[] nc3 = scale(c, -1);
        double[] grain = la >= lb && la >= lc ? a : lb >= lc ? b : c;
        for (int j = 0; j < nb; j++) {
            for (int k = 0; k < nc; k++) {
                quad(minusA, p[0][j][k], p[0][j + 1][k], p[0][j + 1][k + 1], p[0][j][k + 1], na3, up(minusA, grain));
                quad(plusA, p[na][j][k], p[na][j + 1][k], p[na][j + 1][k + 1], p[na][j][k + 1], a, up(plusA, grain));
            }
        }
        for (int i = 0; i < na; i++) {
            for (int k = 0; k < nc; k++) {
                quad(minusB, p[i][0][k], p[i + 1][0][k], p[i + 1][0][k + 1], p[i][0][k + 1], nb3, up(minusB, grain));
                quad(plusB, p[i][nb][k], p[i + 1][nb][k], p[i + 1][nb][k + 1], p[i][nb][k + 1], b, up(plusB, grain));
            }
        }
        for (int i = 0; i < na; i++) {
            for (int j = 0; j < nb; j++) {
                quad(minusC, p[i][j][0], p[i + 1][j][0], p[i + 1][j + 1][0], p[i][j + 1][0], nc3, up(minusC, grain));
                quad(plusC, p[i][j][nc], p[i + 1][j][nc], p[i + 1][j + 1][nc], p[i][j + 1][nc], c, up(plusC, grain));
            }
        }
    }

    /**
     * Which way a face's texture stands: along the grain (a piece's longest edge) for wood, whose textures show
     * upright boards; up for everything else.
     */
    private static double[] up(BuildingSlot slot, double[] grain) {
        return switch (slot) {
            case TRIM, TIMBER, DOOR, SHUTTERS, FLOOR -> grain;
            default -> Y;
        };
    }

    /**
     * A strip: the profile between the polylines {@code lo} and {@code hi} over {@code t} (each point
     * {@code o + T t + V lo} to {@code o + T t + V hi}, {@code T} and {@code V} orthonormal, {@code hi > lo}),
     * extruded {@code length} along {@code e} (unit). A gable, a roof's slab, a carved board, a purlin with a shaped
     * end. Slots: the {@code hi} side, the {@code lo} side, the ends at the first and last t, the profile's face at the
     * start of the extrusion and at its end. {@code t} increases.
     */
    void strip(double[] o, double[] tAxis, double[] vAxis, double[] e, double[] t, double[] lo, double[] hi,
        double length, BuildingSlot hiSlot, BuildingSlot loSlot, BuildingSlot endSlot, BuildingSlot capStart,
        BuildingSlot capEnd) {
        if (t.length < 2 || length < 1e-6) {
            return;
        }
        begin();
        // refine the profile so no column is wider than a cell
        double[][] refined = refine(t, lo, hi);
        double[] ts = refined[0];
        double[] los = refined[1];
        double[] his = refined[2];
        int n = ts.length;
        double tallest = 0;
        for (int k = 0; k < n; k++) {
            if (!(his[k] > los[k])) {
                his[k] = los[k] + 1e-3;
            }
            tallest = Math.max(tallest, his[k] - los[k]);
        }
        int rows = cells(tallest);
        int ne = cells(length);
        double[][][][] p = new double[n][rows + 1][ne + 1][];
        for (int k = 0; k < n; k++) {
            for (int r = 0; r <= rows; r++) {
                double v = r == rows ? his[k] : los[k] + (his[k] - los[k]) * r / rows;
                for (int m = 0; m <= ne; m++) {
                    double f = length * m / ne;
                    p[k][r][m] = new double[] {o[0] + tAxis[0] * ts[k] + vAxis[0] * v + e[0] * f,
                        o[1] + tAxis[1] * ts[k] + vAxis[1] * v + e[1] * f,
                        o[2] + tAxis[2] * ts[k] + vAxis[2] * v + e[2] * f};
                }
            }
        }
        double[] minusE = scale(e, -1);
        double spanT = Math.abs(ts[n - 1] - ts[0]);
        double[] grain = length >= spanT && length >= tallest ? e : spanT >= tallest ? tAxis : vAxis;
        for (int k = 0; k + 1 < n; k++) {
            for (int r = 0; r < rows; r++) {
                quad(capStart, p[k][r][0], p[k + 1][r][0], p[k + 1][r + 1][0], p[k][r + 1][0], minusE,
                    up(capStart, grain));
                quad(capEnd, p[k][r][ne], p[k + 1][r][ne], p[k + 1][r + 1][ne], p[k][r + 1][ne], e, up(capEnd, grain));
            }
            double dt = ts[k + 1] - ts[k];
            double[] hiOut = sub(scale(vAxis, dt), scale(tAxis, his[k + 1] - his[k]));
            double[] loOut = sub(scale(tAxis, los[k + 1] - los[k]), scale(vAxis, dt));
            for (int m = 0; m < ne; m++) {
                quad(hiSlot, p[k][rows][m], p[k + 1][rows][m], p[k + 1][rows][m + 1], p[k][rows][m + 1], hiOut,
                    up(hiSlot, grain));
                quad(loSlot, p[k][0][m], p[k + 1][0][m], p[k + 1][0][m + 1], p[k][0][m + 1], loOut, up(loSlot, grain));
            }
        }
        double[] minusT = scale(tAxis, -1);
        for (int r = 0; r < rows; r++) {
            for (int m = 0; m < ne; m++) {
                quad(endSlot, p[0][r][m], p[0][r + 1][m], p[0][r + 1][m + 1], p[0][r][m + 1], minusT,
                    up(endSlot, grain));
                quad(endSlot, p[n - 1][r][m], p[n - 1][r + 1][m], p[n - 1][r + 1][m + 1], p[n - 1][r][m + 1],
                    tAxis, up(endSlot, grain));
            }
        }
    }

    /** {@link #strip} of one slot. */
    void strip(double[] o, double[] tAxis, double[] vAxis, double[] e, double[] t, double[] lo, double[] hi,
        double length, BuildingSlot slot) {
        strip(o, tAxis, vAxis, e, t, lo, hi, length, slot, slot, slot, slot, slot);
    }

    /**
     * A wall: a slab from {@code o} (its outer face's lower corner) {@code length} along {@code along} (unit,
     * horizontal), {@code height} up and {@code thick} along {@code inward} (unit), with rectangular openings cut
     * through it ({@code x} along from {@code o}, {@code y} up, width, height each). Its outer face, inner face,
     * the openings' reveals, and its edges (ends, bottom, top). {@code turned}: the texture's up runs along the wall,
     * so a texture of upright boards shows horizontal logs.
     */
    void wall(double[] o, double[] along, double[] inward, double length, double height, double thick,
        List<double[]> holes, BuildingSlot outer, BuildingSlot inner, BuildingSlot reveal, BuildingSlot edge,
        boolean turned) {
        if (length < 1e-4 || height < 1e-4) {
            return;
        }
        begin();
        double[] xs = breaks(0, length, holes, 0);
        double[] ys = breaks(0, height, holes, 1);
        int nx = xs.length - 1;
        int ny = ys.length - 1;
        boolean[][] solid = new boolean[nx][ny];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                double cx = (xs[i] + xs[i + 1]) / 2;
                double cy = (ys[j] + ys[j + 1]) / 2;
                boolean in = false;
                for (double[] h : holes) {
                    in |= cx > h[0] && cx < h[0] + h[2] && cy > h[1] && cy < h[1] + h[3];
                }
                solid[i][j] = !in;
            }
        }
        double[][][][] p = new double[nx + 1][ny + 1][2][];
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= ny; j++) {
                for (int d = 0; d < 2; d++) {
                    double w = d * thick;
                    p[i][j][d] = new double[] {o[0] + along[0] * xs[i] + inward[0] * w,
                        o[1] + along[1] * xs[i] + ys[j] + inward[1] * w, o[2] + along[2] * xs[i] + inward[2] * w};
                }
            }
        }
        double[] up = turned ? along : Y;
        double[] out = scale(inward, -1);
        double[] back = scale(along, -1);
        double[] down = {0, -1, 0};
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                if (!solid[i][j]) {
                    continue;
                }
                quad(outer, p[i][j][0], p[i + 1][j][0], p[i + 1][j + 1][0], p[i][j + 1][0], out, up);
                quad(inner, p[i][j][1], p[i + 1][j][1], p[i + 1][j + 1][1], p[i][j + 1][1], inward, up);
                if (i == 0 || !solid[i - 1][j]) {
                    quad(i == 0 ? edge : reveal, p[i][j][0], p[i][j + 1][0], p[i][j + 1][1], p[i][j][1], back, up);
                }
                if (i == nx - 1 || !solid[i + 1][j]) {
                    quad(i == nx - 1 ? edge : reveal, p[i + 1][j][0], p[i + 1][j + 1][0], p[i + 1][j + 1][1],
                        p[i + 1][j][1], along, up);
                }
                if (j == 0 || !solid[i][j - 1]) {
                    quad(j == 0 ? edge : reveal, p[i][j][0], p[i + 1][j][0], p[i + 1][j][1], p[i][j][1], down, up);
                }
                if (j == ny - 1 || !solid[i][j + 1]) {
                    quad(j == ny - 1 ? edge : reveal, p[i][j + 1][0], p[i + 1][j + 1][0], p[i + 1][j + 1][1],
                        p[i][j + 1][1], Y, up);
                }
            }
        }
    }

    /**
     * A pyramid about the vertical through ({@code cx}, {@code cz}): a regular polygon of {@code sides} corners at
     * {@code radius} (the first at angle {@code turn} from +x) at {@code y0}, rising to the apex at {@code y1}: a
     * spire. Its sides are cut into bands and columns no longer than a cell; its base is closed.
     */
    void spire(double cx, double cz, double y0, double y1, double radius, int sides, double turn, BuildingSlot slot,
        BuildingSlot base) {
        begin();
        double slant = Math.sqrt(radius * radius + (y1 - y0) * (y1 - y0));
        int bands = cells(slant);
        int per = cells(2 * radius * Maths.sin(Math.PI / sides));
        int around = sides * per;
        double[][][] ring = new double[bands][around][];
        double[][] corner = new double[sides + 1][];
        for (int i = 0; i <= sides; i++) {
            double a = turn + Maths.TAU * (i % sides) / sides;
            corner[i] = new double[] {Maths.cos(a), Maths.sin(a)};
        }
        for (int k = 0; k < bands; k++) {
            double f = k / (double) bands;
            double r = radius * (1 - f);
            for (int i = 0; i < around; i++) {
                int side = i / per;
                double g = (i % per) / (double) per;
                double ux = corner[side][0] + (corner[side + 1][0] - corner[side][0]) * g;
                double uz = corner[side][1] + (corner[side + 1][1] - corner[side][1]) * g;
                ring[k][i] = new double[] {cx + r * ux, y0 + (y1 - y0) * f, cz + r * uz};
            }
        }
        double[] apex = {cx, y1, cz};
        double[] centre = {cx, y0, cz};
        for (int i = 0; i < around; i++) {
            int j = (i + 1) % around;
            int side = i / per;
            double[] mid = {(corner[side][0] + corner[side + 1][0]) / 2, 0,
                (corner[side][1] + corner[side + 1][1]) / 2};
            double[] outward = {mid[0], radius / Math.max(1e-6, y1 - y0) * length(mid), mid[2]};
            for (int k = 0; k + 1 < bands; k++) {
                quad(slot, ring[k][i], ring[k][j], ring[k + 1][j], ring[k + 1][i], outward, Y);
            }
            tri(slot, ring[bands - 1][i], ring[bands - 1][j], apex, outward, Y);
            tri(base, centre, ring[0][j], ring[0][i], new double[] {0, -1, 0}, Y);
        }
    }

    /**
     * A log: a prism of {@code sides} sides about the axis from {@code p0} along {@code axis}, {@code radius} to its
     * corners, the first corner at {@code turn}; its sides of {@code slot}, its sawn ends of {@code ends}.
     */
    void log(double[] p0, double[] axis, double radius, int sides, double turn, BuildingSlot slot,
        BuildingSlot ends) {
        double len = length(axis);
        if (len < 1e-6) {
            return;
        }
        begin();
        double[] d = scale(axis, 1 / len);
        double[] u = Math.abs(d[1]) < 0.9 ? normalize(cross(d, Y)) : normalize(cross(d, new double[] {1, 0, 0}));
        double[] w = cross(d, u);
        int segments = cells(len);
        double[][][] ring = new double[segments + 1][sides][];
        for (int m = 0; m <= segments; m++) {
            double f = len * m / segments;
            for (int i = 0; i < sides; i++) {
                double a = turn + Maths.TAU * i / sides;
                double cu = radius * Maths.cos(a);
                double cw = radius * Maths.sin(a);
                ring[m][i] = new double[] {p0[0] + d[0] * f + u[0] * cu + w[0] * cw,
                    p0[1] + d[1] * f + u[1] * cu + w[1] * cw, p0[2] + d[2] * f + u[2] * cu + w[2] * cw};
            }
        }
        double[] c0 = p0;
        double[] c1 = add(p0, axis);
        double[] back = scale(d, -1);
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            double a = turn + Maths.TAU * (i + 0.5) / sides;
            double[] outward = add(scale(u, Maths.cos(a)),
                scale(w, Maths.sin(a)));
            for (int m = 0; m < segments; m++) {
                quad(slot, ring[m][i], ring[m][j], ring[m + 1][j], ring[m + 1][i], outward, d);
            }
            tri(ends, c0, ring[0][j], ring[0][i], back, u);
            tri(ends, c1, ring[segments][i], ring[segments][j], d, u);
        }
    }

    /** A blob: an octahedron about {@code c} with half extents {@code rx}, {@code ry}, {@code rz} (flowers, leaves). */
    void blob(double[] c, double rx, double ry, double rz, BuildingSlot slot) {
        begin();
        double[][] axes = {{c[0] + rx, c[1], c[2]}, {c[0], c[1], c[2] + rz}, {c[0] - rx, c[1], c[2]},
            {c[0], c[1], c[2] - rz}};
        double[] top = {c[0], c[1] + ry, c[2]};
        double[] bottom = {c[0], c[1] - ry, c[2]};
        for (int i = 0; i < 4; i++) {
            double[] a = axes[i];
            double[] b = axes[(i + 1) % 4];
            double[] mid = {(a[0] + b[0]) / 2 - c[0], 0, (a[2] + b[2]) / 2 - c[2]};
            tri(slot, a, b, top, add(mid, new double[] {0, 0.5 * length(mid), 0}), Y);
            tri(slot, a, b, bottom, add(mid, new double[] {0, -0.5 * length(mid), 0}), Y);
        }
    }

    /**
     * Single-sheet flora (leaves, petals, stems: open, for two-sided materials) from {@code mesh}, its submesh 0 into
     * the foliage slot and 1 into the flowers slot, placed by the frame: its x along {@code ax}, y along {@code up}, z
     * along {@code out} (orthonormal), from {@code o}. Their texture coordinates are kept (a palette's). Not a closed
     * part: their triangles belong to no part ({@code -1}).
     */
    void sheets(com.truesim.common.procedural.mesh.ProceduralMesh mesh, double[] o, double[] ax, double[] up,
        double[] out) {
        var position = mesh.vertices().attribute(com.truesim.common.procedural.mesh.ProceduralVertex.POSITION);
        var normal = mesh.vertices().attribute(com.truesim.common.procedural.mesh.ProceduralVertex.NORMAL);
        var tangent = mesh.vertices().attribute(com.truesim.common.procedural.mesh.ProceduralVertex.TANGENT);
        var uv = mesh.vertices().attribute(com.truesim.common.procedural.mesh.ProceduralVertex.UV);
        BuildingSlot[] slots = {BuildingSlot.FOLIAGE, BuildingSlot.FLOWERS};
        int[] map = new int[mesh.vertexCount()];
        for (int s = 0; s < Math.min(2, mesh.submeshCount()); s++) {
            Buffer buffer = buffers[slots[s].ordinal()];
            Arrays.fill(map, -1);
            int[] indices = mesh.indices();
            for (int i = mesh.submeshStart(s); i < mesh.submeshEnd(s); i += 3) {
                int[] t = new int[3];
                for (int c = 0; c < 3; c++) {
                    int v = indices[i + c];
                    if (map[v] < 0) {
                        double[] pp = frame(o, ax, up, out, position.x(v), position.y(v), position.z(v));
                        double[] nn = frame(null, ax, up, out, normal.x(v), normal.y(v), normal.z(v));
                        double[] tt = frame(null, ax, up, out, tangent.x(v), tangent.y(v), tangent.z(v));
                        map[v] = buffer.vertices;
                        buffer.vertex(pp, nn, tt, uv.x(v), uv.y(v));
                    }
                    t[c] = map[v];
                }
                buffer.triangle(t[0], t[1], t[2], -1);
            }
        }
    }

    /** The point (or, without an origin, the direction) x, y, z of a frame. */
    private static double[] frame(double[] o, double[] ax, double[] up, double[] out, double x, double y, double z) {
        double[] p = {ax[0] * x + up[0] * y + out[0] * z, ax[1] * x + up[1] * y + out[1] * z,
            ax[2] * x + up[2] * y + out[2] * z};
        return o == null ? p : add(o, p);
    }

    // ---------------------------------------------------------------- faces

    /** A planar quad (corners in order round it, either way), wound to face {@code out}. */
    void quad(BuildingSlot slot, double[] a, double[] b, double[] c, double[] d, double[] out, double[] up) {
        double[] n = cross(sub(c, a), sub(d, b));
        double l = length(n);
        n = l < 1e-12 ? normalize(out) : scale(n, 1 / l);
        if (dot(n, out) < 0) {
            n = scale(n, -1);
            double[] t = b;
            b = d;
            d = t;
        }
        Buffer buffer = buffers[slot.ordinal()];
        int base = emit(slot, buffer, n, up, a, b, c, d);
        buffer.triangle(base, base + 1, base + 2, part);
        buffer.triangle(base, base + 2, base + 3, part);
    }

    /** A triangle, wound to face {@code out}. */
    void tri(BuildingSlot slot, double[] a, double[] b, double[] c, double[] out, double[] up) {
        double[] n = cross(sub(b, a), sub(c, a));
        double l = length(n);
        n = l < 1e-12 ? normalize(out) : scale(n, 1 / l);
        if (dot(n, out) < 0) {
            n = scale(n, -1);
            double[] t = b;
            b = c;
            c = t;
        }
        Buffer buffer = buffers[slot.ordinal()];
        int base = emit(slot, buffer, n, up, a, b, c);
        buffer.triangle(base, base + 1, base + 2, part);
    }

    /** Emits the corners with the face's normal and tangent, texture coordinates from the cell's own repeat. */
    private int emit(BuildingSlot slot, Buffer buffer, double[] n, double[] up, double[]... corners) {
        double[] u = sub(up, scale(n, dot(up, n)));
        if (length(u) < 0.2) {
            u = Math.abs(n[2]) < 0.9 ? new double[] {0, 0, -1} : new double[] {0, 1, 0};
            u = sub(u, scale(n, dot(u, n)));
        }
        u = normalize(u);
        double[] t = normalize(cross(n, u));
        double r = repeat[slot.ordinal()];
        double minU = Double.POSITIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        for (double[] p : corners) {
            minU = Math.min(minU, dot(p, t) / r);
            minV = Math.min(minV, -dot(p, u) / r);
        }
        double ou = Math.floor(minU);
        double ov = Math.floor(minV);
        int base = buffer.vertices;
        for (double[] p : corners) {
            buffer.vertex(p, n, t, dot(p, t) / r - ou, -dot(p, u) / r - ov);
        }
        return base;
    }

    // ---------------------------------------------------------------- the mesh

    /** The mesh of everything added, a submesh per slot, with {@code error} as its LOD error. */
    ProceduralMesh build(MeshWriter writer, double error) {
        writer.reset();
        for (int s = 0; s < buffers.length; s++) {
            if (s > 0) {
                writer.nextSubmesh();
            }
            Buffer b = buffers[s];
            int base = writer.vertexCount();
            for (int v = 0; v < b.vertices; v++) {
                writer.vertex(b.positions[3 * v], b.positions[3 * v + 1], b.positions[3 * v + 2], b.normals[3 * v],
                    b.normals[3 * v + 1], b.normals[3 * v + 2], b.tangents[3 * v], b.tangents[3 * v + 1],
                    b.tangents[3 * v + 2], b.uvs[2 * v], b.uvs[2 * v + 1]);
            }
            for (int i = 0; i < b.indexCount; i += 3) {
                writer.triangle(base + b.indices[i], base + b.indices[i + 1], base + b.indices[i + 2]);
            }
        }
        return writer.build(error);
    }

    /** The part of every triangle of {@link #build}'s mesh, in its order. */
    int[] triangleParts() {
        int total = 0;
        for (Buffer b : buffers) {
            total += b.indexCount / 3;
        }
        int[] out = new int[total];
        int at = 0;
        for (Buffer b : buffers) {
            System.arraycopy(b.parts, 0, out, at, b.indexCount / 3);
            at += b.indexCount / 3;
        }
        return out;
    }

    // ---------------------------------------------------------------- grids

    private int cells(double extent) {
        return Math.max(1, (int) Math.ceil(extent / cell - 1e-9));
    }

    /** Sorted breaks from {@code from} to {@code to}: the holes' edges along axis 0 (x) or 1 (y), cells between. */
    private double[] breaks(double from, double to, List<double[]> holes, int axis) {
        double[] raw = new double[2 + 2 * holes.size()];
        int n = 0;
        raw[n++] = from;
        raw[n++] = to;
        for (double[] h : holes) {
            raw[n++] = Math.clamp(h[axis], from, to);
            raw[n++] = Math.clamp(h[axis] + h[axis + 2], from, to);
        }
        Arrays.sort(raw, 0, n);
        double[] out = new double[64];
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (count > 0 && raw[i] - out[count - 1] < 1e-4) {
                continue;
            }
            if (count > 0) {
                double gap = raw[i] - out[count - 1];
                int split = cells(gap);
                double start = out[count - 1];
                for (int s = 1; s < split; s++) {
                    if (count + 2 > out.length) {
                        out = Arrays.copyOf(out, out.length * 2);
                    }
                    out[count++] = start + gap * s / split;
                }
            }
            if (count + 2 > out.length) {
                out = Arrays.copyOf(out, out.length * 2);
            }
            out[count++] = raw[i];
        }
        return Arrays.copyOf(out, count);
    }

    /**
     * The profile with points added where a column is wider than a cell, measured along its edges (a sloping roof's
     * soffit is longer than its run), on its straight segments.
     */
    private double[][] refine(double[] t, double[] lo, double[] hi) {
        int[] splits = new int[t.length];
        int capacity = t.length;
        for (int k = 1; k < t.length; k++) {
            double dt = t[k] - t[k - 1];
            double edge = Math.max(Maths.hypot(dt, lo[k] - lo[k - 1]), Maths.hypot(dt, hi[k] - hi[k - 1]));
            splits[k] = cells(edge);
            capacity += splits[k];
        }
        double[] ts = new double[capacity];
        double[] los = new double[capacity];
        double[] his = new double[capacity];
        int n = 0;
        for (int k = 0; k < t.length; k++) {
            if (k > 0) {
                int split = splits[k];
                for (int s = 1; s < split; s++) {
                    double f = s / (double) split;
                    ts[n] = t[k - 1] + (t[k] - t[k - 1]) * f;
                    los[n] = lo[k - 1] + (lo[k] - lo[k - 1]) * f;
                    his[n] = hi[k - 1] + (hi[k] - hi[k - 1]) * f;
                    n++;
                }
            }
            ts[n] = t[k];
            los[n] = lo[k];
            his[n] = hi[k];
            n++;
        }
        return new double[][] {Arrays.copyOf(ts, n), Arrays.copyOf(los, n), Arrays.copyOf(his, n)};
    }

    // ---------------------------------------------------------------- vectors

    static double[] p(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    static double[] add(double[] a, double[] b) {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] scale(double[] a, double s) {
        return new double[] {a[0] * s, a[1] * s, a[2] * s};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double length(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    static double[] normalize(double[] a) {
        double l = length(a);
        return l > 0 ? scale(a, 1 / l) : new double[] {0, 1, 0};
    }

    /** One slot's vertices and triangles. */
    private static final class Buffer {
        float[] positions = new float[3 * 256];
        float[] normals = new float[3 * 256];
        float[] tangents = new float[3 * 256];
        float[] uvs = new float[2 * 256];
        int[] indices = new int[3 * 256];
        int[] parts = new int[256];
        int vertices;
        int indexCount;

        void vertex(double[] p, double[] n, double[] t, double u, double v) {
            if (vertices * 3 == positions.length) {
                positions = Arrays.copyOf(positions, positions.length * 2);
                normals = Arrays.copyOf(normals, normals.length * 2);
                tangents = Arrays.copyOf(tangents, tangents.length * 2);
                uvs = Arrays.copyOf(uvs, uvs.length * 2);
            }
            positions[3 * vertices] = (float) p[0];
            positions[3 * vertices + 1] = (float) p[1];
            positions[3 * vertices + 2] = (float) p[2];
            normals[3 * vertices] = (float) n[0];
            normals[3 * vertices + 1] = (float) n[1];
            normals[3 * vertices + 2] = (float) n[2];
            tangents[3 * vertices] = (float) t[0];
            tangents[3 * vertices + 1] = (float) t[1];
            tangents[3 * vertices + 2] = (float) t[2];
            uvs[2 * vertices] = (float) u;
            uvs[2 * vertices + 1] = (float) v;
            vertices++;
        }

        void triangle(int a, int b, int c, int part) {
            if (indexCount + 3 > indices.length) {
                indices = Arrays.copyOf(indices, indices.length * 2);
                parts = Arrays.copyOf(parts, parts.length * 2);
            }
            parts[indexCount / 3] = part;
            indices[indexCount++] = a;
            indices[indexCount++] = b;
            indices[indexCount++] = c;
        }
    }
}
