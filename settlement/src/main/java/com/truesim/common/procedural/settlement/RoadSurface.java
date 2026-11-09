package com.truesim.common.procedural.settlement;

/** What a road is surfaced with: each a material slot of the road meshes ({@link RoadMeshes}). */
public enum RoadSurface {
    /** Cobbles: the square, and the main road and lanes in the core (with kerbs). */
    COBBLES,
    /** Gravel: the main road and lanes outside the core, farm tracks. */
    GRAVEL,
    /** Beaten earth: footpaths. */
    DIRT
}
