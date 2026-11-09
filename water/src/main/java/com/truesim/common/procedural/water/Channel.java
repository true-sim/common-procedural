package com.truesim.common.procedural.water;

/**
 * A reach of the channel network: from a channel's head or a confluence down to the next confluence or the region's
 * edge, as points along its line, downstream, every {@link WaterParameters.Surface#spacing} metres. A reach ending at
 * a confluence shares its last point with the reach that starts there ({@link #downstream()}).
 *
 * <p>At every point: the line in the terrain's frame (x east, z south), the water's surface (never rising
 * downstream: the monotone least-squares fit to the ground along the line), the ground, the catchment and the mean
 * discharge, the width, depth and speed of the hydraulic geometry ({@link WaterParameters.Channels}), the slope, and
 * the horizontal direction of flow.
 */
public final class Channel {

    private final int id;
    private final int downstream;
    private final int order;
    private final String name;
    private final double[] x;
    private final double[] z;
    private final double[] surface;
    private final double[] ground;
    private final double[] distance;
    private final double[] area;
    private final double[] discharge;
    private final double[] width;
    private final double[] depth;
    private final double[] speed;
    private final double[] slope;
    private final double[] directionX;
    private final double[] directionZ;
    private final int[] fall;

    Channel(int id, int downstream, int order, String name, double[] x, double[] z, double[] surface, double[] ground,
        double[] distance, double[] area, double[] discharge, double[] width, double[] depth, double[] speed,
        double[] slope, double[] directionX, double[] directionZ, int[] fall) {
        this.id = id;
        this.downstream = downstream;
        this.order = order;
        this.name = name;
        this.x = x;
        this.z = z;
        this.surface = surface;
        this.ground = ground;
        this.distance = distance;
        this.area = area;
        this.discharge = discharge;
        this.width = width;
        this.depth = depth;
        this.speed = speed;
        this.slope = slope;
        this.directionX = directionX;
        this.directionZ = directionZ;
        this.fall = fall;
    }

    /** Its index in the network's channels. */
    public int id() {
        return id;
    }

    /** The channel it flows into at its last point, or -1 where it leaves the region. */
    public int downstream() {
        return downstream;
    }

    /** Its Strahler order: 1 from a head, one more where two of the same order meet. */
    public int order() {
        return order;
    }

    /** The name of the mapped watercourse it follows, or empty. */
    public String name() {
        return name;
    }

    /** How many points. */
    public int count() {
        return x.length;
    }

    public double x(int i) {
        return x[i];
    }

    public double z(int i) {
        return z[i];
    }

    /** The water's surface, metres. */
    public double surface(int i) {
        return surface[i];
    }

    /** The ground's height on the line, metres (the terrain's, before carving). */
    public double ground(int i) {
        return ground[i];
    }

    /** The bed's lowest point: the surface less the depth. */
    public double bed(int i) {
        return surface[i] - depth[i];
    }

    /** Metres along the line from its first point. */
    public double distance(int i) {
        return distance[i];
    }

    /** The catchment, square metres. */
    public double area(int i) {
        return area[i];
    }

    /** The mean discharge, cubic metres a second. */
    public double discharge(int i) {
        return discharge[i];
    }

    /** The width at the surface, metres. */
    public double width(int i) {
        return width[i];
    }

    /** The mean depth, metres. */
    public double depth(int i) {
        return depth[i];
    }

    /** The mean speed, metres a second. */
    public double speed(int i) {
        return speed[i];
    }

    /** The surface's slope, drop per run (at least the minimum slope). */
    public double slope(int i) {
        return slope[i];
    }

    /** The direction of flow, east part of a horizontal unit vector. */
    public double directionX(int i) {
        return directionX[i];
    }

    /** The direction of flow, south part. */
    public double directionZ(int i) {
        return directionZ[i];
    }

    /** The Froude number {@code v / sqrt(g d)}: above 1 the flow is supercritical, white water. */
    public double froude(int i) {
        return speed[i] / Math.sqrt(9.81 * depth[i]);
    }

    /** The index of the free waterfall the segment from point {@code i} to the next falls down, or -1. */
    public int fall(int i) {
        return fall[i];
    }

    /** Marks the segment from point {@code i} as falling down free fall {@code id}. */
    void markFall(int i, int id) {
        fall[i] = id;
    }
}
