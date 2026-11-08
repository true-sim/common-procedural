package com.truesim.common.procedural.rock;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Meshes;
import com.truesim.common.math.random.Noise;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates rocks: a subdivided icosahedron whose every direction is pushed to the rock's surface there, so all levels
 * of detail sample one surface. The surface is an ellipsoid cut by random planes (cleavage: flat faces, sharp edges),
 * lumped by fractal noise, carved by ridged noise (crests and gullies, as weathering leaves), cracked along the
 * borders of cellular noise's cells, banded by strata, and cut flat where it is buried. Each level is a closed,
 * consistently wound surface. The same parameters and seed always give bit-identical meshes.
 *
 * <p>Keeps its working memory from rock to rock; not thread-safe ({@link Rocks} keeps one per thread).
 */
public final class RockGenerator {

    /** Version of the generated output: changes whenever the same inputs would give different meshes. */
    public static final int VERSION = 1;

    private static final int PLANES = 14;

    private final MeshWriter writer = new MeshWriter();
    private final double[] planes = new double[4 * PLANES];
    private double[] directions = new double[3 * 64];
    private int[] triangles = new int[3 * 64];
    private int directionCount;
    private int triangleCount;
    private long[] edgeKeys = new long[256];
    private int[] edgeValues = new int[256];
    private Noise noise;
    private RockParams params;
    private double[] offset = new double[3];

    /** A rock as {@code params} say, made from {@code seed}. */
    @AllowAllocation("the rock made")
    public Rock generate(RockParams params, long seed) {
        this.params = params;
        noise = Noise.seeded(Seeds.derive(seed, 0));
        Rng rng = Rng.seeded(Seeds.derive(seed, 1));
        offset = new double[] {rng.nextDouble() * 100, rng.nextDouble() * 100, rng.nextDouble() * 100};
        for (int k = 0; k < PLANES; k++) {
            Vec3 n = rng.nextVec3OnSphere();
            planes[4 * k] = n.x();
            planes[4 * k + 1] = n.y();
            planes[4 * k + 2] = n.z();
            planes[4 * k + 3] = 0.62 + 0.28 * rng.nextDouble(); // distance from the center, in unit space
        }
        List<ProceduralMesh> lods = new ArrayList<>(params.subdivisions().size());
        for (int level : params.subdivisions()) {
            icosphere(level);
            lods.add(mesh(lods.isEmpty() ? 0 : error()));
        }
        return new Rock(params, seed, lods);
    }

    /** The surface's distance from the center along the unit direction (x, y, z), in unit space. */
    double radius(double x, double y, double z) {
        double sx = x * 1.3 + offset[0];
        double sy = y * 1.3 + offset[1];
        double sz = z * 1.3 + offset[2];
        double r = 1 + params.lumpiness() * noise.fractal(sx, sy, sz, 3);
        // Cleavage: the nearest of the planes the direction meets
        double cut = Double.POSITIVE_INFINITY;
        for (int k = 0; k < PLANES; k++) {
            double facing = x * planes[4 * k] + y * planes[4 * k + 1] + z * planes[4 * k + 2];
            if (facing > 1e-6) {
                cut = Math.min(cut, planes[4 * k + 3] / facing);
            }
        }
        r = r + params.facets() * (Math.min(r, cut) - r);
        // Weathering: crests and gullies
        r += params.ridges() * (noise.ridged(sx * 2.2, sy * 2.2, sz * 2.2, 5) - 0.55);
        // Cracks along the borders of cells
        double edge = noise.cellularEdge(sx * 2.6, sy * 2.6, sz * 2.6);
        r -= params.cracks() * Math.max(0, 1 - edge / 0.12);
        // Strata: bands across the height
        r += params.strata() * com.truesim.common.math.Maths.sin(y * 9 + 0.6 * noise.simplex(sx, sy * 0.3, sz));
        return Math.max(r, 0.2);
    }

    /** The largest gap between this level's edges and the surface at their midpoints, in metres. */
    private double error() {
        double worst = 0;
        double[] point = new double[3];
        for (int t = 0; t < triangleCount; t++) {
            for (int corner = 0; corner < 3; corner++) {
                int a = triangles[3 * t + corner];
                int b = triangles[3 * t + (corner + 1) % 3];
                if (a > b) {
                    continue; // each edge once
                }
                surface(a, point);
                double ax = point[0];
                double ay = point[1];
                double az = point[2];
                surface(b, point);
                double mx = 0.5 * (ax + point[0]);
                double my = 0.5 * (ay + point[1]);
                double mz = 0.5 * (az + point[2]);
                double dx = directions[3 * a] + directions[3 * b];
                double dy = directions[3 * a + 1] + directions[3 * b + 1];
                double dz = directions[3 * a + 2] + directions[3 * b + 2];
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                place(dx / l, dy / l, dz / l, point);
                double ex = point[0] - mx;
                double ey = point[1] - my;
                double ez = point[2] - mz;
                worst = Math.max(worst, Math.sqrt(ex * ex + ey * ey + ez * ez));
            }
        }
        return worst;
    }

