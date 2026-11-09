package com.truesim.common.procedural.settlement;

/**
 * Ground plan geometry: distances to segments, convex polygons' overlap and containment. Plain double math (square
 * roots only: correctly rounded, so the same on every platform).
 */
final class Geometry {

    private Geometry() {
    }

    /** The length of ({@code dx}, {@code dz}). */
    static double length(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Where ({@code px}, {@code pz}) projects on segment a-b, 0 at a to 1 at b (clamped). */
    static double project(double px, double pz, double ax, double az, double bx, double bz) {
        double ex = bx - ax;
        double ez = bz - az;
        double l = ex * ex + ez * ez;
        return l == 0 ? 0 : Math.clamp(((px - ax) * ex + (pz - az) * ez) / l, 0, 1);
    }

    /** The distance from ({@code px}, {@code pz}) to segment a-b. */
    static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double t = project(px, pz, ax, az, bx, bz);
        return length(ax + t * (bx - ax) - px, az + t * (bz - az) - pz);
    }

    /** Whether segments a-b and c-d cross (touching counts). */
    static boolean crosses(double ax, double az, double bx, double bz, double cx, double cz, double dx, double dz) {
        double d1 = cross(cx, cz, dx, dz, ax, az);
        double d2 = cross(cx, cz, dx, dz, bx, bz);
        double d3 = cross(ax, az, bx, bz, cx, cz);
        double d4 = cross(ax, az, bx, bz, dx, dz);
        if ((d1 > 0 && d2 < 0 || d1 < 0 && d2 > 0) && (d3 > 0 && d4 < 0 || d3 < 0 && d4 > 0)) {
            return true;
        }
        return d1 == 0 && within(cx, cz, dx, dz, ax, az) || d2 == 0 && within(cx, cz, dx, dz, bx, bz)
            || d3 == 0 && within(ax, az, bx, bz, cx, cz) || d4 == 0 && within(ax, az, bx, bz, dx, dz);
    }

    /** Whether point r, on the line through p and q, lies between them. */
    private static boolean within(double px, double pz, double qx, double qz, double rx, double rz) {
        return Math.min(px, qx) <= rx && rx <= Math.max(px, qx) && Math.min(pz, qz) <= rz && rz <= Math.max(pz, qz);
    }

    /** The z component of (b - a) x (p - a): which side of a-b point p lies on. */
    static double cross(double ax, double az, double bx, double bz, double px, double pz) {
        return (bx - ax) * (pz - az) - (bz - az) * (px - ax);
    }

    /** The distance between segments a-b and c-d (0 where they cross). */
    static double segmentSegment(double ax, double az, double bx, double bz, double cx, double cz, double dx,
        double dz) {
        if (crosses(ax, az, bx, bz, cx, cz, dx, dz)) {
            return 0;
        }
        return Math.min(Math.min(segmentDistance(ax, az, cx, cz, dx, dz), segmentDistance(bx, bz, cx, cz, dx, dz)),
            Math.min(segmentDistance(cx, cz, ax, az, bx, bz), segmentDistance(dx, dz, ax, az, bx, bz)));
    }

    /** The signed area of a polygon (positive counter-clockwise in x-z, i.e. clockwise seen from above). */
    static double area(double[] x, double[] z) {
        double a = 0;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            a += x[j] * z[i] - x[i] * z[j];
        }
        return a / 2;
    }

    /** Whether ({@code px}, {@code pz}) is inside a convex polygon of either winding (the boundary counts). */
    static boolean inside(double px, double pz, double[] x, double[] z) {
        boolean positive = false;
        boolean negative = false;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            double c = cross(x[j], z[j], x[i], z[i], px, pz);
            positive |= c > 0;
            negative |= c < 0;
        }
        return !(positive && negative);
    }

    /**
     * How far ({@code px}, {@code pz}) lies inside a convex polygon of either winding: the least distance to its
     * edges' lines, negative outside (an approximation there: the largest distance outside an edge's line).
     */
    static double depth(double px, double pz, double[] x, double[] z) {
        double sign = area(x, z) >= 0 ? 1 : -1;
        double depth = Double.MAX_VALUE;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            double l = length(x[i] - x[j], z[i] - z[j]);
            if (l > 0) {
                depth = Math.min(depth, sign * cross(x[j], z[j], x[i], z[i], px, pz) / l);
            }
        }
        return depth;
    }

    /** The distance from segment a-b to a convex polygon (0 where it touches or enters it). */
    static double segmentPolygon(double ax, double az, double bx, double bz, double[] x, double[] z) {
        if (inside(ax, az, x, z) || inside(bx, bz, x, z)) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            best = Math.min(best, segmentSegment(ax, az, bx, bz, x[j], z[j], x[i], z[i]));
        }
        return best;
    }

    /** Whether two convex polygons overlap by more than {@code tolerance} metres (separating axes). */
    static boolean overlap(double[] ax, double[] az, double[] bx, double[] bz, double tolerance) {
        return !separated(ax, az, bx, bz, tolerance) && !separated(bx, bz, ax, az, tolerance);
    }

    /** Whether an edge normal of polygon a separates the two by at least -tolerance. */
    private static boolean separated(double[] ax, double[] az, double[] bx, double[] bz, double tolerance) {
        for (int i = 0, j = ax.length - 1; i < ax.length; j = i++) {
            double nx = az[i] - az[j];
            double nz = ax[j] - ax[i];
            double l = length(nx, nz);
            if (l == 0) {
                continue;
            }
            nx /= l;
            nz /= l;
            double minA = Double.MAX_VALUE;
            double maxA = -Double.MAX_VALUE;
            for (int k = 0; k < ax.length; k++) {
                double d = ax[k] * nx + az[k] * nz;
                minA = Math.min(minA, d);
                maxA = Math.max(maxA, d);
            }
            double minB = Double.MAX_VALUE;
            double maxB = -Double.MAX_VALUE;
            for (int k = 0; k < bx.length; k++) {
                double d = bx[k] * nx + bz[k] * nz;
                minB = Math.min(minB, d);
                maxB = Math.max(maxB, d);
            }
            if (maxA <= minB + tolerance || maxB <= minA + tolerance) {
                return true;
            }
        }
        return false;
    }

    /** {@code t} smoothed: 0 below 0, 1 above 1, Hermite between. */
    static double smooth(double t) {
        double c = Math.clamp(t, 0, 1);
        return c * c * (3 - 2 * c);
    }
}
