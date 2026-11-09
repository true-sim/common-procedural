package com.truesim.common.procedural.water;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Aabb;
import com.truesim.common.math.layout.ElementBuffer;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/** Water's meshes: channel surfaces chunk by chunk, falls' sheets and jets, with {@link WaterVertex}'s data. */
final class WaterMeshes {

    private final WaterNetwork network;
    private final WaterParameters p;

    WaterMeshes(WaterNetwork network) {
        this.network = network;
        this.p = network.parameters();
    }

    /** The surfaces of the segments {@code segments} (channel and first point pairs) relative to the origin. */
    ProceduralMesh surface(int[] segments, double originX, double originZ, List<BedStone> nearby) {
        Writer w = new Writer();
        Obstacles obstacles = new Obstacles(nearby);
        int across = p.surface().across();
        int k = 0;
        while (k < segments.length) {
            // a run of consecutive segments of one channel: one strip
            int c = segments[k];
            int first = segments[k + 1];
            int last = first;
            k += 2;
            while (k < segments.length && segments[k] == c && segments[k + 1] == last + 1) {
                last++;
                k += 2;
            }
            Channel channel = network.channels().get(c);
            int base = w.count;
            for (int i = first; i <= last + 1; i++) {
                section(w, channel, i, across, originX, originZ, obstacles);
            }
            for (int row = 0; row <= last - first; row++) {
                int a = base + row * (across + 1);
                int b = a + across + 1;
                for (int q = 0; q < across; q++) {
                    w.quad(a + q, a + q + 1, b + q + 1, b + q);
                }
            }
        }
        return w.build();
    }

    private void section(Writer w, Channel c, int i, int across, double originX, double originZ,
        Obstacles obstacles) {
        double dx = c.directionX(i);
        double dz = c.directionZ(i);
        double rx = -dz;
        double rz = dx;
        int i0 = Math.max(0, i - 1);
        int i1 = Math.min(c.count() - 1, i + 1);
        double run = c.distance(i1) - c.distance(i0);
        double drop = run > 0 ? (c.surface(i0) - c.surface(i1)) / run : 0;
        // tangent down the surface; normal = right x tangent
        double tl = Math.sqrt(1 + drop * drop);
        double tx = dx / tl;
        double ty = -drop / tl;
        double tz = dz / tl;
        double nx = -rz * ty;
        double ny = rz * tx - rx * tz;
        double nz = rx * ty;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double hw = c.width(i) / 2;
        double foam = Maths.smoothstep(0.7, 1.6, c.froude(i));
        foam = Math.max(foam, plunge(c, i));
        for (int q = 0; q <= across; q++) {
            double u = -1 + 2.0 * q / across;
            double x = c.x(i) + rx * u * hw;
            double z = c.z(i) + rz * u * hw;
            double depth = Math.max(0.01, 1.5 * c.depth(i) * (1 - u * u));
            double f = Math.max(foam, obstacles.foam(x, z, c.surface(i)));
            w.vertex(x - originX, c.surface(i), z - originZ, nx / nl, ny / nl, nz / nl, tx, ty, tz, c.speed(i), u,
                depth, 0, f, Maths.smoothstep(0.6, 1, Math.abs(u)), 0);
        }
    }

    /** Foam below a fall: white water over a few widths downstream of its foot. */
    private double plunge(Channel c, int i) {
        double foam = 0;
        for (Waterfall fall : network.falls()) {
            if (fall.channel() == c.id() && i >= fall.foot()) {
                double length = 4 * fall.width() + 10;
                double d = c.distance(i) - c.distance(fall.foot());
                foam = Math.max(foam, 1 - Maths.clamp01(d / length));
            }
        }
        return foam;
    }

