package com.truesim.common.procedural.rock;

import java.util.List;

/**
 * What makes a rock: its size and proportions, the fractures that bound it, the weathering of its surface, its
 * bedding (layered limestone) and, for a pile, its members. All lengths in metres; fractions are of the size.
 *
 * <p>The shape is a convex polytope cut by fracture planes (a bedding pair and two joint sets for limestone blocks,
 * random planes for the rest), its edges rounded by weathering (a soft minimum of the planes), chipped at corners,
 * lumped and carved by noise, grooved and stepped along its bedding, and cut flat where it is buried.
 *
 * @param name a name, e.g. for logs and recipes
 * @param size the mean radius
 * @param elongation the length along x relative to the size
 * @param flatness the height relative to the size
 * @param fractures the planes that bound it
 * @param weathering how its surface is worn
 * @param bedding its layers
 * @param cluster a pile of several stones, or one
 * @param buried how much of its height lies under the ground (below it the rock is cut flat), 0 to 1
 * @param subdivisions the subdivisions of the icosahedron per level of detail, most detailed first (20 · 4^n
 *     triangles each, per stone)
 */
public record RockParams(String name, double size, double elongation, double flatness, Fractures fractures,
    Weathering weathering, Bedding bedding, Cluster cluster, double buried, List<Integer> subdivisions) {

    public RockParams {
        subdivisions = List.copyOf(subdivisions);
        if (!(size > 0) || subdivisions.isEmpty()) {
            throw new IllegalArgumentException(name + ": needs a size and at least one level");
        }
        for (int s : subdivisions) {
            if (s < 0 || s > 7) {
                throw new IllegalArgumentException(name + ": subdivisions are 0 to 7, got " + s);
            }
        }
        if (!(buried >= 0 && buried < 1)) {
            throw new IllegalArgumentException(name + ": buried is 0 to 1, got " + buried);
        }
    }

    /**
     * The planes that bound the rock (in unit space, before its proportions).
     *
     * @param planes random fracture planes
     * @param near the nearest a random plane comes to the centre (1 the unit sphere)
     * @param far the farthest
     * @param joints a limestone block's fracture system, 0 to 1: a bedding pair and two joint sets at right angles to
     *     it and to one another (0: none; between: the joints turned off square, 1: square)
     * @param facets how far the surface follows the planes (1) rather than a smooth ellipsoid (0)
     * @param chips small planes that knock corners off, showing fresh stone
     * @param chipDepth how deep a chip cuts into the corner, as a fraction of the size
     */
    public record Fractures(int planes, double near, double far, double joints, double facets, int chips,
        double chipDepth) {

        public Fractures {
            if (planes < 0 || chips < 0 || planes + chips > 48) {
                throw new IllegalArgumentException("0 to 48 planes and chips, got " + planes + " and " + chips);
            }
        }
    }

    /**
     * How the surface is worn.
     *
     * @param rounding how round the edges between planes are worn, as a fraction of the size (0 sharp)
     * @param lumpiness how far large lumps push the outline out and in
     * @param ridges the depth of ridged-noise crests and gullies (erosion-like detail)
     * @param cracks the depth of cracks along the borders of cellular noise's cells
     */
    public record Weathering(double rounding, double lumpiness, double ridges, double cracks) {
    }

    /**
     * Limestone's bedding: layers a rock breaks along and weathers between.
     *
     * @param thickness the beds' thickness, as a fraction of the size (0: no bedding)
     * @param groove the depth of the grooves where beds meet
     * @param recess how far softer beds weather back (each bed its own share of it)
     * @param dip how far the beds tilt from level, degrees
     */
    public record Bedding(double thickness, double groove, double recess, double dip) {

        /** No bedding. */
        public static final Bedding NONE = new Bedding(0, 0, 0, 0);
    }

    /**
     * A pile of stones (cleared from a field, or fallen together), each its own closed surface.
     *
     * @param members how many stones (1: a single rock)
     * @param spread how far the others lie from the first, as a multiple of their radii added (below 1: leaning in)
     * @param shrink each further stone's size relative to the one before, about
     */
    public record Cluster(int members, double spread, double shrink) {

        /** A single rock. */
        public static final Cluster SINGLE = new Cluster(1, 0, 1);

        public Cluster {
            if (members < 1 || members > 16) {
                throw new IllegalArgumentException("1 to 16 members, got " + members);
            }
        }
    }

    /** These parameters with {@code newSize}. */
    public RockParams withSize(double newSize) {
        return new RockParams(name, newSize, elongation, flatness, fractures, weathering, bedding, cluster, buried,
            subdivisions);
    }

    /** These parameters with {@code newBuried} of the height under the ground. */
    public RockParams withBuried(double newBuried) {
        return new RockParams(name, size, elongation, flatness, fractures, weathering, bedding, cluster, newBuried,
            subdivisions);
    }

    /** These parameters with {@code levels} as the subdivisions per level of detail. */
    public RockParams withSubdivisions(List<Integer> levels) {
        return new RockParams(name, size, elongation, flatness, fractures, weathering, bedding, cluster, buried,
            levels);
    }

    /**
     * About how far the rock reaches from its origin across the ground, metres (its footprint's bounding radius,
     * without generating it): the long half-axis, and a pile's spread.
     */
    public double reach() {
        double one = 1.35 * size * Math.max(1, elongation);
        if (cluster.members() == 1) {
            return one;
        }
        return one * (1 + 2 * cluster.spread() * cluster.shrink());
    }

    /** About how high it stands above the ground, metres. */
    public double height() {
        return 2 * size * flatness * (1 - buried);
    }

    private static final List<Integer> LEVELS = List.of(5, 4, 3, 2);

    /** A rounded boulder, weathered smooth with shallow gullies: stream beds, forest floors. */
    public static final RockParams BOULDER = new RockParams("boulder", 1.2, 1.25, 0.8,
        new Fractures(10, 0.7, 0.95, 0, 0.7, 0, 0), new Weathering(0.22, 0.12, 0.05, 0.012), Bedding.NONE,
        Cluster.SINGLE, 0.15, LEVELS);
    /**
     * A limestone block fallen from a cliff: bounded by its bedding and two joint sets, corners chipped off, the beds
     * grooved where they meet. Talus and rockfall.
     */
    public static final RockParams BLOCK = new RockParams("block", 1.0, 1.15, 0.8,
        new Fractures(4, 0.78, 0.95, 0.9, 1, 5, 0.12), new Weathering(0.035, 0.02, 0.025, 0.02),
        new Bedding(0.28, 0.025, 0.03, 6), Cluster.SINGLE, 0.1, LEVELS);
    /** A flat slab of bedded limestone, split along its beds. */
    public static final RockParams SLAB = new RockParams("slab", 1.4, 1.35, 0.32,
        new Fractures(5, 0.7, 0.92, 1, 1, 4, 0.1), new Weathering(0.06, 0.05, 0.03, 0.02),
        new Bedding(0.12, 0.02, 0.025, 3), Cluster.SINGLE, 0.2, LEVELS);
    /** Scree: a small sharp stone, the debris below Alpine cliffs. */
    public static final RockParams SCREE = new RockParams("scree", 0.25, 1.3, 0.7,
        new Fractures(8, 0.62, 0.9, 0.5, 1, 2, 0.12), new Weathering(0.03, 0.06, 0.04, 0.015), Bedding.NONE,
        Cluster.SINGLE, 0.1, List.of(4, 3, 2, 1));
    /**
     * A glacial erratic: a big rounded boulder of crystalline rock the ice left on the valley's floor and terraces,
     * lichened, little buried.
     */
    public static final RockParams ERRATIC = new RockParams("erratic", 2.6, 1.3, 0.7,
        new Fractures(12, 0.72, 0.98, 0, 0.55, 2, 0.06), new Weathering(0.3, 0.14, 0.04, 0.02), Bedding.NONE,
        Cluster.SINGLE, 0.2, LEVELS);
    /**
     * A clearance pile: stones picked off a meadow and heaped at its edge, rounded and mossy, leaning on one another.
     */
    public static final RockParams CLUSTER = new RockParams("cluster", 0.45, 1.2, 0.75,
        new Fractures(8, 0.68, 0.95, 0.3, 0.8, 1, 0.1), new Weathering(0.15, 0.1, 0.04, 0.012),
        new Bedding(0.3, 0.015, 0.02, 10), new Cluster(7, 0.75, 0.82), 0.25, List.of(4, 3, 2, 1));

    /** Every preset. */
    public static final List<RockParams> PRESETS = List.of(BOULDER, BLOCK, SLAB, SCREE, ERRATIC, CLUSTER);

    /** The preset named {@code name}. */
    public static RockParams preset(String name) {
        for (RockParams preset : PRESETS) {
            if (preset.name().equals(name)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("No rock preset '" + name + "'");
    }
}
