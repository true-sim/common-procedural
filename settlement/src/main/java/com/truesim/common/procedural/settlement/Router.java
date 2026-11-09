package com.truesim.common.procedural.settlement;

import com.truesim.common.math.Maths;
import java.util.Arrays;

/**
 * Least-cost paths over the routing grid, after Galin et al., "Procedural Generation of Roads" (Eurographics 2010):
 * an anisotropic shortest path whose steps cost by their grade, the slope they cross, water and wet ground, and their
 * turns, over steps of up to four nodes in every direction they reach (48 directions, 4 to 11 degrees apart) so paths
 * need not follow the grid's axes and can traverse steep ground at a shallow angle. The search's state is a node and the
 * direction it was reached in, so turns are costed and limited: a road that must climb more steeply than its grade
 * allows has no straight way: it traverses the slope and turns back in a hairpin, in switchbacks. Steps along existing roads cost less ({@code reuse}), so
 * roads join the network rather than run beside it. A* from every source at once toward one target.
 */
final class Router {

    /** The steps: every offset of up to {@value #RADIUS} nodes whose components share no factor, by angle from +x. */
    private static final int RADIUS = 4;
    private static final int[] DX;
    private static final int[] DZ;
    private static final int DIRECTIONS;
    /** The direction of a source: any step may follow. */
    private static final int NONE;
    private static final int STATES;
    /** How many states a search may settle before it gives up. */
    private static final int LIMIT = 4_000_000;

