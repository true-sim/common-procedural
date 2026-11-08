package com.truesim.common.procedural.ecology;

import java.util.List;

/**
 * The species of the Alps' vegetation belts, from the colline orchards to the nival lichens, with the wetlands along
 * their rivers. Their envelopes are set from published Alpine ecology, as ranges a species grows in rather than exact
 * physiological limits:
 *
 * <ul>
 *   <li><b>Temperature</b> (the growing season's mean, {@link Climate}): from the belts each species belongs to
 *       (Ozenda 1985, La vegetation de la chaine alpine; Ellenberg and Leuschner 2010, Vegetation Mitteleuropas mit den
 *       Alpen; Landolt 2003, Unsere Alpenflora) translated to temperatures by the default climate (800 m is about 14.2
 *       degrees, 1500 m 10.0, 2200 m 5.8, 3000 m 1.0), and the treeline's 6.4 degrees (Koerner and Paulsen 2004)
 *       as the trees' cold limit: spruce stops there, larch and stone pine a little above, mountain pine krummholz and
 *       the dwarf shrubs higher still.</li>
 *   <li><b>Moisture</b> on Ellenberg's scale around each species' moisture value (Ellenberg et al. 1991, Zeigerwerte
 *       von Pflanzen in Mitteleuropa), wider where it is indifferent; wetland species (sedges, cotton grass, marsh
 *       marigold, Sphagnum) on 8.5 to 10.5 (wet to shallow water; Delarze et al. 2015, Lebensraeume der Schweiz, for
 *       fens, bogs and floodplains).</li>
 *   <li><b>Light</b> (shade under taller layers) from the light value; <b>snow</b>, <b>wind exposure</b> and
 *       <b>substrate</b> from the species' habitats: alpenrose needs snow cover in winter, edelweiss and alpine aster
 *       grow on rocky calcareous ridges, mountain pine on scree, grey alder and willows along rivers.</li>
 * </ul>
 *
 * The indicator values are Ellenberg's for central Europe as transcribed here; for the Alpine species (mountain and
 * stone pine, green alder, alpenrose, gentian, alpine aster, edelweiss) they follow Landolt's Alpine scale in spirit and
 * are approximate. Ids: common-procedural's tree presets ({@code spruce fir larch pine beech birch hazel mugo}) and
 * the flora's ({@code rowan elder dog-rose apple blueberry lingonberry alpenrose gentian arnica alpine-aster edelweiss
 * buttercup clover grass}); species without a generator yet ({@code oak stone-pine ash alder willow green-alder sedge
 * cotton-grass rush marsh-marigold moss lichen}) are placed all the same, for a renderer to draw them or a stand-in.
 */
public final class AlpineSpecies {

    private static final Range TREE_SNOW = new Range(0, 0, 225, 265);
    private static final Range MEADOW_SNOW = new Range(0, 0, 240, 280);
    private static final Range WET = new Range(7.5, 8.5, 10.5, 11.5);

    // Trees: the canopy

