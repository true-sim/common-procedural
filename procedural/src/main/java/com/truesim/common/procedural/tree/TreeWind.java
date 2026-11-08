package com.truesim.common.procedural.tree;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralVertex;

/**
 * The wind data of a skeleton's nodes and branches, as {@link ProceduralVertex} defines it: per main branch (a trunk's
 * child) its pivot, reach and phase and the trunk's weight there; per node its path length from its main branch's
 * pivot; per trunk its base and top. Reused from tree to tree.
 */
final class TreeWind {

    private int[] main = new int[256];      // per branch: its main branch, -1 on a trunk
    private int[] stem = new int[256];      // per branch: its trunk
    private double[] stemBase = new double[256];
    private double[] stemTop = new double[256];
    private double[] pivotHeight = new double[256];
    private double[] reach = new double[256];
    private double[] phase = new double[256];
    private double[] trunkAtPivot = new double[256];
    private double[] path = new double[1024]; // per node: metres from its main branch's pivot
    private Skeleton skeleton;

    @AllowAllocation("growing, until warm")
    void compute(Skeleton skeleton, long seed) {
        this.skeleton = skeleton;
        int branches = skeleton.branchCount();
        if (main.length < branches) {
            int capacity = 2 * branches;
            main = new int[capacity];
            stem = new int[capacity];
            stemBase = new double[capacity];
            stemTop = new double[capacity];
            pivotHeight = new double[capacity];
            reach = new double[capacity];
            phase = new double[capacity];
            trunkAtPivot = new double[capacity];
        }
        if (path.length < skeleton.nodeCount()) {
            path = new double[2 * skeleton.nodeCount()];
        }
        for (int b = 0; b < branches; b++) {
            int level = skeleton.level(b);
            int parent = skeleton.parentBranch(b);
            stem[b] = level == 0 ? b : stem[parent];
            main[b] = level == 0 ? -1 : level == 1 ? b : main[parent];
            if (level == 0) {
                int first = skeleton.firstNode(b);
                double top = skeleton.y(first);
                for (int n = first; n < first + skeleton.nodeCount(b); n++) {
                    top = Math.max(top, skeleton.y(n));
                }
                stemBase[b] = skeleton.y(first);
                stemTop[b] = top;
            }
        }
        for (int b = 0; b < branches; b++) {
            if (skeleton.level(b) == 1) {
                int pivot = skeleton.parent(skeleton.firstNode(b));
                pivotHeight[b] = skeleton.y(pivot);
                reach[b] = Math.max(Axes.length(skeleton, b), 1e-3);
                phase[b] = (Seeds.derive(seed, b) >>> 11) * 0x1.0p-53;
                trunkAtPivot[b] = trunkWeight(skeleton.branchOf(pivot), skeleton.y(pivot));
            }
        }
        for (int n = 0; n < skeleton.nodeCount(); n++) {
            int b = skeleton.branchOf(n);
            int p = skeleton.parent(n);
            if (skeleton.level(b) == 0) {
                path[n] = 0;
            } else if (n == skeleton.firstNode(b)) {
                path[n] = main[b] == b ? 0 : path[p];
            } else {
                path[n] = path[p] + Axes.distance(skeleton, n, p);
            }
        }
    }

    /** The trunk weight at height {@code y} on trunk {@code stem}: {@code (h / height)^1.5}. */
    private double trunkWeight(int stem, double y) {
        double height = Math.max(stemTop[stem] - stemBase[stem], 1e-3);
        double h = Math.clamp((y - stemBase[stem]) / height, 0, 1);
        return h * Math.sqrt(h);
    }

    private static double branchWeight(double path, double reach) {
        double s = Math.clamp(path / reach, 0, 1);
        return s * Math.sqrt(s);
    }

    /** Sets the writer's wind for a point of branch {@code b}, {@code s} metres along it, at height {@code y}. */
    void apply(MeshWriter writer, int b, double s, double y) {
        int m = main[b];
        if (m < 0) {
            int t = stem[b];
            writer.wind(trunkWeight(t, y), 0, 0, 0, 0, stemTop[t] - stemBase[t]);
        } else {
            double along = path[skeleton.firstNode(b)] + s;
            writer.wind(trunkAtPivot[m], branchWeight(along, reach[m]), 0, phase[m], pivotHeight[m], reach[m]);
        }
    }

    /** Sets the writer's wind for a point at node {@code n}, with leaf weight {@code leaf}. */
    void applyNode(MeshWriter writer, int n, double leaf) {
        int b = skeleton.branchOf(n);
        int m = main[b];
        if (m < 0) {
            int t = stem[b];
            writer.wind(trunkWeight(t, skeleton.y(n)), 0, leaf, 0, 0, stemTop[t] - stemBase[t]);
        } else {
            writer.wind(trunkAtPivot[m], branchWeight(path[n], reach[m]), leaf, phase[m], pivotHeight[m], reach[m]);
        }
    }

    /** The trunk weight of node {@code n} (its pivot's, off the trunk). */
    double trunk(int n) {
        int b = skeleton.branchOf(n);
        return main[b] < 0 ? trunkWeight(stem[b], skeleton.y(n)) : trunkAtPivot[main[b]];
    }
}
