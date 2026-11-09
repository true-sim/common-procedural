package com.truesim.common.procedural.assets;

import com.truesim.common.procedural.settlement.SettlementParameters;
import com.truesim.engine.settings.EngineSettings;
import com.truesim.engine.settings.Setting;
import java.util.List;

/**
 * A settlement's tunables as engine settings, one per field of {@link SettlementParameters} (grades, widths,
 * counts, radii, plot sizes, siting thresholds, mesh and grading sizes), their defaults
 * {@link SettlementParameters#DEFAULTS}. Read when a plan is asked for ({@link #parameters}): a change makes a new
 * plan, plans made before keep theirs.
 */
public final class SettlementSettings {

    public static final Setting<Float> GRID_EXTENT =
        Setting.decimal("procedural.settlement.grid.extent", 1024.0f, 128.0f, 8192.0f,
            "the settlement plan's side, metres");

    public static final Setting<Float> GRID_CELL =
        Setting.decimal("procedural.settlement.grid.cell", 4.0f, 1.0f, 16.0f,
            "the settlement's routing grid spacing, metres");

    public static final Setting<Float> GRID_RASTER =
        Setting.decimal("procedural.settlement.grid.raster", 1.0f, 0.25f, 4.0f,
            "the settlement's clearings and grading raster spacing, metres");

    public static final Setting<Float> NETWORK_GRADE_WEIGHT =
        Setting.decimal("procedural.settlement.network.gradeWeight", 6.0f, 0.0f, 100.0f,
            "a road's cost of climbing at its class's steepest grade, per metre");

    public static final Setting<Float> NETWORK_SIDE_WEIGHT =
        Setting.decimal("procedural.settlement.network.sideWeight", 3.0f, 0.0f, 100.0f,
            "a road's cost of crossing a slope as steep as its class allows, per metre");

    public static final Setting<Float> NETWORK_TURN_WEIGHT =
        Setting.decimal("procedural.settlement.network.turnWeight", 2.0f, 0.0f, 100.0f,
            "a road's cost of turning, cell lengths per radian (and per radian squared)");

    public static final Setting<Float> NETWORK_MAX_TURN =
        Setting.decimal("procedural.settlement.network.maxTurn", 170.0f, 10.0f, 179.0f,
            "the sharpest turn between two routing steps, degrees (hairpins)");

    public static final Setting<Float> NETWORK_REUSE =
        Setting.decimal("procedural.settlement.network.reuse", 0.3f, 0.01f, 1.0f,
            "an existing road's routing cost per metre (below 1: roads merge)");

    public static final Setting<Float> NETWORK_BRIDGE_WEIGHT =
        Setting.decimal("procedural.settlement.network.bridgeWeight", 30.0f, 0.0f, 1000.0f,
            "a road's cost of crossing open water, per metre");

    public static final Setting<Float> NETWORK_WET_WEIGHT =
        Setting.decimal("procedural.settlement.network.wetWeight", 4.0f, 0.0f, 100.0f,
            "a road's cost of crossing wetland, per metre");

    public static final Setting<Float> NETWORK_FLOOD_WEIGHT =
        Setting.decimal("procedural.settlement.network.floodWeight", 1.5f, 0.0f, 100.0f,
            "a road's cost of crossing flood-prone ground, per metre");

    public static final Setting<Float> NETWORK_HEADROOM =
        Setting.decimal("procedural.settlement.network.headroom", 0.85f, 0.3f, 1.0f,
            "the share of a road class's grade routing allows (smoothing needs the rest)");

    public static final Setting<Float> NETWORK_STEP =
        Setting.decimal("procedural.settlement.network.step", 2.0f, 0.5f, 8.0f,
            "the spacing of a road's centre-line points, metres");

    public static final Setting<Float> MAIN_WIDTH =
        Setting.decimal("procedural.settlement.main.width", 6.0f, 0.5f, 20.0f,
            "the width of the main road, metres");

    public static final Setting<Float> MAIN_MAX_GRADE =
        Setting.decimal("procedural.settlement.main.maxGrade", 0.1f, 0.01f, 1.0f,
            "the steepest grade of the main road (rise over run)");

