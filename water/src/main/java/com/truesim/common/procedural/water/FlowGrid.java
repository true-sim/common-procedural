package com.truesim.common.procedural.water;

import com.truesim.common.procedural.ecology.EcologyTerrain;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The terrain's flow over a region, on a grid of square cells: heights sampled (each cell's lowest of its subsamples,
 * mapped watercourses burned in), depressions filled by Priority-Flood (Barnes, Lehman and Mulla 2014: each cell
 * raised to just above the one it was flooded from, so every cell drains to the region's edge), single flow
 * directions by steepest descent on the filled heights (O'Callaghan and Mark 1984) and the catchment of every cell.
 * Cell {@code (i, j)} is index {@code j * nx + i}, its centre at {@code x0 + (i + 0.5) cell}, {@code z0 + (j + 0.5)
 * cell}.
 */
final class FlowGrid {

    static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
    static final int[] DZ = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final double DIAGONAL = Math.sqrt(2);

    final double x0;
    final double z0;
    final double cell;
    final int nx;
    final int nz;
    /** The heights flow is routed over: sampled, mapped watercourses burned in. */
    final double[] height;
    /** The heights after filling. */
    final double[] filled;
    /** Each cell's receiver, -1 for an outlet. */
    final int[] receiver;
    /** The cells in drainage order, outlets first: every cell after its receiver. */
    final int[] order;
    /** Each cell's catchment, square metres. */
    final double[] area;
    /** The mapped watercourse a cell lies on, or -1. */
    final int[] course;

    private FlowGrid(WaterRegion region, double cell) {
        this.x0 = region.x0();
        this.z0 = region.z0();
        this.cell = cell;
        this.nx = Math.max(2, (int) Math.ceil(region.width() / cell));
        this.nz = Math.max(2, (int) Math.ceil(region.depth() / cell));
        int count = nx * nz;
        this.height = new double[count];
        this.filled = new double[count];
        this.receiver = new int[count];
        this.order = new int[count];
        this.area = new double[count];
        this.course = new int[count];
    }

    /** Routes the flow over {@code terrain} in {@code region}, following {@code courses}. */
    static FlowGrid build(EcologyTerrain terrain, WaterRegion region, WaterParameters.Flow flow,
        List<Watercourse> courses) {
        FlowGrid grid = new FlowGrid(region, flow.cell());
        grid.sample(terrain, flow.subsamples());
        grid.burn(courses, flow.burnDepth());
        grid.route();
        return grid;
    }

    int count() {
        return nx * nz;
    }

    double x(int a) {
        return x0 + (a % nx + 0.5) * cell;
    }

    double z(int a) {
        return z0 + (a / nx + 0.5) * cell;
    }

    /** The cell holding ({@code x}, {@code z}), or -1 outside. */
    int cellAt(double x, double z) {
        int i = (int) Math.floor((x - x0) / cell);
        int j = (int) Math.floor((z - z0) / cell);
        return i < 0 || j < 0 || i >= nx || j >= nz ? -1 : j * nx + i;
    }

    /** Neighbour {@code d} of cell {@code a} (0 east, then clockwise seen from above with z south), or -1. */
    int neighbour(int a, int d) {
        int i = a % nx + DX[d];
        int j = a / nx + DZ[d];
        return i < 0 || j < 0 || i >= nx || j >= nz ? -1 : j * nx + i;
    }

    private void sample(EcologyTerrain terrain, int subsamples) {
        double step = cell / subsamples;
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                double lowest = Double.POSITIVE_INFINITY;
                for (int sj = 0; sj < subsamples; sj++) {
                    for (int si = 0; si < subsamples; si++) {
                        lowest = Math.min(lowest, terrain.height(x0 + i * cell + (si + 0.5) * step,
                            z0 + j * cell + (sj + 0.5) * step));
                    }
                }
                height[j * nx + i] = lowest;
            }
        });
    }

    /** Lowers the cells along {@code courses} by {@code depth}, and marks them. */
    private void burn(List<Watercourse> courses, double depth) {
        Arrays.fill(course, -1);
        for (int c = 0; c < courses.size(); c++) {
            Watercourse w = courses.get(c);
            for (int p = 0; p + 1 < w.pointCount(); p++) {
                double ax = w.x(p);
                double az = w.z(p);
                double bx = w.x(p + 1);
                double bz = w.z(p + 1);
                double length = Math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
                int steps = Math.max(1, (int) Math.ceil(length / (0.25 * cell)));
                for (int s = 0; s <= steps; s++) {
                    double t = (double) s / steps;
                    int a = cellAt(ax + t * (bx - ax), az + t * (bz - az));
                    if (a >= 0 && course[a] < 0) {
                        course[a] = c;
                        height[a] -= depth;
                    }
                }
            }
        }
    }

    private void route() {
        int count = count();
        boolean[] seen = new boolean[count];
        Heap heap = new Heap(count, filled);
        for (int a = 0; a < count; a++) {
            int i = a % nx;
            int j = a / nx;
            if (i == 0 || j == 0 || i == nx - 1 || j == nz - 1) {
                filled[a] = height[a];
                receiver[a] = -1;
                seen[a] = true;
                heap.push(a);
            }
        }
        int popped = 0;
        while (!heap.isEmpty()) {
            int a = heap.pop();
            order[popped++] = a;
            for (int d = 0; d < 8; d++) {
                int b = neighbour(a, d);
                if (b >= 0 && !seen[b]) {
                    seen[b] = true;
                    filled[b] = Math.max(height[b], Math.nextUp(filled[a]));
                    receiver[b] = a;
                    heap.push(b);
                }
            }
        }
        // steepest descent on the filled heights; the flood's receiver where the fill left a cell level
        for (int k = 0; k < count; k++) {
            int a = order[k];
            int best = receiver[a];
            double steepest = 0;
            for (int d = 0; d < 8; d++) {
                int b = neighbour(a, d);
                if (b >= 0 && filled[b] < filled[a]) {
                    double g = (filled[a] - filled[b]) / ((d & 1) == 1 ? DIAGONAL : 1);
                    if (g > steepest) {
                        steepest = g;
                        best = b;
                    }
                }
            }
            receiver[a] = best;
        }
        Arrays.fill(area, cell * cell);
        for (int k = count - 1; k >= 0; k--) {
            int a = order[k];
            if (receiver[a] >= 0) {
                area[receiver[a]] += area[a];
            }
        }
    }

    /** A binary min-heap of cells by their filled height, ties by index: the same order on every run. */
    private static final class Heap {
        private final int[] cells;
        private final double[] keys;
        private int count;

        Heap(int capacity, double[] keys) {
            this.cells = new int[capacity];
            this.keys = keys;
        }

        boolean isEmpty() {
            return count == 0;
        }

        private boolean less(int a, int b) {
            return keys[a] < keys[b] || keys[a] == keys[b] && a < b;
        }

        void push(int cell) {
            int k = count++;
            while (k > 0) {
                int parent = (k - 1) >> 1;
                if (!less(cell, cells[parent])) {
                    break;
                }
                cells[k] = cells[parent];
                k = parent;
            }
            cells[k] = cell;
        }

        int pop() {
            int top = cells[0];
            int last = cells[--count];
            int k = 0;
            while (true) {
                int child = 2 * k + 1;
                if (child >= count) {
                    break;
                }
                if (child + 1 < count && less(cells[child + 1], cells[child])) {
                    child++;
                }
                if (!less(cells[child], last)) {
                    break;
                }
                cells[k] = cells[child];
                k = child;
            }
            cells[k] = last;
            return top;
        }
    }
}
