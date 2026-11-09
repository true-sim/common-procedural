package com.truesim.common.procedural.settlement;

/**
 * The terrain's heights under a settlement's roads and square: the roads' crowns and camber across their width, the
 * square's plane, and beyond their edges the cuts and fills back to the ground at the batter's slope. A raster over
 * the plan, every {@link SettlementParameters.Grid#raster} metres; where nothing is graded the terrain keeps its own
 * height. A terrain that takes it ({@link #height}) lies under the road meshes, which follow the same profile.
 */
public final class Grading {

    private final double x0;
    private final double z0;
    private final double cell;
    private final int n;
    /** The graded height at each sample, NaN where ungraded. */
    private final float[] target;

    Grading(double x0, double z0, double cell, int n, float[] target) {
        this.x0 = x0;
        this.z0 = z0;
        this.cell = cell;
        this.n = n;
        this.target = target;
    }

    /**
     * The graded height at ({@code x}, {@code z}), where the terrain's own is {@code terrain}: bilinear between the
     * samples, an ungraded sample counting as the terrain.
     */
    public double height(double x, double z, double terrain) {
        double u = (x - x0) / cell;
        double v = (z - z0) / cell;
        int i = (int) Math.floor(u);
        int j = (int) Math.floor(v);
        if (i < 0 || j < 0 || i >= n - 1 || j >= n - 1) {
            return terrain;
        }
        double fu = u - i;
        double fv = v - j;
        double a = value(j * n + i, terrain);
        double b = value(j * n + i + 1, terrain);
        double c = value((j + 1) * n + i, terrain);
        double d = value((j + 1) * n + i + 1, terrain);
        return (1 - fv) * ((1 - fu) * a + fu * b) + fv * ((1 - fu) * c + fu * d);
    }

    private double value(int a, double terrain) {
        float t = target[a];
        return Float.isNaN(t) ? terrain : t;
    }

    /** Whether the ground at ({@code x}, {@code z}) is graded (the nearest sample is). */
    public boolean graded(double x, double z) {
        int i = (int) Math.round((x - x0) / cell);
        int j = (int) Math.round((z - z0) / cell);
        return i >= 0 && j >= 0 && i < n && j < n && !Float.isNaN(target[j * n + i]);
    }

    /** The raster's first sample, east. */
    public double x0() {
        return x0;
    }

    /** The raster's first sample, south. */
    public double z0() {
        return z0;
    }

    /** The samples' spacing, metres. */
    public double cell() {
        return cell;
    }

    /** Samples a side. */
    public int samples() {
        return n;
    }

    /** The graded height of sample ({@code i}, {@code j}), NaN where ungraded. */
    public float sample(int i, int j) {
        return target[j * n + i];
    }

    long bytes() {
        return 4L * target.length;
    }
}
