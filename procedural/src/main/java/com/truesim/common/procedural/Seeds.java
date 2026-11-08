package com.truesim.common.procedural;

/**
 * Seeds derived from seeds: every generator draws its parts' seeds from its own with {@link #derive}, so a part's
 * result depends only on the generator's seed and the part's salt, never on the order parts are made in.
 *
 * <pre>{@code
 * long tree = Seeds.derive(forest, index);        // the forest's 17th tree: Seeds.derive(forest, 17)
 * long branch = Seeds.derive(tree, branchIndex);
 * }</pre>
 */
public final class Seeds {

    private Seeds() {
    }

    /** A seed for part {@code salt} of what {@code seed} makes: SplitMix64's finalizer over both, well mixed. */
    public static long derive(long seed, long salt) {
        return mix(seed + 0x9E3779B97F4A7C15L * (salt + 1));
    }

    /** SplitMix64's finalizer (Stafford's mix 13): a bijection of 64-bit values with full avalanche. */
    public static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
