package com.truesim.common.procedural.ecology;

/**
 * A species' tolerance of one condition as a trapezoid with smooth flanks: nothing at or below {@code min}, rising
 * (smoothstep) to full from {@code low} to {@code high}, falling to nothing at {@code max}. A flank of zero width is a
 * hard limit.
 *
 * @param min where the response starts
 * @param low where it is full
 * @param high where it is last full
 * @param max where it ends
 */
public record Range(double min, double low, double high, double max) {

    /** Every value fully. */
    public static final Range ANY = new Range(-1e9, -1e9, 1e9, 1e9);

    public Range {
        if (!(min <= low && low <= high && high <= max)) {
            throw new IllegalArgumentException("A range needs min <= low <= high <= max: " + min + ", " + low + ", "
                + high + ", " + max);
        }
    }

    /** The response to {@code x}, 0 to 1. */
    public double response(double x) {
        if (x >= low && x <= high) {
            return 1;
        }
        if (x <= min || x >= max) {
            return 0;
        }
        return x < low ? smooth((x - min) / (low - min)) : smooth((max - x) / (max - high));
    }

    /** Hermite smoothstep of {@code t} in [0, 1]. */
    static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** GLSL's smoothstep: 0 at or below {@code edge0}, 1 at or above {@code edge1} (either order), Hermite between. */
    static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
