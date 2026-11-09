package com.truesim.common.procedural.rock;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Aabb;
import com.truesim.common.math.geometry.Meshes;
import com.truesim.common.math.layout.ElementBuffer;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.math.random.Noise;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates rocks. Each stone is a subdivided icosahedron whose every direction is pushed to the stone's surface, so
 * all levels of detail sample one surface:
 *
 * <ol>
 *   <li><b>Fractures:</b> a convex polytope, the intersection of half-spaces: for limestone blocks a bedding pair
 *       (its faces on bed boundaries) and two joint sets at right angles, then random fracture planes; its edges
 *       rounded by weathering as a soft minimum of the planes' distances ({@code -s log sum exp(-r / s)}), blended
 *       toward an ellipsoid for rounded boulders.</li>
 *   <li><b>Chips:</b> small planes cut the farthest corners off: fresh stone, its own submesh.</li>
 *   <li><b>Weathering:</b> lumps (fractal noise), crests and gullies (ridged noise), cracks along the borders of
 *       cellular noise's cells; fresh chips keep less of it.</li>
 *   <li><b>Bedding:</b> grooves where beds meet and each bed weathered back by its own share (softer beds recess),
 *       so bedded limestone shows its steps.</li>
 *   <li><b>On the ground:</b> scaled to its proportions, sunk by its burial and cut flat a little below the
 *       ground.</li>
 * </ol>
 *
 * A pile ({@link RockParams.Cluster}) lays further, smaller stones round the first, each its own closed surface. Moss,
 * lichen and the soil stain go into the vertex color ({@link RockCover}). Each level is closed and consistently wound,
 * its error the largest gap between its edges and the surface. The same parameters, cover and seed always give
 * bit-identical meshes.
 *
 * <p>Keeps its working memory from rock to rock; not thread-safe ({@link Rocks} keeps one per thread).
 */
@AllowAllocation("rocks are generated at load time, in the background")
public final class RockGenerator {

    /** Version of the generated output: changes whenever the same inputs would give different meshes. */
    public static final int VERSION = 3;

    private static final int MAX_PLANES = 48 + 6;
    /** The polytope's radius where no plane bounds it, in unit space. */
    private static final double CAP = 1.6;

    // the current stone
    private final double[] planes = new double[4 * MAX_PLANES];
    private final boolean[] chip = new boolean[MAX_PLANES];
    private int planeCount;
    private RockParams params;
    private Noise noise;
    private double ox;
    private double oy;
    private double oz;
    private double bx;
    private double by;
    private double bz;
    private double bedPhase;
    private long bedSeed;
    private boolean fresh;
    private double stoneX = 1;
    private double stoneY = 1;
    private double stoneZ = 1;

    // icospheres by subdivision level
    private final double[][] sphereDirections = new double[8][];
    private final int[][] sphereTriangles = new int[8][];

    /** A rock as {@code params} say, bare, made from {@code seed}. */
    public Rock generate(RockParams params, long seed) {
        return generate(params, RockCover.BARE, seed);
    }

