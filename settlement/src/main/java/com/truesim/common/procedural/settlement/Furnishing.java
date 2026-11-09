package com.truesim.common.procedural.settlement;

import com.truesim.common.math.geometry.Polygons;
import com.truesim.common.math.curve.Polyline2;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.building.BuildingKind;
import com.truesim.common.procedural.settlement.Settlement.Building;
import com.truesim.common.procedural.settlement.Settlement.Fence;
import com.truesim.common.procedural.settlement.Settlement.FenceKind;
import com.truesim.common.procedural.settlement.Settlement.Lamp;
import com.truesim.common.procedural.settlement.Settlement.Plot;
import com.truesim.common.procedural.settlement.Settlement.Prop;
import com.truesim.common.procedural.settlement.Settlement.Road;
import com.truesim.common.procedural.settlement.Settlement.Square;
import com.truesim.common.procedural.settlement.Settlement.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * What stands along a finished plan's roads: lamps along the core's roads and round the square, fences round the
 * gardens and along the pastures, the square's and the houses' props, signposts where footpaths leave the roads, and
 * rocks, boulders and stumps on the meadows. Heights are the graded ground's.
 */
final class Furnishing {

    private final SettlementParameters p;
    private final Field field;
    private final Grading grading;
    private final List<Road> roads;
    private final List<Polyline2> lines;
    private final Square square;
    private final List<Plot> plots;
    private final List<Building> buildings;
    private final List<Prop> props = new ArrayList<>();
    private final List<Lamp> lamps = new ArrayList<>();
    private final List<Fence> fences = new ArrayList<>();

    Furnishing(SettlementParameters p, Field field, Grading grading, List<Road> roads, List<Polyline2> lines, Square square,
        int innSide, List<Plot> plots, List<Building> buildings, List<Integer> barnPaths, long seed) {
        this.p = p;
        this.field = field;
        this.grading = grading;
        this.roads = roads;
        this.lines = lines;
        this.square = square;
        this.plots = plots;
        this.buildings = buildings;
        SplittableRandom random = new SplittableRandom(seed);
        placeLamps();
        squareProps(innSide);
        houseProps(random);
        signposts(barnPaths);
        rocks(random);
        gardenFences();
        pastureFences();
    }

    List<Prop> props() {
        return props;
    }

    List<Lamp> lamps() {
        return lamps;
    }

    List<Fence> fences() {
        return fences;
    }

    private double ground(double x, double z) {
        return grading.height(x, z, field.heightAt(x, z));
    }

    private static double yaw(double fx, double fz) {
        return Maths.atan2(fx, fz);
    }

    // ---------------------------------------------------------------------------------------------------------- lamps

    /** Lamps round the square's corners and along the main road and lanes in the village, alternating sides. */
    private void placeLamps() {
        double[] q = new double[2];
        for (int k = 0; k < 4; k++) {
            double along = (k == 0 || k == 3 ? -1 : 1) * (square.halfLength() - 1.2);
            double across = (k < 2 ? -1 : 1) * (square.halfWidth() - 1.2);
            square.point(along, across, q);
            if (!onAnyRoad(q[0], q[1], 0.3)) {
                lamps.add(new Lamp(q[0], ground(q[0], q[1]), q[1], yaw(square.x() - q[0], square.z() - q[1]),
                    Settlement.SQUARE));
            }
        }
        double[] a = new double[4];
        double reach = p.core().villageRadius() * 0.8;
        for (Road r : roads) {
            if (r.kind() != RoadKind.MAIN && r.kind() != RoadKind.LANE) {
                continue;
            }
            Polyline2 line = lines.get(r.id());
            double off = r.width() / 2 + p.furniture().lampOffset();
            int side = r.id() % 2 == 0 ? 1 : -1;
            for (double s = p.furniture().lampSpacing() / 2; s < line.length(); s += p.furniture().lampSpacing()) {
                line.at(s, a);
                if (Polygons.length(a[0] - square.x(), a[1] - square.z()) > reach) {
                    continue;
                }
                double x = a[0] + side * a[3] * off;
                double z = a[1] - side * a[2] * off;
                if (free(x, z, 0.5) && !onAnyRoad(x, z, 0.3)) {
                    lamps.add(new Lamp(x, ground(x, z), z, yaw(-side * a[3], side * a[2]), r.id()));
                }
                side = -side;
            }
        }
    }

    /** Whether ({@code x}, {@code z}) lies on a road's surface grown by {@code margin}. */
    private boolean onAnyRoad(double x, double z, double margin) {
        for (Road r : roads) {
            if (r.distance(x, z) < r.width() / 2 + margin) {
                return true;
            }
        }
        return false;
    }

