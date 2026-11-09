package com.truesim.common.procedural.water;

import com.truesim.engine.settings.EngineSettings;
import com.truesim.engine.settings.Setting;
import java.util.List;

/**
 * Water's tunables as engine settings, one for every value of {@link WaterParameters}; their defaults are
 * {@link WaterParameters#DEFAULTS}. Read when a network is built ({@link #parameters}): a change makes new networks,
 * networks built before keep theirs.
 *
 * <pre>{@code
 * registry.add(WATER, WaterSettings.ALL.toArray(Setting[]::new));
 * WaterNetwork rivers = WaterNetwork.build(terrain, region, WaterSettings.parameters(settings));
 * }</pre>
 */
public final class WaterSettings {

    private static final WaterParameters D = WaterParameters.DEFAULTS;

    /** The seed of stones and jets. */
    public static final Setting<Integer> SEED = Setting.integer("water.seed", (int) D.seed(), 0, Integer.MAX_VALUE,
        "the seed of the beds' stones and the waterfalls' jets");

    /** Metres between flow-routing samples. */
    public static final Setting<Float> CELL = Setting.decimal("water.flow.cell", (float) D.flow().cell(), 1, 100,
        "metres between the samples water is routed over");
    /** Heights per cell side, the lowest kept. */
    public static final Setting<Integer> SUBSAMPLES = Setting.integer("water.flow.subsamples",
        D.flow().subsamples(), 1, 8, "heights per cell side, the lowest kept: channels narrower than a cell");
    /** The catchment a channel starts from. */
    public static final Setting<Float> CHANNEL_AREA = Setting.decimal("water.flow.channelArea",
        (float) D.flow().channelArea(), 1_000, 1e9f, "square metres of catchment a channel starts from");
    /** Specific discharge. */
    public static final Setting<Float> RUNOFF = Setting.decimal("water.flow.runoff", (float) D.flow().runoff(),
        0.001f, 1, "mean discharge, cubic metres a second per square kilometre of catchment");
    /** Metres mapped watercourses are burned in by. */
    public static final Setting<Float> BURN_DEPTH = Setting.decimal("water.flow.burnDepth",
        (float) D.flow().burnDepth(), 0, 200, "metres mapped watercourses are lowered by before routing");
    /** The shortest channel kept from its head. */
    public static final Setting<Float> MIN_LENGTH = Setting.decimal("water.flow.minLength",
        (float) D.flow().minLength(), 0, 10_000, "the shortest headwater channel kept, metres");

    /** Hydraulic geometry: width coefficient. */
    public static final Setting<Float> WIDTH_COEFFICIENT = Setting.decimal("water.channels.widthCoefficient",
        (float) D.channels().widthCoefficient(), 0.1f, 50, "a in width = a Q^b, metres");
    /** Hydraulic geometry: width exponent. */
    public static final Setting<Float> WIDTH_EXPONENT = Setting.decimal("water.channels.widthExponent",
        (float) D.channels().widthExponent(), 0, 1, "b in width = a Q^b");
    /** The narrowest channel. */
    public static final Setting<Float> MIN_WIDTH = Setting.decimal("water.channels.minWidth",
        (float) D.channels().minWidth(), 0.05f, 20, "the narrowest channel, metres");
    /** Manning's roughness. */
    public static final Setting<Float> MANNING = Setting.decimal("water.channels.manning",
        (float) D.channels().manning(), 0.01f, 0.2f, "Manning's roughness of the beds");
    /** The gentlest slope used for depth. */
    public static final Setting<Float> MIN_SLOPE = Setting.decimal("water.channels.minSlope",
        (float) D.channels().minSlope(), 0.0001f, 0.1f, "the gentlest slope a channel's depth is worked out for");
    /** The fastest flow. */
    public static final Setting<Float> MAX_SPEED = Setting.decimal("water.channels.maxSpeed",
        (float) D.channels().maxSpeed(), 0.5f, 30, "the fastest flow, metres a second");
    /** Smoothing of the channels' lines. */
    public static final Setting<Float> SMOOTHING = Setting.decimal("water.channels.smoothing",
        (float) D.channels().smoothing(), 0, 200, "metres a channel's line is smoothed over");
    /** Snapping to the thalweg. */
    public static final Setting<Float> SNAP = Setting.decimal("water.channels.snap", (float) D.channels().snap(), 0,
        50, "metres across the flow a channel's line moves to the lowest ground");