    /** A rock as {@code params} say, with {@code cover}, made from {@code seed}. */
    public Rock generate(RockParams params, RockCover cover, long seed) {
        RockParams p = Double.isNaN(cover.buried()) ? params : params.withBuried(cover.buried());
        List<Integer> subdivisions = p.subdivisions();
        int levels = subdivisions.size();
        Level[] out = new Level[levels];
        for (int l = 0; l < levels; l++) {
            out[l] = new Level();
        }
        double[] errors = new double[levels];
        Rng layout = Rng.seeded(Seeds.derive(seed, 2));
        int members = p.cluster().members();
        double[] memberX = new double[members];
        double[] memberZ = new double[members];
        double[] memberScale = new double[members];
        double[] memberYaw = new double[members];
        double[] memberY = new double[members];
        memberScale[0] = 1;
        double angle = layout.nextDouble() * Maths.TAU;
        if (!p.cluster().heap()) {
            // a patch: stones over a disc by a sunflower spiral, sizes by a power law (exponent 2: many small)
            double lo = p.cluster().shrink();
            double radius = p.cluster().spread() * p.size();
            for (int m = 0; m < members; m++) {
                double u = layout.nextDouble();
                memberScale[m] = 1 / Math.sqrt(1 / (lo * lo) - u * (1 / (lo * lo) - 1));
                double a = angle + m * 2.39996 + 0.6 * (layout.nextDouble() - 0.5);
                double d = radius * Math.sqrt((m + 0.5) / members) * (0.85 + 0.3 * layout.nextDouble());
                memberX[m] = d * Maths.cos(a);
                memberZ[m] = d * Maths.sin(a);
                memberYaw[m] = layout.nextDouble() * Maths.TAU;
            }
        }
        for (int m = 1; m < members && p.cluster().heap(); m++) {
            double s = memberScale[m - 1] * p.cluster().shrink() * (0.88 + 0.24 * layout.nextDouble());
            memberScale[m] = m == 1 ? p.cluster().shrink() * (0.9 + 0.2 * layout.nextDouble()) : s;
            angle += 2.39996 + 0.5 * (layout.nextDouble() - 0.5); // the golden angle, jittered
            double distance = p.cluster().spread() * p.size() * (1 + memberScale[m]) * (0.85 + 0.3
                * layout.nextDouble());
            if (m >= 5) {
                // a second course, heaped on the first
                distance *= 0.4;
                memberY[m] = 0.9 * p.size() * p.flatness() * (1 - p.buried());
            }
            memberX[m] = distance * Maths.cos(angle);
            memberZ[m] = distance * Maths.sin(angle);
            memberYaw[m] = layout.nextDouble() * Maths.TAU;
        }
        int finest = 0;
        for (int l = 1; l < levels; l++) {
            if (subdivisions.get(l) > subdivisions.get(finest)) {
                finest = l;
            }
        }
        for (int m = 0; m < members; m++) {
            stone(p, Seeds.derive(seed, 100 + m));
            double size = p.size() * memberScale[m];
            // sink by the finest level's height: the same offset for every level
            double[] range = heightRange(subdivisions.get(finest), size);
            double lift = -range[0] - p.buried() * (range[1] - range[0]);
            double skirt = 0.05 * size;
            for (int l = 0; l < levels; l++) {
                errors[l] = Math.max(errors[l], write(out[l], subdivisions.get(l), size, lift, skirt,
                    memberX[m], memberY[m], memberZ[m], memberYaw[m], l == 0));
            }
        }
        double height = 0;
        for (int v = 0; v < out[0].vertices; v++) {
            height = Math.max(height, out[0].positions[3 * v + 1]);
        }
        List<ProceduralMesh> lods = new ArrayList<>(levels);
        for (int l = 1; l < levels; l++) {
            errors[l] = Math.max(errors[l], errors[l - 1]); // never less than the finer level's
        }
        for (int l = 0; l < levels; l++) {
            tint(out[l], cover, height, p.size(), seed);
            lods.add(out[l].build(l == 0 ? 0 : errors[l]));
        }
        return new Rock(params, cover, seed, lods, footprint(out[0], height));
    }

    // ------------------------------------------------------------------------------------------------ the stone

