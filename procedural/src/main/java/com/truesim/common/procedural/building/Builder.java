package com.truesim.common.procedural.building;

import static com.truesim.common.procedural.building.Kit.p;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.building.Plan.Face;
import com.truesim.common.procedural.building.Plan.Hole;
import com.truesim.common.procedural.building.Plan.Segment;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds one level of detail of a planned building into a {@link Kit}: the foundation stepping down the slope, the
 * walls with their openings, windows (frames, mullions, glass, sills, casings, shutters, flower boxes), doors and
 * steps, log ends at the corners, bands and dentils, rooms (floors, ceilings, beams, partitions, the hearth), the roof
 * (its slabs, ridge, wall plates, rafter ends, purlins with carved ends, knee braces, scalloped gable boards, poles
 * and stones), the chimney, balconies with cut-out railings, the woodpile, the farmhouse's arched gable board, the
 * church's tower, the inn's sign, the woodshed.
 *
 * <p>Levels: 0 everything; 1 without small carving, sash bars, flowers and single logs; 2 closed walls with dark
 * panes, no rooms, plain balconies; 3 the volumes.
 */
@AllowAllocation("generating, at load time")
final class Builder {

    /** How far each level may differ from level 0, metres. */
    static final double[] ERRORS = {0, 0.06, 0.3, 0.9};
    /** The levels made. */
    static final int LEVELS = ERRORS.length;

    private static final double RISE = Plan.RISE;
    private static final double TREAD = Plan.TREAD;

    private final Plan p;
    private final Kit kit;
    private final int level;
    private final boolean rooms;
    private final Rng rng;
    private final BuildingSlot cover;

    Builder(Plan plan, Kit kit, int level) {
        this.p = plan;
        this.kit = kit;
        this.level = level;
        this.rooms = level <= 1 && plan.options.interiors();
        this.rng = Rng.seeded(Seeds.derive(plan.spec.seed(), 2));
        this.cover = plan.cover.slot();
    }

    void build() {
        foundation();
        if (p.kind == BuildingKind.WOODSHED) {
            woodshed();
            roof();
            return;
        }
        walls();
        gables();
        logCorners();
        bands();
        floors();
        if (rooms) {
            partitions();
            hearth();
        }
        roof();
        chimney();
        balconies();
        woodpile();
        ruendi();
        steps();
        cellarDoors();
        if (p.kind == BuildingKind.CHURCH) {
            tower();
            quoins();
        }
        if (p.kind == BuildingKind.INN) {
            sign();
        }
    }

    // ---------------------------------------------------------------- foundation

    /**
     * The plinth round each rectangle of the plan: runs of stone at most a step long, each reaching the embedding
     * below the lowest ground along it, so the foundation steps down the slope and meets the ground everywhere.
     */
    private void foundation() {
        double out = 0.1;
        double depth = 0.6;
        double topY = 0.08;
        double embed = p.options.embed();
        double step = level >= 3 ? 1e9 : p.options.step();
        for (Plan.Rect r : p.foundations) {
            // front and back: the full width; the sides between them
            for (int end = -1; end <= 1; end += 2) {
                double zo = end > 0 ? r.z1() + out : r.z0() - out;
                double zi = end > 0 ? r.z1() - depth : r.z0() + depth;
                double a = r.x0() - out;
                double b = r.x1() + out;
                int runs = Math.max(1, (int) Math.ceil((b - a) / step - 1e-9));
                for (int i = 0; i < runs; i++) {
                    double x0 = a + (b - a) * i / runs;
                    double x1 = a + (b - a) * (i + 1) / runs;
                    double low = Math.min(p.lowest(x0, zo, x1, zo), p.lowest(x0, zi, x1, zi)) - embed;
                    kit.box(BuildingSlot.STONE, x0, low, Math.min(zo, zi), x1, topY, Math.max(zo, zi));
                }
            }
            for (int side = -1; side <= 1; side += 2) {
                double xo = side > 0 ? r.x1() + out : r.x0() - out;
                double xi = side > 0 ? r.x1() - depth : r.x0() + depth;
                double a = r.z0() + depth;
                double b = r.z1() - depth;
                int runs = Math.max(1, (int) Math.ceil((b - a) / step - 1e-9));
                for (int i = 0; i < runs; i++) {
                    double za = a + (b - a) * i / runs;
                    double zb = a + (b - a) * (i + 1) / runs;
                    double low = Math.min(p.lowest(xo, za, xo, zb), p.lowest(xi, za, xi, zb)) - embed;
                    kit.box(BuildingSlot.STONE, Math.min(xo, xi), low, za, Math.max(xo, xi), topY, zb);
                }
            }
        }
    }

    // ---------------------------------------------------------------- walls

    private void walls() {
        for (Segment s : p.walls) {
            double[] start = p.start(s.face());
            double[] along = Plan.along(s.face());
            double[] inward = Plan.inward(s.face());
            double[] o = p(start[0] + along[0] * s.from(), s.y0(), start[2] + along[2] * s.from());
            List<double[]> holes = new ArrayList<>();
            if (level <= 1) {
                for (Hole h : s.holes()) {
                    holes.add(new double[] {h.x() - s.from(), h.y(), h.w(), h.h()});
                }
            }
            kit.wall(o, along, inward, s.to() - s.from(), s.height(), s.thick(), holes, s.outer(), s.inner(),
                s.reveal(), s.outer(), s.logs());
            for (Hole h : s.holes()) {
                dress(s.face(), s, h);
            }
        }
    }

    /** What fills and frames an opening, at this level. */
    private void dress(Face face, Segment s, Hole h) {
        boolean timber = s.outer() == BuildingSlot.TIMBER;
        double x0 = h.x();
        double x1 = h.x() + h.w();
        double y0 = s.y0() + h.y();
        double y1 = y0 + h.h();
        if (level >= 2) {
            switch (h.kind()) {
                case WINDOW, SLIT, BELFRY -> {
                    panel(face, x0, y0, x1, y1, -0.012, 0.004, BuildingSlot.WINDOW);
                    if (level == 2 && h.kind() == Opening.Kind.WINDOW) {
                        ring(face, x0, y0, x1, y1, 0.06, -0.022, 0.0, BuildingSlot.TRIM);
                    }
                }
                case DOOR, HAY_DOOR -> panel(face, x0, y0, x1, y1, -0.03, 0.004, BuildingSlot.DOOR);
                default -> {
                }
            }
            if (level == 2 && h.shutters()) {
                shutters(face, x0, y0, x1, y1, timber);
            }
            return;
        }
        switch (h.kind()) {
            case WINDOW -> window(face, s, h, timber, x0, y0, x1, y1);
            case DOOR -> door(face, s, h, timber, x0, y0, x1, y1, false);
            case HAY_DOOR -> door(face, s, h, timber, x0, y0, x1, y1, true);
            case BELFRY -> louvres(face, x0, y0, x1, y1, s.thick());
            default -> {
            }
        }
    }

    /** A box in {@code face}'s frame: along from x0 to x1, up from y0 to y1, and inward from d0 to d1. */
    private void panel(Face face, double x0, double y0, double x1, double y1, double d0, double d1, BuildingSlot slot) {
        double[] o = p.point(face, x0, y0, d0);
        double[] a = Kit.scale(Plan.along(face), x1 - x0);
        double[] b = p(0, y1 - y0, 0);
        double[] c = Kit.scale(Plan.inward(face), d1 - d0);
        kit.beam(o, a, b, c, slot);
    }

    /** A frame of four bars {@code bar} wide round the rectangle, from depth d0 to d1. */
    private void ring(Face face, double x0, double y0, double x1, double y1, double bar, double d0, double d1,
        BuildingSlot slot) {
        panel(face, x0, y0, x0 + bar, y1, d0, d1, slot);
        panel(face, x1 - bar, y0, x1, y1, d0, d1, slot);
        panel(face, x0 + bar, y0, x1 - bar, y0 + bar, d0, d1, slot);
        panel(face, x0 + bar, y1 - bar, x1 - bar, y1, d0, d1, slot);
    }

