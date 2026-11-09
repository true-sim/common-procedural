package com.truesim.common.procedural.ecology;

import java.util.List;

/**
 * Everything the ecology's result depends on besides the terrain and the species: the seed, the climate, the tiling,
 * the terrain analysis, the hydrology's thresholds, placement and land use. A value: equal parameters over the same
 * terrain and species give identical tiles.
 *
 * @param seed the seed every random choice derives from
 * @param climate the climate
 * @param tileSize a tile's side, metres
 * @param cell the fields' sample spacing, metres (a tile has {@code tileSize / cell + 1} samples a side)
 * @param analysis the terrain analysis (hydrology, topographic position)
 * @param hydrology what the hydrology makes streams, rivers, lakes, riparian ground and wetlands of
 * @param placement how plants are placed
 * @param landUse how people keep the land open
 * @param sharpness how strongly the best-suited species dominate a layer (the exponent on suitability in the shares)
 * @param woody where trees and shrubs can root: not on near-vertical faces nor where the soil is too thin
 */
public record EcologyParameters(long seed, Climate climate, double tileSize, double cell, Analysis analysis,
    Hydrology hydrology, Placement placement, LandUse landUse, double sharpness, Woody woody) {

    /** Parameters with {@link Woody#DEFAULTS}. */
    public EcologyParameters(long seed, Climate climate, double tileSize, double cell, Analysis analysis,
        Hydrology hydrology, Placement placement, LandUse landUse, double sharpness) {
        this(seed, climate, tileSize, cell, analysis, hydrology, placement, landUse, sharpness, Woody.DEFAULTS);
    }

    /**
     * Where trees and shrubs can root. Ground steeper than {@code maxSlope} is a rock face: no tree stands there (on
     * real cliffs only along ledges, finer than the terrain). The soil's depth is estimated from the ground's soil
     * share and its slope ({@link Site#soilDepth}: about 1 m on gentle soil, thinning to nothing toward the
     * steepest); trees need {@code minSoil}, shrubs half of it.
     *
     * @param maxSlope the steepest ground trees and shrubs stand on, degrees (they thin over the 8 degrees below)
     * @param minSoil the least soil depth a tree needs, metres
     */
    public record Woody(double maxSlope, double minSoil) {

        /** Trees up to 48 degrees, on 25 cm of soil and more. */
        public static final Woody DEFAULTS = new Woody(48, 0.25);
    }

    /**
     * The terrain analysis: the heightfield sampled every {@code cell} metres in blocks of {@code block} metres, each
     * analysed over a window {@code margin} wider on every side (so catchments reach that far upstream), blocks
     * blended into each other so the fields are continuous and the same whichever tile asks.
     *
     * @param cell the analysis' sample spacing, metres
     * @param block a block's side, metres
     * @param margin how far a block's window reaches beyond it, metres (at least half a block)
     * @param positionRadius the radius of the topographic position index's neighbourhood, metres
     * @param exposedPosition the position index (metres above the neighbourhood's mean) of full wind exposure
     * @param blocks how many analysed blocks are kept
     */
    public record Analysis(double cell, double block, double margin, double positionRadius, double exposedPosition,
        int blocks) {

        /** 8 m samples in 2 km blocks with 2 km margins; positions over 150 m; 16 blocks kept (about 265 MB). */
        public static final Analysis DEFAULTS = new Analysis(8, 2048, 2048, 150, 40, 16);

        public Analysis {
            if (margin < block / 2 + positionRadius) {
                throw new IllegalArgumentException("The margin must be at least half a block plus the position radius");
            }
            if (block % cell != 0 || margin % cell != 0) {
                throw new IllegalArgumentException("Blocks and margins are whole numbers of cells");
            }
        }
    }

    /**
     * The hydrology's thresholds.
     *
     * @param streamArea the catchment area of a stream's start, square metres
     * @param riverArea the catchment area from which a stream shows as open water, square metres
     * @param riverWidth a river's width per square root of its catchment in square kilometres, metres
     * @param riparianWidth the riparian band's half width per square root of the catchment in square kilometres,
     *     metres, beyond its base of {@code riparianBase}
     * @param riparianBase the riparian band's least half width, metres
     * @param riparianHeight the height above the channel up to which ground is riparian, metres (rising with the
     *     square root of the catchment by a tenth of a metre per square-root square kilometre)
     * @param wetlandSlope the steepest a wetland lies, degrees
     * @param lakeDepth the depth of a filled hollow from which it holds open water, metres
     * @param wetnessReference the topographic wetness index of an ordinary slope (neither drier nor wetter)
     */
    public record Hydrology(double streamArea, double riverArea, double riverWidth, double riparianWidth,
        double riparianBase, double riparianHeight, double wetlandSlope, double lakeDepth, double wetnessReference) {

        /** Streams from 5 ha, open water from 2 km2 (3 m wide per root square kilometre), wetlands to 4 degrees. */
        public static final Hydrology DEFAULTS = new Hydrology(50_000, 2_000_000, 3, 6, 8, 3, 4, 0.5, 8);
    }

    /**
     * How one layer is placed: a candidate in every {@code cell} of a world-aligned grid (jittered), kept by the
     * layer's cover times {@code fill}, then thinned so no two plants are closer than {@code spacing} times their
     * crowns' radii added.
     *
     * @param cell the candidate grid's spacing, metres
     * @param fill the share of candidates where the layer's cover is full
     * @param spacing the share of crown radii plants keep apart
     * @param instances whether the layer is placed as instances at all (else only its fields)
     */
    public record LayerPlacement(double cell, double fill, double spacing, boolean instances) {
    }

    /**
     * Placement.
     *
     * @param layers per {@link Layer}, by ordinal
     * @param iterations rounds of the local thinning (more approaches greedy thinning; the halo grows with them)
     */
    public record Placement(List<LayerPlacement> layers, int iterations) {

        /**
         * Trees from a 4 m grid, shrubs 2.5 m, dwarf shrubs 2 m, herb clumps 2.5 m; grass and ground as fields only.
         */
        public static final Placement DEFAULTS = new Placement(List.of(
            new LayerPlacement(4, 1, 0.75, true),
            new LayerPlacement(2.5, 0.8, 0.8, true),
            new LayerPlacement(2, 0.7, 0.9, true),
            new LayerPlacement(2.5, 0.6, 1, true),
            new LayerPlacement(1, 1, 1, false),
            new LayerPlacement(1, 1, 1, false)), 2);

        public Placement {
            layers = List.copyOf(layers);
            if (layers.size() != Layer.ALL.length) {
                throw new IllegalArgumentException("One placement per layer");
            }
        }

        /** This placement with {@code layer} placed as instances or not. */
        public Placement withInstances(Layer layer, boolean instances) {
            List<LayerPlacement> all = new java.util.ArrayList<>(layers);
            LayerPlacement p = all.get(layer.ordinal());
            all.set(layer.ordinal(), new LayerPlacement(p.cell(), p.fill(), p.spacing(), instances));
            return new Placement(all, iterations);
        }
    }

    /**
     * Land kept open by people: meadows and pastures on gentle slopes below {@code maxAltitude}, the forest left in
     * patches and on steeper ground, orchards round them where apples grow.
     *
     * @param meadows whether land is kept open at all (false: the potential natural vegetation)
     * @param maxSlope the steepest meadows, degrees
     * @param maxAltitude the highest meadows, metres
     * @param patches the scale of the forest patches left, metres
     */
    public record LandUse(boolean meadows, double maxSlope, double maxAltitude, double patches) {

        /** No land use: the potential natural vegetation. */
        public static final LandUse NONE = new LandUse(false, 26, 1850, 180);
        /** Meadows below 1850 m on slopes under 26 degrees, as in the Alpine valleys. */
        public static final LandUse MEADOWS = new LandUse(true, 26, 1850, 180);
    }

    /** The northern Alps' climate, 256 m tiles sampled every 4 m, natural vegetation. */
    public static final EcologyParameters ALPS = new EcologyParameters(1, Climate.NORTHERN_ALPS, 256, 4,
        Analysis.DEFAULTS, Hydrology.DEFAULTS, Placement.DEFAULTS, LandUse.NONE, 3, Woody.DEFAULTS);

    public EcologyParameters {
        if (tileSize % cell != 0) {
            throw new IllegalArgumentException("A tile is a whole number of cells");
        }
    }

    public EcologyParameters withSeed(long value) {
        return new EcologyParameters(value, climate, tileSize, cell, analysis, hydrology, placement, landUse,
            sharpness, woody);
    }

    public EcologyParameters withClimate(Climate value) {
        return new EcologyParameters(seed, value, tileSize, cell, analysis, hydrology, placement, landUse, sharpness,
            woody);
    }

    public EcologyParameters withTiles(double size, double spacing) {
        return new EcologyParameters(seed, climate, size, spacing, analysis, hydrology, placement, landUse, sharpness,
            woody);
    }

    public EcologyParameters withAnalysis(Analysis value) {
        return new EcologyParameters(seed, climate, tileSize, cell, value, hydrology, placement, landUse, sharpness,
            woody);
    }

    public EcologyParameters withHydrology(Hydrology value) {
        return new EcologyParameters(seed, climate, tileSize, cell, analysis, value, placement, landUse, sharpness,
            woody);
    }

    public EcologyParameters withPlacement(Placement value) {
        return new EcologyParameters(seed, climate, tileSize, cell, analysis, hydrology, value, landUse, sharpness,
            woody);
    }

    public EcologyParameters withWoody(Woody value) {
        return new EcologyParameters(seed, climate, tileSize, cell, analysis, hydrology, placement, landUse, sharpness,
            value);
    }

    public EcologyParameters withLandUse(LandUse value) {
        return new EcologyParameters(seed, climate, tileSize, cell, analysis, hydrology, placement, value, sharpness,
            woody);
    }
}
