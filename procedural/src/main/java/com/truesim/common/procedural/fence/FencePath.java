package com.truesim.common.procedural.fence;

import com.truesim.annotations.AllowAllocation;

/** A fence's line in x and z by its length: points and directions at any distance along it. */
@AllowAllocation("generation, at load time")
final class FencePath {

    private final double[] xz;
    private final double[] arc;
    private final int count;

    FencePath(double[] xz) {
        if (xz.length < 4 || xz.length % 2 != 0) {
            throw new IllegalArgumentException("A fence's line is x, z pairs, at least two points");
        }
        // drop repeated points
        double[] kept = new double[xz.length];
        int n = 0;
        for (int i = 0; i < xz.length / 2; i++) {
            if (n > 0 && len(xz[2 * i] - kept[2 * n - 2], xz[2 * i + 1] - kept[2 * n - 1]) < 1e-6) {
                continue;
            }
            kept[2 * n] = xz[2 * i];
            kept[2 * n + 1] = xz[2 * i + 1];
            n++;
        }
        if (n < 2) {
            throw new IllegalArgumentException("A fence's line needs two distinct points");
        }
        this.count = n;
        this.xz = java.util.Arrays.copyOf(kept, 2 * n);
        this.arc = new double[n];
        for (int i = 1; i < n; i++) {
            arc[i] = arc[i - 1] + len(this.xz[2 * i] - this.xz[2 * i - 2], this.xz[2 * i + 1] - this.xz[2 * i - 1]);
        }
    }

    private static double len(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    double length() {
        return arc[count - 1];
    }

    int count() {
        return count;
    }

    /** The distance along the line of point {@code i}. */
    double arc(int i) {
        return arc[i];
    }

    double x(int i) {
        return xz[2 * i];
    }

    double z(int i) {
        return xz[2 * i + 1];
    }

    /** The segment holding distance {@code s} (the later one at a point). */
    int segment(double s) {
        int i = 0;
        while (i < count - 2 && arc[i + 1] <= s) {
            i++;
        }
        return i;
    }

    /** x at distance {@code s} (clamped to the line). */
    double x(double s) {
        int i = segment(s);
        double t = t(i, s);
        return xz[2 * i] + t * (xz[2 * i + 2] - xz[2 * i]);
    }

    /** z at distance {@code s}. */
    double z(double s) {
        int i = segment(s);
        double t = t(i, s);
        return xz[2 * i + 1] + t * (xz[2 * i + 3] - xz[2 * i + 1]);
    }

    private double t(int i, double s) {
        double l = arc[i + 1] - arc[i];
        return Math.clamp((s - arc[i]) / l, 0, 1);
    }

    /** The line's direction (unit x) at {@code s}: within a metre of a corner, turning smoothly through it. */
    double dx(double s) {
        return direction(s, 0);
    }

    /** The line's direction (unit z) at {@code s}. */
    double dz(double s) {
        return direction(s, 1);
    }

    private double direction(double s, int axis) {
        double a = Math.max(0, s - 0.5);
        double b = Math.min(length(), s + 0.5);
        double ex = x(b) - x(a);
        double ez = z(b) - z(a);
        double l = len(ex, ez);
        if (l < 1e-9) {
            int i = segment(s);
            ex = xz[2 * i + 2] - xz[2 * i];
            ez = xz[2 * i + 3] - xz[2 * i + 1];
            l = len(ex, ez);
        }
        return (axis == 0 ? ex : ez) / l;
    }

    /** How sharply the line turns at point {@code i} (0 straight, 1 a right angle, 2 doubled back). */
    double turn(int i) {
        if (i <= 0 || i >= count - 1) {
            return 0;
        }
        double ax = xz[2 * i] - xz[2 * i - 2];
        double az = xz[2 * i + 1] - xz[2 * i - 1];
        double bx = xz[2 * i + 2] - xz[2 * i];
        double bz = xz[2 * i + 3] - xz[2 * i + 1];
        return 1 - (ax * bx + az * bz) / (len(ax, az) * len(bx, bz));
    }
}