    public static final Setting<Float> MAIN_MAX_SIDE_SLOPE =
        Setting.decimal("procedural.settlement.main.maxSideSlope", 24.0f, 1.0f, 60.0f,
            "the steepest ground the main road crosses, degrees");

    public static final Setting<Integer> MAIN_COUNT =
        Setting.integer("procedural.settlement.main.count", 1, 0, 64,
            "the main road: 1 through the square");

    public static final Setting<Float> MAIN_REACH =
        Setting.decimal("procedural.settlement.main.reach", 460.0f, 20.0f, 4000.0f,
            "how far from the square the main road lead, metres");

    public static final Setting<Float> MAIN_FILLET =
        Setting.decimal("procedural.settlement.main.fillet", 4.0f, 0.0f, 50.0f,
            "the radius of the rounded corners where the main road join others, metres");

    public static final Setting<Float> LANE_WIDTH =
        Setting.decimal("procedural.settlement.lane.width", 4.0f, 0.5f, 20.0f,
            "the width of village lanes, metres");

    public static final Setting<Float> LANE_MAX_GRADE =
        Setting.decimal("procedural.settlement.lane.maxGrade", 0.12f, 0.01f, 1.0f,
            "the steepest grade of village lanes (rise over run)");

    public static final Setting<Float> LANE_MAX_SIDE_SLOPE =
        Setting.decimal("procedural.settlement.lane.maxSideSlope", 26.0f, 1.0f, 60.0f,
            "the steepest ground village lanes cross, degrees");

    public static final Setting<Integer> LANE_COUNT =
        Setting.integer("procedural.settlement.lane.count", 6, 0, 64,
            "how many village lanes are planned");

    public static final Setting<Float> LANE_REACH =
        Setting.decimal("procedural.settlement.lane.reach", 200.0f, 20.0f, 4000.0f,
            "how far from the square village lanes lead, metres");

    public static final Setting<Float> LANE_FILLET =
        Setting.decimal("procedural.settlement.lane.fillet", 4.0f, 0.0f, 50.0f,
            "the radius of the rounded corners where village lanes join others, metres");

    public static final Setting<Float> TRACK_WIDTH =
        Setting.decimal("procedural.settlement.track.width", 3.0f, 0.5f, 20.0f,
            "the width of farm tracks, metres");

    public static final Setting<Float> TRACK_MAX_GRADE =
        Setting.decimal("procedural.settlement.track.maxGrade", 0.16f, 0.01f, 1.0f,
            "the steepest grade of farm tracks (rise over run)");

    public static final Setting<Float> TRACK_MAX_SIDE_SLOPE =
        Setting.decimal("procedural.settlement.track.maxSideSlope", 30.0f, 1.0f, 60.0f,
            "the steepest ground farm tracks cross, degrees");

    public static final Setting<Integer> TRACK_COUNT =
        Setting.integer("procedural.settlement.track.count", 6, 0, 64,
            "how many farm tracks are planned");

    public static final Setting<Float> TRACK_REACH =
        Setting.decimal("procedural.settlement.track.reach", 430.0f, 20.0f, 4000.0f,
            "how far from the square farm tracks lead, metres");

    public static final Setting<Float> TRACK_FILLET =
        Setting.decimal("procedural.settlement.track.fillet", 6.0f, 0.0f, 50.0f,
            "the radius of the rounded corners where farm tracks join others, metres");

    public static final Setting<Float> FOOTPATH_WIDTH =
        Setting.decimal("procedural.settlement.footpath.width", 1.4f, 0.5f, 20.0f,
            "the width of footpaths, metres");

    public static final Setting<Float> FOOTPATH_MAX_GRADE =
        Setting.decimal("procedural.settlement.footpath.maxGrade", 0.35f, 0.01f, 1.0f,
            "the steepest grade of footpaths (rise over run)");

    public static final Setting<Float> FOOTPATH_MAX_SIDE_SLOPE =
        Setting.decimal("procedural.settlement.footpath.maxSideSlope", 38.0f, 1.0f, 60.0f,
            "the steepest ground footpaths cross, degrees");

