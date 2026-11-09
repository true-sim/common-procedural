package com.truesim.common.procedural.ecology.rocks;

import java.util.ArrayList;
import java.util.List;

/**
 * A rock field's tunables (common-procedural-assets' {@code RockSettings} declares them as engine settings).
 *
 * @param cell metres between the field's samples (the conditions, the cliffs above)
 * @param cliffs how cliffs are found and how far their rocks run out
 * @param kinds each kind's placement, by {@link RockKind} ordinal
 * @param sorting how sharply the talus' sizes grow downslope: the smallest size at a fraction {@code t} of the way
 *     from the cliff's foot to the run-out's end is its kind's smallest plus {@code t^sorting} of the way to 40 % of
 *     its largest
 * @param spread the sizes' spread about their mean (the standard deviation of the size's logarithm): streams,
 *     piles and erratics
 * @param fractal the talus' and rockfall's power law: the number of rocks larger than a size falls as the size to
 *     the minus this (about 2 to 3 for rockfall debris: Ruiz-Carulla et al. 2015, Hantz et al. 2016), between a
 *     smallest size that grows downslope and the kind's largest
 * @param tilt how far a rock leans with the ground's slope (1: lies flat on it, 0: stands upright)
 * @param packing how close rocks may lie, as a share of their radii added (below 1: touching, partly overlapping)
 * @param plantClearance how much of a plant's crown radius keeps its stem off a rock (crowns overhang rocks)
 * @param variants how many models the rocks share
 */
