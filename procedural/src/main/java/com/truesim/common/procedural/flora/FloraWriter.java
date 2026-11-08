package com.truesim.common.procedural.flora;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Hash;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.mesh.MeshWriter;

/**
 * Meshes the small parts of plants into a {@link MeshWriter}: flowers (petals, bells, composite heads, globes, stars,
 * with sepals and a centre), fruits (berries, apples, cones, grass spikes, with a calyx), stalks, tubes, and flat organs
 * (leaves, blades, petals) as cupped, curving strips. Colors come from the {@link Palette} by texture coordinates; the
 * wind values are the writer's, set by the caller (the part's branch), except the leaf weight, which rises toward the
 * tips of petals and leaves by the flutter given.
 *
 * <p>Shapes are placed in a frame: an origin, an axis (where a flower faces, from a fruit's stalk to its apex) and a
 * reference direction across it. Detail 0 is full, 1 simplified, 2 minimal: fewer segments, sides and parts. Petals
 * and leaves are single sheets (for two-sided materials), fruits closed surfaces. Not thread-safe; reused.
 */
public final class FloraWriter {

    private static final Vec3 DOWN = new Vec3(0, -1, 0);

    private MeshWriter out;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 axis = Vec3.UP;
    private Vec3 reference = Vec3.UNIT_X;
    private Vec3 side = Vec3.UNIT_Z;

    /** Writes into {@code writer} from now on. */
    public FloraWriter target(MeshWriter writer) {
        this.out = writer;
        return this;
    }

    /** The writer written into. */
    public MeshWriter writer() {
        return out;
    }

    /**
     * The frame of the next shape: at {@code at}, facing the unit {@code direction}, {@code across} (any direction
     * not along it) turning it.
     */
    public FloraWriter frame(Vec3 at, Vec3 direction, Vec3 across) {
        origin = at;
        axis = direction.normalize();
        Vec3 r = across.sub(axis.scale(across.dot(axis)));
        reference = r.lengthSquared() > 1e-12 ? r.normalize() : axis.anyPerpendicular().normalize();
        side = axis.cross(reference);
        return this;
    }

    /** A unit vector across {@code direction}, turned {@code angle} radians about it from a fixed start. */
    public static Vec3 around(Vec3 direction, double angle) {
        Vec3 d = direction.normalize();
        Vec3 start = Math.abs(d.y()) < 0.9 ? Vec3.UP : Vec3.UNIT_X;
        Vec3 r = start.sub(d.scale(start.dot(d))).normalize();
        Vec3 b = d.cross(r);
        return r.scale(Maths.cos(angle)).addScaled(b, Maths.sin(angle));
    }

    /** {@code d} turned toward {@code target} by {@code radians}, in the plane they span. */
    public static Vec3 turn(Vec3 d, Vec3 target, double radians) {
        Vec3 perpendicular = target.sub(d.scale(target.dot(d)));
        double length = perpendicular.length();
        if (length < 1e-9) {
            return d;
        }
        return d.scale(Maths.cos(radians)).addScaled(perpendicular, Maths.sin(radians) / length).normalize();
    }

    /** {@code d} turned toward the ground by {@code radians}. */
    public static Vec3 sink(Vec3 d, double radians) {
        return turn(d, DOWN, radians);
    }

    private Vec3 radial(double angle) {
        return reference.scale(Maths.cos(angle)).addScaled(side, Maths.sin(angle));
    }

    private static double jitter(int seed, int salt) {
        return Hash.random(seed, salt) - 0.5;
    }

    // Flowers ---------------------------------------------------------------------------------------------------------

    /**
     * A flower in the frame as {@code state} says (nothing for no flower or a fruit), {@code scale} times its size,
     * petals fluttering up to {@code flutter} at their tips; {@code seed} varies its petals.
     */
    public void flower(FlowerShape shape, FloralState state, Palette palette, double scale, int detail,
        double flutter, int seed) {
        if (state.stage() != FloralState.Stage.BUD && state.stage() != FloralState.Stage.FLOWER) {
            return;
        }
        double size = scale * state.size();
        double open = state.open();
        double age = state.age();
        double leaf = out.leafWeight();
        double turn = Maths.TAU * Hash.random(seed, 1);
        switch (shape.form()) {
            case SIMPLE -> simple(shape, size, open, age, detail, flutter, seed, turn);
            case BELL -> bell(shape, size, open, age, detail, flutter, turn);
            case COMPOSITE, STAR -> composite(shape, size, open, age, detail, flutter, seed, turn);
            case GLOBE -> globe(shape, size, open, age, detail, turn);
        }
        out.leafWeight(leaf);
    }