    /** Whether ({@code x}, {@code z}) is off the square, the plots and the buildings by {@code margin}. */
    private boolean free(double x, double z, double margin) {
        if (square.outside(x, z) < margin) {
            return false;
        }
        for (Plot plot : plots) {
            if (Polygons.convexDepth(x, z, plot.x(), plot.z()) > -margin) {
                return false;
            }
        }
        for (Building b : buildings) {
            if (b.contains(x, z, margin)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------- props

    /** The fountain on the church's half, benches along the square's ends, the inn's terrace tables and planters. */
    private void squareProps(int innSide) {
        squareProp(PropKind.FOUNTAIN, 0, -innSide * square.halfWidth() * 0.55, 0);
        double inward = innSide > 0 ? Math.PI : 0; // across toward the centre, as a turn from +across
        for (int k = -1; k <= 1; k++) {
            squareProp(PropKind.TABLE_SET, k * 6.5, innSide * (square.halfWidth() - 3), 0.3 * k);
        }
        squareProp(PropKind.PLANTER, -2.2, innSide * (square.halfWidth() - 0.8), inward);
        squareProp(PropKind.PLANTER, 2.2, innSide * (square.halfWidth() - 0.8), inward);
        for (int k : new int[] {-1, 1}) {
            squareProp(PropKind.BENCH, k * (square.halfLength() - 1.5), -innSide * square.halfWidth() * 0.55,
                k > 0 ? -Math.PI / 2 : Math.PI / 2);
            squareProp(PropKind.BENCH, k * square.halfLength() * 0.55, -innSide * (square.halfWidth() - 1.2),
                inward + Math.PI);
        }
    }

    /** A prop on the square at ({@code along}, {@code across}), its front turned {@code turn} from +across. */
    private void squareProp(PropKind kind, double along, double across, double turn) {
        double[] q = new double[2];
        square.point(along, across, q);
        if (onAnyRoad(q[0], q[1], 0.4)) {
            return;
        }
        // +across is (-sin angle, cos angle): its turn is atan2 of that
        double base = yaw(-Maths.sin(square.angle()), Maths.cos(square.angle()));
        props.add(new Prop(kind, q[0], ground(q[0], q[1]), q[1], Maths.wrapAngle(base + turn), Settlement.NONE));
    }

    /** By each house: a bench at the door, a woodpile along a wall, a barrel or a crate; a farmstead's trough. */
    private void houseProps(SplittableRandom random) {
        for (Building b : buildings) {
            BuildingKind kind = b.spec().kind();
            if (kind != BuildingKind.CHALET && kind != BuildingKind.FARMHOUSE) {
                continue;
            }
            double hw = b.spec().footprint().width() / 2;
            double hd = b.spec().footprint().depth() / 2;
            int wall = random.nextBoolean() ? 1 : -1;
            boolean barrel = random.nextBoolean();
            houseProp(b, PropKind.BENCH, hw * 0.45, hd + 0.5, 0);
            houseProp(b, PropKind.WOODPILE, wall * (hw + 0.5), 0, wall * Math.PI / 2);
            houseProp(b, barrel ? PropKind.BARREL : PropKind.CRATE, -wall * (hw + 0.6), -hd + 1.2, 0);
            if (kind == BuildingKind.FARMHOUSE) {
                houseProp(b, PropKind.TROUGH, -hw * 0.6, hd + 2.5, Math.PI / 2);
            }
        }
    }

    /** A prop at ({@code lx}, {@code lz}) of building {@code b}'s frame, turned {@code turn} from its front. */
    private void houseProp(Building b, PropKind kind, double lx, double lz, double turn) {
        double[] q = new double[2];
        b.toWorld(lx, lz, q);
        if (onAnyRoad(q[0], q[1], 0.4) || !field.contains(q[0], q[1], 1)) {
            return;
        }
        for (Building other : buildings) {
            if (other != b && other.contains(q[0], q[1], 0.5)) {
                return;
            }
        }
        props.add(new Prop(kind, q[0], ground(q[0], q[1]), q[1], Maths.wrapAngle(b.yaw() + turn), b.id()));
    }

    /** A signpost beside each footpath where it leaves the network (not the barns' paths). */
    private void signposts(List<Integer> barnPaths) {
        double[] a = new double[4];
        for (Road r : roads) {
            if (r.kind() != RoadKind.FOOTPATH || r.from() == Settlement.NONE || barnPaths.contains(r.id())
                || r.from() >= 0 && roads.get(r.from()).kind() == RoadKind.FOOTPATH) {
                continue;
            }
            Polyline2 line = lines.get(r.id());
            line.at(Math.min(4, line.length() / 2), a);
            double off = r.width() / 2 + 0.7;
            double x = a[0] + a[3] * off;
            double z = a[1] - a[2] * off;
            if (!onAnyRoad(x, z, 0.2) && free(x, z, 0.3)) {
                props.add(new Prop(PropKind.SIGNPOST, x, ground(x, z), z, yaw(-a[3], a[2]), Settlement.NONE));
            }
        }
    }

    /** Rocks and boulders on the meadows, stumps and boulders at the forest's edge, off everything built. */
    private void rocks(SplittableRandom random) {
        int wanted = p.furniture().rocks();
        double extent = (field.n - 1) * field.cell;
        for (int tries = 0, placed = 0; placed < wanted && tries < 40 * wanted; tries++) {
            double x = field.x0 + random.nextDouble() * extent;
            double z = field.z0 + random.nextDouble() * extent;
            double yaw = random.nextDouble(0, 2 * Math.PI);
            double pick = random.nextDouble();
            int node = field.node(x, z);
            if (node < 0 || !field.contains(x, z, 8) || field.water[node] > 0.05 || field.slope[node] > 35
                || field.slope[node] < 3 || Polygons.length(x - square.x(), z - square.z())
                < p.core().coreRadius() * 0.8 || onAnyRoad(x, z, 3) || !free(x, z, 3)) {
                continue;
            }
            double meadow = field.meadow[node];
            PropKind kind = meadow < 0.5 ? (pick < 0.6 ? PropKind.STUMP : PropKind.BOULDER)
                : (pick < 0.7 ? PropKind.ROCK : PropKind.BOULDER);
            props.add(new Prop(kind, x, ground(x, z), z, yaw, Settlement.NONE));
            placed++;
        }
    }

    // --------------------------------------------------------------------------------------------------------- fences

    /** Round each garden outside the core: its sides and back (the front open to its road). */
    private void gardenFences() {
        for (Plot plot : plots) {
            if (plot.zone() != Zone.VILLAGE && plot.zone() != Zone.FARMSTEAD) {
                continue;
            }
            double[] px = plot.x();
            double[] pz = plot.z();
            // front-left, back-left, back-right, front-right, a little inside
            int[] order = {0, 3, 2, 1};
            double cx = (px[0] + px[1] + px[2] + px[3]) / 4;
            double cz = (pz[0] + pz[1] + pz[2] + pz[3]) / 4;
            List<double[]> run = new ArrayList<>();
            for (int k : order) {
                double dx = cx - px[k];
                double dz = cz - pz[k];
                double l = Polygons.length(dx, dz);
                double x = px[k] + dx / l * 0.4;
                double z = pz[k] + dz / l * 0.4;
                run.add(new double[] {x, z});
            }
            fence(FenceKind.GARDEN, run, false);
        }
    }

    /** Along the farm tracks and the main road outside the village, both sides, where the ground is open pasture. */
    private void pastureFences() {
        double[] a = new double[4];
        for (Road r : roads) {
            boolean main = r.kind() == RoadKind.MAIN;
            if (r.kind() != RoadKind.TRACK && !main) {
                continue;
            }
            Polyline2 line = lines.get(r.id());
            double off = r.width() / 2 + p.furniture().fenceOffset();
            for (int side : new int[] {1, -1}) {
                List<double[]> run = new ArrayList<>();
                for (double s = 6; s <= line.length() - 6; s += 2) {
                    line.at(s, a);
                    double x = a[0] + side * a[3] * off;
                    double z = a[1] - side * a[2] * off;
                    boolean village = Polygons.length(a[0] - square.x(), a[1] - square.z())
                        < p.core().villageRadius();
                    if (!(main && village) && pasture(r, x, z)) {
                        run.add(new double[] {x, z});
                    } else {
                        fence(FenceKind.PASTURE, run, true);
                        run = new ArrayList<>();
                    }
                }
                fence(FenceKind.PASTURE, run, true);
            }
        }
    }

    /** Whether a pasture fence beside {@code road} may stand at ({@code x}, {@code z}). */
    private boolean pasture(Road road, double x, double z) {
        int node = field.node(x, z);
        if (node < 0 || !field.contains(x, z, 2) || field.water[node] > 0.05 || field.slope[node] > 35
            || field.meadow[node] < 0.3 || !free(x, z, 1.5)) {
            return false;
        }
        for (Road r : roads) {
            if (r != road && r.distance(x, z) < r.width() / 2 + 1) {
                return false;
            }
        }
        return true;
    }

    /** Adds a fence along {@code run} (thinned to a point every few metres) if it is long enough. */
    private void fence(FenceKind kind, List<double[]> run, boolean thin) {
        if (run.size() < 2) {
            return;
        }
        if (!thin) {
            add(kind, run);
            return;
        }
        // keep a point every 4 m and the last
        List<double[]> kept = new ArrayList<>();
        kept.add(run.getFirst());
        double since = 0;
        for (int k = 1; k < run.size(); k++) {
            since += Polygons.length(run.get(k)[0] - run.get(k - 1)[0], run.get(k)[1] - run.get(k - 1)[1]);
            if (since >= 4 || k == run.size() - 1) {
                kept.add(run.get(k));
                since = 0;
            }
        }
        add(kind, kept);
    }

    private void add(FenceKind kind, List<double[]> kept) {
        double[] x = new double[kept.size()];
        double[] y = new double[kept.size()];
        double[] z = new double[kept.size()];
        for (int k = 0; k < x.length; k++) {
            x[k] = kept.get(k)[0];
            z[k] = kept.get(k)[1];
            y[k] = ground(x[k], z[k]);
        }
        Fence fence = new Fence(kind, x, y, z);
        if (fence.length() >= p.furniture().minFence()) {
            fences.add(fence);
        }
    }
}
