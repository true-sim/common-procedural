package com.truesim.common.procedural.ecology.terrain;

import com.truesim.common.math.Maths;
import com.truesim.common.math.random.Noise;
import com.truesim.common.procedural.ecology.rocks.RockField;
import com.truesim.effects.terrain.data.TerrainModifier;

/**
 * The fines of a talus in the terrain itself, as an effects-terrain {@link TerrainModifier}: gravel laid into the
 * terrain's material over the talus ({@link RockField#talus}: {@link TerrainModifier#GRAVEL}), and a little roughness
 * in its height there (the debris' surface, a few centimetres, fading where the terrain is sampled coarser than its
 * grain). Between the field's instanced blocks and the scattered stones, the ground is then debris, not smooth soil.
 *
 * <pre>{@code
 * TerrainSurface bare = new TerrainSurface(site, parameters);                    // what the rocks are worked out from
 * RockField rocks = new RockField(new Ecology(TerrainEcology.of(bare, 2), ...), RockFieldParameters.DEFAULTS);
 * TalusModifier talus = new TalusModifier(rocks, TalusModifier.Parameters.DEFAULTS, bounds);
 * TerrainSurface surface = bare.withModifiers(bare.modifiers().with(talus));    // tiles and queries see the gravel
 * }</pre>
 *
 * The field must be worked out from the unmodified surface (as above): the modifier asks it. Its talus cover is cached
 * in blocks, so a modifier is cheap after its first queries of a block; ask it only within {@code bounds} (the talus
 * of a region of interest) and at fine footprints ({@link Parameters#maxFootprint}): far tiles do not see it.
 */
public final class TalusModifier implements TerrainModifier {

    /**
     * The modifier's tunables.
     *
     * @param gravel how much of the talus' cover is gravel, 0 to 1
     * @param roughness the debris' height variation, metres (half of it either way)
     * @param grain the roughness' size, metres
     * @param maxFootprint the coarsest sampling that sees the gravel at all, metres (the roughness fades from a grain
     *     on)
     */
    public record Parameters(double gravel, double roughness, double grain, double maxFootprint) {

        /** All gravel, 8 cm of roughness at 40 cm, seen up to 32 m samples. */
        public static final Parameters DEFAULTS = new Parameters(1, 0.08, 0.4, 32);
    }

    private final RockField field;
    private final Parameters parameters;
    private final Bounds bounds;
    private final Noise noise;

    /** Gravel and roughness over {@code field}'s talus within {@code bounds} (null: anywhere). */
    public TalusModifier(RockField field, Parameters parameters, Bounds bounds) {
        this.field = field;
        this.parameters = parameters;
        this.bounds = bounds;
        this.noise = Noise.seeded(field.ecology().parameters().seed() ^ 0x7a1a5L);
    }

    @Override
    public double height(double x, double z, double footprint, double height) {
        if (footprint > parameters.maxFootprint() || parameters.roughness() <= 0) {
            return height;
        }
        double fade = 1 - Maths.smoothstep(parameters.grain(), 4 * parameters.grain(), footprint);
        if (fade <= 0) {
            return height;
        }
        double cover = field.talus(x, z);
        if (cover <= 0) {
            return height;
        }
        double g = 1 / parameters.grain();
        double bumps = noise.fractal(x * g, z * g, 2);
        return height + 0.5 * parameters.roughness() * cover * fade * bumps;
    }

    @Override
    public double materials(double x, double z, double footprint, double[] cover) {
        if (footprint > parameters.maxFootprint()) {
            return 0;
        }
        double gravel = parameters.gravel() * field.talus(x, z);
        cover[GRAVEL] = gravel;
        return gravel;
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }
}
