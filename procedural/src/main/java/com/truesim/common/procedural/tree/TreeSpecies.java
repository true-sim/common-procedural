package com.truesim.common.procedural.tree;

import com.truesim.common.procedural.flora.Albedo;
import com.truesim.common.procedural.flora.FlowerShape;
import com.truesim.common.procedural.flora.FlowerShape.Form;
import com.truesim.common.procedural.flora.FlowerShape.Tip;
import com.truesim.common.procedural.flora.FruitShape;
import com.truesim.common.procedural.flora.Fruiting;
import com.truesim.common.procedural.flora.Fruiting.Placement;
import com.truesim.common.procedural.flora.Inflorescence;
import com.truesim.common.procedural.flora.Inflorescence.Arrangement;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.flora.Phenology;
import com.truesim.common.procedural.flora.Ramp;
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
 * @param fruiting the flowers, fruits or cones it bears, or {@code null} for none
 */
public record TreeSpecies(String name, Growth growth, double height, double heightVariation, int stems,
    double stemSpread, double stemTilt, double lean, double wobble, double baseRadius, double rootFlare,
    double pipeExponent, double crownBase, double trunkSegment, List<Branching> levels, Crown crown,
    Foliage foliage, double barkTileWidth, double barkTileLength, double snowDroop, Fruiting fruiting) {

    public TreeSpecies {
        levels = List.copyOf(levels);
        if (growth == Growth.SPACE_COLONIZATION && crown == null) {
            throw new IllegalArgumentException(name + ": space colonization needs a crown");
        }
    }

    /** A species bearing nothing showy. */
    public TreeSpecies(String name, Growth growth, double height, double heightVariation, int stems,
        double stemSpread, double stemTilt, double lean, double wobble, double baseRadius, double rootFlare,
        double pipeExponent, double crownBase, double trunkSegment, List<Branching> levels, Crown crown,
        Foliage foliage, double barkTileWidth, double barkTileLength, double snowDroop) {
        this(name, growth, height, heightVariation, stems, stemSpread, stemTilt, lean, wobble, baseRadius, rootFlare,
            pipeExponent, crownBase, trunkSegment, levels, crown, foliage, barkTileWidth, barkTileLength, snowDroop,
            null);
    }

    /** This species bearing {@code value} (or nothing, for {@code null}). */
    public TreeSpecies withFruiting(Fruiting value) {
        return new TreeSpecies(name, growth, height, heightVariation, stems, stemSpread, stemTilt, lean, wobble,
            baseRadius, rootFlare, pipeExponent, crownBase, trunkSegment, levels, crown, foliage, barkTileWidth,
            barkTileLength, snowDroop, value);
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
        null, atlas2x2(Kind.NEEDLE_SPRAY, 0.36, 0.3, 0.32, 1, 0.8, 0.2, false), 0.55, 0.9, 0.25,
        // Hanging cones, 10 to 15 cm, high in the crown: red-purple in May, ripe brown in autumn, shed in spring
        cones(Placement.TIPS, 1, 0.1, 0, 0.7, 0, 0.017, 3.5, 13, 1, new Phenology(0, 130, 150, 12, 270, 290, 25, 395,
            515), new Ramp(a(0.3, 0.04, 0.08), a(0.12, 0.14, 0.04), a(0.2, 0.1, 0.05))));

    /** Silver fir (Abies alba): a straighter, more cylindrical crown of flat, horizontal sprays. */
    public static final TreeSpecies FIR = new TreeSpecies("fir", Growth.PARAMETRIC, 30, 0.15, 1, 0, 0, 0.006,
        0.8, 0.4, 0.4, 2.3, 0.1, 0.4,
        List.of(
            new Branching(0.1, 0.97, 0.85, 4, 41, 88, 5, 0.2, 0.15, Shape.TAPERED_CYLINDRICAL, 12, 2, 0.05, 0, 0.5),
            new Branching(0.1, 0.96, 0.4, 2, 20, 62, 6, 0.38, 0.2, Shape.CONICAL, 0, 5, 0.06, 0.95, 0.3)),
        null, atlas2x2(Kind.NEEDLE_SPRAY, 0.34, 0.28, 0.3, 1, 0.85, 0.15, false), 0.6, 0.9, 0.3,
        // Upright cones at the very top, falling apart on the tree in autumn
        cones(Placement.TIPS, 1, 0.12, 0, 0.82, 1.5, 0.022, 2.5, 11, 0, new Phenology(0, 130, 150, 12, 250, 270, 20,
            280, 320), new Ramp(a(0.16, 0.18, 0.06), a(0.12, 0.14, 0.05), a(0.18, 0.11, 0.06))));

    /**
     * European larch (Larix decidua): an open cone of single branches in a spiral, with hanging twigs set with tufts
     * of short needles; golden in autumn and bare in winter.
     */
    public static final TreeSpecies LARCH = new TreeSpecies("larch", Growth.PARAMETRIC, 30, 0.15, 1, 0, 0, 0.012,
        1.5, 0.38, 0.35, 2.2, 0.22, 0.4,
        List.of(
            new Branching(0.22, 0.97, 0.22, 1, 137.5, 76, 12, 0.22, 0.2, Shape.CONICAL, 18, 5, 0.25, 0, 0.5),
            new Branching(0.12, 0.95, 0.32, 1, 140, 52, 10, 0.42, 0.25, Shape.CONICAL, -10, 8, 0.6, 0.2, 0.25)),
        null, atlas2x2(Kind.NEEDLE_TUFT, 0.3, 0.26, 0.24, 1, 0.75, 0.35, true), 0.5, 0.8, 0.15,
        // Small upright cones along the twigs: pink "larch roses" in April, brown, kept for years
        cones(Placement.ALONG, 2, 0.2, 0.8, 0.2, 1.2, 0.012, 1.5, 8, 0, new Phenology(0, 100, 125, 12, 250, 275, 20,
            425, 650), new Ramp(a(0.45, 0.08, 0.12), a(0.14, 0.15, 0.05), a(0.2, 0.11, 0.06))));

    /** Scots pine (Pinus sylvestris): a tall bare trunk under a flat, irregular crown, needles in tufts at the tips. */
    public static final TreeSpecies PINE = new TreeSpecies("pine", Growth.PARAMETRIC, 24, 0.15, 1, 0, 0, 0.015,
        1.2, 0.34, 0.3, 2.3, 0.6, 0.45,
        List.of(
            new Branching(0.6, 0.97, 0.6, 4, 40, 68, 10, 0.24, 0.2, Shape.HEMISPHERICAL, 32, 9, 0.05, 0, 0.5),
            new Branching(0.15, 0.95, 0.45, 2, 90, 45, 10, 0.45, 0.2, Shape.CONICAL, 22, 12, 0.05, 0.2, 0.35),
            new Branching(0.2, 0.95, 0.28, 2, 90, 40, 10, 0.42, 0.2, Shape.CONICAL, 18, 10, 0.03, 0, 0.22)),
        null, atlas2x2(Kind.NEEDLE_TUFT, 0.28, 0.24, 0.16, 2, 0.5, 0.25, false), 0.5, 0.8, 0.15,
        cones(Placement.TIPS, 2, 0.02, 0, 0.6, 0, 0.018, 1.9, 10, 0.6, new Phenology(0, 140, 160, 10, 260, 290, 30,
            425, 515), new Ramp(a(0.3, 0.12, 0.12), a(0.14, 0.15, 0.06), a(0.2, 0.13, 0.08))));

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
        atlas2x2(Kind.LEAF_CLUSTER, 0.42, 0.36, 0.25, 1, 0.6, 0.8, true), 0.25, 0.5, 0.2,
        // Nuts in husks, one to four on short shoots: tiny red flowers in March, ripe brown in September
        new Fruiting(Placement.ALONG, 1, 0.6, 0.6, 0.25, new Inflorescence(Arrangement.CLUSTER, 1, 4, 0.5, 0.008,
            0.006, 0.3, 0, 0.4, 1), null, new FruitShape(FruitShape.Form.BERRY, 0.009, 1.15, 0.12, 1.0, 0, 0, 0.2),
            new Phenology(0, 60, 90, 20, 230, 250, 20, 255, 290), 0.6, 0,
            Palette.DEFAULT.with(Part.CONE, new Ramp(a(0.35, 0.05, 0.06), a(0.18, 0.22, 0.06), a(0.25, 0.13, 0.06)))
                .with(Part.SEPAL, new Ramp(a(0.14, 0.2, 0.05), a(0.14, 0.2, 0.05), a(0.2, 0.14, 0.06)))));

    /** Mountain pine (Pinus mugo): the Alpine dwarf pine, stems creeping and rising, needle tufts at their ends. */
    public static final TreeSpecies MUGO = new TreeSpecies("mugo", Growth.PARAMETRIC, 2.5, 0.2, 7, 0.5, 48, 0.02, 6,
        0.06, 0.15, 2.3, 0.2, 0.2,
        List.of(
            new Branching(0.2, 0.95, 0.3, 3, 50, 50, 12, 0.32, 0.2, Shape.TAPERED_CYLINDRICAL, 35, 12, 0.05, 0, 0.18),
            new Branching(0.2, 0.95, 0.22, 2, 90, 42, 10, 0.42, 0.2, Shape.CONICAL, 20, 12, 0.03, 0.2, 0.12)),
        null, atlas2x2(Kind.NEEDLE_TUFT, 0.22, 0.2, 0.12, 1, 0.5, 0.2, false), 0.2, 0.4, 0.1,
        cones(Placement.TIPS, 1, 0.06, 0, 0, 0.3, 0.014, 1.6, 9, 0.3, new Phenology(0, 150, 170, 10, 260, 290, 30,
            425, 515), new Ramp(a(0.3, 0.1, 0.14), a(0.14, 0.15, 0.06), a(0.2, 0.13, 0.08))));

    // Flowering and fruiting species of the Alpine valley. Albedos are linear sRGB, set from typical published
    // reflectance spectra: white petals reflect about 0.6 to 0.8 across the visible, colored ones little outside their
    // hue (the Floral Reflectance Database, Arnold et al. 2010; Chittka et al. 1994); ripe red fruits about 0.4 to 0.5
    // in the red and under 0.06 elsewhere, black ones under 0.05, blue-black ones with a waxy bloom about 0.1 in the
    // blue (Schaefer et al., "The role of chromatic and achromatic signals for fruit detection by birds", Behav. Ecol.
    // 2006); unripe fruits leaf-like (Jacquemoud and Baret 1990). They are approximations for rendering, not
    // measurements of these species.

    /**
     * Rowan (Sorbus aucuparia): a small tree with flat corymbs of creamy white flowers in June and bunches of scarlet
     * berries from August, hanging under their weight, eaten by birds into the winter.
     */
    public static final TreeSpecies ROWAN = new TreeSpecies("rowan", Growth.SPACE_COLONIZATION, 9, 0.2, 1, 0, 0,
        0.012, 1.5, 0.16, 0.25, 2.2, 0.3, 0.3, List.of(),
        new Crown(Crown.Shape.OVOID, 0.3, 1300, 0.26, 10, 2, 0.2, 100),
        atlas2x2(Kind.LEAF_CLUSTER, 0.34, 0.26, 0.24, 1, 0.6, 0.8, true), 0.35, 0.5, 0.12,
        new Fruiting(Placement.ALONG, 1, 5, 0.5, 0.3,
            new Inflorescence(Arrangement.CORYMB, 12, 16, 0.05, 0.1, 0.012, 1.0, 0, 1.6, 10),
            new FlowerShape(Form.SIMPLE, 5, 0.0045, 0.0045, Tip.ROUND, 0.05, 0, 5, 0.0015, 0.0018, 0.0012, 0),
            new FruitShape(FruitShape.Form.BERRY, 0.0048, 0.95, 0.1, 0.35, 0, 0, 0.2),
            new Phenology(14, 145, 175, 10, 215, 240, 18, 275, 400), 0.3, 0.06,
            Palette.DEFAULT
                .with(Part.PETAL, new Ramp(a(0.42, 0.46, 0.3), a(0.72, 0.7, 0.6), a(0.4, 0.35, 0.24)))
                .with(Part.CENTRE, Ramp.of(a(0.62, 0.55, 0.36)))
                .with(Part.STALK, Ramp.of(a(0.08, 0.13, 0.03), a(0.26, 0.08, 0.04)))
                .with(Part.FRUIT, new Ramp(a(0.11, 0.17, 0.04), a(0.55, 0.22, 0.02), a(0.5, 0.05, 0.02)))
                .with(Part.FRUIT_ALT, new Ramp(a(0.11, 0.17, 0.04), a(0.55, 0.22, 0.02), a(0.5, 0.05, 0.02)))));

    /**
     * Apple (Malus domestica): a rounded orchard tree, pink buds opening to white blossom in late April and May on
     * spurs along the branches, then apples, red or green by the tree's cultivar, bowing the boughs by September.
     */
    public static final TreeSpecies APPLE = new TreeSpecies("apple", Growth.SPACE_COLONIZATION, 6, 0.15, 1, 0, 0,
        0.015, 2, 0.15, 0.25, 2.2, 0.3, 0.3, List.of(),
        new Crown(Crown.Shape.ELLIPSOID, 0.5, 1500, 0.25, 10, 2, 0.05, 100),
        atlas2x2(Kind.LEAF_CLUSTER, 0.36, 0.3, 0.24, 1, 0.6, 0.8, true), 0.35, 0.5, 0.12,
        new Fruiting(Placement.ALONG, 1, 3.6, 0.8, 0.25,
            new Inflorescence(Arrangement.CLUSTER, 4, 6, 0.6, 0.01, 0.025, 0.6, 0, 0, 1),
            new FlowerShape(Form.SIMPLE, 5, 0.018, 0.016, Tip.ROUND, 0.35, 0, 5, 0.007, 0.004, 0.004, 0),
            new FruitShape(FruitShape.Form.POME, 0.036, 0.88, 0.15, 0.18, 0, 0.25, 0.9),
            new Phenology(12, 118, 142, 7, 230, 250, 25, 262, 295), 0.2, 0.12,
            Palette.DEFAULT
                .with(Part.PETAL, new Ramp(a(0.5, 0.12, 0.18), a(0.72, 0.62, 0.62), a(0.6, 0.55, 0.5)))
                .with(Part.CENTRE, Ramp.of(a(0.65, 0.55, 0.2)))
                .with(Part.FRUIT, new Ramp(a(0.12, 0.2, 0.04), a(0.35, 0.2, 0.04), a(0.32, 0.03, 0.03)))
                .with(Part.FRUIT_ALT, new Ramp(a(0.12, 0.2, 0.04), a(0.2, 0.3, 0.05), a(0.32, 0.38, 0.08)))));

    /**
     * Elder (Sambucus nigra): a shrub of several stems, broad flat heads of tiny creamy flowers in June, turning
     * into hanging heads of glossy black berries on purple-red stalks in late summer.
     */
    public static final TreeSpecies ELDER = new TreeSpecies("elder", Growth.SPACE_COLONIZATION, 5, 0.2, 4, 0.4, 15,
        0.01, 3, 0.07, 0.15, 2.2, 0.25, 0.25, List.of(),
        new Crown(Crown.Shape.ELLIPSOID, 0.48, 1100, 0.22, 10, 2, 0.05, 90),
        atlas2x2(Kind.LEAF_CLUSTER, 0.36, 0.3, 0.22, 1, 0.6, 0.8, true), 0.3, 0.5, 0.15,
        new Fruiting(Placement.ALONG, 1, 4, 0.5, 0.3,
            new Inflorescence(Arrangement.UMBEL, 12, 16, 0.07, 0.1, 0.04, 1.1, 0, 2.2, 18),
            new FlowerShape(Form.SIMPLE, 5, 0.0025, 0.0022, Tip.ROUND, 0, 0, 0, 0.001, 0.0012, 0.0008, 0),
            new FruitShape(FruitShape.Form.BERRY, 0.003, 1, 0.1, 0, 0, 0, 0.3),
            new Phenology(12, 155, 185, 6, 225, 245, 15, 255, 290), 0.35, 0.05,
            Palette.DEFAULT
                .with(Part.PETAL, new Ramp(a(0.5, 0.52, 0.38), a(0.72, 0.7, 0.6), a(0.45, 0.42, 0.3)))
                .with(Part.CENTRE, Ramp.of(a(0.7, 0.6, 0.25)))
                .with(Part.STALK, Ramp.of(a(0.08, 0.13, 0.03), a(0.2, 0.04, 0.08)))
                .with(Part.FRUIT, new Ramp(a(0.1, 0.15, 0.04), a(0.25, 0.04, 0.04), a(0.025, 0.015, 0.03)))
                .with(Part.FRUIT_ALT, new Ramp(a(0.1, 0.15, 0.04), a(0.25, 0.04, 0.04), a(0.025, 0.015, 0.03)))));

    /**
     * Dog rose (Rosa canina): arching canes, pale pink five-petalled flowers with yellow stamens in June, and oval
     * scarlet hips that hang on through the winter.
     */
    public static final TreeSpecies DOG_ROSE = new TreeSpecies("dogrose", Growth.SPACE_COLONIZATION, 2.2, 0.2, 6,
        0.35, 30, 0.02, 4, 0.025, 0.1, 2.2, 0.25, 0.2, List.of(),
        new Crown(Crown.Shape.HEMISPHERE, 0.6, 900, 0.14, 10, 2, -0.15, 80),
        atlas2x2(Kind.LEAF_CLUSTER, 0.2, 0.16, 0.15, 1, 0.6, 0.7, true), 0.15, 0.4, 0.2,
        new Fruiting(Placement.TIPS, 1, 0.4, 0, 0.2,
            new Inflorescence(Arrangement.CLUSTER, 1, 3, 0.45, 0, 0.02, 0.5, 0, 0.6, 1),
            new FlowerShape(Form.SIMPLE, 5, 0.022, 0.024, Tip.NOTCHED, 0.2, 0, 5, 0.018, 0.006, 0.003, 0),
            new FruitShape(FruitShape.Form.BERRY, 0.008, 1.4, 0.15, 0.5, 0, 0, 0.4),
            new Phenology(12, 160, 185, 4, 245, 270, 20, 330, 440), 0.8, 0.02,
            Palette.DEFAULT
                .with(Part.PETAL, new Ramp(a(0.45, 0.12, 0.2), a(0.7, 0.42, 0.48), a(0.62, 0.5, 0.48)))
                .with(Part.CENTRE, Ramp.of(a(0.7, 0.5, 0.06)))
                .with(Part.FRUIT, new Ramp(a(0.11, 0.17, 0.04), a(0.5, 0.2, 0.02), a(0.45, 0.04, 0.02)))
                .with(Part.FRUIT_ALT, new Ramp(a(0.11, 0.17, 0.04), a(0.5, 0.2, 0.02), a(0.45, 0.04, 0.02)))));

    /**
     * Alpenrose (Rhododendron ferrugineum): an evergreen dwarf shrub of the subalpine belt, trusses of pink-red funnel
     * flowers in late June and July, brown seed capsules after.
     */
    public static final TreeSpecies ALPENROSE = new TreeSpecies("alpenrose", Growth.SPACE_COLONIZATION, 0.8, 0.2, 8,
        0.22, 35, 0.02, 6, 0.012, 0.1, 2.2, 0.15, 0.08, List.of(),
        new Crown(Crown.Shape.HEMISPHERE, 0.8, 700, 0.06, 10, 2, 0.1, 80),
        atlas2x2(Kind.LEAF_CLUSTER, 0.09, 0.08, 0.05, 1, 0.5, 0.5, false), 0.06, 0.2, 0.3,
        new Fruiting(Placement.TIPS, 1, 0.5, 0, 0.3,
            new Inflorescence(Arrangement.CLUSTER, 6, 11, 0.75, 0.005, 0.012, 0.8, 0.15, 0.2, 1),
            new FlowerShape(Form.BELL, 5, 0.014, 0.008, Tip.ROUND, 0.9, 0, 0, 0.002, 0, 0, 0),
            new FruitShape(FruitShape.Form.BERRY, 0.0028, 1.6, 0.1, 0, 0, 0, 0),
            new Phenology(12, 165, 195, 9, 240, 260, 20, 330, 480), 0.5, 0,
            Palette.DEFAULT
                .with(Part.PETAL, new Ramp(a(0.35, 0.04, 0.1), a(0.5, 0.08, 0.18), a(0.35, 0.12, 0.15)))
                .with(Part.FRUIT, new Ramp(a(0.12, 0.15, 0.05), a(0.16, 0.09, 0.05), a(0.1, 0.06, 0.035)))
                .with(Part.FRUIT_ALT, new Ramp(a(0.12, 0.15, 0.05), a(0.16, 0.09, 0.05), a(0.1, 0.06, 0.035)))));

    /**
     * Blueberry (Vaccinium myrtillus): a knee-high shrub of green twigs, nodding pinkish urn flowers in May, and
     * blue-black berries with a pale bloom in July.
     */
    public static final TreeSpecies BLUEBERRY = new TreeSpecies("blueberry", Growth.SPACE_COLONIZATION, 0.35, 0.2, 8,
        0.12, 22, 0.02, 8, 0.004, 0.05, 2.2, 0.15, 0.04, List.of(),
        new Crown(Crown.Shape.HEMISPHERE, 0.9, 450, 0.025, 10, 2, 0.2, 70),
        atlas2x2(Kind.LEAF_CLUSTER, 0.05, 0.045, 0.028, 1, 0.8, 0.6, true), 0.03, 0.1, 0.3,
        new Fruiting(Placement.ALONG, 1, 9, 0.8, 0.2,
            Inflorescence.solitary(0.006, 0, 1.2, 0),
            new FlowerShape(Form.BELL, 5, 0.006, 0.0028, Tip.ROUND, 0.1, 0.5, 0, 0.001, 0, 0, 0),
            new FruitShape(FruitShape.Form.BERRY, 0.004, 0.95, 0.12, 0.6, 0, 0, 0.5),
            new Phenology(10, 130, 160, 8, 195, 215, 10, 235, 265), 0.6, 0,
            Palette.DEFAULT
                .with(Part.PETAL, new Ramp(a(0.35, 0.3, 0.2), a(0.55, 0.38, 0.35), a(0.45, 0.35, 0.28)))
                .with(Part.STALK, Ramp.of(a(0.09, 0.15, 0.04)))
                .with(Part.FRUIT, new Ramp(a(0.12, 0.16, 0.05), a(0.2, 0.05, 0.08), a(0.06, 0.07, 0.13)))
                .with(Part.FRUIT_ALT, new Ramp(a(0.12, 0.16, 0.05), a(0.2, 0.05, 0.08), a(0.06, 0.07, 0.13)))));

    /**
     * Lingonberry (Vaccinium vitis-idaea): an evergreen carpet a hand high, short racemes of nodding white-pink bells
     * in June, red berries from August that last under the snow until spring.
     */
    public static final TreeSpecies LINGONBERRY = new TreeSpecies("lingonberry", Growth.SPACE_COLONIZATION, 0.2,
        0.2, 10, 0.12, 30, 0.02, 8, 0.003, 0.05, 2.2, 0.15, 0.03, List.of(),
        new Crown(Crown.Shape.HEMISPHERE, 1.1, 350, 0.02, 10, 2, 0.1, 60),
        atlas2x2(Kind.LEAF_CLUSTER, 0.04, 0.036, 0.022, 1, 0.8, 0.4, false), 0.03, 0.1, 0.3,
        new Fruiting(Placement.TIPS, 1, 0.5, 0, 0.2,
            new Inflorescence(Arrangement.RACEME, 3, 7, 0.02, 0.005, 0.004, 0.3, 1.4, 0.6, 1),
            new FlowerShape(Form.BELL, 4, 0.006, 0.0035, Tip.ROUND, 0.5, 0, 0, 0.001, 0, 0, 0),
            new FruitShape(FruitShape.Form.BERRY, 0.0042, 1, 0.12, 0.25, 0, 0, 0.6),
            new Phenology(12, 155, 180, 7, 225, 245, 14, 300, 470), 0.6, 0,
            Palette.DEFAULT
                .with(Part.PETAL, new Ramp(a(0.5, 0.2, 0.22), a(0.68, 0.55, 0.55), a(0.55, 0.45, 0.42)))
                .with(Part.FRUIT, new Ramp(a(0.35, 0.4, 0.25), a(0.5, 0.25, 0.12), a(0.4, 0.03, 0.03)))
                .with(Part.FRUIT_ALT, new Ramp(a(0.35, 0.4, 0.25), a(0.5, 0.25, 0.12), a(0.4, 0.03, 0.03)))));

    /** Every preset: trees, then shrubs. */
    public static final List<TreeSpecies> PRESETS = List.of(SPRUCE, FIR, LARCH, PINE, BEECH, BIRCH, ROWAN, APPLE,
        HAZEL, MUGO, ELDER, DOG_ROSE, ALPENROSE, BLUEBERRY, LINGONBERRY);

    private static Albedo a(double r, double g, double b) {
        return new Albedo(r, g, b);
    }

    /** Cones, one at a site, no showy flower: young cones in their first weeks. */
    private static Fruiting cones(Placement placement, int fromLevel, double density, double tipFraction,
        double minHeight, double erect, double radius, double elongation, int scales, double hang, Phenology phenology,
        Ramp colors) {
        return new Fruiting(placement, fromLevel, density, tipFraction, minHeight,
            Inflorescence.solitary(0.004, erect, 0, 0), null,
            new FruitShape(FruitShape.Form.CONE, radius, elongation, 0.15, 0, scales, 0, hang), phenology, 1, 0,
            Palette.DEFAULT.with(Part.CONE, colors));
    }

    /** The deepest branch level of parametric growth. */
    public int maxLevel() {
        return levels.size();
    }
}
