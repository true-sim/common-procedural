package com.truesim.common.procedural.settlement;

/**
 * What the buildings generator is asked to build: the meeting point of the settlement's plan and the generator. A
 * building is made in its own frame: x across its front, z along its depth with +z its front, y up from its ground
 * floor (its foundation reaching below, as the ground under a footprint is not level); the plan places it
 * ({@link Settlement.Building}: a position, the ground floor's height, a turn about up).
 *
 * @param kind what it is
 * @param width its footprint across the front, metres
 * @param depth its footprint from front to back, metres
 * @param floors full storeys above the ground floor's level, at least 1 (the roof's space not counted)
 * @param ridge whether the roof's ridge runs across the front (eaves to the front) or along the depth (a gable front)
 * @param seed the seed of its details (windows, balconies, materials' variation)
 */
public record BuildingSpec(BuildingKind kind, double width, double depth, int floors, Ridge ridge, long seed) {

    /** Which way a pitched roof's ridge runs. */
    public enum Ridge {
        /** Along the footprint's width, parallel to the front: the eaves face the front (and the valley). */
        ACROSS,
        /** Along the footprint's depth: the gable faces the front. */
        ALONG
    }

    public BuildingSpec {
        if (width <= 0 || depth <= 0 || floors < 1) {
            throw new IllegalArgumentException("A building needs a footprint and a floor: " + width + " x " + depth
                + ", " + floors);
        }
    }
}
