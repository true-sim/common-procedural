package com.truesim.common.procedural.tree;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.curve.PolylineFrames;
import com.truesim.common.math.random.Hash;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.Arrays;

/**
 * Meshes a grown skeleton: every branch as a generalized cylinder (rings of vertices swept along the branch with
 * rotation-minimizing frames, radii from the pipe model), closed at both ends, and the leaf cards. Reused from tree
 * to tree; not thread-safe.
 *
 * <p><b>Junctions.</b> A branch starts on its parent's axis, inside the parent, so the two surfaces meet without a gap
 * whatever the angle; around where it leaves the parent's surface it flares (a branch collar, a third wider, easing
 * back over two radii) with normals following the flare, so the joint reads as grown rather than stuck on. Each
 * branch is a closed surface of its own (welded across its texture seams: every edge joins two triangles, wound
 * consistently); branches overlap their parents rather than being joined into one surface.
 *
 * <p><b>Bark.</b> u runs around a branch (a whole number of tiles, by its girth at the base), v along it in tiles of
 * {@code barkTileLength}, continuing from where the branch starts on its parent. v is kept below 4 by restarting it
 * 2 tiles lower at a duplicated ring (the texture tiles, so the restart is invisible), which keeps float16 UVs
 * precise.
 */
final class TreeMesher {

    private static final double COLLAR = 0.35;
    private static final double BARK_WRAP = 2;
    /** Branches at least this thick at their base get rings of their own for the collar. */
    private static final double COLLAR_RINGS_FROM = 0.02;

    private final MeshWriter writer = new MeshWriter();
    private double[] stationS = new double[64];
    private double[] stationR = new double[64];
    private double[] points = new double[3 * 64];
    private double[] tangents = new double[3 * 64];
    private double[] normals = new double[3 * 64];
    private double[] arc = new double[64];
    private int stations;

    /** Everything about a tree the mesher reads besides its skeleton. */
    record Context(TreeSpecies species, Season season, TreeWind wind, double[] barkV, double crownX, double crownY,
        double crownZ, double day, boolean alt, Attachments attachments) {
    }

    /**
     * The level {@code lod} of the tree, leaving out leaf cards but one in {@code keep}, branches below
     * {@code minRadius}, flowers and fruits at {@code attachmentDetail}.
     */
    ProceduralMesh mesh(Skeleton skeleton, Context context, TreeLod lod, int keep, double minRadius,
        int attachmentDetail, double error) {
        writer.reset();
        for (int b = 0; b < skeleton.branchCount(); b++) {
            if (skeleton.radius(skeleton.firstNode(b)) >= minRadius) {
                branch(skeleton, context, lod, b);
            }
        }
        writer.nextSubmesh();
        Foliage foliage = context.species().foliage();
        if (foliage.shown(context.season())) {
            for (int i = 0; i < skeleton.leafCount(); i++) {
                if (Integer.remainderUnsigned(Hash.pcg(i), keep) == 0) {
                    leaf(skeleton, context, i, Math.sqrt(keep));
                }
            }
        }
        writer.nextSubmesh();
        context.attachments().mesh(writer, context.species().fruiting(), context.wind(), context.day(),
            attachmentDetail, context.alt());
        return writer.build(error);
    }

    /** Triangles the mesher made last. */
    int triangles() {
        return writer.triangleCount();
    }

    @AllowAllocation("growing, until warm")
    private void ensure(int count) {
        if (stationS.length < count) {
            int capacity = 2 * count;
            stationS = Arrays.copyOf(stationS, capacity);
            stationR = Arrays.copyOf(stationR, capacity);
            points = Arrays.copyOf(points, 3 * capacity);
            tangents = Arrays.copyOf(tangents, 3 * capacity);
            normals = Arrays.copyOf(normals, 3 * capacity);
            arc = Arrays.copyOf(arc, capacity);
        }
    }