    public static final Setting<Integer> FOOTPATH_COUNT =
        Setting.integer("procedural.settlement.footpath.count", 4, 0, 64,
            "how many footpaths are planned");

    public static final Setting<Float> FOOTPATH_REACH =
        Setting.decimal("procedural.settlement.footpath.reach", 420.0f, 20.0f, 4000.0f,
            "how far from the square footpaths lead, metres");

    public static final Setting<Float> FOOTPATH_FILLET =
        Setting.decimal("procedural.settlement.footpath.fillet", 1.5f, 0.0f, 50.0f,
            "the radius of the rounded corners where footpaths join others, metres");

    public static final Setting<Float> CORE_SQUARE_LENGTH =
        Setting.decimal("procedural.settlement.core.squareLength", 34.0f, 8.0f, 200.0f,
            "the square's length along the main road, metres");

    public static final Setting<Float> CORE_SQUARE_WIDTH =
        Setting.decimal("procedural.settlement.core.squareWidth", 26.0f, 8.0f, 200.0f,
            "the square's width, metres");

    public static final Setting<Float> CORE_CORE_RADIUS =
        Setting.decimal("procedural.settlement.core.coreRadius", 90.0f, 10.0f, 1000.0f,
            "the village core's radius (cobbles, close-set houses), metres");

    public static final Setting<Float> CORE_VILLAGE_RADIUS =
        Setting.decimal("procedural.settlement.core.villageRadius", 220.0f, 20.0f, 2000.0f,
            "the village's radius (plots along the roads), metres");

    public static final Setting<Float> PLOTS_CORE_FRONTAGE =
        Setting.decimal("procedural.settlement.plots.coreFrontage", 14.0f, 4.0f, 100.0f,
            "a core plot's frontage, metres");

    public static final Setting<Float> PLOTS_CORE_DEPTH =
        Setting.decimal("procedural.settlement.plots.coreDepth", 20.0f, 4.0f, 100.0f,
            "a core plot's depth, metres");

    public static final Setting<Float> PLOTS_FRONTAGE =
        Setting.decimal("procedural.settlement.plots.frontage", 24.0f, 4.0f, 200.0f,
            "a plot's frontage outside the core, metres");

    public static final Setting<Float> PLOTS_DEPTH =
        Setting.decimal("procedural.settlement.plots.depth", 32.0f, 4.0f, 200.0f,
            "a plot's depth outside the core, metres");

    public static final Setting<Float> PLOTS_VERGE =
        Setting.decimal("procedural.settlement.plots.verge", 1.0f, 0.0f, 20.0f,
            "the verge between a road's edge and its plots, metres");

    public static final Setting<Float> PLOTS_OUTER_DENSITY =
        Setting.decimal("procedural.settlement.plots.outerDensity", 0.3f, 0.0f, 1.0f,
            "the share of frontage built at the village's edge");

    public static final Setting<Float> BUILDINGS_CORE_SETBACK =
        Setting.decimal("procedural.settlement.buildings.coreSetback", 1.5f, 0.0f, 20.0f,
            "a core house's distance from its plot's front, metres");

    public static final Setting<Float> BUILDINGS_SETBACK =
        Setting.decimal("procedural.settlement.buildings.setback", 4.0f, 0.0f, 50.0f,
            "a house's distance from its plot's front outside the core, metres");

    public static final Setting<Float> BUILDINGS_SIDE_GAP =
        Setting.decimal("procedural.settlement.buildings.sideGap", 1.5f, 0.0f, 20.0f,
            "the least gap between a footprint and its plot's sides, metres");

    public static final Setting<Float> BUILDINGS_HOUSE_MIN =
        Setting.decimal("procedural.settlement.buildings.houseMin", 8.0f, 3.0f, 40.0f,
            "a house's smallest side, metres");

    public static final Setting<Float> BUILDINGS_HOUSE_MAX =
        Setting.decimal("procedural.settlement.buildings.houseMax", 12.0f, 3.0f, 40.0f,
            "a house's largest side, metres");

    public static final Setting<Float> BUILDINGS_FARM_MIN =
        Setting.decimal("procedural.settlement.buildings.farmMin", 10.0f, 3.0f, 60.0f,
            "a farmhouse's smallest side, metres");

