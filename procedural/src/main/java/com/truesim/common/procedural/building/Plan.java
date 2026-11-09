package com.truesim.common.procedural.building;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Aabb;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.Seeds;
import java.util.ArrayList;
import java.util.List;

/**
 * A building's layout, before any mesh: where it stands (its floor's height over the ground), its storeys and roof,
 * its walls and the openings in them, its partitions, rooms, hearth, chimney, balconies and woodpile, and its fittings
 * (anchors, rooms, openings). The meshes of every level are built from it ({@link Builder}); the fittings need only
 * it. Everything the spec leaves open is drawn from the seed here, so all levels agree.
 */
@AllowAllocation("planning, at load time")
final class Plan {

    /** The wall plate: the roof's underside stands this far above the walls' top at their outer face. */
    static final double PLATE = 0.2;
    /** The roof's thickness across the slope (boards, shingles or slates). */
    static final double ROOF = 0.22;
    /** A floor's thickness (boards on joists). */
    static final double SLAB = 0.22;
    /** A step's rise. */
    static final double RISE = 0.17;
    /** A step's tread. */
    static final double TREAD = 0.3;
    /** More steps than this run along the wall rather than straight out. */
    static final int STRAIGHT_STEPS = 4;
    /** A log's height in a Blockbau wall. */
    static final double COURSE = 0.2;
    /** How far log ends stand out past the corners. */
    static final double LOG_END = 0.24;

    /** A face of the main body. */
    enum Face {
        FRONT(0, 1), RIGHT(1, 0), BACK(0, -1), LEFT(-1, 0);

        final double nx;
        final double nz;

        Face(double nx, double nz) {
            this.nx = nx;
            this.nz = nz;
        }

        boolean side() {
            return this == RIGHT || this == LEFT;
        }
    }

    /**
     * An opening in a wall segment: {@code x} along its face from the face's start, {@code y} up from the segment's
     * bottom, its size, what it is and what dresses it.
     */
    record Hole(double x, double y, double w, double h, Opening.Kind kind, boolean shutters, boolean flowers,
        int room, boolean open) {

        double end() {
            return x + w;
        }
    }

    /**
     * A wall segment: on {@code face}, storey {@code storey}, from {@code y0} up {@code height}, {@code thick} inward,
     * along its face from {@code from} to {@code to}; its slots; logs (horizontal: the texture's grain turned along
     * the wall, log ends at its corners) or not (stone, plaster, upright boards).
     */
    record Segment(Face face, int storey, double y0, double height, double thick, double from, double to,
        BuildingSlot outer, BuildingSlot inner, BuildingSlot reveal, boolean logs, List<Hole> holes) {
    }

    /** An inner wall across the body at {@code z} (its front face), {@code thick} toward -z, with its doorway. */
    record Partition(double z, double y0, double height, double thick, double x0, double x1, List<Hole> holes,
        BuildingSlot front, BuildingSlot back) {
    }

    /** A rectangle of the ground plan a foundation goes round. */
    record Rect(double x0, double z0, double x1, double z1) {
    }

    /** A door that steps lead down from: its middle on the outer face, width, outward normal. */
    record Door(double x, double z, double width, double nx, double nz) {
    }

    final BuildingSpec spec;
    final BuildingOptions options;
    final BuildingKind kind;
    final Footprint footprint;
    private final GroundHeight ground;
    final Rng rng;

    final double w;
    final double d;
    final double hw;
    final double hd;
    /** The main body's back and front (z); the church's tower stands before its front. */
    final double z0;
    double z1;
    final int floors;
    final double[] storeyY;
    final double eaves;
    final double pitch;
    final double tan;
    final double cos;
    final double eave;
    final double gable;
    final RoofCover cover;
    /** The ground floor's world height. */
    final double floor;

    boolean plastered;
    int motif;
    double barnZ = Double.NaN;
    double partitionZ = Double.NaN;
    /** The hearth: x and the z of the wall it stands against (facing +z), or null. */
    double[] hearth;
    /** The chimney's middle: x, z, or null. */
    double[] chimney;
    /** The woodpile: side (+1 or -1: against the right or left wall), z from, z to; or null. */
    double[] woodpile;
    final List<Integer> balconies = new ArrayList<>();
    boolean ruendi;
    /** The church's tower: its side, the top of its walls, its walls' thickness and openings per face. */
    double towerSize;
    double towerTop;
    static final double TOWER_WALL = 0.7;
    final List<List<Hole>> towerHoles = faces();

    final List<Segment> walls = new ArrayList<>();
    final List<Partition> partitions = new ArrayList<>();
    final List<Rect> foundations = new ArrayList<>();
    final List<Door> doors = new ArrayList<>();
    /** Per door, which way its stair runs along the wall (1: along, -1: back), or 0 straight out. */
    final List<Integer> stairs = new ArrayList<>();
    final List<Anchor> anchors = new ArrayList<>();
    final List<Room> rooms = new ArrayList<>();
    final List<Opening> openings = new ArrayList<>();