    /** Norway spruce, the montane and subalpine forests' tree, to the treeline. */
    public static final Species SPRUCE = tree("spruce", "Picea abies", new Range(6.4, 7.6, 11.2, 15.4),
        new Range(3, 4, 8, 9.3), TREE_SNOW, 0.5, 0.25, 0.05, -0.2, 35, 3.2, 1.0, 0, 0, true, 5, 3, 0, 0,
        "Ellenberg L5 T3; montane to subalpine, dominant 1200-1900 m in the northern Alps, to the treeline");
    /** Silver fir, with beech and spruce in the montane forests. */
    public static final Species FIR = tree("fir", "Abies alba", new Range(8.8, 10.0, 13.6, 17),
        new Range(3.5, 4.5, 7.5, 9), TREE_SNOW, 0.4, 0.2, 0.03, -0.3, 40, 3.8, 0.9, 0, 0, false, 3, 5, 0, 0,
        "Ellenberg L3 T5; montane beech-fir forests, 900-1600 m");
    /** European larch: the upper subalpine, light-demanding, pioneer on scree, at the treeline. */
    public static final Species LARCH = tree("larch", "Larix decidua", new Range(6.0, 6.6, 9.4, 13),
        new Range(2.5, 3.5, 7, 8.5), new Range(0, 0, 235, 270), 0.6, 0.5, 0.15, -0.2, 32, 3.5, 0.65, 45, 0.5,
        true, 8, 0, 0, 0, "Ellenberg L8; upper subalpine larch and larch-stone pine forests to the treeline");
    /** Stone pine (Arolla pine): the upper subalpine with larch, more continental, rarer in the northern Alps. */
    public static final Species STONE_PINE = tree("stone-pine", "Pinus cembra", new Range(5.9, 6.4, 8.2, 10.5),
        new Range(2.5, 3.5, 7, 8.5), new Range(0, 0, 235, 270), 0.7, 0.4, 0.3, -0.2, 20, 3.5, 0.3, 35, 0.6, true,
        4, 2, 0, 0, "Ellenberg L4 T2; Larici-Pinetum cembrae at the treeline (Ozenda 1985)");
    /** Scots pine: dry, sunny, rocky slopes of the lower belts. */
    public static final Species PINE = tree("pine", "Pinus sylvestris", new Range(9.5, 11, 17, 21),
        new Range(1.5, 2.5, 5.5, 7.5), TREE_SNOW, 0.6, 0.5, 0.25, -0.3, 28, 3.5, 0.6, 50, 0.4, false, 7, 0, 0, 0,
        "Ellenberg L7; dry pine forests on sunny slopes and rock");
    /** European beech: colline and montane forests, the strongest competitor where it is suited. */
    public static final Species BEECH = tree("beech", "Fagus sylvatica", new Range(9.7, 11.2, 15.5, 20),
        new Range(3, 4, 6.5, 8), TREE_SNOW, 0.4, 0.2, 0.03, -0.6, 35, 6.0, 1.3, 0, 0, false, 3, 5, 5, 0,
        "Ellenberg L3 T5 F5; colline-montane, to about 1500 m in the northern Alps; avoids waterlogging");
    /** Oak: the colline belt's warm slopes. */
    public static final Species OAK = tree("oak", "Quercus robur, Q. petraea", new Range(12.6, 14.4, 22, 26),
        new Range(2.5, 3.5, 7, 8.5), TREE_SNOW, 0.4, 0.2, 0.05, 0.1, 30, 7.0, 1.0, 0, 0, false, 7, 6, 0, 0,
        "Ellenberg L7 T6; colline oak and oak-beech forests below about 800 m");
    /** Silver birch: a light-demanding pioneer, scattered. */
    public static final Species BIRCH = tree("birch", "Betula pendula", new Range(7, 9, 17, 21),
        new Range(2, 3, 8.5, 10), TREE_SNOW, 0.5, 0.5, 0.1, 0.1, 22, 3.0, 0.35, 30, 0.5, false, 7, 0, 0, 0,
        "Ellenberg L7; pioneer of clearings, screes and mire edges to the subalpine");
    /** Rowan: scattered small trees to the subalpine. */
    public static final Species ROWAN = tree("rowan", "Sorbus aucuparia", new Range(6.6, 8, 14, 18),
        new Range(3, 4, 7, 8.5), TREE_SNOW, 0.5, 0.3, 0.1, 0, 12, 2.5, 0.3, 0, 0, false, 6, 0, 0, 4,
        "Ellenberg L6 R4; colline to subalpine, often at the treeline");
    /** Apple: orchards round the valley farms, below about 1100 m. */
    public static final Species APPLE = tree("apple", "Malus domestica (M. sylvestris)", new Range(12.4, 14, 20, 24),
        new Range(3.5, 4.5, 6.5, 8), TREE_SNOW, 0.3, 0.1, 0, 0, 8, 3.5, 0.25, 30, 0.5, false, 7, 6, 5, 7,
        "Ellenberg (M. sylvestris) L7 T6 F5; orchards in the valleys");
    /** Ash: moist slopes and floodplain forests of the lower belts. */
    public static final Species ASH = tree("ash", "Fraxinus excelsior", new Range(11, 12.5, 18, 22),
        new Range(4, 5.5, 8, 9.5), TREE_SNOW, 0.4, 0.3, 0.05, 0.5, 30, 5.0, 0.6, 0, 0, false, 4, 5, 0, 7,
        "Ellenberg L4 T5 R7; ravine and hardwood floodplain forests");
    /** Grey alder: the riparian forest of Alpine rivers. */
    public static final Species ALDER = tree("alder", "Alnus incana", new Range(9, 10.5, 16, 20),
        new Range(5, 6.5, 9.5, 10.5), TREE_SNOW, 0.4, 0.6, 0.05, 1, 18, 3.5, 0.9, 25, 0.3, false, 6, 4, 7, 8,
        "Ellenberg L6 T4 F7 R8; Alnetum incanae along montane rivers (Delarze et al. 2015)");