    private void simple(FlowerShape shape, double size, double open, double age, int level, double flutter, int seed,
        double turn) {
        // Tiny flowers (rowan's, elder's: hundreds to a head) are drawn simply at every level
        int detail = shape.reach() * size < 0.008 ? Math.max(level, 2) : level;
        int n = Math.max(shape.petals(), 1);
        double cr = shape.centreRadius() * size;
        double tilt = Maths.lerp(1.3, shape.cupping() * 1.25, open);
        double curl = Maths.lerp(0.9, 0.35 * shape.cupping() - 0.1, open);
        double length = shape.length() * size * (0.55 + 0.45 * open);
        double width = shape.width() * size * (0.55 + 0.45 * open);
        int segments = detail == 0 ? 2 : 1;
        Outline outline = Outline.of(shape.tip());
        sepals(shape, size, open, detail, n, turn, seed);
        for (int k = 0; k < n; k++) {
            double angle = turn + Maths.TAU * (k + 0.15 * jitter(seed, k)) / n;
            double t = tilt + 0.12 * jitter(seed, 50 + k);
            petal(angle, 0.5 * cr, t, curl, length, width, outline, 0.2 + 0.4 * Math.max(shape.cupping(), 0),
                segments, detail, Part.PETAL, age, flutter);
        }
        if (open > 0.2 && shape.centreRadius() > 0) {
            dome(origin.addScaled(axis, 0.15 * length * Maths.sin(tilt)), cr, shape.centreHeight() * size,
                detail == 0 ? 7 : detail == 1 ? 5 : 3, Part.CENTRE, age);
        }
    }

    private void sepals(FlowerShape shape, double size, double open, int detail, int n, double turn, int seed) {
        if (detail > 0 || shape.sepals() == 0) {
            return;
        }
        double length = shape.sepalLength() * size;
        for (int k = 0; k < shape.sepals(); k++) {
            double angle = turn + Maths.TAU * (k + 0.5) / shape.sepals() + 0.1 * jitter(seed, 100 + k);
            petal(angle, 0.3 * shape.centreRadius() * size, Maths.lerp(1.15, -0.25, open), 0.2, length,
                0.4 * length, Outline.POINTED, 0.25, 1, detail, Part.SEPAL, 0, 0);
        }
    }

    /**
     * One petal (or sepal, ray, bract) from {@code base} metres out along {@code angle}, tilted {@code tilt} radians
     * up toward the axis, curling {@code curl} more over its length.
     */
    private void petal(double angle, double base, double tilt, double curl, double length, double width,
        Outline outline, double fold, int segments, int detail, Part part, double u, double flutter) {
        Vec3 out = radial(angle);
        Vec3 direction = out.scale(Maths.cos(tilt)).addScaled(axis, Maths.sin(tilt));
        Vec3 normal = axis.scale(Maths.cos(tilt)).addScaled(out, -Maths.sin(tilt));
        Vec3 start = origin.addScaled(out, base);
        if (detail >= 2) {
            triangle(start, direction, normal, length, width, part, u, flutter);
        } else {
            strip(start, direction, normal, length, width, outline, curl, fold, segments, part, u, 0, flutter,
                Double.NaN, Double.NaN);
        }
    }

    /** A single triangle: the base's point and the tip's width (minimal detail). */
    private void triangle(Vec3 start, Vec3 direction, Vec3 normal, double length, double width, Part part, double u,
        double flutter) {
        Vec3 across = normal.cross(direction);
        double pu = Palette.u(u);
        double pv = Palette.v(part);
        out.leafWeight(0);
        int a = vertex(start, normal, across, pu, pv);
        out.leafWeight(flutter);
        Vec3 tip = start.addScaled(direction, length);
        int b = vertex(tip.addScaled(across, -0.45 * width), normal, across, pu, pv);
        int c = vertex(tip.addScaled(across, 0.45 * width), normal, across, pu, pv);
        out.triangle(a, b, c);
    }