    private void branch(Skeleton skeleton, Context context, TreeLod lod, int b) {
        int first = skeleton.firstNode(b);
        int count = skeleton.nodeCount(b);
        int level = skeleton.level(b);
        ensure(count + 8);
        arc[0] = 0;
        for (int i = 1; i < count; i++) {
            arc[i] = arc[i - 1] + Axes.distance(skeleton, first + i, first + i - 1);
        }
        double length = arc[count - 1];
        if (length <= 0) {
            return;
        }
        double r0 = skeleton.radius(first);
        // Stations: every ringStride-th node, the last, and the collar's or root flare's extra rings
        stations = 0;
        int stride = level >= 2 ? lod.ringStride() + 1 : lod.ringStride();
        for (int i = 0; i < count; i++) {
            if (i % stride == 0 || i == count - 1) {
                addStation(arc[i]);
            }
        }
        double emerge = 0;
        if (level > 0) {
            emerge = Math.min(skeleton.radius(skeleton.parent(first)) * 0.9, length * 0.3);
            if (level == 1 || r0 >= COLLAR_RINGS_FROM) { // thin twigs flare without rings of their own
                addStation(emerge);
                if (emerge + 2 * r0 < length * 0.6) {
                    addStation(emerge + 2 * r0);
                }
            }
        } else if (context.species().rootFlare() > 0) {
            for (double f : ROOT_STATIONS) {
                if (f * r0 < length / 3) {
                    addStation(f * r0);
                }
            }
        }
        // Positions and radii at the stations
        int node = 0;
        for (int k = 0; k < stations; k++) {
            double s = stationS[k];
            while (node < count - 2 && arc[node + 1] < s) {
                node++;
            }
            double span = arc[node + 1] - arc[node];
            double t = span > 0 ? Math.clamp((s - arc[node]) / span, 0, 1) : 0;
            int a = first + node;
            points[3 * k] = Maths.lerp(skeleton.x(a), skeleton.x(a + 1), t);
            points[3 * k + 1] = Maths.lerp(skeleton.y(a), skeleton.y(a + 1), t);
            points[3 * k + 2] = Maths.lerp(skeleton.z(a), skeleton.z(a + 1), t);
            double r = Maths.lerp(skeleton.radius(a), skeleton.radius(a + 1), t);
            if (level > 0) {
                r *= 1 + COLLAR * Math.clamp(1 - (s - emerge) / (2 * r0), 0, 1);
            } else {
                r *= 1 + context.species().rootFlare() * Maths.exp(-s / (1.2 * r0));
            }
            stationR[k] = r;
        }
        if (level == 0) {
            PolylineFrames.compute(points, stations, 1, 0, 0, tangents, normals);
        } else {
            PolylineFrames.compute(points, stations, 0, 1, 0, tangents, normals);
        }
        int sides = lod.sides(level);
        double repeats = Math.max(1, Math.round(Maths.TAU * r0 / context.species().barkTileWidth()));
        double vBase = context.barkV()[first];
        double offset = Math.floor(vBase / BARK_WRAP) * BARK_WRAP;
        double tileLength = context.species().barkTileLength();
        TreeWind wind = context.wind();
        int previous = -1;
        int firstRing = -1;
        for (int k = 0; k < stations; k++) {
            double v = vBase + stationS[k] / tileLength;
            wind.apply(writer, b, stationS[k], points[3 * k + 1]);
            if (previous >= 0 && v - offset > 2 * BARK_WRAP) {
                // Restart v two tiles lower on a copy of the last ring
                offset += BARK_WRAP;
                wind.apply(writer, b, stationS[k - 1], points[3 * k - 2]);
                int copy = ring(k - 1, sides, repeats, vBase + stationS[k - 1] / tileLength - offset, previous);
                wind.apply(writer, b, stationS[k], points[3 * k + 1]);
                previous = copy;
            }
            int ring = ring(k, sides, repeats, v - offset, -1);
            if (previous >= 0) {
                for (int j = 0; j < sides; j++) {
                    writer.quad(previous + j, previous + j + 1, ring + j + 1, ring + j);
                }
            } else {
                firstRing = ring;
            }
            previous = ring;
        }
        double baseV = vBase - Math.floor(vBase / BARK_WRAP) * BARK_WRAP;
        caps(previous, sides, repeats, vBase + stationS[stations - 1] / tileLength - offset);
        // The base cap's center, with the base's wind
        wind.apply(writer, b, 0, points[1]);
        int center = writer.vertex(points[0], points[1], points[2], -tangents[0], -tangents[1], -tangents[2],
            normals[0], normals[1], normals[2], repeats * 0.5, baseV);
        for (int j = 0; j < sides; j++) {
            writer.triangle(firstRing + j + 1, firstRing + j, center);
        }
    }

