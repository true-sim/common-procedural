package com.truesim.common.procedural.water;

/**
 * The rectangle of the terrain's frame (x east, z south, metres) a network is built over. Catchments are cut at its
 * edges: it should hold the catchments of the rivers that matter.
 *
 * @param x0 the west edge
 * @param z0 the north edge
 * @param x1 the east edge
 * @param z1 the south edge
 */
public record WaterRegion(double x0, double z0, double x1, double z1) {

    public WaterRegion {
        if (!(x1 > x0) || !(z1 > z0)) {
            throw new IllegalArgumentException("A region has an extent: " + x0 + ", " + z0 + ", " + x1 + ", " + z1);
        }
    }

    /** The square of {@code size} metres a side centred on ({@code x}, {@code z}). */
    public static WaterRegion around(double x, double z, double size) {
        return new WaterRegion(x - size / 2, z - size / 2, x + size / 2, z + size / 2);
    }

    public double width() {
        return x1 - x0;
    }

    public double depth() {
        return z1 - z0;
    }

    /** Whether ({@code x}, {@code z}) lies in it. */
    public boolean contains(double x, double z) {
        return x >= x0 && x <= x1 && z >= z0 && z <= z1;
    }
}
