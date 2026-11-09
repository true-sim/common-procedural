package com.truesim.common.procedural.settlement;

/** The classes of a settlement's roads, from the widest and gentlest to the narrowest and steepest. */
public enum RoadKind {
    /** The main road along the valley, through the square. */
    MAIN,
    /** A village lane. */
    LANE,
    /** A farm track out to a farmstead. */
    TRACK,
    /** A footpath: up the slopes, to the hay barns; stone steps where steep. */
    FOOTPATH
}
