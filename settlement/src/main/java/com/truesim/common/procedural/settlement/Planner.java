package com.truesim.common.procedural.settlement;

import com.truesim.common.math.geometry.Polygons;
import com.truesim.common.math.curve.Polyline2;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.building.BuildingKind;
import com.truesim.common.procedural.building.BuildingSpec;
import com.truesim.common.procedural.building.Footprint;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.settlement.Settlement.Building;
import com.truesim.common.procedural.settlement.Settlement.Plot;
import com.truesim.common.procedural.settlement.Settlement.Road;
import com.truesim.common.procedural.settlement.Settlement.Square;
import com.truesim.common.procedural.settlement.Settlement.Zone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Makes a settlement's plan, step by step (after Emilien et al., "Procedural Generation of Villages on Arbitrary
 * Terrains", The Visual Computer 2012: roads grown over the terrain by least-cost paths, parcels along them, buildings
 * sited by slope, sun and access):
 * <ol>
 *   <li>the main road along the valley, through the origin: least-cost paths to the two points on a ring about the
 *       origin that lie nearest its height on opposite sides (the valley's way);</li>
 *   <li>the square on it, its length along the road, its surface a plane fitted to the ground; the inn on its
 *       northern side (its front to the square and the sun), the church across;</li>
 *   <li>village lanes, farm tracks and footpaths, each from the network to a destination chosen per sector of a ring
 *       (buildable ground for lanes, meadow for tracks, height for footpaths), routed by {@link Router};</li>
 *   <li>plots along the roads: frontages walked along each side, deeper and wider and fewer away from the core, kept
 *       off roads, each other, flood-prone and steep ground;</li>
 *   <li>a building in each plot, facing the valley and the sun where it fits (its road in the core);</li>
 *   <li>hay barns on the meadows, each with a footpath to the network;</li>
 *   <li>lamps along the core's roads, fences round gardens and along pastures, props, rocks; the grading and the
 *       clearings.</li>
 * </ol>
 * Every random choice draws from a stream derived from the seed per step, so the plan is a function of its inputs.
 */
final class Planner {

    private final SettlementParameters p;
    private final long seed;
    private final double ox;
    private final double oz;
    private final Field field;
    private final Router router;
    private final boolean[] onRoad;
    private final boolean[] blocked;
    private final List<Road> roads = new ArrayList<>();
    private final List<Polyline2> lines = new ArrayList<>();
    /** Roads that lead to a barn (no signpost). */
    private final List<Integer> barnPaths = new ArrayList<>();
    private final List<Plot> plots = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();
    private final List<Integer> roadSteps = new ArrayList<>();
    private final List<Integer> plotSteps = new ArrayList<>();
    private final List<Integer> buildingSteps = new ArrayList<>();
    private int step;
    private Square square;
    private int innSide;

    Planner(SettlementParameters parameters, Ecology ecology, double originX, double originZ, long seed) {
        this.p = parameters;
        this.seed = seed;
        this.ox = originX;
        this.oz = originZ;
        this.field = new Field(ecology, originX, originZ, parameters);
        this.router = new Router(field, parameters.network());
        onRoad = new boolean[field.n * field.n];
        blocked = new boolean[field.n * field.n];
    }

    Settlement plan() {
        mainRoad();
        squarePlots();
        branches(RoadKind.LANE, 0.55, 1.0, 1);
        branches(RoadKind.TRACK, 0.6, 1.0, 2);
        branches(RoadKind.FOOTPATH, 0.5, 1.0, 3);
        parcels();
        for (int k = 0; k < plots.size(); k++) {
            if (plots.get(k).zone() != Zone.SQUARE) {
                house(plots.get(k));
            }
        }
        barns();
        return finish();
    }

    // ---------------------------------------------------------------------------------------------------------- roads

    private void mainRoad() {
        SettlementParameters.RoadClass main = p.main();
        int origin = field.node(ox, oz);
        if (origin < 0) {
            throw new IllegalArgumentException("The origin is outside the plan");
        }
        double radius = Math.min(main.reach(), (field.n - 1) * field.cell / 2 - 4 * field.cell);
        double h0 = field.height[origin];
        int count = 72;
        double[] score = new double[count];
        int[] node = new int[count];
        for (int k = 0; k < count; k++) {
            double a = 2 * Math.PI * k / count;
            node[k] = field.node(ox + radius * Maths.cos(a), oz + radius * Maths.sin(a));
            boolean ok = node[k] >= 0 && field.slope[node[k]] <= main.maxSideSlope() && field.water[node[k]] < 0.5;
            score[k] = ok ? Math.abs(field.height[node[k]] - h0) + 0.5 * field.slope[node[k]] : Double.MAX_VALUE;
        }
        Integer[] byScore = new Integer[count];
        for (int k = 0; k < count; k++) {
            byScore[k] = k;
        }
        Arrays.sort(byScore, (a, b) -> score[a] != score[b] ? Double.compare(score[a], score[b]) : a - b);
        int[] toA = null;
        int[] toB = null;
        int chosenA = -1;
        for (int ia = 0; ia < count && toA == null && score[byScore[ia]] < Double.MAX_VALUE; ia++) {
            toA = router.route(main, new int[] {origin}, node[byScore[ia]], onRoad, blocked);
            chosenA = byScore[ia];
        }
        if (toA == null) {
            throw new IllegalStateException("No way for a main road from the origin");
        }
        for (int ib = 0; ib < count && toB == null && score[byScore[ib]] < Double.MAX_VALUE; ib++) {
            int k = byScore[ib];
            int apart = Math.abs(k - chosenA);
            if (Math.min(apart, count - apart) < count / 3) {
                continue;
            }
            toB = router.route(main, new int[] {origin}, node[k], onRoad, blocked);
        }
        // A to the origin, then on to B (or only the origin to A)
        int[] cells;
        if (toB == null) {
            cells = toA;
        } else {
            cells = new int[toA.length + toB.length - 1];
            for (int k = 0; k < toA.length; k++) {
                cells[k] = toA[toA.length - 1 - k];
            }
            System.arraycopy(toB, 1, cells, toA.length, toB.length - 1);
        }
        Polyline2 raw = Polyline2.chaikin(points(cells, 0, cells.length - 1), 3);
        square = square(raw);
        markSquare();
        for (Polyline2 piece : clip(raw)) {
            boolean startOnSquare = square.outside(piece.x[0], piece.y[0]) < 0.01;
            boolean endOnSquare = square.outside(piece.x[piece.count() - 1], piece.y[piece.count() - 1]) < 0.01;
            road(RoadKind.MAIN, piece, startOnSquare ? Settlement.SQUARE : Settlement.NONE,
                endOnSquare ? Settlement.SQUARE : Settlement.NONE, false);
        }
    }

    /** The square on the main road at the origin: along the road there, a plane fitted to the ground. */
    private Square square(Polyline2 road) {
        double[] q = new double[3];
        double at = road.nearest(ox, oz, q);
        double[] a = new double[4];
        double[] b = new double[4];
        road.at(at - 12, a);
        road.at(at + 12, b);
        double angle = Maths.atan2(b[1] - a[1], b[0] - a[0]);
        double hl = p.core().squareLength() / 2;
        double hw = p.core().squareWidth() / 2;
        // least squares plane over the square, 1 m samples
        double c = Maths.cos(angle);
        double s = Maths.sin(angle);
        double suu = 0;
        double svv = 0;
        double sh = 0;
        double suh = 0;
        double svh = 0;
        int count = 0;
        for (double u = -hl; u <= hl + 1e-9; u += 1) {
            for (double v = -hw; v <= hw + 1e-9; v += 1) {
                double x = ox + u * c - v * s;
                double z = oz + u * s + v * c;
                double h = field.heightAt(x, z);
                suu += u * u;
                svv += v * v;
                sh += h;
                suh += u * h;
                svh += v * h;
                count++;
            }
        }
        double mean = sh / count;
        // the samples are symmetric about the centre: the plane's gradients along u and v decouple
        double du = suu > 0 ? suh / suu : 0;
        double dv = svv > 0 ? svh / svv : 0;
        // into world gradients: h = mean + du u + dv v, u = dx c + dz s, v = -dx s + dz c
        double gx = du * c - dv * s;
        double gz = du * s + dv * c;
        return new Square(ox, mean, oz, angle, hl, hw, gx, gz);
    }

    /** Marks the square's nodes as the network's (roads start from its edge). */
    private void markSquare() {
        for (int j = 0; j < field.n; j++) {
            for (int i = 0; i < field.n; i++) {
                if (square.outside(field.x(i), field.z(j)) <= 0) {
                    onRoad[j * field.n + i] = true;
                }
            }
        }
    }

    /** The parts of {@code line} outside the square, each ending on its edge where it meets it. */
    private List<Polyline2> clip(Polyline2 line) {
        List<Polyline2> pieces = new ArrayList<>();
        List<double[]> current = new ArrayList<>();
        for (int i = 0; i < line.count(); i++) {
            boolean out = square.outside(line.x[i], line.y[i]) > 0;
            if (i > 0) {
                boolean wasOut = square.outside(line.x[i - 1], line.y[i - 1]) > 0;
                if (out != wasOut) {
                    // the crossing, by bisection on the box distance
                    double lo = 0;
                    double hi = 1;
                    for (int it = 0; it < 40; it++) {
                        double m = (lo + hi) / 2;
                        double mx = line.x[i - 1] + m * (line.x[i] - line.x[i - 1]);
                        double mz = line.y[i - 1] + m * (line.y[i] - line.y[i - 1]);
                        if ((square.outside(mx, mz) > 0) == wasOut) {
                            lo = m;
                        } else {
                            hi = m;
                        }
                    }
                    double t = wasOut ? hi : lo;
                    double[] crossing = {line.x[i - 1] + t * (line.x[i] - line.x[i - 1]),
                        line.y[i - 1] + t * (line.y[i] - line.y[i - 1])};
                    // onto the edge exactly
                    double along = Math.clamp(square.along(crossing[0], crossing[1]), -square.halfLength(),
                        square.halfLength());
                    double across = Math.clamp(square.across(crossing[0], crossing[1]), -square.halfWidth(),
                        square.halfWidth());
                    if (Math.abs(Math.abs(along) - square.halfLength()) < Math.abs(Math.abs(across)
                        - square.halfWidth())) {
                        along = Math.signum(along) * square.halfLength();
                    } else {
                        across = Math.signum(across) * square.halfWidth();
                    }
                    square.point(along, across, crossing);
                    current.add(crossing);
                    if (!out) {
                        pieces.add(toLine(current));
                        current = new ArrayList<>();
                    }
                }
            }
            if (out) {
                current.add(new double[] {line.x[i], line.y[i]});
            }
        }
        if (current.size() > 1) {
            pieces.add(toLine(current));
        }
        pieces.removeIf(l -> l.count() < 2 || l.length() < 2 * p.network().step());
        return pieces;
    }

    private static Polyline2 toLine(List<double[]> points) {
        double[] x = new double[points.size()];
        double[] z = new double[points.size()];
        for (int k = 0; k < x.length; k++) {
            x[k] = points.get(k)[0];
            z[k] = points.get(k)[1];
        }
        return new Polyline2(x, z);
    }

    /** The nodes {@code from} to {@code to} of {@code cells} as a line. */
    private Polyline2 points(int[] cells, int from, int to) {
        double[] x = new double[to - from + 1];
        double[] z = new double[to - from + 1];
        for (int k = from; k <= to; k++) {
            x[k - from] = field.x(cells[k] % field.n);
            z[k - from] = field.z(cells[k] / field.n);
        }
        return new Polyline2(x, z);
    }

    /**
     * Branches of {@code kind}: a destination per sector of the ring between {@code inner} and {@code outer} of the
     * class's reach, each routed from the network.
     */
    private void branches(RoadKind kind, double inner, double outer, long salt) {
        SettlementParameters.RoadClass rc = p.road(kind);
        if (rc.count() == 0) {
            return;
        }
        SplittableRandom random = new SplittableRandom(Seeds.derive(seed, salt));
        double reach = Math.min(rc.reach(), (field.n - 1) * field.cell / 2 - 6 * field.cell);
        double offset = random.nextDouble(0, 2 * Math.PI);
        double h0 = field.heightAt(ox, oz);
        for (int sector = 0; sector < rc.count(); sector++) {
            double best = Double.MAX_VALUE;
            double bx = 0;
            double bz = 0;
            for (int c = 0; c < 32; c++) {
                double a = offset + 2 * Math.PI * (sector + random.nextDouble()) / rc.count();
                double r = reach * random.nextDouble(inner, outer);
                double x = ox + r * Maths.cos(a);
                double z = oz + r * Maths.sin(a);
                int node = field.node(x, z);
                if (node < 0 || !field.contains(x, z, 6 * field.cell) || blocked[node] || field.water[node] > 0.1
                    || nearRoad(x, z, kind == RoadKind.LANE ? 0.25 * reach : 0.15 * reach)) {
                    continue;
                }
                double slope = field.slope[node];
                double sc;
                switch (kind) {
                    case LANE -> {
                        if (slope > p.siting().maxSlope() || field.flood[node]) {
                            continue;
                        }
                        sc = slope + 3 * random.nextDouble();
                    }
                    case TRACK -> {
                        if (slope > Math.min(p.siting().maxSlope() + 5, rc.maxSideSlope()) || field.flood[node]
                            || field.meadow[node] < 0.3) {
                            continue;
                        }
                        sc = slope + 10 * (1 - field.meadow[node]) + 3 * random.nextDouble();
                    }
                    default -> {
                        if (slope > rc.maxSideSlope() - 4) {
                            continue;
                        }
                        sc = -(field.height[node] - h0) + 0.5 * slope + 5 * random.nextDouble();
                    }
                }
                if (sc < best) {
                    best = sc;
                    bx = x;
                    bz = z;
                }
            }
            if (best < Double.MAX_VALUE) {
                connect(kind, field.node(bx, bz), Double.NaN, Double.NaN);
            }
        }
    }

    /** Whether a road passes within {@code distance} of ({@code x}, {@code z}). */
    private boolean nearRoad(double x, double z, double distance) {
        for (Road r : roads) {
            if (r.distance(x, z) < distance) {
                return true;
            }
        }
        return square != null && square.outside(x, z) < distance;
    }

    /**
     * Routes a road of {@code kind} from the network to node {@code target}, adds its runs off the network as roads;
     * the last ends at ({@code endX}, {@code endZ}) if given. The ids of the roads added, or none.
     */
    private List<Integer> connect(RoadKind kind, int target, double endX, double endZ) {
        int[] sources = sources();
        int[] cells = router.route(p.road(kind), sources, target, onRoad, blocked);
        List<Integer> added = new ArrayList<>();
        if (cells == null) {
            return added;
        }
        // the runs off the network: each joins it at its start (and at its end where it meets it again)
        int start = 0;
        while (start < cells.length - 1) {
            while (start < cells.length - 1 && onRoad[cells[start + 1]]) {
                start++;
            }
            if (start >= cells.length - 1) {
                break;
            }
            int end = start + 1;
            while (end < cells.length - 1 && !onRoad[cells[end]]) {
                end++;
            }
            Polyline2 line = points(cells, start, end);
            boolean last = end == cells.length - 1;
            if (last && !Double.isNaN(endX)) {
                double[] x = Arrays.copyOf(line.x, line.count() + 1);
                double[] z = Arrays.copyOf(line.y, line.count() + 1);
                x[x.length - 1] = endX;
                z[z.length - 1] = endZ;
                line = new Polyline2(x, z);
            }
            added.addAll(run(kind, line, onRoad[cells[start]], onRoad[cells[end]] && !(last && !onRoad[target])));
            start = end;
        }
        return added;
    }

    private int[] sources() {
        int count = 0;
        for (boolean b : onRoad) {
            if (b) {
                count++;
            }
        }
        int[] sources = new int[count];
        for (int a = 0, k = 0; a < onRoad.length; a++) {
            if (onRoad[a]) {
                sources[k++] = a;
            }
        }
        return sources;
    }

    /** A run of a route as roads: its ends snapped onto the network where they join it. */
    private List<Integer> run(RoadKind kind, Polyline2 line, boolean joinsAtStart, boolean joinsAtEnd) {
        double[] x = line.x.clone();
        double[] z = line.y.clone();
        int from = Settlement.NONE;
        int to = Settlement.NONE;
        double[] q = new double[3];
        if (joinsAtStart) {
            from = snap(x[0], z[0], q);
            x[0] = q[0];
            z[0] = q[1];
        }
        if (joinsAtEnd) {
            to = snap(x[x.length - 1], z[z.length - 1], q);
            x[x.length - 1] = q[0];
            z[z.length - 1] = q[1];
        }
        Polyline2 smooth = Polyline2.chaikin(new Polyline2(x, z), 3);
        // a run may cross the square's corner: keep it outside
        List<Polyline2> pieces = from == Settlement.SQUARE || to == Settlement.SQUARE || touchesSquare(smooth)
            ? clip(smooth) : List.of(smooth);
        List<Integer> ids = new ArrayList<>();
        for (Polyline2 piece : pieces) {
            int f = square.outside(piece.x[0], piece.y[0]) < 0.01 ? Settlement.SQUARE
                : piece.x[0] == smooth.x[0] && piece.y[0] == smooth.y[0] ? from : Settlement.NONE;
            int last = piece.count() - 1;
            int t = square.outside(piece.x[last], piece.y[last]) < 0.01 ? Settlement.SQUARE
                : piece.x[last] == smooth.x[smooth.count() - 1] && piece.y[last] == smooth.y[smooth.count() - 1] ? to
                : Settlement.NONE;
            ids.addAll(road(kind, piece, f, t, false));
        }
        return ids;
    }

    private boolean touchesSquare(Polyline2 line) {
        for (int i = 0; i < line.count(); i++) {
            if (square.outside(line.x[i], line.y[i]) <= 0) {
                return true;
            }
        }
        return false;
    }

    /** The point of the network nearest ({@code x}, {@code z}) into {@code out}: the road's id, or the square's. */
    private int snap(double x, double z, double[] out) {
        double best = square.outside(x, z) <= 0 ? -1 : Double.MAX_VALUE;
        int id = Settlement.SQUARE;
        out[0] = x;
        out[1] = z;
        double[] q = new double[3];
        for (int r = 0; r < roads.size(); r++) {
            lines.get(r).nearest(x, z, q);
            if (q[2] < best) {
                best = q[2];
                id = r;
                out[0] = q[0];
                out[1] = q[1];
            }
        }
        return id;
    }

    /**
     * Adds the road of {@code kind} along {@code line} (smoothed already): resampled, its profile following the ground
     * within the class's grade (cut and filled), its ends at the heights of what they join; split where its surface
     * changes (cobbles in the core). Marks its nodes as the network's. The ids added.
     */
    private List<Integer> road(RoadKind kind, Polyline2 line, int from, int to, boolean barnPath) {
        SettlementParameters.RoadClass rc = p.road(kind);
        Polyline2 l = line.resample(p.network().step());
        int m = l.count();
        double[] y = new double[m];
        for (int i = 0; i < m; i++) {
            y[i] = field.heightAt(l.x[i], l.y[i]);
        }
        // a moving mean over some 12 m: the profile eases over the ground's bumps
        double[] smooth = new double[m];
        int w = Math.max(1, (int) Math.round(6 / p.network().step()));
        for (int i = 0; i < m; i++) {
            double sum = 0;
            int c = 0;
            for (int k = Math.max(0, i - w); k <= Math.min(m - 1, i + w); k++) {
                sum += y[k];
                c++;
            }
            smooth[i] = sum / c;
        }
        double startY = junctionHeight(from, l.x[0], l.y[0]);
        double endY = junctionHeight(to, l.x[m - 1], l.y[m - 1]);
        if (!Double.isNaN(startY)) {
            smooth[0] = startY;
        }
        if (!Double.isNaN(endY)) {
            smooth[m - 1] = endY;
        }
        grade(l, smooth, rc.maxGrade(), !Double.isNaN(startY), !Double.isNaN(endY));
        // pieces by surface: cobbles in the core for the main road and lanes
        List<Integer> ids = new ArrayList<>();
        int pieceStart = 0;
        RoadSurface current = surface(kind, l.x[0], l.y[0]);
        for (int i = 1; i <= m - 1; i++) {
            RoadSurface s = i == m - 1 ? current : surface(kind, l.x[i], l.y[i]);
            if (s != current || i == m - 1) {
                int pieceEnd = i;
                if (pieceEnd - pieceStart >= 1) {
                    int f = pieceStart == 0 ? from : roads.size() - 1;
                    int t = pieceEnd == m - 1 ? to : roads.size() + 1;
                    ids.add(add(kind, current, rc.width(), l.part(pieceStart, pieceEnd),
                        Arrays.copyOfRange(smooth, pieceStart, pieceEnd + 1), f, t));
                }
                pieceStart = pieceEnd;
                current = s;
            }
        }
        if (barnPath) {
            barnPaths.addAll(ids);
        }
        return ids;
    }

    private Integer add(RoadKind kind, RoadSurface surface, double width, Polyline2 line, double[] y, int from, int to) {
        int id = roads.size();
        roads.add(new Road(id, kind, surface, width, line.x, y, line.y, from, to));
        lines.add(line);
        roadSteps.add(step++);
        for (int i = 0; i < line.count(); i++) {
            int a = field.node(line.x[i], line.y[i]);
            if (a >= 0) {
                onRoad[a] = true;
            }
        }
        return id;
    }

    private RoadSurface surface(RoadKind kind, double x, double z) {
        return switch (kind) {
            case MAIN, LANE -> Polygons.length(x - ox, z - oz) <= p.core().coreRadius() ? RoadSurface.COBBLES
                : RoadSurface.GRAVEL;
            case TRACK -> RoadSurface.GRAVEL;
            case FOOTPATH -> RoadSurface.DIRT;
        };
    }

    /** The height of what a road joins at ({@code x}, {@code z}): the square's, a road's crown, or NaN. */
    private double junctionHeight(int what, double x, double z) {
        if (what == Settlement.SQUARE) {
            return square.height(x, z);
        }
        if (what >= 0) {
            double[] q = new double[3];
            double along = lines.get(what).nearest(x, z, q);
            return lines.get(what).value(roads.get(what).y(), along);
        }
        return Double.NaN;
    }

    /**
     * Keeps the profile {@code y} within {@code maxGrade} between neighbouring points: passes clamping each point to
     * its neighbour's reach, forward from a fixed start, backward from a fixed end, until nothing moves.
     */
    static void grade(Polyline2 l, double[] y, double maxGrade, boolean fixedStart, boolean fixedEnd) {
        int m = y.length;
        double limit = maxGrade * (1 - 1e-6);
        for (int it = 0; it < 200; it++) {
            boolean moved = false;
            for (int i = 1; i < m; i++) {
                if (i == m - 1 && fixedEnd) {
                    break;
                }
                double reach = limit * (l.s[i] - l.s[i - 1]);
                double c = Math.clamp(y[i], y[i - 1] - reach, y[i - 1] + reach);
                moved |= c != y[i];
                y[i] = c;
            }
            for (int i = m - 2; i >= 0; i--) {
                if (i == 0 && fixedStart) {
                    break;
                }
                double reach = limit * (l.s[i + 1] - l.s[i]);
                double c = Math.clamp(y[i], y[i + 1] - reach, y[i + 1] + reach);
                moved |= c != y[i];
                y[i] = c;
            }
            if (!moved) {
                return;
            }
        }
        // both ends fixed further apart than the grade allows: the steepest the line allows from the start
        for (int i = 1; i < m; i++) {
            double reach = limit * (l.s[i] - l.s[i - 1]);
            y[i] = Math.clamp(y[i], y[i - 1] - reach, y[i - 1] + reach);
        }
    }

    // ---------------------------------------------------------------------------------------------------------- plots

    /** The inn's plot on the square's northern side, the church's across: fronts on the square. */
    private void squarePlots() {
        double[] north = new double[2];
        square.point(0, -square.halfWidth(), north);
        double[] south = new double[2];
        square.point(0, square.halfWidth(), south);
        innSide = north[1] <= south[1] ? -1 : 1; // the side (across) whose edge lies further north
        SettlementParameters.Buildings b = p.buildings();
        BuildingKind inn = BuildingKind.INN;
        BuildingKind church = BuildingKind.CHURCH;
        reserve(innSide, inn.width() + 2 * b.sideGap(), inn.depth() + b.coreSetback() + b.sideGap(), inn,
            inn.width(), inn.depth());
        reserve(-innSide, church.width() + 2 * b.sideGap(), church.depth() + 2 + b.sideGap(), church,
            church.width(), church.depth());
    }

    /**
     * A plot of {@code frontage} by {@code depth} on the square's side {@code side} (across), slid along it until it
     * keeps off the roads, and its building ({@code kind}, {@code width} by {@code depth}) facing the square.
     */
    private void reserve(int side, double frontage, double depth, BuildingKind kind, double width, double bDepth) {
        double edge = side * (square.halfWidth() + 0.5);
        double room = square.halfLength() - frontage / 2;
        for (double shift : new double[] {0, room * 0.5, -room * 0.5, room, -room}) {
            double[] a = new double[2];
            double[] b = new double[2];
            double[] c = new double[2];
            double[] d = new double[2];
            square.point(shift - side * frontage / 2, edge, a);
            square.point(shift + side * frontage / 2, edge, b);
            square.point(shift + side * frontage / 2, edge + side * depth, c);
            square.point(shift - side * frontage / 2, edge + side * depth, d);
            double[] px = {a[0], b[0], c[0], d[0]};
            double[] pz = {a[1], b[1], c[1], d[1]};
            if (!field.contains(px[2], pz[2], 2) || !field.contains(px[3], pz[3], 2) || clearOfRoads(px, pz, 0.5)
                > 0) {
                continue;
            }
            Plot plot = new Plot(plots.size(), px, pz, Settlement.SQUARE, Zone.SQUARE);
            plots.add(plot);
            plotSteps.add(step++);
            // the lanes routed next keep their width and verge off it, a cell to spare
            block(px, pz, p.lane().width() / 2 + p.plots().verge() + field.cell);
            double[] centre = new double[2];
            double front = square.halfWidth() + 0.5 + (kind == BuildingKind.CHURCH ? 2 : p.buildings()
                .coreSetback());
            square.point(shift, side * (front + bDepth / 2), centre);
            // its front faces the square: across, toward the centre
            double[] fc = new double[2];
            square.point(0, -side, fc);
            double fx = fc[0] - square.x();
            double fz = fc[1] - square.z();
            // the gable (and door) to the square
            place(kind, width, bDepth, 0, true, Seeds.derive(seed, 100 + kind.ordinal()), centre[0], centre[1],
                Maths.atan2(fx, fz), plot.id(), Settlement.SQUARE);
            return;
        }
    }

    /** Whether houses turn their gable (and door) to where they face; else their eaves (the ridge along the front). */
    private boolean gableFront() {
        return !p.buildings().eavesToValley();
    }

    /** Marks the nodes within {@code margin} metres of a polygon blocked to roads. */
    private void block(double[] px, double[] pz, double margin) {
        double minX = Arrays.stream(px).min().orElseThrow() - margin;
        double maxX = Arrays.stream(px).max().orElseThrow() + margin;
        double minZ = Arrays.stream(pz).min().orElseThrow() - margin;
        double maxZ = Arrays.stream(pz).max().orElseThrow() + margin;
        int i0 = Math.max(0, (int) Math.floor((minX - field.x0) / field.cell));
        int i1 = Math.min(field.n - 1, (int) Math.ceil((maxX - field.x0) / field.cell));
        int j0 = Math.max(0, (int) Math.floor((minZ - field.z0) / field.cell));
        int j1 = Math.min(field.n - 1, (int) Math.ceil((maxZ - field.z0) / field.cell));
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if (Polygons.convexDepth(field.x(i), field.z(j), px, pz) > -margin) {
                    blocked[j * field.n + i] = true;
                }
            }
        }
    }

    /**
     * How far a polygon reaches into the roads' corridors (their half width plus {@code verge}): 0 where it keeps
     * clear of them all.
     */
    private double clearOfRoads(double[] px, double[] pz, double verge) {
        double minX = Arrays.stream(px).min().orElseThrow();
        double maxX = Arrays.stream(px).max().orElseThrow();
        double minZ = Arrays.stream(pz).min().orElseThrow();
        double maxZ = Arrays.stream(pz).max().orElseThrow();
        double worst = 0;
        for (Road r : roads) {
            double clear = r.width() / 2 + verge;
            for (int i = 1; i < r.count(); i++) {
                double ax = r.x()[i - 1];
                double az = r.z()[i - 1];
                double bx = r.x()[i];
                double bz = r.z()[i];
                if (Math.max(ax, bx) < minX - clear || Math.min(ax, bx) > maxX + clear
                    || Math.max(az, bz) < minZ - clear || Math.min(az, bz) > maxZ + clear) {
                    continue;
                }
                double d = Polygons.segmentPolygonDistance(ax, az, bx, bz, px, pz);
                if (d < clear) {
                    worst = Math.max(worst, clear - d);
                }
            }
        }
        return worst;
    }

    /** Plots along both sides of the main road, lanes and tracks, nearest the core first. */
    private void parcels() {
        SplittableRandom random = new SplittableRandom(Seeds.derive(seed, 4));
        int count = roads.size();
        for (int r = 0; r < count; r++) {
            Road road = roads.get(r);
            if (road.kind() == RoadKind.FOOTPATH) {
                continue;
            }
            for (int side : new int[] {1, -1}) {
                frontage(road, lines.get(r), side, random);
            }
        }
    }

    /** Walks along one side of {@code road}, laying plots where they fit. */
    private void frontage(Road road, Polyline2 line, int side, SplittableRandom random) {
        SettlementParameters.Plots pp = p.plots();
        double hw = road.width() / 2;
        double length = line.length();
        // keep off the junctions at either end
        double s = road.from() != Settlement.NONE ? 8 : 2;
        double end = length - (road.to() != Settlement.NONE ? 8 : 2);
        double[] a = new double[4];
        double[] b = new double[4];
        boolean farm = road.kind() == RoadKind.TRACK;
        int farmPlots = 0;
        while (s < end) {
            line.at(s, a);
            double r = Polygons.length(a[0] - ox, a[1] - oz);
            boolean core = r < p.core().coreRadius();
            boolean village = r < p.core().villageRadius();
            boolean farmstead = farm && !village && s > length - 70 && road.to() == Settlement.NONE;
            if (!village && !farmstead) {
                s += 4;
                continue;
            }
            double f = (core ? pp.coreFrontage() : pp.frontage()) * random.nextDouble(0.9, 1.15);
            double d = (core ? pp.coreDepth() : pp.depth()) * random.nextDouble(0.9, 1.15);
            double keep = core ? 1 : farmstead ? (farmPlots < 2 ? 1 : 0)
                : 1 - (1 - pp.outerDensity()) * Math.clamp((r - p.core().coreRadius())
                / (p.core().villageRadius() - p.core().coreRadius()), 0, 1);
            if (s + f > end + (farmstead ? 8 : 0)) {
                break;
            }
            if (random.nextDouble() >= keep) {
                s += f;
                continue;
            }
            line.at(s + f, b);
            double off = hw + pp.verge();
            double ax = a[0] + side * a[3] * off;
            double az = a[1] - side * a[2] * off;
            double bx = b[0] + side * b[3] * off;
            double bz = b[1] - side * b[2] * off;
            double ex = bx - ax;
            double ez = bz - az;
            double chord = Polygons.length(ex, ez);
            if (chord < 0.6 * f) {
                s += 3;
                continue;
            }
            ex /= chord;
            ez /= chord;
            // inward: away from the road, on its side
            double mx = side * ez;
            double mz = -side * ex;
            Plot plot = null;
            for (double depth : new double[] {d, 0.75 * d}) {
                double[] px = {ax, bx, bx + mx * depth, ax + mx * depth};
                double[] pz = {az, bz, bz + mz * depth, az + mz * depth};
                if (fits(px, pz)) {
                    plot = new Plot(plots.size(), px, pz, road.id(), core ? Zone.CORE : farmstead ? Zone.FARMSTEAD
                        : Zone.VILLAGE);
                    break;
                }
            }
            if (plot == null) {
                s += 3;
                continue;
            }
            plots.add(plot);
            plotSteps.add(step++);
            // the barns' paths keep off it
            block(plot.x(), plot.z(), p.footpath().width() / 2 + p.plots().verge() + field.cell / 2);
            if (farmstead) {
                farmPlots++;
            }
            s += f + (core ? 0.5 : 2);
        }
    }

    /** Whether a plot fits: in the plan, off the roads, the square and other plots, not flood-prone or too steep. */
    private boolean fits(double[] px, double[] pz) {
        for (int k = 0; k < 4; k++) {
            if (!field.contains(px[k], pz[k], 2 * field.cell)) {
                return false;
            }
        }
        double[] sq = square.corners();
        double[] sx = {sq[0], sq[2], sq[4], sq[6]};
        double[] sz = {sq[1], sq[3], sq[5], sq[7]};
        if (Polygons.convexOverlap(px, pz, sx, sz, 0.01)) {
            return false;
        }
        for (Plot other : plots) {
            if (Polygons.convexOverlap(px, pz, other.x(), other.z(), 0.01)) {
                return false;
            }
        }
        // the ground: flooding anywhere, slopes over most of it
        int steep = 0;
        for (int u = 0; u <= 4; u++) {
            for (int v = 0; v <= 4; v++) {
                double fu = u / 4.0;
                double fv = v / 4.0;
                double x = (1 - fv) * ((1 - fu) * px[0] + fu * px[1]) + fv * ((1 - fu) * px[3] + fu * px[2]);
                double z = (1 - fv) * ((1 - fu) * pz[0] + fu * pz[1]) + fv * ((1 - fu) * pz[3] + fu * pz[2]);
                if (field.floodAt(x, z)) {
                    return false;
                }
                if (field.slopeAt(x, z) > p.siting().maxSlope() + 5) {
                    steep++;
                }
            }
        }
        return steep <= 5 && clearOfRoads(px, pz, p.plots().verge() * 0.5) == 0;
    }

    // ------------------------------------------------------------------------------------------------------ buildings

    /** A house in {@code plot}: facing the valley and the sun where it fits, else its road; none if nothing fits. */
    private void house(Plot plot) {
        SettlementParameters.Buildings b = p.buildings();
        SplittableRandom random = new SplittableRandom(Seeds.derive(seed, 1000 + plot.id()));
        boolean farm = plot.zone() == Zone.FARMSTEAD;
        boolean barn = farm && farmBarn(plot);
        BuildingKind kind = barn ? BuildingKind.HAY_BARN : farm ? BuildingKind.FARMHOUSE : BuildingKind.CHALET;
        double min = barn ? b.barnMin() : farm ? b.farmMin() : b.houseMin();
        double max = barn ? b.barnMax() : farm ? b.farmMax() : b.houseMax();
        double w = random.nextDouble(min, max);
        double d = random.nextDouble(min, max);
        int floors = barn ? 0 : plot.zone() == Zone.CORE ? 2 + random.nextInt(2) : 2;
        long buildingSeed = Seeds.derive(seed, 2000 + plot.id());
        double[] px = plot.x();
        double[] pz = plot.z();
        double fx = (px[0] + px[1]) / 2;
        double fz = (pz[0] + pz[1]) / 2;
        double ex = px[1] - px[0];
        double ez = pz[1] - pz[0];
        double el = Polygons.length(ex, ez);
        double mx = px[3] - px[0];
        double mz = pz[3] - pz[0];
        double ml = Polygons.length(mx, mz);
        mx /= ml;
        mz /= ml;
        double setback = plot.zone() == Zone.CORE ? b.coreSetback() : b.setback();
        double[] facing = new double[2];
        field.facing(fx + mx * ml / 2, fz + mz * ml / 2, 24, b.sunWeight(), facing);
        for (double scale : new double[] {1, 0.9, 0.8}) {
            double sw = Math.max(min, w * scale);
            double sd = Math.max(min, d * scale);
            // the valley and the sun, outside the core: centred in the plot, a little toward its front
            if (plot.zone() != Zone.CORE) {
                double cx = fx + mx * ml * 0.45;
                double cz = fz + mz * ml * 0.45;
                double yaw = Maths.atan2(facing[0], facing[1]);
                if (inPlot(plot, cx, cz, yaw, sw, sd) && sited(cx, cz, yaw, sw, sd, barn)) {
                    place(kind, sw, sd, floors, gableFront(), buildingSeed, cx, cz, yaw, plot.id(), plot.road());
                    woodshed(plot, kind);
                    return;
                }
            }
            // its front to its road, at the setback
            double yaw = Maths.atan2(-mx, -mz);
            double cx = fx + mx * (setback + sd / 2);
            double cz = fz + mz * (setback + sd / 2);
            if (sw <= el - 2 * b.sideGap() + 1e-9 && inPlot(plot, cx, cz, yaw, sw, sd)
                && sited(cx, cz, yaw, sw, sd, barn)) {
                place(kind, sw, sd, floors, gableFront(), buildingSeed, cx, cz, yaw, plot.id(), plot.road());
                woodshed(plot, kind);
                return;
            }
        }
    }

    /** Whether a farmstead's plot is its barn's: the second of a track's farmstead plots. */
    private boolean farmBarn(Plot plot) {
        for (Building other : buildings) {
            if (other.plot() >= 0 && plots.get(other.plot()).road() == plot.road()
                && plots.get(other.plot()).zone() == Zone.FARMSTEAD
                && other.spec().kind() == BuildingKind.FARMHOUSE) {
                return true;
            }
        }
        return false;
    }

    /** Whether a footprint lies within its plot, its sides and back the side gap within (its front the setback). */
    private boolean inPlot(Plot plot, double cx, double cz, double yaw, double w, double d) {
        double gap = Math.min(p.buildings().sideGap(), p.buildings().coreSetback());
        double c = Maths.cos(yaw);
        double s = Maths.sin(yaw);
        for (int k = 0; k < 4; k++) {
            double lx = (k == 1 || k == 2 ? 0.5 : -0.5) * w;
            double lz = (k >= 2 ? 0.5 : -0.5) * d;
            double x = cx + lx * c + lz * s;
            double z = cz - lx * s + lz * c;
            if (Polygons.convexDepth(x, z, plot.x(), plot.z()) < gap - 1e-9) {
                return false;
            }
        }
        return true;
    }

    /** Whether the ground under a footprint is buildable: no flooding, no slope beyond the siting's (a barn's). */
    private boolean sited(double cx, double cz, double yaw, double w, double d, boolean barn) {
        double max = barn ? p.barns().maxSlope() : p.siting().maxSlope();
        double c = Maths.cos(yaw);
        double s = Maths.sin(yaw);
        for (int u = -1; u <= 1; u++) {
            for (int v = -1; v <= 1; v++) {
                double x = cx + u * w / 2 * c + v * d / 2 * s;
                double z = cz - u * w / 2 * s + v * d / 2 * c;
                if (field.floodAt(x, z) || field.slopeAt(x, z) > max) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Adds a building of {@code kind}, {@code w} across the way it faces ({@code yaw}) by {@code d} deep, as the
     * buildings generator's spec: its footprint turned so its gable faces that way ({@code gable}), else its eaves (the
     * generator's ridge runs along a footprint's depth, so the footprint turns a quarter and swaps its sides).
     * {@code floors} 0: the kind's. Its ground floor at the highest ground under it, its foundation to the lowest.
     */
    private void place(BuildingKind kind, double w, double d, int floors, boolean gable, long buildingSeed, double cx,
        double cz, double yaw, int plot, int road) {
        Footprint footprint = gable ? new Footprint(cx, cz, yaw, w, d)
            : new Footprint(cx, cz, Maths.wrapAngle(yaw + Math.PI / 2), d, w);
        BuildingSpec spec = BuildingSpec.of(kind, footprint, buildingSeed);
        if (floors > 0) {
            spec = spec.withFloors(floors);
        }
        double high = -Double.MAX_VALUE;
        double low = Double.MAX_VALUE;
        for (int u = -2; u <= 2; u++) {
            for (int v = -2; v <= 2; v++) {
                double lx = u * footprint.width() / 4;
                double lz = v * footprint.depth() / 4;
                double h = field.heightAt(footprint.worldX(lx, lz), footprint.worldZ(lx, lz));
                high = Math.max(high, h);
                low = Math.min(low, h);
            }
        }
        buildings.add(new Building(buildings.size(), spec, high, low, plot, road));
        buildingSteps.add(step++);
    }

    /**
     * A woodshed beside a farmstead's farmhouse, in its plot: at either side or behind, turned with it, where it fits
     * and is sited.
     */
    private void woodshed(Plot plot, BuildingKind kind) {
        if (kind != BuildingKind.FARMHOUSE) {
            return;
        }
        Footprint f = buildings.getLast().spec().footprint();
        BuildingKind shed = BuildingKind.WOODSHED;
        double gap = 1.5;
        double[][] at = {{f.width() / 2 + gap + shed.width() / 2, 0}, {-f.width() / 2 - gap - shed.width() / 2, 0},
            {0, -f.depth() / 2 - gap - shed.depth() / 2}};
        for (double[] local : at) {
            double cx = f.worldX(local[0], local[1]);
            double cz = f.worldZ(local[0], local[1]);
            if (inPlot(plot, cx, cz, f.yaw(), shed.width(), shed.depth())
                && sited(cx, cz, f.yaw(), shed.width(), shed.depth(), true)) {
                place(shed, shed.width(), shed.depth(), 0, true, Seeds.derive(seed, 4000 + plot.id()), cx, cz,
                    f.yaw(), plot.id(), plot.road());
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------- barns

    /** Hay barns on the meadows, a jittered grid's good candidates, each with a footpath to the network. */
    private void barns() {
        SettlementParameters.Barns bp = p.barns();
        if (bp.density() <= 0) {
            return;
        }
        SplittableRandom random = new SplittableRandom(Seeds.derive(seed, 5));
        double size = (field.n - 1) * field.cell;
        int cells = (int) Math.floor(size / bp.spacing());
        List<double[]> barns = new ArrayList<>();
        for (int cj = 0; cj < cells; cj++) {
            for (int ci = 0; ci < cells; ci++) {
                double x = field.x0 + (ci + random.nextDouble(0.15, 0.85)) * bp.spacing();
                double z = field.z0 + (cj + random.nextDouble(0.15, 0.85)) * bp.spacing();
                double draw = random.nextDouble();
                double yawJitter = random.nextDouble(-0.2, 0.2);
                double w = random.nextDouble(p.buildings().barnMin(), p.buildings().barnMax());
                double d = random.nextDouble(p.buildings().barnMin(), p.buildings().barnMax());
                if (draw >= bp.density() || !field.contains(x, z, 6 * field.cell)
                    || Polygons.length(x - ox, z - oz) < p.core().villageRadius() * 0.6
                    || field.meadowAt(x, z) < bp.meadow() || nearRoad(x, z, bp.clearance()) || nearPlot(x, z,
                    bp.clearance()) || near(barns, x, z, 0.7 * bp.spacing())) {
                    continue;
                }
                double[] facing = new double[2];
                field.facing(x, z, 16, p.buildings().sunWeight(), facing);
                double yaw = Maths.atan2(facing[0], facing[1]) + yawJitter;
                if (!sited(x, z, yaw, w, d, true)) {
                    continue;
                }
                // the footpath to its door, from the network
                double doorX = x + Maths.sin(yaw) * (d / 2 + 1.5);
                double doorZ = z + Maths.cos(yaw) * (d / 2 + 1.5);
                int target = field.node(doorX, doorZ);
                if (target < 0 || blocked[target]) {
                    continue;
                }
                double[] fp = footprint(x, z, yaw, w, d);
                double[] bx = {fp[0], fp[2], fp[4], fp[6]};
                double[] bz = {fp[1], fp[3], fp[5], fp[7]};
                block(bx, bz, 1.5);
                blocked[target] = false;
                List<Integer> path = connectBarn(target, doorX, doorZ);
                if (path.isEmpty()) {
                    continue;
                }
                barns.add(new double[] {x, z});
                place(BuildingKind.HAY_BARN, w, d, 0, gableFront(), Seeds.derive(seed, 3000 + ci + 1000L * cj), x, z,
                    yaw, Settlement.NONE, roads.size() - 1);
            }
        }
    }

    private List<Integer> connectBarn(int target, double doorX, double doorZ) {
        List<Integer> ids = connect(RoadKind.FOOTPATH, target, doorX, doorZ);
        barnPaths.addAll(ids);
        return ids;
    }

    private static double[] footprint(double x, double z, double yaw, double w, double d) {
        double c = Maths.cos(yaw);
        double s = Maths.sin(yaw);
        double[] out = new double[8];
        double[][] at = {{-w / 2, -d / 2}, {w / 2, -d / 2}, {w / 2, d / 2}, {-w / 2, d / 2}};
        for (int k = 0; k < 4; k++) {
            out[2 * k] = x + at[k][0] * c + at[k][1] * s;
            out[2 * k + 1] = z - at[k][0] * s + at[k][1] * c;
        }
        return out;
    }

    private boolean nearPlot(double x, double z, double distance) {
        for (Plot plot : plots) {
            if (Polygons.convexDepth(x, z, plot.x(), plot.z()) > -distance) {
                return true;
            }
        }
        return false;
    }

    private static boolean near(List<double[]> points, double x, double z, double distance) {
        for (double[] q : points) {
            if (Polygons.length(q[0] - x, q[1] - z) < distance) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------------------------------------------------- finish

    /**
     * Drops plots the last paths came too near (and their buildings), renumbers, and adds what stands along the
     * finished roads: grading, clearings, lamps, fences, props.
     */
    private Settlement finish() {
        List<Plot> keptPlots = new ArrayList<>();
        List<Integer> keptPlotSteps = new ArrayList<>();
        int[] plotIds = new int[plots.size()];
        Arrays.fill(plotIds, -1);
        boolean[] built = new boolean[plots.size()];
        for (Building b : buildings) {
            if (b.plot() >= 0) {
                built[b.plot()] = true;
            }
        }
        for (Plot plot : plots) {
            // a plot keeps off every road (the barns' paths came last) and has its building
            if (built[plot.id()] && clearOfRoads(plot.x(), plot.z(), p.plots().verge() * 0.5) == 0) {
                plotIds[plot.id()] = keptPlots.size();
                keptPlots.add(new Plot(keptPlots.size(), plot.x(), plot.z(), plot.road(), plot.zone()));
                keptPlotSteps.add(plotSteps.get(plot.id()));
            }
        }
        List<Building> keptBuildings = new ArrayList<>();
        List<Integer> keptBuildingSteps = new ArrayList<>();
        for (Building b : buildings) {
            int plot = b.plot() >= 0 ? plotIds[b.plot()] : Settlement.NONE;
            if (b.plot() >= 0 && plot < 0) {
                continue;
            }
            keptBuildings.add(new Building(keptBuildings.size(), b.spec(), b.y(), b.foundation(), plot, b.road()));
            keptBuildingSteps.add(buildingSteps.get(b.id()));
        }
        Grading grading = Rasters.grading(p, field, roads, square, Seeds.derive(seed, 7));
        Clearing clearing = Rasters.clearing(p, field, grading, square, keptPlots, keptBuildings);
        Furnishing furnishing = new Furnishing(p, field, grading, roads, lines, square, innSide, keptPlots,
            keptBuildings, barnPaths, Seeds.derive(seed, 6));
        int[] order = new int[roads.size() + keptPlots.size() + keptBuildings.size()];
        int k = 0;
        for (int s : roadSteps) {
            order[k++] = s;
        }
        for (int s : keptPlotSteps) {
            order[k++] = s;
        }
        for (int s : keptBuildingSteps) {
            order[k++] = s;
        }
        return new Settlement(seed, ox, oz, p, square, roads, keptPlots, keptBuildings, furnishing.props(),
            furnishing.lamps(), furnishing.fences(), clearing, grading, order);
    }
}
