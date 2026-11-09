package com.truesim.common.procedural.fence;

/** How a fence is built: the layouts {@link FenceGenerator} knows. */
public enum FenceKind {

    /** Posts with round rails between them (two or three), set into the posts. */
    POST_AND_RAIL,
    /**
     * Split rails laid in a zigzag, stacked where they cross, held by a pair of stakes lashed together at each crossing:
     * the Swiss {@code Schärhag}, the worm fence of other mountains. No posts in the ground but the stakes.
     */
    SPLIT_RAIL,
    /** Square posts, two sawn rails, pointed pickets on their front following the ground. */
    PICKET,
    /** Stakes with rods woven in and out between them (wattle, a woven hurdle). */
    WATTLE,
    /** Square posts with horizontal boards nailed across their front. */
    BOARD,
    /**
     * The Alpine {@code Stangenzaun}: poles slanted along the fence, held between pairs of posts lashed with withy
     * rings.
     */
    STANGENZAUN,
    /** A dry-stone wall: courses of stones following the ground, battered, upright coping stones on top. */
    DRY_STONE,
    /** Wooden posts carrying strands of galvanised wire. */
    WIRE
}