    /** Bank width as a multiple of the channel's. */
    public static final Setting<Float> BANK_WIDTH = Setting.decimal("water.banks.width",
        (float) D.banks().bankWidth(), 0, 10, "the banks' width as a multiple of the channel's");
    /** The narrowest bank. */
    public static final Setting<Float> MIN_BANK = Setting.decimal("water.banks.minWidth",
        (float) D.banks().minBank(), 0.1f, 50, "the narrowest bank, metres");
    /** The banks' slope. */
    public static final Setting<Float> BANK_SLOPE = Setting.decimal("water.banks.slope",
        (float) D.banks().bankSlope(), 0.05f, 5, "the banks' rise per metre");
    /** The bare part of the banks. */
    public static final Setting<Float> GRAVEL = Setting.decimal("water.banks.gravel", (float) D.banks().gravel(), 0,
        1, "the bare gravel beside the water, as a fraction of the bank");
    /** The surface below the ground. */
    public static final Setting<Float> SURFACE_OFFSET = Setting.decimal("water.banks.surfaceOffset",
        (float) D.banks().surfaceOffset(), 0, 5, "metres the water's surface lies below the ground at its line");

    /** Fall detection gradient. */
    public static final Setting<Float> FALL_GRADIENT = Setting.decimal("water.falls.minGradient",
        (float) D.falls().minGradient(), 0.2f, 20, "drop per run from which a channel falls");
    /** Free-fall gradient. */
    public static final Setting<Float> FREE_GRADIENT = Setting.decimal("water.falls.freeGradient",
        (float) D.falls().freeGradient(), 0.2f, 50, "a fall's mean drop per run from which it falls free");
    /** The lowest fall. */
    public static final Setting<Float> FALL_HEIGHT = Setting.decimal("water.falls.minHeight",
        (float) D.falls().minHeight(), 0.5f, 500, "the lowest waterfall, metres");
    /** Horeni's coefficient. */
    public static final Setting<Float> BREAKUP_COEFFICIENT = Setting.decimal("water.falls.breakupCoefficient",
        (float) D.falls().breakupCoefficient(), 0.1f, 100, "a falling sheet's break-up length per q^exponent");
    /** Horeni's exponent. */
    public static final Setting<Float> BREAKUP_EXPONENT = Setting.decimal("water.falls.breakupExponent",
        (float) D.falls().breakupExponent(), 0, 2, "the exponent of a sheet's break-up length");
    /** Terminal speed. */
    public static final Setting<Float> TERMINAL_SPEED = Setting.decimal("water.falls.terminalSpeed",
        (float) D.falls().terminalSpeed(), 1, 100, "the falling water's terminal speed, metres a second");
    /** Atomisation length. */
    public static final Setting<Float> ATOMISATION = Setting.decimal("water.falls.atomisation",
        (float) D.falls().atomisation(), 1, 10_000, "metres of fall over which the water atomises");
    /** The sheet's spreading. */
    public static final Setting<Float> SPREAD = Setting.decimal("water.falls.spread", (float) D.falls().spread(), 0,
        45, "a falling sheet's spreading, degrees each side");
    /** Jet spacing. */
    public static final Setting<Float> JET_SPACING = Setting.decimal("water.falls.jetSpacing",
        (float) D.falls().jetSpacing(), 0.05f, 20, "metres between the jets across a breaking sheet");
    /** Mist radius. */
    public static final Setting<Float> MIST_RADIUS = Setting.decimal("water.falls.mistRadius",
        (float) D.falls().mistRadius(), 0.01f, 5, "the mist's radius as a fraction of the fall's height");
    /** Mist height. */
    public static final Setting<Float> MIST_HEIGHT = Setting.decimal("water.falls.mistHeight",
        (float) D.falls().mistHeight(), 0.01f, 5, "the mist's height as a fraction of the fall's height");
    /** Mist density. */
    public static final Setting<Float> MIST_DENSITY = Setting.decimal("water.falls.mistDensity",
        (float) D.falls().mistDensity(), 0, 10, "the mist's extinction per metre per cubic metre a second of spray");

