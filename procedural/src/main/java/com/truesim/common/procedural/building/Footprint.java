package com.truesim.common.procedural.building;

import com.truesim.common.math.Maths;

/**
 * Where a building stands on the ground plan and how big it is: its centre ({@code x}, {@code z}, world metres), its
 * turn about up ({@code yaw}, radians: 0 faces its front gable to +z), its {@code width} across the front (local x)
 * and {@code depth} along the ridge (local z). A building's meshes are in its own frame: x across, z along (+z its
 * front), y up from its ground floor; placed by the translation ({@code x}, {@link Building#floor()}, {@code z}) and
 * the rotation {@code (0, sin(yaw / 2), 0, cos(yaw / 2))}.
 *
 * @param x the centre's x, world
 * @param z the centre's z, world
 * @param yaw the turn about +y, radians
 * @param width across the front gable, metres
 * @param depth along the ridge, metres
 */
public record Footprint(double x, double z, double yaw, double width, double depth) {

    public Footprint {
        if (!(width >= 2.5 && depth >= 2.5 && width <= 40 && depth <= 60)) {
            throw new IllegalArgumentException("A footprint is 2.5 to 40 m wide and 2.5 to 60 m deep: " + width
                + " x " + depth);
        }
    }

    /** A footprint at the origin, unturned. */
    public static Footprint of(double width, double depth) {
        return new Footprint(0, 0, 0, width, depth);
    }

    /** The world x of the local point ({@code lx}, {@code lz}). */
    public double worldX(double lx, double lz) {
        return x + lx * Maths.cos(yaw) + lz * Maths.sin(yaw);
    }

    /** The world z of the local point ({@code lx}, {@code lz}). */
    public double worldZ(double lx, double lz) {
        return z - lx * Maths.sin(yaw) + lz * Maths.cos(yaw);
    }

    /** This footprint moved to ({@code nx}, {@code nz}) and turned to {@code nyaw}, its size kept. */
    public Footprint at(double nx, double nz, double nyaw) {
        return new Footprint(nx, nz, nyaw, width, depth);
    }
}