    public static final Setting<Float> BUILDINGS_FARM_MAX =
        Setting.decimal("procedural.settlement.buildings.farmMax", 15.0f, 3.0f, 60.0f,
            "a farmhouse's largest side, metres");

    public static final Setting<Float> BUILDINGS_BARN_MIN =
        Setting.decimal("procedural.settlement.buildings.barnMin", 6.0f, 2.0f, 40.0f,
            "a hay barn's smallest side, metres");

    public static final Setting<Float> BUILDINGS_BARN_MAX =
        Setting.decimal("procedural.settlement.buildings.barnMax", 9.0f, 2.0f, 40.0f,
            "a hay barn's largest side, metres");

    public static final Setting<Float> BUILDINGS_SUN_WEIGHT =
        Setting.decimal("procedural.settlement.buildings.sunWeight", 0.4f, 0.0f, 1.0f,
            "how much the south weighs against the way down the slope in a house's facing");

    public static final Setting<Boolean> BUILDINGS_EAVES_TO_VALLEY =
        Setting.bool("procedural.settlement.buildings.eavesToValley", true,
            "houses' ridges along their front (eaves to the valley); off: gables to the valley");

    public static final Setting<Float> SITING_MAX_SLOPE =
        Setting.decimal("procedural.settlement.siting.maxSlope", 20.0f, 1.0f, 60.0f,
            "the steepest ground a building stands on, degrees");

    public static final Setting<Float> SITING_FLOOD_HEIGHT =
        Setting.decimal("procedural.settlement.siting.floodHeight", 1.5f, 0.0f, 50.0f,
            "the height above its drainage (HAND) below which ground floods, metres");

    public static final Setting<Float> SITING_FLOOD_PER_ROOT =
        Setting.decimal("procedural.settlement.siting.floodPerRoot", 0.4f, 0.0f, 10.0f,
            "the flood height's rise per square-root square kilometre of catchment, metres");

    public static final Setting<Float> SITING_WETLAND =
        Setting.decimal("procedural.settlement.siting.wetland", 0.3f, 0.0f, 1.0f,
            "the wetland share above which ground is too wet to build on");

    public static final Setting<Float> SITING_WATER =
        Setting.decimal("procedural.settlement.siting.water", 0.1f, 0.0f, 1.0f,
            "the open water share above which ground is water");

    public static final Setting<Float> BARNS_SPACING =
        Setting.decimal("procedural.settlement.barns.spacing", 70.0f, 10.0f, 1000.0f,
            "the hay barns' candidate spacing (and least distance apart), metres");

    public static final Setting<Float> BARNS_DENSITY =
        Setting.decimal("procedural.settlement.barns.density", 0.45f, 0.0f, 1.0f,
            "the share of good hay barn candidates built");

    public static final Setting<Float> BARNS_MAX_SLOPE =
        Setting.decimal("procedural.settlement.barns.maxSlope", 28.0f, 1.0f, 60.0f,
            "the steepest meadow a hay barn stands on, degrees");

    public static final Setting<Float> BARNS_MEADOW =
        Setting.decimal("procedural.settlement.barns.meadow", 0.5f, 0.0f, 1.0f,
            "the least meadow share (the ecology's land use) under a hay barn");

    public static final Setting<Float> BARNS_CLEARANCE =
        Setting.decimal("procedural.settlement.barns.clearance", 12.0f, 0.0f, 200.0f,
            "a hay barn's least distance from roads and plots, metres");

    public static final Setting<Float> FURNITURE_LAMP_SPACING =
        Setting.decimal("procedural.settlement.furniture.lampSpacing", 24.0f, 4.0f, 200.0f,
            "the distance between street lamps, metres");

    public static final Setting<Float> FURNITURE_LAMP_OFFSET =
        Setting.decimal("procedural.settlement.furniture.lampOffset", 0.6f, 0.0f, 10.0f,
            "a street lamp's distance outside its road's edge, metres");

    public static final Setting<Float> FURNITURE_FENCE_OFFSET =
        Setting.decimal("procedural.settlement.furniture.fenceOffset", 1.5f, 0.0f, 20.0f,
            "a pasture fence's distance outside its track's edge, metres");