    /** Sets up the stone made from {@code seed}: its noise, bedding, fracture planes and chips. */
    private void stone(RockParams p, long seed) {
        params = p;
        noise = Noise.seeded(Seeds.derive(seed, 0));
        Rng rng = Rng.seeded(Seeds.derive(seed, 1));
        ox = rng.nextDouble() * 100;
        oy = rng.nextDouble() * 100;
        oz = rng.nextDouble() * 100;
        RockParams.Fractures f = p.fractures();
        RockParams.Bedding bedding = p.bedding();
        // the bedding's normal: up, tilted by the dip about a random horizontal axis
        double dip = Maths.toRadians(bedding.dip()) * (0.5 + rng.nextDouble());
        double axis = rng.nextDouble() * Maths.TAU;
        bx = Maths.sin(dip) * Maths.cos(axis);
        by = Maths.cos(dip);
        bz = Maths.sin(dip) * Maths.sin(axis);
        bedPhase = rng.nextDouble();
        bedSeed = rng.nextLong();
        // this stone's own proportions
        double irregular = f.irregularity();
        stoneX = 1 + irregular * (2 * rng.nextDouble() - 1);
        stoneY = 1 + irregular * (2 * rng.nextDouble() - 1);
        stoneZ = 1 + irregular * (2 * rng.nextDouble() - 1);
        planeCount = 0;
        if (f.joints() > 0) {
            // the bedding pair, on bed boundaries where the beds are thin enough to have one in reach
            plane(bx, by, bz, bedBoundary(rng.nextDouble(), true), false);
            plane(-bx, -by, -bz, bedBoundary(rng.nextDouble(), false), false);
            // two joint sets: across the bedding and across one another, turned off square by (1 - joints)
            double turn = rng.nextDouble() * Maths.TAU;
            double off = (1 - f.joints()) * Maths.toRadians(35);
            double[] u = perpendicular(bx, by, bz, turn);
            double[] w = cross(bx, by, bz, u[0], u[1], u[2]);
            double[] j1 = tilt(u, bx, by, bz, off * (rng.nextDouble() - 0.5) * 2);
            double[] j2 = tilt(w, bx, by, bz, off * (rng.nextDouble() - 0.5) * 2);
            double[] j2b = turnAbout(j2, bx, by, bz, off * (rng.nextDouble() - 0.5) * 2);
            plane(j1[0], j1[1], j1[2], 0.62 + 0.25 * rng.nextDouble(), false);
            plane(-j1[0], -j1[1], -j1[2], 0.62 + 0.25 * rng.nextDouble(), false);
            plane(j2b[0], j2b[1], j2b[2], 0.62 + 0.25 * rng.nextDouble(), false);
            plane(-j2b[0], -j2b[1], -j2b[2], 0.62 + 0.25 * rng.nextDouble(), false);
        }
        for (int k = 0; k < f.planes(); k++) {
            Vec3 n = rng.nextVec3OnSphere();
            plane(n.x(), n.y(), n.z(), f.near() + (f.far() - f.near()) * rng.nextDouble(), false);
        }
        // chips: cut the farthest of a few corners
        for (int k = 0; k < f.chips(); k++) {
            double[] dirs = sphere(2);
            int count = dirs.length / 3;
            double best = -1;
            double cx = 0;
            double cy = 1;
            double cz = 0;
            for (int t = 0; t < 4; t++) {
                int d = rng.nextInt(count);
                double x = dirs[3 * d];
                double y = dirs[3 * d + 1];
                double z = dirs[3 * d + 2];
                double r = polytope(x, y, z, false);
                if (r > best) {
                    best = r;
                    cx = x;
                    cy = y;
                    cz = z;
                }
            }
            // climb to the corner: the farthest of the directions near the pick
            for (int d = 0; d < count; d++) {
                double x = dirs[3 * d];
                double y = dirs[3 * d + 1];
                double z = dirs[3 * d + 2];
                if (x * cx + y * cy + z * cz > 0.9) {
                    double r = polytope(x, y, z, false);
                    if (r > best) {
                        best = r;
                        cx = x;
                        cy = y;
                        cz = z;
                    }
                }
            }
            double depth = f.chipDepth() * (0.6 + 0.8 * rng.nextDouble());
            plane(cx, cy, cz, Math.max(0.3, best - depth), true);
        }
    }

    private void plane(double x, double y, double z, double distance, boolean chipped) {
        double l = Math.sqrt(x * x + y * y + z * z);
        int k = planeCount++;
        planes[4 * k] = x / l;
        planes[4 * k + 1] = y / l;
        planes[4 * k + 2] = z / l;
        planes[4 * k + 3] = distance;
        chip[k] = chipped;
    }

    /** A bed boundary's distance from the centre along the bedding's normal (or its opposite), about 0.45 to 0.85. */
    private double bedBoundary(double random, boolean up) {
        double t = params.bedding().thickness();
        double target = 0.5 + 0.3 * random;
        if (t <= 0 || t > 0.4) {
            return target;
        }
        // boundaries lie where (s / t + phase) is whole: s = (n - phase) t upward, s = -(n - phase) t downward
        double phase = up ? bedPhase : 1 - bedPhase;
        double n = Math.floor(target / t + phase);
        double s = (n - phase) * t;
        if (s < 0.4) {
            s += t;
        }
        return s;
    }