    // Shrubs

    /** Willows: the gravel banks of rivers and streams. */
    public static final Species WILLOW = shrub("willow", "Salix purpurea, S. eleagnos", new Range(8.5, 10, 18, 22),
        new Range(5.5, 7, 10, 11), new Range(0, 0, 240, 280), 0.4, 0.8, 0.6, 0.1, 1, 6, 2.5, 1.0, 15, 0.4, 8, 5,
        0, 8, "Ellenberg (S. purpurea) L8 T5 R8; willow scrub on river gravels");
    /** Green alder: snow-rich north slopes, avalanche tracks and creeks of the subalpine. */
    public static final Species GREEN_ALDER = shrub("green-alder", "Alnus viridis", new Range(5, 6, 9, 11),
        new Range(5, 6, 8.5, 9.5), new Range(60, 120, 260, 300), 0.4, 1, 0.5, 0.1, 0.6, 3, 2.0, 1.0, 20, 0.5, 6, 2,
        6, 0, "Alnetum viridis: moist, snowy subalpine slopes and avalanche tracks (Ozenda 1985); values approximate");
    /** Hazel: colline and submontane edges and open woods. */
    public static final Species HAZEL = shrub("hazel", "Corylus avellana", new Range(10, 12, 18, 22),
        new Range(3, 4, 6.5, 8), TREE_SNOW, 0.5, 1, 0.3, 0.05, 0, 6, 2.5, 0.9, 15, 0.4, 6, 5, 0, 0,
        "Ellenberg L6 T5; forest edges and hedges to about 1300 m");
    /** Red-berried elder: montane clearings. */
    public static final Species ELDER = shrub("elder", "Sambucus racemosa", new Range(8, 9.5, 14, 17),
        new Range(4, 5, 7, 8.5), TREE_SNOW, 0.5, 1, 0.3, 0.05, 0, 4, 1.8, 0.5, 10, 0.4, 6, 4, 5, 5,
        "Ellenberg L6 T4 F5; montane clearings and edges");
    /** Dog rose: dry, sunny edges and hedges of the lower belts. */
    public static final Species DOG_ROSE = shrub("dog-rose", "Rosa canina", new Range(11, 13, 20, 24),
        new Range(2.5, 3.5, 5.5, 7), TREE_SNOW, 0.6, 1, 0.4, 0.1, -0.2, 2.5, 1.2, 0.5, 8, 0.3, 8, 5, 4, 0,
        "Ellenberg L8 T5 F4; sunny edges and hedges");
    /** Mountain pine: krummholz at and above the treeline, on scree and limestone, and in bogs. */
    public static final Species MUGO = shrub("mugo", "Pinus mugo", new Range(4.4, 5.2, 8, 10.5),
        new Range(2, 3, 8.5, 10), new Range(0, 0, 250, 290), 0.9, 0.8, 1, 0.4, -0.2, 2.5, 2.5, 1.0, 25, 0.5, 7, 2,
        0, 0, "Pinetum mugo: krummholz of the treeline ecotone on scree and limestone (Ozenda 1985); values approximate");

