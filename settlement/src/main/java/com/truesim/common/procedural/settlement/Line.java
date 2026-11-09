package com.truesim.common.procedural.settlement;

import java.util.Arrays;

/**
 * A ground plan polyline with its arc length: points along it by distance, its tangents and left normals; and the
 * operations roads are made with (corner cutting, resampling).
 */
final class Line {

    final double[] x;
    final double[] z;
    /** The arc length at each point. */
    final double[] s;

    Line(double[] x, double[] z) {
        this.x = x;
        this.z = z;
        s = new double[x.length];
        for (int i = 1; i < x.length; i++) {
            s[i] = s[i - 1] + Geometry.length(x[i] - x[i - 1], z[i] - z[i - 1]);
        }
    }

    int count() {
        return x.length;
    }

    double length() {
        return s[s.length - 1];
    }

    /** The segment {@code at} lies on: the last point at or before it. */
    int segment(double at) {
        int k = Arrays.binarySearch(s, at);
        if (k < 0) {
            k = -k - 2;
        }
        return Math.clamp(k, 0, Math.max(0, x.length - 2));
    }

    /**
     * The point {@code at} metres along (clamped) into {@code out}: x, z, then the unit tangent's x and z (the
     * segment's).
     */
    void at(double at, double[] out) {
        int k = segment(at);
        if (x.length == 1) {
            out[0] = x[0];
            out[1] = z[0];
            out[2] = 1;
            out[3] = 0;
            return;
        }
        double l = s[k + 1] - s[k];
        double t = l > 0 ? Math.clamp((at - s[k]) / l, 0, 1) : 0;
        out[0] = x[k] + t * (x[k + 1] - x[k]);
        out[1] = z[k] + t * (z[k + 1] - z[k]);
        double dx = x[k + 1] - x[k];
        double dz = z[k + 1] - z[k];
        double d = Geometry.length(dx, dz);
        out[2] = d > 0 ? dx / d : 1;
        out[3] = d > 0 ? dz / d : 0;
    }

    /** The value of {@code values} (one per point) {@code at} metres along, interpolated. */
    double value(double[] values, double at) {
        int k = segment(at);
        if (values.length == 1) {
            return values[0];
        }
        double l = s[k + 1] - s[k];
        double t = l > 0 ? Math.clamp((at - s[k]) / l, 0, 1) : 0;
        return values[k] + t * (values[k + 1] - values[k]);
    }

    /**
     * The distance along of the point nearest ({@code px}, {@code pz}), and that point into {@code out} (x, z,
     * distance away).
     */
    double nearest(double px, double pz, double[] out) {
        double best = Double.MAX_VALUE;
        double along = 0;
        for (int i = 1; i < x.length; i++) {
            double t = Geometry.project(px, pz, x[i - 1], z[i - 1], x[i], z[i]);
            double qx = x[i - 1] + t * (x[i] - x[i - 1]);
            double qz = z[i - 1] + t * (z[i] - z[i - 1]);
            double d = Geometry.length(qx - px, qz - pz);
            if (d < best) {
                best = d;
                along = s[i - 1] + t * (s[i] - s[i - 1]);
                out[0] = qx;
                out[1] = qz;
                out[2] = d;
            }
        }
        return along;
    }

    /** Chaikin's corner cutting, {@code iterations} times, the ends kept: a smooth curve within the polygon's hull. */
    static Line chaikin(Line line, int iterations) {
        double[] x = line.x;
        double[] z = line.z;
        for (int it = 0; it < iterations && x.length > 2; it++) {
            int m = x.length;
            double[] nx = new double[2 * (m - 1)];
            double[] nz = new double[2 * (m - 1)];
            int k = 0;
            nx[k] = x[0];
            nz[k++] = z[0];
            for (int i = 0; i + 1 < m; i++) {
                if (i > 0) {
                    nx[k] = 0.75 * x[i] + 0.25 * x[i + 1];
                    nz[k++] = 0.75 * z[i] + 0.25 * z[i + 1];
                }
                if (i + 2 < m) {
                    nx[k] = 0.25 * x[i] + 0.75 * x[i + 1];
                    nz[k++] = 0.25 * z[i] + 0.75 * z[i + 1];
                }
            }
            nx[k] = x[m - 1];
            nz[k++] = z[m - 1];
            x = Arrays.copyOf(nx, k);
            z = Arrays.copyOf(nz, k);
        }
        return new Line(x, z);
    }

    /** The line resampled every {@code step} metres or a little less (evenly), its ends kept. */
    Line resample(double step) {
        double length = length();
        int segments = Math.max(1, (int) Math.ceil(length / step - 1e-9));
        double[] rx = new double[segments + 1];
        double[] rz = new double[segments + 1];
        double[] p = new double[4];
        for (int k = 0; k <= segments; k++) {
            at(length * k / segments, p);
            rx[k] = p[0];
            rz[k] = p[1];
        }
        rx[segments] = x[x.length - 1];
        rz[segments] = z[z.length - 1];
        return new Line(rx, rz);
    }

    /** Points {@code from} to {@code to} (inclusive). */
    Line part(int from, int to) {
        return new Line(Arrays.copyOfRange(x, from, to + 1), Arrays.copyOfRange(z, from, to + 1));
    }
}
