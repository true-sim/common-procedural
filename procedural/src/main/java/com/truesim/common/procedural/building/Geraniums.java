package com.truesim.common.procedural.building;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.flora.Albedo;
import com.truesim.common.procedural.flora.FloraWriter;
import com.truesim.common.procedural.flora.FloralState;
import com.truesim.common.procedural.flora.FlowerShape;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.flora.Ramp;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;

/**
 * The geraniums of Bernese balconies and window boxes, overflowing as they are in summer, their flowers and stems meshed
 * by common-procedural's flora ({@link FloraWriter}): zonal geraniums (Pelargonium zonale) packed in two rows, dense
 * mounds of round, scalloped, cupped leaves with their dark horseshoe band spilling over the box's rim, and many
 * ball-shaped umbels close together above them; ivy-leaf geraniums (Pelargonium peltatum) along the front, curtains of
 * stems hanging 40 to 80 cm over a balcony's railing (less under a window), each with its own sway, leafy all along,
 * flowering in clusters down their length. Scarlet, pink or cerise by the seed, each box as full as its seed says.
 *
 * <p>A box's mesh is in its own frame: x along the box from 0 to its length, y up from the soil, z out toward the
 * front edge (the box spans -depth/2 to depth/2). Submesh 0 the leaves and stems, 1 the flowers; both colored from
 * {@link #palette()} by texture coordinates (a building's {@code foliage} and {@code flowers} slots). Leaves are a
 * front sheet (the band in its colors) and a darker back sheet just behind it, so seen from below they read darker;
 * petals are single sheets (two-sided materials). Detail 0: every leaf with its band and back, every flower; 1: fewer,
 * simpler leaves and flowers; 2: two cards (a curtain of leaves over the edge, a band of flowers); 3: nothing. Each
 * level keeps a budget per metre of box ({@link #BUDGET}), thinned until it fits.
 */
@AllowAllocation("generating, at load time")
public final class Geraniums {

    /** The petals' ramp: scarlet (0), pink (0.5), cerise (1); a box's cultivar picks its place. */
    private static final Ramp PETALS = new Ramp(new Albedo(0.55, 0.025, 0.03), new Albedo(0.72, 0.24, 0.36),
        new Albedo(0.46, 0.04, 0.2));
    /** Zonal leaves: their green (0 to 0.5) and the dark horseshoe band (1). */
    private static final Ramp ZONAL_LEAF = new Ramp(new Albedo(0.032, 0.075, 0.018), new Albedo(0.04, 0.085, 0.02),
        new Albedo(0.03, 0.035, 0.012));
    /** Ivy-leaf geraniums' leaves: a deeper, glossier green, a faint darker zone in the middle (1). */
    private static final Ramp IVY_LEAF = new Ramp(new Albedo(0.03, 0.08, 0.02), new Albedo(0.028, 0.065, 0.018),
        new Albedo(0.022, 0.045, 0.014));
    /** The leaves' undersides, darker and greyer. */
    private static final Ramp UNDERSIDE = Ramp.of(new Albedo(0.022, 0.04, 0.015));

    /** Geranium colors: the petals, pale centres, the leaves (zonal in the leaf row, ivy in the blade row). */
    private static final Palette PALETTE = Palette.DEFAULT.with(Part.PETAL, PETALS)
        .with(Part.CENTRE, Ramp.of(new Albedo(0.65, 0.5, 0.5)))
        .with(Part.LEAF, ZONAL_LEAF)
        .with(Part.BLADE, IVY_LEAF)
        .with(Part.FRUIT_ALT, UNDERSIDE)
        .with(Part.STALK, Ramp.of(new Albedo(0.06, 0.11, 0.03)))
        .with(Part.SEPAL, Ramp.of(new Albedo(0.05, 0.1, 0.03)));