    /** The polytope's radius along the unit direction, sharp or with its edges rounded; sets {@link #fresh}. */
    private double polytope(double x, double y, double z, boolean rounded) {
        double nearest = CAP;
        int which = -1;
        for (int k = 0; k < planeCount; k++) {
            double facing = x * planes[4 * k] + y * planes[4 * k + 1] + z * planes[4 * k + 2];
            if (facing > 1e-6) {
                double r = planes[4 * k + 3] / facing;
                if (r < nearest) {
                    nearest = r;
                    which = k;
                }
            }
        }
        fresh = which >= 0 && chip[which];
        double s = params.weathering().rounding();
        if (!rounded || s <= 0) {
            return nearest;
        }
        // soft minimum: -s log sum exp(-(r - nearest) / s), the cap counted as a plane
        double sum = Maths.exp(-(CAP - nearest) / s);
        for (int k = 0; k < planeCount; k++) {
            double facing = x * planes[4 * k] + y * planes[4 * k + 1] + z * planes[4 * k + 2];
            if (facing > 1e-6) {
                double r = planes[4 * k + 3] / facing;
                double e = (r - nearest) / s;
                if (e < 30) {
                    sum += Maths.exp(-e);
                }
            }
        }
        return nearest - s * Maths.log(sum);
    }

    /** The surface's distance from the centre along the unit direction (x, y, z), in unit space. */
    double radius(double x, double y, double z) {
        RockParams.Fractures f = params.fractures();
        RockParams.Weathering w = params.weathering();
        fresh = false;
        double poly = planeCount == 0 ? 1 : polytope(x, y, z, true);
        double r = 1 + f.facets() * (poly - 1);
        double worn = fresh ? 0.35 : 1;
        double sx = x * 1.3 + ox;
        double sy = y * 1.3 + oy;
        double sz = z * 1.3 + oz;
        r *= 1 + worn * w.lumpiness() * noise.fractal(sx, sy, sz, 3);
        r += worn * w.ridges() * (noise.ridged(sx * 2.2, sy * 2.2, sz * 2.2, 5) - 0.55);
        double edge = noise.cellularEdge(sx * 2.6, sy * 2.6, sz * 2.6);
        r -= worn * w.cracks() * Math.max(0, 1 - edge / 0.12);
        RockParams.Bedding b = params.bedding();
        if (b.thickness() > 0) {
            double s = r * (x * bx + y * by + z * bz) / b.thickness() + bedPhase
                + b.waviness() * noise.simplex(x * 1.7 + ox, z * 1.7 + oz);
            double bed = Math.floor(s);
            double inBed = s - bed;
            double groove = 1 - Maths.smoothstep(0, 0.2, Math.min(inBed, 1 - inBed));
            double recess = (Seeds.mix(bedSeed + (long) bed) >>> 11) * 0x1.0p-53;
            r -= worn * (b.groove() * groove + b.recess() * recess);
        }
        return Math.max(r, 0.2);
    }

    /** The lowest and highest y of the stone of {@code size} at subdivision {@code level}, before it is sunk. */
    private double[] heightRange(int level, double size) {
        double[] dirs = sphere(level);
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int v = 0; v < dirs.length / 3; v++) {
            double y = dirs[3 * v + 1] * radius(dirs[3 * v], dirs[3 * v + 1], dirs[3 * v + 2]) * size
                * params.flatness() * stoneY;
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        return new double[] {min, max};
    }

