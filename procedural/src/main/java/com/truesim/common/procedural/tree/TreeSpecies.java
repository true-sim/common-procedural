package com.truesim.common.procedural.tree;

import com.truesim.common.procedural.tree.Branching.Shape;
import com.truesim.common.procedural.tree.Foliage.Kind;
import java.util.List;

/**
 * Everything that makes a species: size, trunk, how its branches grow, its leaves and bark. The presets are tuned
 * by eye after the species' habit; make variants with the record's {@code with}-style copy (all fields are values, so
 * equal species are equal, e.g. as an asset's recipe).
 *
 * @param name a name, e.g. for logs and recipes
 * @param growth how branches grow: {@link #levels()} for {@link Growth#PARAMETRIC}, {@link #crown()} for
 *     {@link Growth#SPACE_COLONIZATION}
 * @param height the mean height, metres
 * @param heightVariation the height's random variation, as a fraction
 * @param stems trunks from the ground: 1 for trees, more for shrubs
 * @param stemSpread for several stems, how far apart their bases are, metres
 * @param stemTilt for several stems, degrees they lean outward
 * @param lean how far a trunk leans, as a fraction of its length (kept small: the wind's pivots are on the y axis)
 * @param wobble degrees of random turning of a trunk per metre
 * @param baseRadius the first trunk's radius at the ground at the mean height, metres (others follow the pipe model)
 * @param rootFlare how much wider the trunk is at the very ground, as a fraction of its radius
 * @param pipeExponent the pipe model's exponent: 2 is da Vinci's rule, measured trees lie between 1.8 and 2.6
 * @param crownBase where the crown begins, as a fraction of the height
 * @param trunkSegment metres between trunk nodes
 * @param levels for parametric growth, the branching of levels 1, 2, ...: one entry per level
 * @param crown for space colonization, the crown
 * @param foliage the leaves or needles
 * @param barkTileWidth metres one bark texture tile covers around a stem (u)
 * @param barkTileLength metres one bark texture tile covers along a stem (v)
 * @param snowDroop how far branch tips sink under a full snow load, as a fraction of their length
 */