    private static final double[] ROOT_STATIONS = {0.25, 0.6, 1.2, 2.4};

    /** Adds a station at arc length {@code s}, keeping them sorted and apart. */
    private void addStation(double s) {
        int k = stations;
        while (k > 0 && stationS[k - 1] > s) {
            k--;
        }
        if ((k > 0 && s - stationS[k - 1] < 1e-4) || (k < stations && stationS[k] - s < 1e-4)) {
            return;
        }
        System.arraycopy(stationS, k, stationS, k + 1, stations - k);
        stationS[k] = s;
        stations++;
    }

    /**
     * Writes the ring of station {@code k} with v {@code v}: sides + 1 vertices, the last at the first's position
     * (the u seam). With {@code copyOf} at least 0, its positions are copied from that ring bit for bit. Returns the
     * first vertex.
     */
    private int ring(int k, int sides, double repeats, double v, int copyOf) {
        double tx = tangents[3 * k];
        double ty = tangents[3 * k + 1];
        double tz = tangents[3 * k + 2];
        double nx = normals[3 * k];
        double ny = normals[3 * k + 1];
        double nz = normals[3 * k + 2];
        double bx = ty * nz - tz * ny;
        double by = tz * nx - tx * nz;
        double bz = tx * ny - ty * nx;
        double r = stationR[k];
        // The radius' slope tilts the normals: d r / d s by neighbouring stations
        int lo = Math.max(k - 1, 0);
        int hi = Math.min(k + 1, stations - 1);
        double ds = stationS[hi] - stationS[lo];
        double slope = ds > 0 ? (stationR[hi] - stationR[lo]) / ds : 0;
        int start = -1;
        for (int j = 0; j <= sides; j++) {
            double angle = Maths.TAU * (j % sides) / sides;
            double c = Maths.cos(angle);
            double s = Maths.sin(angle);
            double rx = c * nx + s * bx;
            double ry = c * ny + s * by;
            double rz = c * nz + s * bz;
            double mx = rx - slope * tx;
            double my = ry - slope * ty;
            double mz = rz - slope * tz;
            double ml = Math.sqrt(mx * mx + my * my + mz * mz);
            double u = repeats * j / sides;
            int vertex;
            if (copyOf >= 0) {
                vertex = writer.copyPosition(copyOf + j, mx / ml, my / ml, mz / ml, -s * nx + c * bx,
                    -s * ny + c * by, -s * nz + c * bz, u, v);
            } else if (j == sides) {
                vertex = writer.copyPosition(start, mx / ml, my / ml, mz / ml, -s * nx + c * bx, -s * ny + c * by,
                    -s * nz + c * bz, u, v);
            } else {
                vertex = writer.vertex(points[3 * k] + r * rx, points[3 * k + 1] + r * ry,
                    points[3 * k + 2] + r * rz, mx / ml, my / ml, mz / ml, -s * nx + c * bx, -s * ny + c * by,
                    -s * nz + c * bz, u, v);
            }
            if (j == 0) {
                start = vertex;
            }
        }
        return start;
    }

