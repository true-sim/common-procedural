package com.truesim.common.procedural.tree;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import java.util.Arrays;

/**
 * A tree's branching structure: nodes (points on branch axes, with a radius) and branches (runs of consecutive nodes),
 * plus the leaf cards hanging from nodes. Every node's parent comes before it: the previous node of its branch, or for
 * a branch's first node the node of the parent branch it starts at (the first node lies there, on the parent's axis).
 * Level 0 is a trunk (a shrub has several), level 1 its branches, and so on.
 *
 * <p>Radii follow the pipe model (Shinozaki et al. 1964; da Vinci's rule): every node carries the pipes of everything
 * above it, {@code r^e = sum of its children's r^e + its leaves' pipes}, with {@link #pipeExponent() e} the species'
 * exponent and each leaf card {@link #leafPipe() one pipe} of cross section {@code leafPipe} (in units of
 * {@code r^e}). Radii only change where something branches off or a leaf hangs.
 *
 * <p>Filled by the growth methods and read by the mesher; reused from tree to tree ({@link #clear}).
 */
public final class Skeleton {

    // Nodes
    private double[] x = new double[1024];
    private double[] y = new double[1024];
    private double[] z = new double[1024];
    private double[] radius = new double[1024];
    private int[] parent = new int[1024];
    private int[] branchOf = new int[1024];
    private double[] leaves = new double[1024]; // leaf pipes hanging at each node
    private int nodeCount;

    // Branches
    private int[] first = new int[256];
    private int[] count = new int[256];
    private int[] level = new int[256];
    private int[] parentBranch = new int[256];
    private int branchCount;

    // Leaf cards: attachment node, origin, direction along the card (unit), its normal (unit), length and width
    private int[] leafNode = new int[1024];
    private double[] leafData = new double[11 * 1024];
    private int leafCount;

    private double pipeExponent = 2;
    private double leafPipe = 1;

    /** Empties the skeleton, keeping its memory. */
    public void clear() {
        nodeCount = 0;
        branchCount = 0;
        leafCount = 0;
    }

    /** Starts a branch of {@code level} off {@code parentBranch} (-1 for a trunk); returns its index. */
    @AllowAllocation("growing, until warm")
    public int beginBranch(int level, int parentBranch) {
        if (branchCount == first.length) {
            int capacity = 2 * branchCount;
            first = Arrays.copyOf(first, capacity);
            count = Arrays.copyOf(count, capacity);
            this.level = Arrays.copyOf(this.level, capacity);
            this.parentBranch = Arrays.copyOf(this.parentBranch, capacity);
        }
        first[branchCount] = nodeCount;
        count[branchCount] = 0;
        this.level[branchCount] = level;
        this.parentBranch[branchCount] = parentBranch;
        return branchCount++;
    }

    /**
     * Adds a node to {@code branch} (which must be the branch begun last) at (x, y, z), its parent {@code parentNode}
     * (-1 for a trunk's root). Returns its index.
     */
    @AllowAllocation("growing, until warm")
    public int addNode(int branch, double px, double py, double pz, int parentNode) {
        if (nodeCount == x.length) {
            int capacity = 2 * nodeCount;
            x = Arrays.copyOf(x, capacity);
            y = Arrays.copyOf(y, capacity);
            z = Arrays.copyOf(z, capacity);
            radius = Arrays.copyOf(radius, capacity);
            parent = Arrays.copyOf(parent, capacity);
            branchOf = Arrays.copyOf(branchOf, capacity);
            leaves = Arrays.copyOf(leaves, capacity);
        }
        x[nodeCount] = px;
        y[nodeCount] = py;
        z[nodeCount] = pz;
        radius[nodeCount] = 0;
        parent[nodeCount] = parentNode;
        branchOf[nodeCount] = branch;
        leaves[nodeCount] = 0;
        count[branch]++;
        return nodeCount++;
    }