    /**
     * Writes the stone at subdivision {@code level} into {@code out}: scaled to {@code size}, lifted by {@code lift},
     * cut at {@code -skirt}, turned by {@code yaw} and moved to ({@code x}, {@code y}, {@code z}). Returns its error: the largest
     * gap between its edges' midpoints and the surface (0 when {@code exact}).
     */
    private double write(Level out, int level, double size, double lift, double skirt, double x, double y,
        double z, double yaw, boolean exact) {
        double[] dirs = sphere(level);
        int[] tris = sphereTriangles[level];
        int n = dirs.length / 3;
        float[] positions = new float[3 * n];
        double[] points = exact ? null : new double[3 * n];
        boolean[] fresh = new boolean[n];
        double cos = Maths.cos(yaw);
        double sin = Maths.sin(yaw);
        double[] point = new double[3];
        for (int v = 0; v < n; v++) {
            place(dirs[3 * v], dirs[3 * v + 1], dirs[3 * v + 2], size, lift, point);
            fresh[v] = this.fresh;
            if (points != null) {
                System.arraycopy(point, 0, points, 3 * v, 3);
            }
            positions[3 * v] = (float) (x + point[0] * cos + point[2] * sin);
            positions[3 * v + 1] = (float) (y + Math.max(point[1], -skirt));
            positions[3 * v + 2] = (float) (z - point[0] * sin + point[2] * cos);
        }
        float[] normals = new float[3 * n];
        Meshes.vertexNormals(positions, n, tris, tris.length, normals);
        out.add(positions, normals, fresh, n, tris);
        if (exact) {
            return 0;
        }
        double worst = 0;
        for (int t = 0; t < tris.length / 3; t++) {
            for (int corner = 0; corner < 3; corner++) {
                int i = tris[3 * t + corner];
                int j = tris[3 * t + (corner + 1) % 3];
                if (i > j) {
                    continue; // each edge once
                }
                double mx = 0.5 * (points[3 * i] + points[3 * j]);
                double my = 0.5 * (Math.max(points[3 * i + 1], -skirt) + Math.max(points[3 * j + 1], -skirt));
                double mz = 0.5 * (points[3 * i + 2] + points[3 * j + 2]);
                double dx = dirs[3 * i] + dirs[3 * j];
                double dy = dirs[3 * i + 1] + dirs[3 * j + 1];
                double dz = dirs[3 * i + 2] + dirs[3 * j + 2];
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                place(dx / l, dy / l, dz / l, size, lift, point);
                double ex = point[0] - mx;
                double ey = Math.max(point[1], -skirt) - my;
                double ez = point[2] - mz;
                worst = Math.max(worst, Math.sqrt(ex * ex + ey * ey + ez * ez));
            }
        }
        return worst;
    }

    /** The point of the stone in direction (x, y, z), metres: scaled to its proportions and lifted (not yet cut). */
    private void place(double x, double y, double z, double size, double lift, double[] out) {
        double r = radius(x, y, z) * size;
        out[0] = x * r * params.elongation() * stoneX;
        out[1] = y * r * params.flatness() * stoneY + lift;
        out[2] = z * r * stoneZ;
    }

    // ------------------------------------------------------------------------------------------------ cover

