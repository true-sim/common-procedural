package com.truesim.common.procedural.ecology.rocks;

/** Why a rock lies where it does: each kind has its own ground, sizes, shapes and age. */
public enum RockKind {

    /**
     * Talus: the cone of debris at a cliff's foot, at its angle of repose; blocks sorted by size downslope (big blocks
     * roll further), fresh near the apex where rocks still fall.
     */
    TALUS,
    /** Rockfall: the big blocks that ran out past the talus onto the meadows below a cliff, within its shadow angle. */
    ROCKFALL,
    /** Boulders along streams, rounded by the water, mossy. */
    STREAM,
    /** Clearance piles: stones picked off the meadows and heaped at their edges. */
    CLEARANCE,
    /** Glacial erratics: big boulders the ice left, alone on gentle ground, lichened. */
    ERRATIC;

    /** Every kind, in order. */
    public static final RockKind[] ALL = values();
}
