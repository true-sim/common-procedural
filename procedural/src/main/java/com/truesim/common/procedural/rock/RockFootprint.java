package com.truesim.common.procedural.rock;

/**
 * Where a rock meets the ground and how far it reaches, in its mesh space (metres): what keeps plants off it and
 * clears the grass under it.
 *
 * @param halfX the half extent of its contact with the ground along x (the cut at y 0)
 * @param halfZ along z
 * @param reach the farthest any part of it lies from the y axis
 * @param height its top above the ground
 */
public record RockFootprint(double halfX, double halfZ, double reach, double height) {

    /**
     * Whether ({@code x}, {@code z}) (mesh space) lies within the contact ellipse grown by {@code margin} metres.
     */
    public boolean covers(double x, double z, double margin) {
        double ax = halfX + margin;
        double az = halfZ + margin;
        if (ax <= 0 || az <= 0) {
            return false;
        }
        double u = x / ax;
        double v = z / az;
        return u * u + v * v <= 1;
    }
}