    /** Closes the branch's tip: a cone to a point past it. */
    private void caps(int lastRing, int sides, double repeats, double tipV) {
        int k = stations - 1;
        double tx = tangents[3 * k];
        double ty = tangents[3 * k + 1];
        double tz = tangents[3 * k + 2];
        double reach = stationR[k] * 1.5;
        int apex = -1;
        for (int j = 0; j < sides; j++) {
            double u = repeats * (j + 0.5) / sides;
            int vertex = apex < 0
                ? writer.vertex(points[3 * k] + tx * reach, points[3 * k + 1] + ty * reach,
                    points[3 * k + 2] + tz * reach, tx, ty, tz, normals[3 * k], normals[3 * k + 1],
                    normals[3 * k + 2], u, tipV + 0.05)
                : writer.copyPosition(apex, tx, ty, tz, normals[3 * k], normals[3 * k + 1], normals[3 * k + 2], u,
                    tipV + 0.05);
            if (apex < 0) {
                apex = vertex;
            }
            writer.triangle(lastRing + j, lastRing + j + 1, vertex);
        }
    }

    private void leaf(Skeleton skeleton, Context context, int i, double scale) {
        Foliage foliage = context.species().foliage();
        double ox = skeleton.leaf(i, 0);
        double oy = skeleton.leaf(i, 1);
        double oz = skeleton.leaf(i, 2);
        double dx = skeleton.leaf(i, 3);
        double dy = skeleton.leaf(i, 4);
        double dz = skeleton.leaf(i, 5);
        double gx = skeleton.leaf(i, 6);
        double gy = skeleton.leaf(i, 7);
        double gz = skeleton.leaf(i, 8);
        double length = skeleton.leaf(i, 9) * scale;
        double width = skeleton.leaf(i, 10) * scale;
        // Across the card: direction x normal, so normal x across = direction (bitangent along +v)
        double wx = dy * gz - dz * gy;
        double wy = dz * gx - dx * gz;
        double wz = dx * gy - dy * gx;
        double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
        wx /= wl;
        wy /= wl;
        wz /= wl;
        // Shading normal: the card's, bent halfway out of the crown, kept across the card's width
        double cx = ox + dx * length * 0.5 - context.crownX();
        double cy = oy + dy * length * 0.5 - context.crownY();
        double cz = oz + dz * length * 0.5 - context.crownZ();
        double cl = Math.max(Math.sqrt(cx * cx + cy * cy + cz * cz), 1e-9);
        double sx = gx + cx / cl;
        double sy = gy + cy / cl;
        double sz = gz + cz / cl;
        double along = sx * wx + sy * wy + sz * wz;
        sx -= along * wx;
        sy -= along * wy;
        sz -= along * wz;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-6) {
            sx = gx;
            sy = gy;
            sz = gz;
            sl = 1;
        }
        sx /= sl;
        sy /= sl;
        sz /= sl;
        int tile = foliage.tile(context.season());
        double u0 = (double) (tile % foliage.atlasColumns()) / foliage.atlasColumns();
        double v0 = (double) (tile / foliage.atlasColumns()) / foliage.atlasRows();
        double du = 1.0 / foliage.atlasColumns();
        double dv = 1.0 / foliage.atlasRows();
        double hw = width * 0.5;
        int node = skeleton.leafNode(i);
        context.wind().applyNode(writer, node, 0);
        int a = writer.vertex(ox - wx * hw, oy - wy * hw, oz - wz * hw, sx, sy, sz, wx, wy, wz, u0, v0);
        int b = writer.vertex(ox + wx * hw, oy + wy * hw, oz + wz * hw, sx, sy, sz, wx, wy, wz, u0 + du, v0);
        context.wind().applyNode(writer, node, foliage.flutter());
        double fx = ox + dx * length;
        double fy = oy + dy * length;
        double fz = oz + dz * length;
        int c = writer.vertex(fx + wx * hw, fy + wy * hw, fz + wz * hw, sx, sy, sz, wx, wy, wz, u0 + du, v0 + dv);
        int d = writer.vertex(fx - wx * hw, fy - wy * hw, fz - wz * hw, sx, sy, sz, wx, wy, wz, u0, v0 + dv);
        writer.quad(a, b, c, d);
    }
}