    private void window(Face face, Segment s, Hole h, boolean timber, double x0, double y0, double x1, double y1) {
        double fd = timber ? 0.05 : Math.min(0.14, s.thick() * 0.3);
        double f = 0.07;
        ring(face, x0, y0, x1, y1, f, fd, fd + 0.07, BuildingSlot.TRIM);
        double xc = (x0 + x1) / 2;
        if (h.w() > 0.5) {
            panel(face, xc - 0.025, y0 + f, xc + 0.025, y1 - f, fd + 0.01, fd + 0.06, BuildingSlot.TRIM);
            double[] bars = level == 0 ? new double[] {1 / 3.0, 2 / 3.0} : new double[] {2 / 3.0};
            for (double fr : bars) {
                double y = y0 + f + (y1 - y0 - 2 * f) * fr;
                panel(face, x0 + f, y - 0.017, x1 - f, y + 0.017, fd + 0.015, fd + 0.055, BuildingSlot.TRIM);
            }
        }
        panel(face, x0 + 0.03, y0 + 0.03, x1 - 0.03, y1 - 0.03, fd + 0.03, fd + 0.038, BuildingSlot.GLASS);
        // the sill outside, and over the window a casing (log walls) or a lintel (stone)
        if (timber) {
            panel(face, x0 - 0.09, y0 - 0.05, x1 + 0.09, y0 + 0.005, -0.075, fd, BuildingSlot.TRIM);
            panel(face, x0 - 0.1, y0 - 0.05, x0, y1 + 0.1, -0.025, 0.005, BuildingSlot.TRIM);
            panel(face, x1, y0 - 0.05, x1 + 0.1, y1 + 0.1, -0.025, 0.005, BuildingSlot.TRIM);
            panel(face, x0, y1, x1, y1 + 0.1, -0.025, 0.005, BuildingSlot.TRIM);
            if (level == 0) {
                crown(face, x0 - 0.14, x1 + 0.14, y1 + 0.1);
            } else {
                panel(face, x0 - 0.14, y1 + 0.1, x1 + 0.14, y1 + 0.16, -0.05, 0.005, BuildingSlot.TRIM);
            }
        } else {
            BuildingSlot stone = s.outer() == BuildingSlot.PLASTER && p.kind != BuildingKind.CHURCH
                ? BuildingSlot.TRIM : BuildingSlot.STONE;
            panel(face, x0 - 0.06, y0 - 0.08, x1 + 0.06, y0 + 0.005, -0.06, fd, stone);
            if (p.kind == BuildingKind.CHURCH) {
                // a stone surround
                panel(face, x0 - 0.16, y0, x0, y1 + 0.16, -0.03, 0.01, BuildingSlot.STONE);
                panel(face, x1, y0, x1 + 0.16, y1 + 0.16, -0.03, 0.01, BuildingSlot.STONE);
                panel(face, x0, y1, x1, y1 + 0.16, -0.03, 0.01, BuildingSlot.STONE);
            } else {
                panel(face, x0 - 0.15, y1, x1 + 0.15, y1 + 0.16, -0.012, 0.2, BuildingSlot.TRIM);
            }
        }
        if (h.shutters()) {
            shutters(face, x0, y0, x1, y1, timber);
        }
        if (h.flowers()) {
            flowerBox(face, x0 - 0.05, x1 + 0.05, y0 - 0.06, timber ? 0.075 : 0.06);
        }
    }

    /** A carved crown board over a window: an arched top. */
    private void crown(Face face, double x0, double x1, double y) {
        int n = 9;
        double[] t = new double[n];
        double[] lo = new double[n];
        double[] hi = new double[n];
        for (int i = 0; i < n; i++) {
            double f = i / (double) (n - 1);
            t[i] = (x1 - x0) * f;
            lo[i] = y;
            hi[i] = y + 0.035 + 0.08 * Maths.sin(Math.PI * f);
        }
        kit.strip(p.point(face, x0, 0, -0.05), Plan.along(face), p(0, 1, 0), Plan.inward(face), t, lo, hi, 0.055,
            BuildingSlot.TRIM);
    }

    /** Two shutters open against the wall beside the opening. */
    private void shutters(Face face, double x0, double y0, double x1, double y1, boolean timber) {
        double leaf = (x1 - x0) / 2 + 0.02;
        double gap = timber ? 0.11 : 0.03;
        double d0 = timber ? -0.065 : -0.04;
        double d1 = d0 + 0.035;
        for (int side = -1; side <= 1; side += 2) {
            double a = side < 0 ? x0 - gap - leaf : x1 + gap;
            panel(face, a, y0, a + leaf, y1, d0, d1, BuildingSlot.SHUTTERS);
            if (level == 0) {
                for (double y : new double[] {y0 + 0.12, y1 - 0.2}) {
                    panel(face, a + 0.03, y, a + leaf - 0.03, y + 0.08, d0 - 0.015, d0 + 0.002,
                        BuildingSlot.SHUTTERS);
                }
            }
        }
    }

    /** A flower box under a sill: a wooden box, geraniums and their leaves. */
    private void flowerBox(Face face, double x0, double x1, double top, double proud) {
        double d0 = -proud - 0.25;
        double d1 = -proud - 0.005;
        panel(face, x0, top - 0.2, x1, top, d0, d1, BuildingSlot.TRIM);
        flowers(face, x0, x1, top, (d0 + d1) / 2, level);
    }

    /** Geraniums on a box's top: leaves in clumps, flowers above and hanging over the front. */
    private void flowers(Face face, double x0, double x1, double top, double depth, int detail) {
        if (detail > 1) {
            return;
        }
        int clumps = Math.max(2, (int) Math.round((x1 - x0) / (detail == 0 ? 0.17 : 0.4)));
        for (int i = 0; i < clumps; i++) {
            double x = x0 + (x1 - x0) * (i + 0.5) / clumps;
            double[] c = p.point(face, x, top + 0.04, depth + (rng.nextDouble() - 0.5) * 0.06);
            double r = detail == 0 ? 0.1 : 0.2;
            kit.blob(c, r * (0.9 + 0.3 * rng.nextDouble()), 0.09, r * 0.9, BuildingSlot.FOLIAGE);
            if (detail == 0) {
                for (int k = 0; k < 2; k++) {
                    double[] f = p.point(face, x + (rng.nextDouble() - 0.5) * 0.12, top + 0.12 + 0.05
                        * rng.nextDouble(), depth + (rng.nextDouble() - 0.5) * 0.12);
                    kit.blob(f, 0.045, 0.04, 0.045, BuildingSlot.FLOWERS);
                }
                if (rng.nextDouble() < 0.5) {
                    double[] hang = p.point(face, x, top - 0.12 - 0.1 * rng.nextDouble(), depth - 0.14);
                    kit.blob(hang, 0.06, 0.08, 0.05, BuildingSlot.FOLIAGE);
                    kit.blob(Kit.add(hang, p(0, -0.06, 0)), 0.04, 0.035, 0.04, BuildingSlot.FLOWERS);
                }
            }
        }
    }

    private void door(Face face, Segment s, Hole h, boolean timber, double x0, double y0, double x1, double y1,
        boolean hay) {
        double ld = timber ? 0.05 : 0.12;
        if (h.open()) {
            // swung in against the reveal: the room's inside, past the wall
            double t = s.thick();
            panel(face, x0 + 0.02, y0 + 0.01, x0 + 0.08, y1 - 0.02, t, t + Math.max(0.3, h.w() - 0.1),
                BuildingSlot.DOOR);
        } else {
            panel(face, x0, y0, x1, y1, ld, ld + 0.06, BuildingSlot.DOOR);
            if (level == 0) {
                if (hay) {
                    // battens and a brace
                    for (double y : new double[] {y0 + 0.2, y1 - 0.3}) {
                        panel(face, x0 + 0.05, y, x1 - 0.05, y + 0.12, ld - 0.03, ld, BuildingSlot.TRIM);
                    }
                    double[] o = p.point(face, x0 + 0.1, y0 + 0.32, ld - 0.03);
                    double[] a = Kit.add(Kit.scale(Plan.along(face), x1 - x0 - 0.3), p(0, y1 - y0 - 0.72, 0));
                    double[] b = Kit.scale(Kit.normalize(Kit.cross(a, Plan.inward(face))), 0.1);
                    kit.beam(o, a, b, Kit.scale(Plan.inward(face), 0.03), BuildingSlot.TRIM);
                    panel(face, (x0 + x1) / 2 - 0.02, y0, (x0 + x1) / 2 + 0.02, y1, ld - 0.01, ld, BuildingSlot.TRIM);
                } else {
                    double m = 0.12;
                    double split = y0 + (y1 - y0) * 0.45;
                    panel(face, x0 + m, y0 + 0.2, x1 - m, split - 0.08, ld - 0.015, ld, BuildingSlot.DOOR);
                    panel(face, x0 + m, split + 0.08, x1 - m, y1 - 0.2, ld - 0.015, ld, BuildingSlot.DOOR);
                    panel(face, x1 - 0.13, y0 + 1.0, x1 - 0.08, y0 + 1.12, ld - 0.05, ld, BuildingSlot.METAL);
                }
            }
        }
        // the casing round it
        if (timber || p.kind == BuildingKind.HAY_BARN) {
            panel(face, x0 - 0.12, y0, x0, y1 + 0.12, -0.03, 0.01, BuildingSlot.TRIM);
            panel(face, x1, y0, x1 + 0.12, y1 + 0.12, -0.03, 0.01, BuildingSlot.TRIM);
            panel(face, x0, y1, x1, y1 + 0.12, -0.03, 0.01, BuildingSlot.TRIM);
        } else {
            BuildingSlot frame = p.kind == BuildingKind.CHURCH ? BuildingSlot.STONE : BuildingSlot.TRIM;
            panel(face, x0 - 0.18, y0, x0, y1 + 0.18, -0.035, 0.01, frame);
            panel(face, x1, y0, x1 + 0.18, y1 + 0.18, -0.035, 0.01, frame);
            panel(face, x0, y1, x1, y1 + 0.18, -0.035, 0.01, frame);
        }
    }

