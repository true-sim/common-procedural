package com.truesim.common.procedural.building;

import com.truesim.common.math.Vec3;

/**
 * An opening cut through a wall: a window, a door, a hay door, a slit, a belfry's arch. In the building's frame. For a
 * renderer: lit windows at night, portals for light, where to look in.
 *
 * @param kind what it is
 * @param center the middle of the opening on the wall's outer face
 * @param outward the wall's outward normal (horizontal, unit)
 * @param width across, metres
 * @param height up, metres
 * @param depth the wall's thickness: the opening runs from {@code center} to {@code center - outward * depth}
 * @param room the room behind it ({@link Fittings#rooms()} index), or -1
 */
public record Opening(Kind kind, Vec3 center, Vec3 outward, double width, double height, double depth, int room) {

    /** What an opening is. */
    public enum Kind {
        /** A glazed window: frame, mullions and a pane of glass in it. */
        WINDOW,
        /** A door, its leaf closed in it (open on the café's tour door: see the anchor). */
        DOOR,
        /** A barn's hay door, closed. */
        HAY_DOOR,
        /** An open slit for air (barns). */
        SLIT,
        /** A belfry's opening, with louvres. */
        BELFRY
    }

    /** The unit along the wall, to the right seen from outside. */
    public Vec3 along() {
        return new Vec3(outward.z(), 0, -outward.x());
    }
}
