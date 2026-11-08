package com.truesim.common.procedural.tree;

/**
 * Procedural trees, the direct way: a species, options and a seed in, a {@link Tree} out (its levels of detail, a
 * billboard and impostor hints), deterministic and engine-agnostic.
 *
 * <pre>{@code
 * Tree spruce = Trees.generate(TreeSpecies.SPRUCE, 42);
 * Tree larch = Trees.generate(TreeSpecies.LARCH, TreeOptions.DEFAULTS.withSeason(Season.AUTUMN), 7);
 * ProceduralMesh near = spruce.lods().getFirst();                         // bark submesh 0, leaves 1
 * }</pre>
 *
 * Each thread uses a {@link TreeGenerator} of its own, kept for the thread's next tree.
 */
public final class Trees {

    private static final ThreadLocal<TreeGenerator> GENERATORS = ThreadLocal.withInitial(TreeGenerator::new);

    private Trees() {
    }

    /** A tree of {@code species} with {@link TreeOptions#DEFAULTS}. */
    public static Tree generate(TreeSpecies species, long seed) {
        return GENERATORS.get().generate(species, seed);
    }

    /** A tree of {@code species} as {@code options} say. */
    public static Tree generate(TreeSpecies species, TreeOptions options, long seed) {
        return GENERATORS.get().generate(species, options, seed);
    }
}
