package com.truesim.common.procedural.fence;

/**
 * Procedural fences, the direct way: a type, options, a line over the ground and a seed in, a {@link Fence} out,
 * deterministic and engine-agnostic.
 *
 * <pre>{@code
 * double[] line = {0, 0, 12, 3, 20, 9};                                  // x, z pairs in the ground's frame
 * Fence fence = Fences.generate(FenceType.SCHAERHAG, FenceOptions.DEFAULTS, line, terrain::height, 7);
 * ProceduralMesh near = fence.lods().getFirst();                         // wood 0, stone 1, metal 2
 * Vec3 at = fence.origin();                                              // place the mesh there
 * }</pre>
 *
 * Each thread uses a {@link FenceGenerator} of its own, kept for the thread's next fence.
 */
public final class Fences {

    private static final ThreadLocal<FenceGenerator> GENERATORS = ThreadLocal.withInitial(FenceGenerator::new);

    private Fences() {
    }

    /** A fence with {@link FenceTuning#DEFAULTS}. */
    public static Fence generate(FenceType type, FenceOptions options, double[] line, FenceGround ground, long seed) {
        return generate(type, options, FenceTuning.DEFAULTS, line, ground, seed);
    }

    /** A fence of {@code type} along {@code line} (x, z pairs) over {@code ground}, tuned by {@code tuning}. */
    public static Fence generate(FenceType type, FenceOptions options, FenceTuning tuning, double[] line,
        FenceGround ground, long seed) {
        return GENERATORS.get().generate(type, options, tuning, line, ground, seed);
    }
}
