package com.truesim.common.procedural.flora;

import com.truesim.common.math.Maths;

/** The outline of a flat organ (petal, sepal, ray, bract, leaf, blade): its width along its length. */
public enum Outline {
    /** Narrow at the base, widest past the middle, a rounded tip: apple and buttercup petals. */
    ROUND,
    /** Widest in the middle, to a point: gentian lobes, edelweiss bracts, sepals. */
    POINTED,
    /** Rounded with a notch in the tip: dog rose petals. */
    NOTCHED,
    /** Rounded, cut square with small teeth: arnica's rays. */
    TOOTHED,
    /** Long and narrow, pointed at both ends: lanceolate leaves, aster rays. */
    LANCEOLATE,
    /** Oval: elliptic leaves, clover's leaflets. */
    ELLIPTIC,
    /** Widest below the middle, from a short stalk: arnica's basal leaves. */
    OVATE,
    /** Widest near the tip, narrowing to the base: spatulate leaves, buttercup's lobes. */
    SPATULATE,
    /** Widest at the base, tapering to a point: a grass blade. */
    BLADE;

    /** The width at {@code s} along the length (0 base, 1 tip), as a fraction of the widest. */
    public double width(double s) {
        double c = Math.clamp(s, 0, 1);
        return switch (this) {
            case ROUND -> c < 0.6 ? 0.45 + 0.55 * Maths.sin(0.5 * Math.PI * c / 0.6)
                : 1 - 0.35 * sq((c - 0.6) / 0.4);
            case NOTCHED, TOOTHED -> c < 0.6 ? 0.45 + 0.55 * Maths.sin(0.5 * Math.PI * c / 0.6)
                : 1 - 0.2 * sq((c - 0.6) / 0.4);
            case POINTED -> c < 0.5 ? 0.3 + 0.7 * Maths.sin(0.5 * Math.PI * c / 0.5)
                : 1 - 0.95 * Maths.pow((c - 0.5) / 0.5, 1.3);
            case LANCEOLATE -> Math.max(0.06, Maths.pow(Maths.sin(Math.PI * c), 0.7));
            case ELLIPTIC -> Math.max(0.08, Maths.sin(Math.PI * (0.06 + 0.88 * c)));
            case OVATE -> Math.max(0.08, Maths.sin(Math.PI * Maths.pow(c, 0.75)));
            case SPATULATE -> Math.max(0.1, Maths.sin(Math.PI * Maths.pow(c, 1.6)));
            case BLADE -> 1 - 0.95 * Maths.pow(c, 1.3);
        };
    }

    /** How far the tip's middle reaches past (positive) or falls short of (a notch) its edges, as a fraction. */
    public double tip() {
        return switch (this) {
            case ROUND -> 0.06;
            case NOTCHED -> -0.1;
            case TOOTHED -> 0;
            case POINTED, LANCEOLATE, ELLIPTIC, OVATE -> 0.04;
            case SPATULATE -> 0.05;
            case BLADE -> 0.02;
        };
    }

    /** The outline of a petal tip. */
    public static Outline of(FlowerShape.Tip tip) {
        return switch (tip) {
            case ROUND -> ROUND;
            case POINTED -> POINTED;
            case NOTCHED -> NOTCHED;
            case TOOTHED -> TOOTHED;
        };
    }

    private static double sq(double x) {
        return x * x;
    }
}
