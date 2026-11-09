package com.truesim.common.procedural.rock;

/**
 * What grows on a rock where it lies, and how it lies: moss and lichen, the way it faces and how deep it is sunk. A
 * rock's vertex color carries them as a tint of its material's albedo ({@link Rock}), so one material draws bare,
 * mossy and lichened stone.
 *
 * <ul>
 *   <li><b>Moss</b> grows where it stays damp: on the top and the north side (in the northern hemisphere the shaded
 *       side), and up from the ground in a band (soil, splash and the ground's humidity), in patches.</li>
 *   <li><b>Lichen</b> crusts grow where moss does not, on lit, exposed faces: round blotches, pale grey and some
 *       orange (Xanthoria and Caloplaca on limestone, nitrogen-loving where birds perch on tops).</li>
 *   <li><b>Burial</b> sinks the rock: its lower part is under the ground and cut flat; a soil stain rises a little
 *       above the ground.</li>
 * </ul>
 *
 * @param moss how mossy, 0 (bare) to 1 (damp, old, shaded)
 * @param lichen how lichened, 0 to 1 (old, exposed)
 * @param yaw the turn the rock is placed with in the world, radians about +y (counter-clockwise from above): the
 *     side that ends up facing north (-z) gets the moss
 * @param buried how much of the rock's height lies under the ground, 0 to 1, or NaN for its parameters'
 */
public record RockCover(double moss, double lichen, double yaw, double buried) {

    /** Bare stone, its parameters' burial: the vertex color white. */
    public static final RockCover BARE = new RockCover(0, 0, 0, Double.NaN);
    /** Some moss and lichen, as on a boulder that has lain a few decades at the forest's edge. */
    public static final RockCover WEATHERED = new RockCover(0.5, 0.4, 0, Double.NaN);

    public RockCover {
        if (!(moss >= 0 && moss <= 1 && lichen >= 0 && lichen <= 1)) {
            throw new IllegalArgumentException("Moss and lichen are 0 to 1: " + moss + ", " + lichen);
        }
        if (!Double.isNaN(buried) && !(buried >= 0 && buried < 1)) {
            throw new IllegalArgumentException("Buried is 0 to 1: " + buried);
        }
    }

    /** This cover turned by {@code newYaw}. */
    public RockCover withYaw(double newYaw) {
        return new RockCover(moss, lichen, newYaw, buried);
    }

    /** This cover sunk by {@code newBuried} of the height. */
    public RockCover withBuried(double newBuried) {
        return new RockCover(moss, lichen, yaw, newBuried);
    }

    /** Whether it tints at all. */
    public boolean bare() {
        return moss == 0 && lichen == 0;
    }

    // Tints of a rock material's albedo (linear, about 0.45 for limestone): weathered stone a little darker and
    // greyer than fresh, moss (albedo about 0.05 to 0.1, green) and lichen (pale grey, orange) relative to it.

    /** Weathered stone's tint: fresh fractures are 1. */
    static final double[] WEATHERED_TINT = {0.86, 0.86, 0.84};
    /** Moss: Hypnum, Homalothecium on limestone, about (0.06, 0.09, 0.03) in albedo. */
    static final double[] MOSS_TINT = {0.16, 0.23, 0.07};
    /** Pale crustose lichen. */
    static final double[] LICHEN_TINT = {0.97, 0.96, 0.9};
    /** Orange lichen (Xanthoria). */
    static final double[] ORANGE_TINT = {0.95, 0.55, 0.18};
    /** Soil stain at the ground. */
    static final double[] SOIL_TINT = {0.5, 0.44, 0.36};
}
