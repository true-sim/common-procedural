package com.truesim.common.procedural.ecology;

/**
 * The altitudinal vegetation belts of the Alps (Ozenda 1985; Ellenberg and Leuschner 2010), bounded by the growing
 * season's temperature, so aspect shifts them as it shifts the climate: a sunny slope carries every belt higher.
 */
public enum Belt {
    /** Below about 800 m: oak, beech, hazel, orchards. */
    COLLINE,
    /** About 800 to 1500 m: beech, fir and spruce. */
    MONTANE,
    /** About 1500 to 2200 m: spruce, then larch and stone pine, the treeline ecotone. */
    SUBALPINE,
    /** About 2200 to 3000 m: alpine grassland, dwarf shrubs, snowbeds. */
    ALPINE,
    /** Above about 3000 m: rock, snow and lichens. */
    NIVAL;

    /** The belts, cached. */
    static final Belt[] ALL = values();
}
