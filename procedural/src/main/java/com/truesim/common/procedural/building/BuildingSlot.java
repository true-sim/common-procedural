package com.truesim.common.procedural.building;

import java.util.List;

/**
 * The material slots of a building's meshes: submesh {@code ordinal()} is drawn with the slot's material, named in
 * recipes as {@code material.<id>}. A consumer binds textures to them (Poly Haven's scans in the showcase): texture
 * coordinates are in repeats of the slot's {@link #repeat() repeat} (the metres one texture covers; {@link
 * BuildingOptions#repeats()} changes them), u along a face, v down it, so a texture of that real size shows at its
 * size, upright. They stay small (under 1 + cell / repeat): faces are cut into cells of at most
 * {@link BuildingOptions#cell()} metres, each starting at its own repeat's fraction (the texture wraps), so half-float
 * coordinates keep their precision. A slot a building does not use has an empty submesh.
 */
public enum BuildingSlot {

    /** Rubble or dressed stone: plinths, stone ground floors, steps, chimneys, roof stones, quoins. */
    STONE("stone", 2.0),
    /** Lime plaster: plastered walls, the church. */
    PLASTER("plaster", 3.0),
    /** Timber walls: log walls (Blockbau), board cladding. */
    TIMBER("timber", 2.0),
    /** Sawn wood: beams, rafters, purlins, frames, sills, boards, balconies, railings. */
    TRIM("trim", 2.0),
    /** Wooden shingles on roofs. */
    SHINGLES("shingles", 2.0),
    /** Slates on roofs and spires. */
    SLATES("slates", 3.0),
    /** Inner walls and ceilings of rooms. */
    INTERIOR("interior", 3.0),
    /** Floors of rooms. */
    FLOOR("floor", 2.0),
    /** Window glass: closed thin panes (a transmissive, two-sided material). */
    GLASS("glass", 1.0),
    /** Windows seen from afar: opaque dark panes on the far levels, whose walls are closed. */
    WINDOW("window", 1.0),
    /** The hearth: fireplace stone, soot. */
    HEARTH("hearth", 1.5),
    /** Painted shutters. */
    SHUTTERS("shutters", 1.0),
    /** Door leaves. */
    DOOR("door", 1.5),
    /** Metal: the spire's cross and ball, clock hands, the sign's bracket. */
    METAL("metal", 1.0),
    /** Firewood: the bark of the logs (their sawn ends are {@link #TRIM}). */
    FIREWOOD("firewood", 1.0),
    /** Leaves in flower boxes. */
    FOLIAGE("foliage", 0.5),
    /** Flowers in flower boxes (geraniums). */
    FLOWERS("flowers", 0.5);

    private static final List<BuildingSlot> ALL = List.of(values());
    private static final List<String> NAMES = ALL.stream().map(BuildingSlot::id).toList();

    private final String id;
    private final double repeat;

    BuildingSlot(String id, double repeat) {
        this.id = id;
        this.repeat = repeat;
    }

    /** Its name in recipes ({@code material.<id>}). */
    public String id() {
        return id;
    }

    /** The metres one texture repeat covers, by default. */
    public double repeat() {
        return repeat;
    }

    /** Every slot, in submesh order. */
    public static List<BuildingSlot> all() {
        return ALL;
    }

    /** The slots' ids, in submesh order. */
    public static List<String> names() {
        return NAMES;
    }
}