    /** The vertex colors of {@code level}: weathered or fresh stone, bedding, moss, lichen and the soil stain. */
    private void tint(Level level, RockCover cover, double height, double size, long seed) {
        Noise patches = Noise.seeded(Seeds.derive(seed, 3));
        double northX = Maths.sin(cover.yaw());
        double northZ = -Maths.cos(cover.yaw());
        double bedTone = params.bedding().thickness() > 0 ? 0.05 : 0;
        double band = 0.25 * height + 0.08;
        double stain = 0.05 * size + 0.02;
        double blotch = 1 / Math.max(0.06, 0.1 * size);
        for (int v = 0; v < level.vertices; v++) {
            double px = level.positions[3 * v];
            double py = level.positions[3 * v + 1];
            double pz = level.positions[3 * v + 2];
            double nx = level.normals[3 * v];
            double ny = level.normals[3 * v + 1];
            double nz = level.normals[3 * v + 2];
            double[] base = level.fresh[v] ? null : RockCover.WEATHERED_TINT;
            double r = base == null ? 1 : base[0];
            double g = base == null ? 1 : base[1];
            double b = base == null ? 1 : base[2];
            if (bedTone > 0) {
                double s = Math.floor(py / (params.bedding().thickness() * size) + bedPhase);
                double tone = 1 - bedTone * ((Seeds.mix(bedSeed ^ (long) s) >>> 11) * 0x1.0p-53);
                r *= tone;
                g *= tone;
                b *= tone;
            }
            double qx = px / size;
            double qy = py / size;
            double qz = pz / size;
            double patch = patches.fractal(qx * 1.7 + 11, qy * 1.7, qz * 1.7, 3);
            // lichen: blotches on lit, exposed faces
            double north = nx * northX + nz * northZ;
            double lit = 0.5 + 0.35 * ny - 0.35 * north;
            double moss = 0;
            if (cover.moss() > 0) {
                double damp = 0.55 * ny + 0.65 * north;
                double ground = 1 - Maths.smoothstep(0, band, py);
                double grow = 0.6 * damp + 0.7 * ground + 0.4 * patch + 0.8 * (cover.moss() - 0.5);
                moss = cover.moss() * Maths.smoothstep(0.1, 0.5, grow) * Maths.smoothstep(-0.7, -0.2, ny);
            }
            double lichen = 0;
            if (cover.lichen() > 0) {
                double cell = patches.cellular(px * blotch, py * blotch + 7, pz * blotch);
                double spot = 1 - Maths.smoothstep(0.35, 0.55, cell + 0.3 * (0.5 - cover.lichen()));
                lichen = cover.lichen() * spot * Maths.smoothstep(0.25, 0.6, lit) * (1 - moss);
                double[] crust = patches.simplex(px * 0.8, py * 0.8 + 3, pz * 0.8) > 0.3 && ny > 0.3
                    ? RockCover.ORANGE_TINT : RockCover.LICHEN_TINT;
                r += lichen * (crust[0] - r);
                g += lichen * (crust[1] - g);
                b += lichen * (crust[2] - b);
            }
            r += moss * (RockCover.MOSS_TINT[0] - r);
            g += moss * (RockCover.MOSS_TINT[1] - g);
            b += moss * (RockCover.MOSS_TINT[2] - b);
            double soil = 0.85 * (1 - Maths.smoothstep(-0.01, stain, py)) * (1 - 0.6 * moss);
            r += soil * (RockCover.SOIL_TINT[0] - r);
            g += soil * (RockCover.SOIL_TINT[1] - g);
            b += soil * (RockCover.SOIL_TINT[2] - b);
            level.colors[4 * v] = (float) Maths.clamp01(r);
            level.colors[4 * v + 1] = (float) Maths.clamp01(g);
            level.colors[4 * v + 2] = (float) Maths.clamp01(b);
            level.colors[4 * v + 3] = 1;
        }
    }

    /** The rock's contact with the ground and reach, from its most detailed level. */
    private static RockFootprint footprint(Level level, double height) {
        double halfX = 0;
        double halfZ = 0;
        double reach = 0;
        for (int v = 0; v < level.vertices; v++) {
            double x = level.positions[3 * v];
            double y = level.positions[3 * v + 1];
            double z = level.positions[3 * v + 2];
            reach = Math.max(reach, Math.sqrt(x * x + z * z));
            if (y <= 0) {
                halfX = Math.max(halfX, Math.abs(x));
                halfZ = Math.max(halfZ, Math.abs(z));
            }
        }
        return new RockFootprint(halfX, halfZ, reach, height);
    }

    // ------------------------------------------------------------------------------------------------ vectors

    /** A unit vector across (x, y, z), turned by {@code turn} about it. */
    private static double[] perpendicular(double x, double y, double z, double turn) {
        double[] a = Math.abs(x) < 0.9 ? new double[] {1, 0, 0} : new double[] {0, 0, 1};
        double[] u = cross(x, y, z, a[0], a[1], a[2]);
        double[] w = cross(x, y, z, u[0], u[1], u[2]);
        double c = Maths.cos(turn);
        double s = Maths.sin(turn);
        return normalize(u[0] * c + w[0] * s, u[1] * c + w[1] * s, u[2] * c + w[2] * s);
    }

    /** {@code v} tilted toward (x, y, z) by {@code angle}. */
    private static double[] tilt(double[] v, double x, double y, double z, double angle) {
        double c = Maths.cos(angle);
        double s = Maths.sin(angle);
        return normalize(v[0] * c + x * s, v[1] * c + y * s, v[2] * c + z * s);
    }

