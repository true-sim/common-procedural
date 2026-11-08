package com.truesim.common.procedural.ecology;

/**
 * The terrain the ecology grows on, at any point of its frame: x east, z south, y up, metres. Only the altitude is
 * required; slope, aspect, hydrology and wind exposure come from it. Implementations must be pure (the same point
 * always gives the same answer) and thread-safe: tiles are generated on several threads, and a tile's result must not
 * depend on which tiles were made before it. effects-terrain's surface is adapted in
 * {@code common-procedural-ecology-terrain}.
 */
public interface EcologyTerrain {

    /** The altitude above sea level at ({@code x}, {@code z}), metres: what the climate follows. */
    double altitude(double x, double z);

    /**
     * The height at ({@code x}, {@code z}) in the frame plants stand in (the altitude, unless the terrain is drawn
     * lowered, by the Earth's curvature for one).
     */
    default double height(double x, double z) {
        return altitude(x, z);
    }

    /**
     * The ground at ({@code x}, {@code z}) as fractions of rock, scree, soil and snow ({@link Ground}), into
     * {@code fractions}, when the terrain knows it (its material's layers); false when it does not, and the ecology
     * works them out from slope and climate.
     */
    default boolean ground(double x, double z, double[] fractions) {
        return false;
    }

    /**
     * Days a year the ground at ({@code x}, {@code z}) is snow-covered, when the terrain (or a weather simulation)
     * knows; NaN when it does not, and the ecology works it out from the climate, aspect and wind exposure.
     */
    default double snowDays(double x, double z) {
        return Double.NaN;
    }
}
