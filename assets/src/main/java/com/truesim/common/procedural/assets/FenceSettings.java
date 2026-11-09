package com.truesim.common.procedural.assets;

import com.truesim.common.procedural.fence.FenceTuning;
import com.truesim.engine.settings.EngineSettings;
import com.truesim.engine.settings.Setting;
import java.util.List;

/**
 * Procedural fences' tunables, as engine settings: the generator's {@link FenceTuning} and the level a fence's model
 * starts from. Read when a fence is referenced ({@link FenceAssets#tuning}, {@link FenceAssets#detail}): a change
 * makes new assets, fences made before keep theirs. Whether models are cluster hierarchies is
 * {@link ProceduralSettings#HIERARCHY}, as for every procedural model.
 */
public final class FenceSettings {

    /** How deep posts and stakes go below the lowest ground around their foot. */
    public static final Setting<Float> SINK = Setting.decimal("procedural.fences.sink",
        (float) FenceTuning.DEFAULTS.sink(), 0, 2, "metres fence posts go below the lowest ground around their foot");
    /** The longest step between points where pieces along the ground sample its height. */
    public static final Setting<Float> GROUND_STEP = Setting.decimal("procedural.fences.groundStep",
        (float) FenceTuning.DEFAULTS.groundStep(), 0.1f, 4,
        "metres between the ground samples of fence pieces lying along it (walls, low rails, far wattle)");
    /** Round pieces' sides at the most detailed level. */
    public static final Setting<Integer> SIDES = Setting.integer("procedural.fences.sides", FenceTuning.DEFAULTS.sides(),
        3, 32, "sides of a fence's round posts and rails at the most detailed level (rods and wire half as many)");
    /** How many levels of detail fences are generated with. */
    public static final Setting<Integer> LEVELS = Setting.integer("procedural.fences.levels",
        FenceTuning.DEFAULTS.levels(), 1, 3, "levels of detail generated for procedural fences");
    /** The generated level a fence's model starts from. */
    public static final Setting<Integer> LEVEL = Setting.integer("procedural.fences.level", 0, 0, 2,
        "the generated level a procedural fence's model starts from (0: the most detailed)");

    /** Every setting, to register. */
    public static final List<Setting<?>> ALL = List.of(SINK, GROUND_STEP, SIDES, LEVELS, LEVEL);

    private FenceSettings() {
    }

    /** The generator's tuning {@code settings} ask for. */
    public static FenceTuning tuning(EngineSettings settings) {
        return new FenceTuning(settings.get(SINK), settings.get(GROUND_STEP), settings.get(SIDES),
            settings.get(LEVELS));
    }

    /** How fences' models carry their detail, as {@code settings} ask: a hierarchy or flat, from which level. */
    public static ModelDetail detail(EngineSettings settings) {
        return new ModelDetail(settings.get(ProceduralSettings.HIERARCHY), 0, settings.get(LEVEL));
    }
}
