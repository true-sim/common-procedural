package com.truesim.common.procedural.fence;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.fence.FencePiece.Role;
import com.truesim.common.procedural.fence.Piece.End;
import com.truesim.common.procedural.fence.Piece.Section;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays a fence out along its line over the ground: where every post, rail, picket, board, stake, pole, rod, strand and
 * stone goes, as {@link Piece}s in the ground's frame. Everything stands on the ground where it is: a post's foot
 * sinks {@link FenceTuning#sink()} below the lowest ground around it, its height counts from the ground at its centre;
 * rails, boards and wire run from post to post at their heights above each post's ground, so they follow the slope, and
 * are lifted where the ground between rises toward them; pickets, rods and stones take the ground at their own place.
 * The line is cut into runs by gates; each run is laid by its kind, each gate gets its posts and a braced leaf.
 */
@AllowAllocation("generation, at load time")
final class FenceLayout {

    private static final double RING = 0.12;

    final List<Piece> pieces = new ArrayList<>();
    private final FenceType type;
    private final FenceOptions options;
    private final FenceTuning tuning;
    private final FencePath line;
    private final FenceGround ground;
    private final long seed;
    private final double jitter;
    private Rng rng;

    /** A post as the layout knows it: its foot on the ground and its axis. */
    static final class Post {
        double s;
        double x;
        double y;
        double z;
        double dx;
        double dy = 1;
        double dz;
        double radius;
        double height;

        /** The point of its axis {@code h} above its ground. */
        double ax(double h) {
            return x + dx * h / dy;
        }

        double ay(double h) {
            return y + h;
        }

        double az(double h) {
            return z + dz * h / dy;
        }
    }

    FenceLayout(FenceType type, FenceOptions options, FenceTuning tuning, FencePath line, FenceGround ground,
        long seed) {
        this.type = type;
        this.options = options;
        this.tuning = tuning;
        this.line = line;
        this.ground = ground;
        this.seed = seed;
        this.jitter = options.jitter();
    }

    void build() {
        double length = line.length();
        double gateWidth = options.gateWidth();
        double gatePost = Math.max(type.postRadius(), 0.065) * 1.3;
        boolean postKind = switch (type.kind()) {
            case POST_AND_RAIL, PICKET, BOARD, WIRE -> true;
            default -> false;
        };
        // the gates, in order, inside the line, apart
        List<Double> gates = new ArrayList<>();
        for (double at : options.gates().stream().sorted().toList()) {
            double half = 0.5 * gateWidth + 2 * gatePost + 0.3;
            if (at - half < 0 || at + half > length || (!gates.isEmpty() && at - gates.getLast() < 2 * half)) {
                continue;
            }
            gates.add(at);
        }
        double start = 0;
        int run = 0;
        for (int g = 0; g <= gates.size(); g++) {
            double gateA = g < gates.size() ? gates.get(g) - 0.5 * gateWidth - gatePost : length;
            double end = g < gates.size() && !postKind ? gateA - gatePost - 0.06 : gateA;
            boolean gateBefore = g > 0;
            boolean gateAfter = g < gates.size();
            rng = Rng.seeded(Seeds.derive(seed, run++));
            if (end - start > 0.3) {
                run(start, end, gateBefore && postKind, gateAfter && postKind);
            }
            if (g < gates.size()) {
                rng = Rng.seeded(Seeds.derive(seed, 1000 + g));
                double gateB = gates.get(g) + 0.5 * gateWidth + gatePost;
                gate(gateA, gateB, gatePost);
                start = postKind ? gateB : gateB + gatePost + 0.06;
            }
        }
    }

    private void run(double a, double b, boolean gateAtStart, boolean gateAtEnd) {
        switch (type.kind()) {
            case POST_AND_RAIL -> postAndRail(a, b, gateAtStart, gateAtEnd);
            case PICKET -> picket(a, b, gateAtStart, gateAtEnd);
            case BOARD -> board(a, b, gateAtStart, gateAtEnd);
            case WIRE -> wire(a, b, gateAtStart, gateAtEnd);
            case WATTLE -> wattle(a, b);
            case SPLIT_RAIL -> splitRail(a, b);
            case STANGENZAUN -> stangenzaun(a, b);
            case DRY_STONE -> wall(a, b);
            default -> throw new IllegalStateException();
        }
    }

    // ---------------------------------------------------------------- shared

    private double gauss() {
        return rng.nextGaussian();
    }

    private double chance() {
        return rng.nextDouble();
    }

    /** Distances along the line for posts from a to b about {@code spacing} apart: at both ends and every corner. */
    private double[] stations(double a, double b, double spacing, double vary) {
        List<Double> anchors = new ArrayList<>();
        anchors.add(a);
        for (int i = 1; i < line.count() - 1; i++) {
            double s = line.arc(i);
            if (s > a + 0.3 && s < b - 0.3 && line.turn(i) > 0.015) {
                anchors.add(s);
            }
        }
        anchors.add(b);
        List<Double> out = new ArrayList<>();
        for (int k = 0; k + 1 < anchors.size(); k++) {
            double s0 = anchors.get(k);
            double s1 = anchors.get(k + 1);
            int bays = Math.max(1, (int) Math.round((s1 - s0) / spacing));
            double bay = (s1 - s0) / bays;
            out.add(s0);
            for (int i = 1; i < bays; i++) {
                out.add(s0 + bay * i + jitter * vary * bay * (chance() - 0.5));
            }
        }
        out.add(b);
        double[] result = new double[out.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    /** The lowest ground around a foot of {@code radius} at (x, z). */
    private double lowest(double x, double z, double radius) {
        double low = ground.height(x, z);
        for (int k = 0; k < 8; k++) {
            double a = Maths.TAU * k / 8;
            low = Math.min(low, ground.height(x + radius * Maths.cos(a), z + radius * Maths.sin(a)));
        }
        return low;
    }

    /**
     * A post (or stake) at (x, z): {@code height} above the ground at its centre, leaning by {@code lean} radians
     * toward {@code (lx, lz)}, its foot {@code sink} below the lowest ground around it.
     */
    private Post post(double s, double x, double z, double radius, double height, double lean, double lx, double lz,
        boolean square, Role role, End top, double sideX, double sideZ) {
        Post post = new Post();
        post.s = s;
        post.x = x;
        post.z = z;
        post.y = ground.height(x, z);
        post.radius = radius;
        post.height = height;
        double ll = Math.sqrt(lx * lx + lz * lz);
        double sin = Maths.sin(lean);
        post.dx = ll > 0 ? sin * lx / ll : 0;
        post.dz = ll > 0 ? sin * lz / ll : 0;
        post.dy = Maths.cos(lean);
        double depth = post.y - lowest(x, z, radius * 1.3) + tuning.sink() + radius * Math.abs(sin);
        double[] path = Piece.line(post.ax(-depth), post.ay(-depth), post.az(-depth), post.ax(height),
            post.ay(height), post.az(height), RING, 2);
        Piece piece = new Piece(role, options.wood()).beam(square ? Section.RECT : Section.ROUND, radius, radius, path)
            .up(sideX, 0, sideZ).ends(End.OPEN, top);
        piece.roll = rng.nextDouble() * Maths.TAU;
        piece.tip = top == End.JAGGED ? radius * 1.2 : radius * 0.7;
        piece.jagN = 0.6 * (chance() - 0.5);
        piece.jagB = 0.6 * (chance() - 0.5);
        look(piece);
        pieces.add(piece);
        return post;
    }

    /** A post at distance {@code s} along the line, leaning as a hand-set post does (more when damaged). */
    private Post linePost(double s, double radius, double height, boolean square, Role role, End top) {
        double x = line.x(s);
        double z = line.z(s);
        double sideX = -line.dz(s);
        double sideZ = line.dx(s);
        double lean = Maths.toRadians(jitter * 1.3 * Math.abs(gauss()));
        if (chance() < 0.5 * options.damage()) {
            lean += Maths.toRadians(2 + 6 * chance());
        }
        // mostly across the fence: pushed by cattle and snow
        double across = chance() < 0.5 ? -1 : 1;
        double turn = 0.6 * (chance() - 0.5);
        double lx = across * sideX * Maths.cos(turn) - across * sideZ * Maths.sin(turn);
        double lz = across * sideZ * Maths.cos(turn) + across * sideX * Maths.sin(turn);
        return post(s, x, z, radius, height * (1 + jitter * 0.03 * gauss()), lean, lx, lz, square, role, top, sideX,
            sideZ);
    }

    /** A piece's look: where its texture starts, its shade, how much more or less weathered than the fence. */
    private void look(Piece piece) {
        piece.v0 = rng.nextDouble();
        piece.tone = Math.clamp(0.6 * gauss(), -1, 1);
        piece.wear = 0.12 * gauss();
    }

    /**
     * A beam from a to b, bowing down {@code sag} at its middle, lifted to {@code clearance} above the ground under
     * it, in points at most {@code step} apart.
     */
    private double[] span(double ax, double ay, double az, double bx, double by, double bz, double sag,
        double clearance, double step) {
        double[] path = Piece.line(ax, ay, az, bx, by, bz, step, 2);
        int n = path.length / 3;
        for (int i = 1; i < n - 1; i++) {
            double t = i / (double) (n - 1);
            path[3 * i + 1] -= 4 * sag * t * (1 - t);
            double floor = ground.height(path[3 * i], path[3 * i + 2]) + clearance;
            path[3 * i + 1] = Math.max(path[3 * i + 1], floor);
        }
        return path;
    }

    /** The highest the ground rises above the straight line a-b (0 if it stays below), sampled along it. */
    private double rise(double ax, double ay, double az, double bx, double by, double bz) {
        double l = Math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
        int n = Math.max(2, (int) Math.ceil(l / tuning.groundStep()));
        double worst = 0;
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            worst = Math.max(worst, ground.height(ax + t * (bx - ax), az + t * (bz - az)) - (ay + t * (by - ay)));
        }
        return worst;
    }

    /** Posts for a run: gate posts at an end where a gate is, heavier and taller. */
    private Post[] posts(double[] at, boolean gateAtStart, boolean gateAtEnd, End top) {
        Post[] posts = new Post[at.length];
        for (int i = 0; i < at.length; i++) {
            boolean gate = (i == 0 && gateAtStart) || (i == at.length - 1 && gateAtEnd);
            double radius = gate ? Math.max(type.postRadius(), 0.065) * 1.3 : type.postRadius();
            double height = type.height() + (gate ? 0.12 : 0.04);
            posts[i] = linePost(at[i], radius * (1 + 0.06 * jitter * gauss()), height, type.squarePosts(), Role.POST,
                top);
        }
        return posts;
    }

    // ---------------------------------------------------------------- post and rail

    private void postAndRail(double a, double b, boolean gateAtStart, boolean gateAtEnd) {
        Post[] posts = posts(stations(a, b, type.spacing(), 0.25), gateAtStart, gateAtEnd, End.POINT);
        int rails = Math.max(1, type.rails());
        for (int i = 0; i + 1 < posts.length; i++) {
            Post p = posts[i];
            Post q = posts[i + 1];
            for (int k = 0; k < rails; k++) {
                double f = rails == 1 ? 0.88 : 0.33 + 0.55 * k / (rails - 1);
                double h = f * type.height() + 0.02 * jitter * gauss();
                double radius = type.railRadius() * (1 + 0.1 * jitter * gauss());
                double roll = chance();
                if (roll < 0.35 * options.damage()) {
                    continue; // missing
                }
                Piece rail = new Piece(Role.RAIL, options.wood()).ends(End.OPEN, End.OPEN);
                rail.roll = rng.nextDouble() * Maths.TAU;
                look(rail);
                if (roll < 0.7 * options.damage()) {
                    broken(rail, p, q, h, radius);
                } else {
                    double span = Math.sqrt((q.x - p.x) * (q.x - p.x) + (q.z - p.z) * (q.z - p.z));
                    double sag = type.sag() * span * (1 + jitter * 0.6 * gauss());
                    rail.beam(Section.ROUND, radius, radius, span(p.ax(h), p.ay(h), p.az(h), q.ax(h), q.ay(h),
                        q.az(h), Math.max(0, sag), radius + 0.03, 0.3));
                }
                pieces.add(rail);
            }
        }
    }

    /** A rail broken off one post: it hangs from the other to the ground. */
    private void broken(Piece rail, Post p, Post q, double h, double radius) {
        boolean fromP = chance() < 0.5;
        Post held = fromP ? p : q;
        Post lost = fromP ? q : p;
        double t = 0.45 + 0.35 * chance();
        double fx = held.x + t * (lost.x - held.x);
        double fz = held.z + t * (lost.z - held.z);
        double fy = ground.height(fx, fz) + 0.8 * radius;
        rail.beam(Section.ROUND, radius, radius, Piece.line(held.ax(h), held.ay(h), held.az(h), fx, fy, fz, 0.3, 2));
        rail.end = End.JAGGED;
        rail.tip = 3 * radius;
        rail.jagN = chance() - 0.5;
        rail.jagB = chance() - 0.5;
    }

    // ---------------------------------------------------------------- pickets

    private void picket(double a, double b, boolean gateAtStart, boolean gateAtEnd) {
        Post[] posts = posts(stations(a, b, type.spacing(), 0.12), gateAtStart, gateAtEnd, End.FLAT);
        double railHalf = type.railRadius();
        double railThick = 0.017;
        double picketThick = 0.011;
        double width = type.pieceWidth();
        double pitch = width + type.pieceGap();
        for (int i = 0; i + 1 < posts.length; i++) {
            Post p = posts[i];
            Post q = posts[i + 1];
            double ex = q.x - p.x;
            double ez = q.z - p.z;
            double span = Math.sqrt(ex * ex + ez * ez);
            double sideX = -ez / span;
            double sideZ = ex / span;
            for (double f : new double[] {0.22, 0.78}) {
                double h = f * type.height();
                Piece rail = new Piece(Role.RAIL, options.wood()).beam(Section.RECT, railHalf, railThick,
                    span(p.ax(h), p.ay(h), p.az(h), q.ax(h), q.ay(h), q.az(h), type.sag() * span, railHalf + 0.04,
                        0.4)).ends(End.OPEN, End.OPEN);
                look(rail);
                pieces.add(rail);
            }
            // pickets on the rails' front, each on the ground where it stands
            double clear = p.radius + 0.5 * width + 0.012;
            int count = (int) Math.floor((span - 2 * clear) / pitch) + 1;
            if (count < 1) {
                continue;
            }
            double first = 0.5 * (span - (count - 1) * pitch);
            double offset = railThick + picketThick;
            for (int k = 0; k < count; k++) {
                double t = (first + k * pitch) / span;
                double x = p.x + t * ex + sideX * offset;
                double z = p.z + t * ez + sideZ * offset;
                double bottom = ground.height(x, z) + 0.04;
                double top = p.y + t * (q.y - p.y) + type.height() + 0.01 * jitter * gauss();
                double roll = chance();
                if (roll < 0.3 * options.damage() || top - bottom < 0.3) {
                    continue;
                }
                boolean broken = roll < 0.6 * options.damage();
                if (broken) {
                    top = bottom + (top - bottom) * (0.45 + 0.3 * chance());
                }
                Piece picket = new Piece(Role.PICKET, options.wood()).beam(Section.RECT, picketThick, 0.5 * width,
                    Piece.line(x, bottom, z, x, top, z, 0.3, 2)).up(sideX, 0, sideZ)
                    .ends(End.FLAT, broken ? End.JAGGED : End.POINT);
                picket.tip = broken ? 0.06 : 0.55 * width;
                picket.jagB = broken ? chance() - 0.5 : 0;
                look(picket);
                pieces.add(picket);
            }
        }
    }

    // ---------------------------------------------------------------- boards

    private void board(double a, double b, boolean gateAtStart, boolean gateAtEnd) {
        Post[] posts = posts(stations(a, b, type.spacing(), 0.12), gateAtStart, gateAtEnd, End.FLAT);
        double half = type.railRadius();
        double thick = 0.011;
        double pitch = 2 * half + type.pieceGap();
        int count = type.rails() > 0 ? type.rails()
            : Math.max(1, (int) Math.floor((type.height() - 0.1 + type.pieceGap()) / pitch));
        for (int i = 0; i + 1 < posts.length; i++) {
            Post p = posts[i];
            Post q = posts[i + 1];
            double ex = q.x - p.x;
            double ez = q.z - p.z;
            double span = Math.sqrt(ex * ex + ez * ez);
            double offset = Math.max(p.radius, q.radius) + thick;
            double sx = -ez / span * offset;
            double sz = ex / span * offset;
            // the lowest board clears the ground between the posts: the bay's boards rise together
            double base = 0.1 + half;
            double lift = Math.max(0, rise(p.ax(base), p.ay(base), p.az(base), q.ax(base), q.ay(base), q.az(base))
                + half + 0.03);
            for (int k = 0; k < count; k++) {
                double h = base + lift + k * pitch;
                if (h + half > type.height() + 0.04 + lift) {
                    break;
                }
                double roll = chance();
                if (roll < 0.25 * options.damage()) {
                    continue;
                }
                double ax = p.ax(h) + sx;
                double ay = p.ay(h);
                double az = p.az(h) + sz;
                double bx = q.ax(h) + sx;
                double by = q.ay(h);
                double bz = q.az(h) + sz;
                if (roll < 0.45 * options.damage()) {
                    // hanging by one nail: its far end dropped toward the ground
                    double floor = ground.height(bx, bz) + half + 0.02;
                    by = Math.max(floor, by - (0.3 + 0.5 * chance()) * span);
                }
                Piece board = new Piece(Role.BOARD, options.wood()).beam(Section.RECT, half, thick,
                    span(ax, ay, az, bx, by, bz, type.sag() * span, half + 0.02, 0.25)).ends(End.FLAT, End.FLAT);
                look(board);
                pieces.add(board);
            }
        }
    }

    // ---------------------------------------------------------------- wire

    private void wire(double a, double b, boolean gateAtStart, boolean gateAtEnd) {
        Post[] posts = posts(stations(a, b, type.spacing(), 0.2), gateAtStart, gateAtEnd, End.POINT);
        int strands = Math.max(1, type.rails());
        for (int i = 0; i + 1 < posts.length; i++) {
            Post p = posts[i];
            Post q = posts[i + 1];
            double span = Math.sqrt((q.x - p.x) * (q.x - p.x) + (q.z - p.z) * (q.z - p.z));
            for (int k = 0; k < strands; k++) {
                double h = strands == 1 ? 0.85 * type.height() : (0.2 + 0.72 * k / (strands - 1)) * type.height();
                double roll = chance();
                if (roll < 0.25 * options.damage()) {
                    continue;
                }
                double sag = type.sag() * span * (1 + jitter * 0.5 * Math.abs(gauss()));
                if (roll < 0.55 * options.damage()) {
                    sag = 0.3 * span; // slack: down on the ground
                }
                Piece strand = new Piece(Role.WIRE, options.metal()).beam(Section.ROUND, type.railRadius(),
                    type.railRadius(), span(p.ax(h), p.ay(h), p.az(h), q.ax(h), q.ay(h), q.az(h), sag,
                        type.railRadius() + 0.01, span / 12)).ends(End.OPEN, End.OPEN);
                strand.thin = true;
                look(strand);
                pieces.add(strand);
            }
        }
    }

    // ---------------------------------------------------------------- wattle

    private void wattle(double a, double b) {
        double[] at = stations(a, b, type.spacing(), 0.15);
        int n = at.length;
        double stakeR = type.postRadius();
        double rodR = type.railRadius();
        double rowPitch = 2 * rodR + type.pieceGap();
        double bottom = 0.08;
        int rows = type.rails() > 0 ? type.rails() : Math.max(1, (int) Math.floor((type.height() - bottom - 0.04)
            / rowPitch));
        Post[] stakes = new Post[n];
        double[] sideX = new double[n];
        double[] sideZ = new double[n];
        for (int i = 0; i < n; i++) {
            sideX[i] = -line.dz(at[i]);
            sideZ[i] = line.dx(at[i]);
            double lean = Maths.toRadians(jitter * 2 * Math.abs(gauss()));
            double turn = chance() * Maths.TAU;
            stakes[i] = post(at[i], line.x(at[i]), line.z(at[i]), stakeR * (1 + 0.12 * jitter * gauss()),
                type.height() + 0.07 + 0.03 * jitter * gauss(), lean, Maths.cos(turn), Maths.sin(turn), false,
                Role.STAKE, End.JAGGED, sideX[i], sideZ[i]);
        }
        // rods: each row woven in front of and behind alternate stakes, a rod every four to six stakes
        int sub = 4;
        for (int k = 0; k < rows; k++) {
            double h = bottom + rodR + k * rowPitch;
            int i0 = 0;
            int span = 2 + (k * 3) % 4;
            while (i0 < n - 1) {
                int i1 = Math.min(n - 1, i0 + span);
                span = 4 + rng.nextInt(3);
                if (chance() < 0.3 * options.damage() * (0.3 + (double) k / rows)) {
                    i0 = i1;
                    continue;
                }
                double radius = rodR * (0.85 + 0.3 * chance());
                double[] path = new double[3 * ((i1 - i0) * sub + 1)];
                int at3 = 0;
                for (int i = i0; i <= i1; i++) {
                    int steps = i < i1 ? sub : 1;
                    for (int j = 0; j < steps; j++) {
                        double t = j / (double) sub;
                        int i2 = Math.min(i + 1, n - 1);
                        double oa = ((i + k) & 1) == 0 ? 1 : -1;
                        double ob = -oa;
                        double w = 0.5 * (1 + Maths.cos(Maths.PI * t));
                        double off = (stakes[i].radius + radius) * (w * oa + (1 - w) * ob);
                        double sx = sideX[i] + t * (sideX[i2] - sideX[i]);
                        double sz = sideZ[i] + t * (sideZ[i2] - sideZ[i]);
                        double sl = Math.sqrt(sx * sx + sz * sz);
                        double ha = h + 0.004 * gauss();
                        double cx = stakes[i].ax(ha) + t * (stakes[i2].ax(ha) - stakes[i].ax(ha));
                        double cz = stakes[i].az(ha) + t * (stakes[i2].az(ha) - stakes[i].az(ha));
                        double cy = stakes[i].ay(ha) + t * (stakes[i2].ay(ha) - stakes[i].ay(ha));
                        path[at3++] = cx + off * sx / sl;
                        path[at3++] = cy;
                        path[at3++] = cz + off * sz / sl;
                    }
                }
                Piece rod = new Piece(Role.ROD, options.wood()).beam(Section.ROUND, radius, radius, path)
                    .ends(End.OPEN, End.OPEN).levels(0, 1);
                rod.thin = true;
                rod.roll = chance() * Maths.TAU;
                look(rod);
                pieces.add(rod);
                i0 = i1;
            }
        }
        // a coarse level's woven panels: one slab per gap, as high as the rows
        double panel = rows * rowPitch;
        for (int i = 0; i + 1 < n; i++) {
            double hm = bottom + 0.5 * panel;
            Piece slab = new Piece(Role.ROD, options.wood()).beam(Section.RECT, 0.5 * panel, 0.8 * stakeR,
                Piece.line(stakes[i].ax(hm), stakes[i].ay(hm), stakes[i].az(hm), stakes[i + 1].ax(hm),
                    stakes[i + 1].ay(hm), stakes[i + 1].az(hm), tuning.groundStep(), 1)).ends(End.OPEN, End.OPEN)
                .levels(2, Integer.MAX_VALUE);
            look(slab);
            pieces.add(slab);
        }
    }

    // ---------------------------------------------------------------- split rails (Schärhag)

    private void splitRail(double a, double b) {
        int bays = Math.max(1, (int) Math.round((b - a) / type.spacing()));
        double step = (b - a) / bays;
        int n = bays + 1;
        double[] cx = new double[n];
        double[] cz = new double[n];
        double[] cy = new double[n];
        double[] sx = new double[n];
        double[] sz = new double[n];
        double amplitude = type.pieceWidth();
        for (int k = 0; k < n; k++) {
            double s = a + k * step + (k > 0 && k < n - 1 ? jitter * 0.08 * step * (chance() - 0.5) : 0);
            sx[k] = -line.dz(s);
            sz[k] = line.dx(s);
            double side = (k & 1) == 0 ? amplitude : -amplitude;
            cx[k] = line.x(s) + sx[k] * side;
            cz[k] = line.z(s) + sz[k] * side;
            cy[k] = ground.height(cx[k], cz[k]);
        }
        int rails = Math.max(1, type.rails());
        double base = 0.1;
        double pitch = (type.height() - base - 0.08) / rails;
        double radius = type.railRadius();
        double over = 0.24;
        for (int k = 0; k + 1 < n; k++) {
            double ex = cx[k + 1] - cx[k];
            double ez = cz[k + 1] - cz[k];
            double l = Math.sqrt(ex * ex + ez * ez);
            double ux = ex / l;
            double uz = ez / l;
            double stagger = (k & 1) * 0.5;
            for (int j = 0; j < rails; j++) {
                double ya = cy[k] + base + (j + stagger) * pitch + 0.5 * radius;
                double yb = cy[k + 1] + base + (j + stagger) * pitch + 0.5 * radius;
                double slope = (yb - ya) / l;
                double roll = chance();
                if (roll < options.damage() * (0.15 + 0.5 * j / rails)) {
                    continue;
                }
                double r = radius * (0.85 + 0.3 * chance());
                Piece rail = new Piece(Role.RAIL, options.wood()).up(0, 1, 0).ends(End.FLAT, End.FLAT);
                rail.roll = Maths.toRadians(200 + 50 * (chance() - 0.5)) + (chance() < 0.5 ? 0 : Maths.HALF_PI);
                look(rail);
                double ax = cx[k] - ux * over;
                double az = cz[k] - uz * over;
                double bx = cx[k + 1] + ux * over;
                double bz = cz[k + 1] + uz * over;
                double ay = ya - slope * over;
                double by = yb + slope * over;
                if (roll < options.damage() * (0.3 + 0.5 * j / rails)) {
                    // fallen from one crossing: its end on the ground
                    boolean dropA = chance() < 0.5;
                    if (dropA) {
                        ay = ground.height(ax, az) + 0.5 * r;
                    } else {
                        by = ground.height(bx, bz) + 0.5 * r;
                    }
                }
                rail.beam(Section.SPLIT, r, r, span(ax, ay, az, bx, by, bz, type.sag() * l, 0.5 * r, 0.5));
                pieces.add(rail);
            }
        }
        // a pair of stakes at every crossing, either side of the rails, lashed at the top and the middle
        double stakeR = type.postRadius();
        double top = base + (rails + 0.5) * pitch;
        for (int k = 0; k < n; k++) {
            double d = 1.15 * radius + stakeR + 0.01;
            Post[] pair = new Post[2];
            for (int side = 0; side < 2; side++) {
                double sign = side == 0 ? 1 : -1;
                double lean = Maths.toRadians(3 + jitter * 1.5 * Math.abs(gauss()));
                pair[side] = post(a, cx[k] + sign * d * sx[k], cz[k] + sign * d * sz[k], stakeR * (1 + 0.1 * gauss()),
                    top + 0.18 + 0.05 * jitter * gauss(), lean, -sign * sx[k], -sign * sz[k], false, Role.STAKE,
                    End.JAGGED, sx[k], sz[k]);
            }
            lashing(pair[0], pair[1], top + 0.02);
            lashing(pair[0], pair[1], base + 0.45 * rails * pitch);
        }
    }

    /** A withy ring around two posts at {@code h} above the first's ground. */
    private void lashing(Post p, Post q, double h) {
        double hq = h + p.y - q.y;
        double px = p.ax(h);
        double pz = p.az(h);
        double qx = q.ax(hq);
        double qz = q.az(hq);
        double ex = qx - px;
        double ez = qz - pz;
        double l = Math.sqrt(ex * ex + ez * ez);
        double ux = ex / l;
        double uz = ez / l;
        double withy = 0.011;
        int half = 6;
        double[] path = new double[3 * 2 * (half + 1)];
        int at = 0;
        for (int end = 0; end < 2; end++) {
            Post post = end == 0 ? q : p;
            double ox = end == 0 ? qx : px;
            double oz = end == 0 ? qz : pz;
            double oy = end == 0 ? q.ay(hq) : p.ay(h);
            double r = post.radius + withy;
            for (int i = 0; i <= half; i++) {
                double a = -Maths.HALF_PI + Maths.PI * i / half + end * Maths.PI;
                // around the post: from its outer side, half a turn
                double c = Maths.cos(a);
                double s = Maths.sin(a);
                path[at++] = ox + r * (c * ux - s * uz);
                path[at++] = oy + 0.01 * (i - half / 2.0) / half;
                path[at++] = oz + r * (c * uz + s * ux);
            }
        }
        Piece ring = new Piece(Role.LASHING, options.wood()).beam(Section.ROUND, withy, withy, path).levels(0, 1);
        ring.closed = true;
        ring.thin = true;
        look(ring);
        ring.wear += 0.1;
        pieces.add(ring);
    }

    // ---------------------------------------------------------------- Stangenzaun

    private void stangenzaun(double a, double b) {
        double[] at = stations(a, b, type.spacing(), 0.1);
        double poleR = type.railRadius();
        double postR = type.postRadius();
        double d = poleR * 1.15 + postR + 0.004;
        double height = type.height();
        for (double s : at) {
            double sx = -line.dz(s);
            double sz = line.dx(s);
            Post[] pair = new Post[2];
            for (int side = 0; side < 2; side++) {
                double sign = side == 0 ? 1 : -1;
                double lean = Maths.toRadians(jitter * 1.2 * Math.abs(gauss()));
                double turn = chance() * Maths.TAU;
                pair[side] = post(s, line.x(s) + sign * d * sx, line.z(s) + sign * d * sz, postR * (1 + 0.1 * gauss()),
                    height + 0.12 + 0.04 * jitter * gauss(), lean, Maths.cos(turn), Maths.sin(turn), false, Role.POST,
                    End.JAGGED, sx, sz);
            }
            lashing(pair[0], pair[1], 0.38 * height);
            lashing(pair[0], pair[1], 0.88 * height);
        }
        // the poles: parallel, slanted along the fence, each held by the pairs it passes
        double rise = height - 0.06;
        double run = Math.max(1.1 * type.spacing(), rise / Maths.tan(Maths.toRadians(type.angle())));
        double gap = type.pieceWidth();
        for (double s0 = a - run + gap; s0 < b - 0.3; s0 += gap * (1 + 0.15 * jitter * (chance() - 0.5))) {
            double s1 = s0 + run;
            double from = Math.max(s0, a);
            double to = Math.min(s1, b);
            if (to - from < 0.4) {
                continue;
            }
            if (chance() < 0.25 * options.damage()) {
                continue;
            }
            double xa = line.x(from);
            double za = line.z(from);
            double xb = line.x(to);
            double zb = line.z(to);
            double ya = ground.height(xa, za) + rise * (from - s0) / run - (from == s0 ? 0.04 : 0);
            double yb = ground.height(xb, zb) + rise * (to - s0) / run;
            double r = poleR * (0.85 + 0.3 * chance());
            // straight in height, following the line between the pairs (a curving fence's poles bend a little)
            int steps = Math.max(2, (int) Math.ceil((to - from) / 0.35));
            double[] path = new double[3 * (steps + 1)];
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double si = from + t * (to - from);
                path[3 * i] = i == 0 ? xa : i == steps ? xb : line.x(si);
                path[3 * i + 2] = i == 0 ? za : i == steps ? zb : line.z(si);
                double floor = ground.height(path[3 * i], path[3 * i + 2]) + (i == 0 ? -0.05 : r);
                path[3 * i + 1] = Math.max(ya + t * (yb - ya), floor);
            }
            Piece pole = new Piece(Role.POLE, options.wood()).beam(Section.ROUND, r, r, path).up(0, 1, 0)
                .ends(End.FLAT, End.FLAT);
            pole.roll = chance() * Maths.TAU;
            look(pole);
            pieces.add(pole);
        }
    }

    // ---------------------------------------------------------------- dry-stone wall

    private void wall(double a, double b) {
        int courses = Math.max(1, type.rails());
        double coping = 0.22;
        double body = type.height() - coping;
        double[] heights = new double[courses];
        double total = 0;
        for (int c = 0; c < courses; c++) {
            heights[c] = (1.15 - 0.1 * c) * (1 + 0.12 * jitter * gauss());
            total += heights[c];
        }
        for (int c = 0; c < courses; c++) {
            heights[c] *= body / total;
        }
        double baseHalf = type.postRadius();
        double topHalf = type.pieceWidth();
        double below = 0;
        for (int c = 0; c < courses; c++) {
            double mid = (below + 0.5 * heights[c]) / type.height();
            double half = baseHalf + mid * (topHalf - baseHalf);
            boolean top = c == courses - 1;
            double s = a - chance() * type.spacing() * 0.8;
            while (s < b - 0.05) {
                double length = type.spacing() * (0.65 + 0.8 * chance()) * (1 - 0.15 * c / courses);
                double s0 = Math.max(s, a);
                double s1 = Math.min(s + length, b);
                if (b - s1 < 0.15) {
                    s1 = b;
                }
                s = s1 + 0.012;
                if (s1 - s0 < 0.12) {
                    continue;
                }
                double sink = c == 0 ? 0.07 : 0;
                double h = heights[c] * (1.02 + 0.1 * chance()) + sink;
                if (top && chance() < 0.5 * options.damage()) {
                    fallen(0.5 * (s0 + s1), 0.5 * (s1 - s0), 0.5 * h, half);
                    continue;
                }
                stone(0.5 * (s0 + s1), 0.5 * (s1 - s0) - 0.004, h, half * (0.95 + 0.1 * chance()), below - sink, 0);
            }
            below += heights[c];
        }
        // coping: stones on edge along the top
        double s = a + 0.02;
        while (s < b - 0.08) {
            double length = 0.09 + 0.07 * chance();
            double s1 = Math.min(s + length, b - 0.01);
            double mid = 0.5 * (s + s1);
            s = s1 + 0.006;
            double h = coping * (0.85 + 0.35 * chance());
            if (chance() < 0.7 * options.damage()) {
                if (chance() < 0.6) {
                    fallen(mid, 0.5 * h, s1 - mid, topHalf);
                }
                continue;
            }
            stone(mid, s1 - mid - 0.002, h, topHalf * (0.9 + 0.15 * chance()), below,
                Maths.toRadians(14 * jitter * (chance() - 0.5)));
        }
        // corners: a column of stones across the corner, so the courses' outer ends meet
        for (int i = 1; i < line.count() - 1; i++) {
            double at = line.arc(i);
            if (at <= a + 0.2 || at >= b - 0.2 || line.turn(i) < 0.08) {
                continue;
            }
            double under = 0;
            for (int c = 0; c < courses; c++) {
                double mid = (under + 0.5 * heights[c]) / type.height();
                double half = baseHalf + mid * (topHalf - baseHalf);
                stone(at, half * 1.05, heights[c] + (c == 0 ? 0.07 : 0), half, under - (c == 0 ? 0.07 : 0), 0);
                under += heights[c];
            }
        }
        // a coarse level's wall: two bands of blocks following the ground
        double[] bandBottom = {-0.05, 0.5 * type.height()};
        double[] bandTop = {0.5 * type.height(), type.height()};
        for (int band = 0; band < 2; band++) {
            double mid = 0.5 * (bandBottom[band] + bandTop[band]);
            double half = baseHalf + mid / type.height() * (topHalf - baseHalf);
            double bandHalf = 0.5 * (bandTop[band] - bandBottom[band]);
            int blocks = Math.max(1, (int) Math.round((b - a) / 1.2));
            for (int k = 0; k < blocks; k++) {
                double from = a + (b - a) * k / blocks;
                double to = a + (b - a) * (k + 1) / blocks;
                int steps = Math.max(1, (int) Math.ceil((to - from) / tuning.groundStep()));
                double[] path = new double[3 * (steps + 1)];
                for (int i = 0; i <= steps; i++) {
                    double si = from + (to - from) * i / steps;
                    path[3 * i] = line.x(si);
                    path[3 * i + 2] = line.z(si);
                    path[3 * i + 1] = ground.height(path[3 * i], path[3 * i + 2]) + mid;
                }
                Piece block = new Piece(Role.STONE, options.stone()).beam(Section.RECT, bandHalf, half, path)
                    .ends(End.FLAT, End.FLAT).levels(2, Integer.MAX_VALUE);
                look(block);
                pieces.add(block);
            }
        }
    }

    /**
     * A stone centred at {@code s} along the line: half length {@code halfLength}, {@code height} high, half thickness
     * {@code half}, its bottom {@code bottom} above the ground at its centre, leaning {@code lean} along the wall.
     */
    private void stone(double s, double halfLength, double height, double half, double bottom, double lean) {
        double x = line.x(s) + 0.015 * jitter * gauss() * -line.dz(s);
        double z = line.z(s) + 0.015 * jitter * gauss() * line.dx(s);
        double tx = line.dx(s);
        double tz = line.dz(s);
        // tilted with half the ground's slope along the wall, and its lean
        double slope = 0.5 * (ground.height(x + 0.2 * tx, z + 0.2 * tz) - ground.height(x - 0.2 * tx, z - 0.2 * tz))
            / 0.4 + Maths.tan(lean);
        Piece stone = stonePiece(x, ground.height(x, z) + bottom, z, tx, slope, tz, halfLength, 0.5 * height, half);
        pieces.add(stone);
    }

    /** A stone fallen from the wall near {@code s}: on the ground beside it, turned at random. */
    private void fallen(double s, double halfLength, double halfHeight, double wallHalf) {
        double side = (chance() < 0.5 ? -1 : 1) * (wallHalf + 0.15 + 0.6 * chance());
        double x = line.x(s) - line.dz(s) * side + 0.3 * gauss();
        double z = line.z(s) + line.dx(s) * side + 0.3 * gauss();
        double yaw = chance() * Maths.TAU;
        Piece stone = stonePiece(x, ground.height(x, z) - 0.04, z, Maths.cos(yaw), 0.25 * (chance() - 0.5),
            Maths.sin(yaw), halfLength, halfHeight, wallHalf * (0.5 + 0.4 * chance()));
        stone.wear += 0.1;
        pieces.add(stone);
    }

    private Piece stonePiece(double x, double y, double z, double ax, double ay, double az, double halfLength,
        double halfHeight, double halfDepth) {
        double al = Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= al;
        ay /= al;
        az /= al;
        // up: +y less its part along the stone; across = along x up
        double ux = -ay * ax;
        double uy = 1 - ay * ay;
        double uz = -ay * az;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        Piece stone = new Piece(Role.STONE, options.stone());
        stone.stone = true;
        double[] f = stone.frame;
        f[0] = ax;
        f[1] = ay;
        f[2] = az;
        f[3] = ux;
        f[4] = uy;
        f[5] = uz;
        f[6] = ay * uz - az * uy;
        f[7] = az * ux - ax * uz;
        f[8] = ax * uy - ay * ux;
        stone.cx = x;
        stone.cy = y;
        stone.cz = z;
        stone.sx = halfLength;
        stone.sy = halfHeight;
        stone.sz = halfDepth;
        stone.roundness = 5 + 4 * chance();
        stone.shape = rng.nextLong();
        look(stone);
        stone.v0 = rng.nextDouble();
        return stone;
    }

    // ---------------------------------------------------------------- gates

    /** A gate between distances a and b: its posts (unless the run's end posts are) and a braced leaf. */
    private void gate(double a, double b, double postRadius) {
        boolean postKind = switch (type.kind()) {
            case POST_AND_RAIL, PICKET, BOARD, WIRE -> true;
            default -> false;
        };
        Post p;
        Post q;
        double height = Math.min(type.height(), 1.25) + 0.12;
        if (postKind) {
            // the runs' end posts are the gate's: find them
            p = postAt(a, postRadius, height);
            q = postAt(b, postRadius, height);
        } else {
            p = linePost(a, postRadius, height, false, Role.POST, End.POINT);
            q = linePost(b, postRadius, height, false, Role.POST, End.POINT);
        }
        double ex = q.x - p.x;
        double ez = q.z - p.z;
        double span = Math.sqrt(ex * ex + ez * ez);
        double ux = ex / span;
        double uz = ez / span;
        double sideX = -uz;
        double sideZ = ux;
        double hinge = p.radius + 0.03;
        double latch = span - q.radius - 0.03;
        if (latch - hinge < 0.3) {
            return;
        }
        double x0 = p.x + ux * hinge;
        double z0 = p.z + uz * hinge;
        double x1 = p.x + ux * latch;
        double z1 = p.z + uz * latch;
        double floor = Math.max(ground.height(x0, z0), ground.height(x1, z1));
        int n = Math.max(2, (int) Math.ceil((latch - hinge) / 0.1));
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            floor = Math.max(floor, ground.height(x0 + t * (x1 - x0), z0 + t * (z1 - z0)));
        }
        double bottom = floor + 0.07;
        double top = bottom + Math.min(type.height(), 1.15) - 0.07;
        double stile = 0.045;
        double thick = 0.025;
        // stiles at the hinge and the latch
        for (double t : new double[] {0, 1}) {
            double x = x0 + t * (x1 - x0) + ux * stile * (t == 0 ? 1 : -1);
            double z = z0 + t * (z1 - z0) + uz * stile * (t == 0 ? 1 : -1);
            Piece s = new Piece(Role.GATE, options.wood()).beam(Section.RECT, thick, stile,
                Piece.line(x, bottom, z, x, top, z, 0.3, 1)).up(sideX, 0, sideZ).ends(End.FLAT, End.FLAT);
            look(s);
            pieces.add(s);
        }
        // three rails across, and a brace from the hinge's foot to the latch's top
        double railHalf = 0.045;
        double ax = x0 + ux * 2 * stile;
        double az = z0 + uz * 2 * stile;
        double bx = x1 - ux * 2 * stile;
        double bz = z1 - uz * 2 * stile;
        for (double f : new double[] {0.12, 0.5, 0.88}) {
            double y = bottom + f * (top - bottom);
            Piece rail = new Piece(Role.GATE, options.wood()).beam(Section.RECT, railHalf, 0.02,
                Piece.line(ax - ux * stile, y, az - uz * stile, bx + ux * stile, y, bz + uz * stile, 0.4, 1))
                .ends(End.FLAT, End.FLAT);
            look(rail);
            pieces.add(rail);
        }
        double yb = bottom + 0.12 * (top - bottom) + railHalf;
        double yt = bottom + 0.88 * (top - bottom) - railHalf;
        Piece brace = new Piece(Role.GATE, options.wood()).beam(Section.RECT, 0.04, 0.018,
            Piece.line(ax, yb, az, bx, yt, bz, 0.4, 1)).ends(End.FLAT, End.FLAT); // broad faces front and back
        look(brace);
        pieces.add(brace);
    }

    /** The post laid at distance {@code s} (a run's end post), or a new gate post there. */
    private Post postAt(double s, double radius, double height) {
        Post post = new Post();
        post.s = s;
        post.x = line.x(s);
        post.z = line.z(s);
        post.y = ground.height(post.x, post.z);
        post.radius = radius;
        post.height = height;
        return post;
    }
}