    /** A free fall's sheet (submesh 0) and jets (submesh 1), relative to its lip. */
    ProceduralMesh fall(Waterfall fall) {
        Writer w = new Writer();
        Channel channel = network.channels().get(fall.channel());
        double ox = channel.x(fall.lip());
        double oy = channel.surface(fall.lip());
        double oz = channel.z(fall.lip());
        Waterfall.Path path = fall.path();
        double dx = channel.directionX(fall.lip());
        double dz = channel.directionZ(fall.lip());
        double rx = -dz;
        double rz = dx;
        WaterParameters.Falls f = p.falls();
        int across = p.surface().across();
        double perWidth = fall.discharge() / fall.width();
        for (int k = 0; k < path.count(); k++) {
            double fallen = path.fallen(k);
            double speed = Math.max(1e-3, path.speed(k));
            double hw = path.width()[k] / 2;
            double thickness = perWidth / speed * fall.width() / path.width()[k];
            double spray = 1 - Maths.exp(-fallen / f.atomisation());
            double foam = Maths.clamp01(0.25 + 0.75 * fallen / fall.breakup());
            for (int q = 0; q <= across; q++) {
                double u = -1 + 2.0 * q / across;
                w.vertex(path.x()[k] + rx * u * hw - ox, path.y()[k] - oy, path.z()[k] + rz * u * hw - oz, dx, 0, dz,
                    path.vx()[k] / speed, path.vy()[k] / speed, path.vz()[k] / speed, speed, u, thickness,
                    fallen / fall.breakup(), foam, Maths.smoothstep(0.6, 1, Math.abs(u)), spray);
            }
        }
        for (int k = 0; k + 1 < path.count(); k++) {
            int a = k * (across + 1);
            int b = a + across + 1;
            for (int q = 0; q < across; q++) {
                w.quad(a + q, a + q + 1, b + q + 1, b + q);
            }
        }
        w.nextSubmesh();
        jets(w, fall, ox, oy, oz, dx, dz);
        return w.build();
    }

    /**
     * The jets a sheet breaks into: one every {@link WaterParameters.Falls#jetSpacing} metres across it, each from a
     * little below the break-up down to where it has atomised (an exponential length of mean half the atomisation
     * length), drifting sideways and narrowing.
     */
    private void jets(Writer w, Waterfall fall, double ox, double oy, double oz, double dx, double dz) {
        WaterParameters.Falls f = p.falls();
        Waterfall.Path path = fall.path();
        double height = fall.height();
        if (path.count() < 2 || fall.breakup() >= height) {
            return;
        }
        double rx = -dz;
        double rz = dx;
        int jets = Math.max(1, (int) Math.round(fall.width() / f.jetSpacing()));
        SplittableRandom random = new SplittableRandom(Seeds.derive(p.seed(), 7919L * (fall.id() + 1)));
        double drift = Maths.tan(Maths.toRadians(3 * f.spread()));
        for (int j = 0; j < jets; j++) {
            double start = fall.breakup() * (1 + 0.3 * random.nextDouble());
            double end = Math.min(height, start - 0.5 * f.atomisation() * Maths.log(1 - random.nextDouble()));
            double u0 = (random.nextDouble() * 2 - 1);
            double side = (random.nextDouble() * 2 - 1) * drift;
            double out = random.nextDouble();
            double speedScale = 0.85 + 0.3 * random.nextDouble();
            double width0 = Math.max(0.1, 0.7 * fall.width() / jets);
            int base = w.count;
            int rows = 0;
            for (int k = 0; k < path.count(); k++) {
                double fallen = path.fallen(k);
                if (fallen < start || fallen > end) {
                    continue;
                }
                double along = (fallen - start) / Math.max(1e-6, end - start);
                double offset = u0 * path.width()[k] / 2 + side * (fallen - start);
                double half = 0.5 * width0 * (1 - 0.7 * along);
                double speed = Math.max(1e-3, path.speed(k)) * speedScale;
                double cx = path.x()[k] + rx * offset + dx * out;
                double cz = path.z()[k] + rz * offset + dz * out;
                double norm = Math.max(1e-3, path.speed(k));
                for (int q = 0; q <= 1; q++) {
                    double u = q == 0 ? -1 : 1;
                    w.vertex(cx + rx * u * half - ox, path.y()[k] - oy, cz + rz * u * half - oz, dx, 0, dz,
                        path.vx()[k] / norm, path.vy()[k] / norm, path.vz()[k] / norm, speed, u, 2 * half,
                        fallen / fall.breakup(), 1, 1, 1 - Maths.exp(-fallen / f.atomisation()));
                }
                rows++;
            }
            for (int r = 0; r + 1 < rows; r++) {
                int a = base + 2 * r;
                w.quad(a, a + 1, a + 3, a + 2);
            }
        }
    }

    /** Emergent stones near a chunk, bucketed, for the foam round them. */
    private static final class Obstacles {
        private static final double CELL = 8;
        private final java.util.Map<Long, List<BedStone>> cells = new java.util.HashMap<>();

