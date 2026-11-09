package com.truesim.common.procedural.building;

import com.truesim.annotations.AllowAllocation;

/**
 * The ground's height at a point of the world's ground plan, metres: what a building stands on. Its foundation steps
 * down the slope to meet it, its doors' steps reach it, its woodpile stands on it.
 */
@FunctionalInterface
public interface GroundHeight {

    /** The height at ({@code x}, {@code z}). */
    double height(double x, double z);

    /** Level ground at {@code height}. */
    @AllowAllocation("a ground, at set-up")
    static GroundHeight flat(double height) {
        return (x, z) -> height;
    }

    /** A plane through {@code height} at the origin, rising {@code gradeX} per metre of x and {@code gradeZ} of z. */
    @AllowAllocation("a ground, at set-up")
    static GroundHeight plane(double height, double gradeX, double gradeZ) {
        return (x, z) -> height + gradeX * x + gradeZ * z;
    }
}