    private void bell(FlowerShape shape, double size, double open, double age, int detail, double flutter,
        double turn) {
        int lobes = Math.max(shape.petals(), 3);
        int sides = detail == 0 ? 2 * lobes : detail == 1 ? lobes : 3;
        int rings = detail == 0 ? 4 : detail == 1 ? 2 : 1;
        double length = shape.length() * size * (0.7 + 0.3 * open);
        double mouth = shape.width() * size;
        double throat = 0.35 * mouth;
        double flare = shape.cupping() * open;
        double pu = Palette.u(age);
        double pv = Palette.v(Part.PETAL);
        int first = -1;
        for (int i = 0; i <= rings; i++) {
            double s = (double) i / rings;
            double rho = throat + (mouth - throat) * Maths.pow(s, 0.8 + flare)
                + shape.waist() * mouth * 0.35 * Maths.sin(Math.PI * s);
            rho *= 1 - shape.waist() * 0.6 * Phenology.smooth(0.6, 1, s);
            if (s > 0.35) {
                rho *= Maths.lerp(0.4, 1, open); // a closed bud: a spindle
            }
            double z = length * s;
            out.leafWeight(flutter * s);
            int ring = -1;
            for (int j = 0; j <= sides; j++) {
                double angle = turn + Maths.TAU * (j % sides) / sides;
                double r = rho;
                double h = z;
                if (i == rings && detail == 0) {
                    boolean lobe = j % 2 == 0;
                    r = lobe ? rho * (1 + 0.45 * flare) : rho * (1 + 0.1 * flare);
                    h = lobe ? z + 0.12 * length * (1 - 0.6 * flare) : z - 0.1 * length;
                }
                Vec3 radial = radial(angle);
                Vec3 p = origin.addScaled(axis, h).addScaled(radial, r);
                double slope = (mouth - throat) / Math.max(length, 1e-9);
                Vec3 normal = radial.addScaled(axis, -slope).normalize();
                Vec3 tangent = axis.cross(radial);
                int v = vertex(p, normal, tangent, pu, pv);
                if (j == 0) {
                    ring = v;
                }
            }
            if (first >= 0) {
                for (int j = 0; j < sides; j++) {
                    out.quad(first + j, first + j + 1, ring + j + 1, ring + j);
                }
            }
            first = ring;
        }
    }

    private void composite(FlowerShape shape, double size, double open, double age, int detail, double flutter,
        int seed, double turn) {
        boolean star = shape.form() == FlowerShape.Form.STAR;
        int n = Math.max(shape.petals(), 1);
        int rays = detail == 0 ? n : detail == 1 ? Math.max(5, (n + 1) / 2) : Math.min(n, 6);
        double cr = shape.centreRadius() * size;
        double tilt = Maths.lerp(1.35, shape.cupping() * 0.6, open);
        double length = shape.length() * size * (0.3 + 0.7 * open);
        double width = shape.width() * size * (n / (double) rays);
        Outline outline = star ? Outline.POINTED : Outline.of(shape.tip());
        sepals(shape, size, open, detail, n, turn, seed);
        for (int k = 0; k < rays; k++) {
            double angle = turn + Maths.TAU * (k + 0.2 * jitter(seed, k)) / rays;
            double grow = star ? 0.7 + 0.6 * Hash.random(seed, 200 + k) : 1;
            petal(angle, 0.85 * cr, tilt + 0.15 * jitter(seed, 300 + k), star ? 0.15 : -0.25 * open, length * grow,
                width * (star ? 0.8 + 0.4 * grow : 1), outline, star ? 0.35 : 0.15, detail == 0 ? 2 : 1, detail,
                Part.PETAL, age, flutter);
        }
        int sides = detail == 0 ? 8 : detail == 1 ? 5 : 3;
        double height = shape.centreHeight() * size;
        if (star && detail < 2 && shape.heads() > 1) {
            Vec3 at = origin;
            for (int h = 0; h < shape.heads(); h++) {
                double angle = turn + Maths.TAU * h / (shape.heads() - 1);
                Vec3 c = h == 0 ? at.addScaled(axis, 0.3 * height)
                    : at.addScaled(radial(angle), 1.1 * cr).addScaled(axis, 0.1 * height);
                dome(c, 0.75 * cr, height, sides, Part.CENTRE, age);
            }
        } else if (cr > 0) {
            dome(origin, cr, height, sides, Part.CENTRE, age);
        }
    }

    private void globe(FlowerShape shape, double size, double open, double age, int detail, double turn) {
        double radius = shape.length() * size;
        int around = detail == 0 ? 10 : detail == 1 ? 6 : 3;
        int stacks = detail == 0 ? 7 : detail == 1 ? 4 : 2;
        Vec3 centre = origin.addScaled(axis, 0.8 * radius);
        spheroid(centre, radius, radius, around, stacks, FruitShape.Form.BERRY, 0, Math.max(shape.petals(), 1),
            Part.PETAL, Maths.lerp(0.15, age, open), turn);
        sepals(shape, size, 1, detail, shape.petals(), turn, 0);
    }

