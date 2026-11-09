package com.truesim.common.procedural.ecology.rocks;

import com.truesim.common.procedural.ecology.Obstacles;

/**
 * The rocks of a region: those whose origin lies in [x0, x1) x [z0, z1) ({@link #instances()}, each in exactly one
 * tile of a field), and those of its margin too ({@link #nearby()}, rocks reaching into it), for keeping plants and
 * grass off them.
 */
public final class RockTile implements Obstacles.Region {

    private final double x0;
    private final double z0;
    private final double x1;
    private final double z1;
    private final RockInstances instances;
    private final RockInstances nearby;
    private final double clearance;
    private final Buckets buckets;
    private final Talus talus;

    /** Plants of a crown radius above this (metres) are woody: trees and shrubs, which active talus does not take. */
    public static final double WOODY = 0.6;

    /** Where the talus is active, at the field's world-aligned samples. */
    record Talus(long i0, long j0, int width, int depth, double cell, boolean[] active) {

        /** Whether the sample nearest to (x, z) is active. */
        boolean at(double x, double z) {
            long i = Math.round(x / cell) - i0;
            long j = Math.round(z / cell) - j0;
            return i >= 0 && j >= 0 && i < width && j < depth && active[(int) (j * width + i)];
        }
    }

    RockTile(double x0, double z0, double x1, double z1, RockInstances instances, RockInstances nearby,
        double clearance, Talus talus) {
        this.talus = talus;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.instances = instances;
        this.nearby = nearby;
        this.clearance = clearance;
        this.buckets = new Buckets(nearby, x0, z0, x1, z1);
    }

    public double x0() {
        return x0;
    }

    public double z0() {
        return z0;
    }

    public double x1() {
        return x1;
    }

    public double z1() {
        return z1;
    }

    /** The rocks whose origin lies in the region. */
    public RockInstances instances() {
        return instances;
    }

    /** The rocks reaching into the region, its own and its neighbours'. */
    public RockInstances nearby() {
        return nearby;
    }

    /**
     * Whether a plant of crown radius {@code radius} may not stand at ({@code x}, {@code z}): its stem, with the
     * field's share of its crown, would be on a rock's contact with the ground, or it is woody on active talus.
     */
    @Override
    public boolean blocks(double x, double z, double radius) {
        if (radius > WOODY && activeTalus(x, z)) {
            return true;
        }
        return buckets.any(x, z, clearance * radius);
    }

    /** Whether ({@code x}, {@code z}) lies on active talus: rocks still fall there, no tree or shrub takes root. */
    public boolean activeTalus(double x, double z) {
        return talus.at(x, z);
    }

    /** How much of ({@code x}, {@code z}) a rock covers, 0 to 1 (1 inside a contact, falling over {@code soft} metres). */
    public double cover(double x, double z, double soft) {
        return buckets.cover(x, z, soft);
    }

    /**
     * The rocks' footprint for a ground map (grass and ground cover kept off them): {@code width} x {@code height}
     * texels of {@code texel} metres from ({@code originX}, {@code originZ}) (texel centres at half texels), row by
     * row, 1 where a rock's contact covers the texel's centre, falling to 0 over a texel outside it. Only the rocks
     * of this tile and its margin are drawn: ask a tile covering the map.
     */
    public float[] footprint(double originX, double originZ, double texel, int width, int height) {
        float[] out = new float[width * height];
        for (int i = 0; i < nearby.count(); i++) {
            double r = nearby.radius(i) + texel;
            int c0 = (int) Math.max(0, Math.floor((nearby.x(i) - r - originX) / texel));
            int c1 = (int) Math.min(width - 1, Math.floor((nearby.x(i) + r - originX) / texel));
            int r0 = (int) Math.max(0, Math.floor((nearby.z(i) - r - originZ) / texel));
            int r1 = (int) Math.min(height - 1, Math.floor((nearby.z(i) + r - originZ) / texel));
            for (int row = r0; row <= r1; row++) {
                double z = originZ + (row + 0.5) * texel;
                for (int col = c0; col <= c1; col++) {
                    double x = originX + (col + 0.5) * texel;
                    double v = Buckets.cover(nearby, i, x, z, texel);
                    int at = row * width + col;
                    if (v > out[at]) {
                        out[at] = (float) v;
                    }
                }
            }
        }
        return out;
    }