    /** {@code v} turned about the unit axis (x, y, z) by {@code angle} (Rodrigues). */
    private static double[] turnAbout(double[] v, double x, double y, double z, double angle) {
        double c = Maths.cos(angle);
        double s = Maths.sin(angle);
        double[] k = cross(x, y, z, v[0], v[1], v[2]);
        double dot = x * v[0] + y * v[1] + z * v[2];
        return normalize(v[0] * c + k[0] * s + x * dot * (1 - c), v[1] * c + k[1] * s + y * dot * (1 - c),
            v[2] * c + k[2] * s + z * dot * (1 - c));
    }

    private static double[] cross(double ax, double ay, double az, double bx, double by, double bz) {
        return normalize(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
    }

    private static double[] normalize(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / l, y / l, z / l};
    }

    // ------------------------------------------------------------------------------------------------ the icosphere

    /** The unit icosahedron's vertex directions subdivided {@code level} times (its triangles alongside). */
    private double[] sphere(int level) {
        if (sphereDirections[level] == null) {
            icosphere(level);
        }
        return sphereDirections[level];
    }

    /** The unit icosahedron subdivided {@code level} times: shared vertices, counter-clockwise from outside. */
    private void icosphere(int level) {
        int vertices = 10 * (1 << (2 * level)) + 2;
        int faces = 20 * (1 << (2 * level));
        double[] directions = new double[3 * vertices];
        int[] triangles = new int[3 * faces];
        double g = (1 + Math.sqrt(5)) / 2;
        double[] base = {-1, g, 0, 1, g, 0, -1, -g, 0, 1, -g, 0, 0, -1, g, 0, 1, g, 0, -1, -g, 0, 1, -g, g, 0, -1, g,
            0, 1, -g, 0, -1, -g, 0, 1};
        int[] faces0 = {0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1,
            8, 3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1};
        int[] count = {0};
        for (int v = 0; v < 12; v++) {
            add(directions, count, base[3 * v], base[3 * v + 1], base[3 * v + 2]);
        }
        System.arraycopy(faces0, 0, triangles, 0, faces0.length);
        int triangleCount = 20;
        int[] next = new int[3 * faces];
        for (int l = 0; l < level; l++) {
            int capacity = Integer.highestOneBit(Math.max(16, triangleCount * 3)) << 1;
            long[] keys = new long[capacity];
            int[] values = new int[capacity];
            Arrays.fill(keys, -1);
            int made = 0;
            for (int t = 0; t < triangleCount; t++) {
                int a = triangles[3 * t];
                int b = triangles[3 * t + 1];
                int c = triangles[3 * t + 2];
                int ab = midpoint(directions, count, keys, values, a, b);
                int bc = midpoint(directions, count, keys, values, b, c);
                int ca = midpoint(directions, count, keys, values, c, a);
                int[] split = {a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca};
                System.arraycopy(split, 0, next, 3 * made, 12);
                made += 4;
            }
            System.arraycopy(next, 0, triangles, 0, 3 * made);
            triangleCount = made;
        }
        sphereDirections[level] = directions;
        sphereTriangles[level] = triangles;
    }

    private static int add(double[] directions, int[] count, double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        int i = count[0]++;
        directions[3 * i] = x / l;
        directions[3 * i + 1] = y / l;
        directions[3 * i + 2] = z / l;
        return i;
    }

    /** The vertex halfway along edge a-b, made once per edge (an open-addressing table keyed by the edge). */
    private static int midpoint(double[] directions, int[] count, long[] keys, int[] values, int a, int b) {
        int capacity = keys.length;
        long key = (long) Math.min(a, b) << 32 | Math.max(a, b);
        int slot = (int) (Seeds.mix(key) & (capacity - 1));
        while (keys[slot] != -1) {
            if (keys[slot] == key) {
                return values[slot];
            }
            slot = (slot + 1) & (capacity - 1);
        }
        int m = add(directions, count, directions[3 * a] + directions[3 * b], directions[3 * a + 1]
            + directions[3 * b + 1], directions[3 * a + 2] + directions[3 * b + 2]);
        keys[slot] = key;
        values[slot] = m;
        return m;
    }

