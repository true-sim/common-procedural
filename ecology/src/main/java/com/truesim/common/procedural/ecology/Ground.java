package com.truesim.common.procedural.ecology;

/** Indices of the ground's fractions that {@link EcologyTerrain#ground} writes: they sum to 1. */
public final class Ground {

    /** Bare rock. */
    public static final int ROCK = 0;
    /** Scree, loose stones. */
    public static final int SCREE = 1;
    /** Soil: where plants root. */
    public static final int SOIL = 2;
    /** Lying snow or ice all year. */
    public static final int SNOW = 3;
    /** How many fractions. */
    public static final int COUNT = 4;

    private Ground() {
    }
}
