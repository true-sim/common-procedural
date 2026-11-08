package com.truesim.common.procedural.plant;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.random.Hash;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.flora.Albedo;
import com.truesim.common.procedural.flora.FloraSummary;
import com.truesim.common.procedural.flora.FloraWriter;
import com.truesim.common.procedural.flora.FloralState;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.flora.Phenology;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates small plants and clumps: a rosette (or a grass's blades) from the foot, stems leaning out and nodding over
 * with their leaves and side branches, and a flower or seed head on each, as the day says; snow presses them down.
 * Every level is meshed from the same draws, so levels differ only in detail. The same species, options and seed give
 * bit-identical meshes.
 *
 * <p><b>Wind</b> in the trees' format ({@link ProceduralVertex}): no trunk weight; every part sways about the foot
 * (pivot height 0, reach the plant's height) by its branch weight, {@code (s / height)^1.5} of its path {@code s}
 * from the foot; leaves and petals flutter by the leaf weight toward their tips; each stem and leaf has a phase of its
 * own, so a clump does not sway as one. Not thread-safe: one per thread ({@link Plants} keeps one per thread).
 */
public final class PlantGenerator {

    /** Version of the generated output: changes whenever the same inputs would give different meshes. */
    public static final int VERSION = 1;

    private static final int POINTS = 7;

    private final MeshWriter writer = new MeshWriter();
    private final MeshWriter cards = new MeshWriter();
    private final FloraWriter flora = new FloraWriter();
    private final FloralState state = new FloralState();
    private final FloraSummary.Builder summary = new FloraSummary.Builder();
    private final double[] points = new double[3 * POINTS];
    private final double[] weights = new double[POINTS];
    // Heads met while meshing the foliage: position, direction, branch weight, phase, hash
    private double[] heads = new double[8 * 32];
    private int[] headHash = new int[32];
    private int headCount;

    /** The plant generated from {@code seed}. */
    @AllowAllocation("the plant made")
    public Plant generate(PlantSpecies species, PlantOptions options, long seed) {
        Rng rng = Rng.seeded(Seeds.derive(seed, 0));
        double height = species.height() * (1 + species.heightVariation() * (2 * rng.nextDouble() - 1));
        List<ProceduralMesh> lods = new ArrayList<>(options.lods().size());
        double error = 0;
        FloraSummary flora = FloraSummary.NONE;
        for (int l = 0; l < options.lods().size(); l++) {
            PlantLod lod = options.lods().get(l);
            int keep = lod.keep();
            for (int attempt = 0; ; attempt++) {
                double levelError = Math.max(error, species.basal().length() * (Math.sqrt(keep) - 1)
                    + (lod.detail() > 0 ? 0.3 * lod.detail() * reach(species) : 0));
                ProceduralMesh mesh = mesh(species, options, seed, height, lod.detail(), keep, l == 0, levelError);
                if (mesh.triangleCount() <= lod.triangleBudget() || attempt == 12) {
                    if (l == 0) {
                        flora = summary.build();
                    }
                    error = levelError;
                    lods.add(mesh);
                    break;
                }
                keep += Math.max(1, keep / 2);
            }
        }
        ProceduralMesh full = lods.getFirst();
        double top = Math.max(full.bounds().max().y(), 1e-3);
        double radius = radius(full);
        return new Plant(species, seed, lods, billboard(top, radius), top, radius, flora, species.palette());
    }

    private static double reach(PlantSpecies species) {
        return species.flower() != null ? species.flower().reach() : species.seedHead().halfLength();
    }

    /** One level: the whole plant from the same draws at {@code detail}, one leaf in {@code keep}. */
    private ProceduralMesh mesh(PlantSpecies species, PlantOptions options, long seed, double height, int detail,
        int keep, boolean summarize, double error) {
        writer.reset();
        flora.target(writer);
        headCount = 0;
        Rng rng = Rng.seeded(Seeds.derive(seed, 1));
        double day = options.day();
        double snow = options.snowLoad();
        double reach = Math.max(height, 0.02);
        // The leaves' season: grown in spring, withering in autumn, dead through the winter
        double d = ((day % 365) + 365) % 365;
        boolean dormant = d < species.greenUp() - 25 || d >= species.senescence() + 60;
        double withered = dormant ? 1 : Math.clamp((d - species.senescence()) / 40, 0, 1);
        double grown = dormant ? 0.7 : 0.4 + 0.6 * Phenology.smooth(species.greenUp() - 25,
            species.greenUp(), d);
        double flatten = Math.min(0.9, 0.7 * snow + (dormant ? 0.5 : 0.35 * withered));
        Part foliage = species.grass() ? Part.BLADE : Part.LEAF;
        // Stems show from about a month before their buds until their leaves have withered
        Phenology p = species.phenology();
        double emerge = p.bloomStart() - p.budDays();
        double stemGrowth = dormant || d < emerge - 30 ? 0 : Phenology.smooth(emerge - 30, emerge, d);
        // Basal leaves or blades
        int basal = species.basal().minCount()
            + (int) (rng.nextDouble() * (species.basal().maxCount() - species.basal().minCount() + 1));
        for (int i = 0; i < basal; i++) {
            double azimuth = i * 2.399963 + 0.6 * (rng.nextDouble() - 0.5);
            double offset = species.spread() * 0.6 * Math.sqrt(rng.nextDouble());
            double rise = Math.max(0.03, species.basal().rise() + 0.35 * (rng.nextDouble() - 0.5));
            double length = species.basal().length() * (0.75 + 0.5 * rng.nextDouble()) * grown;
            double phase = rng.nextDouble();
            double twist = rng.nextDouble() - 0.5;
            if (Integer.remainderUnsigned(Hash.pcg(i), keep) != 0) {
                continue;
            }
            Vec3 out = new Vec3(Maths.cos(azimuth), 0, Maths.sin(azimuth));
            Vec3 foot = out.scale(offset);
            leaf(species, species.basal(), foot, out, rise * (1 - flatten), length, Math.min(Math.sqrt(keep), 1.6), 0, reach,
                phase, twist, detail, foliage, withered);
        }
        // Stems with their leaves, side branches and heads
        int stems = species.minStems() + (int) (rng.nextDouble() * (species.maxStems() - species.minStems() + 1));
        for (int k = 0; k < stems; k++) {
            double azimuth = Maths.TAU * rng.nextDouble();
            double offset = species.spread() * 0.4 * Math.sqrt(rng.nextDouble());
            double lean = species.stemLean() * rng.nextDouble();
            double length = height * (0.8 + 0.2 * rng.nextDouble()) * stemGrowth * (1 - 0.6 * snow);
            double phase = rng.nextDouble();
            int branches = species.branches() > 0 ? (int) (rng.nextDouble() * (species.branches() + 1)) : 0;
            long stemSeed = rng.nextLong();
            if (length < 1e-3) {
                continue;
            }
            Vec3 out = new Vec3(Maths.cos(azimuth), 0, Maths.sin(azimuth));
            stem(species, out.scale(offset), out, lean, length, reach, phase, branches, stemSeed, k, detail, keep,
                foliage, withered);
        }
        writer.nextSubmesh();
        heads(species, day, detail, summarize);
        writer.leafWeight(0);
        return writer.build(error);
    }

    /** A leaf (or a palmate or trifoliate one's leaflets) from {@code foot} toward {@code out}, rising. */
    private void leaf(PlantSpecies species, LeafSpec spec, Vec3 foot, Vec3 out, double rise, double length,
        double widen, double path, double reach, double phase, double twist, int detail, Part part, double withered) {
        // a leaf from the ground arches over at most to the level, never into the soil
        double archAt = foot.y() < 1e-6 ? Math.max(spec.arch(), -rise) : spec.arch();
        Vec3 direction = out.scale(Maths.cos(rise)).add(0, Maths.sin(rise), 0).normalize();
        Vec3 side = Vec3.UP.cross(out).normalize();
        Vec3 normal = direction.cross(side).normalize(); // the upper face, toward the sky and the plant's middle
        normal = FloraWriter.turn(normal, side, 0.4 * twist);
        Vec3 base = foot;
        double s = path;
        writer.wind(0, weight(s, reach), 0, phase, 0, reach);
        if (spec.petiole() > 0) {
            double petiole = spec.petiole() * length / spec.length();
            Vec3 stalkDirection = out.scale(Maths.cos(rise + 0.35)).add(0, Maths.sin(rise + 0.35), 0).normalize();
            Vec3 end = base.addScaled(stalkDirection, petiole);
            if (detail == 0) {
                points[0] = base.x();
                points[1] = base.y();
                points[2] = base.z();
                points[3] = end.x();
                points[4] = end.y();
                points[5] = end.z();
                weights[0] = weight(s, reach);
                weights[1] = weight(s + petiole, reach);
                flora.tube(points, 2, 0.0008, 0.0006, 3, part, withered, weights);
            }
            base = end;
            s += petiole;
        }
        int segments = detail == 0 ? (length > 0.12 ? 4 : 3) : detail == 1 ? 2 : 1;
        int leaflets = detail == 2 ? Math.min(spec.leaflets(), 3) : spec.leaflets();
        double fan = leaflets == 3 ? 0.75 : 0.5;
        double flutter = species.flutter();
        for (int j = 0; j < leaflets; j++) {
            double angle = leaflets == 1 ? 0 : (j - (leaflets - 1) / 2.0) * fan;
            Vec3 d = rotate(direction, normal, angle);
            double scale = leaflets == 1 ? 1 : 1 - 0.25 * Math.abs(angle);
            double l = length * scale;
            flora.strip(base, d, normal, l, spec.width() * widen * scale * Math.min(1, length / spec.length() + 0.3),
                spec.outline(), archAt, spec.fold(), segments, part, withered, 0, flutter, weight(s, reach),
                weight(s + l, reach));
        }
    }

    /** {@code v} turned {@code radians} about the unit {@code axis}. */
    private static Vec3 rotate(Vec3 v, Vec3 axis, double radians) {
        double c = Maths.cos(radians);
        double s = Maths.sin(radians);
        return v.scale(c).add(axis.cross(v).scale(s)).addScaled(axis, axis.dot(v) * (1 - c));
    }

    private static double weight(double path, double reach) {
        double s = Math.clamp(path / reach, 0, 1);
        return s * Math.sqrt(s);
    }

    /** A stem from {@code foot}, leaning toward {@code out}, nodding over, with its leaves, branches and heads. */
    private void stem(PlantSpecies species, Vec3 foot, Vec3 out, double lean, double length, double reach,
        double phase, int branches, long seed, int index, int detail, int keep, Part part, double withered) {
        int count = detail == 0 ? POINTS : detail == 1 ? 4 : 2;
        Vec3 direction = FloraWriter.turn(Vec3.UP, out, lean);
        Vec3 p = foot;
        double step = length / (count - 1);
        for (int i = 0; i < count; i++) {
            points[3 * i] = p.x();
            points[3 * i + 1] = p.y();
            points[3 * i + 2] = p.z();
            weights[i] = weight(step * i, reach);
            // nodding over toward the top, more the further up
            direction = FloraWriter.turn(direction, out, species.stemNod() * 2.0 * i / ((count - 1) * (count - 1)));
            p = p.addScaled(direction, step);
        }
        writer.wind(0, 0, 0, phase, 0, reach);
        int sides = detail == 0 ? 4 : 3;
        flora.tube(points, count, species.stemRadius(), 0.6 * species.stemRadius(), sides, part, withered, weights);
        Vec3 top = new Vec3(points[3 * count - 3], points[3 * count - 2], points[3 * count - 1]);
        Vec3 below = new Vec3(points[3 * count - 6], points[3 * count - 5], points[3 * count - 4]);
        Vec3 tangent = top.sub(below).normalize();
        int salt = (int) seed;
        // Leaves along the stem: opposite pairs when even, alternating otherwise
        LeafSpec spec = species.stemLeaves();
        int leaves = spec.minCount() + Integer.remainderUnsigned(Hash.pcg(salt), spec.maxCount() - spec.minCount() + 1);
        for (int j = 0; j < leaves; j++) {
            if (Integer.remainderUnsigned(Hash.pcg(salt + 31 * j), keep) != 0) {
                continue;
            }
            boolean pairs = leaves % 2 == 0;
            int level = pairs ? j / 2 : j;
            int levels = pairs ? leaves / 2 : leaves;
            double at = 0.2 + 0.45 * (level + 0.5) / levels;
            Vec3 point = along(count, at);
            double azimuth = Maths.TAU * Hash.random(salt, 5) + (pairs ? (j % 2) * Math.PI + level * 1.5708
                : j * 2.399963);
            Vec3 leafOut = new Vec3(Maths.cos(azimuth), 0, Maths.sin(azimuth));
            double size = 1 - 0.35 * at;
            leaf(species, spec, point, leafOut, spec.rise(), spec.length() * size, 1, at * length, reach,
                Hash.random(salt, 40 + j), Hash.random(salt, 60 + j) - 0.5, detail, part, withered);
        }
        addHead(top, tangent, weight(length, reach), phase, Hash.combine(salt, 1));
        // Side branches, each with a head
        for (int b = 0; b < branches; b++) {
            double at = 0.5 + 0.3 * Hash.random(salt, 80 + b);
            Vec3 from = along(count, at);
            double azimuth = Maths.TAU * Hash.random(salt, 90 + b);
            Vec3 sideways = new Vec3(Maths.cos(azimuth), 0, Maths.sin(azimuth));
            Vec3 d = FloraWriter.turn(tangent, sideways, 0.55);
            double l = length * (0.3 + 0.15 * Hash.random(salt, 100 + b));
            Vec3 mid = from.addScaled(d, 0.5 * l).add(0, 0.12 * l, 0);
            Vec3 end = from.addScaled(d, l).add(0, 0.3 * l, 0);
            points[0] = from.x();
            points[1] = from.y();
            points[2] = from.z();
            points[3] = mid.x();
            points[4] = mid.y();
            points[5] = mid.z();
            points[6] = end.x();
            points[7] = end.y();
            points[8] = end.z();
            weights[0] = weight(at * length, reach);
            weights[1] = weight(at * length + 0.5 * l, reach);
            weights[2] = weight(at * length + l, reach);
            flora.tube(points, 3, 0.7 * species.stemRadius(), 0.5 * species.stemRadius(), 3, part, withered,
                weights);
            addHead(end, end.sub(mid).normalize(), weights[2], phase, Hash.combine(salt, 2 + b));
        }
    }

    /** The point {@code at} (0 to 1) along the stem in {@link #points}. */
    private Vec3 along(int count, double at) {
        double x = at * (count - 1);
        int i = Math.min((int) x, count - 2);
        double t = x - i;
        return new Vec3(Maths.lerp(points[3 * i], points[3 * i + 3], t),
            Maths.lerp(points[3 * i + 1], points[3 * i + 4], t), Maths.lerp(points[3 * i + 2], points[3 * i + 5], t));
    }

    @AllowAllocation("growing, until warm")
    private void addHead(Vec3 at, Vec3 direction, double branch, double phase, int hash) {
        if (headCount == headHash.length) {
            heads = Arrays.copyOf(heads, 2 * heads.length);
            headHash = Arrays.copyOf(headHash, 2 * headHash.length);
        }
        headHash[headCount] = hash;
        int o = 8 * headCount++;
        heads[o] = at.x();
        heads[o + 1] = at.y();
        heads[o + 2] = at.z();
        heads[o + 3] = direction.x();
        heads[o + 4] = direction.y();
        heads[o + 5] = direction.z();
        heads[o + 6] = branch;
        heads[o + 7] = phase;
    }

    /** The flowers and seed heads on their stems' tips, in the flowers' submesh. */
    private void heads(PlantSpecies species, double day, int detail, boolean summarize) {
        if (summarize) {
            summary.clear();
        }
        Phenology p = species.phenology();
        for (int i = 0; i < headCount; i++) {
            int o = 8 * i;
            int hash = headHash[i];
            double phase = heads[o + 7];
            Vec3 at = new Vec3(heads[o], heads[o + 1], heads[o + 2]);
            Vec3 direction = FloraWriter.sink(new Vec3(heads[o + 3], heads[o + 4], heads[o + 5]), species.nodding());
            boolean sets = Hash.random(hash, 3) < species.fruitSet();
            p.state(day, Hash.random(hash, 4), Hash.random(hash, 5), Hash.random(hash, 6), sets, state);
            if (!state.shown()) {
                continue;
            }
            writer.wind(0, heads[o + 6], 0, phase, 0, Math.max(species.height(), 0.02));
            Vec3 across = FloraWriter.around(direction, Maths.TAU * Hash.random(hash, 7));
            double scale = 1 + 0.15 * (Hash.random(hash, 8) - 0.5);
            Albedo albedo;
            double area;
            if (state.stage() == FloralState.Stage.FRUIT || species.flower() == null) {
                boolean ripe = state.stage() == FloralState.Stage.FRUIT;
                double u = ripe ? 0.3 + 0.7 * state.ripeness() : 0.2 * state.open();
                double size = ripe ? 1 : 0.6 + 0.4 * state.size();
                flora.frame(at, direction, across).fruit(species.seedHead(), size * scale, u, Part.FRUIT, detail,
                    hash);
                albedo = species.palette().albedo(Part.FRUIT, u);
                double r = species.seedHead().radius() * size;
                area = Math.PI * r * r * species.seedHead().elongation();
            } else {
                flora.frame(at, direction, across).flower(species.flower(), state, species.palette(), scale, detail,
                    species.flutter(), hash);
                albedo = species.palette().albedo(Part.PETAL, state.age());
                double r = species.flower().reach() * state.size() * (0.5 + 0.5 * state.open());
                area = Math.PI * r * r;
            }
            if (summarize) {
                summary.add(state.stage() == FloralState.Stage.NONE ? FloralState.Stage.BUD : state.stage(), albedo,
                    area);
            }
        }
    }

    private static double radius(ProceduralMesh mesh) {
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        double max = 0;
        for (int i = 0; i < mesh.vertexCount(); i++) {
            max = Math.max(max, position.x(i) * position.x(i) + position.z(i) * position.z(i));
        }
        return Math.sqrt(max);
    }

    /** Three quads crossed at 60 degrees through the y axis, as tall and wide as the plant. */
    private ProceduralMesh billboard(double top, double radius) {
        cards.reset();
        double r = Math.max(radius, 1e-3);
        for (int q = 0; q < 3; q++) {
            double angle = Math.PI * q / 3;
            double cx = Maths.cos(angle);
            double cz = Maths.sin(angle);
            int first = -1;
            for (int k = 0; k < 4; k++) {
                double u = k == 1 || k == 2 ? 1 : 0;
                double v = k >= 2 ? 1 : 0;
                cards.wind(0, v * Math.sqrt(v), 0, 0, 0, top);
                int vertex = cards.vertex((2 * u - 1) * r * cx, v * top, (2 * u - 1) * r * cz, -cz, 0, cx, cx, 0, cz,
                    u, v);
                if (k == 0) {
                    first = vertex;
                }
            }
            cards.quad(first, first + 1, first + 2, first + 3);
        }
        return cards.build(r * 0.3);
    }
}
