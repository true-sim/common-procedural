package com.truesim.common.procedural.water;

/**
 * A stone in a channel's bed or on its bank, for a rock model ({@code common-procedural}'s {@code Rocks}: a
 * {@code RockParams} preset by name and a seed) scaled to its size.
 *
 * @param x east, metres: where its base's middle lies
 * @param y the bed's height there, metres; the stone is set into it by a third of its size
 * @param z south, metres
 * @param size its size, metres (the preset's size scaled to it)
 * @param yaw its turn about the vertical, radians
 * @param preset the rock preset: {@code boulder} (1 m and larger), {@code block}, {@code scree} (below 0.4 m)
 * @param seed the rock's seed
 * @param channel the channel it lies in
 * @param emergent whether it stands out of the water (the flow breaks white round it)
 */
public record BedStone(double x, double y, double z, double size, double yaw, String preset, long seed, int channel,
    boolean emergent) {

    /** The preset for a stone of {@code size} metres. */
    static String preset(double size) {
        return size >= 1 ? "boulder" : size >= 0.4 ? "block" : "scree";
    }
}
