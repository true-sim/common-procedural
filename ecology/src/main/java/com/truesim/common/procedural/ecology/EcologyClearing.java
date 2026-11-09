package com.truesim.common.procedural.ecology;

/**
 * Ground kept open of plants: roads, yards and gardens, a village's square. The ecology's placement multiplies each
 * candidate's cover by {@link #keep}, so cleared ground gets no plants and its edge thins them out (before plants
 * are spaced, so no gaps open beside it). Implementations must be pure and thread-safe, as the terrain is.
 */
@FunctionalInterface
public interface EcologyClearing {

    /** Nothing kept open. */
    EcologyClearing NONE = (x, z, layer) -> 1;

    /** How much of the ecology's cover of {@code layer} is kept at ({@code x}, {@code z}): 0 cleared to 1 as it is. */
    double keep(double x, double z, Layer layer);
}
