package com.truesim.common.procedural.settlement;

import com.truesim.common.math.geometry.Polygons;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.building.BuildingSpec;
import com.truesim.common.procedural.building.Footprint;
import java.util.List;

/**
 * A settlement's plan, as data: its roads, the square, plots, buildings (as specs for the buildings generator, placed),
 * props as typed anchors, lamps and fences (polylines for the fence generator), the clearings the ecology keeps open
 * and the grading the terrain takes under the roads and the square. Made by {@link Settlements#plan}; its road meshes
 * by {@link RoadMeshes#build}.
 *
 * <p>The frame is the terrain's: x east, z south, y up, metres. A building's or prop's turn ({@code yaw}, radians
 * about up) turns its front from +z (south): its front faces {@code (sin yaw, cos yaw)}; a point ({@code lx},
 * {@code lz}) of its frame is at {@code (x + lx cos yaw + lz sin yaw, z - lx sin yaw + lz cos yaw)}.
 */
public final class Settlement {

    /** A road's {@code from} or {@code to} when it starts or ends on the square. */
    public static final int SQUARE = -2;
    /** A road's {@code from} or {@code to} when it starts or ends on nothing (the plan's edge, a destination). */
    public static final int NONE = -1;

    /**
     * A road: its centre line every {@link SettlementParameters.Network#step} metres or so, {@code y} the surface's
     * height at the crown (graded: cut and filled to keep within its class's grade).
     *
     * @param id its index in {@link #roads()}
     * @param kind its class
     * @param surface what it is surfaced with
     * @param width its surface's width, metres
     * @param x the centre line's points, east
     * @param y the crown's heights
     * @param z the centre line's points, south
     * @param from the road it starts on ({@link #SQUARE}, {@link #NONE} or an id)
     * @param to the road it ends on ({@link #SQUARE}, {@link #NONE} or an id)
     */
    public record Road(int id, RoadKind kind, RoadSurface surface, double width, double[] x, double[] y, double[] z,
        int from, int to) {

        /** How many points. */
        public int count() {
            return x.length;
        }

        /** Its length along the ground plan, metres. */
        public double length() {
            double l = 0;
            for (int i = 1; i < x.length; i++) {
                l += Polygons.length(x[i] - x[i - 1], z[i] - z[i - 1]);
            }
            return l;
        }

        /** The horizontal distance from ({@code px}, {@code pz}) to the centre line, metres. */
        public double distance(double px, double pz) {
            double best = Double.MAX_VALUE;
            for (int i = 1; i < x.length; i++) {
                best = Math.min(best, Polygons.segmentDistance(px, pz, x[i - 1], z[i - 1], x[i], z[i]));
            }
            return best;
        }

        /** The steepest grade between neighbouring points (rise over run). */
        public double maxGrade() {
            double g = 0;
            for (int i = 1; i < x.length; i++) {
                double run = Polygons.length(x[i] - x[i - 1], z[i] - z[i - 1]);
                if (run > 1e-9) {
                    g = Math.max(g, Math.abs(y[i] - y[i - 1]) / run);
                }
            }
            return g;
        }
    }

    /**
     * The square: a rectangle about ({@code x}, {@code z}), its length along {@code (cos angle, sin angle)} (the main
     * road's way through it), its surface a plane of height {@code y} at the centre and the gradients given.
     *
     * @param x its centre, east
     * @param y its surface's height at the centre
     * @param z its centre, south
     * @param angle the direction of its length from +x toward +z, radians
     * @param halfLength half its length, metres
     * @param halfWidth half its width, metres
     * @param slopeX the surface's rise per metre east
     * @param slopeZ the surface's rise per metre south
     */
    public record Square(double x, double y, double z, double angle, double halfLength, double halfWidth,
        double slopeX, double slopeZ) {

        /** The surface's height at ({@code px}, {@code pz}). */
        public double height(double px, double pz) {
            return y + slopeX * (px - x) + slopeZ * (pz - z);
        }

        /** Along the length from the centre, metres. */
        public double along(double px, double pz) {
            return (px - x) * Maths.cos(angle) + (pz - z) * Maths.sin(angle);
        }

        /** Across, from the centre toward {@code (-sin angle, cos angle)}, metres. */
        public double across(double px, double pz) {
            return -(px - x) * Maths.sin(angle) + (pz - z) * Maths.cos(angle);
        }

        /** How far ({@code px}, {@code pz}) lies outside the rectangle (negative inside), metres (a box distance). */
        public double outside(double px, double pz) {
            return Math.max(Math.abs(along(px, pz)) - halfLength, Math.abs(across(px, pz)) - halfWidth);
        }

        /** The ground plan point at {@code along}, {@code across} into {@code out} (x, z). */
        public void point(double along, double across, double[] out) {
            double c = Maths.cos(angle);
            double s = Maths.sin(angle);
            out[0] = x + along * c - across * s;
            out[1] = z + along * s + across * c;
        }

        /** The corners, counter-clockwise on the ground plan (as seen from above), x then z of each. */
        public double[] corners() {
            double[] out = new double[8];
            double[] p = new double[2];
            double[][] at = {{-halfLength, -halfWidth}, {halfLength, -halfWidth}, {halfLength, halfWidth},
                {-halfLength, halfWidth}};
            for (int k = 0; k < 4; k++) {
                point(at[k][0], at[k][1], p);
                out[2 * k] = p[0];
                out[2 * k + 1] = p[1];
            }
            return out;
        }
    }

