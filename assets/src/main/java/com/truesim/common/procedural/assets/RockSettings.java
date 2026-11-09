package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.ecology.rocks.RockFieldParameters;
import com.truesim.common.procedural.ecology.rocks.RockKind;
import com.truesim.engine.settings.EngineSettings;
import com.truesim.engine.settings.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A rock field's tunables as engine settings ({@link RockFieldParameters}): the cliffs and their run-out, each kind's
 * density and sizes, sorting, resting and how many models the rocks share. {@link #parameters} reads them; their
 * defaults are {@link RockFieldParameters#DEFAULTS}.
 */
@AllowAllocation("settings: declared once, read when a field is made")
public final class RockSettings {

    private static final RockFieldParameters D = RockFieldParameters.DEFAULTS;

    /** Metres between the field's samples. */
    public static final Setting<Float> CELL = decimal("procedural.rocks.cell", D.cell(), 1, 32,
        "metres between a rock field's samples of the conditions and the cliffs above");
    /** Ground steeper than this is a cliff, degrees. */
    public static final Setting<Float> CLIFF_SLOPE = decimal("procedural.rocks.cliffSlope", D.cliffs().slope(), 30,
        85, "ground steeper than this is a cliff that sheds rocks, degrees");
    /** The least height of a cliff that sheds rocks, metres. */
    public static final Setting<Float> CLIFF_HEIGHT = decimal("procedural.rocks.cliffHeight", D.cliffs().minHeight(),
        1, 500, "the least height of a cliff that sheds rocks, metres");
    /** How far uphill to look for a cliff, metres. */
    public static final Setting<Float> SEARCH = decimal("procedural.rocks.search", D.cliffs().search(), 20, 3000,
        "how far uphill a rock field looks for a cliff, metres along the climb");
    /** The shadow angle: rockfall's reach, degrees. */
    public static final Setting<Float> SHADOW_ANGLE = decimal("procedural.rocks.shadowAngle",
        D.cliffs().shadowAngle(), 15, 45, "rockfall runs out to where the line to the cliff's top falls below this, "
            + "degrees (the shadow angle)");
    /** Deposits steeper than this are talus, degrees. */
    public static final Setting<Float> TALUS_SLOPE = decimal("procedural.rocks.talusSlope", D.cliffs().talusSlope(),
        5, 45, "a deposit steeper than this is talus, shallower is run-out, degrees");
    /** Nothing rests on ground steeper than this, degrees. */
    public static final Setting<Float> REPOSE_SLOPE = decimal("procedural.rocks.reposeSlope",
        D.cliffs().reposeSlope(), 20, 60, "no rock rests on ground steeper than this, degrees");
    /** The upper share of a talus where no tree or shrub takes root. */
    public static final Setting<Float> ACTIVE_TALUS = decimal("procedural.rocks.activeTalus",
        D.cliffs().activeTalus(), 0, 1, "the upper share of a talus where rocks still fall: no tree or shrub there");
    /** How sharply sizes grow down the talus. */
    public static final Setting<Float> SORTING = decimal("procedural.rocks.sorting", D.sorting(), 0, 8,
        "how sharply the talus' sizes grow downslope (the exponent of the way down)");
    /** The spread of sizes about their mean. */
    public static final Setting<Float> SPREAD = decimal("procedural.rocks.spread", D.spread(), 0, 2,
        "the spread of rocks' sizes about their mean (of the size's logarithm)");
    /** How far rocks lean with the slope. */
    public static final Setting<Float> TILT = decimal("procedural.rocks.tilt", D.tilt(), 0, 1,
        "how far rocks lean with the ground's slope (1: flat on it)");
    /** How close rocks may lie. */
    public static final Setting<Float> PACKING = decimal("procedural.rocks.packing", D.packing(), 0.2f, 2,
        "how close rocks may lie, as a share of their radii added");
    /** How much of a plant's crown keeps it off a rock. */
    public static final Setting<Float> PLANT_CLEARANCE = decimal("procedural.rocks.plantClearance",
        D.plantClearance(), 0, 2, "how much of a plant's crown radius keeps its stem off a rock");
    /** Shapes per preset. */
    public static final Setting<Integer> SEEDS = Setting.integer("procedural.rocks.seeds", D.variants().seeds(), 1,
        64, "rock shapes per preset that a field's rocks share");
    /** Headings. */
    public static final Setting<Integer> HEADINGS = Setting.integer("procedural.rocks.headings",
        D.variants().yawSteps(), 1, 64, "headings a field's rock models are grown for (moss on the north side)");
    /** Moss and lichen levels. */
    public static final Setting<Integer> COVER_LEVELS = Setting.integer("procedural.rocks.coverLevels",
        D.variants().coverSteps(), 2, 16, "levels of moss and of lichen a field's rock models are grown with");
    /** Burial depths. */
    public static final Setting<Integer> DEPTHS = Setting.integer("procedural.rocks.depths",
        D.variants().burialSteps(), 1, 8, "depths a field's rock models are sunk to");

    /** Each kind's density (rocks per square metre at its densest), by {@link RockKind} ordinal. */
    public static final List<Setting<Float>> DENSITY = perKind("density", "rocks per square metre at their densest",
        0, 1, 0);
    /** Each kind's smallest size, metres. */
    public static final List<Setting<Float>> MIN_SIZE = perKind("minSize", "the smallest rock's size, metres",
        0.02f, 20, 1);
    /** Each kind's largest size, metres. */
    public static final List<Setting<Float>> MAX_SIZE = perKind("maxSize", "the largest rock's size, metres",
        0.02f, 20, 2);
    /** Each kind's candidate spacing, metres. */
    public static final List<Setting<Float>> SPACING = perKind("cell", "metres between candidate rocks", 0.5f, 500,
        3);

    /** Every setting, to register. */
    public static final List<Setting<?>> ALL = all();

    private RockSettings() {
    }

    /** The rock field parameters {@code settings} ask for. */
    public static RockFieldParameters parameters(EngineSettings settings) {
        RockFieldParameters.Cliffs cliffs = new RockFieldParameters.Cliffs(get(settings, CLIFF_SLOPE),
            get(settings, CLIFF_HEIGHT), get(settings, SEARCH), get(settings, SHADOW_ANGLE), get(settings, TALUS_SLOPE),
            get(settings, REPOSE_SLOPE), get(settings, ACTIVE_TALUS));
        List<RockFieldParameters.KindPlacement> kinds = new ArrayList<>();
        for (RockKind kind : RockKind.ALL) {
            int k = kind.ordinal();
            double min = get(settings, MIN_SIZE.get(k));
            kinds.add(new RockFieldParameters.KindPlacement(get(settings, SPACING.get(k)), get(settings,
                DENSITY.get(k)), min, Math.max(min, get(settings, MAX_SIZE.get(k)))));
        }
        return new RockFieldParameters(get(settings, CELL), cliffs, kinds, get(settings, SORTING), get(settings,
            SPREAD), get(settings, TILT), get(settings, PACKING), get(settings, PLANT_CLEARANCE),
            new RockFieldParameters.Variants(settings.get(SEEDS), settings.get(HEADINGS), settings.get(COVER_LEVELS),
                settings.get(DEPTHS)));
    }

    /** A float setting's value as the double it was written as (0.05f reads 0.05, not 0.0500000007). */
    private static double get(EngineSettings settings, Setting<Float> setting) {
        return Double.parseDouble(Float.toString(settings.get(setting)));
    }

    private static Setting<Float> decimal(String name, double value, float min, float max, String description) {
        return Setting.decimal(name, (float) value, min, max, description);
    }

    private static List<Setting<Float>> perKind(String what, String description, float min, float max, int field) {
        List<Setting<Float>> list = new ArrayList<>();
        for (RockKind kind : RockKind.ALL) {
            RockFieldParameters.KindPlacement p = D.kind(kind);
            double value = switch (field) {
                case 0 -> p.density();
                case 1 -> p.minSize();
                case 2 -> p.maxSize();
                default -> p.cell();
            };
            String name = kind.name().toLowerCase(Locale.ROOT);
            list.add(decimal("procedural.rocks." + name + "." + what, value, min, max, name + ": " + description));
        }
        return List.copyOf(list);
    }

    private static List<Setting<?>> all() {
        List<Setting<?>> list = new ArrayList<>(List.of(CELL, CLIFF_SLOPE, CLIFF_HEIGHT, SEARCH, SHADOW_ANGLE,
            TALUS_SLOPE, REPOSE_SLOPE, ACTIVE_TALUS, SORTING, SPREAD, TILT, PACKING, PLANT_CLEARANCE, SEEDS, HEADINGS, COVER_LEVELS,
            DEPTHS));
        list.addAll(DENSITY);
        list.addAll(MIN_SIZE);
        list.addAll(MAX_SIZE);
        list.addAll(SPACING);
        return List.copyOf(list);
    }
}
