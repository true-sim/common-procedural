package com.truesim.common.procedural.tree;

import java.util.List;

/**
 * What to generate of a tree besides its species and seed: the season, a snow load, the levels of detail and the
 * impostor's capture.
 *
 * @param season which leaf tile shows, and whether deciduous trees are bare
 * @param snowLoad 0 to 1: how much snow weighs the branches down (by the species' {@code snowDroop}); the snow itself
 *     is the material's (e.g. the scene's snow cover)
 * @param lods the mesh levels, most detailed first
 * @param impostorFrames views per side of the octahedral impostor's grid
 * @param impostorResolution pixels per side of each impostor view
 */
public record TreeOptions(Season season, double snowLoad, List<TreeLod> lods, int impostorFrames,
    int impostorResolution) {

    public TreeOptions {
        lods = List.copyOf(lods);
        if (lods.isEmpty() || snowLoad < 0 || snowLoad > 1 || impostorFrames < 2 || impostorResolution < 1) {
            throw new IllegalArgumentException("Bad tree options: " + lods.size() + " levels, snow " + snowLoad
                + ", " + impostorFrames + " frames of " + impostorResolution);
        }
    }

    /** Summer, no snow, three levels ({@link TreeLod#FULL}, {@link TreeLod#MEDIUM}, {@link TreeLod#LOW}). */
    public static final TreeOptions DEFAULTS = new TreeOptions(Season.SUMMER, 0,
        List.of(TreeLod.FULL, TreeLod.MEDIUM, TreeLod.LOW), 8, 256);

    public TreeOptions withSeason(Season value) {
        return new TreeOptions(value, snowLoad, lods, impostorFrames, impostorResolution);
    }

    public TreeOptions withSnowLoad(double value) {
        return new TreeOptions(season, value, lods, impostorFrames, impostorResolution);
    }

    public TreeOptions withLods(List<TreeLod> value) {
        return new TreeOptions(season, snowLoad, value, impostorFrames, impostorResolution);
    }
}