    /**
     * Hangs a leaf card on {@code node}: from (ox, oy, oz) along the unit direction (dx, dy, dz) for {@code length},
     * {@code width} across, facing the unit normal (nx, ny, nz); it adds {@code pipes} leaf pipes to the node.
     */
    @AllowAllocation("growing, until warm")
    public void addLeaf(int node, double ox, double oy, double oz, double dx, double dy, double dz, double nx,
        double ny, double nz, double length, double width, double pipes) {
        if (leafCount == leafNode.length) {
            leafNode = Arrays.copyOf(leafNode, 2 * leafCount);
            leafData = Arrays.copyOf(leafData, 11 * 2 * leafCount);
        }
        leafNode[leafCount] = node;
        int o = 11 * leafCount;
        leafData[o] = ox;
        leafData[o + 1] = oy;
        leafData[o + 2] = oz;
        leafData[o + 3] = dx;
        leafData[o + 4] = dy;
        leafData[o + 5] = dz;
        leafData[o + 6] = nx;
        leafData[o + 7] = ny;
        leafData[o + 8] = nz;
        leafData[o + 9] = length;
        leafData[o + 10] = width;
        leaves[node] += pipes;
        leafCount++;
    }

    /**
     * Gives every node its pipe-model radius: tips without leaves carry one pipe, each leaf its pipes, and each node
     * the sum of what it carries; then all radii scale so the first trunk's root has {@code baseRadius} (scaling keeps
     * the pipe model, with {@link #leafPipe()} scaled alike).
     */
    public void pipeRadii(double exponent, double baseRadius) {
        pipeExponent = exponent;
        // r^e accumulated from the tips down: every parent comes before its children
        for (int n = 0; n < nodeCount; n++) {
            radius[n] = leaves[n];
        }
        for (int n = nodeCount - 1; n >= 0; n--) {
            if (radius[n] == 0) {
                radius[n] = 1; // a bare tip: one pipe
                leaves[n] = 1;
            }
            if (parent[n] >= 0) {
                radius[parent[n]] += radius[n];
            }
        }
        double scale = nodeCount == 0 ? 1 : baseRadius / Maths.pow(radius[0], 1 / exponent);
        double pipeScale = Maths.pow(scale, exponent);
        for (int n = 0; n < nodeCount; n++) {
            radius[n] = Maths.pow(radius[n], 1 / exponent) * scale;
            leaves[n] *= pipeScale;
        }
        leafPipe = pipeScale;
    }

    /** The exponent of the pipe model the radii follow. */
    public double pipeExponent() {
        return pipeExponent;
    }

    /** The cross section ({@code r^e}) one leaf pipe adds, after scaling. */
    public double leafPipe() {
        return leafPipe;
    }

    public int nodeCount() {
        return nodeCount;
    }

    public double x(int node) {
        return x[node];
    }

    public double y(int node) {
        return y[node];
    }

    public double z(int node) {
        return z[node];
    }

    /** Moves a node. */
    public void move(int node, double px, double py, double pz) {
        x[node] = px;
        y[node] = py;
        z[node] = pz;
    }

    public double radius(int node) {
        return radius[node];
    }

    /** The node's parent: the previous node of its branch, or where its branch starts on the parent; -1 at a root. */
    public int parent(int node) {
        return parent[node];
    }

    public int branchOf(int node) {
        return branchOf[node];
    }

    /** The leaf pipes hanging at the node ({@code r^e} units after {@link #pipeRadii}; a bare tip has one). */
    public double leafPipes(int node) {
        return leaves[node];
    }

    public int branchCount() {
        return branchCount;
    }

    /** The branch's first node, which lies on its parent's axis (a trunk's at its root). */
    public int firstNode(int branch) {
        return first[branch];
    }

    public int nodeCount(int branch) {
        return count[branch];
    }

    public int level(int branch) {
        return level[branch];
    }

    public int parentBranch(int branch) {
        return parentBranch[branch];
    }

    public int leafCount() {
        return leafCount;
    }

    public int leafNode(int leaf) {
        return leafNode[leaf];
    }

    /** Component {@code k} of leaf card {@code leaf}: 0-2 origin, 3-5 direction, 6-8 normal, 9 length, 10 width. */
    public double leaf(int leaf, int k) {
        return leafData[11 * leaf + k];
    }
}
