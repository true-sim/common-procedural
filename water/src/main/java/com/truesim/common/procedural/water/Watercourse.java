package com.truesim.common.procedural.water;

import java.util.Arrays;
import java.util.Objects;

/**
 * A mapped watercourse: a line in the terrain's frame (x east, z south, metres) from its source downstream, as a
 * survey has it (swisstopo's swissTLM3D {@code TLM_FLIESSGEWAESSER}, OpenStreetMap's waterways). Where watercourses
 * are given, the network follows them: they are burned into the terrain before the flow is routed, and their cells
 * are channels whatever their catchment.
 *
 * @param name its name (may be empty)
 * @param points x and z pairs, downstream
 * @param width its width, metres, or NaN: from its discharge
 * @param discharge its mean discharge, cubic metres a second, or NaN: from its catchment
 */
public record Watercourse(String name, double[] points, double width, double discharge) {

    public Watercourse {
        Objects.requireNonNull(name, "name");
        if (points.length < 4 || (points.length & 1) != 0) {
            throw new IllegalArgumentException("A watercourse has two points or more: " + points.length / 2);
        }
        points = points.clone();
        if (!(Double.isNaN(width) || width > 0) || !(Double.isNaN(discharge) || discharge > 0)) {
            throw new IllegalArgumentException("A width and a discharge are positive or NaN: " + width + ", "
                + discharge);
        }
    }

    /** A watercourse of the given points, its width and discharge from the terrain. */
    public static Watercourse of(String name, double... points) {
        return new Watercourse(name, points, Double.NaN, Double.NaN);
    }

    /** Its points, a copy. */
    @Override
    public double[] points() {
        return points.clone();
    }

    public int pointCount() {
        return points.length / 2;
    }

    public double x(int i) {
        return points[2 * i];
    }

    public double z(int i) {
        return points[2 * i + 1];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Watercourse w && name.equals(w.name) && Arrays.equals(points, w.points)
            && Double.compare(width, w.width) == 0 && Double.compare(discharge, w.discharge) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, Arrays.hashCode(points), width, discharge);
    }

    @Override
    public String toString() {
        return "Watercourse[" + name + ", " + pointCount() + " points]";
    }
}