        Obstacles(List<BedStone> stones) {
            for (BedStone s : stones) {
                if (s.emergent()) {
                    cells.computeIfAbsent(key(Math.floor(s.x() / CELL), Math.floor(s.z() / CELL)),
                        k -> new ArrayList<>()).add(s);
                }
            }
        }

        private static long key(double i, double j) {
            return (long) i * 0x9E3779B97F4A7C15L ^ (long) j;
        }

        /** Foam at ({@code x}, {@code z}) round emergent stones: a ring a stone's size wide. */
        double foam(double x, double z, double surface) {
            if (cells.isEmpty()) {
                return 0;
            }
            double foam = 0;
            double ci = Math.floor(x / CELL);
            double cj = Math.floor(z / CELL);
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    List<BedStone> list = cells.get(key(ci + di, cj + dj));
                    if (list == null) {
                        continue;
                    }
                    for (BedStone s : list) {
                        double ex = x - s.x();
                        double ez = z - s.z();
                        double r = Math.sqrt(ex * ex + ez * ez) / s.size();
                        double above = Maths.clamp01((s.y() + s.size() - surface) / (0.3 * s.size()));
                        foam = Math.max(foam, above * Maths.exp(-r * r));
                    }
                }
            }
            return foam;
        }
    }

    /** Vertices with water's data, and triangles in submeshes. */
    private static final class Writer {
        private float[] v = new float[16 * 256];
        private int[] indices = new int[1024];
        private int[] submeshes = {0, 0, 0, 0};
        private int submeshCount;
        private int count;
        private int indexCount;

        void vertex(double... values) {
            if ((count + 1) * 16 > v.length) {
                v = Arrays.copyOf(v, v.length * 2);
            }
            for (int k = 0; k < 16; k++) {
                v[count * 16 + k] = (float) values[k];
            }
            count++;
        }

        void quad(int a, int b, int c, int d) {
            if (indexCount + 6 > indices.length) {
                indices = Arrays.copyOf(indices, indices.length * 2);
            }
            indices[indexCount++] = a;
            indices[indexCount++] = b;
            indices[indexCount++] = c;
            indices[indexCount++] = a;
            indices[indexCount++] = c;
            indices[indexCount++] = d;
        }

        void nextSubmesh() {
            submeshes[++submeshCount] = indexCount;
        }

        ProceduralMesh build() {
            ElementBuffer buffer = ElementBuffer.onHeap(ProceduralVertex.LAYOUT, count);
            Vec3Attribute position = buffer.attribute(ProceduralVertex.POSITION);
            Vec3Attribute normal = buffer.attribute(ProceduralVertex.NORMAL);
            Vec3Attribute tangent = buffer.attribute(ProceduralVertex.TANGENT);
            Vec2Attribute uv = buffer.attribute(ProceduralVertex.UV);
            Vec2Attribute uv1 = buffer.attribute(ProceduralVertex.WIND_PIVOT);
            Vec4Attribute color = buffer.attribute(ProceduralVertex.WIND);
            double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int i = 0; i < count; i++) {
                int o = 16 * i;
                position.set(i, v[o], v[o + 1], v[o + 2]);
                normal.set(i, v[o + 3], v[o + 4], v[o + 5]);
                tangent.set(i, v[o + 6], v[o + 7], v[o + 8]);
                uv.set(i, v[o + 9], v[o + 10]);
                uv1.set(i, v[o + 11], v[o + 12]);
                color.set(i, Maths.clamp01(v[o + 13]), Maths.clamp01(v[o + 14]), Maths.clamp01(v[o + 15]), 1);
                for (int a = 0; a < 3; a++) {
                    min[a] = Math.min(min[a], v[o + a]);
                    max[a] = Math.max(max[a], v[o + a]);
                }
            }
            int[] starts = Arrays.copyOf(submeshes, submeshCount + 2);
            starts[submeshCount + 1] = indexCount;
            Aabb bounds = count == 0 ? new Aabb(Vec3.ZERO, Vec3.ZERO)
                : new Aabb(new Vec3(min[0], min[1], min[2]), new Vec3(max[0], max[1], max[2]));
            return new ProceduralMesh(buffer, Arrays.copyOf(indices, indexCount), starts, bounds, 0);
        }
    }
}