    /** Where a plot lies: what kind of house it is for. */
    public enum Zone {
        /** About the square: close-set village houses. */
        CORE,
        /** The village's lanes beyond the core: houses in gardens. */
        VILLAGE,
        /** Out at a farm track's end: a farmhouse and its barn. */
        FARMSTEAD,
        /** On the square: the inn's and the church's. */
        SQUARE
    }

    /**
     * A parcel: a quadrilateral, corners 0 and 1 its front on its road (or the square), 2 and 3 its back.
     *
     * @param id its index in {@link #plots()}
     * @param x its corners, east
     * @param z its corners, south
     * @param road the road its front is on ({@link #SQUARE} for the square's)
     * @param zone where it lies
     */
    public record Plot(int id, double[] x, double[] z, int road, Zone zone) {

        /** Its area, square metres. */
        public double area() {
            return Math.abs(Polygons.signedArea(x, z));
        }

        /** Whether ({@code px}, {@code pz}) is inside. */
        public boolean contains(double px, double pz) {
            return Polygons.insideConvex(px, pz, x, z);
        }
    }

    /**
     * A building placed: the buildings generator's spec (common-procedural's {@code building} package: its kind,
     * {@link Footprint} with its place and turn, floors, roof and seed) and where its ground floor stands.
     *
     * @param id its index in {@link #buildings()}
     * @param spec what to build, where: {@code Buildings.generate(spec, ground)} builds it
     * @param y its ground floor's height: the highest ground under the footprint
     * @param foundation the lowest ground under the footprint (how far down its foundation must reach)
     * @param plot its plot, or {@link #NONE} (a hay barn on the meadows)
     * @param road the road that reaches it (its plot's, or a barn's footpath)
     */
    public record Building(int id, BuildingSpec spec, double y, double foundation, int plot, int road) {

        /** Its footprint's centre, east. */
        public double x() {
            return spec.footprint().x();
        }

        /** Its footprint's centre, south. */
        public double z() {
            return spec.footprint().z();
        }

        /** Its turn: its front (gable) faces {@code (sin yaw, cos yaw)}. */
        public double yaw() {
            return spec.footprint().yaw();
        }

        /** The ground plan point of ({@code lx}, {@code lz}) in its frame into {@code out} (x, z). */
        public void toWorld(double lx, double lz, double[] out) {
            out[0] = spec.footprint().worldX(lx, lz);
            out[1] = spec.footprint().worldZ(lx, lz);
        }

        /** The front door's ground plan point (the front gable's middle) into {@code out}. */
        public void door(double[] out) {
            toWorld(0, spec.footprint().depth() / 2, out);
        }

        /** The footprint's corners, x then z of each. */
        public double[] corners() {
            double hw = spec.footprint().width() / 2;
            double hd = spec.footprint().depth() / 2;
            double[] out = new double[8];
            double[] p = new double[2];
            double[][] at = {{-hw, -hd}, {hw, -hd}, {hw, hd}, {-hw, hd}};
            for (int k = 0; k < 4; k++) {
                toWorld(at[k][0], at[k][1], p);
                out[2 * k] = p[0];
                out[2 * k + 1] = p[1];
            }
            return out;
        }

        /** Whether ({@code px}, {@code pz}) is within its footprint, grown by {@code margin} metres. */
        public boolean contains(double px, double pz, double margin) {
            double c = Maths.cos(yaw());
            double s = Maths.sin(yaw());
            double dx = px - x();
            double dz = pz - z();
            double lx = dx * c - dz * s;
            double lz = dx * s + dz * c;
            return Math.abs(lx) <= spec.footprint().width() / 2 + margin
                && Math.abs(lz) <= spec.footprint().depth() / 2 + margin;
        }
    }

    /**
     * A prop's anchor: what stands there, on the ground at (x, y, z), turned.
     *
     * @param kind what it is
     * @param x where, east
     * @param y the ground's height there
     * @param z where, south
     * @param yaw its turn (its front faces {@code (sin yaw, cos yaw)})
     * @param building the building it belongs to, or {@link #NONE}
     */
    public record Prop(PropKind kind, double x, double y, double z, double yaw, int building) {
    }

    /**
     * A street lamp: a post on the ground at (x, y, z), its lantern's arm toward its road.
     *
     * @param x where, east
     * @param y the ground's height
     * @param z where, south
     * @param yaw its turn: faces {@code (sin yaw, cos yaw)}, toward the road
     * @param road the road it lights, or {@link #SQUARE}
     */
    public record Lamp(double x, double y, double z, double yaw, int road) {
    }