    public static final Setting<Float> FURNITURE_MIN_FENCE =
        Setting.decimal("procedural.settlement.furniture.minFence", 12.0f, 0.0f, 200.0f,
            "the shortest fence run kept, metres");

    public static final Setting<Integer> FURNITURE_ROCKS =
        Setting.integer("procedural.settlement.furniture.rocks", 40, 0, 10000,
            "how many rocks, boulders and stumps on the meadows");

    public static final Setting<Float> FURNITURE_TREE_MARGIN =
        Setting.decimal("procedural.settlement.furniture.treeMargin", 3.0f, 0.0f, 50.0f,
            "the margin kept clear of trees round roads, the square and buildings, metres");

    public static final Setting<Float> SURFACE_TILE =
        Setting.decimal("procedural.settlement.surface.tile", 64.0f, 8.0f, 1024.0f,
            "the road meshes' tile side, metres (a whole number of cells)");

    public static final Setting<Float> SURFACE_CELL =
        Setting.decimal("procedural.settlement.surface.cell", 0.5f, 0.125f, 4.0f,
            "the road surface's triangulation grid, metres");

    public static final Setting<Float> SURFACE_CAMBER =
        Setting.decimal("procedural.settlement.surface.camber", 0.025f, 0.0f, 0.2f,
            "the roads' crossfall from the crown to the edges (rise over run)");

    public static final Setting<Float> SURFACE_KERB_WIDTH =
        Setting.decimal("procedural.settlement.surface.kerbWidth", 0.3f, 0.05f, 2.0f,
            "a kerb's width, metres");

    public static final Setting<Float> SURFACE_KERB_HEIGHT =
        Setting.decimal("procedural.settlement.surface.kerbHeight", 0.14f, 0.0f, 1.0f,
            "a kerb's height above the cobbles, metres");

    public static final Setting<Float> SURFACE_STEP_GRADE =
        Setting.decimal("procedural.settlement.surface.stepGrade", 0.2f, 0.01f, 2.0f,
            "the grade above which a footpath is stone steps");

    public static final Setting<Float> SURFACE_STEP_RISE =
        Setting.decimal("procedural.settlement.surface.stepRise", 0.17f, 0.05f, 0.5f,
            "a stone step's rise, metres");

    public static final Setting<Float> SURFACE_BATTER =
        Setting.decimal("procedural.settlement.surface.batter", 1.5f, 0.1f, 10.0f,
            "the roads' cuts' and fills' run per metre of rise");

    public static final Setting<Float> SURFACE_MIN_RUN =
        Setting.decimal("procedural.settlement.surface.minRun", 2.0f, 0.0f, 50.0f,
            "the shortest cut or fill beyond a road's shoulder, metres");

    public static final Setting<Float> SURFACE_SHOULDER =
        Setting.decimal("procedural.settlement.surface.shoulder", 0.6f, 0.0f, 10.0f,
            "the level strip of terrain beside a road's edge, metres");

    public static final Setting<Float> SURFACE_SINK =
        Setting.decimal("procedural.settlement.surface.sink", 0.15f, 0.0f, 2.0f,
            "how far the terrain lies under a road's surface, metres");

    public static final Setting<Float> SURFACE_VERGE =
        Setting.decimal("procedural.settlement.surface.verge", 1.5f, 0.0f, 20.0f,
            "the width of the worn verge and gravel scatter beside a road, metres");

    public static final Setting<Float> SURFACE_TEXTURE =
        Setting.decimal("procedural.settlement.surface.texture", 2.0f, 0.1f, 50.0f,
            "the road surfaces' texture tile, metres");

    public static final Setting<Float> SURFACE_LIFT =
        Setting.decimal("procedural.settlement.surface.lift", 0.02f, 0.0f, 0.5f,
            "how far a road's surface lies above the terrain inside its edge, metres");