    // Dwarf shrubs: the heath

    /** Alpenrose: the dwarf-shrub heath at the treeline, under winter snow. */
    public static final Species ALPENROSE = dwarf("alpenrose", "Rhododendron ferrugineum", new Range(4.4, 5.4, 8.2, 10.5),
        new Range(4, 5, 7, 8.5), new Range(140, 180, 260, 290), 0.3, 1, 0.4, 0.1, 0.8, 0.6, 1.0, 15, 0.6, 6, 2, 5, 2,
        "Rhododendro-Vaccinietum at the treeline; needs snow protection in winter (Ellenberg and Leuschner 2010); "
            + "values approximate");
    /** Blueberry: the spruce forests' floor and the subalpine heath. */
    public static final Species BLUEBERRY = dwarf("blueberry", "Vaccinium myrtillus", new Range(5, 6.5, 11, 14),
        new Range(3, 4.5, 7, 8.5), new Range(0, 0, 250, 280), 0.4, 1, 0.3, 0.05, 0.4, 0.5, 1.0, 10, 0.4, 5, 0, 0, 2,
        "Ellenberg L5 R2; acid forest floors and heaths");
    /** Lingonberry: drier, more exposed heath. */
    public static final Species LINGONBERRY = dwarf("lingonberry", "Vaccinium vitis-idaea", new Range(4.5, 6, 10, 13),
        new Range(2.5, 3.5, 6, 8), new Range(0, 0, 240, 270), 0.7, 1, 0.3, 0.05, 0.25, 0.4, 0.7, 8, 0.4, 5, 0, 4, 2,
        "Ellenberg L5 F4 R2; dry acid heath and pine forest floors");

    // Herbs and flowers

