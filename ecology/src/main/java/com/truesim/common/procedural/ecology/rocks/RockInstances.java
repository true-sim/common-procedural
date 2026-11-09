package com.truesim.common.procedural.ecology.rocks;

import java.util.Arrays;
import java.util.List;

/**
 * Rock instances, as parallel arrays: where each lies (x, y, z: its origin, resting on the ground), its rotation (a
 * unit quaternion: its variant's heading, then leaning with the slope), its uniform scale of its variant's model, its
 * kind, its variant (an index into {@link #variants()}), and its footprint radius on the ground.
 */
public final class RockInstances {

    private final List<RockVariant> variants;
    private int count;
    private double[] x = new double[64];
    private double[] y = new double[64];
    private double[] z = new double[64];
    private float[] rotation = new float[4 * 64];
    private float[] scale = new float[64];
    private float[] radius = new float[64];
    private float[] halfX = new float[64];
    private float[] halfZ = new float[64];
    private byte[] kind = new byte[64];
    private int[] variant = new int[64];

    RockInstances(List<RockVariant> variants) {
        this.variants = variants;
    }

    void add(double px, double py, double pz, double[] q, double s, double r, double hx, double hz, RockKind k,
        int v) {
        if (count == x.length) {
            int n = 2 * count;
            x = Arrays.copyOf(x, n);
            y = Arrays.copyOf(y, n);
            z = Arrays.copyOf(z, n);
            rotation = Arrays.copyOf(rotation, 4 * n);
            scale = Arrays.copyOf(scale, n);
            radius = Arrays.copyOf(radius, n);
            halfX = Arrays.copyOf(halfX, n);
            halfZ = Arrays.copyOf(halfZ, n);
            kind = Arrays.copyOf(kind, n);
            variant = Arrays.copyOf(variant, n);
        }
        x[count] = px;
        y[count] = py;
        z[count] = pz;
        for (int c = 0; c < 4; c++) {
            rotation[4 * count + c] = (float) q[c];
        }
        scale[count] = (float) s;
        radius[count] = (float) r;
        halfX[count] = (float) hx;
        halfZ[count] = (float) hz;
        kind[count] = (byte) k.ordinal();
        variant[count] = v;
        count++;
    }

    /** How many. */
    public int count() {
        return count;
    }

    /** Rock {@code i}'s x, metres east. */
    public double x(int i) {
        return x[i];
    }

    /** Its y: its model's origin (the ground's level under it, the model cut flat a little below). */
    public double y(int i) {
        return y[i];
    }

    /** Its z, metres south. */
    public double z(int i) {
        return z[i];
    }

    /** Its rotation's x (a unit quaternion x, y, z, w: model to world). */
    public float qx(int i) {
        return rotation[4 * i];
    }

    public float qy(int i) {
        return rotation[4 * i + 1];
    }

    public float qz(int i) {
        return rotation[4 * i + 2];
    }

    public float qw(int i) {
        return rotation[4 * i + 3];
    }

    /** Its uniform scale of its variant's model (the preset's size times this is its size). */
    public float scale(int i) {
        return scale[i];
    }

    /** Its size: the mean radius, metres. */
    public double size(int i) {
        return scale[i] * variants.get(variant[i]).params().size();
    }

    /** How far it reaches across the ground from its origin, metres. */
    public float radius(int i) {
        return radius[i];
    }

    /** Its contact with the ground: the half extent along its model's x, metres (scaled). */
    public float halfX(int i) {
        return halfX[i];
    }

    /** Along its model's z. */
    public float halfZ(int i) {
        return halfZ[i];
    }

    /** Why it lies there. */
    public RockKind kind(int i) {
        return RockKind.ALL[kind[i]];
    }

    /** Its variant's index into {@link #variants()}. */
    public int variantIndex(int i) {
        return variant[i];
    }

    /** Its variant: the model it draws. */
    public RockVariant variant(int i) {
        return variants.get(variant[i]);
    }

    /** The field's variants, which every instance indexes (the same list for every tile of a field). */
    public List<RockVariant> variants() {
        return variants;
    }

    /** Its heading: the turn about +y its variant's moss was grown for, radians. */
    public double yaw(int i) {
        return variants.get(variant[i]).cover().yaw();
    }
}