    /** Louvres in a belfry's opening: slats sloping out and down. */
    private void louvres(Face face, double x0, double y0, double x1, double y1, double thick) {
        int n = 6;
        double[] a = Kit.scale(Plan.along(face), x1 - x0);
        double[] in = Plan.inward(face);
        for (int i = 0; i < n; i++) {
            double y = y0 + 0.15 + (y1 - y0 - 0.3) * i / (n - 1);
            double[] o = p.point(face, x0, y - 0.1, thick * 0.35);
            double[] b = p(0, 0.035, 0);
            double[] c = Kit.add(Kit.scale(in, 0.22), p(0, 0.16, 0));
            kit.beam(o, a, b, c, BuildingSlot.TRIM);
        }
    }

    // ---------------------------------------------------------------- gables, logs, bands

    /** The walls' top storey on {@code face}, or null. */
    private Segment top(Face face) {
        Segment found = null;
        for (Segment s : p.walls) {
            if (s.face() == face && (found == null || s.storey() > found.storey())) {
                found = s;
            }
        }
        return found;
    }

    /** The gables: the walls' triangles up to the roof's underside, front and back; the wall plates on the sides. */
    private void gables() {
        double e = p.eaves;
        for (Face face : new Face[] {Face.FRONT, Face.BACK}) {
            Segment s = top(face);
            double[] o = face == Face.FRONT ? p(-p.hw, 0, p.z1) : p(-p.hw, 0, p.z0);
            double[] in = p(0, 0, face == Face.FRONT ? -1 : 1);
            double[] t = {0, p.hw, p.w};
            double[] lo = {e, e, e};
            double[] hi = {e + Plan.PLATE, p.underside(0), e + Plan.PLATE};
            kit.strip(o, p(1, 0, 0), p(0, 1, 0), in, t, lo, hi, s.thick(), s.outer(), s.outer(), s.outer(),
                s.outer(), s.inner());
        }
        double thick = top(Face.RIGHT).thick();
        for (int side = -1; side <= 1; side += 2) {
            double xo = side * (p.hw + 0.02);
            double xi = side * (p.hw - thick - 0.02);
            kit.box(BuildingSlot.TRIM, Math.min(xo, xi), e - 0.02, p.z0, Math.max(xo, xi), e + Plan.PLATE
                + (thick + 0.04) * p.tan, p.z1);
        }
    }

    /** Log ends crossing at the corners of log storeys (Blockbau's Vorstoesse). */
    private void logCorners() {
        if (level >= 2) {
            return;
        }
        for (Segment s : p.walls) {
            if (!s.logs() || s.face().side()) {
                continue;
            }
            boolean front = s.face() == Face.FRONT;
            double t = s.thick();
            double zFace = front ? p.z1 : p.z0;
            double zIn = front ? p.z1 - t : p.z0 + t;
            double zOut = front ? p.z1 + Plan.LOG_END : p.z0 - Plan.LOG_END;
            int n = Math.max(1, (int) Math.round(s.height() / Plan.COURSE));
            double c = s.height() / n;
            for (int sx = -1; sx <= 1; sx += 2) {
                double xFace = sx * p.hw;
                double xOut = sx * (p.hw + Plan.LOG_END);
                double xIn = sx * (p.hw - t);
                if (level == 1) {
                    kit.box(BuildingSlot.TRIM, Math.min(xFace, xOut), s.y0(), Math.min(zFace, zIn),
                        Math.max(xFace, xOut), s.y0() + s.height(), Math.max(zFace, zIn));
                    kit.box(BuildingSlot.TRIM, Math.min(xFace, xIn), s.y0(), Math.min(zFace, zOut),
                        Math.max(xFace, xIn), s.y0() + s.height(), Math.max(zFace, zOut));
                    continue;
                }
                for (int k = 0; k < n; k++) {
                    double y0 = s.y0() + k * c + 0.012;
                    double y1 = s.y0() + (k + 1) * c - 0.012;
                    double reach = Plan.LOG_END * (0.92 + 0.16 * rng.nextDouble());
                    if (k % 2 == 0) {
                        double xo = sx * (p.hw + reach);
                        kit.box(BuildingSlot.TRIM, Math.min(xFace, xo), y0, Math.min(zFace, zIn), Math.max(xFace, xo),
                            y1, Math.max(zFace, zIn));
                    } else {
                        double zo = front ? p.z1 + reach : p.z0 - reach;
                        kit.box(BuildingSlot.TRIM, Math.min(xFace, xIn), y0, Math.min(zFace, zo), Math.max(xFace, xIn),
                            y1, Math.max(zFace, zo));
                    }
                }
            }
        }
    }

    /** A band beam round the house where a log storey sits on the one below, dentils under it on the front. */
    private void bands() {
        if (level >= 2) {
            return;
        }
        for (int s = 1; s < p.floors; s++) {
            boolean logs = false;
            for (Segment seg : p.walls) {
                logs |= seg.storey() == s && seg.logs();
            }
            if (!logs) {
                continue;
            }
            double y = p.storeyY[s];
            double out = 0.05;
            kit.box(BuildingSlot.TRIM, -p.hw - out, y - 0.12, p.z1 - 0.1, p.hw + out, y + 0.1, p.z1 + out);
            kit.box(BuildingSlot.TRIM, -p.hw - out, y - 0.12, p.z0 - out, p.hw + out, y + 0.1, p.z0 + 0.1);
            kit.box(BuildingSlot.TRIM, p.hw - 0.1, y - 0.12, p.z0, p.hw + out, y + 0.1, p.z1);
            kit.box(BuildingSlot.TRIM, -p.hw - out, y - 0.12, p.z0, -p.hw + 0.1, y + 0.1, p.z1);
            if (level == 0) {
                for (double x = -p.hw + 0.12; x < p.hw - 0.1; x += 0.12) {
                    kit.box(BuildingSlot.TRIM, x - 0.03, y - 0.2, p.z1 - 0.01, x + 0.03, y - 0.135, p.z1 + 0.04);
                }
            }
        }
    }

    // ---------------------------------------------------------------- rooms

    /** The floor slabs: the ground floor's, one under each storey above (the ceiling below), the attic's. */
    private void floors() {
        boolean inhabited = p.kind.inhabited();
        if (level >= 2 || inhabited && !rooms) {
            return;
        }
        double t0 = p.walls.getFirst().thick();
        BuildingSlot ground = inhabited ? BuildingSlot.FLOOR : BuildingSlot.STONE;
        kit.beam(p(-p.hw + t0, -0.3, p.z0 + t0), p(p.w - 2 * t0, 0, 0), p(0, 0.3, 0), p(0, 0, p.z1 - p.z0 - 2 * t0),
            BuildingSlot.STONE, BuildingSlot.STONE, BuildingSlot.STONE, ground, BuildingSlot.STONE,
            BuildingSlot.STONE);
        for (int s = 1; s <= p.floors; s++) {
            double y = p.storeyY[s];
            double t = thickness(Math.min(s, p.floors - 1));
            BuildingSlot ceiling = p.kind == BuildingKind.CHURCH ? BuildingSlot.INTERIOR : BuildingSlot.TRIM;
            BuildingSlot top = s == p.floors ? BuildingSlot.TRIM : BuildingSlot.FLOOR;
            double slab = p.kind == BuildingKind.CHURCH ? 0.3 : Plan.SLAB;
            kit.beam(p(-p.hw + t, y - slab, p.z0 + t), p(p.w - 2 * t, 0, 0), p(0, slab, 0),
                p(0, 0, p.z1 - p.z0 - 2 * t), BuildingSlot.TRIM, BuildingSlot.TRIM, ceiling, top, BuildingSlot.TRIM,
                BuildingSlot.TRIM);
            if (level == 0 && inhabited && p.kind != BuildingKind.CHURCH) {
                double tb = thickness(s - 1);
                for (double z = p.z0 + tb + 0.9; z < p.z1 - tb - 0.5; z += 1.3) {
                    kit.box(BuildingSlot.TRIM, -p.hw + tb, y - slab - 0.15, z - 0.08, p.hw - tb, y - slab, z + 0.08);
                }
            }
        }
        if (p.kind == BuildingKind.CHURCH) {
            double ts = p.towerSize / 2 - Plan.TOWER_WALL;
            kit.beam(p(-ts, -0.3, p.hd - p.towerSize + Plan.TOWER_WALL), p(2 * ts, 0, 0), p(0, 0.3, 0),
                p(0, 0, p.towerSize - 2 * Plan.TOWER_WALL), BuildingSlot.STONE, BuildingSlot.STONE,
                BuildingSlot.STONE, BuildingSlot.FLOOR, BuildingSlot.STONE, BuildingSlot.STONE);
        }
    }

