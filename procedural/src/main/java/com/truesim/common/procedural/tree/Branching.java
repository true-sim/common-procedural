package com.truesim.common.procedural.tree;

/**
 * How branches of one level grow off their parents, in the parametric method (after Weber and Penn 1995): where they
 * start, how many, at which angles, how long, and how they curve.
 *
 * @param start where along the parent children begin, as a fraction of its length (for the trunk's children: of
 *     the tree's height, the crown's base)
 * @param end where along the parent they stop, as a fraction of its length
 * @param spacing metres between child positions along the parent (whorls, or single children)
 * @param perPosition children at each position: a whorl (4-6 for spruce and fir), 1 for alternate or spiral
 * @param rotate degrees each position turns from the previous about the parent: 137.5 for a spiral, 180 alternate;
 *     a whorl's children are spread evenly besides
 * @param downAngle degrees between a child and its parent's direction where it starts
 * @param downAngleVariation degrees of random variation of the down angle
 * @param length a child's length as a fraction of its parent's (for the trunk's children: of the tree's height),
 *     before the shape
 * @param lengthVariation fraction of random variation of the length
 * @param shape how lengths vary along the parent
 * @param curve degrees a child turns up toward the sky over its length (negative: down)
 * @param curveVariation degrees of random turning per metre, either way
 * @param droop how far the tip sinks under its own weight, as a fraction of the length, growing with the square of
 *     the distance from the base
 * @param planar 0 to 1: how much children keep to the sides (the horizontal plane through the parent), as fir and
 *     spruce shoots do; 0 spreads them all around
 * @param segment metres between the nodes of a child
 */
public record Branching(double start, double end, double spacing, int perPosition, double rotate, double downAngle,
    double downAngleVariation, double length, double lengthVariation, Shape shape, double curve,
    double curveVariation, double droop, double planar, double segment) {

    /** How children's lengths vary along their parent: {@code t} is 0 where children start and 1 where they end. */
    public enum Shape {
        /** Longest at the bottom, a fifth at the top: spruce and fir. */
        CONICAL,
        /** Longest in the middle: a rounded crown. */
        SPHERICAL,
        /** Longest at the bottom, rounding off to the top: a broad dome, as old pines have. */
        HEMISPHERICAL,
        /** All alike. */
        CYLINDRICAL,
        /** Longest at the bottom, half at the top. */
        TAPERED_CYLINDRICAL,
        /** Longest at 70 %: a flame. */
        FLAME;

        /** The length factor at {@code t} in [0, 1]. */
        public double at(double t) {
            double clamped = Math.clamp(t, 0, 1);
            return switch (this) {
                case CONICAL -> 1 - 0.8 * clamped;
                case SPHERICAL -> 0.2 + 0.8 * com.truesim.common.math.Maths.sin(Math.PI * clamped);
                case HEMISPHERICAL -> 0.2 + 0.8 * com.truesim.common.math.Maths.cos(0.5 * Math.PI * clamped);
                case CYLINDRICAL -> 1;
                case TAPERED_CYLINDRICAL -> 1 - 0.5 * clamped;
                case FLAME -> clamped <= 0.7 ? 0.3 + 0.7 * clamped / 0.7 : 0.3 + 0.7 * (1 - clamped) / 0.3;
            };
        }
    }
}