    /** A dome over a disc: a centre of stamens, a composite's disc. */
    private void dome(Vec3 at, double radius, double height, int sides, Part part, double u) {
        double pu = Palette.u(u);
        double pv = Palette.v(part);
        Vec3 apex = at.addScaled(axis, height);
        int center = vertex(apex, axis, reference, pu, pv);
        int ring = -1;
        double slope = height / Math.max(radius, 1e-9);
        for (int j = 0; j <= sides; j++) {
            Vec3 radial = radial(Maths.TAU * (j % sides) / sides);
            Vec3 normal = radial.scale(slope).add(axis).normalize();
            int v = vertex(at.addScaled(radial, radius), normal, axis.cross(radial), pu, pv);
            if (j == 0) {
                ring = v;
            }
        }
        for (int j = 0; j < sides; j++) {
            out.triangle(ring + j, ring + j + 1, center);
        }
    }

    // Fruits ----------------------------------------------------------------------------------------------------------

    /**
     * A fruit hanging from the frame's origin along its axis (stalk end at the origin, apex away), {@code size} times
     * its full size, colored {@code part} at {@code ripeness}.
     */
    public void fruit(FruitShape shape, double size, double ripeness, Part part, int detail, int seed) {
        double radius = shape.radius() * size;
        double half = shape.halfLength() * size;
        int around;
        int stacks;
        boolean small = shape.radius() < 0.012;
        boolean large = shape.radius() >= 0.025;
        boolean tiny = radius < 0.0065;
        boolean minute = radius < 0.0035;
        switch (shape.form()) {
            case CONE -> {
                boolean little = shape.radius() < 0.015;
                around = detail == 0 ? (little ? 6 : 7) : detail == 1 ? 5 : 3;
                stacks = detail == 0 ? (little ? 6 : 8) : detail == 1 ? 4 : 3;
            }
            case SPIKE -> {
                around = detail == 0 ? 5 : detail == 1 ? 4 : 3;
                stacks = detail == 0 ? 7 : detail == 1 ? 4 : 2;
            }
            default -> {
                around = detail == 0 ? (minute ? 4 : tiny ? 5 : small ? 6 : large ? 10 : 8)
                    : detail == 1 ? (tiny ? 4 : small ? 4 : large ? 7 : 5) : 4;
                stacks = detail == 0 ? (minute ? 2 : tiny ? 3 : small ? 4 : large ? 7 : 5)
                    : detail == 1 ? (tiny ? 2 : small ? 3 : large ? 5 : 4) : 2;
            }
        }
        double turn = Maths.TAU * Hash.random(seed, 7);
        Vec3 centre = origin.addScaled(axis, half * (1 - 0.5 * shape.dimple()));
        spheroid(centre, radius, half, around, stacks, shape.form(), shape.dimple(), shape.scales(), part, ripeness,
            turn);
        if (shape.calyx() > 0 && detail == 0 && radius >= 0.006) {
            calyx(centre.addScaled(axis, half * (1 - shape.dimple())), shape.calyx() * radius, turn);
        }
    }

