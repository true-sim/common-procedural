package com.truesim.common.procedural.ecology.terrain;

import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.ecology.Ground;
import com.truesim.effects.terrain.data.TerrainSurface;

/**
 * The ecology's terrain over effects-terrain's: heights from its surface (the same function its tiles and CPU queries
 * read), and optionally the ground from its material's layers.
 *
 * <pre>{@code
 * TerrainSurface surface = new TerrainSurface(site, SurfaceParameters.DATA);
 * EcologyTerrain ground = TerrainEcology.of(surface, 2);                        // heights only
 * EcologyTerrain layered = TerrainEcology.of(surface, 2, terrain::layers);      // with a Terrain's layers
 * Ecology ecology = new Ecology(layered, EcologyParameters.ALPS, AlpineSpecies.ALL);
 * }</pre>
 *
 * <ul>
 *   <li><b>Altitude</b> (the climate's): the surface's height as tiles of {@code spacing} draw it ({@link
 *       TerrainSurface#heightAt}), plus the curvature's fall, so the climate follows the altitude above sea level.</li>
 *   <li><b>Height</b> (where plants stand): {@code heightAt}, as {@code Terrain.heightAt} gives it.</li>
 *   <li><b>Ground</b>: effects-terrain's layers (rock, grass, scree, snow, as {@code Terrain.layers} and
 *       {@code TerrainLayers.weights} give them) as rock, soil, scree and snow; without layers the ecology works the
 *       ground out itself.</li>
 * </ul>
 *
 * effects-terrain's {@code vegetation} (the grass layer's weight) is the soil fraction here; slope and normal come
 * from the heights (the same triangles {@code normalAt} reads).
 */
public final class TerrainEcology implements EcologyTerrain {

    /**
     * The material's layer weights at a point, as effects-terrain's {@code Terrain.layers} and {@code
     * TerrainLayers.weights} give them: rock, grass, scree, snow.
     */
    @FunctionalInterface
    public interface Layers {

        /** effects-terrain's index of rock. */
        int ROCK = 0;
        /** Of grass. */
        int GRASS = 1;
        /** Of scree. */
        int SCREE = 2;
        /** Of snow. */
        int SNOW = 3;

        /** The weights at ({@code x}, {@code z}) into {@code weights}. */
        void layers(double x, double z, double[] weights);
    }

    private static final ThreadLocal<double[]> WEIGHTS = ThreadLocal.withInitial(() -> new double[4]);

    private final TerrainSurface surface;
    private final double spacing;
    private final Layers layers;

    private TerrainEcology(TerrainSurface surface, double spacing, Layers layers) {
        this.surface = surface;
        this.spacing = spacing;
        this.layers = layers;
    }

    /** The surface's heights as a grid {@code spacing} metres apart draws them; the ground worked out by the ecology. */
    public static TerrainEcology of(TerrainSurface surface, double spacing) {
        return new TerrainEcology(surface, spacing, null);
    }

    /** The surface's heights, and the ground from {@code layers}. */
    public static TerrainEcology of(TerrainSurface surface, double spacing, Layers layers) {
        return new TerrainEcology(surface, spacing, layers);
    }

    public TerrainSurface surface() {
        return surface;
    }

    @Override
    public double altitude(double x, double z) {
        return surface.heightAt(x, z, spacing) + surface.fall(x, z);
    }

    @Override
    public double height(double x, double z) {
        return surface.heightAt(x, z, spacing);
    }

    @Override
    public boolean ground(double x, double z, double[] fractions) {
        if (layers == null) {
            return false;
        }
        double[] w = WEIGHTS.get();
        layers.layers(x, z, w);
        fractions[Ground.ROCK] = w[Layers.ROCK];
        fractions[Ground.SCREE] = w[Layers.SCREE];
        fractions[Ground.SOIL] = w[Layers.GRASS];
        fractions[Ground.SNOW] = w[Layers.SNOW];
        return true;
    }
}
