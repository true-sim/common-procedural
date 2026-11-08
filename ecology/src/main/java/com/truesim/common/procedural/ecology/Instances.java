package com.truesim.common.procedural.ecology;

import java.util.Arrays;

/**
 * Plant instances, as parallel arrays: where each stands (x, y, z: the terrain's height under it), its turn about the
 * vertical, its height and width scales (relative to its species' grown plant: a tree's model as generated), its
 * species (an index into the ecology's species), a seed for its variant, its age (0 young to 1 grown, relative to what
 * the site allows) and its health (its site's suitability, 0 to 1). For effects-vegetation to scatter and draw.
 */
public final class Instances {

    private int count;
    private double[] x = new double[64];
    private double[] y = new double[64];
    private double[] z = new double[64];
    private float[] yaw = new float[64];
    private float[] height = new float[64];
    private float[] width = new float[64];
    private float[] age = new float[64];
    private float[] health = new float[64];
    private int[] species = new int[64];
    private long[] variant = new long[64];

    void add(double px, double py, double pz, double turn, double heightScale, double widthScale, int kind,
        long seed, double grown, double suited) {
        if (count == x.length) {
            int n = 2 * count;
            x = Arrays.copyOf(x, n);
            y = Arrays.copyOf(y, n);
            z = Arrays.copyOf(z, n);
            yaw = Arrays.copyOf(yaw, n);
            height = Arrays.copyOf(height, n);
            width = Arrays.copyOf(width, n);
            age = Arrays.copyOf(age, n);
            health = Arrays.copyOf(health, n);
            species = Arrays.copyOf(species, n);
            variant = Arrays.copyOf(variant, n);
        }
        x[count] = px;
        y[count] = py;
        z[count] = pz;
        yaw[count] = (float) turn;
        height[count] = (float) heightScale;
        width[count] = (float) widthScale;
        species[count] = kind;
        variant[count] = seed;
        age[count] = (float) grown;
        health[count] = (float) suited;
        count++;
    }

    /** How many. */
    public int count() {
        return count;
    }

    /** Instance {@code i}'s x, metres east. */
    public double x(int i) {
        return x[i];
    }

    /** Its height: the terrain's under it, metres. */
    public double y(int i) {
        return y[i];
    }

    /** Its z, metres south. */
    public double z(int i) {
        return z[i];
    }

    /** Its turn about the vertical, radians. */
    public float yaw(int i) {
        return yaw[i];
    }

    /** Its height relative to its species' grown plant. */
    public float height(int i) {
        return height[i];
    }

    /** Its width relative to its species' grown plant (wider than its height where it grows as krummholz). */
    public float width(int i) {
        return width[i];
    }

    /** Its species: an index into {@link Ecology#species()}. */
    public int species(int i) {
        return species[i];
    }

    /** A seed for its variant. */
    public long variant(int i) {
        return variant[i];
    }

    /** Its age, 0 young to 1 grown. */
    public float age(int i) {
        return age[i];
    }

    /** Its health: its site's suitability, 0 to 1. */
    public float health(int i) {
        return health[i];
    }
}
