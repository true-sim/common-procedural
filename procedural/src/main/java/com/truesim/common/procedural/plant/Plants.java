package com.truesim.common.procedural.plant;

/**
 * Small plants and clumps, the direct way: a species, options and a seed in, a {@link Plant} out, deterministic and
 * engine-agnostic.
 *
 * <pre>{@code
 * Plant gentian = Plants.generate(PlantSpecies.GENTIAN, 3);
 * Plant grass = Plants.generate(PlantSpecies.GRASS, PlantOptions.DEFAULTS.withDay(250), 4);
 * }</pre>
 *
 * Each thread uses a {@link PlantGenerator} of its own.
 */
public final class Plants {

    private static final ThreadLocal<PlantGenerator> GENERATORS = ThreadLocal.withInitial(PlantGenerator::new);

    private Plants() {
    }

    /** A plant of {@code species} with {@link PlantOptions#DEFAULTS}. */
    public static Plant generate(PlantSpecies species, long seed) {
        return GENERATORS.get().generate(species, PlantOptions.DEFAULTS, seed);
    }

    /** A plant of {@code species} as {@code options} say. */
    public static Plant generate(PlantSpecies species, PlantOptions options, long seed) {
        return GENERATORS.get().generate(species, options, seed);
    }
}
