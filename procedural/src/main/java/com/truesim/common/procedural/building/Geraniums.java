package com.truesim.common.procedural.building;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.flora.Albedo;
import com.truesim.common.procedural.flora.FloraWriter;
import com.truesim.common.procedural.flora.FloralState;
import com.truesim.common.procedural.flora.FlowerShape;
import com.truesim.common.procedural.flora.Outline;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.flora.Ramp;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;

/**
 * The geraniums of Bernese balconies and window boxes, meshed by common-procedural's flora ({@link FloraWriter}):
 * zonal geraniums (Pelargonium zonale) standing in the box, round cupped leaves on stalks in a mound and umbels of
 * five-petalled flowers held above them on long stems; ivy-leaf geraniums (Pelargonium peltatum) along the front,
 * trailing over the edge and hanging down with their leaves and small umbels. Scarlet, pink or cerise by the seed, each
 * box as full as its seed says.
 *
 * <p>A box's mesh is in its own frame: x along the box from 0 to its length, y up from the soil, z out toward the
 * front edge (the box spans -depth/2 to depth/2). Submesh 0 the leaves and stems, 1 the flowers; both colored from
 * {@link #palette()} by texture coordinates (a building's {@code foliage} and {@code flowers} slots). Leaves and petals
 * are single sheets (two-sided materials). Detail 0: every flower, petals as strips; 1: fewer, simpler flowers and
 * leaves; 2: two cards (a band of leaves hanging over the edge, a band of flowers above); 3: nothing.
 */
@AllowAllocation("generating, at load time")
public final class Geraniums {

    /** The petals' ramp: scarlet (0), pink (0.5), cerise (1); a box's cultivar picks its place. */
    private static final Ramp PETALS = new Ramp(new Albedo(0.55, 0.025, 0.03), new Albedo(0.72, 0.24, 0.36),
        new Albedo(0.46, 0.04, 0.2));
    private static final Albedo LEAF = new Albedo(0.05, 0.12, 0.03);

    /** Geranium colors: petals as {@link #PETALS}, pale centres, fresh green leaves and stems. */
    private static final Palette PALETTE = Palette.DEFAULT.with(Part.PETAL, PETALS)
        .with(Part.CENTRE, Ramp.of(new Albedo(0.65, 0.5, 0.5)))
        .with(Part.LEAF, new Ramp(LEAF, new Albedo(0.06, 0.13, 0.03), new Albedo(0.04, 0.09, 0.02)))
        .with(Part.STALK, Ramp.of(new Albedo(0.08, 0.14, 0.04)))
        .with(Part.SEPAL, Ramp.of(new Albedo(0.07, 0.13, 0.035)));

    /** A zonal geranium's flower: five broad round petals, about 3 cm across. */
    static final FlowerShape ZONAL = new FlowerShape(FlowerShape.Form.SIMPLE, 5, 0.014, 0.013, FlowerShape.Tip.ROUND,
        0.12, 0, 5, 0.006, 0, 0, 0);
    /** An ivy-leaf geranium's flower: narrower petals, a little smaller. */
    static final FlowerShape IVY = new FlowerShape(FlowerShape.Form.SIMPLE, 5, 0.013, 0.009, FlowerShape.Tip.ROUND,
        0.2, 0, 5, 0.005, 0, 0, 0);

    private final MeshWriter writer = new MeshWriter();
    private final FloraWriter flora = new FloraWriter().target(writer);
    private final FloralState state = new FloralState();
    private final double[] points = new double[3 * 8];

    /** The geraniums' colors: the base color texture of a building's {@code foliage} and {@code flowers} slots. */
    public static Palette palette() {
        return PALETTE;
    }

    /**
     * A box's geraniums: {@code length} along, {@code depth} across, at {@code detail} (0 to 3), from {@code seed};
     * {@code tall} for a balcony's box (taller plants), else a window's.
     */
    public ProceduralMesh box(double length, double depth, int detail, long seed, boolean tall) {
        int budget = (int) Math.ceil(BUDGET[Math.clamp(detail, 0, 3)] * Math.max(1, length));
        double thin = 1;
        for (int attempt = 0; ; attempt++) {
            ProceduralMesh mesh = box(length, depth, detail, seed, tall, thin);
            if (mesh.triangleCount() <= budget || attempt == 8) {
                return mesh;
            }
            thin *= 0.85; // over budget: fewer plants, stems and flowers, the same draws
        }
    }

    /**
     * The most triangles a metre of box has at each detail (a shorter box a metre's): over it, the box is thinned
     * until it fits.
     */
    static final int[] BUDGET = {3200, 450, 8, 0};

