package com.truesim.common.procedural.fence;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Meshes;
import com.truesim.common.math.random.Noise;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a fence's pieces at a level of detail: beams as a cross-section swept along their centre line (rings at its
 * points, fewer at coarser levels; rotation by a fixed up reference, so sawn pieces keep their faces), closed by flat
 * caps, points or splintered ends; stones as rounded, lumpy blocks (a subdivided cube pushed to a superellipsoid).
 * Pieces are written one after the other, ring by ring, so their triangles share vertices and stay together: the order
 * meshlet builders want, and no piece is larger than a few meshlets.
 */
@AllowAllocation("generation, at load time")
final class FenceMesher {

    private static final int CUTS = 5;

    private final MeshWriter writer = new MeshWriter();
    private final double[] cuts = new double[4 * CUTS];
    private final Map<Integer, int[]> cubeTriangles = new HashMap<>();
    private final Map<Integer, int[]> cubeCorners = new HashMap<>();
    private FenceColors colors;
    private Noise lumps;
    private double ox;
    private double oy;
    private double oz;
    private int sides;
    // a cross-section: points, normals (in the n, b plane), u, whether a point joins the next
    private double[] pn = new double[64];
    private double[] pb = new double[64];
    private double[] qn = new double[64];
    private double[] qb = new double[64];
    private double[] pu = new double[64];
    private boolean[] joins = new boolean[64];
    private int profile;
    // frames along a beam
    private double[] frames = new double[9 * 64];

    /** The vertex range of each piece in the last mesh (first, end), for pieces drawn. */
    int[] ranges = new int[0];

    /**
     * Draws {@code pieces} at {@code level}: wood, stone and metal submeshes, positions less (ox, oy, oz).
     */
    ProceduralMesh mesh(List<Piece> pieces, int level, FenceColors shading, Noise stoneNoise, double x0, double y0,
        double z0, int roundSides, double error) {
        this.colors = shading;
        this.lumps = stoneNoise;
        this.ox = x0;
        this.oy = y0;
        this.oz = z0;
        this.sides = roundSides;
        writer.reset();
        ranges = new int[2 * pieces.size()];
        for (int part = 0; part < 3; part++) {
            if (part > 0) {
                writer.nextSubmesh();
            }
            for (int i = 0; i < pieces.size(); i++) {
                Piece piece = pieces.get(i);
                if (piece.part != part || !piece.shownAt(level)) {
                    continue;
                }
                ranges[2 * i] = writer.vertexCount();
                if (piece.stone) {
                    stone(piece, level);
                } else {
                    beam(piece, level);
                }
                ranges[2 * i + 1] = writer.vertexCount();
            }
        }
        return writer.build(error);
    }

    /** The sides of a round piece at {@code level}. */
    static int roundSides(Piece piece, int level, int sides) {
        double scale = Maths.pow(0.62, level);
        int k = piece.thin ? (int) Math.round(0.5 * sides * Maths.pow(0.75, level)) : (int) Math.round(sides * scale);
        return Math.max(3, k);
    }

    /** The subdivisions of a stone's cube at {@code level}. */
    static int stoneSubdivisions(Piece piece, int level) {
        int m = Math.max(piece.sx, Math.max(piece.sy, piece.sz)) > 0.11 ? 3 : 2;
        return Math.max(1, m - level);
    }

    // ---------------------------------------------------------------- beams

