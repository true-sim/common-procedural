package com.truesim.common.procedural.tree;

/**
 * The time of year: which leaf atlas tile a species shows, whether deciduous trees carry leaves at all, and (through
 * its {@link #day()}) what flowers and fruits show.
 */
public enum Season {
    SPRING, SUMMER, AUTUMN, WINTER;

    /**
     * The day of the year (0 is 1 January) a season stands for, when no day is given: mid May, mid July, late
     * September, mid January.
     */
    public double day() {
        return switch (this) {
            case SPRING -> 135;
            case SUMMER -> 196;
            case AUTUMN -> 270;
            case WINTER -> 15;
        };
    }

    /**
     * The leaves' season on day {@code day} of the year: March to May spring, June to the equinox (22 September)
     * summer, then autumn colors to the end of November, winter. (Leaves in the Alps turn from late September: a
     * rowan's berries ripen among green leaves.)
     */
    public static Season of(double day) {
        double d = ((day % 365) + 365) % 365;
        return d < 59 || d >= 334 ? WINTER : d < 151 ? SPRING : d < 264 ? SUMMER : AUTUMN;
    }
}
