package com.truesim.common.procedural.assets;

import com.truesim.engine.settings.Setting;
import java.util.List;

/**
 * Procedural assets' tunables, as engine settings: how models carry their detail ({@link ModelDetail}). Read when a
 * model is referenced ({@link ProceduralAssets#detail}): a change makes new model assets, models made before keep
 * theirs.
 */
public final class ProceduralSettings {

    /** Models' meshes as a cluster hierarchy (detail by distance, per meshlet); off: one flat level. */
    public static final Setting<Boolean> HIERARCHY = Setting.bool("procedural.models.hierarchy", true,
        "procedural models as cluster hierarchies (off: one flat level, drawn whole at any distance)");
    /** The generated level a tree's model starts from (0 the most detailed). */
    public static final Setting<Integer> TREE_LEVEL = Setting.integer("procedural.models.treeLevel", 1, 0, 8,
        "the generated level a procedural tree's model starts from (0: the most detailed)");
    /** The generated level a plant's or rock's model starts from. */
    public static final Setting<Integer> PLANT_LEVEL = Setting.integer("procedural.models.plantLevel", 0, 0, 8,
        "the generated level a procedural plant's or rock's model starts from (0: the most detailed)");

    /** Every setting, to register. */
    public static final List<Setting<?>> ALL = List.of(HIERARCHY, TREE_LEVEL, PLANT_LEVEL);

    private ProceduralSettings() {
    }
}