    private void spheroid(Vec3 centre, double radius, double half, int around, int stacks, FruitShape.Form form,
        double dimple, int scales, Part part, double u, double turn) {
        double pu = Palette.u(u);
        double pv = Palette.v(part);
        int previous = -1;
        for (int i = 0; i <= stacks; i++) {
            double theta = Math.PI * i / stacks;
            double st = Maths.sin(theta);
            double ct = Maths.cos(theta);
            double rho = radius * st;
            double h = -half * ct;
            switch (form) {
                case POME -> h *= 1 - dimple * Maths.exp(-st * st / (0.35 * 0.35));
                case CONE -> rho = radius * Maths.pow(st, 0.85) * (1 - 0.35 * Math.max(0, -ct));
                case SPIKE -> rho = radius * Maths.pow(st, 0.7);
                default -> {
                }
            }
            boolean pole = i == 0 || i == stacks;
            int ring = -1;
            for (int j = 0; j <= around; j++) {
                double angle = turn + Maths.TAU * (j % around) / around;
                Vec3 radial = radial(angle);
                double r = rho;
                if (scales > 0 && !pole) {
                    // a spiral of scales (cones) or florets (globes, spikes): bumps that read in the outline
                    double phase = scales * (j % around) / (double) around + 2.5 * theta / Math.PI;
                    r *= 1 + (form == FruitShape.Form.CONE ? 0.16 : 0.1) * Maths.cos(Maths.TAU * phase);
                }
                Vec3 p = centre.addScaled(axis, h).addScaled(radial, r);
                // the spheroid's normal: (radial rho / radius^2, axis h / half^2)
                Vec3 normal = radial.scale(st / Math.max(radius, 1e-9)).addScaled(axis, -ct / Math.max(half, 1e-9));
                normal = normal.lengthSquared() > 1e-18 ? normal.normalize() : (ct > 0 ? axis.negate() : axis);
                int v = vertex(p, normal, axis.cross(radial), pu, pv);
                if (j == 0) {
                    ring = v;
                }
            }
            if (previous >= 0) {
                for (int j = 0; j < around; j++) {
                    if (i == 1) {
                        out.triangle(previous + j, ring + j + 1, ring + j);
                    } else if (i == stacks) {
                        out.triangle(previous + j, previous + j + 1, ring + j);
                    } else {
                        out.quad(previous + j, previous + j + 1, ring + j + 1, ring + j);
                    }
                }
            }
            previous = ring;
        }
    }

    /** A calyx crown at {@code at}: five small dried lobes spreading from the apex. */
    private void calyx(Vec3 at, double radius, double turn) {
        double pu = Palette.u(1);
        double pv = Palette.v(Part.SEPAL);
        for (int k = 0; k < 5; k++) {
            Vec3 a = radial(turn + Maths.TAU * k / 5);
            Vec3 b = radial(turn + Maths.TAU * (k + 0.5) / 5);
            Vec3 tip = at.addScaled(b, radius).addScaled(axis, 0.35 * radius);
            int c = vertex(at.addScaled(axis, -0.1 * radius), axis, a, pu, pv);
            int l = vertex(at.addScaled(a, 0.45 * radius), axis, a, pu, pv);
            int r = vertex(at.addScaled(radial(turn + Maths.TAU * (k + 1) / 5), 0.45 * radius), axis, a, pu, pv);
            int t = vertex(tip, axis, a, pu, pv);
            out.triangle(c, l, t);
            out.triangle(c, t, r);
        }
    }

    // Stalks and strips -----------------------------------------------------------------------------------------------

    /** A thin stalk from {@code from} to {@code to}, {@code sides} around, colored by the stalk ramp at {@code u}. */
    public void stalk(Vec3 from, Vec3 to, double radius, int sides, double u) {
        Vec3 d = to.sub(from);
        double length = d.length();
        if (length < 1e-9) {
            return;
        }
        Vec3 t = d.scale(1 / length);
        Vec3 r = around(t, 0);
        Vec3 b = t.cross(r);
        double pu = Palette.u(u);
        double pv = Palette.v(Part.STALK);
        int start = -1;
        for (int end = 0; end < 2; end++) {
            Vec3 at = end == 0 ? from : to;
            double rr = end == 0 ? radius : 0.75 * radius;
            int ring = -1;
            for (int j = 0; j <= sides; j++) {
                double angle = Maths.TAU * (j % sides) / sides;
                Vec3 radial = r.scale(Maths.cos(angle)).addScaled(b, Maths.sin(angle));
                int v = vertex(at.addScaled(radial, rr), radial, t, pu, pv);
                if (j == 0) {
                    ring = v;
                }
            }
            if (start >= 0) {
                for (int j = 0; j < sides; j++) {
                    out.quad(start + j, start + j + 1, ring + j + 1, ring + j);
                }
            }
            start = ring;
        }
    }