    private void beam(Piece p, int level) {
        crossSection(p, level);
        int n = p.points();
        int stride = 1 << Math.min(level, 4);
        // the rings drawn: every stride-th point, always the ends (a closed loop: no end)
        int[] rings = new int[n + 1];
        int count = 0;
        for (int i = 0; i < n; i += stride) {
            rings[count++] = i;
        }
        if (!p.closed && rings[count - 1] != n - 1) {
            rings[count++] = n - 1;
        }
        if (p.closed && count < 3) {
            count = 0;
            for (int i = 0; i < n; i++) {
                rings[count++] = i;
            }
        }
        if (frames.length < 9 * count) {
            frames = new double[9 * count];
        }
        double[] path = p.path;
        for (int r = 0; r < count; r++) {
            int i = rings[r];
            int before = r > 0 ? rings[r - 1] : p.closed ? rings[count - 1] : i;
            int after = r < count - 1 ? rings[r + 1] : p.closed ? rings[0] : i;
            double tx = path[3 * after] - path[3 * before];
            double ty = path[3 * after + 1] - path[3 * before + 1];
            double tz = path[3 * after + 2] - path[3 * before + 2];
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            tx /= tl;
            ty /= tl;
            tz /= tl;
            double d = p.upX * tx + p.upY * ty + p.upZ * tz;
            double nx = p.upX - d * tx;
            double ny = p.upY - d * ty;
            double nz = p.upZ - d * tz;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-6) {
                nx = 1 - tx * tx;
                ny = -tx * ty;
                nz = -tx * tz;
                nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            }
            nx /= nl;
            ny /= nl;
            nz /= nl;
            int f = 9 * r;
            frames[f] = tx;
            frames[f + 1] = ty;
            frames[f + 2] = tz;
            frames[f + 3] = nx;
            frames[f + 4] = ny;
            frames[f + 5] = nz;
            frames[f + 6] = ty * nz - tz * ny; // b = t x n
            frames[f + 7] = tz * nx - tx * nz;
            frames[f + 8] = tx * ny - ty * nx;
        }
        // the rings
        int first = writer.vertexCount();
        double v = p.v0;
        for (int r = 0; r < count; r++) {
            int i = rings[r];
            if (r > 0) {
                int j = rings[r - 1];
                double dx = path[3 * i] - path[3 * j];
                double dy = path[3 * i + 1] - path[3 * j + 1];
                double dz = path[3 * i + 2] - path[3 * j + 2];
                v += Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            int f = 9 * r;
            for (int k = 0; k < profile; k++) {
                double x = path[3 * i] + frames[f + 3] * pn[k] + frames[f + 6] * pb[k];
                double y = path[3 * i + 1] + frames[f + 4] * pn[k] + frames[f + 7] * pb[k];
                double z = path[3 * i + 2] + frames[f + 5] * pn[k] + frames[f + 8] * pb[k];
                double nx = frames[f + 3] * qn[k] + frames[f + 6] * qb[k];
                double ny = frames[f + 4] * qn[k] + frames[f + 7] * qb[k];
                double nz = frames[f + 5] * qn[k] + frames[f + 8] * qb[k];
                // the tangent along u: the normal turned a quarter toward b
                double ux = -frames[f + 3] * qb[k] + frames[f + 6] * qn[k];
                double uy = -frames[f + 4] * qb[k] + frames[f + 7] * qn[k];
                double uz = -frames[f + 5] * qb[k] + frames[f + 8] * qn[k];
                emit(p, x, y, z, nx, ny, nz, ux, uy, uz, pu[k], v);
            }
        }
        int ringCount = p.closed ? count : count - 1;
        for (int r = 0; r < ringCount; r++) {
            int a = first + r * profile;
            int b = first + ((r + 1) % count) * profile;
            for (int k = 0; k < profile - 1; k++) {
                if (joins[k]) {
                    writer.quad(a + k, a + k + 1, b + k + 1, b + k);
                }
            }
        }
        if (!p.closed) {
            end(p, rings[0], 0, false, p.start);
            end(p, rings[count - 1], count - 1, true, p.end);
        }
    }

    /** Closes a beam at ring {@code r} (path point {@code i}): at its last end ({@code last}) or its first. */
    private void end(Piece p, int i, int r, boolean last, Piece.End end) {
        if (end == Piece.End.OPEN) {
            return;
        }
        int f = 9 * r;
        double s = last ? 1 : -1;
        double tx = s * frames[f];
        double ty = s * frames[f + 1];
        double tz = s * frames[f + 2];
        double cx = p.path[3 * i];
        double cy = p.path[3 * i + 1];
        double cz = p.path[3 * i + 2];
        if (end == Piece.End.FLAT) {
            int centre = emit(p, cx, cy, cz, tx, ty, tz, frames[f + 3], frames[f + 4], frames[f + 5], 0, 0);
            int ring = writer.vertexCount();
            for (int k = 0; k < profile; k++) {
                emit(p, point(cx, f, k, 0), point(cy, f, k, 1), point(cz, f, k, 2), tx, ty, tz, frames[f + 3],
                    frames[f + 4], frames[f + 5], pn[k], s * pb[k]);
            }
            for (int k = 0; k < profile - 1; k++) {
                if (joins[k]) {
                    if (last) {
                        writer.triangle(centre, ring + k, ring + k + 1);
                    } else {
                        writer.triangle(centre, ring + k + 1, ring + k);
                    }
                }
            }
            return;
        }
        double length = p.tip * (end == Piece.End.JAGGED ? 0.6 : 1);
        double ax = cx + tx * length + frames[f + 3] * p.jagN * p.hn + frames[f + 6] * p.jagB * p.hb;
        double ay = cy + ty * length + frames[f + 4] * p.jagN * p.hn + frames[f + 7] * p.jagB * p.hb;
        double az = cz + tz * length + frames[f + 5] * p.jagN * p.hn + frames[f + 8] * p.jagB * p.hb;
        for (int k = 0; k < profile - 1; k++) {
            if (!joins[k]) {
                continue;
            }
            double x0 = point(cx, f, k, 0);
            double y0 = point(cy, f, k, 1);
            double z0 = point(cz, f, k, 2);
            double x1 = point(cx, f, k + 1, 0);
            double y1 = point(cy, f, k + 1, 1);
            double z1 = point(cz, f, k + 1, 2);
            if (last) {
                flat(p, x0, y0, z0, x1, y1, z1, ax, ay, az);
            } else {
                flat(p, x1, y1, z1, x0, y0, z0, ax, ay, az);
            }
        }
    }