    static {
        java.util.List<int[]> steps = new java.util.ArrayList<>();
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                if ((dx != 0 || dz != 0) && gcd(Math.abs(dx), Math.abs(dz)) == 1) {
                    steps.add(new int[] {dx, dz});
                }
            }
        }
        steps.sort((a, b) -> Double.compare(Maths.atan2(a[1], a[0]), Maths.atan2(b[1], b[0])));
        DIRECTIONS = steps.size();
        DX = new int[DIRECTIONS];
        DZ = new int[DIRECTIONS];
        for (int d = 0; d < DIRECTIONS; d++) {
            DX[d] = steps.get(d)[0];
            DZ[d] = steps.get(d)[1];
        }
        NONE = DIRECTIONS;
        STATES = DIRECTIONS + 1;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private final Field field;
    private final SettlementParameters.Network network;
    private final int n;
    private final double[] length = new double[DIRECTIONS];
    private final double[][] turn = new double[STATES][DIRECTIONS];
    /** The nodes each step passes over between its ends (both nodes either side of its line), as offsets. */
    private final int[][] passes = new int[DIRECTIONS][];
    private final double[] cost;
    private final int[] previous;
    private final boolean[] seen;
    private final int[] queue;
    private int[] heap = new int[1 << 12];
    private double[] keys = new double[1 << 12];
    private int size;
    /** The search's target's height, its class's grade and the grade it routes at. */
    private double targetHeight;
    private double grade = 1;
    private double climbGrade = 1;
    private final double rootWeight;

    Router(Field field, SettlementParameters.Network network) {
        this.field = field;
        this.network = network;
        this.n = field.n;
        this.rootWeight = Math.sqrt(network.gradeWeight());
        cost = new double[n * n * STATES];
        previous = new int[n * n * STATES];
        seen = new boolean[n * n];
        queue = new int[n * n];
        double[] angle = new double[DIRECTIONS];
        for (int d = 0; d < DIRECTIONS; d++) {
            length[d] = Math.sqrt(DX[d] * DX[d] + DZ[d] * DZ[d]) * field.cell;
            angle[d] = Maths.atan2(DZ[d], DX[d]);
        }
        for (int d = 0; d < DIRECTIONS; d++) {
            int m = Math.max(Math.abs(DX[d]), Math.abs(DZ[d]));
            java.util.List<Integer> over = new java.util.ArrayList<>();
            for (int k = 1; k < m; k++) {
                double px = (double) DX[d] * k / m;
                double pz = (double) DZ[d] * k / m;
                for (int[] c : new int[][] {{(int) Math.floor(px), (int) Math.floor(pz)},
                    {(int) Math.ceil(px), (int) Math.ceil(pz)}}) {
                    over.add(c[0]);
                    over.add(c[1]);
                }
            }
            passes[d] = over.stream().mapToInt(Integer::intValue).toArray();
        }
        for (int a = 0; a < STATES; a++) {
            for (int b = 0; b < DIRECTIONS; b++) {
                turn[a][b] = a == NONE ? 0 : Math.abs(Maths.deltaAngle(angle[a], angle[b]));
            }
        }
    }

    /**
     * The least-cost path for a road of {@code road}'s class from any of {@code sources} (nodes) to {@code target}:
     * its nodes from a source to the target, or null if there is none. {@code onRoad} marks the network's nodes;
     * {@code blocked} nodes are never entered.
     */
    int[] route(SettlementParameters.RoadClass road, int[] sources, int target, boolean[] onRoad, boolean[] blocked) {
        if (!reachable(road, sources, target, onRoad, blocked)) {
            return null;
        }
        Arrays.fill(cost, Double.POSITIVE_INFINITY);
        size = 0;
        targetHeight = field.height[target];
        grade = road.maxGrade();
        climbGrade = road.maxGrade() * network.headroom();
        int ti = target % n;
        int tj = target / n;
        double maxGrade = road.maxGrade() * network.headroom();
        double maxTan = Maths.tan(Maths.toRadians(road.maxSideSlope()));
        double maxTurn = Maths.toRadians(network.maxTurn()) + 1e-9;
        double turnCost = network.turnWeight() * field.cell;
        for (int s : sources) {
            int state = s * STATES + NONE;
            cost[state] = 0;
            previous[state] = -1;
            push(state, heuristic(s, ti, tj));
        }
        int settled = 0;
        while (size > 0) {
            double key = keys[0];
            int state = pop();
            int node = state / STATES;
            int from = state % STATES;
            double g = cost[state];
            if (key > g + heuristic(node, ti, tj) + 1e-9 * (1 + g)) {
                continue; // a stale entry
            }
            if (node == target) {
                return path(state);
            }
            if (++settled > LIMIT) {
                return null;
            }
            int i = node % n;
            int j = node / n;
            for (int d = 0; d < DIRECTIONS; d++) {
                double t = turn[from][d];
                if (t > maxTurn) {
                    continue;
                }
                int ni = i + DX[d];
                int nj = j + DZ[d];
                if (ni < RADIUS || nj < RADIUS || ni >= n - RADIUS || nj >= n - RADIUS) {
                    continue;
                }
                int next = nj * n + ni;
                if (blocked[next]) {
                    continue;
                }
                double step = stepCost(road, node, next, d, maxGrade, maxTan, onRoad, blocked);
                if (step == Double.POSITIVE_INFINITY) {
                    continue;
                }
                double total = g + step + turnCost * t * (1 + t); // sharp turns dearer: hairpins with long legs
                int nextState = next * STATES + d;
                if (total < cost[nextState]) {
                    cost[nextState] = total;
                    previous[nextState] = state;
                    push(nextState, total + heuristic(next, ti, tj));
                }
            }
        }
        return null;
    }

    /**
     * Whether any step-by-step way leads from the sources to the target, turns aside: a breadth-first flood over the
     * nodes, so a search for a target it cannot reach ends at once rather than settling every state.
     */
    private boolean reachable(SettlementParameters.RoadClass road, int[] sources, int target, boolean[] onRoad,
        boolean[] blocked) {
        double maxGrade = road.maxGrade() * network.headroom();
        double maxTan = Maths.tan(Maths.toRadians(road.maxSideSlope()));
        Arrays.fill(seen, false);
        int head = 0;
        int tail = 0;
        for (int s : sources) {
            if (!seen[s]) {
                seen[s] = true;
                queue[tail++] = s;
            }
        }
        while (head < tail) {
            int node = queue[head++];
            if (node == target) {
                return true;
            }
            int i = node % n;
            int j = node / n;
            for (int d = 0; d < DIRECTIONS; d++) {
                int ni = i + DX[d];
                int nj = j + DZ[d];
                if (ni < RADIUS || nj < RADIUS || ni >= n - RADIUS || nj >= n - RADIUS) {
                    continue;
                }
                int next = nj * n + ni;
                if (seen[next] || blocked[next]
                    || stepCost(road, node, next, d, maxGrade, maxTan, onRoad, blocked) == Double.POSITIVE_INFINITY) {
                    continue;
                }
                seen[next] = true;
                queue[tail++] = next;
            }
        }
        return false;
    }

    /** The cost of the step from {@code a} to {@code b} in direction {@code d}, infinite where it may not be taken. */
    private double stepCost(SettlementParameters.RoadClass road, int a, int b, int d, double maxGrade, double maxTan,
        boolean[] onRoad, boolean[] blocked) {
        double l = length[d];
        if (onRoad[a] && onRoad[b]) {
            return l * network.reuse();
        }
        // a long step passes over the nodes along its line: they must be passable too
        int ai = a % n;
        int aj = a / n;
        int[] over = passes[d];
        double side = slopeTan(b);
        for (int k = 0; k < over.length; k += 2) {
            int node = (aj + over[k + 1]) * n + ai + over[k];
            if (blocked[node]) {
                return Double.POSITIVE_INFINITY;
            }
            side = Math.max(side, slopeTan(node));
        }
        double grade = Math.abs(field.height[b] - field.height[a]) / l;
        if (grade > maxGrade) {
            return Double.POSITIVE_INFINITY;
        }
        if (side > maxTan) {
            return Double.POSITIVE_INFINITY;
        }
        double g = grade / road.maxGrade();
        double s = side / maxTan;
        double per = 1 + network.gradeWeight() * g * g + network.sideWeight() * s * s
            + network.wetWeight() * field.wetland[b] + (field.flood[b] ? network.floodWeight() : 0)
            + network.bridgeWeight() * field.water[b];
        return l * per;
    }

    private double slopeTan(int node) {
        return field.gradient[node];
    }

    /**
     * The least a way to the target can cost off the network: at least the straight distance, and long enough to climb
     * the height between at the class's grade; climbing {@code dh} over a length {@code L} costs at least
     * {@code L + gradeWeight dh^2 / (maxGrade^2 L)} (evenly is cheapest: the cost is convex in the grade), least at
     * {@code L = sqrt(gradeWeight) dh / maxGrade}. It may overestimate along the network, where steps cost less, which
     * only makes a path a little dearer than the cheapest.
     */
    private double heuristic(int node, int ti, int tj) {
        double dx = node % n - ti;
        double dz = node / n - tj;
        double d = Math.sqrt(dx * dx + dz * dz) * field.cell;
        double dh = Math.abs(field.height[node] - targetHeight);
        double l = Math.max(d, Math.max(dh / climbGrade, rootWeight * dh / grade));
        return l <= 0 ? 0 : l + network.gradeWeight() * dh * dh / (grade * grade * l);
    }

    private int[] path(int state) {
        int count = 0;
        for (int s = state; s >= 0; s = previous[s]) {
            count++;
        }
        int[] nodes = new int[count];
        for (int s = state, k = count - 1; s >= 0; s = previous[s], k--) {
            nodes[k] = s / STATES;
        }
        return nodes;
    }

    private void push(int state, double key) {
        if (size == heap.length) {
            heap = Arrays.copyOf(heap, size * 2);
            keys = Arrays.copyOf(keys, size * 2);
        }
        int k = size++;
        while (k > 0) {
            int parent = (k - 1) >> 1;
            if (!before(key, state, keys[parent], heap[parent])) {
                break;
            }
            heap[k] = heap[parent];
            keys[k] = keys[parent];
            k = parent;
        }
        heap[k] = state;
        keys[k] = key;
    }

    private int pop() {
        int top = heap[0];
        int lastState = heap[--size];
        double lastKey = keys[size];
        int k = 0;
        while (true) {
            int child = 2 * k + 1;
            if (child >= size) {
                break;
            }
            if (child + 1 < size && before(keys[child + 1], heap[child + 1], keys[child], heap[child])) {
                child++;
            }
            if (!before(keys[child], heap[child], lastKey, lastState)) {
                break;
            }
            heap[k] = heap[child];
            keys[k] = keys[child];
            k = child;
        }
        if (size > 0) {
            heap[k] = lastState;
            keys[k] = lastKey;
        }
        return top;
    }

    /** Orders by key, then by state: the same order on every run. */
    private static boolean before(double keyA, int stateA, double keyB, int stateB) {
        return keyA < keyB || keyA == keyB && stateA < stateB;
    }
}