    /** Stone density. */
    public static final Setting<Float> STONE_DENSITY = Setting.decimal("water.stones.density",
        (float) D.stones().density(), 0, 10, "stones a square metre of bed");
    /** Stone size spread. */
    public static final Setting<Float> STONE_SPREAD = Setting.decimal("water.stones.spread",
        (float) D.stones().spread(), 0, 3, "the log-normal spread of the stones' sizes");
    /** The smallest stone. */
    public static final Setting<Float> STONE_MIN = Setting.decimal("water.stones.minSize",
        (float) D.stones().minSize(), 0.01f, 10, "the smallest stone placed, metres");
    /** The largest stone. */
    public static final Setting<Float> STONE_MAX = Setting.decimal("water.stones.maxSize",
        (float) D.stones().maxSize(), 0.01f, 20, "the largest stone placed, metres");
    /** Stones on the banks. */
    public static final Setting<Float> STONE_BANKS = Setting.decimal("water.stones.banks",
        (float) D.stones().banks(), 0, 2, "stones beyond the water's edge, as a fraction of the width");
    /** The bed-shaping flood's depth. */
    public static final Setting<Float> STONE_FLOOD = Setting.decimal("water.stones.flood",
        (float) D.stones().flood(), 0.1f, 20, "the bed-shaping flood's depth as a multiple of the mean depth");

    /** Chunk size. */
    public static final Setting<Float> CHUNK = Setting.decimal("water.surface.chunk", (float) D.surface().chunk(), 16,
        8192, "metres a side of the chunks water surfaces are cut into");
    /** Cross-section spacing. */
    public static final Setting<Float> SPACING = Setting.decimal("water.surface.spacing",
        (float) D.surface().spacing(), 0.25f, 100, "metres between a surface's cross-sections");
    /** Quads across. */
    public static final Setting<Integer> ACROSS = Setting.integer("water.surface.across", D.surface().across(), 1, 32,
        "quads across a channel's surface");

    /** Every setting, to register. */
    public static final List<Setting<?>> ALL = List.of(SEED, CELL, SUBSAMPLES, CHANNEL_AREA, RUNOFF, BURN_DEPTH,
        MIN_LENGTH, WIDTH_COEFFICIENT, WIDTH_EXPONENT, MIN_WIDTH, MANNING, MIN_SLOPE, MAX_SPEED, SMOOTHING, SNAP,
        BANK_WIDTH, MIN_BANK, BANK_SLOPE, GRAVEL, SURFACE_OFFSET, FALL_GRADIENT, FREE_GRADIENT, FALL_HEIGHT,
        BREAKUP_COEFFICIENT, BREAKUP_EXPONENT, TERMINAL_SPEED, ATOMISATION, SPREAD, JET_SPACING, MIST_RADIUS,
        MIST_HEIGHT, MIST_DENSITY, STONE_DENSITY, STONE_SPREAD, STONE_MIN, STONE_MAX, STONE_BANKS, STONE_FLOOD, CHUNK, SPACING,
        ACROSS);

    private WaterSettings() {
    }

    /** The parameters {@code settings} hold. */
    public static WaterParameters parameters(EngineSettings settings) {
        return new WaterParameters(settings.get(SEED),
            new WaterParameters.Flow(d(settings, CELL), settings.get(SUBSAMPLES), d(settings, CHANNEL_AREA),
                d(settings, RUNOFF), d(settings, BURN_DEPTH), d(settings, MIN_LENGTH)),
            new WaterParameters.Channels(d(settings, WIDTH_COEFFICIENT), d(settings, WIDTH_EXPONENT),
                d(settings, MIN_WIDTH), d(settings, MANNING), d(settings, MIN_SLOPE), d(settings, MAX_SPEED),
                d(settings, SMOOTHING), d(settings, SNAP)),
            new WaterParameters.Banks(d(settings, BANK_WIDTH), d(settings, MIN_BANK), d(settings, BANK_SLOPE),
                d(settings, GRAVEL), d(settings, SURFACE_OFFSET)),
            new WaterParameters.Falls(d(settings, FALL_GRADIENT), d(settings, FREE_GRADIENT),
                d(settings, FALL_HEIGHT), d(settings, BREAKUP_COEFFICIENT), d(settings, BREAKUP_EXPONENT),
                d(settings, TERMINAL_SPEED), d(settings, ATOMISATION), d(settings, SPREAD), d(settings, JET_SPACING),
                d(settings, MIST_RADIUS), d(settings, MIST_HEIGHT), d(settings, MIST_DENSITY)),
            new WaterParameters.Stones(d(settings, STONE_DENSITY), d(settings, STONE_SPREAD), d(settings, STONE_MIN),
                d(settings, STONE_MAX), d(settings, STONE_BANKS), d(settings, STONE_FLOOD)),
            new WaterParameters.Surface(d(settings, CHUNK), d(settings, SPACING), settings.get(ACROSS)));
    }

    /** A decimal setting as the double its float's shortest decimal names (0.045f as 0.045). */
    private static double d(EngineSettings settings, Setting<Float> setting) {
        return Double.parseDouble(Float.toString(settings.get(setting)));
    }
}
