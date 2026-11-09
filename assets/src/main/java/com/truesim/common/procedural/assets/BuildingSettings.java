package com.truesim.common.procedural.assets;

import com.truesim.common.procedural.building.BuildingOptions;
import com.truesim.engine.settings.EngineSettings;
import com.truesim.engine.settings.Setting;
import java.util.List;

/**
 * Procedural buildings' tunables, as engine settings: how fine faces are cut (texture coordinates restart in every
 * cell), how foundations meet the ground, whether rooms are built. Read into {@link BuildingOptions} by
 * {@link #options}; every value changes the meshes, so buildings made with other values are other assets (the options
 * key the generation cache).
 */
public final class BuildingSettings {

    /** The largest face cell, metres. */
    public static final Setting<Float> CELL = Setting.decimal("procedural.buildings.cell", 2.0f, 0.25f, 8f,
        "the largest face cell of a procedural building, metres: texture coordinates restart in each (half floats)");
    /** How far the ground floor stands above the highest ground under the footprint. */
    public static final Setting<Float> CLEARANCE = Setting.decimal("procedural.buildings.clearance", 0.3f, 0.05f, 2f,
        "how far a procedural building's ground floor stands above the highest ground under it, metres");
    /** How far foundations, steps and woodpiles reach below the ground. */
    public static final Setting<Float> EMBED = Setting.decimal("procedural.buildings.embed", 0.5f, 0.05f, 3f,
        "how far a procedural building's foundation and steps reach below the ground, metres");
    /** The longest run of foundation at one depth. */
    public static final Setting<Float> STEP = Setting.decimal("procedural.buildings.step", 1.6f, 0.3f, 20f,
        "the longest run of a procedural building's foundation at one depth, metres: shorter follows the slope closer");
    /** Whether to build rooms. */
    public static final Setting<Boolean> INTERIORS = Setting.bool("procedural.buildings.interiors", true,
        "procedural buildings with rooms on their near levels (floors, ceilings, inner walls, the hearth)");

    /** Every setting, to register. */
    public static final List<Setting<?>> ALL = List.of(CELL, CLEARANCE, EMBED, STEP, INTERIORS);

    private BuildingSettings() {
    }

    /** The options {@code settings} ask for, the slots' texture repeats as {@code base} has them. */
    public static BuildingOptions options(EngineSettings settings, BuildingOptions base) {
        return new BuildingOptions(settings.get(CELL), settings.get(CLEARANCE), settings.get(EMBED),
            settings.get(STEP), settings.get(INTERIORS), base.repeats());
    }

    /** The options {@code settings} ask for, the slots' default repeats. */
    public static BuildingOptions options(EngineSettings settings) {
        return options(settings, BuildingOptions.DEFAULTS);
    }
}
