package com.truesim.common.procedural.settlement;

/**
 * Everything a settlement's plan depends on besides the terrain, its origin and the seed: a value, so equal parameters
 * on the same terrain give the same plan. Every field is a setting in {@code common-procedural-assets}
 * ({@code SettlementSettings}); {@link #DEFAULTS} are an Alpine valley village's.
 *
 * @param grid the plan's area and its sampling
 * @param network how roads are routed over the terrain
 * @param main the main valley road
 * @param lane village lanes
 * @param track farm tracks
 * @param footpath footpaths (and the paths to the meadows' hay barns)
 * @param core the square and the village's extent
 * @param plots parcels along the roads
 * @param buildings footprints in the plots
 * @param siting where building is allowed (slope, flooding)
 * @param barns hay barns on the meadows
 * @param furniture lamps, fences, rocks and the clearings' margins
 * @param surface the road meshes and the terrain's grading
 */
public record SettlementParameters(Grid grid, Network network, RoadClass main, RoadClass lane, RoadClass track,
    RoadClass footpath, Core core, Plots plots, Buildings buildings, Siting siting, Barns barns, Furniture furniture,
    Surface surface) {

    /** An Alpine valley village. */
    public static final SettlementParameters DEFAULTS = new SettlementParameters(Grid.DEFAULTS, Network.DEFAULTS,
        RoadClass.MAIN, RoadClass.LANE, RoadClass.TRACK, RoadClass.FOOTPATH, Core.DEFAULTS, Plots.DEFAULTS,
        Buildings.DEFAULTS, Siting.DEFAULTS, Barns.DEFAULTS, Furniture.DEFAULTS, Surface.DEFAULTS);

    /** The parameters of roads of {@code kind}. */
    public RoadClass road(RoadKind kind) {
        return switch (kind) {
            case MAIN -> main;
            case LANE -> lane;
            case TRACK -> track;
            case FOOTPATH -> footpath;
        };
    }

    public SettlementParameters withGrid(Grid value) {
        return new SettlementParameters(value, network, main, lane, track, footpath, core, plots, buildings, siting,
            barns, furniture, surface);
    }

    public SettlementParameters withNetwork(Network value) {
        return new SettlementParameters(grid, value, main, lane, track, footpath, core, plots, buildings, siting,
            barns, furniture, surface);
    }

    /** These parameters with {@code value} for roads of {@code kind}. */
    public SettlementParameters withRoad(RoadKind kind, RoadClass value) {
        return new SettlementParameters(grid, network, kind == RoadKind.MAIN ? value : main,
            kind == RoadKind.LANE ? value : lane, kind == RoadKind.TRACK ? value : track,
            kind == RoadKind.FOOTPATH ? value : footpath, core, plots, buildings, siting, barns, furniture, surface);
    }

    public SettlementParameters withCore(Core value) {
        return new SettlementParameters(grid, network, main, lane, track, footpath, value, plots, buildings, siting,
            barns, furniture, surface);
    }

    public SettlementParameters withPlots(Plots value) {
        return new SettlementParameters(grid, network, main, lane, track, footpath, core, value, buildings, siting,
            barns, furniture, surface);
    }

    public SettlementParameters withBuildings(Buildings value) {
        return new SettlementParameters(grid, network, main, lane, track, footpath, core, plots, value, siting,
            barns, furniture, surface);
    }

    public SettlementParameters withSiting(Siting value) {
        return new SettlementParameters(grid, network, main, lane, track, footpath, core, plots, buildings, value,
            barns, furniture, surface);
    }

    public SettlementParameters withBarns(Barns value) {
        return new SettlementParameters(grid, network, main, lane, track, footpath, core, plots, buildings, siting,
            value, furniture, surface);
    }

    public SettlementParameters withFurniture(Furniture value) {
        return new SettlementParameters(grid, network, main, lane, track, footpath, core, plots, buildings, siting,
            barns, value, surface);
    }

    public SettlementParameters withSurface(Surface value) {
        return new SettlementParameters(grid, network, main, lane, track, footpath, core, plots, buildings, siting,
            barns, furniture, value);
    }

    /**
     * The plan's area: a square {@code extent} metres a side about the origin, the terrain sampled every {@code cell}
     * metres for routing and siting, the clearings and grading rasterised every {@code raster} metres.
     *
     * @param extent the plan's side, metres
     * @param cell the routing grid's spacing, metres
     * @param raster the clearings' and grading's spacing, metres
     */
    public record Grid(double extent, double cell, double raster) {

        /** A kilometre square, routed every 4 m, rasterised every metre. */
        public static final Grid DEFAULTS = new Grid(1024, 4, 1);

        public Grid {
            if (extent < 16 * cell || cell <= 0 || raster <= 0) {
                throw new IllegalArgumentException("A plan is at least 16 cells a side: " + extent + ", " + cell);
            }
        }
    }

    /**
     * How roads are routed: least-cost paths over the routing grid (steps of up to four nodes, 48 directions), each
     * step costing its length times {@code 1 + gradeWeight (grade / maxGrade)^2 +
     * sideWeight (tan slope / tan maxSideSlope)^2 + wetWeight wetland + floodWeight flood + bridgeWeight water},
     * plus {@code turnWeight} cell lengths times {@code a (1 + a)} for a turn of {@code a} radians (a hairpin's
     * dearer than its share of gentle bends, so switchbacks have long legs); a step on an existing road costs {@code reuse} of its
     * length (roads merge into a network). Steps steeper than the class's grade times {@code headroom} are not taken,
     * so on steep ground a road traverses and turns back in hairpins (switchbacks), and smoothing keeps within the
     * grade. Turns up to {@code maxTurn} are allowed between two steps: a hairpin is one sharp turn, rounded.
     *
     * @param gradeWeight the cost of climbing at the class's steepest grade, per metre
     * @param sideWeight the cost of crossing a slope as steep as the class allows, per metre
     * @param turnWeight the cost of turning, cell lengths per radian (and per radian squared)
     * @param maxTurn the sharpest turn between two steps, degrees
     * @param reuse an existing road's cost per metre (below 1: roads share their way)
     * @param bridgeWeight the cost of crossing open water, per metre
     * @param wetWeight the cost of wetland, per metre
     * @param floodWeight the cost of flood-prone ground, per metre
     * @param headroom the share of the class's grade routing allows (smoothing needs the rest)
     * @param step the spacing of the roads' points, metres
     */
    public record Network(double gradeWeight, double sideWeight, double turnWeight, double maxTurn, double reuse,
        double bridgeWeight, double wetWeight, double floodWeight, double headroom, double step) {

        public static final Network DEFAULTS = new Network(6, 3, 2, 170, 0.3, 30, 4, 1.5, 0.85, 2);
    }

    /**
     * A class of road.
     *
     * @param width the surface's width, metres
     * @param maxGrade the steepest grade (rise over run)
     * @param maxSideSlope the steepest ground the road crosses, degrees
     * @param count how many roads of the class are planned (the main road: one, through the square)
     * @param reach how far from the origin the class's roads lead, metres
     * @param fillet the radius of the rounded corners where a road of the class joins another, metres (the kerbs'
     *     radius in the core, a widened apron where tracks join)
     */
    public record RoadClass(double width, double maxGrade, double maxSideSlope, int count, double reach,
        double fillet) {

        /** The main road: 6 m, 10 %, along the valley through the square, 460 m each way; 4 m corners. */
        public static final RoadClass MAIN = new RoadClass(6, 0.10, 24, 1, 460, 4);
        /** Village lanes: 4 m, 12 %, six of them 200 m out; 4 m corners (the kerbs' radius in the core). */
        public static final RoadClass LANE = new RoadClass(4, 0.12, 26, 6, 200, 4);
        /** Farm tracks: 3 m, 16 %, six of them to farmsteads 430 m out; a 6 m apron where they join. */
        public static final RoadClass TRACK = new RoadClass(3, 0.16, 30, 6, 430, 6);
        /** Footpaths: 1.4 m, 35 % (steps where steep), four of them up the slopes; 1.5 m corners. */
        public static final RoadClass FOOTPATH = new RoadClass(1.4, 0.35, 38, 4, 420, 1.5);

        public RoadClass {
            if (width <= 0 || maxGrade <= 0 || count < 0 || fillet < 0) {
                throw new IllegalArgumentException("A road class needs a width and a grade");
            }
        }
    }

    /**
     * The square and the village's extent.
     *
     * @param squareLength the square's length along the main road, metres
     * @param squareWidth its width across, metres
     * @param coreRadius the core's radius about the origin (cobbles, lamps, close-set houses), metres
     * @param villageRadius the village's radius (plots along the roads), metres
     */
    public record Core(double squareLength, double squareWidth, double coreRadius, double villageRadius) {

        public static final Core DEFAULTS = new Core(34, 26, 90, 220);
    }

    /**
     * Parcels along the roads, each a frontage on its road and a depth away from it.
     *
     * @param coreFrontage a core plot's frontage, metres
     * @param coreDepth a core plot's depth, metres
     * @param frontage a plot's frontage outside the core, metres
     * @param depth a plot's depth outside the core, metres
     * @param verge the strip between a road's edge and its plots, metres
     * @param outerDensity the share of frontage built at the village's edge (all of it in the core)
     */
    public record Plots(double coreFrontage, double coreDepth, double frontage, double depth, double verge,
        double outerDensity) {

        public static final Plots DEFAULTS = new Plots(14, 20, 24, 32, 1, 0.3);
    }

    /**
     * Footprints in the plots. Houses and farmhouses face the valley and the sun: their front turned toward a blend of
     * the way down the slope and the south, by {@code sunWeight}.
     *
     * @param coreSetback a core house's distance from its plot's front, metres
     * @param setback a house's distance from its plot's front outside the core, metres
     * @param sideGap the least distance between a footprint and its plot's sides and back, metres
     * @param houseMin a house's smallest side, metres
     * @param houseMax a house's largest side, metres
     * @param farmMin a farmhouse's smallest side, metres
     * @param farmMax a farmhouse's largest side, metres
     * @param barnMin a hay barn's smallest side, metres
     * @param barnMax a hay barn's largest side, metres
     * @param sunWeight how much the south weighs against the way down the slope in a house's facing, 0 to 1
     * @param eavesToValley houses' ridges along their front (eaves to the valley), else across it (gable to the valley)
     */
    public record Buildings(double coreSetback, double setback, double sideGap, double houseMin, double houseMax,
        double farmMin, double farmMax, double barnMin, double barnMax, double sunWeight, boolean eavesToValley) {

        public static final Buildings DEFAULTS = new Buildings(1.5, 4, 1.5, 8, 12, 10, 15, 6, 9, 0.4, true);
    }

    /**
     * Where building is allowed: not steeper than {@code maxSlope}, not flood-prone. Ground is flood-prone where it is
     * open water or wetland, or lower above its drainage (HAND) than {@code floodHeight} plus {@code floodPerRoot}
     * per square root of the drainage's catchment in square kilometres (big rivers rise higher).
     *
     * @param maxSlope the steepest ground a footprint stands on, degrees
     * @param floodHeight the height above the drainage below which ground floods, metres
     * @param floodPerRoot the flood height's rise per square-root square kilometre of catchment, metres
     * @param wetland the wetland share above which ground is too wet
     * @param water the open water share above which ground is water
     */
    public record Siting(double maxSlope, double floodHeight, double floodPerRoot, double wetland, double water) {

        public static final Siting DEFAULTS = new Siting(20, 1.5, 0.4, 0.3, 0.1);
    }

    /**
     * Hay barns scattered on the meadows, each reached by a footpath.
     *
     * @param spacing the candidates' grid spacing (and their least distance apart), metres
     * @param density the share of good candidates built
     * @param maxSlope the steepest meadow a barn stands on, degrees
     * @param meadow the least share of land kept open as meadow (the ecology's land use)
     * @param clearance the least distance from a road or plot, metres
     */
    public record Barns(double spacing, double density, double maxSlope, double meadow, double clearance) {

        public static final Barns DEFAULTS = new Barns(70, 0.45, 28, 0.5, 12);
    }

    /**
     * Lamps, fences, rocks and the clearings.
     *
     * @param lampSpacing the distance between lamps along the core's lanes, metres
     * @param lampOffset a lamp's distance outside its road's edge, metres
     * @param fenceOffset a pasture fence's distance outside its track's edge, metres
     * @param minFence the shortest fence run kept, metres
     * @param rocks how many rocks, boulders and stumps on the meadows
     * @param treeMargin the margin kept clear of trees round roads, plots and the square, metres
     */
    public record Furniture(double lampSpacing, double lampOffset, double fenceOffset, double minFence, int rocks,
        double treeMargin) {

        public static final Furniture DEFAULTS = new Furniture(24, 0.6, 1.5, 12, 40, 3);
    }

    /**
     * The road meshes and the terrain beside them. The roads' surface is one mesh over the network (merged at
     * junctions, rounded by the classes' fillets), triangulated on a grid of {@code cell}, lying {@code lift} above
     * the terrain inside its edge and on it at the edge; the terrain takes the road's height under it (from its outer
     * strip inward sunk {@code sink} below, so it never shows through), keeps it level for {@code shoulder} beyond the
     * edge, then falls or rises to the natural ground over at least {@code minRun}, at the batter's slope.
     *
     * @param tile the road meshes' tile side, metres (a whole number of cells)
     * @param cell the surface's triangulation grid, metres
     * @param camber the roads' crossfall from the crown to the edges (rise over run)
     * @param kerbWidth a kerb's width, metres
     * @param kerbHeight a kerb's height above the cobbles, metres
     * @param stepGrade the grade above which a footpath is stone steps
     * @param stepRise a step's rise, metres
     * @param batter the cuts' and fills' run per metre of rise
     * @param minRun the shortest cut or fill beyond the shoulder, metres
     * @param shoulder the level strip of terrain beside a road's edge, metres
     * @param sink how far the terrain lies under the road's surface, metres
     * @param verge the width of the worn verge and gravel scatter beside a road, metres
     * @param texture the surfaces' texture tile, metres
     * @param lift how far the surface lies above the terrain inside its edge, metres (none at the edge itself, where
     *     the two meet)
     */
    public record Surface(double tile, double cell, double camber, double kerbWidth, double kerbHeight,
        double stepGrade, double stepRise, double batter, double minRun, double shoulder, double sink, double verge,
        double texture, double lift) {

        public static final Surface DEFAULTS = new Surface(64, 0.5, 0.025, 0.3, 0.14, 0.2, 0.17, 1.5, 2, 0.6, 0.15,
            1.5, 2, 0.02);

        public Surface {
            if (cell <= 0 || Math.abs(tile / cell - Math.round(tile / cell)) > 1e-9) {
                throw new IllegalArgumentException("A tile is a whole number of cells: " + tile + ", " + cell);
            }
        }
    }
}
