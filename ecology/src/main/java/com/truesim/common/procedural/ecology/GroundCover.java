package com.truesim.common.procedural.ecology;

/**
 * Ground-cover classes for the terrain's material blending (and a later water renderer): fractions that sum to 1 at
 * every sample of a tile.
 */
public enum GroundCover {
    /** Grassland: meadows, pastures, alpine grassland. */
    MEADOW,
    /** Under the canopy: needles, leaf litter, moss. */
    FOREST_FLOOR,
    /** Dwarf-shrub heath: blueberry, lingonberry, alpenrose. */
    HEATH,
    /** Loose stones. */
    SCREE,
    /** Bare rock: cliffs, nival rock. */
    ROCK,
    /** Lying all year. */
    SNOW,
    /** Saturated ground: fens, bogs, wet meadows, mires. */
    WETLAND,
    /** Open water: rivers, lakes. */
    WATER;

    /** The classes, cached. */
    static final GroundCover[] ALL = values();
}
