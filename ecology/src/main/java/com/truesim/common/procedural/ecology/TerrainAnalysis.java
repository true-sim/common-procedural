package com.truesim.common.procedural.ecology;

import com.truesim.common.math.Maths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The terrain's hydrology and topographic position, analysed in world-aligned blocks and blended between them, so
 * every field is a pure function of the point: the same whichever tile asks, in whatever order.
 *
 * <p>Each block samples the terrain every {@code cell} metres over itself and a margin round it, then:
 * <ol>
 *   <li><b>Fills depressions</b> by Priority-Flood (Barnes, Lehman and Mulla 2014) from the window's edge, each cell
 *       raised to just above the one it was flooded from, so every cell drains and the order cells leave the queue in
 *       is a drainage order; the fill's depth marks lakes.</li>
 *   <li><b>Accumulates flow</b> by multiple flow directions (Quinn, Beven, Chevallier and Planchon 1991): each cell
 *       passes its catchment to all lower neighbours by slope times contour length (half a cell straight, 0.354
 *       diagonally), giving the specific catchment area {@code a} (catchment per contour width).</li>
 *   <li><b>Topographic wetness index</b> {@code ln(a / tan(slope))} (Beven and Kirkby 1979), the slope at least
 *       0.001.</li>
 *   <li><b>Channels</b> by single flow directions (steepest descent on the filled surface; O'Callaghan and Mark 1984),
 *       which keep a river together across a flat floor where multiple directions spread it: a cell is a stream where
 *       its single-direction catchment passes the stream threshold.</li>
 *   <li><b>Height above the nearest drainage</b> (HAND; Renno et al. 2008), following the single flow directions down
 *       to a stream cell; and the nearest stream and river (straight distance by chamfer passes), their catchments and
 *       the height above them: what riparian bands and rivers' water are measured from.</li>
 *   <li><b>Topographic position index</b> (Weiss 2001): the height above the mean of a square neighbourhood, ridges
 *       above 0, hollows below.</li>
 * </ol>
 *
 * A point blends the four blocks round it bilinearly by its position among their centres; each block's window
 * reaches at least half a block plus the position radius beyond any point it is blended at, so catchments are cut
 * only by the margin.
 */
final class TerrainAnalysis {

    /** The topographic wetness index. */
    static final int WETNESS = 0;
    /** Height above the nearest drainage, metres. */
    static final int HAND = 1;
    /** The distance to the nearest stream, metres. */
    static final int DISTANCE = 2;
    /** The nearest stream's catchment area, square metres. */
    static final int STREAM_AREA = 3;
    /** The topographic position index, metres. */
    static final int POSITION = 4;
    /** The depth of filling, metres: lakes. */
    static final int FILL = 5;
    /** The specific catchment area, metres. */
    static final int CATCHMENT = 6;
    /** The height above the nearest stream, metres. */
    static final int STREAM_HEIGHT = 7;
    /** The distance to the nearest river (catchment above the river threshold), metres. */
    static final int RIVER_DISTANCE = 8;
    /** The nearest river's catchment area, square metres. */
    static final int RIVER_AREA = 9;
    /** How many fields. */
    static final int FIELDS = 10;

    private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] DZ = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final double DIAGONAL = Math.sqrt(2);

    private final EcologyTerrain terrain;
    private final EcologyParameters.Analysis analysis;
    private final EcologyParameters.Hydrology hydrology;
    private final int size;
    private final Map<Long, FutureTask<float[][]>> blocks;
    private final AtomicLong analysed = new AtomicLong();
    private final AtomicLong nanos = new AtomicLong();

    TerrainAnalysis(EcologyTerrain terrain, EcologyParameters.Analysis analysis,
        EcologyParameters.Hydrology hydrology) {
        this.terrain = terrain;
        this.analysis = analysis;
        this.hydrology = hydrology;
        this.size = (int) Math.round((analysis.block() + 2 * analysis.margin()) / analysis.cell()) + 1;
        int kept = analysis.blocks();
        this.blocks = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, FutureTask<float[][]>> eldest) {
                return size() > kept;
            }
        };
    }

    /** Blocks analysed so far (again after eviction too). */
    long analysed() {
        return analysed.get();
    }

    /** Nanoseconds spent analysing blocks. */
    long nanos() {
        return nanos.get();
    }

    /** The fields at ({@code x}, {@code z}) into {@code out} ({@link #FIELDS} of them). */
    void sample(double x, double z, double[] out) {
        double b = analysis.block();
        double u = x / b - 0.5;
        double v = z / b - 0.5;
        long bx = (long) Math.floor(u);
        long bz = (long) Math.floor(v);
        double fu = u - bx;
        double fv = v - bz;
        java.util.Arrays.fill(out, 0, FIELDS, 0);
        for (int k = 0; k < 4; k++) {
            int ix = k & 1;
            int iz = k >> 1;
            double w = (ix == 0 ? 1 - fu : fu) * (iz == 0 ? 1 - fv : fv);
            if (w > 0) {
                add(block(bx + ix, bz + iz), bx + ix, bz + iz, x, z, w, out);
            }
        }
    }

    private void add(float[][] fields, long bx, long bz, double x, double z, double w, double[] out) {
        double c = analysis.cell();
        double ox = bx * analysis.block() - analysis.margin();
        double oz = bz * analysis.block() - analysis.margin();
        double gx = Math.clamp((x - ox) / c, 0, size - 1.000001);
        double gz = Math.clamp((z - oz) / c, 0, size - 1.000001);
        int i = (int) gx;
        int j = (int) gz;
        double fx = gx - i;
        double fz = gz - j;
        int a = j * size + i;
        double w00 = (1 - fx) * (1 - fz) * w;
        double w10 = fx * (1 - fz) * w;
        double w01 = (1 - fx) * fz * w;
        double w11 = fx * fz * w;
        for (int f = 0; f < FIELDS; f++) {
            float[] field = fields[f];
            out[f] += w00 * field[a] + w10 * field[a + 1] + w01 * field[a + size] + w11 * field[a + size + 1];
        }
    }

    private float[][] block(long bx, long bz) {
        long key = bx * 0x9E3779B1L ^ bz;
        FutureTask<float[][]> task;
        boolean mine = false;
        synchronized (blocks) {
            task = blocks.get(key);
            if (task == null) {
                task = new FutureTask<>(() -> analyse(bx, bz));
                blocks.put(key, task);
                mine = true;
            }
        }
        if (mine) {
            task.run();
        }
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted analysing the terrain", e);
        } catch (ExecutionException e) {
            synchronized (blocks) {
                blocks.remove(key);
            }
            throw new IllegalStateException("Analysing the terrain failed", e.getCause());
        }
    }

    /** Analyses block ({@code bx}, {@code bz}). */
    float[][] analyse(long bx, long bz) {
        long start = System.nanoTime();
        int n = size;
        int count = n * n;
        double c = analysis.cell();
        double ox = bx * analysis.block() - analysis.margin();
        double oz = bz * analysis.block() - analysis.margin();
        double[] h = new double[count];
        // the heights row by row on the common pool: each a pure function of its point, so the order is free
        java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
            double z = oz + j * c;
            for (int i = 0; i < n; i++) {
                h[j * n + i] = terrain.altitude(ox + i * c, z);
            }
        });
        // Priority-Flood: fill and a drainage order
        double[] filled = new double[count];
        int[] receiver = new int[count];
        int[] order = new int[count];
        boolean[] seen = new boolean[count];
        Heap heap = new Heap(count, filled);
        for (int a = 0; a < count; a++) {
            int i = a % n;
            int j = a / n;
            if (i == 0 || j == 0 || i == n - 1 || j == n - 1) {
                filled[a] = h[a];
                receiver[a] = -1;
                seen[a] = true;
                heap.push(a);
            }
        }
        int popped = 0;
        while (!heap.isEmpty()) {
            int a = heap.pop();
            order[popped++] = a;
            int i = a % n;
            int j = a / n;
            for (int d = 0; d < 8; d++) {
                int ni = i + DX[d];
                int nj = j + DZ[d];
                if (ni < 0 || nj < 0 || ni >= n || nj >= n) {
                    continue;
                }
                int b = nj * n + ni;
                if (!seen[b]) {
                    seen[b] = true;
                    filled[b] = Math.max(h[b], Math.nextUp(filled[a]));
                    receiver[b] = a;
                    heap.push(b);
                }
            }
        }
        // Multiple flow directions (Quinn et al. 1991), from the top of the drainage order down
        double[] area = new double[count];
        java.util.Arrays.fill(area, c * c);
        for (int k = count - 1; k >= 0; k--) {
            int a = order[k];
            int i = a % n;
            int j = a / n;
            double total = 0;
            for (int d = 0; d < 8; d++) {
                int b = neighbour(i, j, d, n);
                if (b >= 0 && filled[b] < filled[a]) {
                    total += weight(filled[a] - filled[b], d, c);
                }
            }
            if (total > 0) {
                for (int d = 0; d < 8; d++) {
                    int b = neighbour(i, j, d, n);
                    if (b >= 0 && filled[b] < filled[a]) {
                        area[b] += area[a] * weight(filled[a] - filled[b], d, c) / total;
                    }
                }
            } else if (receiver[a] >= 0) {
                area[receiver[a]] += area[a];
            }
        }
        // Single flow directions (steepest descent on the filled surface, O'Callaghan and Mark 1984) for the
        // channels: multiple directions spread a river over a flat valley floor, one direction keeps it together
        int[] steepest = new int[count];
        double[] channelArea = new double[count];
        java.util.Arrays.fill(channelArea, c * c);
        for (int k = count - 1; k >= 0; k--) {
            int a = order[k];
            int i = a % n;
            int j = a / n;
            int best = receiver[a];
            double drop = 0;
            for (int d = 0; d < 8; d++) {
                int b = neighbour(i, j, d, n);
                if (b >= 0 && filled[b] < filled[a]) {
                    double g = (filled[a] - filled[b]) / ((d & 1) == 1 ? DIAGONAL : 1);
                    if (g > drop) {
                        drop = g;
                        best = b;
                    }
                }
            }
            steepest[a] = best;
            if (best >= 0) {
                channelArea[best] += channelArea[a];
            }
        }
        float[][] fields = new float[FIELDS][count];
        // Wetness index, from the unfilled surface's slope
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int a = j * n + i;
                double gx = (h[j * n + Math.min(i + 1, n - 1)] - h[j * n + Math.max(i - 1, 0)])
                    / (c * (Math.min(i + 1, n - 1) - Math.max(i - 1, 0)));
                double gz = (h[Math.min(j + 1, n - 1) * n + i] - h[Math.max(j - 1, 0) * n + i])
                    / (c * (Math.min(j + 1, n - 1) - Math.max(j - 1, 0)));
                double tan = Math.max(Math.sqrt(gx * gx + gz * gz), 0.001);
                double sca = area[a] / c;
                fields[CATCHMENT][a] = (float) sca;
                fields[WETNESS][a] = (float) Maths.log(sca / tan);
                fields[FILL][a] = (float) (filled[a] - h[a]);
            }
        }
        // Height above the nearest drainage, along the flood receivers, receivers first
        double[] drain = new double[count];
        for (int k = 0; k < count; k++) {
            int a = order[k];
            int r = steepest[a];
            if (channelArea[a] >= hydrology.streamArea() || r < 0) {
                drain[a] = h[a];
            } else {
                drain[a] = drain[r];
            }
            fields[HAND][a] = (float) Math.max(0, h[a] - drain[a]);
        }
        // The nearest stream and the nearest river, by octile distance (two chamfer passes carrying the source)
        int[] nearestStream = nearest(channelArea, hydrology.streamArea(), n, c);
        int[] nearestRiver = nearest(channelArea, hydrology.riverArea(), n, c);
        for (int a = 0; a < count; a++) {
            int sa = nearestStream[a];
            int ra = nearestRiver[a];
            fields[DISTANCE][a] = sa < 0 ? 1e6f : (float) straight(a, sa, n, c);
            fields[STREAM_AREA][a] = sa < 0 ? 0 : (float) channelArea[sa];
            fields[STREAM_HEIGHT][a] = sa < 0 ? 1e4f : (float) Math.max(0, h[a] - h[sa]);
            fields[RIVER_DISTANCE][a] = ra < 0 ? 1e6f : (float) straight(a, ra, n, c);
            fields[RIVER_AREA][a] = ra < 0 ? 0 : (float) channelArea[ra];
        }
        // Topographic position: height above the square neighbourhood's mean, by a summed-area table
        int r = (int) Math.round(analysis.positionRadius() / c);
        double[] sums = new double[(n + 1) * (n + 1)];
        for (int j = 0; j < n; j++) {
            double row = 0;
            for (int i = 0; i < n; i++) {
                row += h[j * n + i];
                sums[(j + 1) * (n + 1) + i + 1] = sums[j * (n + 1) + i + 1] + row;
            }
        }
        for (int j = 0; j < n; j++) {
            int j0 = Math.max(0, j - r);
            int j1 = Math.min(n, j + r + 1);
            for (int i = 0; i < n; i++) {
                int i0 = Math.max(0, i - r);
                int i1 = Math.min(n, i + r + 1);
                double sum = sums[j1 * (n + 1) + i1] - sums[j0 * (n + 1) + i1] - sums[j1 * (n + 1) + i0]
                    + sums[j0 * (n + 1) + i0];
                fields[POSITION][j * n + i] = (float) (h[j * n + i] - sum / ((i1 - i0) * (j1 - j0)));
            }
        }
        analysed.incrementAndGet();
        nanos.addAndGet(System.nanoTime() - start);
        return fields;
    }

    /**
     * For every cell, the nearest cell whose catchment is at least {@code threshold} (-1 if none), by two chamfer
     * passes with octile steps.
     */
    private static int[] nearest(double[] area, double threshold, int n, double c) {
        int count = n * n;
        int[] source = new int[count];
        double[] distance = new double[count];
        for (int a = 0; a < count; a++) {
            boolean channel = area[a] >= threshold;
            source[a] = channel ? a : -1;
            distance[a] = channel ? 0 : Double.MAX_VALUE;
        }
        double diagonal = DIAGONAL * c;
        for (int pass = 0; pass < 2; pass++) {
            int step = pass == 0 ? 1 : -1;
            int start = pass == 0 ? 0 : count - 1;
            for (int k = 0; k < count; k++) {
                int a = start + step * k;
                int i = a % n;
                int j = a / n;
                // the four neighbours this pass has already visited
                relax(a, i - step, j, c, n, source, distance);
                relax(a, i - step, j - step, diagonal, n, source, distance);
                relax(a, i, j - step, c, n, source, distance);
                relax(a, i + step, j - step, diagonal, n, source, distance);
            }
        }
        return source;
    }

    private static void relax(int a, int i, int j, double step, int n, int[] source, double[] distance) {
        if (i < 0 || j < 0 || i >= n || j >= n) {
            return;
        }
        int b = j * n + i;
        if (source[b] >= 0 && distance[b] + step < distance[a]) {
            distance[a] = distance[b] + step;
            source[a] = source[b];
        }
    }

    /** The straight distance between cells {@code a} and {@code b}, metres. */
    private static double straight(int a, int b, int n, double c) {
        double dx = a % n - b % n;
        double dz = a / n - b / n;
        return Math.sqrt(dx * dx + dz * dz) * c;
    }

    private static int neighbour(int i, int j, int d, int n) {
        int ni = i + DX[d];
        int nj = j + DZ[d];
        return ni < 0 || nj < 0 || ni >= n || nj >= n ? -1 : nj * n + ni;
    }

    /** Quinn's weight: the slope to the neighbour times the contour length it drains across. */
    private static double weight(double drop, int d, double c) {
        boolean diagonal = (d & 1) == 1;
        return diagonal ? drop / (DIAGONAL * c) * 0.354 : drop / c * 0.5;
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
