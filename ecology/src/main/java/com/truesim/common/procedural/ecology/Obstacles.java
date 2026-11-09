package com.truesim.common.procedural.ecology;

/**
 * Ground taken by something other than plants (rocks, and whatever else lies on the terrain): an ecology
 * {@link Ecology#avoiding avoiding} it places no plant where it blocks. Implementations must be pure and thread-safe
 * like the terrain: the same region always gives the same answers, whichever tiles came before.
 */
public interface Obstacles {

    /** Nothing in the way. */
    Obstacles NONE = (x0, z0, x1, z1) -> (x, z, radius) -> false;

    /** The obstacles over [{@code x0}, {@code x1}) x [{@code z0}, {@code z1}), metres, asked about plants there. */
    Region over(double x0, double z0, double x1, double z1);

    /** The obstacles of a region. */
    interface Region {

        /** Whether a plant of crown radius {@code radius} metres may not stand at ({@code x}, {@code z}). */
        boolean blocks(double x, double z, double radius);
    }
}