    /** What a fence encloses. */
    public enum FenceKind {
        /** A garden: round a plot's sides and back. */
        GARDEN,
        /** A pasture: along a farm track or the main road outside the village. */
        PASTURE
    }

    /**
     * A fence: a polyline on the ground for the fence generator, posts where it likes.
     *
     * @param kind what it encloses
     * @param x its points, east
     * @param y the ground's heights there
     * @param z its points, south
     */
    public record Fence(FenceKind kind, double[] x, double[] y, double[] z) {

        /** Its points as x, z pairs: the line the fence generator ({@code Fences.generate}) takes. */
        public double[] line() {
            double[] line = new double[2 * x.length];
            for (int i = 0; i < x.length; i++) {
                line[2 * i] = x[i];
                line[2 * i + 1] = z[i];
            }
            return line;
        }

        /** Its length along the ground plan, metres. */
        public double length() {
            double l = 0;
            for (int i = 1; i < x.length; i++) {
                l += Polygons.length(x[i] - x[i - 1], z[i] - z[i - 1]);
            }
            return l;
        }
    }

    private final long seed;
    private final double originX;
    private final double originZ;
    private final SettlementParameters parameters;
    private final Square square;
    private final List<Road> roads;
    private final List<Plot> plots;
    private final List<Building> buildings;
    private final List<Prop> props;
    private final List<Lamp> lamps;
    private final List<Fence> fences;
    private final Clearing clearing;
    private final Grading grading;
    /** The road meshes, made when first asked for. */
    private volatile List<RoadMeshes.Tile> roadMeshes;
    /** The order roads, plots and buildings were made in (for pictures of the plan growing): see {@link #order}. */
    private final int[] order;

    Settlement(long seed, double originX, double originZ, SettlementParameters parameters, Square square,
        List<Road> roads, List<Plot> plots, List<Building> buildings, List<Prop> props, List<Lamp> lamps,
        List<Fence> fences, Clearing clearing, Grading grading, int[] order) {
        this.seed = seed;
        this.originX = originX;
        this.originZ = originZ;
        this.parameters = parameters;
        this.square = square;
        this.roads = List.copyOf(roads);
        this.plots = List.copyOf(plots);
        this.buildings = List.copyOf(buildings);
        this.props = List.copyOf(props);
        this.lamps = List.copyOf(lamps);
        this.fences = List.copyOf(fences);
        this.clearing = clearing;
        this.grading = grading;
        this.order = order;
    }

    public long seed() {
        return seed;
    }

    /** Where the square's centre was asked for, east. */
    public double originX() {
        return originX;
    }

    /** Where the square's centre was asked for, south. */
    public double originZ() {
        return originZ;
    }

    public SettlementParameters parameters() {
        return parameters;
    }

    public Square square() {
        return square;
    }

    public List<Road> roads() {
        return roads;
    }

    public List<Plot> plots() {
        return plots;
    }

    public List<Building> buildings() {
        return buildings;
    }

    public List<Prop> props() {
        return props;
    }

    public List<Lamp> lamps() {
        return lamps;
    }

    public List<Fence> fences() {
        return fences;
    }

    /** Where trees and grass may grow: kept open on the roads, plots, square and buildings. */
    public Clearing clearing() {
        return clearing;
    }

    /** The road meshes ({@link RoadMeshes#build}), made once when first asked for. */
    public List<RoadMeshes.Tile> roadMeshes() {
        List<RoadMeshes.Tile> meshes = roadMeshes;
        if (meshes == null) {
            synchronized (this) {
                meshes = roadMeshes;
                if (meshes == null) {
                    meshes = List.copyOf(RoadMeshes.build(this));
                    roadMeshes = meshes;
                }
            }
        }
        return meshes;
    }

    /** The terrain's heights under the roads and the square: their cuts and fills. */
    public Grading grading() {
        return grading;
    }

    /**
     * The step at which element {@code index} of {@code kind} (0 roads, 1 plots, 2 buildings) was made, for pictures
     * of the plan growing: roads first, then plots and their buildings, then the barns and their paths.
     */
    public int order(int kind, int index) {
        int offset = switch (kind) {
            case 0 -> 0;
            case 1 -> roads.size();
            case 2 -> roads.size() + plots.size();
            default -> throw new IllegalArgumentException("kind " + kind);
        };
        return order[offset + index];
    }

    /** The building whose footprint is round ({@code x}, {@code z}), or null. */
    public Building building(double x, double z) {
        for (Building b : buildings) {
            if (b.contains(x, z, 0)) {
                return b;
            }
        }
        return null;
    }

    /** An estimate of the plan's memory, bytes (an asset's size). */
    public long bytes() {
        long b = 4096 + clearing.bytes() + grading.bytes();
        for (Road r : roads) {
            b += 64 + 24L * r.count();
        }
        return b + 96L * (plots.size() + buildings.size() + props.size() + lamps.size()) + 64L * fences.size();
    }
}