    private double point(double c, int f, int k, int axis) {
        return c + frames[f + 3 + axis] * pn[k] + frames[f + 6 + axis] * pb[k];
    }

    /** A flat-shaded triangle a, b, c (counter-clockwise from its front), mapped in its plane. */
    private void flat(Piece p, double ax, double ay, double az, double bx, double by, double bz, double cx, double cy,
        double cz) {
        double ux = bx - ax;
        double uy = by - ay;
        double uz = bz - az;
        double vx = cx - ax;
        double vy = cy - ay;
        double vz = cz - az;
        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (nl < 1e-12 || ul < 1e-12) {
            return;
        }
        nx /= nl;
        ny /= nl;
        nz /= nl;
        double tx = ux / ul;
        double ty = uy / ul;
        double tz = uz / ul;
        // bitangent n x t: v
        double wx = ny * tz - nz * ty;
        double wy = nz * tx - nx * tz;
        double wz = nx * ty - ny * tx;
        int a = emit(p, ax, ay, az, nx, ny, nz, tx, ty, tz, 0, p.v0);
        int b = emit(p, bx, by, bz, nx, ny, nz, tx, ty, tz, ul, p.v0);
        int c = emit(p, cx, cy, cz, nx, ny, nz, tx, ty, tz, vx * tx + vy * ty + vz * tz,
            p.v0 + vx * wx + vy * wy + vz * wz);
        writer.triangle(a, b, c);
    }

    /** The cross-section of {@code p} at {@code level}. */
    private void crossSection(Piece p, int level) {
        profile = 0;
        switch (p.section) {
            case ROUND -> {
                int k = roundSides(p, level, sides);
                double perimeter = 2 * Maths.PI * Math.sqrt(0.5 * (p.hn * p.hn + p.hb * p.hb));
                for (int j = 0; j <= k; j++) {
                    double a = p.roll + Maths.TAU * j / k;
                    double c = Maths.cos(a);
                    double s = Maths.sin(a);
                    add(p.hn * c, p.hb * s, c / p.hn, s / p.hb, perimeter * j / k, j < k);
                }
            }
            case RECT -> {
                double u = 0;
                double[] corners = {p.hn, -p.hb, p.hn, p.hb, -p.hn, p.hb, -p.hn, -p.hb};
                for (int face = 0; face < 4; face++) {
                    int a = 2 * face;
                    int b = 2 * ((face + 1) % 4);
                    double en = corners[b] - corners[a];
                    double eb = corners[b + 1] - corners[a + 1];
                    double l = Math.sqrt(en * en + eb * eb);
                    add(corners[a], corners[a + 1], eb / l, -en / l, u, true);
                    u += l;
                    add(corners[b], corners[b + 1], eb / l, -en / l, u, false);
                }
            }
            case SPLIT -> {
                double radius = Math.max(p.hn, p.hb);
                int arc = Math.max(1, roundSides(p, level, sides) / 2);
                double c = Maths.cos(p.roll);
                double s = Maths.sin(p.roll);
                double centre = 4 * radius / (3 * Maths.PI);
                // the apex (the log's heart) to the bark, the bark's arc, back to the apex; centred on the area
                double u = 0;
                splitPoint(0, 0, 0, -1, c, s, centre, u, true);
                u += radius;
                splitPoint(radius, 0, 0, -1, c, s, centre, u, false);
                double step = radius * Maths.HALF_PI / arc;
                for (int j = 0; j <= arc; j++) {
                    double a = Maths.HALF_PI * j / arc;
                    splitPoint(radius * Maths.cos(a), radius * Maths.sin(a), Maths.cos(a), Maths.sin(a), c, s, centre,
                        u + step * j, j < arc);
                }
                u += step * arc;
                splitPoint(0, radius, -1, 0, c, s, centre, u, true);
                splitPoint(0, 0, -1, 0, c, s, centre, u + radius, false);
            }
            default -> throw new IllegalStateException();
        }
    }

