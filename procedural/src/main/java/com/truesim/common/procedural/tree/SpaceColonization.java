package com.truesim.common.procedural.tree;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.random.Rng;
import java.util.Arrays;

/**
 * Space colonization (Runions, Lane and Prusinkiewicz, "Modeling trees with a space colonization algorithm", 2007):
 * attraction points fill the crown's envelope; each step, every point pulls the node nearest to it within the
 * influence distance, every pulled node grows one segment toward the mean of its pulls (plus tropism), and points a
 * node has come within the kill distance of are used up. Branches thus compete for space and fill the crown as
 * broadleaf trees do. The node graph is then smoothed and split into branches, each continuing into its heaviest child
 * (the one carrying the most); the trunk ends at the crown's base, the limbs above it its main branches.
 *
 * <p>Nearest-node queries go through a uniform grid of cells as large as the influence distance. Reused from tree to
 * tree; not thread-safe.
 */
final class SpaceColonization {

    /** At most this many nodes per tree: growth stops there. */
    private static final int MAX_NODES = 60_000;

    private final Skeleton trunks = new Skeleton();
    // Graph nodes: position, parent, the pull accumulated this step
    private double[] nx = new double[4096];
    private double[] ny = new double[4096];
    private double[] nz = new double[4096];
    private int[] parent = new int[4096];
    private double[] pullX = new double[4096];
    private double[] pullY = new double[4096];
    private double[] pullZ = new double[4096];
    private int[] pulls = new int[4096];
    private int nodes;
    private int trunkNodes; // the first nodes: the trunks below the crown
    // Attraction points still alive
    private double[] px = new double[2048];
    private double[] py = new double[2048];
    private double[] pz = new double[2048];
    private int points;
    // The grid: per cell its first node, per node the next in its cell
    private int[] head = new int[1024];
    private int[] next = new int[4096];
    private int columns;
    private int layers;
    private int rows;
    private double originX;
    private double originY;
    private double originZ;
    private double cell;
    // Splitting into branches
    private int[] weight = new int[4096];
    private int[] childStart = new int[4097];
    private int[] children = new int[4096];
    private int[] skeletonNode = new int[4096];
    private int[] stack = new int[3 * 1024];

    /** Grows {@code species}' trunks and crown into {@code skeleton}, {@code height} metres tall. */
    void grow(Skeleton skeleton, TreeSpecies species, double height, Rng rng) {
        Crown crown = species.crown();
        double bottom = species.crownBase() * height;
        double radius = crown.radius() * height;
        double segment = crown.segment();
        double influence = crown.influence() * segment;
        double kill = crown.kill() * segment;
        // Trunks up to the crown's base, as the graph's first nodes
        trunks.clear();
        Trunks.grow(trunks, species, height, bottom, rng);
        nodes = 0;
        trunkNodes = trunks.nodeCount();
        for (int n = 0; n < trunkNodes; n++) {
            addNode(trunks.x(n), trunks.y(n), trunks.z(n), trunks.parent(n));
        }
        scatter(crown, rng, bottom, height, radius);
        grid(radius + influence, height + influence, influence);
        for (int n = 0; n < nodes; n++) {
            insert(n);
        }
        for (int step = 0; step < crown.iterations() && points > 0 && nodes < MAX_NODES; step++) {
            if (!step(segment, influence, kill, crown.tropism())) {
                break;
            }
        }
        smooth();
        split(skeleton);
    }

    /** Attraction points spread uniformly through the envelope. */
    @AllowAllocation("growing, until warm")
    private void scatter(Crown crown, Rng rng, double bottom, double top, double radius) {
        int want = crown.points();
        if (px.length < want) {
            px = new double[want];
            py = new double[want];
            pz = new double[want];
        }
        points = 0;
        while (points < want) {
            double x = rng.nextDouble() * 2 - 1;
            double y = rng.nextDouble();
            double z = rng.nextDouble() * 2 - 1;
            if (crown.shape().contains(x, y, z)) {
                px[points] = x * radius;
                py[points] = bottom + y * (top - bottom);
                pz[points] = z * radius;
                points++;
            }
        }
    }

    @AllowAllocation("growing, until warm")
    private void grid(double halfWidth, double top, double size) {
        cell = size;
        originX = -halfWidth;
        originY = -size;
        originZ = -halfWidth;
        columns = (int) Math.ceil(2 * halfWidth / size) + 1;
        rows = (int) Math.ceil(2 * halfWidth / size) + 1;
        layers = (int) Math.ceil((top + size) / size) + 1;
        int cells = columns * rows * layers;
        if (head.length < cells) {
            head = new int[cells];
        }
        Arrays.fill(head, 0, cells, -1);
    }