    /** The thickness of storey {@code s}'s front wall. */
    private double thickness(int s) {
        for (Segment seg : p.walls) {
            if (seg.storey() == s && seg.face() == Face.FRONT) {
                return seg.thick();
            }
        }
        return p.walls.getFirst().thick();
    }

    private void partitions() {
        for (Plan.Partition q : p.partitions) {
            List<double[]> holes = new ArrayList<>();
            for (Hole h : q.holes()) {
                holes.add(new double[] {h.x(), h.y(), h.w(), h.h()});
            }
            kit.wall(p(q.x0(), q.y0(), q.z()), p(1, 0, 0), p(0, 0, -1), q.x1() - q.x0(), q.height(), q.thick(),
                holes, q.front(), q.back(), q.front(), q.front(), false);
        }
    }

    /** An open fireplace against the wall behind it: hearthstone, cheeks, lintel, hood, soot, a mantel, logs. */
    private void hearth() {
        if (p.hearth == null) {
            return;
        }
        double x = p.hearth[0];
        double z = p.hearth[1];
        double ceiling = p.storeyY[1] - Plan.SLAB;
        BuildingSlot h = BuildingSlot.HEARTH;
        kit.box(h, x - 0.95, 0, z, x + 0.95, 0.12, z + 1.0);
        kit.box(h, x - 0.9, 0.12, z, x - 0.62, 1.05, z + 0.7);
        kit.box(h, x + 0.62, 0.12, z, x + 0.9, 1.05, z + 0.7);
        kit.box(h, x - 0.9, 1.05, z, x + 0.9, 1.32, z + 0.75);
        kit.box(h, x - 0.66, 1.32, z, x + 0.66, ceiling, z + 0.58);
        kit.box(h, x - 0.62, 0.12, z, x + 0.62, 1.05, z + 0.06);
        if (level == 0) {
            kit.box(BuildingSlot.TRIM, x - 1.0, 1.32, z, x + 1.0, 1.39, z + 0.84);
            kit.log(p(x - 0.3, 0.17, z + 0.3), p(0.6, 0, 0.08), 0.05, 6, 0.3, BuildingSlot.FIREWOOD,
                BuildingSlot.TRIM);
            kit.log(p(x - 0.25, 0.25, z + 0.42), p(0.5, 0.02, -0.12), 0.045, 6, 0.9, BuildingSlot.FIREWOOD,
                BuildingSlot.TRIM);
        }
    }

    // ---------------------------------------------------------------- the roof

    private void roof() {
        double zr0 = p.z0 - p.gable;
        double zr1 = p.z1 + p.gable;
        double run = p.hw + p.eave;
        double thick = Plan.ROOF / p.cos;
        for (int side = -1; side <= 1; side += 2) {
            double[] lo = {p.underside(run), p.underside(0)};
            double[] hi = {lo[0] + thick, lo[1] + thick};
            kit.strip(p(side * run, 0, zr0), p(-side, 0, 0), p(0, 1, 0), p(0, 0, 1), new double[] {0, run}, lo, hi,
                zr1 - zr0, cover, BuildingSlot.TRIM, BuildingSlot.TRIM, BuildingSlot.TRIM, BuildingSlot.TRIM);
        }
        // the ridge
        double r = 0.2;
        kit.strip(p(-r, 0, zr0 - 0.04), p(1, 0, 0), p(0, 1, 0), p(0, 0, 1), new double[] {0, r, 2 * r},
            new double[] {p.top(r) - 0.05, p.top(0) - 0.05, p.top(r) - 0.05},
            new double[] {p.top(0) + 0.08, p.top(0) + 0.08, p.top(0) + 0.08}, zr1 - zr0 + 0.08, cover);
        if (level <= 1) {
            rafters(zr0, zr1);
            purlins();
            gableBoards(zr0, zr1);
        }
        if (level <= 1 && p.cover == RoofCover.STONE_WEIGHTED) {
            stones(zr0, zr1);
        }
    }

