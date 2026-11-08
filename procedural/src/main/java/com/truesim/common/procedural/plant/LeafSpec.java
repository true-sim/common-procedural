package com.truesim.common.procedural.plant;

import com.truesim.common.procedural.flora.Outline;

/**
 * A plant's leaves of one kind (its basal rosette, or the leaves along a stem, or a grass's blades): how many, their
 * size and outline, how they stand and arch. Metres and radians.
 *
 * @param minCount the fewest (basal: per plant; stem leaves: per stem)
 * @param maxCount the most
 * @param length a leaf's (or leaflet's) length
 * @param width its widest width
 * @param outline its outline
 * @param rise radians above the horizontal it leaves at
 * @param arch radians it curves over its length: negative arches over and down (grass blades), positive curls up
 * @param fold how far its edges are raised, a fraction of the half width: a keel or a cupped leaf
 * @param leaflets leaflets from the end of the leaf stalk: 1 simple, 3 trifoliate (clover), 5 palmate lobes
 *     (buttercup)
 * @param petiole the leaf stalk's length before the blade
 */
public record LeafSpec(int minCount, int maxCount, double length, double width, Outline outline, double rise,
    double arch, double fold, int leaflets, double petiole) {

    /** No leaves of this kind. */
    public static final LeafSpec NONE = new LeafSpec(0, 0, 0.01, 0.01, Outline.ELLIPTIC, 0, 0, 0, 1, 0);

    public LeafSpec {
        if (minCount < 0 || maxCount < minCount || length <= 0 || width <= 0 || leaflets < 1 || petiole < 0) {
            throw new IllegalArgumentException("Not leaves: " + minCount + " to " + maxCount + " of " + length);
        }
    }
}
