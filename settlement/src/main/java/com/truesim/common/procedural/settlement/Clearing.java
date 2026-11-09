package com.truesim.common.procedural.settlement;

/**
 * Where a settlement leaves the ground open, for the ecology's plants and the grass: two densities from 0 (nothing
 * grows) to 1 (as the ecology says), fading over a metre or two at their edges. Trees and shrubs keep off the roads,
 * the square and the buildings by a margin and off the plots (gardens and yards); grass keeps off what is paved or
 * built (the ground-cover mask: roads with their kerbs and steps, the square, footprints). A raster over the plan,
 * every {@link SettlementParameters.Grid#raster} metres; 1 outside it.
 */
public final class Clearing {

    private final double x0;
    private final double z0;
    private final double cell;
    private final int n;
    private final byte[] trees;
    private final byte[] grass;

    Clearing(double x0, double z0, double cell, int n, byte[] trees, byte[] grass) {
        this.x0 = x0;
        this.z0 = z0;
        this.cell = cell;
        this.n = n;
        this.trees = trees;
        this.grass = grass;
    }

    /** How much trees and shrubs may grow at ({@code x}, {@code z}), 0 to 1: the ecology's instances' keep. */
    public double trees(double x, double z) {
        return sample(trees, x, z);
    }

    /** How much grass and herbs may grow at ({@code x}, {@code z}), 0 to 1: the ground-cover mask. */
    public double grass(double x, double z) {
        return sample(grass, x, z);
    }

    private double sample(byte[] raster, double x, double z) {
        double u = (x - x0) / cell;
        double v = (z - z0) / cell;
        int i = (int) Math.floor(u);
        int j = (int) Math.floor(v);
        if (i < 0 || j < 0 || i >= n - 1 || j >= n - 1) {
            return 1;
        }
        double fu = u - i;
        double fv = v - j;
        double a = raster[j * n + i] & 0xff;
        double b = raster[j * n + i + 1] & 0xff;
        double c = raster[(j + 1) * n + i] & 0xff;
        double d = raster[(j + 1) * n + i + 1] & 0xff;
        return ((1 - fv) * ((1 - fu) * a + fu * b) + fv * ((1 - fu) * c + fu * d)) / 255;
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

    /** The grass mask's sample ({@code i}, {@code j}), 0 to 255: for a terrain's ground-cover texture. */
    public int grassSample(int i, int j) {
        return grass[j * n + i] & 0xff;
    }

    /** The trees' sample ({@code i}, {@code j}), 0 to 255. */
    public int treeSample(int i, int j) {
        return trees[j * n + i] & 0xff;
    }

    long bytes() {
        return trees.length + (long) grass.length;
    }
}