public record TreeSpecies(String name, Growth growth, double height, double heightVariation, int stems,
    double stemSpread, double stemTilt, double lean, double wobble, double baseRadius, double rootFlare,
    double pipeExponent, double crownBase, double trunkSegment, List<Branching> levels, Crown crown,
    Foliage foliage, double barkTileWidth, double barkTileLength, double snowDroop) {

    public TreeSpecies {
        levels = List.copyOf(levels);
        if (growth == Growth.SPACE_COLONIZATION && crown == null) {
            throw new IllegalArgumentException(name + ": space colonization needs a crown");
        }
    }

    private static Foliage atlas2x2(Kind kind, double length, double width, double spacing, int fromLevel,
        double tipFraction, double flutter, boolean deciduous) {
        return new Foliage(kind, length, width, spacing, fromLevel, tipFraction, flutter, deciduous, 2, 2, 0, 1, 2, 3);
    }

    /** Norway spruce (Picea abies): conical, whorled branches curving up at their tips, hanging side shoots. */
    public static final TreeSpecies SPRUCE = new TreeSpecies("spruce", Growth.PARAMETRIC, 28, 0.15, 1, 0, 0, 0.008,
        1.2, 0.36, 0.45, 2.3, 0.06, 0.4,
        List.of(
            new Branching(0.06, 0.97, 0.8, 4, 37, 96, 8, 0.27, 0.15, Shape.CONICAL, 28, 3, 0.12, 0, 0.5),
            new Branching(0.12, 0.96, 0.42, 2, 25, 58, 10, 0.36, 0.2, Shape.CONICAL, -5, 8, 0.35, 0.7, 0.3)),
        null, atlas2x2(Kind.NEEDLE_SPRAY, 0.36, 0.3, 0.32, 1, 0.8, 0.2, false), 0.55, 0.9, 0.25);

    /** Silver fir (Abies alba): a straighter, more cylindrical crown of flat, horizontal sprays. */
    public static final TreeSpecies FIR = new TreeSpecies("fir", Growth.PARAMETRIC, 30, 0.15, 1, 0, 0, 0.006,
        0.8, 0.4, 0.4, 2.3, 0.1, 0.4,
        List.of(
            new Branching(0.1, 0.97, 0.85, 4, 41, 88, 5, 0.2, 0.15, Shape.TAPERED_CYLINDRICAL, 12, 2, 0.05, 0, 0.5),
            new Branching(0.1, 0.96, 0.4, 2, 20, 62, 6, 0.38, 0.2, Shape.CONICAL, 0, 5, 0.06, 0.95, 0.3)),
        null, atlas2x2(Kind.NEEDLE_SPRAY, 0.34, 0.28, 0.3, 1, 0.85, 0.15, false), 0.6, 0.9, 0.3);

    /**
     * European larch (Larix decidua): an open cone of single branches in a spiral, with hanging twigs set with tufts
     * of short needles; golden in autumn and bare in winter.
     */
    public static final TreeSpecies LARCH = new TreeSpecies("larch", Growth.PARAMETRIC, 30, 0.15, 1, 0, 0, 0.012,
        1.5, 0.38, 0.35, 2.2, 0.22, 0.4,
        List.of(
            new Branching(0.22, 0.97, 0.22, 1, 137.5, 76, 12, 0.22, 0.2, Shape.CONICAL, 18, 5, 0.25, 0, 0.5),
            new Branching(0.12, 0.95, 0.32, 1, 140, 52, 10, 0.42, 0.25, Shape.CONICAL, -10, 8, 0.6, 0.2, 0.25)),
        null, atlas2x2(Kind.NEEDLE_TUFT, 0.3, 0.26, 0.24, 1, 0.75, 0.35, true), 0.5, 0.8, 0.15);

    /** Scots pine (Pinus sylvestris): a tall bare trunk under a flat, irregular crown, needles in tufts at the tips. */
    public static final TreeSpecies PINE = new TreeSpecies("pine", Growth.PARAMETRIC, 24, 0.15, 1, 0, 0, 0.015,
        1.2, 0.34, 0.3, 2.3, 0.6, 0.45,
        List.of(
            new Branching(0.6, 0.97, 0.6, 4, 40, 68, 10, 0.24, 0.2, Shape.HEMISPHERICAL, 32, 9, 0.05, 0, 0.5),
            new Branching(0.15, 0.95, 0.45, 2, 90, 45, 10, 0.45, 0.2, Shape.CONICAL, 22, 12, 0.05, 0.2, 0.35),
            new Branching(0.2, 0.95, 0.28, 2, 90, 40, 10, 0.42, 0.2, Shape.CONICAL, 18, 10, 0.03, 0, 0.22)),
        null, atlas2x2(Kind.NEEDLE_TUFT, 0.28, 0.24, 0.16, 2, 0.5, 0.25, false), 0.5, 0.8, 0.15);

    /** European beech (Fagus sylvatica): a broad dome of fine branching, grown by space colonization. */
    public static final TreeSpecies BEECH = new TreeSpecies("beech", Growth.SPACE_COLONIZATION, 25, 0.15, 1, 0, 0,
        0.01, 1, 0.45, 0.35, 2.2, 0.28, 0.4, List.of(),
        new Crown(Crown.Shape.ELLIPSOID, 0.36, 2400, 0.4, 11, 2.2, 0.12, 110),
        atlas2x2(Kind.LEAF_CLUSTER, 0.6, 0.5, 0.35, 1, 0.6, 0.8, true), 0.6, 0.8, 0.1);

    /** Silver birch (Betula pendula): slender, a narrow egg-shaped crown with hanging twigs. */
    public static final TreeSpecies BIRCH = new TreeSpecies("birch", Growth.SPACE_COLONIZATION, 20, 0.15, 1, 0, 0,
        0.015, 1.5, 0.24, 0.25, 2.2, 0.35, 0.35, List.of(),
        new Crown(Crown.Shape.OVOID, 0.22, 1600, 0.32, 10, 2, -0.3, 100),
        atlas2x2(Kind.LEAF_CLUSTER, 0.45, 0.38, 0.3, 1, 0.6, 1, true), 0.4, 0.6, 0.15);

    /** Hazel (Corylus avellana): a shrub of many stems from the ground under a dome of leaves. */
    public static final TreeSpecies HAZEL = new TreeSpecies("hazel", Growth.SPACE_COLONIZATION, 5, 0.2, 6, 0.6, 16,
        0.01, 3, 0.05, 0.1, 2.2, 0.3, 0.25, List.of(),
        new Crown(Crown.Shape.HEMISPHERE, 0.6, 1100, 0.22, 10, 2, 0.25, 80),
        atlas2x2(Kind.LEAF_CLUSTER, 0.42, 0.36, 0.25, 1, 0.6, 0.8, true), 0.25, 0.5, 0.2);

    /** Mountain pine (Pinus mugo): the Alpine dwarf pine, stems creeping and rising, needle tufts at their ends. */
    public static final TreeSpecies MUGO = new TreeSpecies("mugo", Growth.PARAMETRIC, 2.5, 0.2, 7, 0.5, 48, 0.02, 6,
        0.06, 0.15, 2.3, 0.2, 0.2,
        List.of(
            new Branching(0.2, 0.95, 0.3, 3, 50, 50, 12, 0.32, 0.2, Shape.TAPERED_CYLINDRICAL, 35, 12, 0.05, 0, 0.18),
            new Branching(0.2, 0.95, 0.22, 2, 90, 42, 10, 0.42, 0.2, Shape.CONICAL, 20, 12, 0.03, 0.2, 0.12)),
        null, atlas2x2(Kind.NEEDLE_TUFT, 0.22, 0.2, 0.12, 1, 0.5, 0.2, false), 0.2, 0.4, 0.1);

    /** Every preset: trees, then shrubs. */
    public static final List<TreeSpecies> PRESETS = List.of(SPRUCE, FIR, LARCH, PINE, BEECH, BIRCH, HAZEL, MUGO);

    /** The deepest branch level of parametric growth. */
    public int maxLevel() {
        return levels.size();
    }
}
