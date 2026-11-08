package com.truesim.common.procedural.ecology;

/**
 * The layers of a plant community, tallest first. Plants of a taller layer win space from lower ones (a tree excludes
 * shrubs within its crown unless they bear its shade) and shade them; within a layer, larger plants exclude smaller.
 */
public enum Layer {
    /** Trees: the canopy. */
    CANOPY,
    /** Shrubs and krummholz, 1 to 6 m. */
    SHRUB,
    /** Dwarf shrubs, the heath: blueberry, lingonberry, alpenrose. */
    DWARF_SHRUB,
    /** Herbs and flowers, sedges and rushes: as clumps. */
    HERB,
    /** Grasses: a density for grass fields, no instances. */
    GRASS,
    /** Mosses and lichens: ground cover, no instances. */
    GROUND;

    /** The layers, cached ({@code values()} copies). */
    static final Layer[] ALL = values();

    /** How tall the layer stands: higher dominates lower. */
    int rank() {
        return ALL.length - ordinal();
    }
}
