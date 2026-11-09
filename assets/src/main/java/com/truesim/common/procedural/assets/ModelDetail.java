package com.truesim.common.procedural.assets;

import com.truesim.engine.settings.EngineSettings;

/**
 * How a recipe's model carries its detail ({@link ProceduralAssets#model}): its mesh as a cluster hierarchy built from
 * one of the generated levels (the engine then draws each meshlet at the detail its distance needs, so a forest's far
 * trees cost a few meshlets each), or as that level flat (one level, drawn whole at any distance).
 *
 * <p>Which level the hierarchy starts from trades the nearest trees' detail against memory and the time to build: a
 * tree's level 0 (some 90,000 triangles with its flowers) or level 1 (some 25,000, thin twigs left to the leaves),
 * a plant's level 0. The benchmark in effects-vegetation measured both (its README); the settings
 * ({@link ProceduralSettings}) choose.
 *
 * @param hierarchy a cluster hierarchy (true) or one flat level
 * @param treeLevel the generated level a tree's model starts from (clamped to the levels it has)
 * @param plantLevel the generated level a plant's or rock's model starts from
 */
public record ModelDetail(boolean hierarchy, int treeLevel, int plantLevel) {

    /** A cluster hierarchy from a tree's level 1 and a plant's level 0: the settings' defaults. */
    public static final ModelDetail DEFAULT = new ModelDetail(true, 1, 0);
    /** Level 0, flat: every tree drawn whole at any distance (before cluster hierarchies). */
    public static final ModelDetail FLAT = new ModelDetail(false, 0, 0);

    public ModelDetail {
        if (treeLevel < 0 || plantLevel < 0) {
            throw new IllegalArgumentException("Levels from 0: " + treeLevel + ", " + plantLevel);
        }
    }

    /** The detail {@code settings} ask for ({@link ProceduralSettings}). */
    public static ModelDetail of(EngineSettings settings) {
        return new ModelDetail(settings.get(ProceduralSettings.HIERARCHY), settings.get(ProceduralSettings.TREE_LEVEL),
            settings.get(ProceduralSettings.PLANT_LEVEL));
    }

    /** The level a model of generator {@code generator} starts from, of {@code levels} levels. */
    public int level(String generator, int levels) {
        return Math.min("tree".equals(generator) ? treeLevel : plantLevel, Math.max(0, levels - 1));
    }
}
