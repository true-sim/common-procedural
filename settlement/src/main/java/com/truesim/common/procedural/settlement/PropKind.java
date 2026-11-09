package com.truesim.common.procedural.settlement;

/** What stands at a prop anchor ({@link Settlement.Prop}): the showcase maps each to a model. */
public enum PropKind {
    /** A fountain with its trough, on the square. */
    FOUNTAIN,
    /** A bench: round the square, by house doors. */
    BENCH,
    /** An outdoor table with its chairs: the inn's terrace. */
    TABLE_SET,
    /** A planter or potted plant: at the inn's door. */
    PLANTER,
    /** A barrel by a house. */
    BARREL,
    /** A crate by a house. */
    CRATE,
    /** A woodpile stacked along a wall (a farmstead's woodshed is a building of its own). */
    WOODPILE,
    /** A water trough at a farmstead. */
    TROUGH,
    /** A signpost where a footpath leaves the roads. */
    SIGNPOST,
    /** A mossy rock on the meadows. */
    ROCK,
    /** A boulder. */
    BOULDER,
    /** A tree stump at the forest's edge. */
    STUMP
}