    private void splitPoint(double n, double b, double normalN, double normalB, double c, double s, double centre,
        double u, boolean join) {
        double x = n - centre;
        double y = b - centre;
        add(c * x - s * y, s * x + c * y, c * normalN - s * normalB, s * normalN + c * normalB, u, join);
    }

    private void add(double n, double b, double normalN, double normalB, double u, boolean join) {
        if (profile == pn.length) {
            int size = 2 * profile;
            pn = java.util.Arrays.copyOf(pn, size);
            pb = java.util.Arrays.copyOf(pb, size);
            qn = java.util.Arrays.copyOf(qn, size);
            qb = java.util.Arrays.copyOf(qb, size);
            pu = java.util.Arrays.copyOf(pu, size);
            joins = java.util.Arrays.copyOf(joins, size);
        }
        double l = Math.sqrt(normalN * normalN + normalB * normalB);
        pn[profile] = n;
        pb[profile] = b;
        qn[profile] = normalN / l;
        qb[profile] = normalB / l;
        pu[profile] = u;
        joins[profile] = join;
        profile++;
    }

    // ---------------------------------------------------------------- stones

    private void stone(Piece p, int level) {
        int m = stoneSubdivisions(p, level);
        int[] corners = cube(m);
        int[] triangles = cubeTriangles.get(m);
        int count = corners.length / 3;
        float[] local = new float[3 * count];
        double offset = (p.shape & 1023) * 0.731;
        double[] f = p.frame;
        Rng shape = Rng.seeded(p.shape);
        for (int k = 0; k < CUTS; k++) {
            Vec3 n = shape.nextVec3OnSphere();
            cuts[4 * k] = n.x();
            cuts[4 * k + 1] = n.y();
            cuts[4 * k + 2] = n.z();
            cuts[4 * k + 3] = 0.72 + 0.25 * shape.nextDouble();
        }
        double taper = 0.25 * (shape.nextDouble() - 0.3);
        double skew = 0.18 * (shape.nextDouble() - 0.5);
        for (int v = 0; v < count; v++) {
            double x = corners[3 * v];
            double y = corners[3 * v + 1];
            double z = corners[3 * v + 2];
            double l = Math.sqrt(x * x + y * y + z * z);
            x /= l;
            y /= l;
            z /= l;
            double e = p.roundness;
            double r = Maths.pow(Maths.pow(Math.abs(x), e) + Maths.pow(Math.abs(y), e) + Maths.pow(Math.abs(z), e),
                -1 / e);
            // cleaved: cut by the stone's planes (flat faces, sharp-ish edges), then lumped a little
            for (int k = 0; k < CUTS; k++) {
                double facing = x * cuts[4 * k] + y * cuts[4 * k + 1] + z * cuts[4 * k + 2];
                if (facing > 1e-6) {
                    r = Math.min(r, cuts[4 * k + 3] / facing);
                }
            }
            r *= 1 + 0.04 * lumps.fractal(x * 1.6 + offset, y * 1.6, z * 1.6 - offset, 2);
            // tapered and skewed: no two stones the same block
            double ux = x * r;
            double uy = y * r;
            double uz = z * r;
            double ax = (ux * (1 - taper * uy) + skew * uy) * p.sx;
            double ay = (uy + 1) * p.sy;
            double az = uz * (1 - 0.5 * taper * uy) * p.sz;
            local[3 * v] = (float) (f[0] * ax + f[3] * ay + f[6] * az);
            local[3 * v + 1] = (float) (f[1] * ax + f[4] * ay + f[7] * az);
            local[3 * v + 2] = (float) (f[2] * ax + f[5] * ay + f[8] * az);
        }
        float[] normals = new float[3 * count];
        Meshes.vertexNormals(local, count, triangles, triangles.length, normals);
        int first = writer.vertexCount();
        for (int v = 0; v < count; v++) {
            double nx = normals[3 * v];
            double ny = normals[3 * v + 1];
            double nz = normals[3 * v + 2];
            // the tangent: the stone's along direction, across the normal (its across direction where they meet)
            double d = f[0] * nx + f[1] * ny + f[2] * nz;
            double tx = f[0] - d * nx;
            double ty = f[1] - d * ny;
            double tz = f[2] - d * nz;
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-3) {
                d = f[6] * nx + f[7] * ny + f[8] * nz;
                tx = f[6] - d * nx;
                ty = f[7] - d * ny;
                tz = f[8] - d * nz;
                tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            }
            double lx = local[3 * v];
            double ly = local[3 * v + 1];
            double lz = local[3 * v + 2];
            double along = f[0] * lx + f[1] * ly + f[2] * lz;
            double up = f[3] * lx + f[4] * ly + f[5] * lz;
            emit(p, p.cx + lx, p.cy + ly, p.cz + lz, nx, ny, nz, tx / tl, ty / tl, tz / tl, along + p.v0, up);
        }
        for (int t = 0; t < triangles.length; t += 3) {
            writer.triangle(first + triangles[t], first + triangles[t + 1], first + triangles[t + 2]);
        }
    }

    /** The cube [-m, m]^3's surface in a grid of m x m quads a face, vertices shared, counter-clockwise outward. */
    private int[] cube(int m) {
        int[] cached = cubeCorners.get(m);
        if (cached != null) {
            return cached;
        }
        int[][] axes = {{1, 0, 0, 0, 1, 0, 0, 0, 1}, {-1, 0, 0, 0, 0, 1, 0, 1, 0}, {0, 1, 0, 0, 0, 1, 1, 0, 0},
            {0, -1, 0, 1, 0, 0, 0, 0, 1}, {0, 0, 1, 1, 0, 0, 0, 1, 0}, {0, 0, -1, 0, 1, 0, 1, 0, 0}};
        Map<Long, Integer> index = new HashMap<>();
        int[] corners = new int[3 * 6 * (m + 1) * (m + 1)];
        int[] triangles = new int[6 * 6 * m * m];
        int count = 0;
        int t = 0;
        int[] grid = new int[(m + 1) * (m + 1)];
        for (int[] a : axes) {
            for (int j = 0; j <= m; j++) {
                for (int i = 0; i <= m; i++) {
                    int x = a[0] * m + (2 * i - m) * a[3] + (2 * j - m) * a[6];
                    int y = a[1] * m + (2 * i - m) * a[4] + (2 * j - m) * a[7];
                    int z = a[2] * m + (2 * i - m) * a[5] + (2 * j - m) * a[8];
                    long key = ((long) (x + 64) << 16) | ((long) (y + 64) << 8) | (z + 64);
                    Integer known = index.get(key);
                    if (known == null) {
                        known = count;
                        index.put(key, count);
                        corners[3 * count] = x;
                        corners[3 * count + 1] = y;
                        corners[3 * count + 2] = z;
                        count++;
                    }
                    grid[j * (m + 1) + i] = known;
                }
            }
            for (int j = 0; j < m; j++) {
                for (int i = 0; i < m; i++) {
                    int p00 = grid[j * (m + 1) + i];
                    int p10 = grid[j * (m + 1) + i + 1];
                    int p11 = grid[(j + 1) * (m + 1) + i + 1];
                    int p01 = grid[(j + 1) * (m + 1) + i];
                    triangles[t++] = p00;
                    triangles[t++] = p10;
                    triangles[t++] = p11;
                    triangles[t++] = p00;
                    triangles[t++] = p11;
                    triangles[t++] = p01;
                }
            }
        }
        int[] kept = java.util.Arrays.copyOf(corners, 3 * count);
        cubeCorners.put(m, kept);
        cubeTriangles.put(m, triangles);
        return kept;
    }

    // ---------------------------------------------------------------- vertices

    /** A vertex of {@code p} at (x, y, z) in the ground's frame, shaded there; returns its index. */
    private int emit(Piece p, double x, double y, double z, double nx, double ny, double nz, double tx, double ty,
        double tz, double u, double v) {
        colors.shade(p, x, y, z, nx, ny, nz);
        writer.color(colors.r, colors.g, colors.b);
        writer.uv1(colors.moss, colors.lichen);
        return writer.vertex(x - ox, y - oy, z - oz, nx, ny, nz, tx, ty, tz, u, v);
    }
}
