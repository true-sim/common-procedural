package com.truesim.common.procedural.fence;

/**
 * The fence generator's tunables, the same for every fence of a game (common-procedural-assets reads them from the
 * engine settings, {@code FenceSettings}).
 *
 * @param sink how deep posts and stakes go below the lowest ground around their foot, metres
 * @param groundStep the longest step, metres, between points where pieces lying along the ground (walls, the lowest
 *     rails, a coarse level's wattle panels) sample the ground's height
 * @param sides round pieces' sides at the most detailed level (posts, rails; rods and wire fewer)
 * @param levels how many levels of detail, 1 to 3
 */
public record FenceTuning(double sink, double groundStep, int sides, int levels) {

    /** Posts 0.3 m below the ground, the ground sampled every half metre, eight-sided posts, three levels. */
    public static final FenceTuning DEFAULTS = new FenceTuning(0.3, 0.5, 8, 3);

    public FenceTuning {
        if (!(sink >= 0) || !(groundStep > 0.05) || sides < 3 || sides > 32 || levels < 1 || levels > 3) {
            throw new IllegalArgumentException("A sink from 0, a ground step over 5 cm, 3 to 32 sides, 1 to 3 levels");
        }
    }
}