    Plan(BuildingSpec spec, GroundHeight ground, BuildingOptions options) {
        this.spec = spec;
        this.options = options;
        this.kind = spec.kind();
        this.footprint = spec.footprint();
        this.ground = ground;
        this.rng = Rng.seeded(Seeds.derive(spec.seed(), 1));
        w = footprint.width();
        d = footprint.depth();
        hw = w / 2;
        hd = d / 2;
        z0 = -hd;
        z1 = hd;
        floors = spec.floors();
        storeyY = new double[floors + 1];
        for (int s = 1; s <= floors; s++) {
            storeyY[s] = kind.groundStorey() + (s - 1) * kind.upperStorey();
        }
        eaves = storeyY[floors];
        pitch = Maths.toRadians(spec.pitch());
        tan = Maths.tan(pitch);
        cos = Maths.cos(pitch);
        double scale = Math.min(1, Math.min(w, d) / Math.min(kind.width(), kind.depth()));
        eave = kind.eaveOverhang() * Math.max(0.6, scale);
        gable = kind.gableOverhang() * Math.max(0.6, scale);
        cover = spec.roof();
        // the floor: above the highest ground under the footprint
        double highest = Double.NEGATIVE_INFINITY;
        int n = 8;
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                double lx = -hw + w * i / n;
                double lz = -hd + d * j / n;
                highest = Math.max(highest, ground.height(footprint.worldX(lx, lz), footprint.worldZ(lx, lz)));
            }
        }
        if (kind == BuildingKind.HAY_BARN) {
            // dug into the slope: the stable's door at the ground before it, the hay door at the ground behind it
            double front = Double.POSITIVE_INFINITY;
            for (int i = 0; i <= n; i++) {
                double lx = -hw + w * i / n;
                front = Math.min(front, ground.height(footprint.worldX(lx, hd), footprint.worldZ(lx, hd)));
            }
            floor = Math.min(highest, Math.max(front, highest - (kind.groundStorey() - 0.45))) + options.clearance();
        } else {
            floor = highest + options.clearance();
        }
        motif = rng.nextInt(3);
        switch (kind) {
            case CHALET -> house(false);
            case INN -> house(true);
            case FARMHOUSE -> farmhouse();
            case HAY_BARN -> hayBarn();
            case CHURCH -> church();
            case WOODSHED -> woodshed();
            default -> throw new IllegalStateException();
        }
        if (foundations.isEmpty()) {
            foundations.add(new Rect(-hw, z0, hw, z1));
        }
        cellarDoors();
        fittings();
    }

    // ---------------------------------------------------------------- the frame

    /** The ground's height at the local point (lx, lz), relative to the floor. */
    double ground(double lx, double lz) {
        return ground.height(footprint.worldX(lx, lz), footprint.worldZ(lx, lz)) - floor;
    }

    /** The lowest ground along the segment from (ax, az) to (bx, bz), sampled every 0.2 m and at its ends. */
    double lowest(double ax, double az, double bx, double bz) {
        double len = Maths.hypot(bx - ax, bz - az);
        int n = Math.max(1, (int) Math.ceil(len / 0.2));
        double low = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            low = Math.min(low, ground(ax + (bx - ax) * f, az + (bz - az) * f));
        }
        return low;
    }

    /** The lowest ground over the rectangle (x0..x1, za..zb). */
    double lowest(double x0, double za, double x1, double zb, boolean area) {
        double low = Double.POSITIVE_INFINITY;
        int nx = Math.max(1, (int) Math.ceil(Math.abs(x1 - x0) / 0.25));
        int nz = Math.max(1, (int) Math.ceil(Math.abs(zb - za) / 0.25));
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= nz; j++) {
                low = Math.min(low, ground(x0 + (x1 - x0) * i / nx, za + (zb - za) * j / nz));
            }
        }
        return low;
    }

    /** The highest ground over the rectangle. */
    double highest(double x0, double za, double x1, double zb) {
        double high = Double.NEGATIVE_INFINITY;
        int nx = Math.max(1, (int) Math.ceil(Math.abs(x1 - x0) / 0.25));
        int nz = Math.max(1, (int) Math.ceil(Math.abs(zb - za) / 0.25));
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= nz; j++) {
                high = Math.max(high, ground(x0 + (x1 - x0) * i / nx, za + (zb - za) * j / nz));
            }
        }
        return high;
    }

    /** The roof's underside over x (the main body's roof). */
    double underside(double x) {
        return eaves + PLATE + (hw - Math.abs(x)) * tan;
    }

    /** The roof's top over x. */
    double top(double x) {
        return underside(x) + ROOF / cos;
    }

    /** The ridge's height (the roof's top at x = 0). */
    double ridge() {
        return top(0);
    }

    /** Where {@code face}'s wall starts: its outer face's lower left corner, seen from outside. */
    double[] start(Face face) {
        return switch (face) {
            case FRONT -> Kit.p(-hw, 0, z1);
            case RIGHT -> Kit.p(hw, 0, z1);
            case BACK -> Kit.p(hw, 0, z0);
            case LEFT -> Kit.p(-hw, 0, z0);
        };
    }

    /** Where the church tower's {@code face} wall starts. */
    double[] towerStart(Face face) {
        double ts = towerSize / 2;
        double tz0 = hd - towerSize;
        return switch (face) {
            case FRONT -> Kit.p(-ts, 0, hd);
            case RIGHT -> Kit.p(ts, 0, hd);
            case BACK -> Kit.p(ts, 0, tz0);
            case LEFT -> Kit.p(-ts, 0, tz0);
        };
    }

    /** Along {@code face}'s wall, to the right seen from outside. */
    static double[] along(Face face) {
        return Kit.p(face.nz, 0, -face.nx);
    }

    /** Into the building from {@code face}. */
    static double[] inward(Face face) {
        return Kit.p(-face.nx, 0, -face.nz);
    }

    /** The length of {@code face}'s wall. */
    double length(Face face) {
        return face.side() ? z1 - z0 : w;
    }

    /** The point {@code x} along {@code face} from its start, {@code y} up, {@code depth} in from its outer face. */
    double[] point(Face face, double x, double y, double depth) {
        double[] s = start(face);
        double[] a = along(face);
        double[] in = inward(face);
        return Kit.p(s[0] + a[0] * x + in[0] * depth, y, s[2] + a[2] * x + in[2] * depth);
    }

    // ---------------------------------------------------------------- kinds

    private BuildingSlot groundOuter() {
        return plastered ? BuildingSlot.PLASTER : BuildingSlot.STONE;
    }

    /** A storey's wall on every face, the sides between the front and back walls. */
    private void storey(int s, double thick, BuildingSlot outer, BuildingSlot inner, BuildingSlot reveal,
        boolean logs, List<List<Hole>> holes) {
        double h = storeyY[s + 1] - storeyY[s];
        for (Face face : Face.values()) {
            double from = face.side() ? thick : 0;
            double to = length(face) - from;
            walls.add(new Segment(face, s, storeyY[s], h, thick, from, to, outer, inner, reveal, logs,
                holes.get(face.ordinal())));
        }
    }

    /** {@code count} windows evenly between {@code from} and {@code to}, those overlapping {@code avoid} left out. */
    private List<Hole> row(double from, double to, int count, double width, double height, double sill,
        boolean shutters, double flowers, int room, double[]... avoid) {
        List<Hole> out = new ArrayList<>();
        if (count <= 0 || to - from < width) {
            return out;
        }
        for (int i = 0; i < count; i++) {
            double c = from + (to - from) * (i + 0.5) / count;
            double x = c - width / 2;
            boolean clear = true;
            for (double[] a : avoid) {
                clear &= x + width + 0.2 < a[0] || x - 0.2 > a[1];
            }
            if (clear) {
                out.add(new Hole(x, sill, width, height, Opening.Kind.WINDOW, shutters, rng.nextDouble() < flowers,
                    room, false));
            }
        }
        return out;
    }

    private static List<List<Hole>> faces() {
        List<List<Hole>> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            out.add(new ArrayList<>());
        }
        return out;
    }

    /** A chalet, or an inn: the stone or plastered ground floor, log floors above, balconies, a hearth, a woodpile. */
    private void house(boolean inn) {
        plastered = inn || rng.nextDouble() < 0.34;
        double t0 = 0.5;
        double t1 = 0.22;
        double inner = z1 - z0 - 2 * t0;
        boolean partition = d >= 8.5;
        partitionZ = partition ? z0 + t0 + inner * (inn ? 0.38 : 0.4) : Double.NaN;
        int parlour = 0;
        int kitchen = partition ? 1 : 0;
        // the hearth against the partition (or the back wall), to one side; its chimney above
        double side = rng.nextBoolean() ? 1 : -1;
        double hx = side * Math.min(hw * 0.32, hw - t0 - 1.3);
        double wallZ = partition ? partitionZ : z0 + t0;
        hearth = new double[] {hx, wallZ};
        chimney = new double[] {hx, wallZ + 0.3};
        // the woodpile against one side wall, at the back
        double woodSide = rng.nextBoolean() ? 1 : -1;
        double wz0 = z0 + 0.7;
        double wz1 = Math.min(wz0 + Math.min(3.6, d * 0.38), z1 - 2);
        woodpile = new double[] {woodSide, wz0, wz1};
        boolean shutters0 = !inn && rng.nextDouble() < 0.65;
        boolean shutters1 = rng.nextDouble() < 0.4;
        double flowers = inn ? 0.7 : 0.5;
        // the ground floor
        List<List<Hole>> g = faces();
        double doorW = inn ? 1.3 : 1.0;
        double doorX = inn ? hw - doorW / 2 : Math.max(t0 + 0.7, w * 0.28 - doorW / 2);
        g.get(Face.FRONT.ordinal()).add(new Hole(doorX, 0, doorW, inn ? 2.25 : 2.05, Opening.Kind.DOOR, false,
            false, parlour, inn));
        double[] door = {doorX, doorX + doorW};
        double ww = inn ? 1.25 : 0.9;
        double wh = inn ? 1.45 : 1.05;
        double sill = inn ? 0.75 : 0.95;
        if (inn) {
            g.get(Face.FRONT.ordinal()).addAll(row(t0 + 0.4, doorX - 0.3, 2, ww, wh, sill, false, 0, parlour));
            g.get(Face.FRONT.ordinal()).addAll(row(doorX + doorW + 0.3, w - t0 - 0.4, 2, ww, wh, sill, false, 0,
                parlour));
        } else {
            g.get(Face.FRONT.ordinal()).addAll(row(doorX + doorW + 0.4, w - t0 - 0.5, Math.max(1, (int) ((w - doorX
                - doorW - 1) / 2.2)), ww, wh, sill, shutters0, flowers, parlour));
        }
        double sideLen = z1 - z0;
        // partition and woodpile zones on the sides (measured along each side from its start)
        double[] rightPartition = partition ? new double[] {z1 - partitionZ - 0.2, z1 - partitionZ + 0.4}
            : new double[] {-9, -9};
        double[] leftPartition = partition ? new double[] {partitionZ - 0.4 - z0, partitionZ + 0.2 - z0}
            : new double[] {-9, -9};
        double[] rightWood = woodSide > 0 ? new double[] {z1 - wz1, z1 - wz0} : new double[] {-9, -9};
        double[] leftWood = woodSide < 0 ? new double[] {wz0 - z0, wz1 - z0} : new double[] {-9, -9};
        int sideCount = Math.max(2, (int) Math.round((sideLen - 2) / 2.6));
        g.get(Face.RIGHT.ordinal()).addAll(roomRow(row(t0 + 0.5, sideLen - t0 - 0.5, sideCount, ww, wh, sill,
            shutters0, 0.25, parlour, rightPartition, rightWood), Face.RIGHT, parlour, kitchen));
        g.get(Face.LEFT.ordinal()).addAll(roomRow(row(t0 + 0.5, sideLen - t0 - 0.5, sideCount, ww, wh, sill,
            shutters0, 0.25, parlour, leftPartition, leftWood), Face.LEFT, parlour, kitchen));
        double[] backHearth = partition ? new double[] {-9, -9} : new double[] {hw - hx - 1.1, hw - hx + 1.1};
        g.get(Face.BACK.ordinal()).addAll(row(t0 + 0.5, w - t0 - 0.5, partition ? 2 : 1, ww, wh, sill, shutters0,
            0.2, kitchen, backHearth));
        storey(0, t0, groundOuter(), BuildingSlot.INTERIOR, groundOuter(), false, g);
        // the upper floors: log walls, a band of windows on the front
        for (int s = 1; s < floors; s++) {
            int room = (partition ? 2 : 1) + (s - 1);
            List<List<Hole>> u = faces();
            int band = Math.max(2, (int) ((w - 1.4) / (inn ? 1.15 : 1.25)));
            u.get(Face.FRONT.ordinal()).addAll(row(t1 + 0.35, w - t1 - 0.35, band, 0.8, 1.05, 0.75, false, 0, room));
            int sides = Math.max(2, (int) Math.round((sideLen - 2) / 2.4));
            u.get(Face.RIGHT.ordinal()).addAll(row(t1 + 0.6, sideLen - t1 - 0.6, sides, 0.8, 1.05, 0.75,
                shutters1, flowers, room));
            u.get(Face.LEFT.ordinal()).addAll(row(t1 + 0.6, sideLen - t1 - 0.6, sides, 0.8, 1.05, 0.75, shutters1,
                flowers, room));
            u.get(Face.BACK.ordinal()).addAll(row(t1 + 0.6, w - t1 - 0.6, 2, 0.8, 1.05, 0.75, shutters1, 0.3,
                room));
            storey(s, t1, BuildingSlot.TIMBER, BuildingSlot.TIMBER, BuildingSlot.TRIM, true, u);
            balconies.add(s);
        }
        if (partition) {
            double px0 = -hw + t0;
            partitions.add(new Partition(partitionZ, 0, storeyY[1] - SLAB, 0.16, px0, hw - t0,
                List.of(new Hole(side > 0 ? 0.5 : w - 2 * t0 - 1.4, 0, 0.9, 2.0, Opening.Kind.DOOR, false, false,
                    -1, true)), BuildingSlot.INTERIOR, BuildingSlot.INTERIOR));
        }
        doors.add(new Door(-hw + doorX + doorW / 2, z1, doorW, 0, 1));
        rooms(inn ? "parlour" : "parlour", partition, t0, t1);
    }

    /** The rooms of a house: the ground floor's parlour (and kitchen behind the partition), a chamber per floor. */
    private void rooms(String front, boolean partition, double t0, double t1) {
        double top0 = storeyY[1] - SLAB;
        if (partition) {
            rooms.add(new Room(front, 0, box(-hw + t0, 0, partitionZ, hw - t0, top0, z1 - t0)));
            rooms.add(new Room("kitchen", 0, box(-hw + t0, 0, z0 + t0, hw - t0, top0, partitionZ - 0.16)));
        } else {
            rooms.add(new Room(front, 0, box(-hw + t0, 0, z0 + t0, hw - t0, top0, z1 - t0)));
        }
        for (int s = 1; s < floors; s++) {
            rooms.add(new Room("chamber", s, box(-hw + t1, storeyY[s], z0 + t1, hw - t1, storeyY[s + 1] - SLAB,
                z1 - t1)));
        }
    }

    /** Assigns side windows to the parlour or the kitchen by where they are. */
    private List<Hole> roomRow(List<Hole> holes, Face face, int parlour, int kitchen) {
        if (Double.isNaN(partitionZ)) {
            return holes;
        }
        List<Hole> out = new ArrayList<>();
        for (Hole h : holes) {
            double c = h.x() + h.w() / 2;
            double z = face == Face.RIGHT ? z1 - c : z0 + c;
            out.add(new Hole(h.x(), h.y(), h.w(), h.h(), h.kind(), h.shutters(), h.flowers(),
                z > partitionZ ? parlour : kitchen, h.open()));
        }
        return out;
    }

    private static Aabb box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new Aabb(new Vec3(x0, y0, z0), new Vec3(x1, y1, z1));
    }

    /** A farmhouse: the house at the front, the barn behind under one roof, an arched gable board. */
    private void farmhouse() {
        plastered = rng.nextDouble() < 0.25;
        ruendi = true;
        double t0 = 0.5;
        double t1 = 0.22;
        double living = Math.clamp(d * 0.5, 6, d - 4);
        barnZ = z1 - living;
        double innerLiving = living - t0 - 0.2;
        partitionZ = barnZ + 0.2 + innerLiving * 0.42;
        double side = rng.nextBoolean() ? 1 : -1;
        double hx = side * Math.min(hw * 0.32, hw - t0 - 1.3);
        hearth = new double[] {hx, partitionZ};
        chimney = new double[] {hx, partitionZ + 0.3};
        double woodSide = rng.nextBoolean() ? 1 : -1;
        woodpile = new double[] {woodSide, barnZ + 0.4, Math.min(barnZ + 0.4 + 3.4, partitionZ - 0.2)};
        boolean shutters0 = rng.nextDouble() < 0.6;
        boolean shutters1 = rng.nextDouble() < 0.4;
        int parlour = 0;
        int kitchen = 1;
        double sideLen = z1 - z0;
        // the ground floor: the house's front and its sides' front part; the stable behind
        double doorX = Math.max(t0 + 0.7, w * 0.3 - 0.5);
        List<Hole> front = new ArrayList<>();
        front.add(new Hole(doorX, 0, 1.0, 2.05, Opening.Kind.DOOR, false, false, parlour, false));
        front.addAll(row(doorX + 1.4, w - t0 - 0.5, Math.max(1, (int) ((w - doorX - 2) / 2.2)), 0.9, 1.05, 0.95,
            shutters0, 0.5, parlour));
        double livingSide = living;
        double[] rightWood = woodSide > 0 ? new double[] {z1 - woodpile[2], z1 - woodpile[1]} : new double[] {-9, -9};
        double[] leftWood = woodSide < 0 ? new double[] {woodpile[1] - z0, woodpile[2] - z0} : new double[] {-9, -9};
        double[] rightPart = {z1 - partitionZ - 0.2, z1 - partitionZ + 0.4};
        double[] leftPart = {partitionZ - z0 - 0.4, partitionZ - z0 + 0.2};
        List<Hole> right = roomRow(row(t0 + 0.5, livingSide - 0.4, 2, 0.9, 1.05, 0.95, shutters0, 0.25, parlour,
            rightPart, rightWood), Face.RIGHT, parlour, kitchen);
        List<Hole> left = new ArrayList<>(roomRow(row(sideLen - livingSide + 0.4, sideLen - t0 - 0.5, 2, 0.9, 1.05,
            0.95, shutters0, 0.25, parlour, leftPart, leftWood), Face.LEFT, parlour, kitchen));
        // the stable: a door on the right, small windows
        double barnLen = sideLen - living;
        right.add(new Hole(living + barnLen * 0.45, 0, 1.1, 1.95, Opening.Kind.DOOR, false, false, -1, false));
        right.addAll(row(living + barnLen * 0.65, sideLen - t0 - 0.3, 1, 0.55, 0.45, 1.35, false, 0, -1));
        left.addAll(row(t0 + 0.4, barnLen - 0.3, 2, 0.55, 0.45, 1.35, false, 0, -1));
        List<Hole> back = row(t0 + 0.5, w - t0 - 0.5, 2, 0.55, 0.45, 1.35, false, 0, -1);
        segments(0, t0, front, right, back, left, false);
        // the upper floors: log walls at the front, boards over the barn with its hay door and slits
        for (int s = 1; s < floors; s++) {
            int room = 2 + (s - 1);
            List<Hole> uFront = row(t1 + 0.35, w - t1 - 0.35, Math.max(2, (int) ((w - 1.4) / 1.25)), 0.8, 1.05,
                0.75, false, 0, room);
            List<Hole> uRight = new ArrayList<>(row(t1 + 0.6, livingSide - 0.5, 2, 0.8, 1.05, 0.75, shutters1, 0.5,
                room));
            List<Hole> uLeft = new ArrayList<>(row(sideLen - livingSide + 0.5, sideLen - t1 - 0.6, 2, 0.8, 1.05,
                0.75, shutters1, 0.5, room));
            if (s == 1) {
                uLeft.add(new Hole(barnLen * 0.5 - 1.1, 0.15, 2.2, 2.0, Opening.Kind.HAY_DOOR, false, false, -1,
                    false));
                for (int i = 0; i < 3; i++) {
                    uRight.add(new Hole(living + barnLen * (0.25 + 0.25 * i) - 0.06, 0.8, 0.12, 0.7,
                        Opening.Kind.SLIT, false, false, -1, false));
                }
            }
            segments(s, t1, uFront, uRight, new ArrayList<>(), uLeft, true);
            balconies.add(s);
        }
        for (int s = 0; s < floors; s++) {
            partitions.add(new Partition(barnZ + 0.2, storeyY[s], storeyY[s + 1] - storeyY[s] - (s == 0 ? SLAB : 0),
                0.2, -hw + (s == 0 ? t0 : t1), hw - (s == 0 ? t0 : t1), List.of(), BuildingSlot.INTERIOR,
                BuildingSlot.TIMBER));
        }
        partitions.add(new Partition(partitionZ, 0, storeyY[1] - SLAB, 0.16, -hw + t0, hw - t0,
            List.of(new Hole(side > 0 ? 0.5 : w - 2 * t0 - 1.4, 0, 0.9, 2.0, Opening.Kind.DOOR, false, false, -1,
                true)), BuildingSlot.INTERIOR, BuildingSlot.INTERIOR));
        doors.add(new Door(-hw + doorX + 0.5, z1, 1.0, 0, 1));
        double stableC = z1 - (living + barnLen * 0.45 + 0.55);
        doors.add(new Door(hw, stableC, 1.1, 1, 0));
        double top0 = storeyY[1] - SLAB;
        rooms.add(new Room("parlour", 0, box(-hw + t0, 0, partitionZ, hw - t0, top0, z1 - t0)));
        rooms.add(new Room("kitchen", 0, box(-hw + t0, 0, barnZ + 0.2, hw - t0, top0, partitionZ - 0.16)));
        for (int s = 1; s < floors; s++) {
            rooms.add(new Room("chamber", s, box(-hw + t1, storeyY[s], barnZ + 0.2, hw - t1, storeyY[s + 1] - SLAB,
                z1 - t1)));
        }
    }

    /**
     * A storey of the farmhouse: the front (living) and back (barn) walls, each side split where the barn begins; the
     * upper floor's barn clad in boards, the house in logs.
     */
    private void segments(int s, double thick, List<Hole> front, List<Hole> right, List<Hole> back, List<Hole> left,
        boolean upper) {
        double h = storeyY[s + 1] - storeyY[s];
        double split = z1 - barnZ; // along the right side from its start (the front); the left side runs back to front
        BuildingSlot houseOuter = upper ? BuildingSlot.TIMBER : groundOuter();
        BuildingSlot houseInner = upper ? BuildingSlot.TIMBER : BuildingSlot.INTERIOR;
        BuildingSlot houseReveal = upper ? BuildingSlot.TRIM : groundOuter();
        BuildingSlot barnOuter = upper ? BuildingSlot.TIMBER : BuildingSlot.STONE;
        BuildingSlot barnReveal = upper ? BuildingSlot.TRIM : BuildingSlot.STONE;
        double barnThick = upper ? 0.08 : thick;
        double len = z1 - z0;
        walls.add(new Segment(Face.FRONT, s, storeyY[s], h, thick, 0, w, houseOuter, houseInner, houseReveal, upper,
            front));
        walls.add(new Segment(Face.BACK, s, storeyY[s], h, barnThick, 0, w, barnOuter, barnOuter, barnReveal, false,
            back));
        walls.add(new Segment(Face.RIGHT, s, storeyY[s], h, thick, thick, split, houseOuter, houseInner,
            houseReveal, upper, split(right, thick, split)));
        walls.add(new Segment(Face.RIGHT, s, storeyY[s], h, barnThick, split, len - barnThick, barnOuter,
            barnOuter, barnReveal, false, split(right, split, len)));
        walls.add(new Segment(Face.LEFT, s, storeyY[s], h, barnThick, barnThick, len - split, barnOuter, barnOuter,
            barnReveal, false, split(left, 0, len - split)));
        walls.add(new Segment(Face.LEFT, s, storeyY[s], h, thick, len - split, len - thick, houseOuter, houseInner,
            houseReveal, upper, split(left, len - split, len)));
    }

    private static List<Hole> split(List<Hole> holes, double from, double to) {
        List<Hole> out = new ArrayList<>();
        for (Hole h : holes) {
            if (h.x() >= from && h.end() <= to) {
                out.add(h);
            }
        }
        return out;
    }

    /** A hay barn: a stone stable, a log loft, a hay door, slits. */
    private void hayBarn() {
        double t0 = 0.45;
        double t1 = 0.18;
        double sideLen = z1 - z0;
        List<List<Hole>> g = faces();
        double doorW = 0.95;
        g.get(Face.FRONT.ordinal()).add(new Hole(hw - doorW / 2 + (rng.nextDouble() - 0.5) * 0.6, 0, doorW, 1.8,
            Opening.Kind.DOOR, false, false, -1, false));
        g.get(Face.RIGHT.ordinal()).add(new Hole(sideLen * 0.5 - 0.17, 1.25, 0.34, 0.3, Opening.Kind.SLIT, false,
            false, -1, false));
        g.get(Face.LEFT.ordinal()).add(new Hole(sideLen * 0.5 - 0.17, 1.25, 0.34, 0.3, Opening.Kind.SLIT, false,
            false, -1, false));
        storey(0, t0, BuildingSlot.STONE, BuildingSlot.STONE, BuildingSlot.STONE, false, g);
        for (int s = 1; s < floors; s++) {
            List<List<Hole>> u = faces();
            u.get(Face.BACK.ordinal()).add(new Hole(hw - 0.7, 0.25, 1.4, 1.5, Opening.Kind.HAY_DOOR, false, false,
                -1, false));
            for (Face f : new Face[] {Face.RIGHT, Face.LEFT}) {
                for (int i = 0; i < 2; i++) {
                    u.get(f.ordinal()).add(new Hole(sideLen * (0.33 + 0.34 * i) - 0.05, 0.9, 0.1, 0.6,
                        Opening.Kind.SLIT, false, false, -1, false));
                }
            }
            u.get(Face.FRONT.ordinal()).add(new Hole(hw - 0.25, 1.0, 0.5, 0.5, Opening.Kind.SLIT, false, false, -1,
                false));
            storey(s, t1, BuildingSlot.TIMBER, BuildingSlot.TIMBER, BuildingSlot.TRIM, true, u);
        }
        doors.add(new Door(g.get(0).getFirst().x() + doorW / 2 - hw, z1, doorW, 0, 1));
    }

    /** A church: a nave, a tower before its front with a belfry and a spire. */
    private void church() {
        plastered = true;
        towerSize = Math.clamp(w * 0.45, 3.2, 4.6);
        double t = 0.8;
        double tt = 0.7;
        z1 = hd - towerSize + 0.35; // the nave's front wall, the tower before it
        double sideLen = z1 - z0;
        List<List<Hole>> g = faces();
        int count = Math.max(2, (int) ((sideLen - 3) / 3.2));
        for (Face f : new Face[] {Face.RIGHT, Face.LEFT}) {
            g.get(f.ordinal()).addAll(row(t + 0.8, sideLen - t - 0.8, count, 1.1, 3.0, 2.4, false, 0, 0));
        }
        g.get(Face.FRONT.ordinal()).add(new Hole(hw - 0.8, 0, 1.6, 2.8, Opening.Kind.DOOR, false, false, 0, true));
        g.get(Face.BACK.ordinal()).add(new Hole(hw - 0.5, 3.6, 1.0, 1.6, Opening.Kind.WINDOW, false, false, 0,
            false));
        storey(0, t, BuildingSlot.PLASTER, BuildingSlot.INTERIOR, BuildingSlot.PLASTER, false, g);
        towerTop = ridge() + 5.2;
        foundations.add(new Rect(-hw, z0, hw, z1));
        double ts = towerSize / 2;
        foundations.add(new Rect(-ts, hd - towerSize, ts, hd));
        // the tower: a door through it into the nave, slits up it, the belfry's openings
        double ts2 = towerSize / 2;
        for (Face f : Face.values()) {
            List<Hole> holes = towerHoles.get(f.ordinal());
            if (f == Face.FRONT || f == Face.BACK) {
                holes.add(new Hole(ts2 - 0.8, 0, 1.6, 2.8, Opening.Kind.DOOR, false, false, 1, true));
            }
            if (f != Face.BACK && towerTop > 12) {
                holes.add(new Hole(ts2 - 0.18, 6.0, 0.36, 1.0, Opening.Kind.SLIT, false, false, 1, false));
            }
            holes.add(new Hole(ts2 - 0.55, towerTop - 3.0, 1.1, 2.0, Opening.Kind.BELFRY, false, false, -1, false));
        }
        rooms.add(new Room("nave", 0, box(-hw + t, 0, z0 + t, hw - t, storeyY[1] - 0.3, z1 - t)));
        rooms.add(new Room("tower", 0, box(-ts + tt, 0, hd - towerSize + tt, ts - tt, 4, hd - tt)));
        doors.add(new Door(0, hd, 1.6, 0, 1));
    }

    /** A woodshed: no walls of its own (posts and boards), its foundation, its logs. */
    private void woodshed() {
        woodpile = new double[] {0, z0 + 0.15, z1 - 0.35};
    }

    // ---------------------------------------------------------------- slope

    /** Where the foundation stands tall (2 m and more at a face's middle), a door into it: a cellar or stable. */
    private void cellarDoors() {
        if (kind == BuildingKind.WOODSHED) {
            return;
        }
        for (Face face : Face.values()) {
            if (kind == BuildingKind.CHURCH && face == Face.FRONT) {
                continue; // the tower stands there
            }
            double len = length(face);
            double[] mid = point(face, len / 2, 0, 0);
            double g = ground(mid[0] + face.nx * 0.3, mid[2] + face.nz * 0.3);
            if (-g < 2.1) {
                continue;
            }
            boolean taken = false;
            for (Door door : doors) {
                taken |= door.nx() == face.nx && door.nz() == face.nz;
            }
            if (woodpile != null && face.side() && woodpile[0] == face.nx) {
                taken = true;
            }
            double x = taken ? len * 0.2 : len / 2;
            double[] at = point(face, x, 0, 0);
            double base = Math.max(ground(at[0] + face.nx * 0.3, at[2] + face.nz * 0.3),
                ground(at[0] + face.nx * 0.3 + along(face)[0], at[2] + face.nz * 0.3 + along(face)[2]));
            if (-base < 2.1) {
                continue;
            }
            anchors.add(new Anchor(Anchor.Kind.CELLAR_DOOR, new Vec3(at[0] + face.nx * 0.14, base, at[2]
                + face.nz * 0.14), Maths.atan2(face.nx, face.nz), -1));
        }
    }

    // ---------------------------------------------------------------- fittings

    private void anchor(Anchor.Kind kind, double x, double y, double z, double yaw, int room) {
        anchors.add(new Anchor(kind, new Vec3(x, y, z), yaw, room));
    }

    /** The anchors and openings of everything planned. */
    private void fittings() {
        for (Segment s : walls) {
            double[] start = start(s.face());
            double[] a = along(s.face());
            for (Hole h : s.holes()) {
                double c = h.x() + h.w() / 2;
                Vec3 centre = new Vec3(start[0] + a[0] * c, s.y0() + h.y() + h.h() / 2, start[2] + a[2] * c);
                openings.add(new Opening(h.kind(), centre, new Vec3(s.face().nx, 0, s.face().nz), h.w(), h.h(),
                    s.thick(), h.room()));
            }
        }
        for (Face f : Face.values()) {
            double[] start = towerStart(f);
            double[] a = along(f);
            for (Hole h : towerHoles.get(f.ordinal())) {
                double c = h.x() + h.w() / 2;
                Vec3 centre = new Vec3(start[0] + a[0] * c, h.y() + h.h() / 2, start[2] + a[2] * c);
                openings.add(new Opening(h.kind(), centre, new Vec3(f.nx, 0, f.nz), h.w(), h.h(), TOWER_WALL,
                    h.room()));
            }
        }
        double yawFront = 0;
        for (Door door : doors) {
            stairs.add(stair(door));
        }
        for (Door door : doors) {
            double yaw = Maths.atan2(door.nx(), door.nz());
            int stair = stairs.get(doors.indexOf(door));
            anchor(Anchor.Kind.DOOR, door.x() + door.nx() * 0.05, 0, door.z() + door.nz() * 0.05, yaw, -1);
            if (kind == BuildingKind.HAY_BARN) {
                continue;
            }
            double[] a = {door.nz(), 0, -door.nx()};
            double off = door.width() / 2 + 0.45;
            double y = kind == BuildingKind.CHURCH ? 2.9 : 2.25;
            boolean main = door == doors.getFirst();
            for (int side = -1; side <= 1; side += 2) {
                if (!main && side < 0) {
                    continue;
                }
                anchor(Anchor.Kind.LANTERN, door.x() + a[0] * off * side + door.nx() * 0.22, y, door.z() + a[2] * off
                    * side + door.nz() * 0.22, yaw, -1);
            }
            if (main && kind != BuildingKind.CHURCH) {
                double b = (stair > 0 ? -1 : 1) * (door.width() / 2 + 1.5);
                anchor(Anchor.Kind.BENCH, door.x() + a[0] * b + door.nx() * 0.45, groundAt(door, b), door.z() + a[2]
                    * b + door.nz() * 0.45, yaw, -1);
            }
        }
        if (hearth != null && options.interiors()) {
            anchor(Anchor.Kind.FIRE, hearth[0], 0.15, hearth[1] + 0.35, yawFront, 0);
        }
        if (chimney != null) {
            anchor(Anchor.Kind.CHIMNEY, chimney[0], chimneyTop() + 0.12, chimney[1], 0, -1);
        }
        if (woodpile != null) {
            double x = kind == BuildingKind.WOODSHED ? 0 : woodpile[0] * (hw + 0.3);
            double z = (woodpile[1] + woodpile[2]) / 2;
            anchor(Anchor.Kind.WOODPILE, x, lowest(x - 0.3, woodpile[1], x + 0.3, woodpile[2], true), z,
                kind == BuildingKind.WOODSHED ? 0 : Maths.atan2(woodpile[0], 0), -1);
        }
        for (int r = 0; r < rooms.size(); r++) {
            Room room = rooms.get(r);
            Aabb b = room.bounds();
            if (kind == BuildingKind.CHURCH) {
                continue;
            }
            anchor(Anchor.Kind.CEILING_LAMP, (b.min().x() + b.max().x()) / 2, b.max().y() - 0.45,
                (b.min().z() + b.max().z()) / 2, 0, r);
        }
        if (kind == BuildingKind.CHURCH) {
            churchFittings();
        } else if (kind.inhabited()) {
            furniture();
        }
    }

    /** Which way a door's stair runs: straight out when a few steps reach the ground, else along the wall uphill. */
    private int stair(Door door) {
        double[] a = {door.nz(), 0, -door.nx()};
        double g = ground(door.x() + door.nx() * 0.9, door.z() + door.nz() * 0.9);
        if (-g <= STRAIGHT_STEPS * RISE) {
            return 0;
        }
        double reach = door.width() / 2 + 2.5;
        double plus = ground(door.x() + a[0] * reach + door.nx() * 0.6, door.z() + a[2] * reach + door.nz() * 0.6);
        double minus = ground(door.x() - a[0] * reach + door.nx() * 0.6, door.z() - a[2] * reach + door.nz() * 0.6);
        return plus >= minus ? 1 : -1;
    }

    private double groundAt(Door door, double along) {
        double[] a = {door.nz(), 0, -door.nx()};
        return ground(door.x() + a[0] * along + door.nx() * 0.45, door.z() + a[2] * along + door.nz() * 0.45);
    }

    /** The chimney's top: above the ridge's height over its x. */
    double chimneyTop() {
        return top(chimney[0]) + 0.9;
    }

    /** Tables, chairs, cabinets, beds, the inn's bar, sign, name board and terrace. */
    private void furniture() {
        boolean inn = kind == BuildingKind.INN;
        Aabb parlour = rooms.getFirst().bounds();
        double cx = (parlour.min().x() + parlour.max().x()) / 2;
        double cz = (parlour.min().z() + parlour.max().z()) / 2;
        double q = Maths.HALF_PI;
        if (inn) {
            double sx = (parlour.max().x() - parlour.min().x()) / 4;
            for (int i = -1; i <= 1; i++) {
                for (int j = 0; j < 2; j++) {
                    double x = cx + sx * i * 1.15;
                    double z = parlour.min().z() + 1.6 + j * 2.2;
                    if (z > parlour.max().z() - 1) {
                        continue;
                    }
                    anchor(Anchor.Kind.TABLE, x, 0, z, 0, 0);
                    anchor(Anchor.Kind.CHAIR, x - 0.75, 0, z, q, 0);
                    anchor(Anchor.Kind.CHAIR, x + 0.75, 0, z, -q, 0);
                }
            }
            anchor(Anchor.Kind.BAR, parlour.min().x() + 1.6, 0, parlour.min().z() + 0.5, 0, 0);
        } else {
            double tx = cx - (hearth != null ? Math.signum(hearth[0]) : 1) * (parlour.max().x() - cx) * 0.4;
            anchor(Anchor.Kind.TABLE, tx, 0, cz + 0.4, 0, 0);
            anchor(Anchor.Kind.CHAIR, tx - 0.7, 0, cz + 0.4, q, 0);
            anchor(Anchor.Kind.CHAIR, tx + 0.7, 0, cz + 0.4, -q, 0);
            double corner = hearth != null && hearth[0] > 0 ? parlour.min().x() : parlour.max().x();
            anchor(Anchor.Kind.BENCH, corner + Math.signum(cx - corner) * 0.35, 0, parlour.max().z() - 0.9,
                Math.signum(cx - corner) > 0 ? q : -q, 0);
        }
        // a cabinet against the parlour's (or kitchen's) side wall, away from the hearth
        int room = rooms.size() > 1 && rooms.get(1).floor() == 0 ? 1 : 0;
        Aabb k = rooms.get(room).bounds();
        double wallX = hearth != null && hearth[0] > 0 ? k.min().x() : k.max().x();
        anchor(Anchor.Kind.CABINET, wallX + Math.signum(-wallX) * 0.3, 0, (k.min().z() + k.max().z()) / 2,
            wallX < 0 ? q : -q, room);
        if (room == 1) {
            anchor(Anchor.Kind.TABLE, (k.min().x() + k.max().x()) / 2, 0, (k.min().z() + k.max().z()) / 2, 0, 1);
        }
        for (int r = 0; r < rooms.size(); r++) {
            Room c = rooms.get(r);
            if (c.floor() == 0) {
                continue;
            }
            Aabb b = c.bounds();
            anchor(Anchor.Kind.BED, b.min().x() + 1.1, b.min().y(), b.min().z() + 1.3, q, r);
            anchor(Anchor.Kind.CABINET, b.max().x() - 0.3, b.min().y(), (b.min().z() + b.max().z()) / 2, -q, r);
        }
        if (inn) {
            Door door = doors.getFirst();
            anchor(Anchor.Kind.SIGN, door.x() + 1.6, storeyY[1] - 0.3, z1 + 0.85, q, -1);
            anchor(Anchor.Kind.NAME_BOARD, 0, storeyY[1] - 0.42, z1 + 0.035, 0, -1);
            for (int i = -1; i <= 1; i++) {
                double x = i * w * 0.3;
                double z = z1 + 3.0;
                anchor(Anchor.Kind.TERRACE_TABLE, x, ground(x, z), z, 0, -1);
            }
        }
    }

    /** The church's bell, clocks, altar, pews and lamps. */
    private void churchFittings() {
        double ts = towerSize / 2;
        double tc = hd - ts;
        anchor(Anchor.Kind.BELL, 0, towerTop - 1.9, tc, 0, -1);
        double clockY = towerTop - 4.0;
        for (Face f : Face.values()) {
            anchor(Anchor.Kind.CLOCK, f.nx * (ts + 0.06), clockY, tc + f.nz * (ts + 0.06), Maths.atan2(f.nx, f.nz),
                -1);
        }
        Aabb nave = rooms.getFirst().bounds();
        anchor(Anchor.Kind.ALTAR, 0, 0, nave.min().z() + 1.4, 0, 0);
        for (double z = nave.min().z() + 3.4; z < nave.max().z() - 1.6; z += 1.05) {
            for (int side = -1; side <= 1; side += 2) {
                anchor(Anchor.Kind.PEW, side * (nave.max().x() * 0.5 + 0.2), 0, z, Math.PI, 0);
            }
        }
        for (int i = 0; i < 3; i++) {
            double z = nave.min().z() + (nave.max().z() - nave.min().z()) * (i + 0.5) / 3;
            anchor(Anchor.Kind.CEILING_LAMP, 0, nave.max().y() - 1.2, z, 0, 0);
        }
    }

    /** The plan's fittings. */
    Fittings toFittings() {
        return new Fittings(anchors, rooms, openings);
    }
}