    private ProceduralMesh box(double length, double depth, int detail, long seed, boolean tall, double thin) {
        writer.reset();
        Rng rng = Rng.seeded(seed);
        double fullness = (0.65 + 0.35 * rng.nextDouble()) * thin;
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
        // the zonal geraniums in a row along the back and middle, the ivy-leaf ones along the front
        int zonal = Math.max(1, (int) Math.round(length / 0.2 * fullness));
        int ivy = Math.max(1, (int) Math.round(length / 0.4 * fullness));
        double height = (tall ? 0.22 : 0.18) * (0.85 + 0.3 * rng.nextDouble());
        long[] seeds = new long[zonal + ivy];
        double[] at = new double[zonal + ivy];
        double[] colour = new double[zonal + ivy];
        for (int i = 0; i < zonal + ivy; i++) {
            seeds[i] = rng.nextLong();
            boolean z = i < zonal;
            int n = z ? zonal : ivy;
            int k = z ? i : i - zonal;
            at[i] = length * (k + 0.5) / n + (rng.nextDouble() - 0.5) * 0.06;
            colour[i] = z ? (mixed && k % 2 == 1 ? 0.5 : zonalColour) : ivyColour;
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
                    zonal(plant, at[i], -0.02 + 0.04 * plant.nextDouble() - 0.01, height, fullness, colour[i],
                        detail, pass == 1);
                } else {
                    ivy(plant, at[i], depth / 2 - 0.035, fullness, colour[i], detail, pass == 1, tall);
                }
            }
        }
        return writer.build(0);
    }

    /**
     * A zonal geranium at x along the box: a dense mound of round, cupped leaves (7 to 10 cm across), stems rising
     * through it, each with a ball-shaped umbel of 16 to 24 flowers.
     */
    private void zonal(Rng rng, double x, double z, double height, double fullness, double colour, int detail,
        boolean flowers) {
        Vec3 foot = new Vec3(x, 0, z);
        int most = detail == 0 ? 16 : 7;
        int leaves = (int) Math.round(most * fullness);
        for (int k = 0; k < most; k++) {
            double azimuth = k * 2.399963 + 0.5 * (rng.nextDouble() - 0.5);
            double rise = 0.1 + 0.65 * rng.nextDouble();
            double out = 0.02 + 0.08 * rng.nextDouble();
            double up = 0.04 + 0.1 * rng.nextDouble() * (1 - 0.5 * rise);
            double size = 0.08 + 0.04 * rng.nextDouble();
            double tone = 0.25 * rng.nextDouble();
            if (flowers || k >= leaves) {
                continue;
            }
            Vec3 dir = new Vec3(Maths.cos(azimuth), 0, 0.75 * Maths.sin(azimuth) + 0.35).normalize();
            Vec3 d = dir.scale(Maths.cos(rise)).add(0, Maths.sin(rise), 0).normalize();
            Vec3 base = foot.addScaled(dir, out).add(0, up, 0);
            Vec3 side = Vec3.UP.cross(dir).normalize();
            // facing up and out of the box, toward whoever looks at it
            Vec3 normal = FloraWriter.turn(d.cross(side).normalize(), Vec3.UP, 0.35).add(0, 0, 0.7);
            normal = normal.sub(d.scale(normal.dot(d))).normalize();
            flora.strip(base, d, normal, size, size * 1.15, Outline.ROUND, -0.35, 0.25, detail == 0 ? 2 : 1,
                Part.LEAF, tone, 0, 0, Double.NaN, Double.NaN);
        }
        int stems = Math.max(1, (int) Math.round((2 + 2 * rng.nextDouble()) * fullness));
        for (int s = 0; s < stems; s++) {
            double azimuth = Maths.TAU * rng.nextDouble();
            double lean = 0.05 + 0.35 * rng.nextDouble();
            double h = height * (0.85 + 0.35 * rng.nextDouble());
            int count = detail == 0 ? 16 + rng.nextInt(9) : 7 + rng.nextInt(3);
            long umbelSeed = rng.nextLong();
            Vec3 dir = new Vec3(Maths.cos(azimuth), 0, 0.6 * Maths.sin(azimuth)).normalize();
            Vec3 direction = FloraWriter.turn(Vec3.UP, dir, lean);
            Vec3 top = foot.addScaled(direction, h);
            if (!flowers) {
                flora.stalk(foot.add(0, 0.02, 0), top, 0.0032, 3, 0);
            } else {
                ball(top, FloraWriter.turn(direction, Vec3.UP, 0.5), count, 0.034, ZONAL, colour, umbelSeed, detail);
            }
        }
    }

    /**
     * An ivy-leaf geranium at the box's front: stems arching over the edge and hanging down, dense with shield-shaped
     * leaves, umbels of 8 to 12 flowers along them.
     */
    private void ivy(Rng rng, double x, double z, double fullness, double colour, int detail, boolean flowers,
        boolean tall) {
        int stems = Math.max(1, (int) Math.round((3 + 2 * rng.nextDouble()) * fullness));
        for (int s = 0; s < stems; s++) {
            double sideways = (rng.nextDouble() - 0.5) * 0.4;
            double drop = (tall ? 0.25 : 0.15) + (tall ? 0.35 : 0.2) * rng.nextDouble() * fullness;
            double reach = 0.06 + 0.06 * rng.nextDouble();
            long stemSeed = rng.nextLong();
            // out of the soil, over the edge, then down
            int n = detail == 0 ? 6 : 3;
            for (int i = 0; i < n; i++) {
                double t = i / (double) (n - 1);
                double over = Math.min(1, t * 2.5);
                double down = Math.max(0, t - 0.3) / 0.7;
                points[3 * i] = x + sideways * t;
                points[3 * i + 1] = 0.04 + 0.05 * Maths.sin(Math.PI * over) - drop * down * down;
                points[3 * i + 2] = z + reach * over + 0.04 * down;
            }
            Rng stem = Rng.seeded(stemSeed);
            if (!flowers) {
                flora.tube(points, n, 0.003, 0.0022, 3, Part.STALK, 0, null);
            }
            // leaves all along it, alternating, facing out and up
            int leaves = detail == 0 ? 9 : 4;
            for (int k = 0; k < leaves; k++) {
                double t = (k + 0.5) / leaves;
                Vec3 p = along(n, t);
                double azimuth = (k % 2 == 0 ? 1 : -1) * (0.5 + 0.7 * stem.nextDouble());
                double size = 0.045 + 0.025 * stem.nextDouble();
                if (flowers) {
                    continue;
                }
                Vec3 d = new Vec3(Maths.sin(azimuth), 0.3, Maths.cos(azimuth) * 0.5 + 0.25).normalize();
                Vec3 normal = new Vec3(0, 0.35, 1).normalize();
                normal = normal.sub(d.scale(normal.dot(d))).normalize();
                flora.strip(p, d, normal, size, size * 1.2, Outline.ROUND, 0.2, 0.2, 1, Part.LEAF, 0.15, 0, 0,
                    Double.NaN, Double.NaN);
            }
            // an umbel by the edge, another down the stem
            int umbels = detail == 0 ? 2 : 1;
            for (int u = 0; u < umbels; u++) {
                Vec3 p = along(n, u == 0 ? 0.3 : 0.75);
                long umbelSeed = stem.nextLong();
                int count = detail == 0 ? 8 + stem.nextInt(5) : 5;
                if (flowers) {
                    ball(p.add(0, 0.025, 0.02), new Vec3(0, 0.6, 1).normalize(), count, 0.026, IVY, colour,
                        umbelSeed, detail);
                }
            }
        }
    }

    /** The point {@code t} (0 to 1) along the stem in {@link #points}. */
    private Vec3 along(int count, double t) {
        double x = t * (count - 1);
        int i = Math.min((int) x, count - 2);
        double f = x - i;
        return new Vec3(Maths.lerp(points[3 * i], points[3 * i + 3], f),
            Maths.lerp(points[3 * i + 1], points[3 * i + 4], f), Maths.lerp(points[3 * i + 2], points[3 * i + 5], f));
    }

    /**
     * An umbel as geraniums hold them: {@code count} flowers on short pedicels from {@code at}, spreading over most of a
     * ball about {@code facing} (a few still buds), each facing out of it; single-triangle petals, as close up they
     * read among so many.
     */
    private void ball(Vec3 at, Vec3 facing, int count, double radius, FlowerShape shape, double colour, long seed,
        int detail) {
        Rng rng = Rng.seeded(seed);
        Vec3 centre = at.addScaled(facing, 0.5 * radius);
        for (int f = 0; f < count; f++) {
            // a spiral over the cap, out to 1.6 radians from the umbel's axis
            double a = f * 2.399963 + 0.3 * rng.nextDouble();
            double c = 1 - (f + 0.5) / count * (1 - Maths.cos(1.6));
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

    /** Far: a band of leaves hanging over the front edge and a band of flowers above the box, facing out. */
    private void cards(double length, double depth, double colour, boolean tall) {
        double z = depth / 2 + 0.01;
        double hang = tall ? 0.3 : 0.18;
        card(0, length, -hang, 0.1, z, Part.LEAF, 0.1);
        writer.nextSubmesh();
        card(0.03, length - 0.03, 0.08, tall ? 0.24 : 0.18, z - 0.06, Part.PETAL, colour);
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
