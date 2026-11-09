package com.truesim.common.procedural.rock;

/**
 * Procedural rocks, the direct way: parameters, a cover and a seed in, a {@link Rock} out, deterministic and
 * engine-agnostic.
 *
 * <pre>{@code
 * Rock block = Rocks.generate(RockParams.BLOCK, 42);
 * Rock mossy = Rocks.generate(RockParams.BOULDER, new RockCover(0.8, 0.3, yaw, 0.3), 7); // placed with yaw
 * ProceduralMesh near = block.lods().getFirst();       // submesh 0 weathered, 1 fresh fractures
 * RockFootprint ground = block.footprint();            // keeps plants and grass off it
 * }</pre>
 */
public final class Rocks {

    private static final ThreadLocal<RockGenerator> GENERATORS = ThreadLocal.withInitial(RockGenerator::new);

    private Rocks() {
    }

    /** A bare rock as {@code params} say, made from {@code seed}. */
    public static Rock generate(RockParams params, long seed) {
        return GENERATORS.get().generate(params, seed);
    }

    /** A rock as {@code params} say, with {@code cover}, made from {@code seed}. */
    public static Rock generate(RockParams params, RockCover cover, long seed) {
        return GENERATORS.get().generate(params, cover, seed);
    }
}