    private int cellOf(double x, double y, double z) {
        int cx = Math.clamp((int) Math.floor((x - originX) / cell), 0, columns - 1);
        int cy = Math.clamp((int) Math.floor((y - originY) / cell), 0, layers - 1);
        int cz = Math.clamp((int) Math.floor((z - originZ) / cell), 0, rows - 1);
        return (cy * rows + cz) * columns + cx;
    }

    private void insert(int n) {
        int c = cellOf(nx[n], ny[n], nz[n]);
        next[n] = head[c];
        head[c] = n;
    }

    @AllowAllocation("growing, until warm")
    private int addNode(double x, double y, double z, int parentNode) {
        if (nodes == nx.length) {
            int capacity = 2 * nodes;
            nx = Arrays.copyOf(nx, capacity);
            ny = Arrays.copyOf(ny, capacity);
            nz = Arrays.copyOf(nz, capacity);
            parent = Arrays.copyOf(parent, capacity);
            pullX = Arrays.copyOf(pullX, capacity);
            pullY = Arrays.copyOf(pullY, capacity);
            pullZ = Arrays.copyOf(pullZ, capacity);
            pulls = Arrays.copyOf(pulls, capacity);
            next = Arrays.copyOf(next, capacity);
        }
        nx[nodes] = x;
        ny[nodes] = y;
        nz[nodes] = z;
        parent[nodes] = parentNode;
        pullX[nodes] = 0;
        pullY[nodes] = 0;
        pullZ[nodes] = 0;
        pulls[nodes] = 0;
        return nodes++;
    }

