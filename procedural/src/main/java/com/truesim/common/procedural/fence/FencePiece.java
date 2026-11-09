package com.truesim.common.procedural.fence;

/**
 * One piece of a generated fence, where it is in the ground's frame (not the mesh's: add nothing, the ground's
 * {@code (x, z)} are these) and which vertices of the most detailed level are its. For placing things by the fence,
 * physics, and tests.
 *
 * @param role what it is
 * @param part its submesh: {@link Fence#WOOD}, {@link Fence#STONE} or {@link Fence#METAL}
 * @param path its centre line, x, y, z triples from its start to its end (a post's foot, under the ground, to its top;
 *     a stone's bottom centre to its top centre; a lashing's loop, closed)
 * @param width its width (diameter of round pieces; a stone's length along the wall)
 * @param depth its depth across (a stone's thickness through the wall)
 * @param firstVertex its first vertex in level 0
 * @param endVertex the end (exclusive) of its vertices in level 0
 */
public record FencePiece(Role role, int part, double[] path, double width, double depth, int firstVertex,
    int endVertex) {

    /** What a piece is. */
    public enum Role {
        /** A post in the ground. */
        POST,
        /** A rail between posts (sawn, round or split). */
        RAIL,
        /** A picket. */
        PICKET,
        /** A board. */
        BOARD,
        /** A stake: wattle's, or a pair's at a split-rail crossing. */
        STAKE,
        /** A slanted pole of a Stangenzaun. */
        POLE,
        /** A woven rod. */
        ROD,
        /** A withy ring lashing stakes or posts together. */
        LASHING,
        /** A strand of wire between posts. */
        WIRE,
        /** A stone of a wall (or one fallen from it). */
        STONE,
        /** A part of a gate's leaf. */
        GATE
    }

    public FencePiece {
        path = path.clone();
    }

    @Override
    public double[] path() {
        return path.clone();
    }

    /** Its number of path points. */
    public int points() {
        return path.length / 3;
    }

    /** Coordinate {@code axis} (0 x, 1 y, 2 z) of path point {@code i}. */
    public double at(int i, int axis) {
        return path[3 * i + axis];
    }

    /** Coordinate {@code axis} of its last path point. */
    public double end(int axis) {
        return path[path.length - 3 + axis];
    }
}
