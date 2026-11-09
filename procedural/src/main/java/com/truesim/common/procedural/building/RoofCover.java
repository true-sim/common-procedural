package com.truesim.common.procedural.building;

import com.truesim.annotations.AllowAllocation;

/** What a roof is covered with: each its material slot, the stone-weighted roof its poles and stones too. */
public enum RoofCover {

    /** Wooden shingles (the {@code shingles} slot). */
    SHINGLES("shingles"),
    /** Slates (the {@code slates} slot). */
    SLATES("slates"),
    /**
     * Shingles held down by rows of stones on poles across the slope (Schwardach), on gentle roofs: the shingles, the
     * poles of the {@code trim} slot and the stones of the {@code stone} slot.
     */
    STONE_WEIGHTED("stone-weighted");

    private final String id;

    RoofCover(String id) {
        this.id = id;
    }

    /** Its name in recipes ({@code roof = stone-weighted}). */
    public String id() {
        return id;
    }

    /** The slot its covering is drawn with. */
    public BuildingSlot slot() {
        return this == SLATES ? BuildingSlot.SLATES : BuildingSlot.SHINGLES;
    }

    /** The cover named {@code id} ({@link #id()}, or the constant's name in any case). */
    @AllowAllocation("parsing names, at load time")
    public static RoofCover of(String id) {
        for (RoofCover cover : values()) {
            if (cover.id.equals(id) || cover.name().equalsIgnoreCase(id)) {
                return cover;
            }
        }
        throw new IllegalArgumentException("No roof cover '" + id + "'");
    }
}