    // ------------------------------------------------------------------------------------------------ meshes

    /** One level's stones gathered: weathered triangles and fresh ones, then written as a mesh. */
    private static final class Level {
        float[] positions = new float[0];
        float[] normals = new float[0];
        float[] colors = new float[0];
        boolean[] fresh = new boolean[0];
        int vertices;
        int[] rock = new int[0];
        int rockCount;
        int[] chips = new int[0];
        int chipCount;

        void add(float[] p, float[] n, boolean[] f, int count, int[] tris) {
            int base = vertices;
            positions = Arrays.copyOf(positions, 3 * (base + count));
            normals = Arrays.copyOf(normals, 3 * (base + count));
            colors = Arrays.copyOf(colors, 4 * (base + count));
            fresh = Arrays.copyOf(fresh, base + count);
            System.arraycopy(p, 0, positions, 3 * base, 3 * count);
            System.arraycopy(n, 0, normals, 3 * base, 3 * count);
            System.arraycopy(f, 0, fresh, base, count);
            vertices += count;
            rock = Arrays.copyOf(rock, rockCount + tris.length);
            chips = Arrays.copyOf(chips, chipCount + tris.length);
            for (int t = 0; t < tris.length; t += 3) {
                int a = tris[t];
                int b = tris[t + 1];
                int c = tris[t + 2];
                boolean chipped = f[a] && f[b] && f[c];
                int[] into = chipped ? chips : rock;
                int at = chipped ? chipCount : rockCount;
                into[at] = base + a;
                into[at + 1] = base + b;
                into[at + 2] = base + c;
                if (chipped) {
                    chipCount += 3;
                } else {
                    rockCount += 3;
                }
            }
        }

        /** The mesh: vertices in the order the triangles first use them, weathered triangles then fresh ones. */
        ProceduralMesh build(double error) {
            int[] indices = new int[rockCount + chipCount];
            System.arraycopy(rock, 0, indices, 0, rockCount);
            System.arraycopy(chips, 0, indices, rockCount, chipCount);
            int[] order = new int[vertices];
            Arrays.fill(order, -1);
            int used = 0;
            for (int i = 0; i < indices.length; i++) {
                int v = indices[i];
                if (order[v] < 0) {
                    order[v] = used++;
                }
                indices[i] = order[v];
            }
            ElementBuffer buffer = ElementBuffer.onHeap(ProceduralVertex.LAYOUT, used);
            Vec3Attribute position = buffer.attribute(ProceduralVertex.POSITION);
            Vec3Attribute normal = buffer.attribute(ProceduralVertex.NORMAL);
            Vec3Attribute tangent = buffer.attribute(ProceduralVertex.TANGENT);
            Vec2Attribute uv = buffer.attribute(ProceduralVertex.UV);
            Vec2Attribute pivot = buffer.attribute(ProceduralVertex.WIND_PIVOT);
            Vec4Attribute color = buffer.attribute(ProceduralVertex.WIND);
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int v = 0; v < vertices; v++) {
                int o = order[v];
                if (o < 0) {
                    continue;
                }
                float x = positions[3 * v];
                float y = positions[3 * v + 1];
                float z = positions[3 * v + 2];
                double nx = normals[3 * v];
                double ny = normals[3 * v + 1];
                double nz = normals[3 * v + 2];
                // tangent: +x across the normal (+z where the normal is along x)
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
                position.set(o, x, y, z);
                normal.set(o, nx, ny, nz);
                tangent.set(o, tx / tl, ty / tl, tz / tl);
                uv.set(o, x, z);
                pivot.set(o, 0, 0);
                color.set(o, colors[4 * v], colors[4 * v + 1], colors[4 * v + 2], colors[4 * v + 3]);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
            Aabb bounds = used == 0 ? new Aabb(Vec3.ZERO, Vec3.ZERO)
                : new Aabb(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
            return new ProceduralMesh(buffer, indices, new int[] {0, rockCount, rockCount + chipCount}, bounds,
                error);
        }
    }
}