    /** A zonal geranium's flower: five broad round petals, about 3 cm across. */
    static final FlowerShape ZONAL = new FlowerShape(FlowerShape.Form.SIMPLE, 5, 0.014, 0.013, FlowerShape.Tip.ROUND,
        0.12, 0, 5, 0.006, 0, 0, 0);
    /** An ivy-leaf geranium's flower: narrower petals, a little smaller. */
    static final FlowerShape IVY = new FlowerShape(FlowerShape.Form.SIMPLE, 5, 0.013, 0.009, FlowerShape.Tip.ROUND,
        0.2, 0, 5, 0.005, 0, 0, 0);

    /**
     * The most triangles a metre of box has at each detail (a shorter box a metre's): over it, the box is thinned
     * until it fits.
     */
    static final int[] BUDGET = {14000, 3000, 8, 0};

    private static final int MAX_POINTS = 16;

    private final MeshWriter writer = new MeshWriter();
    private final FloraWriter flora = new FloraWriter().target(writer);
    private final FloralState state = new FloralState();
    private final double[] points = new double[3 * MAX_POINTS];
    private int pointCount;

    /** The geraniums' colors: the base color texture of a building's {@code foliage} and {@code flowers} slots. */
    public static Palette palette() {
        return PALETTE;
    }

    /**
     * A box's geraniums: {@code length} along, {@code depth} across, at {@code detail} (0 to 3), from {@code seed};
     * {@code tall} for a balcony's box (taller plants, longer curtains), else a window's.
     */
    public ProceduralMesh box(double length, double depth, int detail, long seed, boolean tall) {
        int budget = (int) Math.ceil(BUDGET[Math.clamp(detail, 0, 3)] * Math.max(1, length));
        double thin = 1;
        for (int attempt = 0; ; attempt++) {
            ProceduralMesh mesh = box(length, depth, detail, seed, tall, thin);
            if (mesh.triangleCount() <= budget || attempt == 10) {
                return mesh;
            }
            thin *= 0.88; // over budget: fewer plants, stems, leaves and flowers, the same draws
        }
    }

