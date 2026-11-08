package com.truesim.common.procedural.tree;

/**
 * How one level of detail of a tree is meshed: the simplifications that carry trees well (fewer sides per branch,
 * fewer rings, thin branches left to the leaves, fewer but larger leaf cards), and the triangle budget the level must
 * keep. Over budget, the level drops more leaf cards and branches until it fits.
 *
 * @param trunkSides sides around a trunk
 * @param branchSides sides around a main branch (level 1)
 * @param twigSides sides around finer branches
 * @param minRadius branches thinner than this at their base, metres, are not meshed
 * @param ringStride a ring at every this many nodes along a branch (the first and last always; one more for twigs)
 * @param leafKeep keep one leaf card in this many, the kept ones scaled up to cover as much
 * @param triangleBudget the most triangles the level may have
 */
public record TreeLod(int trunkSides, int branchSides, int twigSides, double minRadius, int ringStride, int leafKeep,
    int triangleBudget) {

    public TreeLod {
        if (trunkSides < 3 || branchSides < 3 || twigSides < 3 || ringStride < 1 || leafKeep < 1
            || triangleBudget < 1) {
            throw new IllegalArgumentException("Not a level of detail: " + trunkSides + ", " + branchSides + ", "
                + twigSides + ", " + ringStride + ", " + leafKeep + ", " + triangleBudget);
        }
    }

    /** Full detail. */
    public static final TreeLod FULL = new TreeLod(10, 6, 3, 0.008, 1, 1, 90_000);
    /** Half distance: thin twigs left to the leaves, half the cards. */
    public static final TreeLod MEDIUM = new TreeLod(7, 4, 3, 0.025, 2, 2, 25_000);
    /** Far: main branches only, a sixth of the cards. */
    public static final TreeLod LOW = new TreeLod(5, 3, 3, 0.07, 3, 6, 8_000);

    /** Sides around a branch of {@code level}. */
    public int sides(int level) {
        return level == 0 ? trunkSides : level == 1 ? branchSides : twigSides;
    }
}
