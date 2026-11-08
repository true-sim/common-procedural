package com.truesim.common.procedural.flora;

/**
 * A fruit's shape, meshed by {@link FloraWriter#fruit}: a spheroid along its axis (from the stalk to the apex), with
 * a calyx crown at the apex, dimples at the ends, or the overlapping scales of a cone. Metres.
 *
 * @param form the kind of fruit
 * @param radius the equatorial radius at full size
 * @param elongation the length along the axis over the width: 1 round, above 1 long (hips, cones), below flat
 * @param sizeVariation the size's random variation from fruit to fruit, as a fraction
 * @param calyx the calyx crown at the apex, as a fraction of the radius (0 none): blueberries' and hips' crowns
 * @param scales a cone's scales around (0 for fruits): their spiral reads in the surface
 * @param dimple how deep the ends are drawn in, as a fraction of the radius (apples)
 * @param hang 0 to 1: how far a ripe fruit hangs down from its stalk under its weight
 */
public record FruitShape(Form form, double radius, double elongation, double sizeVariation, double calyx, int scales,
    double dimple, double hang) {

    /** Kinds of fruit. */
    public enum Form {
        /** A berry, a hip, a drupe or a capsule: a smooth spheroid. */
        BERRY,
        /** An apple: an oblate spheroid drawn in at both ends. */
        POME,
        /** A conifer's cone: a long spheroid of overlapping scales. */
        CONE,
        /** A grass's spike-like panicle: a slender spindle of spikelets. */
        SPIKE
    }

    public FruitShape {
        if (radius <= 0 || elongation <= 0 || sizeVariation < 0 || sizeVariation >= 1 || calyx < 0 || scales < 0
            || dimple < 0 || dimple >= 1 || hang < 0 || hang > 1) {
            throw new IllegalArgumentException("Not a fruit: radius " + radius + ", elongation " + elongation);
        }
    }

    /** Half its length along the axis at full size. */
    public double halfLength() {
        return radius * elongation;
    }
}