    /** Trumpet gentian: alpine and subalpine pastures. */
    public static final Species GENTIAN = herb("gentian", "Gentiana acaulis, G. clusii", new Range(1.0, 3.0, 7, 10),
        new Range(3, 4, 6.5, 8), new Range(0, 0, 280, 310), 0.8, 1, 0.3, 0.1, 0, 0.25, 0.8, 6, 0.6, 8, 2, 5, 0,
        "alpine grasslands (Landolt 2003); values approximate");
    /** Arnica: acid subalpine meadows. */
    public static final Species ARNICA = herb("arnica", "Arnica montana", new Range(4, 6, 10, 13),
        new Range(3, 4, 6.5, 8), MEADOW_SNOW, 0.6, 1, 0.15, 0.02, 0, 0.25, 0.8, 8, 0.6, 9, 4, 5, 3,
        "Ellenberg L9 T4 F5 R3; acid montane-subalpine meadows (Nardion)");
    /** Alpine aster: rocky, calcareous alpine grassland. */
    public static final Species ALPINE_ASTER = herb("alpine-aster", "Aster alpinus", new Range(0.5, 2.2, 5.5, 8),
        new Range(2, 3, 5.5, 7), new Range(0, 0, 270, 300), 0.9, 0.8, 0.8, 0.6, 0, 0.2, 0.8, 5, 0.6, 9, 2, 4, 8,
        "rocky calcareous alpine grassland (Seslerion; Landolt 2003); values approximate");
    /** Edelweiss: rock ledges and wind-blown calcareous ridges of the alpine belt. */
    public static final Species EDELWEISS = herb("edelweiss", "Leontopodium alpinum", new Range(-0.5, 1.2, 4.5, 7),
        new Range(1.5, 2.5, 5, 6.5), new Range(0, 0, 250, 290), 1, 0.5, 0.8, 1, -1, 0.15, 0.6, 4, 0.7, 9, 1, 4, 9,
        "rock ledges and ridges on limestone (Landolt 2003); values approximate");
    /** Meadow buttercup: montane hay meadows. */
    public static final Species BUTTERCUP = herb("buttercup", "Ranunculus acris", new Range(6.5, 9, 18, 22),
        new Range(4, 5, 7.5, 9), MEADOW_SNOW, 0.6, 1, 0.15, 0.02, 0, 0.3, 1.0, 8, 0.5, 7, 0, 6, 0,
        "Ellenberg L7 F6; nutrient-rich hay meadows");
    /** Clover: meadows and pastures. */
    public static final Species CLOVER = herb("clover", "Trifolium repens, T. pratense", new Range(7, 9, 19, 23),
        new Range(3.5, 4.5, 6.5, 8), MEADOW_SNOW, 0.6, 1, 0.15, 0.02, 0, 0.3, 1.0, 6, 0.4, 8, 0, 5, 6,
        "Ellenberg (T. repens) L8 F5 R6; meadows and pastures");
    /** Marsh marigold: wet meadows, springs and stream banks. */
    public static final Species MARSH_MARIGOLD = herb("marsh-marigold", "Caltha palustris", new Range(6, 8, 18, 22),
        WET, new Range(0, 0, 260, 300), 0.5, 1, 0.1, 0, 0.6, 0.35, 0.9, 6, 0.5, 7, 0, 9, 0,
        "Ellenberg L7 F9; wet meadows, springs and banks");
    /** Sedges: fens and mires to the alpine belt. */
    public static final Species SEDGE = herb("sedge", "Carex nigra, C. rostrata", new Range(2, 4, 16, 20), WET,
        new Range(0, 0, 280, 320), 0.6, 1, 0.1, 0, 0.4, 0.35, 1.0, 0, 0, 8, 0, 8, 3,
        "Ellenberg (C. nigra) L8 F8 R3; small-sedge fens (Caricion fuscae; Delarze et al. 2015)");
    /** Cotton grass: fens and transitional mires, up to the alpine belt. */
    public static final Species COTTON_GRASS = herb("cotton-grass", "Eriophorum angustifolium", new Range(1, 3, 12, 16),
        WET, new Range(0, 0, 290, 320), 0.6, 1, 0.1, 0, 0, 0.3, 1.0, 10, 0.5, 8, 0, 9, 4,
        "Ellenberg L8 F9 R4; fens and transitional mires");
    /** Soft rush: wet pastures and ditches of the lower belts. */
    public static final Species RUSH = herb("rush", "Juncus effusus", new Range(8, 10, 18, 22),
        new Range(6.5, 7.5, 9.5, 10.5), MEADOW_SNOW, 0.5, 1, 0.1, 0, 0.4, 0.4, 0.9, 8, 0.4, 8, 5, 7, 3,
        "Ellenberg L8 T5 F7 R3; wet pastures");

    // Grass and ground

    /** Grasses: meadows, pastures and alpine grassland. */
    public static final Species GRASS = new Species("grass", "Poaceae (Festuca, Sesleria, Nardus, Poa)", Layer.GRASS,
        new Species.Envelope(new Range(0, 2, 20, 26), new Range(2, 3, 8, 9.5), new Range(0, 0, 290, 330), 0.8, 1,
            0.2, 0.03, 0),
        new Species.Habit(0.5, 0.3, 1.0, 0, 0, false), new Species.Indicators(7, 0, 0, 0),
        "grassland from the valley meadows to the alpine Caricion curvulae and Seslerion");
    /** Sphagnum and brown mosses: bogs and transitional mires. */
    public static final Species MOSS = new Species("moss", "Sphagnum spp.", Layer.GROUND,
        new Species.Envelope(new Range(0, 2, 14, 18), new Range(8, 9, 11, 12), new Range(0, 0, 280, 320), 0.5, 1,
            0, 0, 0.2),
        new Species.Habit(0.05, 0.3, 1.0, 0, 0, false), new Species.Indicators(0, 0, 9, 1),
        "raised bogs and transitional mires (Delarze et al. 2015)");
    /** Lichens: rock and wind-blown ground of the alpine and nival belts. */
    public static final Species LICHEN = new Species("lichen", "crustose and fruticose lichens", Layer.GROUND,
        new Species.Envelope(new Range(-8, -4, 3, 6), new Range(1, 2, 8, 10), new Range(0, 0, 345, 363), 1, 0.2, 0.6,
            1, 0),
        new Species.Habit(0.03, 0.3, 1.0, 0, 0, false), new Species.Indicators(9, 1, 0, 0),
        "the nival belt's rock above about 3000 m (Ozenda 1985)");

