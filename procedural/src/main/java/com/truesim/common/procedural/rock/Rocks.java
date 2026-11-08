package com.truesim.common.procedural.rock;

/**
 * Procedural rocks, the direct way: parameters and a seed in, a {@link Rock} out, deterministic and engine-agnostic.
 *
 * <pre>{@code
 * Rock boulder = Rocks.generate(RockParams.BOULDER, 42);
 * ProceduralMesh near = boulder.lods().getFirst();
 * }</pre>
 */
public final class Rocks {

    private static final ThreadLocal<RockGenerator> GENERATORS = ThreadLocal.withInitial(RockGenerator::new);

    private Rocks() {
    }

    /** A rock as {@code params} say, made from {@code seed}. */
    public static Rock generate(RockParams params, long seed) {
        return GENERATORS.get().generate(params, seed);
    }
}