    /** Every setting, to register. */
    public static final List<Setting<?>> ALL = List.of(
        GRID_EXTENT, GRID_CELL, GRID_RASTER, NETWORK_GRADE_WEIGHT, NETWORK_SIDE_WEIGHT, NETWORK_TURN_WEIGHT,
        NETWORK_MAX_TURN, NETWORK_REUSE, NETWORK_BRIDGE_WEIGHT, NETWORK_WET_WEIGHT, NETWORK_FLOOD_WEIGHT,
        NETWORK_HEADROOM, NETWORK_STEP, MAIN_WIDTH, MAIN_MAX_GRADE, MAIN_MAX_SIDE_SLOPE, MAIN_COUNT, MAIN_REACH,
        MAIN_FILLET, LANE_WIDTH, LANE_MAX_GRADE, LANE_MAX_SIDE_SLOPE, LANE_COUNT, LANE_REACH, LANE_FILLET,
        TRACK_WIDTH, TRACK_MAX_GRADE, TRACK_MAX_SIDE_SLOPE, TRACK_COUNT, TRACK_REACH, TRACK_FILLET, FOOTPATH_WIDTH,
        FOOTPATH_MAX_GRADE, FOOTPATH_MAX_SIDE_SLOPE, FOOTPATH_COUNT, FOOTPATH_REACH, FOOTPATH_FILLET,
        CORE_SQUARE_LENGTH, CORE_SQUARE_WIDTH, CORE_CORE_RADIUS, CORE_VILLAGE_RADIUS, PLOTS_CORE_FRONTAGE,
        PLOTS_CORE_DEPTH, PLOTS_FRONTAGE, PLOTS_DEPTH, PLOTS_VERGE, PLOTS_OUTER_DENSITY, BUILDINGS_CORE_SETBACK,
        BUILDINGS_SETBACK, BUILDINGS_SIDE_GAP, BUILDINGS_HOUSE_MIN, BUILDINGS_HOUSE_MAX, BUILDINGS_FARM_MIN,
        BUILDINGS_FARM_MAX, BUILDINGS_BARN_MIN, BUILDINGS_BARN_MAX, BUILDINGS_SUN_WEIGHT, BUILDINGS_EAVES_TO_VALLEY,
        SITING_MAX_SLOPE, SITING_FLOOD_HEIGHT, SITING_FLOOD_PER_ROOT, SITING_WETLAND, SITING_WATER, BARNS_SPACING,
        BARNS_DENSITY, BARNS_MAX_SLOPE, BARNS_MEADOW, BARNS_CLEARANCE, FURNITURE_LAMP_SPACING, FURNITURE_LAMP_OFFSET,
        FURNITURE_FENCE_OFFSET, FURNITURE_MIN_FENCE, FURNITURE_ROCKS, FURNITURE_TREE_MARGIN, SURFACE_TILE,
        SURFACE_CELL, SURFACE_CAMBER, SURFACE_KERB_WIDTH, SURFACE_KERB_HEIGHT, SURFACE_STEP_GRADE, SURFACE_STEP_RISE,
        SURFACE_BATTER, SURFACE_MIN_RUN, SURFACE_SHOULDER, SURFACE_SINK, SURFACE_VERGE, SURFACE_TEXTURE,
        SURFACE_LIFT);

    private SettlementSettings() {
    }