    /** Rocks bucketed by position for point queries. */
    private static final class Buckets {
        final RockInstances rocks;
        final double ox;
        final double oz;
        final double size;
        final int columns;
        final int rows;
        final int[] head;
        final int[] next;
        final double reach;

        Buckets(RockInstances rocks, double x0, double z0, double x1, double z1) {
            this.rocks = rocks;
            double r = 0;
            for (int i = 0; i < rocks.count(); i++) {
                r = Math.max(r, rocks.radius(i));
            }
            reach = r;
            size = Math.max(4, 2 * r);
            ox = x0 - r;
            oz = z0 - r;
            columns = Math.max(1, (int) Math.ceil((x1 - x0 + 2 * r) / size));
            rows = Math.max(1, (int) Math.ceil((z1 - z0 + 2 * r) / size));
            head = new int[columns * rows];
            next = new int[Math.max(1, rocks.count())];
            java.util.Arrays.fill(head, -1);
            for (int i = 0; i < rocks.count(); i++) {
                int b = row(rocks.z(i)) * columns + column(rocks.x(i));
                next[i] = head[b];
                head[b] = i;
            }
        }

        int column(double x) {
            return Math.clamp((int) Math.floor((x - ox) / size), 0, columns - 1);
        }

        int row(double z) {
            return Math.clamp((int) Math.floor((z - oz) / size), 0, rows - 1);
        }

        boolean any(double x, double z, double margin) {
            double r = reach + margin;
            for (int bj = row(z - r); bj <= row(z + r); bj++) {
                for (int bi = column(x - r); bi <= column(x + r); bi++) {
                    for (int i = head[bj * columns + bi]; i >= 0; i = next[i]) {
                        if (inside(rocks, i, x, z, margin) <= 1) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        double cover(double x, double z, double soft) {
            double r = reach + soft;
            double best = 0;
            for (int bj = row(z - r); bj <= row(z + r); bj++) {
                for (int bi = column(x - r); bi <= column(x + r); bi++) {
                    for (int i = head[bj * columns + bi]; i >= 0; i = next[i]) {
                        best = Math.max(best, cover(rocks, i, x, z, soft));
                    }
                }
            }
            return best;
        }

        /** Rock {@code i}'s cover of (x, z): 1 inside its contact ellipse, 0 beyond {@code soft} metres of it. */
        static double cover(RockInstances rocks, int i, double x, double z, double soft) {
            double e = inside(rocks, i, x, z, 0);
            if (e <= 1) {
                return 1;
            }
            // the distance outside, about: the ellipse's radius along the direction times (sqrt(e) - 1)
            double dx = x - rocks.x(i);
            double dz = z - rocks.z(i);
            double d = Math.sqrt(dx * dx + dz * dz);
            double outside = d * (1 - 1 / Math.sqrt(e));
            return Math.max(0, 1 - outside / soft);
        }

        /**
         * Where (x, z) lies relative to rock {@code i}'s contact ellipse grown by {@code margin}: the ellipse's
         * quadratic form, 1 on its edge.
         */
        static double inside(RockInstances rocks, int i, double x, double z, double margin) {
            double dx = x - rocks.x(i);
            double dz = z - rocks.z(i);
            // into the rock's frame: undo its heading (the tilt is small; the contact is taken level)
            double qy = rocks.qy(i);
            double qw = rocks.qw(i);
            double qx = rocks.qx(i);
            double qz = rocks.qz(i);
            // the model's x axis in the world, projected to the ground
            double ax = 1 - 2 * (qy * qy + qz * qz);
            double az = 2 * (qx * qz - qw * qy);
            double al = Math.sqrt(ax * ax + az * az);
            ax /= al;
            az /= al;
            double u = dx * ax + dz * az;
            double v = -dx * az + dz * ax;
            double hx = rocks.halfX(i) + margin;
            double hz = rocks.halfZ(i) + margin;
            return (u * u) / (hx * hx) + (v * v) / (hz * hz);
        }
    }
}
