package com.truesim.common.procedural.building;

import com.truesim.annotations.AllowAllocation;

/**
 * What a building is, with the defaults of its kind: the footprint, floors and roof of a typical example in
 * Lauterbrunnen (Bernese Oberland). A {@link BuildingSpec} overrides the footprint and floors.
 */
public enum BuildingKind {

    /**
     * A chalet: a stone (or plastered) ground floor under a timber upper floor of Blockbau (horizontal logs, their ends
     * crossing at the corners), the gable to the front with a band of windows and a balcony, a gentle roof with deep
     * overhangs, a hearth and a chimney.
     */
    CHALET("chalet", 9.5, 11, 2, 24, 2.5, 2.45, 1.3, 1.8),
    /**
     * A farmhouse (Bauernhaus): the house and the barn under one long roof, the living part at the front (stone and
     * log, as a chalet), the barn behind (a stone stable under a board-clad hay loft with a hay door), an arched gable
     * board (Ruendi) under the front's overhang.
     */
    FARMHOUSE("farmhouse", 12, 17, 2, 24, 2.4, 2.5, 1.5, 2.0),
    /**
     * A hay barn (Gaden, Stadel): small, scattered over the meadows, a stone stable under a log hay loft, a hay door,
     * no windows but slits, a shingle roof, often stone-weighted.
     */
    HAY_BARN("hay-barn", 5.5, 6.5, 2, 25, 2.1, 2.3, 0.7, 0.9),
    /**
     * A church: a whitewashed nave with tall windows and a steep roof, a bell tower before its front with louvred
     * belfry openings, clock faces and a spire with a cross.
     */
    CHURCH("church", 9, 17, 1, 38, 7.0, 0, 0.6, 0.5),
    /**
     * An inn (Gasthaus): a large chalet of three floors, a plastered ground floor with wide windows (the parlour), two
     * timber floors with balconies, a sign by the door and tables before it.
     */
    INN("inn", 13, 12, 3, 22, 2.9, 2.5, 1.4, 2.0),
    /** A woodshed: posts under a small roof, board walls with gaps on three sides, open at the front, full of logs. */
    WOODSHED("woodshed", 3.5, 5, 1, 22, 2.3, 0, 0.6, 0.7);

    private final String id;
    private final double width;
    private final double depth;
    private final int floors;
    private final double pitch;
    private final double groundStorey;
    private final double upperStorey;
    private final double eaveOverhang;
    private final double gableOverhang;

    BuildingKind(String id, double width, double depth, int floors, double pitch, double groundStorey,
        double upperStorey, double eaveOverhang, double gableOverhang) {
        this.id = id;
        this.width = width;
        this.depth = depth;
        this.floors = floors;
        this.pitch = pitch;
        this.groundStorey = groundStorey;
        this.upperStorey = upperStorey;
        this.eaveOverhang = eaveOverhang;
        this.gableOverhang = gableOverhang;
    }

    /** Its name in recipes ({@code preset = hay-barn}). */
    public String id() {
        return id;
    }

    /** The typical width across the gable front, metres. */
    public double width() {
        return width;
    }

    /** The typical depth along the ridge, metres. */
    public double depth() {
        return depth;
    }

    /** The typical number of floors (storeys of walls under the eaves). */
    public int floors() {
        return floors;
    }

    /** The roof's pitch, degrees. */
    public double pitch() {
        return pitch;
    }

    /** The ground floor's height (floor to floor), metres. */
    public double groundStorey() {
        return groundStorey;
    }

    /** Each upper floor's height, metres. */
    public double upperStorey() {
        return upperStorey;
    }

    /** How far the roof reaches past the walls at the eaves (the long sides), metres. */
    public double eaveOverhang() {
        return eaveOverhang;
    }

    /** How far the roof reaches past the gables (front and back), metres. */
    public double gableOverhang() {
        return gableOverhang;
    }

    /** Whether people live or meet in it: rooms with lamps, props and indoor zones on its ground floor. */
    public boolean inhabited() {
        return this == CHALET || this == FARMHOUSE || this == INN || this == CHURCH;
    }

    /** The kind named {@code id} ({@link #id()}, or the constant's name in any case). */
    @AllowAllocation("parsing names, at load time")
    public static BuildingKind of(String id) {
        for (BuildingKind kind : values()) {
            if (kind.id.equals(id) || kind.name().equalsIgnoreCase(id)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("No building kind '" + id + "'");
    }
}