    /** The parameters {@code settings} ask for. */
    public static SettlementParameters parameters(EngineSettings settings) {
        return new SettlementParameters(
            new SettlementParameters.Grid(d(settings, GRID_EXTENT), d(settings, GRID_CELL), d(settings, GRID_RASTER)),
            new SettlementParameters.Network(d(settings, NETWORK_GRADE_WEIGHT), d(settings, NETWORK_SIDE_WEIGHT),
                d(settings, NETWORK_TURN_WEIGHT), d(settings, NETWORK_MAX_TURN), d(settings, NETWORK_REUSE),
                d(settings, NETWORK_BRIDGE_WEIGHT), d(settings, NETWORK_WET_WEIGHT),
                d(settings, NETWORK_FLOOD_WEIGHT), d(settings, NETWORK_HEADROOM), d(settings, NETWORK_STEP)),
            new SettlementParameters.RoadClass(d(settings, MAIN_WIDTH), d(settings, MAIN_MAX_GRADE),
                d(settings, MAIN_MAX_SIDE_SLOPE), settings.get(MAIN_COUNT), d(settings, MAIN_REACH),
                d(settings, MAIN_FILLET)),
            new SettlementParameters.RoadClass(d(settings, LANE_WIDTH), d(settings, LANE_MAX_GRADE),
                d(settings, LANE_MAX_SIDE_SLOPE), settings.get(LANE_COUNT), d(settings, LANE_REACH),
                d(settings, LANE_FILLET)),
            new SettlementParameters.RoadClass(d(settings, TRACK_WIDTH), d(settings, TRACK_MAX_GRADE),
                d(settings, TRACK_MAX_SIDE_SLOPE), settings.get(TRACK_COUNT), d(settings, TRACK_REACH),
                d(settings, TRACK_FILLET)),
            new SettlementParameters.RoadClass(d(settings, FOOTPATH_WIDTH), d(settings, FOOTPATH_MAX_GRADE),
                d(settings, FOOTPATH_MAX_SIDE_SLOPE), settings.get(FOOTPATH_COUNT), d(settings, FOOTPATH_REACH),
                d(settings, FOOTPATH_FILLET)),
            new SettlementParameters.Core(d(settings, CORE_SQUARE_LENGTH), d(settings, CORE_SQUARE_WIDTH),
                d(settings, CORE_CORE_RADIUS), d(settings, CORE_VILLAGE_RADIUS)),
            new SettlementParameters.Plots(d(settings, PLOTS_CORE_FRONTAGE), d(settings, PLOTS_CORE_DEPTH),
                d(settings, PLOTS_FRONTAGE), d(settings, PLOTS_DEPTH), d(settings, PLOTS_VERGE),
                d(settings, PLOTS_OUTER_DENSITY)),
            new SettlementParameters.Buildings(d(settings, BUILDINGS_CORE_SETBACK), d(settings, BUILDINGS_SETBACK),
                d(settings, BUILDINGS_SIDE_GAP), d(settings, BUILDINGS_HOUSE_MIN), d(settings, BUILDINGS_HOUSE_MAX),
                d(settings, BUILDINGS_FARM_MIN), d(settings, BUILDINGS_FARM_MAX), d(settings, BUILDINGS_BARN_MIN),
                d(settings, BUILDINGS_BARN_MAX), d(settings, BUILDINGS_SUN_WEIGHT),
                settings.get(BUILDINGS_EAVES_TO_VALLEY)),
            new SettlementParameters.Siting(d(settings, SITING_MAX_SLOPE), d(settings, SITING_FLOOD_HEIGHT),
                d(settings, SITING_FLOOD_PER_ROOT), d(settings, SITING_WETLAND), d(settings, SITING_WATER)),
            new SettlementParameters.Barns(d(settings, BARNS_SPACING), d(settings, BARNS_DENSITY),
                d(settings, BARNS_MAX_SLOPE), d(settings, BARNS_MEADOW), d(settings, BARNS_CLEARANCE)),
            new SettlementParameters.Furniture(d(settings, FURNITURE_LAMP_SPACING),
                d(settings, FURNITURE_LAMP_OFFSET), d(settings, FURNITURE_FENCE_OFFSET),
                d(settings, FURNITURE_MIN_FENCE), settings.get(FURNITURE_ROCKS), d(settings, FURNITURE_TREE_MARGIN)),
            new SettlementParameters.Surface(d(settings, SURFACE_TILE), d(settings, SURFACE_CELL),
                d(settings, SURFACE_CAMBER), d(settings, SURFACE_KERB_WIDTH), d(settings, SURFACE_KERB_HEIGHT),
                d(settings, SURFACE_STEP_GRADE), d(settings, SURFACE_STEP_RISE), d(settings, SURFACE_BATTER),
                d(settings, SURFACE_MIN_RUN), d(settings, SURFACE_SHOULDER), d(settings, SURFACE_SINK),
                d(settings, SURFACE_VERGE), d(settings, SURFACE_TEXTURE), d(settings, SURFACE_LIFT)));
    }

    /** A decimal setting as the double it was written as (0.1f reads 0.1, not 0.10000000149). */
    private static double d(EngineSettings settings, Setting<Float> setting) {
        return Double.parseDouble(Float.toString(settings.get(setting)));
    }
}