    private void surface(int vertex, double[] out) {
        place(directions[3 * vertex], directions[3 * vertex + 1], directions[3 * vertex + 2], out);
    }

    /** The point of the rock in direction (x, y, z), in metres: scaled, set on the ground, cut flat below. */
    private void place(double x, double y, double z, double[] out) {
        double r = radius(x, y, z) * params.size();
        double height = params.flatness() * params.size();
        out[0] = x * r * params.elongation();
        out[1] = y * r * params.flatness() + height * (1 - 2 * params.buried());
        out[2] = z * r;
        out[1] = Math.max(out[1], -0.05 * params.size());
    }

    @AllowAllocation("the mesh made")
    private ProceduralMesh mesh(double error) {
        writer.reset();
        float[] positions = new float[3 * directionCount];
        double[] point = new double[3];
        for (int v = 0; v < directionCount; v++) {
            surface(v, point);
            positions[3 * v] = (float) point[0];
            positions[3 * v + 1] = (float) point[1];
            positions[3 * v + 2] = (float) point[2];
        }
        float[] normals = new float[3 * directionCount];
        Meshes.vertexNormals(positions, directionCount, triangles, 3 * triangleCount, normals);
        for (int v = 0; v < directionCount; v++) {
            double nx = normals[3 * v];
            double ny = normals[3 * v + 1];
            double nz = normals[3 * v + 2];
            // Tangent: +x across the normal (+z where the normal is along x)
            double tx = 1 - nx * nx;
            double ty = -nx * ny;
            double tz = -nx * nz;
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-3) {
                tx = -nz * nx;
                ty = -nz * ny;
                tz = 1 - nz * nz;
                tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            }
            writer.vertex(positions[3 * v], positions[3 * v + 1], positions[3 * v + 2], nx, ny, nz, tx / tl, ty / tl,
                tz / tl, positions[3 * v], positions[3 * v + 2]);
        }
        for (int t = 0; t < triangleCount; t++) {
            writer.triangle(triangles[3 * t], triangles[3 * t + 1], triangles[3 * t + 2]);
        }
        return writer.build(error);
    }

    // ---------------------------------------------------------------- the icosphere

    /** The unit icosahedron subdivided {@code level} times: shared vertices, counter-clockwise from outside. */
    @AllowAllocation("growing, until warm")
    private void icosphere(int level) {
        int vertices = 10 * (1 << (2 * level)) + 2;
        int faces = 20 * (1 << (2 * level));
        if (directions.length < 3 * vertices) {
            directions = new double[3 * vertices];
        }
        if (triangles.length < 3 * faces) {
            triangles = new int[3 * faces];
        }
        double g = (1 + Math.sqrt(5)) / 2;
        double[] base = {-1, g, 0, 1, g, 0, -1, -g, 0, 1, -g, 0, 0, -1, g, 0, 1, g, 0, -1, -g, 0, 1, -g, g, 0, -1, g,
            0, 1, -g, 0, -1, -g, 0, 1};
        int[] faces0 = {0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1,
            8, 3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1};
        directionCount = 0;
        for (int v = 0; v < 12; v++) {
            add(base[3 * v], base[3 * v + 1], base[3 * v + 2]);
        }
        System.arraycopy(faces0, 0, triangles, 0, faces0.length);
        triangleCount = 20;
        int[] next = new int[3 * faces];
        for (int l = 0; l < level; l++) {
            int capacity = Integer.highestOneBit(Math.max(16, triangleCount * 3)) << 1;
            if (edgeKeys.length < capacity) {
                edgeKeys = new long[capacity];
                edgeValues = new int[capacity];
            }
            Arrays.fill(edgeKeys, 0, capacity, -1);
            int count = 0;
            for (int t = 0; t < triangleCount; t++) {
                int a = triangles[3 * t];
                int b = triangles[3 * t + 1];
                int c = triangles[3 * t + 2];
                int ab = midpoint(a, b, capacity);
                int bc = midpoint(b, c, capacity);
                int ca = midpoint(c, a, capacity);
                int[] split = {a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca};
                System.arraycopy(split, 0, next, 3 * count, 12);
                count += 4;
            }
            System.arraycopy(next, 0, triangles, 0, 3 * count);
            triangleCount = count;
        }
    }

    private int add(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        directions[3 * directionCount] = x / l;
        directions[3 * directionCount + 1] = y / l;
        directions[3 * directionCount + 2] = z / l;
        return directionCount++;
    }

    /** The vertex halfway along edge a-b, made once per edge (an open-addressing table keyed by the edge). */
    private int midpoint(int a, int b, int capacity) {
        long key = (long) Math.min(a, b) << 32 | Math.max(a, b);
        int slot = (int) (Seeds.mix(key) & (capacity - 1));
        while (edgeKeys[slot] != -1) {
            if (edgeKeys[slot] == key) {
                return edgeValues[slot];
            }
            slot = (slot + 1) & (capacity - 1);
        }
        int m = add(directions[3 * a] + directions[3 * b], directions[3 * a + 1] + directions[3 * b + 1],
            directions[3 * a + 2] + directions[3 * b + 2]);
        edgeKeys[slot] = key;
        edgeValues[slot] = m;
        return m;
    }
}
