package com.truesim.common.procedural.plant;

/**
 * How one level of detail of a small plant is meshed: the shapes' detail, how many of its leaves and blades it keeps
 * (the kept ones a little wider), and the triangle budget it must keep. Over budget, it keeps fewer.
 *
 * @param detail 0 full (petals, sepals, curved leaves), 1 simplified, 2 minimal (a leaf a triangle or two)
 * @param keep keep one leaf or blade in this many
 * @param triangleBudget the most triangles the level may have
 */
public record PlantLod(int detail, int keep, int triangleBudget) {

    public PlantLod {
        if (detail < 0 || detail > 2 || keep < 1 || triangleBudget < 1) {
            throw new IllegalArgumentException("Not a level of detail: " + detail + ", " + keep + ", "
                + triangleBudget);
        }
    }

    /** Full detail: a few thousand triangles. */
    public static final PlantLod FULL = new PlantLod(0, 1, 6000);
    /** Medium: simpler shapes, half the leaves. */
    public static final PlantLod MEDIUM = new PlantLod(1, 2, 1500);
    /** Far: minimal shapes, a quarter of the leaves. */
    public static final PlantLod LOW = new PlantLod(2, 4, 500);
}
