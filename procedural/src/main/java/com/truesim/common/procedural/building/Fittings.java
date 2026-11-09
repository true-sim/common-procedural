package com.truesim.common.procedural.building;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything about a building that is not its mesh, as data in its frame: anchors for lights, effects and props
 * (fire, chimney, ceiling lamps, doors, lanterns, tables, the bell, ...), its rooms (indoor zones) and the openings
 * through its walls.
 *
 * @param anchors the anchors
 * @param rooms the rooms, ground floor first
 * @param openings the openings through the outer walls
 */
public record Fittings(List<Anchor> anchors, List<Room> rooms, List<Opening> openings) {

    public Fittings {
        anchors = List.copyOf(anchors);
        rooms = List.copyOf(rooms);
        openings = List.copyOf(openings);
    }

    /** The first anchor of {@code kind}, or null. */
    public Anchor first(Anchor.Kind kind) {
        for (Anchor anchor : anchors) {
            if (anchor.kind() == kind) {
                return anchor;
            }
        }
        return null;
    }

    /** Every anchor of {@code kind}. */
    public List<Anchor> all(Anchor.Kind kind) {
        List<Anchor> out = new ArrayList<>();
        for (Anchor anchor : anchors) {
            if (anchor.kind() == kind) {
                out.add(anchor);
            }
        }
        return out;
    }

    /** Every opening of {@code kind}. */
    public List<Opening> openings(Opening.Kind kind) {
        List<Opening> out = new ArrayList<>();
        for (Opening opening : openings) {
            if (opening.kind() == kind) {
                out.add(opening);
            }
        }
        return out;
    }
}
