package com.truesim.common.procedural.rock;

import java.util.List;

/**
 * What makes a rock: its size and proportions, and the weathering carved into it. All lengths in metres.
 *
 * @param name a name, e.g. for logs and recipes
 * @param size the mean radius
 * @param elongation the length along x relative to the size
 * @param flatness the height relative to the size
 * @param lumpiness how far large lumps push the outline out and in, as a fraction of the size
 * @param ridges the depth of ridged-noise crests and gullies (erosion-like detail), as a fraction of the size
 * @param facets how strongly the surface breaks into flat planes (cleaved stone), 0 to 1
 * @param cracks the depth of cracks between the facets, as a fraction of the size
 * @param strata the depth of horizontal bands (layered rock), as a fraction of the size
 * @param buried how much of the height lies under the ground: below it the rock is cut flat
 * @param subdivisions the subdivisions of the icosahedron per level of detail, most detailed first (20 · 4^n
 *     triangles each)
 */
public record RockParams(String name, double size, double elongation, double flatness, double lumpiness,
    double ridges, double facets, double cracks, double strata, double buried, List<Integer> subdivisions) {

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
    }

    private static final List<Integer> LEVELS = List.of(5, 4, 3, 2);

    /** A rounded boulder, weathered smooth with shallow gullies. */
    public static final RockParams BOULDER = new RockParams("boulder", 1.2, 1.25, 0.8, 0.22, 0.06, 0.15, 0.015, 0,
        0.15, LEVELS);
    /** A cleaved block of granite: flat faces, sharp edges, cracks between them. */
    public static final RockParams BLOCK = new RockParams("block", 1.0, 1.1, 0.9, 0.08, 0.05, 1, 0.03, 0, 0.1,
        LEVELS);
    /** A flat slab of layered limestone. */
    public static final RockParams SLAB = new RockParams("slab", 1.5, 1.4, 0.35, 0.12, 0.05, 0.6, 0.02, 0.05, 0.2,
        LEVELS);
    /** Scree: a small sharp stone, the debris below Alpine cliffs. */
    public static final RockParams SCREE = new RockParams("scree", 0.25, 1.3, 0.7, 0.1, 0.06, 1, 0.02, 0, 0.1,
        List.of(4, 3, 2, 1));

    /** Every preset. */
    public static final List<RockParams> PRESETS = List.of(BOULDER, BLOCK, SLAB, SCREE);
}
