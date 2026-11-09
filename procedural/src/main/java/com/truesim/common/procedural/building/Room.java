package com.truesim.common.procedural.building;

import com.truesim.common.math.geometry.Aabb;

/**
 * A room: the box between its inner walls, floor and ceiling, in the building's frame. An indoor zone (no outdoor fog,
 * no rain, dimmer sky light) for a renderer, and what anchors and openings belong to.
 *
 * @param name what it is: parlour, kitchen, chamber, nave, ...
 * @param floor the storey it is on, 0 the ground floor
 * @param bounds its box
 */
public record Room(String name, int floor, Aabb bounds) {
}