    private ProceduralMesh box(double length, double depth, int detail, long seed, boolean tall, double thin) {
        writer.reset();
        Rng rng = Rng.seeded(seed);
        double fullness = (0.8 + 0.2 * rng.nextDouble()) * thin;
        double pick = rng.nextDouble();
        double zonalColour = pick < 0.6 ? 0.03 : pick < 0.88 ? 0.5 : 0.95;
        double ivyColour = rng.nextDouble() < 0.6 ? 0.55 + 0.35 * rng.nextDouble() : 0.05;
        boolean mixed = rng.nextDouble() < 0.2;
        if (detail >= 3) {
            writer.nextSubmesh();
            return writer.build(0);
        }
        if (detail == 2) {
            cards(length, depth, zonalColour, tall);
            return writer.build(0);
        }
        // zonal geraniums packed in two staggered rows, ivy-leaf ones along the front edge
        int zonal = Math.max(2, (int) Math.round(length / 0.11 * fullness));
        int ivy = Math.max(1, (int) Math.round(length / 0.17 * fullness));
        double height = (tall ? 0.2 : 0.16) * (0.9 + 0.2 * rng.nextDouble());
        long[] seeds = new long[zonal + ivy];
        double[] at = new double[zonal + ivy];
        double[] colour = new double[zonal + ivy];
        for (int i = 0; i < zonal + ivy; i++) {
            seeds[i] = rng.nextLong();
            boolean z = i < zonal;
            int n = z ? zonal : ivy;
            int k = z ? i : i - zonal;
            at[i] = Math.clamp(length * (k + 0.5) / n + (rng.nextDouble() - 0.5) * 0.04, 0.02, length - 0.02);
            colour[i] = z ? (mixed && k % 3 == 1 ? 0.5 : zonalColour) : ivyColour;
            colour[i] = Math.clamp(colour[i] + 0.06 * (rng.nextDouble() - 0.5), 0, 1);
        }
        // leaves and stems first (submesh 0), then the flowers (submesh 1), from the same draws
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                writer.nextSubmesh();
            }
            for (int i = 0; i < zonal + ivy; i++) {
                Rng plant = Rng.seeded(seeds[i]);
                if (i < zonal) {
                    double row = i % 2 == 0 ? -0.045 : 0.025;
                    zonal(plant, at[i], row, depth, height, fullness, colour[i], detail, pass == 1);
                } else {
                    ivy(plant, at[i], depth / 2 - 0.03, fullness, colour[i], detail, pass == 1, tall);
                }
            }
        }
        return writer.build(0);
    }

    /**
     * A zonal geranium at x along the box: a dense mound of round, scalloped leaves (8 to 12 cm across) leaning out
     * over the rim, stems rising through it, each with a ball-shaped umbel of 16 to 24 flowers just above it.
     */
    private void zonal(Rng rng, double x, double z, double depth, double height, double fullness, double colour,
        int detail, boolean flowers) {
        Vec3 foot = new Vec3(x, 0, z);
        int most = detail == 0 ? 18 : 8;
        int leaves = (int) Math.round(most * fullness);
        for (int k = 0; k < most; k++) {
            double azimuth = k * 2.399963 + 0.5 * (rng.nextDouble() - 0.5);
            double rise = 0.05 + 0.55 * rng.nextDouble();
            double out = 0.02 + 0.1 * rng.nextDouble();
            double up = 0.01 + 0.11 * rng.nextDouble();
            double radius = 0.04 + 0.02 * rng.nextDouble();
            double turn = rng.nextDouble();
            if (flowers || k >= leaves) {
                continue;
            }
            // most leaves lean toward the front and over the rim, as the mound spills out of the box
            Vec3 dir = new Vec3(Maths.cos(azimuth), 0, 0.7 * Maths.sin(azimuth) + 0.55).normalize();
            Vec3 base = foot.addScaled(dir, out).add(0, up, 0);
            Vec3 d = dir.scale(Maths.cos(rise)).add(0, Maths.sin(rise), 0).normalize();
            Vec3 normal = FloraWriter.turn(Vec3.UP, dir, 0.25 + 0.6 * rise).add(0, 0, 0.25);
            leaf(base, d, normal, radius, 0.3, Part.LEAF, detail, turn, true);
        }
        int stems = Math.max(1, (int) Math.round((3 + 2 * rng.nextDouble()) * fullness));
        for (int s = 0; s < stems; s++) {
            double azimuth = Maths.TAU * rng.nextDouble();
            double lean = 0.1 + 0.4 * rng.nextDouble();
            double h = height * (0.8 + 0.4 * rng.nextDouble());
            int count = detail == 0 ? 14 + rng.nextInt(7) : 8 + rng.nextInt(3);
            long umbelSeed = rng.nextLong();
            Vec3 dir = new Vec3(Maths.cos(azimuth), 0, 0.6 * Maths.sin(azimuth) + 0.3).normalize();
            Vec3 direction = FloraWriter.turn(Vec3.UP, dir, lean);
            Vec3 top = foot.addScaled(direction, h);
            if (!flowers) {
                flora.stalk(foot.add(0, 0.03, 0), top, 0.003, 3, 0);
            } else {
                ball(top, FloraWriter.turn(direction, Vec3.UP, 0.4), count, 0.034, ZONAL, colour, umbelSeed);
            }
        }
    }

    /**
     * An ivy-leaf geranium at the box's front: stems arching over the edge and hanging down under their weight, each
     * swaying its own way, leafy all along, umbels down their length.
     */
    private void ivy(Rng rng, double x, double z, double fullness, double colour, int detail, boolean flowers,
        boolean tall) {
        int stems = Math.max(1, (int) Math.round((3 + 3 * rng.nextDouble()) * fullness));
        for (int s = 0; s < stems; s++) {
            double hang = (tall ? 0.4 : 0.25) + (tall ? 0.4 : 0.25) * rng.nextDouble();
            double sideways = (rng.nextDouble() - 0.5) * 0.2;
            double reach = 0.04 + 0.05 * rng.nextDouble();
            double drift = 0.02 + 0.05 * rng.nextDouble();
            double sway = 0.01 + 0.04 * rng.nextDouble();
            double phase = Maths.TAU * rng.nextDouble();
            double waves = 0.6 + 0.9 * rng.nextDouble();
            long stemSeed = rng.nextLong();
            curtain(x, z, hang, sideways, reach, drift, sway, phase, waves, detail == 0 ? 9 : 5);
            Rng stem = Rng.seeded(stemSeed);
            if (!flowers) {
                flora.tube(points, pointCount, 0.0032, 0.0022, 3, Part.STALK, 0, null);
            }
            // leaves all along it, alternating, facing out (and a little up), smaller toward the tip
            double length = hang + reach + 0.05;
            int leaves = (int) Math.round(length / (detail == 0 ? 0.055 : 0.1)) + 1;
            for (int k = 0; k < leaves; k++) {
                double t = (k + 0.4) / leaves;
                double side = k % 2 == 0 ? 1 : -1;
                double angle = side * (0.6 + 0.7 * stem.nextDouble());
                double radius = (0.024 + 0.012 * stem.nextDouble()) * (1 - 0.35 * t);
                double turn = stem.nextDouble();
                if (flowers) {
                    continue;
                }
                Vec3 p = along(t);
                Vec3 d = new Vec3(Maths.sin(angle), -0.15 + 0.3 * stem.nextDouble(), 0.5 + 0.3 * Maths.cos(angle))
                    .normalize();
                leaf(p, d, new Vec3(0.2 * side, 0.45, 1), radius, 0.15, Part.BLADE, Math.max(detail, 1), turn, false);
            }
            // flowers in clusters down the stem, a cluster every 15 cm or so
            int umbels = Math.max(1, (int) Math.round(length / (detail == 0 ? 0.18 : 0.3)));
            for (int u = 0; u < umbels; u++) {
                double t = (u + 0.3 + 0.4 * stem.nextDouble()) / umbels;
                int count = detail == 0 ? 7 + stem.nextInt(5) : 4;
                long umbelSeed = stem.nextLong();
                if (flowers) {
                    ball(along(t).add(0, 0.01, 0.03), new Vec3(0, 0.3, 1).normalize(), count, 0.024, IVY, colour,
                        umbelSeed);
                }
            }
        }
    }

    /**
     * The path of a hanging stem into {@link #points}: from the soil at (x, z) out over the edge by {@code reach},
     * then down {@code hang} under its weight, drifting out by {@code drift}, swaying {@code sway} to the side in
     * {@code waves} waves from {@code phase}.
     */
    private void curtain(double x, double z, double hang, double sideways, double reach, double drift, double sway,
        double phase, double waves, int count) {
        pointCount = count;
        double edge = 0.25; // the share of the path over the edge
        for (int i = 0; i < count; i++) {
            double t = i / (double) (count - 1);
            double over = Math.min(1, t / edge);
            double down = Math.max(0, (t - edge) / (1 - edge));
            // gravity: the stem leaves the edge nearly level and soon hangs straight down
            double fall = down * (1.25 - 0.25 * down);
            double y = 0.04 + 0.04 * Maths.sin(Math.PI * over) - hang * fall;
            double zz = z + reach * Maths.sin(0.5 * Math.PI * over) + drift * Maths.sqrt(down);
            double xx = x + sideways * t + sway * down * Maths.sin(phase + Maths.TAU * waves * down);
            points[3 * i] = xx;
            points[3 * i + 1] = y;
            points[3 * i + 2] = zz;
        }
    }

    /** The point {@code t} (0 to 1) along the stem in {@link #points}. */
    private Vec3 along(double t) {
        double x = Math.clamp(t, 0, 1) * (pointCount - 1);
        int i = Math.min((int) x, pointCount - 2);
        double f = x - i;
        return new Vec3(Maths.lerp(points[3 * i], points[3 * i + 3], f),
            Maths.lerp(points[3 * i + 1], points[3 * i + 4], f), Maths.lerp(points[3 * i + 2], points[3 * i + 5], f));
    }

    /**
     * A leaf: a round, scalloped, cupped blade of {@code radius}, its stalk's end at {@code base}, lying along
     * {@code d}, its face toward {@code normal}; colored from {@code part}'s ramp: the green, a band at 0.55 of the
     * radius as dark as {@code band} says (the zonal geranium's horseshoe); a darker back sheet just behind it. Detail
     * 0: twelve scallops, the band and the back (30 triangles); 1: a hexagon, its back (12); ivy leaves are hexagons
     * at every level, small as they are.
     */
    private void leaf(Vec3 base, Vec3 d, Vec3 normal, double radius, double cup, Part part, int detail, double turn,
        boolean zonal) {
        Vec3 n = normal.sub(d.scale(normal.dot(d))).normalize();
        Vec3 side = n.cross(d).normalize();
        Vec3 centre = base.addScaled(d, 0.9 * radius);
        double pv = Palette.v(part);
        double green = Palette.u(0.3);
        double bandU = Palette.u(zonal ? 1.0 : 0.85);
        int rim = detail == 0 ? 12 : 6;
        int front;
        double start = turn * Maths.TAU / rim;
        if (detail == 0) {
            // the centre, a ring at the band, the scalloped rim
            front = vertex(centre.addScaled(n, -0.15 * cup * radius), n, d, green, pv);
            for (int i = 0; i < 6; i++) {
                double a = start + Maths.TAU * i / 6;
                Vec3 p = leafPoint(centre, d, side, n, a, 0.55 * radius, radius, cup);
                vertex(p, tilt(n, d, side, a, 0.3 * cup), d, bandU, pv);
            }
            for (int i = 0; i < rim; i++) {
                double a = start + Maths.TAU * i / rim;
                double r = radius * (i % 2 == 0 ? 1.0 : 0.88);
                vertex(leafPoint(centre, d, side, n, a, r, radius, cup), tilt(n, d, side, a, cup), d, green, pv);
            }
            for (int i = 0; i < 6; i++) {
                int b0 = front + 1 + i;
                int b1 = front + 1 + (i + 1) % 6;
                writer.triangle(front, b0, b1);
                int r0 = front + 7 + 2 * i;
                int r1 = front + 7 + 2 * i + 1;
                int r2 = front + 7 + (2 * i + 2) % rim;
                writer.triangle(b0, r0, r1);
                writer.triangle(b0, r1, b1);
                writer.triangle(b1, r1, r2);
            }
        } else {
            front = vertex(centre, n, d, green, pv);
            for (int i = 0; i < rim; i++) {
                double a = start + Maths.TAU * i / rim;
                vertex(leafPoint(centre, d, side, n, a, radius, radius, cup), tilt(n, d, side, a, cup), d, green, pv);
            }
            for (int i = 0; i < rim; i++) {
                writer.triangle(front, front + 1 + i, front + 1 + (i + 1) % rim);
            }
        }
        // the underside: a slightly smaller sheet just behind, facing away, darker
        double bu = Palette.u(0);
        double bv = Palette.v(Part.FRUIT_ALT);
        int sides = 6;
        Vec3 back = n.scale(-1);
        Vec3 behind = n.scale(-0.0012);
        int b = vertex(centre.addScaled(n, -0.15 * cup * radius).add(behind), back, d, bu, bv);
        for (int i = 0; i < sides; i++) {
            double a = start + Maths.TAU * i / sides;
            vertex(leafPoint(centre, d, side, n, a, 0.9 * radius, radius, cup).add(behind), back, d, bu, bv);
        }
        for (int i = 0; i < sides; i++) {
            writer.triangle(b, b + 1 + (i + 1) % sides, b + 1 + i);
        }
    }

    /** A point of a leaf at angle {@code a} and distance {@code r} from its centre, cupped: its rim raised. */
    private static Vec3 leafPoint(Vec3 centre, Vec3 d, Vec3 side, Vec3 n, double a, double r, double radius,
        double cup) {
        double s = r / radius;
        return centre.addScaled(d, r * Maths.cos(a)).addScaled(side, r * Maths.sin(a))
            .addScaled(n, cup * radius * (s * s - 0.15));
    }

    /** The normal of a cupped leaf at angle {@code a}: tilted in toward its middle. */
    private static Vec3 tilt(Vec3 n, Vec3 d, Vec3 side, double a, double amount) {
        return n.addScaled(d, -amount * Maths.cos(a)).addScaled(side, -amount * Maths.sin(a)).normalize();
    }

    private int vertex(Vec3 p, Vec3 n, Vec3 t, double u, double v) {
        Vec3 tangent = t.sub(n.scale(t.dot(n)));
        tangent = tangent.lengthSquared() > 1e-12 ? tangent.normalize() : n.anyPerpendicular().normalize();
        return writer.vertex(p.x(), p.y(), p.z(), n.x(), n.y(), n.z(), tangent.x(), tangent.y(), tangent.z(), u, v);
    }

    /**
     * An umbel as geraniums hold them: {@code count} flowers spreading over most of a ball about {@code facing} (a few
     * still buds), each facing out of it; single-triangle petals, as among so many they read.
     */
    private void ball(Vec3 at, Vec3 facing, int count, double radius, FlowerShape shape, double colour, long seed) {
        Rng rng = Rng.seeded(seed);
        Vec3 centre = at.addScaled(facing, 0.5 * radius);
        for (int f = 0; f < count; f++) {
            // a spiral over the cap, out to 1.7 radians from the umbel's axis
            double a = f * 2.399963 + 0.3 * rng.nextDouble();
            double c = 1 - (f + 0.5) / count * (1 - Maths.cos(1.7));
            double tilt = Maths.acos(Math.clamp(c, -1, 1));
            Vec3 d = FloraWriter.turn(facing, FloraWriter.around(facing, a), tilt);
            Vec3 p = centre.addScaled(d, radius * (0.85 + 0.3 * rng.nextDouble()));
            boolean bud = rng.nextDouble() < 0.1;
            double age = Math.clamp(colour + 0.06 * (rng.nextDouble() - 0.5), 0, 1);
            int hash = (int) rng.nextLong();
            double scale = 1.0 + 0.3 * rng.nextDouble();
            state.set(bud ? FloralState.Stage.BUD : FloralState.Stage.FLOWER, bud ? 0 : 1, bud ? 0.6 : 1, age, 0);
            flora.frame(p, d, FloraWriter.around(d, a)).flower(shape, state, PALETTE, scale, 2, 0, hash);
        }
    }

    /** Far: a curtain of leaves hanging over the front edge and a band of flowers above the box, facing out. */
    private void cards(double length, double depth, double colour, boolean tall) {
        double z = depth / 2 + 0.02;
        double hang = tall ? 0.55 : 0.35;
        card(0, length, -hang, 0.12, z, Part.BLADE, 0.3);
        writer.nextSubmesh();
        card(0.03, length - 0.03, 0.08, tall ? 0.24 : 0.2, z - 0.06, Part.PETAL, colour);
    }

    private void card(double x0, double x1, double y0, double y1, double z, Part part, double u) {
        double pu = Palette.u(u);
        double pv = Palette.v(part);
        int a = writer.vertex(x0, y0, z, 0, 0, 1, 1, 0, 0, pu, pv);
        int b = writer.vertex(x1, y0, z, 0, 0, 1, 1, 0, 0, pu, pv);
        int c = writer.vertex(x1, y1, z, 0, 0, 1, 1, 0, 0, pu, pv);
        int d = writer.vertex(x0, y1, z, 0, 0, 1, 1, 0, 0, pu, pv);
        writer.quad(a, b, c, d);
    }
}
