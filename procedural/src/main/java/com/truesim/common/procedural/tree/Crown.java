package com.truesim.common.procedural.tree;

/**
 * The crown of a tree grown by space colonization (Runions et al. 2007): its envelope, filled with attraction points
 * that the branches grow toward, and the growth's distances.
 *
 * @param shape the envelope's shape
 * @param radius the envelope's horizontal radius as a fraction of the tree's height
 * @param points attraction points in the envelope: more give denser, finer branching
 * @param segment metres a branch grows per step (the node spacing)
 * @param influence how far a point attracts nodes, in segments
 * @param kill how near a node must come to a point to use it up, in segments
 * @param tropism how much each step bends toward the sky (positive) or the ground (negative), against the points'
 *     pull of 1
 * @param iterations at most this many growth steps
 */
public record Crown(Shape shape, double radius, int points, double segment, double influence, double kill,
    double tropism, int iterations) {

    /** Envelopes, between the crown's base (y 0) and top (y 1), x and z within the radius (in [-1, 1]). */
    public enum Shape {
        /** An ellipsoid: beech, oak. */
        ELLIPSOID,
        /** An egg, widest low: birch, young broadleaves. */
        OVOID,
        /** A dome on the ground: shrubs. */
        HEMISPHERE;

        /** Whether (x, y, z), x and z in [-1, 1], y in [0, 1], lies inside. */
        public boolean contains(double x, double y, double z) {
            double r2 = x * x + z * z;
            return switch (this) {
                case ELLIPSOID -> r2 + (2 * y - 1) * (2 * y - 1) <= 1;
                case OVOID -> {
                    // widest at 35 %, narrowing to a point at the top
                    double width = y < 0.35 ? Math.sqrt(1 - (0.35 - y) * (0.35 - y) / (0.35 * 0.35))
                        : Math.sqrt(1 - (y - 0.35) * (y - 0.35) / (0.65 * 0.65));
                    yield r2 <= width * width;
                }
                case HEMISPHERE -> r2 + y * y <= 1;
            };
        }
    }
}
