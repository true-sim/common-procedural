package com.truesim.common.procedural.settlement;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.ecology.Site;
import java.util.stream.IntStream;

/**
 * The terrain over the plan's area at the routing grid's nodes: heights, slopes, and what the ecology's hydrology and
 * land use say of each (flood-prone ground, open water, wetland, meadow). Sampled once, rows on all cores; the
 * ecology's fields are pure functions of the point, so the result does not depend on the order.
 */
final class Field {

    final int n;
    final double cell;
    final double x0;
    final double z0;
    final EcologyTerrain terrain;
    /** Heights at the nodes. */
    final float[] height;
    /** Slopes at the nodes, degrees. */
    final float[] slope;
    /** Slopes at the nodes, rise over run. */
    final float[] gradient;
    /** Open water, 0 to 1. */
    final float[] water;
    /** Wetland, 0 to 1. */
    final float[] wetland;
    /** Land kept open as meadow, 0 to 1. */
    final float[] meadow;
    /** Flood-prone ground: open water, wetland, or low above its drainage. */
    final boolean[] flood;

    Field(Ecology ecology, double originX, double originZ, SettlementParameters parameters) {
        SettlementParameters.Grid grid = parameters.grid();
        SettlementParameters.Siting siting = parameters.siting();
        this.cell = grid.cell();
        this.n = (int) Math.round(grid.extent() / cell) + 1;
        this.x0 = originX - (n - 1) * cell / 2;
        this.z0 = originZ - (n - 1) * cell / 2;
        this.terrain = ecology.terrain();
        height = new float[n * n];
        slope = new float[n * n];
        gradient = new float[n * n];
        water = new float[n * n];
        wetland = new float[n * n];
        meadow = new float[n * n];
        flood = new boolean[n * n];
        IntStream.range(0, n).forEach(j -> {
            Site site = new Site();
            for (int i = 0; i < n; i++) {
                int a = j * n + i;
                double x = x(i);
                double z = z(j);
                height[a] = (float) terrain.height(x, z);
                ecology.conditions(x, z, site);
                water[a] = (float) site.water;
                wetland[a] = (float) site.wetland;
                meadow[a] = (float) site.meadow;
                double rises = siting.floodHeight() + siting.floodPerRoot() * Math.sqrt(site.streamArea / 1e6);
                flood[a] = site.water > siting.water() || site.wetland > siting.wetland() || site.hand < rises;
            }
        });
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int il = Math.max(i - 1, 0);
                int ir = Math.min(i + 1, n - 1);
                int jl = Math.max(j - 1, 0);
                int jr = Math.min(j + 1, n - 1);
                double gx = (height[j * n + ir] - height[j * n + il]) / ((ir - il) * cell);
                double gz = (height[jr * n + i] - height[jl * n + i]) / ((jr - jl) * cell);
                gradient[j * n + i] = (float) Math.sqrt(gx * gx + gz * gz);
                slope[j * n + i] = (float) Maths.toDegrees(Maths.atan(gradient[j * n + i]));
            }
        }
    }

    double x(int i) {
        return x0 + i * cell;
    }

    double z(int j) {
        return z0 + j * cell;
    }

    /** The node nearest ({@code x}, {@code z}), or -1 outside the grid. */
    int node(double x, double z) {
        int i = (int) Math.round((x - x0) / cell);
        int j = (int) Math.round((z - z0) / cell);
        return i < 0 || j < 0 || i >= n || j >= n ? -1 : j * n + i;
    }

    /** Whether ({@code x}, {@code z}) is at least {@code margin} metres inside the grid. */
    boolean contains(double x, double z, double margin) {
        return x >= x0 + margin && z >= z0 + margin && x <= x0 + (n - 1) * cell - margin
            && z <= z0 + (n - 1) * cell - margin;
    }

    /** The slope at the node nearest ({@code x}, {@code z}), degrees (90 outside). */
    double slopeAt(double x, double z) {
        int a = node(x, z);
        return a < 0 ? 90 : slope[a];
    }

    /** Whether the ground at the node nearest ({@code x}, {@code z}) floods (true outside). */
    boolean floodAt(double x, double z) {
        int a = node(x, z);
        return a < 0 || flood[a];
    }

    /** The meadow share at the node nearest ({@code x}, {@code z}) (0 outside). */
    double meadowAt(double x, double z) {
        int a = node(x, z);
        return a < 0 ? 0 : meadow[a];
    }

    /** The terrain's height at ({@code x}, {@code z}). */
    double heightAt(double x, double z) {
        return terrain.height(x, z);
    }

    /**
     * The way a building at ({@code x}, {@code z}) faces into {@code out} (unit x, z): the way down the slope over
     * {@code radius} metres blended with the south by {@code sun}; the south on level ground.
     */
    void facing(double x, double z, double radius, double sun, double[] out) {
        double gx = (terrain.height(x + radius, z) - terrain.height(x - radius, z)) / (2 * radius);
        double gz = (terrain.height(x, z + radius) - terrain.height(x, z - radius)) / (2 * radius);
        double g = Math.sqrt(gx * gx + gz * gz);
        double dx = 0;
        double dz = 0;
        if (g > 1e-3) {
            dx = -gx / g;
            dz = -gz / g;
        }
        // the slope's pull fades on level ground, where the south (the sun) decides
        double pull = (1 - sun) * Math.min(1, g / 0.05);
        double fx = pull * dx;
        double fz = pull * dz + sun;
        double l = Math.sqrt(fx * fx + fz * fz);
        if (l < 1e-9) {
            out[0] = 0;
            out[1] = 1;
        } else {
            out[0] = fx / l;
            out[1] = fz / l;
        }
    }
}
