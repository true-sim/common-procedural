package com.truesim.common.procedural.tree;

import java.util.List;

/**
 * What to generate of a tree besides its species and seed: the season and day, a snow load, the levels of detail and
 * the impostor's capture.
 *
 * @param season which leaf tile shows, and whether deciduous trees are bare
 * @param snowLoad 0 to 1: how much snow weighs the branches down (by the species' {@code snowDroop}); the snow itself
 *     is the material's (e.g. the scene's snow cover)
 * @param lods the mesh levels, most detailed first
 * @param impostorFrames views per side of the octahedral impostor's grid
 * @param impostorResolution pixels per side of each impostor view
 * @param day the day of the year (0 is 1 January): which flowers are open, which fruits ripe (the species'
 *     {@link com.truesim.common.procedural.flora.Phenology}); the season's {@link Season#day()} unless set
 */
public record TreeOptions(Season season, double snowLoad, List<TreeLod> lods, int impostorFrames,
    int impostorResolution, double day) {

    public TreeOptions {
        lods = List.copyOf(lods);
        if (lods.isEmpty() || snowLoad < 0 || snowLoad > 1 || impostorFrames < 2 || impostorResolution < 1
            || !(day >= 0 && day < 366)) {
            throw new IllegalArgumentException("Bad tree options: " + lods.size() + " levels, snow " + snowLoad
                + ", " + impostorFrames + " frames of " + impostorResolution + ", day " + day);
        }
    }

    /** Options on the season's day. */
    public TreeOptions(Season season, double snowLoad, List<TreeLod> lods, int impostorFrames,
        int impostorResolution) {
        this(season, snowLoad, lods, impostorFrames, impostorResolution, season.day());
    }

    /** Summer, no snow, three levels ({@link TreeLod#FULL}, {@link TreeLod#MEDIUM}, {@link TreeLod#LOW}). */
    public static final TreeOptions DEFAULTS = new TreeOptions(Season.SUMMER, 0,
        List.of(TreeLod.FULL, TreeLod.MEDIUM, TreeLod.LOW), 8, 256);

    /** In {@code value}, on its day. */
    public TreeOptions withSeason(Season value) {
        return new TreeOptions(value, snowLoad, lods, impostorFrames, impostorResolution, value.day());
    }

    /** On day {@code value} of the year (0 to 365), in its season ({@link Season#of}). */
    public TreeOptions withDay(double value) {
        return new TreeOptions(Season.of(value), snowLoad, lods, impostorFrames, impostorResolution, value);
    }

    public TreeOptions withSnowLoad(double value) {
        return new TreeOptions(season, value, lods, impostorFrames, impostorResolution, day);
    }

    public TreeOptions withLods(List<TreeLod> value) {
        return new TreeOptions(season, snowLoad, value, impostorFrames, impostorResolution, day);
    }
}
