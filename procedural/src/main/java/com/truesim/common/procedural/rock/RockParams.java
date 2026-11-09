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
     * @param irregularity how far each stone's proportions stray from the preset's, 0 to 1 (its length, width and
     *     height each scaled by up to this share either way, from its seed)
     */
    public record Fractures(int planes, double near, double far, double joints, double facets, int chips,
        double chipDepth, double irregularity) {

        public Fractures {
            if (!(irregularity >= 0 && irregularity < 1)) {
                throw new IllegalArgumentException("Irregularity is 0 to 1, got " + irregularity);
            }
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
     * @param waviness how far the beds' boundaries wander, as a share of their thickness (uneven beds: their
     *     thickness varies across the stone and from bed to bed)
     */
    public record Bedding(double thickness, double groove, double recess, double dip, double waviness) {

        /** No bedding. */
        public static final Bedding NONE = new Bedding(0, 0, 0, 0, 0);
    }

    /**
     * Several stones in one model, each its own closed surface: a heaped pile (cleared from a field, fallen together)
     * or a flat patch of debris (scree between the blocks of a talus, drawn as one model from afar).
     *
     * @param members how many stones (1: a single rock)
     * @param spread a pile: how far the others lie from the first, as a multiple of their radii added (below 1:
     *     leaning in); a patch: the patch's radius, as a multiple of the size
     * @param shrink a pile: each further stone's size relative to the one before, about; a patch: the smallest
     *     stone's size relative to the largest (sizes between follow a power law, many small, few large)
     * @param heap a pile heaped in two courses (true) or a patch spread flat over a disc (false: a sunflower spiral)
     */
    public record Cluster(int members, double spread, double shrink, boolean heap) {

        /** A single rock. */
        public static final Cluster SINGLE = new Cluster(1, 0, 1, true);

        public Cluster {
            if (members < 1 || members > 64) {
                throw new IllegalArgumentException("1 to 64 members, got " + members);
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
        if (!cluster.heap()) {
            return size * cluster.spread() + one;
        }
        return one * (1 + 2 * cluster.spread() * cluster.shrink());
    }

    /** About how high it stands above the ground, metres. */
    public double height() {
        return 2 * size * flatness * (1 - buried);
    }

    private static final List<Integer> LEVELS = List.of(5, 4, 3, 2);

    /**
     * A boulder, worn round at its edges but still showing a few large fracture facets: stream beds, forest floors.
     */
    public static final RockParams BOULDER = new RockParams("boulder", 1.2, 1.25, 0.75,
        new Fractures(7, 0.74, 0.95, 0, 0.92, 0, 0, 0.25), new Weathering(0.12, 0.035, 0.015, 0.006), Bedding.NONE,
        Cluster.SINGLE, 0.15, LEVELS);
    /**
     * A limestone block fallen from a cliff: angular, bounded by sharp fracture faces (its joints turned well off
     * square, deep random fractures), corners chipped off, its bedding only faint uneven lines; proportions varying
     * stone by stone. Talus and rockfall.
     */
    public static final RockParams BLOCK = new RockParams("block", 1.0, 1.2, 0.8,
        new Fractures(6, 0.52, 0.88, 0.45, 1, 4, 0.16, 0.35), new Weathering(0.012, 0.01, 0.01, 0.008),
        new Bedding(0.42, 0.006, 0.006, 8, 0.3), Cluster.SINGLE, 0.1, LEVELS);
    /** A slab of limestone split along a bedding plane, its edges broken across: thin, angular, irregular. */
    public static final RockParams SLAB = new RockParams("slab", 1.3, 1.3, 0.38,
        new Fractures(7, 0.55, 0.9, 0.6, 1, 3, 0.14, 0.3), new Weathering(0.015, 0.012, 0.01, 0.008),
        new Bedding(0.5, 0.005, 0.005, 4, 0.3), Cluster.SINGLE, 0.15, LEVELS);
    /** Scree: a small sharp stone, the debris below Alpine cliffs. */
    public static final RockParams SCREE = new RockParams("scree", 0.25, 1.3, 0.7,
        new Fractures(8, 0.55, 0.9, 0.4, 1, 2, 0.14, 0.35), new Weathering(0.01, 0.01, 0.01, 0.005), Bedding.NONE,
        Cluster.SINGLE, 0.1, List.of(4, 3, 2, 1));
    /**
     * A glacial erratic: a big boulder of crystalline rock the ice left on the valley's floor and terraces, its large
     * facets rounded, lichened, little buried.
     */
    public static final RockParams ERRATIC = new RockParams("erratic", 2.6, 1.3, 0.7,
        new Fractures(9, 0.74, 0.97, 0, 0.88, 2, 0.06, 0.25), new Weathering(0.16, 0.04, 0.015, 0.01), Bedding.NONE,
        Cluster.SINGLE, 0.2, LEVELS);
    /**
     * A clearance pile: stones picked off a meadow and heaped at its edge, rounded and mossy, leaning on one another.
     */
    public static final RockParams CLUSTER = new RockParams("cluster", 0.45, 1.2, 0.75,
        new Fractures(8, 0.68, 0.95, 0.3, 0.85, 1, 0.1, 0.3), new Weathering(0.1, 0.04, 0.015, 0.008),
        new Bedding(0.4, 0.006, 0.006, 10, 0.3), new Cluster(7, 0.75, 0.82, true), 0.25, List.of(4, 3, 2, 1));
    /**
     * A patch of talus debris: 64 angular stones from 3 to 25 cm (the largest about this preset's size) spread over a
     * disc about 1.1 m across by a power law, many small and few large: the scree between a talus' blocks drawn as
     * one model, its coarse levels far away (near the camera, single stones).
     */
    public static final RockParams SCREE_PATCH = new RockParams("scree-patch", 0.12, 1.3, 0.7,
        new Fractures(7, 0.55, 0.9, 0.4, 1, 1, 0.14, 0.35), new Weathering(0.01, 0.01, 0.01, 0.005), Bedding.NONE,
        new Cluster(64, 4.5, 0.12, false), 0.25, List.of(2, 1, 0));

    /** Every preset. */
    public static final List<RockParams> PRESETS = List.of(BOULDER, BLOCK, SLAB, SCREE, ERRATIC, CLUSTER,
        SCREE_PATCH);

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
