package com.truesim.common.procedural.ecology.rocks;

import com.truesim.common.procedural.rock.RockCover;
import com.truesim.common.procedural.rock.RockParams;

/**
 * One of the models a rock field's rocks share: a preset's shape from a seed, with its cover (moss, lichen, heading,
 * burial) baked in. Rocks of one variant draw one model, scaled and placed by their instance. A value: equal variants
 * are one model ({@code RockAssets.recipe} makes its recipe).
 *
 * @param params the preset
 * @param seed the shape's seed
 * @param cover its moss, lichen, the heading it is placed with and its burial
 */
public record RockVariant(RockParams params, long seed, RockCover cover) {
}