    /** One growth step; whether anything grew. */
    private boolean step(double segment, double influence, double kill, double tropism) {
        double influence2 = influence * influence;
        double kill2 = kill * kill;
        for (int i = 0; i < points; i++) {
            int nearest = -1;
            double best = influence2;
            int cx = Math.clamp((int) Math.floor((px[i] - originX) / cell), 0, columns - 1);
            int cy = Math.clamp((int) Math.floor((py[i] - originY) / cell), 0, layers - 1);
            int cz = Math.clamp((int) Math.floor((pz[i] - originZ) / cell), 0, rows - 1);
            for (int y = Math.max(cy - 1, 0); y <= Math.min(cy + 1, layers - 1); y++) {
                for (int z = Math.max(cz - 1, 0); z <= Math.min(cz + 1, rows - 1); z++) {
                    for (int x = Math.max(cx - 1, 0); x <= Math.min(cx + 1, columns - 1); x++) {
                        for (int n = head[(y * rows + z) * columns + x]; n >= 0; n = next[n]) {
                            double dx = px[i] - nx[n];
                            double dy = py[i] - ny[n];
                            double dz = pz[i] - nz[n];
                            double d2 = dx * dx + dy * dy + dz * dz;
                            if (d2 < best || (d2 == best && n < nearest)) {
                                best = d2;
                                nearest = n;
                            }
                        }
                    }
                }
            }
            if (nearest < 0) {
                continue;
            }
            if (best < kill2) {
                // Used up: the last point takes its place
                points--;
                px[i] = px[points];
                py[i] = py[points];
                pz[i] = pz[points];
                i--;
                continue;
            }
            double d = Math.sqrt(best);
            pullX[nearest] += (px[i] - nx[nearest]) / d;
            pullY[nearest] += (py[i] - ny[nearest]) / d;
            pullZ[nearest] += (pz[i] - nz[nearest]) / d;
            pulls[nearest]++;
        }
        boolean grew = false;
        int before = nodes;
        for (int n = 0; n < before && nodes < MAX_NODES; n++) {
            if (pulls[n] == 0) {
                continue;
            }
            double length = Math.sqrt(pullX[n] * pullX[n] + pullY[n] * pullY[n] + pullZ[n] * pullZ[n]);
            double dx = pullX[n] / Math.max(length, 1e-9);
            double dy = pullY[n] / Math.max(length, 1e-9) + tropism;
            double dz = pullZ[n] / Math.max(length, 1e-9);
            pullX[n] = 0;
            pullY[n] = 0;
            pullZ[n] = 0;
            pulls[n] = 0;
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 0.1 || l < 1e-6) {
                continue; // pulls that cancel: no clear way to grow
            }
            double cx = nx[n] + dx / l * segment;
            double cy = ny[n] + dy / l * segment;
            double cz = nz[n] + dz / l * segment;
            if (grownAlready(n, cx, cy, cz, 0.2 * segment)) {
                continue; // pulled the same way again: that child exists
            }
            insert(addNode(cx, cy, cz, n));
            grew = true;
        }
        return grew;
    }

    /** Whether node {@code n} has a child within {@code near} of (x, y, z). */
    private boolean grownAlready(int n, double x, double y, double z, double near) {
        int cx = Math.clamp((int) Math.floor((x - originX) / cell), 0, columns - 1);
        int cy = Math.clamp((int) Math.floor((y - originY) / cell), 0, layers - 1);
        int cz = Math.clamp((int) Math.floor((z - originZ) / cell), 0, rows - 1);
        for (int gy = Math.max(cy - 1, 0); gy <= Math.min(cy + 1, layers - 1); gy++) {
            for (int gz = Math.max(cz - 1, 0); gz <= Math.min(cz + 1, rows - 1); gz++) {
                for (int gx = Math.max(cx - 1, 0); gx <= Math.min(cx + 1, columns - 1); gx++) {
                    for (int m = head[(gy * rows + gz) * columns + gx]; m >= 0; m = next[m]) {
                        double dx = nx[m] - x;
                        double dy = ny[m] - y;
                        double dz = nz[m] - z;
                        if (parent[m] == n && dx * dx + dy * dy + dz * dz < near * near) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Two passes of Laplacian smoothing on nodes inside a chain (one parent, one child): less zigzag. */
    @AllowAllocation("growing, until warm")
    private void smooth() {
        countChildren();
        for (int pass = 0; pass < 2; pass++) {
            for (int n = 0; n < nodes; n++) {
                int p = parent[n];
                if (p < 0 || childStart[n + 1] - childStart[n] != 1) {
                    continue;
                }
                int c = children[childStart[n]];
                nx[n] = 0.5 * nx[n] + 0.25 * (nx[p] + nx[c]);
                ny[n] = 0.5 * ny[n] + 0.25 * (ny[p] + ny[c]);
                nz[n] = 0.5 * nz[n] + 0.25 * (nz[p] + nz[c]);
            }
        }
    }

    @AllowAllocation("growing, until warm")
    private void countChildren() {
        if (childStart.length < nodes + 1) {
            childStart = new int[2 * nodes + 1];
            children = new int[2 * nodes];
            weight = new int[2 * nodes];
            skeletonNode = new int[2 * nodes];
        }
        Arrays.fill(childStart, 0, nodes + 1, 0);
        for (int n = 0; n < nodes; n++) {
            if (parent[n] >= 0) {
                childStart[parent[n] + 1]++;
            }
        }
        for (int n = 0; n < nodes; n++) {
            childStart[n + 1] += childStart[n];
        }
        int[] fill = weight; // as a cursor per node for now
        System.arraycopy(childStart, 0, fill, 0, nodes);
        for (int n = 0; n < nodes; n++) {
            if (parent[n] >= 0) {
                children[fill[parent[n]]++] = n;
            }
        }
    }

    /** The graph as branches: each continues into its heaviest child, the others start branches a level finer. */
    @AllowAllocation("growing, until warm")
    private void split(Skeleton skeleton) {
        for (int n = 0; n < nodes; n++) {
            weight[n] = 1;
        }
        for (int n = nodes - 1; n >= 0; n--) {
            if (parent[n] >= 0) {
                weight[parent[n]] += weight[n];
            }
        }
        skeleton.clear();
        int top = 0;
        for (int n = nodes - 1; n >= 0; n--) { // roots, pushed last first so the first is taken first
            if (parent[n] < 0) {
                top = push(top, n, 0, -1);
            }
        }
        while (top > 0) {
            top -= 3;
            int start = stack[top];
            int level = stack[top + 1];
            int attach = stack[top + 2];
            int branch = skeleton.beginBranch(level, attach < 0 ? -1 : skeleton.branchOf(attach));
            int previous = attach < 0 ? -1
                : skeleton.addNode(branch, skeleton.x(attach), skeleton.y(attach), skeleton.z(attach), attach);
            for (int n = start; n >= 0; ) {
                previous = skeleton.addNode(branch, nx[n], ny[n], nz[n], previous);
                skeletonNode[n] = previous;
                int heaviest = -1;
                for (int k = childStart[n]; k < childStart[n + 1]; k++) {
                    int c = children[k];
                    // A trunk ends where the crown begins: what grows on from its top are main branches, whose pivots
                    // then stay on the trunk's axis (the wind format's)
                    boolean allowed = level > 0 || c < trunkNodes;
                    if (allowed && (heaviest < 0 || weight[c] > weight[heaviest])) {
                        heaviest = c;
                    }
                }
                for (int k = childStart[n + 1] - 1; k >= childStart[n]; k--) {
                    int c = children[k];
                    if (c != heaviest) {
                        top = push(top, c, level + 1, previous);
                    }
                }
                n = heaviest;
            }
        }
    }

    @AllowAllocation("growing, until warm")
    private int push(int top, int start, int level, int attach) {
        if (top + 3 > stack.length) {
            stack = Arrays.copyOf(stack, 2 * stack.length);
        }
        stack[top] = start;
        stack[top + 1] = level;
        stack[top + 2] = attach;
        return top + 3;
    }
}