    /** Every species, tallest layer first. */
    public static final List<Species> ALL = List.of(SPRUCE, FIR, LARCH, STONE_PINE, PINE, BEECH, OAK, BIRCH, ROWAN,
        APPLE, ASH, ALDER, WILLOW, GREEN_ALDER, HAZEL, ELDER, DOG_ROSE, MUGO, ALPENROSE, BLUEBERRY, LINGONBERRY,
        GENTIAN, ARNICA, ALPINE_ASTER, EDELWEISS, BUTTERCUP, CLOVER, MARSH_MARIGOLD, SEDGE, COTTON_GRASS, RUSH, GRASS,
        MOSS, LICHEN);

    private AlpineSpecies() {
    }

    /** The species with {@code id}. */
    public static Species byId(String id) {
        for (Species species : ALL) {
            if (species.id().equals(id)) {
                return species;
            }
        }
        throw new IllegalArgumentException("No species '" + id + "'");
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static Species tree(String id, String name, Range temperature, Range moisture, Range snow,
        double exposure, double scree, double rock, double riparian, double height, double crown, double weight,
        double cluster, double clumping, boolean krummholz, int l, int t, int f, int r, String source) {
        return new Species(id, name, Layer.CANOPY,
            new Species.Envelope(temperature, moisture, snow, exposure, 1, scree, rock, riparian),
            new Species.Habit(height, crown, weight, cluster, clumping, krummholz), new Species.Indicators(l, t, f, r),
            source);
    }

    private static Species shrub(String id, String name, Range temperature, Range moisture, Range snow,
        double exposure, double soil, double scree, double rock, double riparian, double height, double crown,
        double weight, double cluster, double clumping, int l, int t, int f, int r, String source) {
        return new Species(id, name, Layer.SHRUB,
            new Species.Envelope(temperature, moisture, snow, exposure, soil, scree, rock, riparian),
            new Species.Habit(height, crown, weight, cluster, clumping, false), new Species.Indicators(l, t, f, r),
            source);
    }

    private static Species dwarf(String id, String name, Range temperature, Range moisture, Range snow,
        double exposure, double soil, double scree, double rock, double height, double crown, double weight,
        double cluster, double clumping, int l, int t, int f, int r, String source) {
        return new Species(id, name, Layer.DWARF_SHRUB,
            new Species.Envelope(temperature, moisture, snow, exposure, soil, scree, rock, 0),
            new Species.Habit(height, crown, weight, cluster, clumping, false), new Species.Indicators(l, t, f, r),
            source);
    }

    private static Species herb(String id, String name, Range temperature, Range moisture, Range snow,
        double exposure, double soil, double scree, double rock, double riparian, double crown, double weight,
        double cluster, double clumping, int l, int t, int f, int r, String source) {
        return new Species(id, name, Layer.HERB,
            new Species.Envelope(temperature, moisture, snow, exposure, soil, scree, rock, riparian),
            new Species.Habit(crown * 2, crown, weight, cluster, clumping, false), new Species.Indicators(l, t, f, r),
            source);
    }
}
