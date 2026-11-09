package com.truesim.common.procedural.fence;

import com.truesim.annotations.AllowAllocation;

/**
 * The ground a fence stands on: its height at any point, {@code (x, z) -> y}, in metres, y up. A terrain's height
 * query fits as it is ({@code terrain::height}). Must be pure (the same point always gives the same height) and
 * thread-safe: fences may be generated on several threads.
 */
@FunctionalInterface
public interface FenceGround {

    /** Level ground at y 0. */
    FenceGround FLAT = (x, z) -> 0;

    /** The height of the ground at ({@code x}, {@code z}). */
    double height(double x, double z);

    /** A plane through the origin rising {@code slopeX} metres per metre along x and {@code slopeZ} along z. */
    @AllowAllocation("a ground, at load time")
    static FenceGround plane(double slopeX, double slopeZ) {
        return (x, z) -> slopeX * x + slopeZ * z;
    }

    /**
     * The ground as a path's points know it: {@code xyz} are x, y, z triples along a line; the height at a point is
     * that of the nearest point of the line (in x and z), interpolated between its points. For recipes that carry
     * their own heights.
     */
    @AllowAllocation("a ground, at load time")
    static FenceGround alongPath(double[] xyz) {
        if (xyz.length < 3 || xyz.length % 3 != 0) {
            throw new IllegalArgumentException("A path is x, y, z triples: " + xyz.length + " values");
        }
        double[] points = xyz.clone();
        int n = points.length / 3;
        return (x, z) -> {
            if (n == 1) {
                return points[1];
            }
            double best = Double.POSITIVE_INFINITY;
            double height = points[1];
            for (int i = 0; i + 1 < n; i++) {
                double ax = points[3 * i];
                double az = points[3 * i + 2];
                double dx = points[3 * i + 3] - ax;
                double dz = points[3 * i + 5] - az;
                double l2 = dx * dx + dz * dz;
                double t = l2 == 0 ? 0 : Math.clamp(((x - ax) * dx + (z - az) * dz) / l2, 0, 1);
                double ex = ax + t * dx - x;
                double ez = az + t * dz - z;
                double d2 = ex * ex + ez * ez;
                if (d2 < best) {
                    best = d2;
                    height = points[3 * i + 1] + t * (points[3 * i + 4] - points[3 * i + 1]);
                }
            }
            return height;
        };
    }
}