    /** The rafters' ends under the eaves, from the wall out to the fascia. */
    private void rafters(double zr0, double zr1) {
        double spacing = level == 0 ? 0.8 : 1.6;
        double depth = 0.16 / p.cos;
        double inset = 0.12;
        double reach = p.eave - 0.05 + inset;
        int n = (int) Math.floor((zr1 - zr0 - 0.5) / spacing);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i <= n; i++) {
                double z = zr0 + 0.25 + (zr1 - zr0 - 0.5) * i / Math.max(1, n);
                double x = side * (p.hw - inset);
                double[] o = p(x, p.underside(p.hw - inset) - depth + 0.01, z - 0.06);
                kit.beam(o, p(side * reach, -reach * p.tan, 0), p(0, depth, 0), p(0, 0, 0.12), BuildingSlot.TRIM);
            }
        }
    }

    /** Purlins standing out under the gables' overhang, carved ends; knee braces under the outer ones. */
    private void purlins() {
        double[] xs = {0, -p.hw * 0.5, p.hw * 0.5, -(p.hw - 0.12), p.hw - 0.12};
        for (int end = -1; end <= 1; end += 2) {
            double face = end > 0 ? p.z1 : p.z0;
            double length = p.gable + 0.25;
            for (double x : xs) {
                double topY = p.underside(Math.abs(x)) + 0.02;
                double[] o = p(x - 0.09, 0, face - end * 0.3);
                double[] dir = p(0, 0, end);
                if (level == 0) {
                    int n = 9;
                    double[] t = new double[n + 1];
                    double[] lo = new double[n + 1];
                    double[] hi = new double[n + 1];
                    t[0] = 0;
                    lo[0] = topY - 0.26;
                    hi[0] = topY;
                    for (int i = 1; i <= n; i++) {
                        double u = (i - 1) / (double) (n - 1);
                        t[i] = length - 0.5 + 0.5 * u;
                        lo[i] = topY - 0.26 + 0.17 * (1 - Math.sqrt(Math.max(0, 1 - u * u)));
                        hi[i] = topY;
                    }
                    kit.strip(o, dir, p(0, 1, 0), p(1, 0, 0), t, lo, hi, 0.18, BuildingSlot.TRIM);
                } else {
                    kit.box(BuildingSlot.TRIM, x - 0.09, topY - 0.26, Math.min(face - end * 0.3, face + end * (length
                        - 0.3)), x + 0.09, topY, Math.max(face - end * 0.3, face + end * (length - 0.3)));
                }
                if (Math.abs(x) > 0.1 && p.gable > 0.75 && p.kind != BuildingKind.CHURCH) {
                    double ya = topY - 1.25;
                    double yb = topY - 0.26;
                    double reach = (length - 0.3) * 0.62;
                    double[] a = p(0, yb - ya, end * (reach + 0.05));
                    double[] c = Kit.scale(Kit.normalize(p(0, a[2], -a[1])), 0.12);
                    kit.beam(p(x - 0.06, ya, face - end * 0.05), a, p(0.12, 0, 0), c, BuildingSlot.TRIM);
                }
            }
        }
    }

    /** Boards along the verges under the roof's edge, their lower edges scalloped. */
    private void gableBoards(double zr0, double zr1) {
        double run = p.hw + p.eave;
        double slope = run / p.cos;
        double depth = 0.3;
        for (int end = -1; end <= 1; end += 2) {
            double z = end > 0 ? zr1 - 0.085 : zr0 + 0.05;
            for (int side = -1; side <= 1; side += 2) {
                double[] o = p(side * run, p.underside(run), z);
                double[] tAxis = p(-side * p.cos, Maths.sin(p.pitch), 0);
                double[] vAxis = p(-side * Maths.sin(p.pitch), -p.cos, 0);
                int periods = Math.max(1, (int) Math.round(slope / 0.4));
                int per = level == 0 ? 6 : 1;
                int n = periods * per + 1;
                double[] t = new double[n];
                double[] lo = new double[n];
                double[] hi = new double[n];
                for (int i = 0; i < n; i++) {
                    t[i] = slope * i / (n - 1);
                    lo[i] = -0.03;
                    double u = (i % per) / (double) per;
                    hi[i] = level == 0 ? depth - 0.09 * Math.sqrt(Math.max(0, 1 - (2 * u - 1) * (2 * u - 1)))
                        : depth - 0.04;
                }
                kit.strip(o, tAxis, vAxis, p(0, 0, 1), t, lo, hi, 0.035, BuildingSlot.TRIM);
            }
        }
    }

    /** A stone-weighted roof: poles across the slope, stones resting on them. */
    private void stones(double zr0, double zr1) {
        double run = p.hw + p.eave;
        double slope = run / p.cos;
        double sin = Maths.sin(p.pitch);
        for (int side = -1; side <= 1; side += 2) {
            double[] up = p(-side * p.cos, sin, 0);
            double[] normal = p(side * sin, p.cos, 0);
            for (double s = 0.45; s < slope - 0.5; s += 1.15) {
                double x = side * (run - s * p.cos);
                double y = p.top(Math.abs(x));
                double[] base = Kit.add(p(x, y, 0), Kit.scale(normal, 0.045));
                kit.log(p(base[0], base[1], zr0 + 0.1), p(0, 0, zr1 - zr0 - 0.2), 0.05, 6, 0.2, BuildingSlot.TRIM,
                    BuildingSlot.TRIM);
                // stones on the upslope side of the pole, a row along it
                for (double z = zr0 + 0.35; z < zr1 - 0.35; z += 0.42 + 0.3 * rng.nextDouble()) {
                    double la = 0.22 + 0.16 * rng.nextDouble();
                    double lc = 0.18 + 0.14 * rng.nextDouble();
                    double lb = 0.1 + 0.08 * rng.nextDouble();
                    double turn = (rng.nextDouble() - 0.5) * 0.8;
                    double[] a = Kit.add(Kit.scale(up, la * Maths.cos(turn)), p(0, 0, la * Maths.sin(turn)));
                    double[] c = Kit.add(Kit.scale(up, -lc * Maths.sin(turn)), p(0, 0, lc * Maths.cos(turn)));
                    double[] b = Kit.scale(normal, lb);
                    double[] at = Kit.add(p(x, y, z), Kit.scale(up, 0.06));
                    double[] o = Kit.sub(Kit.sub(Kit.add(at, Kit.scale(normal, -0.03)), Kit.scale(c, 0.5)),
                        Kit.scale(a, 0.0));
                    kit.beam(o, a, b, c, BuildingSlot.STONE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- chimney, balconies, woodpile

    private void chimney() {
        if (p.chimney == null) {
            return;
        }
        double x = p.chimney[0];
        double z = p.chimney[1];
        double top = p.chimneyTop();
        double bottom = level <= 1 ? p.storeyY[1] - Plan.SLAB : p.eaves;
        BuildingSlot slot = p.plastered ? BuildingSlot.PLASTER : BuildingSlot.STONE;
        kit.box(slot, x - 0.3, bottom, z - 0.3, x + 0.3, top, z + 0.3);
        if (level <= 2) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    kit.box(BuildingSlot.STONE, x + sx * 0.3 - 0.04, top, z + sz * 0.3 - 0.04, x + sx * 0.3 + 0.04,
                        top + 0.25, z + sz * 0.3 + 0.04);
                }
            }
            kit.box(BuildingSlot.STONE, x - 0.42, top + 0.25, z - 0.42, x + 0.42, top + 0.33, z + 0.42);
        }
    }

    private void balconies() {
        if (level >= 3) {
            return;
        }
        for (int s : p.balconies) {
            double y = p.storeyY[s];
            double x0 = -p.hw + 0.3;
            double x1 = p.hw - 0.3;
            double z = p.z1;
            double depth = 1.05;
            double front = z + depth;
            double rail = 1.0;
            BuildingSlot wood = BuildingSlot.TRIM;
            kit.box(wood, x0, y - 0.09, z, x1, y - 0.01, front);
            if (level == 2) {
                kit.box(wood, x0, y - 0.01, front - 0.1, x1, y + rail, front);
                kit.box(wood, x0, y - 0.01, z, x0 + 0.1, y + rail, front - 0.1);
                kit.box(wood, x1 - 0.1, y - 0.01, z, x1, y + rail, front - 0.1);
                continue;
            }
            // brackets
            int brackets = Math.max(2, (int) Math.round((x1 - x0) / 1.6) + 1);
            for (int i = 0; i < brackets; i++) {
                double bx = x0 + 0.1 + (x1 - x0 - 0.2) * i / (brackets - 1);
                if (level == 0) {
                    int n = 8;
                    double[] t = new double[n + 1];
                    double[] lo = new double[n + 1];
                    double[] hi = new double[n + 1];
                    double len = depth + 0.1;
                    lo[0] = y - 0.36;
                    hi[0] = y - 0.09;
                    for (int k = 1; k <= n; k++) {
                        double u = (k - 1) / (double) (n - 1);
                        t[k] = len - 0.45 + 0.45 * u;
                        lo[k] = y - 0.36 + 0.2 * u * u;
                        hi[k] = y - 0.09;
                    }
                    kit.strip(p(bx - 0.07, 0, z - 0.1), p(0, 0, 1), p(0, 1, 0), p(1, 0, 0), t, lo, hi, 0.14, wood);
                } else {
                    kit.box(wood, bx - 0.07, y - 0.34, z - 0.1, bx + 0.07, y - 0.09, front - 0.05);
                }
            }
            // posts, rails
            int posts = Math.max(2, (int) Math.ceil((x1 - x0) / 2.4) + 1);
            for (int i = 0; i < posts; i++) {
                double px = x0 + 0.05 + (x1 - x0 - 0.1) * i / (posts - 1);
                kit.box(wood, px - 0.05, y - 0.01, front - 0.11, px + 0.05, y + rail, front - 0.01);
            }
            kit.box(wood, x0, y + rail - 0.1, front - 0.13, x1, y + rail, front);
            kit.box(wood, x0, y - 0.01, front - 0.1, x1, y + 0.07, front - 0.02);
            for (int sd = -1; sd <= 1; sd += 2) {
                double xs = sd < 0 ? x0 : x1 - 0.1;
                kit.box(wood, xs, y + rail - 0.1, z, xs + 0.1, y + rail, front - 0.13);
                kit.box(wood, xs, y - 0.01, z, xs + 0.1, y + 0.07, front - 0.1);
            }
            // the boards between the rails: cut-out silhouettes (level 0), or a plain panel
            double by0 = y + 0.07;
            double by1 = y + rail - 0.1;
            if (level == 0) {
                double bw = 0.16;
                int count = (int) Math.floor((x1 - x0 - 0.2) / bw);
                double start = (x0 + x1) / 2 - count * bw / 2;
                for (int i = 0; i < count; i++) {
                    board(p(start + i * bw, by0, front - 0.075), p(1, 0, 0), p(0, 0, 1), bw, by1 - by0);
                }
                for (int sd = -1; sd <= 1; sd += 2) {
                    double xs = sd < 0 ? x0 + 0.035 : x1 - 0.065;
                    int side = (int) Math.floor((depth - 0.2) / bw);
                    for (int i = 0; i < side; i++) {
                        board(p(xs, by0, z + 0.05 + i * bw), p(0, 0, 1), p(1, 0, 0), bw, by1 - by0);
                    }
                }
            } else {
                kit.box(wood, x0 + 0.1, by0, front - 0.075, x1 - 0.1, by1, front - 0.045);
                kit.box(wood, x0 + 0.035, by0, z, x0 + 0.065, by1, front - 0.13);
                kit.box(wood, x1 - 0.065, by0, z, x1 - 0.035, by1, front - 0.13);
            }
            // flower boxes on the front rail
            int boxes = p.w > 8 ? 3 : 2;
            for (int i = 0; i < boxes; i++) {
                double c = x0 + (x1 - x0) * (i + 0.5) / boxes;
                double half = Math.min(0.6, (x1 - x0) / boxes / 2 - 0.15);
                kit.box(wood, c - half, y + rail - 0.22, front, c + half, y + rail, front + 0.24);
                flowers(Face.FRONT, c - half + p.hw, c + half + p.hw, y + rail, -(front + 0.12 - p.z1), level);
            }
        }
    }

    /** A railing board with a cut-out silhouette on both edges (each shape completed by its neighbour). */
    private void board(double[] o, double[] across, double[] thickness, double width, double height) {
        double[][] shape = MOTIFS[p.motif];
        int n = shape.length + 2;
        double[] t = new double[n];
        double[] lo = new double[n];
        double[] hi = new double[n];
        t[0] = 0;
        t[n - 1] = height;
        for (int i = 0; i < shape.length; i++) {
            t[i + 1] = shape[i][0] * height;
            lo[i + 1] = shape[i][1];
            hi[i + 1] = width - shape[i][1];
        }
        hi[0] = width;
        hi[n - 1] = width;
        kit.strip(o, p(0, 1, 0), across, thickness, t, lo, hi, 0.03, BuildingSlot.TRIM);
    }

    /** Railing silhouettes: (height fraction, depth of the cut) along a board's edge. */
    private static final double[][][] MOTIFS = {
        // a tulip over a drop
        {{0.18, 0}, {0.24, 0.03}, {0.3, 0.045}, {0.36, 0.03}, {0.41, 0.012}, {0.47, 0.012}, {0.52, 0.04},
            {0.58, 0.058}, {0.65, 0.05}, {0.71, 0.02}, {0.76, 0.045}, {0.8, 0}},
        // a heart over a diamond
        {{0.2, 0}, {0.3, 0.05}, {0.38, 0}, {0.46, 0}, {0.52, 0.03}, {0.58, 0.06}, {0.64, 0.065}, {0.69, 0.05},
            {0.72, 0.035}, {0.75, 0.05}, {0.79, 0.04}, {0.83, 0}},
        // a chain of rounds
        {{0.22, 0}, {0.25, 0.035}, {0.3, 0.05}, {0.35, 0.035}, {0.38, 0}, {0.42, 0}, {0.45, 0.035}, {0.5, 0.05},
            {0.55, 0.035}, {0.58, 0}, {0.62, 0}, {0.65, 0.035}, {0.7, 0.05}, {0.75, 0.035}, {0.78, 0}}};

    /** Firewood stacked under the eaves against a side wall (or filling the woodshed). */
    private void woodpile() {
        if (p.woodpile == null || level >= 3 || p.kind == BuildingKind.WOODSHED) {
            return;
        }
        double side = p.woodpile[0];
        double za = p.woodpile[1];
        double zb = p.woodpile[2];
        double xa = side * (p.hw + 0.08);
        double xb = side * (p.hw + 0.58);
        double bottom = p.lowest(Math.min(xa, xb), za, Math.max(xa, xb), zb, true) - 0.08;
        double high = p.highest(Math.min(xa, xb), za, Math.max(xa, xb), zb);
        double top = Math.min(high + 1.75, p.storeyY[Math.min(1, p.floors)] + 0.45);
        if (top - Math.max(high, bottom) < 0.5) {
            return;
        }
        if (level >= 1) {
            kit.beam(p(Math.min(xa, xb), bottom, za), p(Math.abs(xb - xa), 0, 0), p(0, top - bottom, 0),
                p(0, 0, zb - za), BuildingSlot.TRIM, BuildingSlot.TRIM, BuildingSlot.FIREWOOD, BuildingSlot.FIREWOOD,
                BuildingSlot.FIREWOOD, BuildingSlot.FIREWOOD);
            return;
        }
        logs(xa, side, 0.5, za, zb, bottom, top, true);
    }

    /** Logs pointing along x from {@code xa} (direction {@code dir}), stacked between za and zb, bottom to top. */
    private void logs(double xa, double dir, double length, double za, double zb, double bottom, double top,
        boolean onGround) {
        double r = 0.07;
        int row = 0;
        for (double y = bottom + r; y + r <= top; y += 0.125, row++) {
            for (double z = za + r + (row % 2) * 0.07; z + r <= zb; z += 0.145) {
                double g = onGround ? p.ground(xa + dir * length / 2, z) : -1e9;
                if (y + r < g - 0.02) {
                    continue; // buried
                }
                double len = length * (0.92 + 0.1 * rng.nextDouble());
                double rad = r * (0.85 + 0.25 * rng.nextDouble());
                double jitter = (rng.nextDouble() - 0.5) * 0.05;
                kit.log(p(xa + dir * (jitter + 0.02), y, z), p(dir * len, 0, 0), rad, 6, rng.nextDouble(),
                    BuildingSlot.FIREWOOD, BuildingSlot.TRIM);
            }
        }
    }

    /** The farmhouse's Ruendi: an arched board closing the front gable's overhang under the roof. */
    private void ruendi() {
        if (!p.ruendi || level >= 3) {
            return;
        }
        double z = p.z1 + p.gable * 0.72;
        double half = p.hw + 0.05;
        int n = level == 0 ? 25 : 9;
        double[] t = new double[n];
        double[] lo = new double[n];
        double[] hi = new double[n];
        double base = p.eaves + 0.05;
        double apex = p.underside(0) - 0.6;
        for (int i = 0; i < n; i++) {
            double x = -half + 2 * half * i / (n - 1);
            double u = x / half;
            t[i] = x + half;
            lo[i] = base + (apex - base) * Math.sqrt(Math.max(0, 1 - u * u));
            hi[i] = p.underside(x) + 0.04;
        }
        kit.strip(p(-half, 0, z), p(1, 0, 0), p(0, 1, 0), p(0, 0, 1), t, lo, hi, 0.04, BuildingSlot.TRIM);
        // a profiled rim along the arch, standing out
        double[] rimLo = new double[n];
        double[] rimHi = new double[n];
        for (int i = 0; i < n; i++) {
            rimLo[i] = lo[i] - 0.03;
            rimHi[i] = Math.min(lo[i] + 0.11, hi[i] - 0.01);
        }
        kit.strip(p(-half, 0, z + 0.04), p(1, 0, 0), p(0, 1, 0), p(0, 0, 1), t, rimLo, rimHi, 0.05,
            BuildingSlot.SHUTTERS);
    }

    // ---------------------------------------------------------------- steps, cellar doors

    /**
     * Steps from each door down to the ground: a landing, then treads until the ground is reached, straight out when
     * a few do, else along the wall toward the higher ground (as the stone stairs of houses on slopes run).
     */
    private void steps() {
        if (level >= 3) {
            return;
        }
        double embed = p.options.embed();
        for (int d = 0; d < p.doors.size(); d++) {
            Plan.Door door = p.doors.get(d);
            int stair = p.stairs.get(d);
            double[] n = p(door.nx(), 0, door.nz());
            double[] a = p(door.nz(), 0, -door.nx());
            double half = door.width() / 2 + 0.25;
            double landing = stair == 0 ? 0.7 : 1.15;
            if (highest(door, n, a, -half, half, 0, landing) >= -0.03) {
                continue;
            }
            stepBox(door, n, a, -half, half, -0.05, landing, 0, embed);
            for (int i = 0; i < 40; i++) {
                double topY = -RISE * (i + 1);
                double u0;
                double u1;
                double v0;
                double v1;
                if (stair == 0) {
                    u0 = -half;
                    u1 = half;
                    v0 = landing + TREAD * i;
                    v1 = v0 + TREAD;
                } else {
                    double from = half + TREAD * i;
                    u0 = stair > 0 ? from : -from - TREAD;
                    u1 = u0 + TREAD;
                    v0 = 0.05;
                    v1 = landing;
                }
                if (topY <= lowest(door, n, a, u0, u1, v0, v1) + 0.03) {
                    break;
                }
                stepBox(door, n, a, u0, u1, v0, v1, topY, embed);
            }
        }
    }

    /** The highest ground under a box along the door's wall from u0 to u1 and out from v0 to v1. */
    private double highest(Plan.Door door, double[] n, double[] a, double u0, double u1, double v0, double v1) {
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 4; i++) {
            for (int j = 0; j <= 2; j++) {
                double u = u0 + (u1 - u0) * i / 4;
                double v = v0 + (v1 - v0) * j / 2;
                best = Math.max(best, p.ground(door.x() + a[0] * u + n[0] * v, door.z() + a[2] * u + n[2] * v));
            }
        }
        return best;
    }

    /** The lowest ground under such a box. */
    private double lowest(Plan.Door door, double[] n, double[] a, double u0, double u1, double v0, double v1) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= 4; i++) {
            for (int j = 0; j <= 2; j++) {
                double u = u0 + (u1 - u0) * i / 4;
                double v = v0 + (v1 - v0) * j / 2;
                best = Math.min(best, p.ground(door.x() + a[0] * u + n[0] * v, door.z() + a[2] * u + n[2] * v));
            }
        }
        return best;
    }

    private void stepBox(Plan.Door door, double[] n, double[] a, double u0, double u1, double v0, double v1,
        double topY, double embed) {
        double low = lowest(door, n, a, u0, u1, v0, v1) - embed;
        double[] o = p(door.x() + a[0] * u0 + n[0] * v0, low, door.z() + a[2] * u0 + n[2] * v0);
        kit.beam(o, Kit.scale(a, u1 - u0), p(0, topY - low, 0), Kit.scale(n, v1 - v0), BuildingSlot.STONE);
    }

    /** Doors into the foundation where it stands tall (its anchors), closed, with a lintel. */
    private void cellarDoors() {
        if (level >= 3) {
            return;
        }
        for (Anchor anchor : p.anchors) {
            if (anchor.kind() != Anchor.Kind.CELLAR_DOOR) {
                continue;
            }
            double[] n = p(Maths.sin(anchor.yaw()), 0, Maths.cos(anchor.yaw()));
            double[] a = p(n[2], 0, -n[0]);
            double[] c = p(anchor.position().x() - n[0] * 0.14, anchor.position().y(),
                anchor.position().z() - n[2] * 0.14);
            double[] o = Kit.add(Kit.sub(c, Kit.scale(a, 0.5)), Kit.scale(n, 0.09));
            kit.beam(o, Kit.scale(a, 1.0), p(0, 1.85, 0), Kit.scale(n, 0.05), BuildingSlot.DOOR);
            double[] l = Kit.add(Kit.sub(c, Kit.scale(a, 0.65)), Kit.add(Kit.scale(n, 0.08), p(0, 1.85, 0)));
            kit.beam(l, Kit.scale(a, 1.3), p(0, 0.16, 0), Kit.scale(n, 0.08), BuildingSlot.TRIM);
        }
    }

    // ---------------------------------------------------------------- the church

    private void tower() {
        double size = p.towerSize;
        double tw = Plan.TOWER_WALL;
        double topY = p.towerTop;
        for (Face f : Face.values()) {
            double[] start = p.towerStart(f);
            double[] along = Plan.along(f);
            double from = f.side() ? tw : 0;
            double len = size - 2 * from;
            double[] o = p(start[0] + along[0] * from, 0, start[2] + along[2] * from);
            List<double[]> holes = new ArrayList<>();
            List<Hole> planned = p.towerHoles.get(f.ordinal());
            if (level <= 1) {
                for (Hole h : planned) {
                    holes.add(new double[] {h.x() - from, h.y(), h.w(), h.h()});
                }
            }
            kit.wall(o, along, Plan.inward(f), len, topY, tw, holes, BuildingSlot.PLASTER, BuildingSlot.INTERIOR,
                BuildingSlot.PLASTER, BuildingSlot.PLASTER, false);
            for (Hole h : planned) {
                double[] a0 = Kit.add(start, Kit.scale(along, h.x()));
                double[] a1 = Kit.add(start, Kit.scale(along, h.x() + h.w()));
                towerDress(f, h, a0, a1, along);
            }
        }
        double ts = size / 2;
        double tz0 = p.hd - size;
        kit.box(BuildingSlot.STONE, -ts - 0.25, topY - 0.05, tz0 - 0.25, ts + 0.25, topY + 0.3, p.hd + 0.25);
        double radius = (ts + 0.25) / Maths.cos(Math.PI / 8);
        double base = topY + 0.3;
        double apex = base + 2.3 * size + 2;
        kit.spire(0, p.hd - ts, base, apex, radius, 8, Math.PI / 8, cover, BuildingSlot.STONE);
        if (level <= 2) {
            kit.box(BuildingSlot.METAL, -0.05, apex - 0.4, p.hd - ts - 0.05, 0.05, apex + 1.7, p.hd - ts + 0.05);
            kit.box(BuildingSlot.METAL, -0.45, apex + 1.0, p.hd - ts - 0.05, 0.45, apex + 1.1, p.hd - ts + 0.05);
            if (level <= 1) {
                kit.blob(p(0, apex + 0.05, p.hd - ts), 0.16, 0.16, 0.16, BuildingSlot.METAL);
            }
            // the clocks
            double cy = topY - 4.0;
            for (Face f : Face.values()) {
                double[] n = p(f.nx, 0, f.nz);
                double[] c = p(f.nx * ts, cy, p.hd - ts + f.nz * ts);
                kit.log(c, Kit.scale(n, 0.06), 0.72, level == 0 ? 20 : 10, 0, BuildingSlot.METAL,
                    BuildingSlot.PLASTER);
                if (level <= 1) {
                    double[] a = Plan.along(f);
                    double[] face = Kit.add(c, Kit.scale(n, 0.06));
                    kit.beam(Kit.add(face, Kit.scale(a, -0.03)), Kit.scale(a, 0.06), p(0, 0.45, 0),
                        Kit.scale(n, 0.02), BuildingSlot.METAL);
                    kit.beam(Kit.add(face, p(0, -0.03, 0)), Kit.scale(a, 0.55), p(0, 0.06, 0), Kit.scale(n, 0.03),
                        BuildingSlot.METAL);
                }
            }
        }
    }

    /** What fills the tower's openings at this level. */
    private void towerDress(Face f, Hole h, double[] a0, double[] a1, double[] along) {
        double[] in = Plan.inward(f);
        double[] o0 = p(a0[0], h.y(), a0[2]);
        double width = h.w();
        if (level >= 2) {
            if (h.kind() != Opening.Kind.DOOR) {
                kit.beam(Kit.add(o0, Kit.scale(in, -0.012)), Kit.scale(along, width), p(0, h.h(), 0),
                    Kit.scale(in, 0.016), BuildingSlot.WINDOW);
            } else {
                kit.beam(Kit.add(o0, Kit.scale(in, -0.03)), Kit.scale(along, width), p(0, h.h(), 0),
                    Kit.scale(in, 0.034), BuildingSlot.DOOR);
            }
            return;
        }
        if (h.kind() == Opening.Kind.BELFRY) {
            for (int i = 0; i < 6; i++) {
                double y = h.y() + 0.15 + (h.h() - 0.3) * i / 5;
                double[] o = Kit.add(p(a0[0], y - 0.1, a0[2]), Kit.scale(in, Plan.TOWER_WALL * 0.35));
                kit.beam(o, Kit.scale(along, width), p(0, 0.035, 0), Kit.add(Kit.scale(in, 0.22), p(0, 0.16, 0)),
                    BuildingSlot.TRIM);
            }
        }
        if (h.kind() == Opening.Kind.DOOR && f == Face.FRONT) {
            // a stone surround
            double[] out = Kit.scale(in, -0.035);
            kit.beam(Kit.add(Kit.add(o0, Kit.scale(along, -0.2)), out), Kit.scale(along, 0.2), p(0, h.h() + 0.2, 0),
                Kit.scale(in, 0.045), BuildingSlot.STONE);
            kit.beam(Kit.add(Kit.add(o0, Kit.scale(along, width)), out), Kit.scale(along, 0.2), p(0, h.h() + 0.2, 0),
                Kit.scale(in, 0.045), BuildingSlot.STONE);
            kit.beam(Kit.add(Kit.add(o0, p(0, h.h(), 0)), out), Kit.scale(along, width), p(0, 0.2, 0),
                Kit.scale(in, 0.045), BuildingSlot.STONE);
        }
    }

    /** Dressed stones at the church's corners, long and short in turn. */
    private void quoins() {
        if (level >= 2) {
            return;
        }
        double ts = p.towerSize / 2;
        quoinColumn(p.hw, p.z0, 1, -1, p.eaves);
        quoinColumn(-p.hw, p.z0, -1, -1, p.eaves);
        quoinColumn(p.hw, p.z1, 1, 1, p.eaves);
        quoinColumn(-p.hw, p.z1, -1, 1, p.eaves);
        quoinColumn(ts, p.hd, 1, 1, p.towerTop);
        quoinColumn(-ts, p.hd, -1, 1, p.towerTop);
        quoinColumn(ts, p.hd - p.towerSize, 1, -1, p.towerTop);
        quoinColumn(-ts, p.hd - p.towerSize, -1, -1, p.towerTop);
    }

    private void quoinColumn(double x, double z, int sx, int sz, double height) {
        double h = 0.32;
        int n = (int) Math.floor(height / h);
        for (int k = 0; k < n; k++) {
            double y0 = k * h + 0.01;
            double y1 = (k + 1) * h - 0.01;
            double lx = k % 2 == 0 ? 0.55 : 0.3;
            double lz = k % 2 == 0 ? 0.3 : 0.55;
            double xa = x + sx * 0.025;
            double xb = x - sx * lx;
            double za = z + sz * 0.025;
            double zb = z - sz * lz;
            kit.box(BuildingSlot.STONE, Math.min(xa, xb), y0, Math.min(za, zb), Math.max(xa, xb), y1, Math.max(za, zb));
        }
    }

    // ---------------------------------------------------------------- the inn, the woodshed

    /** The inn's hanging sign on its iron bracket, and the board for its name over the door. */
    private void sign() {
        if (level >= 2) {
            return;
        }
        Anchor sign = null;
        Anchor name = null;
        for (Anchor a : p.anchors) {
            if (a.kind() == Anchor.Kind.SIGN) {
                sign = a;
            } else if (a.kind() == Anchor.Kind.NAME_BOARD) {
                name = a;
            }
        }
        if (sign != null) {
            double x = sign.position().x();
            double y = sign.position().y();
            double z = sign.position().z();
            kit.box(BuildingSlot.METAL, x - 0.025, y + 0.42, p.z1 - 0.02, x + 0.025, y + 0.47, z + 0.45);
            kit.beam(p(x - 0.02, y - 0.05, p.z1 - 0.02), p(0, 0.47, z - p.z1), p(0.04, 0, 0),
                Kit.scale(Kit.normalize(p(0, z - p.z1, -0.47)), 0.04), BuildingSlot.METAL);
            kit.box(BuildingSlot.TRIM, x - 0.02, y - 0.3, z - 0.38, x + 0.02, y + 0.38, z + 0.38);
        }
        if (name != null) {
            double half = p.w * 0.3;
            kit.box(BuildingSlot.TRIM, -half, name.position().y() - 0.2, p.z1 - 0.01, half, name.position().y() + 0.2,
                p.z1 + 0.035);
        }
    }

    /** The woodshed: a floor, posts, plates, boards with gaps on three sides, boarded gables, its logs. */
    private void woodshed() {
        double e = p.eaves;
        double hw = p.hw;
        BuildingSlot wood = BuildingSlot.TRIM;
        kit.beam(p(-hw, -0.25, p.z0), p(p.w, 0, 0), p(0, 0.25, 0), p(0, 0, p.d), BuildingSlot.STONE,
            BuildingSlot.STONE, BuildingSlot.STONE, BuildingSlot.FLOOR, BuildingSlot.STONE, BuildingSlot.STONE);
        // posts and plates
        int along = Math.max(1, (int) Math.ceil(p.d / 1.8));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int i = 0; i <= along; i++) {
                double z = p.z0 + 0.07 + (p.d - 0.14) * i / along;
                kit.box(wood, sx * hw - 0.07, 0, z - 0.07, sx * hw + 0.07, e, z + 0.07);
            }
            kit.box(wood, Math.min(sx * hw - 0.08, sx * hw + 0.08), e - 0.02, p.z0, Math.max(sx * hw - 0.08,
                sx * hw + 0.08), e + Plan.PLATE + 0.16 * p.tan, p.z1);
        }
        for (double z : new double[] {p.z0, p.z1}) {
            kit.box(wood, -hw, e - 0.18, z - 0.07, hw, e, z + 0.07);
        }
        // boards with gaps: the back and the sides; the gables boarded
        double bw = 0.16;
        double gap = 0.03;
        if (level <= 1) {
            for (double x = -hw - 0.08; x + bw <= hw + 0.08 + 1e-9; x += bw + gap) {
                kit.box(BuildingSlot.TIMBER, x, -0.1, p.z0 - 0.1, x + bw, e - 0.02, p.z0 - 0.075);
            }
            for (int sx = -1; sx <= 1; sx += 2) {
                for (double z = p.z0 - 0.075; z + bw <= p.z1 - 0.25; z += bw + gap) {
                    double xo = sx * (hw + 0.1);
                    double xi = sx * (hw + 0.075);
                    kit.box(BuildingSlot.TIMBER, Math.min(xo, xi), -0.1, z, Math.max(xo, xi), e - 0.02, z + bw);
                }
            }
            for (double zf : new double[] {p.z1 + 0.07, p.z0 - 0.1}) {
                for (double x = -hw; x + bw <= hw + 1e-9; x += bw + 0.01) {
                    double[] t = {0, bw};
                    double[] lo = {e, e};
                    double[] hi = {p.underside(x) + 0.02, p.underside(x + bw) + 0.02};
                    if (x < 0 && x + bw > 0) {
                        t = new double[] {0, -x, bw};
                        lo = new double[] {e, e, e};
                        hi = new double[] {p.underside(x) + 0.02, p.underside(0) + 0.02, p.underside(x + bw) + 0.02};
                    }
                    kit.strip(p(x, 0, zf), p(1, 0, 0), p(0, 1, 0), p(0, 0, 1), t, lo, hi, 0.025, BuildingSlot.TIMBER);
                }
            }
        } else {
            kit.box(BuildingSlot.TIMBER, -hw - 0.1, -0.1, p.z0 - 0.1, hw + 0.1, e, p.z0 - 0.075);
            for (int sx = -1; sx <= 1; sx += 2) {
                double xo = sx * (hw + 0.1);
                double xi = sx * (hw + 0.075);
                kit.box(BuildingSlot.TIMBER, Math.min(xo, xi), -0.1, p.z0 - 0.075, Math.max(xo, xi), e, p.z1 - 0.25);
            }
            for (double zf : new double[] {p.z1 + 0.07, p.z0 - 0.1}) {
                kit.strip(p(-hw, 0, zf), p(1, 0, 0), p(0, 1, 0), p(0, 0, 1), new double[] {0, hw, p.w},
                    new double[] {e, e, e}, new double[] {e + Plan.PLATE, p.underside(0), e + Plan.PLATE}, 0.025,
                    BuildingSlot.TIMBER);
            }
        }
        // the logs: a stack at the front whose ends show, a block behind it
        if (level <= 2) {
            double x0 = -hw + 0.12;
            double x1 = hw - 0.12;
            double top = e - 0.45;
            double zf = p.z1 - 0.35;
            if (level == 0) {
                for (double x = x0; x < x1 - 0.1; x += 0.0) {
                    double next = Math.min(x1, x + 1.2);
                    logsAlongZ(x, next, zf - 0.5, 0.02, top);
                    x = next;
                }
                kit.beam(p(x0, 0.0, p.z0 + 0.1), p(x1 - x0, 0, 0), p(0, top - 0.05, 0), p(0, 0, zf - 0.55 - p.z0
                    - 0.1), BuildingSlot.FIREWOOD, BuildingSlot.FIREWOOD, BuildingSlot.FIREWOOD,
                    BuildingSlot.FIREWOOD, BuildingSlot.TRIM, BuildingSlot.TRIM);
            } else {
                kit.beam(p(x0, 0.0, p.z0 + 0.1), p(x1 - x0, 0, 0), p(0, top, 0), p(0, 0, zf - p.z0 - 0.1),
                    BuildingSlot.FIREWOOD, BuildingSlot.FIREWOOD, BuildingSlot.FIREWOOD, BuildingSlot.FIREWOOD,
                    BuildingSlot.TRIM, BuildingSlot.TRIM);
            }
        }
    }

    /** Logs pointing along z from {@code z0}, stacked between x0 and x1 from y0 to y1. */
    private void logsAlongZ(double x0, double x1, double z0, double y0, double y1) {
        double r = 0.07;
        int row = 0;
        for (double y = y0 + r; y + r <= y1; y += 0.125, row++) {
            for (double x = x0 + r + (row % 2) * 0.07; x + r <= x1; x += 0.145) {
                double len = 0.5 * (0.92 + 0.1 * rng.nextDouble());
                kit.log(p(x, y, z0 + (rng.nextDouble() - 0.5) * 0.04), p(0, 0, len), r * (0.85 + 0.25
                    * rng.nextDouble()), 6, rng.nextDouble(), BuildingSlot.FIREWOOD, BuildingSlot.TRIM);
            }
        }
    }
}