public record RockFieldParameters(double cell, Cliffs cliffs, List<KindPlacement> kinds, double sorting,
    double spread, double fractal, double tilt, double packing, double plantClearance, Variants variants) {

    public RockFieldParameters {
        kinds = List.copyOf(kinds);
        if (kinds.size() != RockKind.ALL.length) {
            throw new IllegalArgumentException("A placement for each of " + RockKind.ALL.length + " kinds");
        }
        if (!(cell > 0) || !(packing > 0)) {
            throw new IllegalArgumentException("A cell and packing above 0: " + cell + ", " + packing);
        }
    }

    /**
     * Where rocks fall from.
     *
     * @param slope ground steeper than this is a cliff, degrees
     * @param minHeight the least height of a cliff that sheds rocks, metres
     * @param search how far uphill to look for a cliff, metres along the climb
     * @param shadowAngle rockfall runs out no further than where the line to the cliff's top falls below this angle,
     *     degrees (the shadow angle: 27.5, Evans and Hungr 1993)
     * @param talusSlope a deposit steeper than this is talus (the cone), shallower is run-out, degrees
     * @param reposeSlope nothing rests on ground steeper than this, degrees (scree's angle of repose, about 35 to 40)
     * @param activeTalus the upper share of a talus (of the way from the cliff's foot to the run-out's end) where
     *     rocks still fall and the scree moves: no tree or shrub takes root there (herbs and grass do)
     */
    public record Cliffs(double slope, double minHeight, double search, double shadowAngle, double talusSlope,
        double reposeSlope, double activeTalus) {

        /** Lauterbrunnen's limestone walls: cliffs from 50 degrees, 15 m and up. */
        public static final Cliffs DEFAULTS = new Cliffs(50, 15, 600, 27.5, 24, 42, 0.4);
    }

    /**
     * How one kind is placed: a jittered candidate per cell, kept by its density.
     *
     * @param cell metres between candidates
     * @param density rocks per square metre where the kind is at its densest
     * @param minSize the smallest rock's size (its mean radius), metres
     * @param maxSize the largest
     */
    public record KindPlacement(double cell, double density, double minSize, double maxSize) {

        public KindPlacement {
            if (!(cell > 0) || !(density >= 0) || !(minSize > 0) || !(maxSize >= minSize)) {
                throw new IllegalArgumentException("A cell, a density and sizes: " + cell + ", " + density + ", "
                    + minSize + ", " + maxSize);
            }
        }

        public KindPlacement withDensity(double value) {
            return new KindPlacement(cell, value, minSize, maxSize);
        }
    }

    /**
     * How many models the rocks share: each rock is one of a preset's seeds, turned to one of a few headings, its moss
     * and lichen on one of a few levels, sunk to one of a few depths (each a model of its own, generated once).
     *
     * @param seeds shapes per preset
     * @param yawSteps headings
     * @param coverSteps levels of moss and of lichen
     * @param burialSteps depths
     */
    public record Variants(int seeds, int yawSteps, int coverSteps, int burialSteps) {

        public static final Variants DEFAULTS = new Variants(4, 4, 3, 2);

        public Variants {
            if (seeds < 1 || yawSteps < 1 || coverSteps < 2 || burialSteps < 1) {
                throw new IllegalArgumentException("At least a seed, a heading, two cover levels and a depth");
            }
        }
    }

    /** Talus, rockfall, stream boulders, clearance piles, erratics. */
    public static final List<KindPlacement> KINDS = List.of(
        new KindPlacement(1.5, 0.35, 0.25, 3),
        new KindPlacement(6, 0.003, 0.6, 4),
        new KindPlacement(3, 0.03, 0.25, 1.4),
        new KindPlacement(30, 0.0006, 0.4, 0.55),
        new KindPlacement(40, 0.00015, 1.6, 4.5));

    public static final RockFieldParameters DEFAULTS = new RockFieldParameters(4, Cliffs.DEFAULTS, KINDS, 1.2, 0.35,
        2.2, 0.85, 0.8, 0.3, Variants.DEFAULTS);

    /** The placement of {@code kind}. */
    public KindPlacement kind(RockKind kind) {
        return kinds.get(kind.ordinal());
    }

    public RockFieldParameters withCell(double value) {
        return new RockFieldParameters(value, cliffs, kinds, sorting, spread, fractal,
            tilt, packing, plantClearance, variants);
    }

    public RockFieldParameters withCliffs(Cliffs value) {
        return new RockFieldParameters(cell, value, kinds, sorting, spread, fractal,
            tilt, packing, plantClearance, variants);
    }

    /** These parameters with {@code kind} placed by {@code value}. */
    public RockFieldParameters withKind(RockKind kind, KindPlacement value) {
        List<KindPlacement> list = new ArrayList<>(kinds);
        list.set(kind.ordinal(), value);
        return new RockFieldParameters(cell, cliffs, list, sorting, spread, fractal,
            tilt, packing, plantClearance, variants);
    }

    public RockFieldParameters withSorting(double value) {
        return new RockFieldParameters(cell, cliffs, kinds, value, spread, fractal,
            tilt, packing, plantClearance, variants);
    }

    public RockFieldParameters withSpread(double value) {
        return new RockFieldParameters(cell, cliffs, kinds, sorting, value, fractal,
            tilt, packing, plantClearance, variants);
    }

    public RockFieldParameters withFractal(double value) {
        return new RockFieldParameters(cell, cliffs, kinds, sorting, spread, value, tilt, packing, plantClearance,
            variants);
    }

    public RockFieldParameters withTilt(double value) {
        return new RockFieldParameters(cell, cliffs, kinds, sorting, spread, fractal,
            value, packing, plantClearance, variants);
    }

    public RockFieldParameters withPacking(double value) {
        return new RockFieldParameters(cell, cliffs, kinds, sorting, spread, fractal,
            tilt, value, plantClearance, variants);
    }

    public RockFieldParameters withPlantClearance(double value) {
        return new RockFieldParameters(cell, cliffs, kinds, sorting, spread, fractal, tilt, packing, value, variants);
    }

    public RockFieldParameters withVariants(Variants value) {
        return new RockFieldParameters(cell, cliffs, kinds, sorting, spread, fractal,
            tilt, packing, plantClearance, value);
    }
}
