package com.truesim.common.procedural.water;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The water at any point of the terrain's frame, for the terrain and its vegetation: the channel there, the water's
 * surface and depth, the ground carved into a bed and banks, and how densely grass grows.
 *
 * <p><b>The bed</b> is a parabola across the water, its deepest point 1.5 times the mean depth below the surface (so
 * its mean depth is the channel's); <b>the banks</b> rise from the water's edge at {@link
 * WaterParameters.Banks#bankSlope} over {@link WaterParameters.Banks#bankWidth} times the width (at least {@link
 * WaterParameters.Banks#minBank}), blending back into the ground over their outer half. The ground is only ever
 * lowered. <b>Grass</b> is absent from the water and the bare gravel beside it, and grows back across the bank.
 *
 * <pre>{@code
 * WaterField water = network.field();
 * double ground = water.carve(x, z, terrain.height(x, z));      // the terrain's height with the river's bed
 * double grass = water.grass(x, z);                             // multiplies vegetation's density
 * WaterField.Raster maps = water.raster(terrain, x0, z0, 512, 256); // the same as textures, for the GPU
 * }</pre>
 *
 * Thread-safe: queries only read.
 */
public final class WaterField {

    private static final double BUCKET = 32;

    private final List<Channel> channels;
    private final WaterParameters.Banks banks;
    private final Map<Long, int[]> buckets;

    /**
     * The water at a point: the nearest channel's segment and how far across it the point lies.
     *
     * @param channel the channel
     * @param segment the segment's first point
     * @param t how far along the segment, 0 to 1
     * @param distance metres from the channel's line
     * @param halfWidth half the water's width there
     * @param bank the bank's width there
     * @param surface the water's surface there
     * @param depth the channel's mean depth there
     * @param speed the mean speed there, metres a second
     * @param directionX the flow's direction, east part
     * @param directionZ south part
     */
    public record Sample(int channel, int segment, double t, double distance, double halfWidth, double bank,
        double surface, double depth, double speed, double directionX, double directionZ) {

        /** Whether the point is in the water. */
        public boolean wet() {
            return distance <= halfWidth;
        }

        /** The water's depth at the point (0 on the banks). */
        public double localDepth() {
            double u = distance / halfWidth;
            return u >= 1 ? 0 : 1.5 * depth * (1 - u * u);
        }
    }

    /**
     * The field as rasters over a square, texel by texel (row-major, texel centres): for heightmaps and vegetation
     * maps on the GPU.
     *
     * @param x0 the west edge
     * @param z0 the north edge
     * @param size metres a side
     * @param texels texels a side
     * @param ground the carved ground's height
     * @param grass grass density, 0 to 1
     * @param depth the water's depth (0 outside the water)
     * @param surface the water's surface (NaN outside the water)
     */
    public record Raster(double x0, double z0, double size, int texels, float[] ground, float[] grass, float[] depth,
        float[] surface) {
    }

    WaterField(List<Channel> channels, WaterParameters.Banks banks) {
        this.channels = channels;
        this.banks = banks;
        Map<Long, List<Integer>> lists = new HashMap<>();
        for (Channel c : channels) {
            for (int i = 0; i + 1 < c.count(); i++) {
                double reach = 0.5 * Math.max(c.width(i), c.width(i + 1)) + bank(Math.max(c.width(i),
                    c.width(i + 1)));
                long bx0 = (long) Math.floor((Math.min(c.x(i), c.x(i + 1)) - reach) / BUCKET);
                long bx1 = (long) Math.floor((Math.max(c.x(i), c.x(i + 1)) + reach) / BUCKET);
                long bz0 = (long) Math.floor((Math.min(c.z(i), c.z(i + 1)) - reach) / BUCKET);
                long bz1 = (long) Math.floor((Math.max(c.z(i), c.z(i + 1)) + reach) / BUCKET);
                for (long bz = bz0; bz <= bz1; bz++) {
                    for (long bx = bx0; bx <= bx1; bx++) {
                        lists.computeIfAbsent(key(bx, bz), k -> new ArrayList<>()).add(c.id());
                        lists.get(key(bx, bz)).add(i);
                    }
                }
            }
        }
        this.buckets = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> e : lists.entrySet()) {
            int[] a = new int[e.getValue().size()];
            for (int k = 0; k < a.length; k++) {
                a[k] = e.getValue().get(k);
            }
            buckets.put(e.getKey(), a);
        }
    }

    private static long key(long bx, long bz) {
        return bx * 0x9E3779B97F4A7C15L ^ bz;
    }

    /** The bank's width beside water {@code width} metres wide. */
    private double bank(double width) {
        return Math.max(banks.minBank(), banks.bankWidth() * width);
    }

    /** The water at ({@code x}, {@code z}): the channel whose water or bank it is nearest (relative to their width). */
    public Sample sample(double x, double z) {
        int[] candidates = buckets.get(key((long) Math.floor(x / BUCKET), (long) Math.floor(z / BUCKET)));
        if (candidates == null) {
            return null;
        }
        Sample best = null;
        double bestRelative = Double.POSITIVE_INFINITY;
        for (int k = 0; k < candidates.length; k += 2) {
            Sample s = segment(channels.get(candidates[k]), candidates[k + 1], x, z);
            double relative = s.distance() / (s.halfWidth() + s.bank());
            if (relative <= 1 && relative < bestRelative) {
                bestRelative = relative;
                best = s;
            }
        }
        return best;
    }

    private Sample segment(Channel c, int i, double x, double z) {
        double ax = c.x(i);
        double az = c.z(i);
        double ex = c.x(i + 1) - ax;
        double ez = c.z(i + 1) - az;
        double length2 = ex * ex + ez * ez;
        double t = length2 > 0 ? Maths.clamp01(((x - ax) * ex + (z - az) * ez) / length2) : 0;
        double px = ax + t * ex - x;
        double pz = az + t * ez - z;
        double width = c.width(i) + t * (c.width(i + 1) - c.width(i));
        double dx = c.directionX(i) + t * (c.directionX(i + 1) - c.directionX(i));
        double dz = c.directionZ(i) + t * (c.directionZ(i + 1) - c.directionZ(i));
        double norm = Math.sqrt(dx * dx + dz * dz);
        return new Sample(c.id(), i, t, Math.sqrt(px * px + pz * pz), width / 2, bank(width),
            c.surface(i) + t * (c.surface(i + 1) - c.surface(i)), c.depth(i) + t * (c.depth(i + 1) - c.depth(i)),
            c.speed(i) + t * (c.speed(i + 1) - c.speed(i)), norm > 0 ? dx / norm : 1, norm > 0 ? dz / norm : 0);
    }

    /** The water's surface at ({@code x}, {@code z}), or NaN where there is no water. */
    public double surface(double x, double z) {
        Sample s = sample(x, z);
        return s != null && s.wet() ? s.surface() : Double.NaN;
    }

    /** The water's depth at ({@code x}, {@code z}), 0 where there is none. */
    public double depth(double x, double z) {
        Sample s = sample(x, z);
        return s == null ? 0 : s.localDepth();
    }

    /**
     * The ground at ({@code x}, {@code z}) with the beds and banks carved into it, from the terrain's {@code ground}
     * height there: never higher than it.
     */
    public double carve(double x, double z, double ground) {
        int[] candidates = buckets.get(key((long) Math.floor(x / BUCKET), (long) Math.floor(z / BUCKET)));
        if (candidates == null) {
            return ground;
        }
        double carved = ground;
        for (int k = 0; k < candidates.length; k += 2) {
            Sample s = segment(channels.get(candidates[k]), candidates[k + 1], x, z);
            double hw = s.halfWidth();
            double d = s.distance();
            if (d > hw + s.bank()) {
                continue;
            }
            double profile = d <= hw ? s.surface() - s.localDepth() : s.surface() + (d - hw) * banks.bankSlope();
            double keep = Maths.smoothstep(hw + 0.5 * s.bank(), hw + s.bank(), d);
            double cut = Math.min(ground, profile);
            carved = Math.min(carved, cut + keep * (ground - cut));
        }
        return carved;
    }

    /** How densely grass grows at ({@code x}, {@code z}): 0 in the water and on the bare gravel, 1 off the banks. */
    public double grass(double x, double z) {
        int[] candidates = buckets.get(key((long) Math.floor(x / BUCKET), (long) Math.floor(z / BUCKET)));
        if (candidates == null) {
            return 1;
        }
        double grass = 1;
        for (int k = 0; k < candidates.length; k += 2) {
            Sample s = segment(channels.get(candidates[k]), candidates[k + 1], x, z);
            double bare = s.halfWidth() + banks.gravel() * s.bank();
            grass = Math.min(grass, Maths.smoothstep(bare, s.halfWidth() + s.bank(), s.distance()));
        }
        return grass;
    }

    /** The field over the square of {@code size} metres from ({@code x0}, {@code z0}), {@code texels} a side. */
    public Raster raster(EcologyTerrain terrain, double x0, double z0, double size, int texels) {
        int n = texels * texels;
        float[] ground = new float[n];
        float[] grass = new float[n];
        float[] depth = new float[n];
        float[] surface = new float[n];
        double texel = size / texels;
        for (int j = 0; j < texels; j++) {
            for (int i = 0; i < texels; i++) {
                double x = x0 + (i + 0.5) * texel;
                double z = z0 + (j + 0.5) * texel;
                int a = j * texels + i;
                ground[a] = (float) carve(x, z, terrain.height(x, z));
                grass[a] = (float) grass(x, z);
                Sample s = sample(x, z);
                boolean wet = s != null && s.wet();
                depth[a] = wet ? (float) s.localDepth() : 0;
                surface[a] = wet ? (float) s.surface() : Float.NaN;
            }
        }
        return new Raster(x0, z0, size, texels, ground, grass, depth, surface);
    }
}
