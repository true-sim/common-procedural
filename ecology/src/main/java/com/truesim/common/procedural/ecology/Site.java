package com.truesim.common.procedural.ecology;

/**
 * The conditions at one location, everything the species' envelopes read: filled by {@link Ecology#conditions}, reused
 * between calls.
 */
public final class Site {

    /** Where: x east, z south, metres. */
    public double x;
    /** Where: z south, metres. */
    public double z;
    /** Above sea level, metres. */
    public double altitude;
    /** Degrees from level. */
    public double slope;
    /** The direction the slope faces, degrees clockwise from north (180 south). */
    public double aspect;
    /** Growing-season sunshine relative to level ground's. */
    public double insolation;
    /** The growing season's mean temperature, degrees Celsius. */
    public double temperature;
    /** Days of snow cover a year. */
    public double snowDays;
    /** Topographic position index, metres (ridges above 0, hollows below). */
    public double position;
    /** Wind exposure, 0 sheltered to 1 on ridges. */
    public double exposure;
    /** Topographic wetness index. */
    public double wetness;
    /** Height above the nearest drainage, metres. */
    public double hand;
    /** The distance to the nearest stream, metres. */
    public double streamDistance;
    /** The nearest stream's catchment area, square metres. */
    public double streamArea;
    /** The height above the nearest stream, metres. */
    public double streamHeight;
    /** The distance to the nearest river, metres. */
    public double riverDistance;
    /** The nearest river's catchment area, square metres. */
    public double riverArea;
    /** Moisture on Ellenberg's scale (1 very dry to 12 under water). */
    public double moisture;
    /**
     * The soil's depth, metres, estimated: about a metre on gentle soil, thinning with the slope to nothing at the
     * steepest ground trees stand on, and with the ground's share of rock and scree.
     */
    public double soilDepth;
    /** Ground fractions ({@link Ground}): rock, scree, soil, snow. */
    public final double[] ground = new double[Ground.COUNT];
    /** Open water, 0 to 1: rivers and lakes. */
    public double water;
    /** Wetland, 0 to 1: saturated, flat ground. */
    public double wetland;
    /** Riparian ground along streams, 0 to 1. */
    public double riparian;
    /** How much the land is kept open as meadow, 0 to 1. */
    public double meadow;
    /** The vegetation belt. */
    public Belt belt = Belt.COLLINE;
}