    /**
     * A tube along {@code count} points ({@code xyz} triples): a plant's stem. Radii run from {@code r0} to
     * {@code r1}; with {@code branch} not null, vertices take its branch weights per point.
     */
    public void tube(double[] xyz, int count, double r0, double r1, int sides, Part part, double u, double[] branch) {
        double pu = Palette.u(u);
        double pv = Palette.v(part);
        Vec3 previousR = null;
        int start = -1;
        for (int i = 0; i < count; i++) {
            Vec3 p = new Vec3(xyz[3 * i], xyz[3 * i + 1], xyz[3 * i + 2]);
            int a = Math.max(i - 1, 0);
            int b = Math.min(i + 1, count - 1);
            Vec3 t = new Vec3(xyz[3 * b] - xyz[3 * a], xyz[3 * b + 1] - xyz[3 * a + 1], xyz[3 * b + 2] - xyz[3 * a + 2]);
            t = t.lengthSquared() > 1e-18 ? t.normalize() : Vec3.UP;
            // rotation-minimizing enough for gently curving stems: project the previous reference
            Vec3 r = previousR == null ? around(t, 0) : previousR.sub(t.scale(previousR.dot(t)));
            r = r.lengthSquared() > 1e-12 ? r.normalize() : around(t, 0);
            previousR = r;
            Vec3 bb = t.cross(r);
            double radius = Maths.lerp(r0, r1, count > 1 ? (double) i / (count - 1) : 0);
            if (branch != null) {
                out.branchWeight(branch[i]);
            }
            int ring = -1;
            for (int j = 0; j <= sides; j++) {
                double angle = Maths.TAU * (j % sides) / sides;
                Vec3 radial = r.scale(Maths.cos(angle)).addScaled(bb, Maths.sin(angle));
                int v = vertex(p.addScaled(radial, radius), radial, t, pu, pv);
                if (j == 0) {
                    ring = v;
                }
            }
            if (start >= 0) {
                for (int j = 0; j < sides; j++) {
                    out.quad(start + j, start + j + 1, ring + j + 1, ring + j);
                }
            }
            start = ring;
        }
    }

    /**
     * A flat organ as a strip from {@code base} along the unit {@code direction}, its face toward the unit
     * {@code normal}: {@code length} long, {@code width} at its widest by {@code outline}, curving {@code bend}
     * radians toward its face over its length (negative: arching away), its edges raised by {@code fold} of their
     * half width (a cupped petal, a keeled blade), in {@code segments}. The leaf weight runs from {@code leaf0} at
     * the base to {@code leaf1} at the tip, the branch weight (unless NaN) from {@code branch0} to {@code branch1}.
     */
    public void strip(Vec3 base, Vec3 direction, Vec3 normal, double length, double width, Outline outline,
        double bend, double fold, int segments, Part part, double u, double leaf0, double leaf1, double branch0,
        double branch1) {
        Vec3 across = normal.cross(direction).normalize();
        double pu = Palette.u(u);
        double pv = Palette.v(part);
        int previous = -1;
        double floor = segments < 3 ? 0.35 : 0;
        for (int i = 0; i <= segments; i++) {
            double s = (double) i / segments;
            double beta = bend * s;
            Vec3 d = direction.scale(Maths.cos(beta)).addScaled(normal, Maths.sin(beta));
            Vec3 n = normal.scale(Maths.cos(beta)).addScaled(direction, -Maths.sin(beta));
            Vec3 p;
            if (Math.abs(bend) > 1e-6) {
                p = base.addScaled(direction, length * Maths.sin(beta) / bend)
                    .addScaled(normal, length * (1 - Maths.cos(beta)) / bend);
            } else {
                p = base.addScaled(direction, length * s);
            }
            double w = i == segments ? outline.width(s) : Math.max(outline.width(s), floor);
            double hw = 0.5 * width * w;
            out.leafWeight(Maths.lerp(leaf0, leaf1, s));
            if (!Double.isNaN(branch0)) {
                out.branchWeight(Maths.lerp(branch0, branch1, s));
            }
            Vec3 lift = n.scale(fold * hw);
            Vec3 middle = i == segments ? p.addScaled(d, outline.tip() * length) : p;
            int right = vertex(p.addScaled(across, -hw).add(lift), n.addScaled(across, fold).normalize(), across, pu,
                pv);
            int mid = vertex(middle, n, across, pu, pv);
            int left = vertex(p.addScaled(across, hw).add(lift), n.addScaled(across, -fold).normalize(), across, pu,
                pv);
            if (previous >= 0) {
                // previous: right, mid, left at previous, previous + 1, previous + 2
                out.quad(previous + 1, previous, right, mid);
                out.quad(previous + 1, mid, left, previous + 2);
            }
            previous = right;
        }
    }

    private int vertex(Vec3 p, Vec3 n, Vec3 t, double u, double v) {
        Vec3 tangent = t.sub(n.scale(t.dot(n)));
        tangent = tangent.lengthSquared() > 1e-12 ? tangent.normalize() : n.anyPerpendicular().normalize();
        return out.vertex(p.x(), p.y(), p.z(), n.x(), n.y(), n.z(), tangent.x(), tangent.y(), tangent.z(), u, v);
    }
}
