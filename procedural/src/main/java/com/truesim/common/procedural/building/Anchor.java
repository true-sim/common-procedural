package com.truesim.common.procedural.building;

import com.truesim.common.math.Vec3;

/**
 * A place on or in a building for something the consumer adds: a light, an effect, a prop. In the building's frame
 * (x across, z along, +z its front, y up from its ground floor).
 *
 * @param kind what goes there
 * @param position where, metres
 * @param yaw which way it faces about up, radians: 0 toward +z, as a footprint's yaw (a chair's or bench's front, a
 *     door's outside, a lantern's wall's outside)
 * @param room the room it is in ({@link Fittings#rooms()} index), or -1 outside
 */
public record Anchor(Kind kind, Vec3 position, double yaw, int room) {

    /** What an anchor is for. */
    public enum Kind {
        /** The hearth's fire: a fire effect and its light. */
        FIRE,
        /** The chimney's top: smoke. */
        CHIMNEY,
        /** A ceiling lamp: under the ceiling, in the middle of its room. */
        CEILING_LAMP,
        /** A door's threshold, outside, facing out. */
        DOOR,
        /** A lantern on the wall beside a door, at head height, facing out. */
        LANTERN,
        /** A bench: outside by the door, or a corner bench in a parlour. */
        BENCH,
        /** A table in a room. */
        TABLE,
        /** A chair at a table, facing it. */
        CHAIR,
        /** A cabinet or dresser against a wall, facing into the room. */
        CABINET,
        /** A bed, against a wall. */
        BED,
        /** The inn's bar counter. */
        BAR,
        /** The inn's hanging sign, on its bracket. */
        SIGN,
        /** The board for a name painted over the inn's door: its centre, facing out. */
        NAME_BOARD,
        /** A table outside the inn. */
        TERRACE_TABLE,
        /** A woodpile's middle, facing out. */
        WOODPILE,
        /** The church's bell, in the belfry. */
        BELL,
        /** A clock face's centre, facing out. */
        CLOCK,
        /** The church's altar. */
        ALTAR,
        /** A pew, facing the altar. */
        PEW,
        /** A door into the foundation (a cellar's or stable's) where the ground falls away, facing out. */
        CELLAR_DOOR
    }
}
