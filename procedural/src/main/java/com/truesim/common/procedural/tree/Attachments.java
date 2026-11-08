package com.truesim.common.procedural.tree;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Hash;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.flora.Albedo;
import com.truesim.common.procedural.flora.FloraSummary;
import com.truesim.common.procedural.flora.FloraWriter;
import com.truesim.common.procedural.flora.FloralState;
import com.truesim.common.procedural.flora.FlowerShape;
import com.truesim.common.procedural.flora.FruitShape;
import com.truesim.common.procedural.flora.Fruiting;
import com.truesim.common.procedural.flora.Inflorescence;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.flora.Phenology;
import com.truesim.common.procedural.mesh.MeshWriter;
import java.util.Arrays;

/**
 * A tree's flowers, fruits and cones: where they are borne (sites on the twigs, by the species' {@link Fruiting}
 * rules, the same on every day), and their meshes on a day. A site is one inflorescence: its flowers' positions,
 * stalks and dates follow from the site's own hash, so a flower is the same flower from bud to fruit, and its fruit
 * hangs where it flowered. Every vertex of a site takes the wind of the node it grows from (its branch's weights,
 * phase and pivot), so it sways with its branch; petals flutter by the leaf weight. Reused from tree to tree.
 */
final class Attachments {

    private static final int MAX_FLOWERS = 512;
    private static final Vec3 UP = Axes.UP;

    // Sites: the node, the position and the shoot's direction there, a hash
    private int count;
    private int[] node = new int[256];
    private int[] hash = new int[256];
    private double[] data = new double[6 * 256];

    private final FloralState state = new FloralState();
    private final FloraWriter flora = new FloraWriter();
    private final FloraSummary.Builder summary = new FloraSummary.Builder();
    // Per flower of the site being meshed
    private final FloralState.Stage[] stage = new FloralState.Stage[MAX_FLOWERS];
    private final double[] open = new double[MAX_FLOWERS];
    private final double[] size = new double[MAX_FLOWERS];
    private final double[] age = new double[MAX_FLOWERS];
    private final double[] ripeness = new double[MAX_FLOWERS];

    /** Sites placed last. */
    int count() {
        return count;
    }

    int node(int site) {
        return node[site];
    }

    /** The site's hash: its flowers' count, places and dates. */
    int hash(int site) {
        return hash[site];
    }

    Vec3 position(int site) {
        return new Vec3(data[6 * site], data[6 * site + 1], data[6 * site + 2]);
    }

    Vec3 direction(int site) {
        return new Vec3(data[6 * site + 3], data[6 * site + 4], data[6 * site + 5]);
    }

    /** Places the sites of {@code fruiting} on the skeleton's twigs (none for {@code null}). */
    void place(Skeleton skeleton, Fruiting fruiting, double height, Rng rng) {
        count = 0;
        if (fruiting == null || fruiting.density() <= 0) {
            return;
        }
        double low = fruiting.minHeight() * height;
        for (int b = 0; b < skeleton.branchCount(); b++) {
            if (skeleton.level(b) < fruiting.fromLevel()) {
                continue;
            }
            int first = skeleton.firstNode(b);
            int last = first + skeleton.nodeCount(b) - 1;
            if (last <= first) {
                continue;
            }
            if (fruiting.placement() == Fruiting.Placement.TIPS) {
                if (rng.nextDouble() < fruiting.density() && skeleton.y(last) >= low) {
                    add(skeleton, last, Axes.position(skeleton, last), Axes.tangent(skeleton, b, last), rng);
                }
                continue;
            }
            along(skeleton, fruiting, b, first, last, low, rng);
        }
    }

    /** Sites along the outer part of branch {@code b}, {@code density} per metre, off to its sides. */
    private void along(Skeleton skeleton, Fruiting fruiting, int b, int first, int last, double low, Rng rng) {
        double length = Axes.length(skeleton, b);
        double spacing = 1 / fruiting.density();
        double from = length * (1 - fruiting.tipFraction());
        double s = length - spacing * rng.nextDouble();
        int segmentEnd = last;
        double endArc = length;
        while (s >= from && segmentEnd > first) {
            int segmentStart = segmentEnd - 1;
            double segmentLength = Axes.distance(skeleton, segmentEnd, segmentStart);
            double startArc = endArc - segmentLength;
            if (s < startArc && segmentStart > first) {
                segmentEnd = segmentStart;
                endArc = startArc;
                continue;
            }
            double t = segmentLength > 0 ? Math.clamp((s - startArc) / segmentLength, 0, 1) : 0;
            Vec3 a = Axes.position(skeleton, segmentStart);
            Vec3 e = Axes.position(skeleton, segmentEnd);
            Vec3 p = a.interpolate(e, t);
            Vec3 tangent = segmentLength > 0 ? e.sub(a).scale(1 / segmentLength) : UP;
            // off to a side of the shoot, as spurs and axillary flowers grow
            Vec3 out = FloraWriter.turn(tangent, FloraWriter.around(tangent, Maths.TAU * rng.nextDouble()), 0.75);
            if (p.y() >= low) {
                add(skeleton, t > 0.5 ? segmentEnd : segmentStart, p, out, rng);
            }
            s -= spacing * (0.6 + 0.8 * rng.nextDouble());
        }
    }

    @AllowAllocation("growing, until warm")
    private void add(Skeleton skeleton, int at, Vec3 p, Vec3 direction, Rng rng) {
        if (count == node.length) {
            node = Arrays.copyOf(node, 2 * count);
            hash = Arrays.copyOf(hash, 2 * count);
            data = Arrays.copyOf(data, 12 * count);
        }
        node[count] = at;
        hash[count] = rng.nextInt();
        int o = 6 * count;
        data[o] = p.x();
        data[o + 1] = p.y();
        data[o + 2] = p.z();
        data[o + 3] = direction.x();
        data[o + 4] = direction.y();
        data[o + 5] = direction.z();
        count++;
    }

    /**
     * The crop's weight on {@code day}, 0 to 1: the expected share of flowers hanging as fruit, by their size cubed
     * (their mass): what bows fruit-laden boughs.
     */
    double load(Fruiting fruiting, double day) {
        if (fruiting == null || fruiting.weightDroop() <= 0) {
            return 0;
        }
        double sum = 0;
        int samples = 64;
        for (int i = 0; i < samples; i++) {
            int h = Hash.pcg(0x51ed27 + i);
            flowerState(fruiting, day, h, 0);
            if (state.stage() == FloralState.Stage.FRUIT) {
                sum += state.size() * state.size() * state.size();
            }
        }
        return sum / samples;
    }

    /** The state of flower {@code j} of the site of hash {@code h}, into {@link #state}. */
    private FloralState flowerState(Fruiting fruiting, double day, int h, int j) {
        Phenology p = fruiting.phenology();
        boolean sets = Hash.random(h, 13 + 4 * j) < fruiting.fruitSet();
        // A head flowers within about a week, the tree over its whole season: the head's time, then the flower's
        // (a tree flowers over the middle of its species' window: its heads within about half of it)
        double open = 0.25 + 0.4 * Hash.random(h, 9) + 0.1 * Hash.random(h, 10 + 4 * j);
        double ripen = 0.6 * Hash.random(h, 8) + 0.4 * Hash.random(h, 11 + 4 * j);
        return p.state(day, open, ripen, Hash.random(h, 12 + 4 * j), sets, state);
    }

    /** The flowers in the site of hash {@code h}: its stalks times their florets. */
    static int flowers(Inflorescence inflorescence, int h) {
        int span = inflorescence.maxFlowers() - inflorescence.minFlowers() + 1;
        int stalks = inflorescence.minFlowers() + Integer.remainderUnsigned(Hash.pcg(h), span);
        int florets = inflorescence.florets();
        return Math.min(MAX_FLOWERS / florets, stalks) * florets;
    }

    /** How far a level with attachments of {@code detail} may differ from the full ones, metres. */
    static double error(Fruiting fruiting, int detail) {
        if (fruiting == null || detail == 0) {
            return 0;
        }
        Inflorescence inflorescence = fruiting.inflorescence();
        double reach = fruiting.flower() == null ? fruiting.fruit().halfLength() : fruiting.flower().reach();
        double head = inflorescence.spread() * (inflorescence.arrangement() == Inflorescence.Arrangement.CLUSTER
            ? inflorescence.pedicel() : 1) + reach;
        return switch (detail) {
            case 1 -> 0.3 * reach;
            case 2 -> head;
            default -> head + inflorescence.peduncle() + inflorescence.pedicel();
        };
    }

    /**
     * Writes every site's flowers, fruits or cones on {@code day} into {@code writer} (the attachments submesh),
     * at {@code detail}; {@code alt} colors fruits as the species' second cultivar.
     */
    void mesh(MeshWriter writer, Fruiting fruiting, TreeWind wind, double day, int detail, boolean alt) {
        if (fruiting == null || detail >= TreeLod.NO_ATTACHMENTS) {
            return;
        }
        flora.target(writer);
        for (int i = 0; i < count; i++) {
            site(writer, fruiting, wind, day, detail, alt, i);
        }
        writer.leafWeight(0);
    }

    /** What the sites show on {@code day}: buds, flowers, fruits, their mean albedo and area. */
    FloraSummary summary(Fruiting fruiting, double day, boolean alt) {
        summary.clear();
        if (fruiting == null) {
            return summary.build();
        }
        Part fruitPart = fruiting.cones() ? Part.CONE : alt ? Part.FRUIT_ALT : Part.FRUIT;
        for (int i = 0; i < count; i++) {
            int k = states(fruiting, day, hash[i]);
            summarize(fruiting, fruiting.palette(), fruitPart, k);
        }
        return summary.build();
    }

    /** The states of the flowers of the site of hash {@code h} into the per-flower arrays; returns how many. */
    private int states(Fruiting fruiting, double day, int h) {
        int k = flowers(fruiting.inflorescence(), h);
        for (int j = 0; j < k; j++) {
            flowerState(fruiting, day, h, j);
            stage[j] = state.stage();
            open[j] = state.open();
            size[j] = state.size();
            age[j] = state.age();
            ripeness[j] = state.ripeness();
        }
        return k;
    }

    private void site(MeshWriter writer, Fruiting fruiting, TreeWind wind, double day, int detail, boolean alt,
        int i) {
        Inflorescence inflorescence = fruiting.inflorescence();
        int h = hash[i];
        int k = states(fruiting, day, h);
        int fruits = 0;
        int shown = 0;
        int blooming = 0;
        double fruitSize = 0;
        double ripe = 0;
        double ages = 0;
        for (int j = 0; j < k; j++) {
            if (stage[j] == FloralState.Stage.FRUIT) {
                fruits++;
                fruitSize += size[j];
                ripe += ripeness[j];
            } else if (stage[j] != FloralState.Stage.NONE) {
                blooming++;
                ages += age[j];
            }
            shown += stage[j] != FloralState.Stage.NONE ? 1 : 0;
        }
        if (shown == 0) {
            return;
        }
        double meanRipe = fruits > 0 ? ripe / fruits : 0;
        double meanAge = blooming > 0 ? ages / blooming : 0.5;
        double droop = inflorescence.fruitDroop() * fruits / (double) k * (fruits > 0 ? fruitSize / fruits : 0);
        Vec3 site = position(i);
        Vec3 axis = FloraWriter.sink(FloraWriter.turn(direction(i), UP, inflorescence.erect()), droop);
        Vec3 reference = FloraWriter.around(axis, Maths.TAU * Hash.random(h, 2));
        Vec3 across = axis.cross(reference);
        wind.applyNode(writer, node[i], 0);
        Palette palette = fruiting.palette();
        Part fruitPart = fruiting.cones() ? Part.CONE : alt ? Part.FRUIT_ALT : Part.FRUIT;
        double stalkU = fruits > 0 ? meanRipe : 0;
        double pedicelRadius = Math.max(0.0004, 0.015 * inflorescence.pedicel());
        if (detail == 2 && k > 3) {
            merged(fruiting, site, axis, reference, fruits, blooming, meanRipe, meanAge, fruitPart, detail, h);
            return;
        }
        if (detail <= 1 && inflorescence.peduncle() > 0 && k > 1) {
            flora.stalk(site, site.addScaled(axis, inflorescence.peduncle()), 1.6 * pedicelRadius, 3, stalkU);
        }
        FlowerShape flower = fruiting.flower();
        FruitShape fruit = fruiting.fruit();
        int florets = inflorescence.florets();
        int rays = k / florets;
        // a compound head's florets packed around their stalk's end
        double spacing = 1.7 * Math.max(flower == null ? 0 : flower.reach(), 1.1 * fruit.radius());
        for (int j = 0; j < rays; j++) {
            boolean any = false;
            boolean bearing = false;
            for (int m = 0; m < florets; m++) {
                any |= stage[j * florets + m] != FloralState.Stage.NONE;
                bearing |= stage[j * florets + m] == FloralState.Stage.FRUIT;
            }
            if (!any) {
                continue;
            }
            // Where the stalk starts, where its flowers sit, which way they face
            double fraction = Math.sqrt((j + 0.5) / rays);
            double angle = Maths.TAU * Hash.random(h, 3) + j * 2.399963;
            Vec3 radial = reference.scale(Maths.cos(angle)).addScaled(across, Maths.sin(angle));
            Vec3 base;
            Vec3 at;
            Vec3 facing;
            double pedicel = inflorescence.pedicel() * (0.85 + 0.3 * Hash.random(h, 400 + j));
            double peduncle = inflorescence.peduncle();
            switch (inflorescence.arrangement()) {
                case SOLITARY -> {
                    base = site;
                    facing = axis;
                    at = site.addScaled(axis, pedicel + peduncle);
                }
                case CLUSTER -> {
                    base = site.addScaled(axis, peduncle);
                    facing = FloraWriter.turn(axis, radial, inflorescence.spread() * (0.35 + 0.65 * fraction));
                    at = base.addScaled(facing, pedicel);
                }
                case UMBEL -> {
                    base = site.addScaled(axis, peduncle);
                    double r = inflorescence.spread() * fraction;
                    at = base.addScaled(axis, pedicel * (1 - 0.3 * fraction * fraction)).addScaled(radial, r);
                    facing = FloraWriter.turn(axis, radial, 0.5 * fraction);
                }
                case CORYMB -> {
                    double r = inflorescence.spread() * fraction;
                    // branching partway out, the lower branches longer: a flat top
                    base = site.addScaled(axis, peduncle * (0.25 + 0.75 * (1 - fraction))).addScaled(radial, 0.5 * r);
                    at = site.addScaled(axis, peduncle + pedicel).addScaled(radial, r);
                    facing = FloraWriter.turn(axis, radial, 0.4 * fraction);
                }
                default -> { // RACEME: along an axis, alternate sides
                    base = site.addScaled(axis, peduncle + inflorescence.spread() * j / Math.max(rays - 1, 1));
                    Vec3 sideways = j % 2 == 0 ? radial : radial.negate();
                    facing = FloraWriter.turn(axis, sideways, 1.0);
                    at = base.addScaled(facing, pedicel);
                }
            }
            facing = FloraWriter.sink(facing, inflorescence.nodding());
            if (detail == 0 && pedicel > 0) {
                flora.stalk(base, at, pedicelRadius, 3, bearing ? stalkU : 0);
            }
            Vec3 r1 = FloraWriter.around(facing, angle);
            Vec3 r2 = facing.cross(r1);
            if (detail >= 1 && florets > 1) {
                umbellet(fruiting, j * florets, florets, at, facing, r1, spacing, fruitPart, h + j);
                continue;
            }
            for (int m = 0; m < florets; m++) {
                int f = j * florets + m;
                if (stage[f] == FloralState.Stage.NONE) {
                    continue;
                }
                Vec3 point = at;
                if (m > 0) {
                    double a = m * 2.399963;
                    double d = 0.6 * spacing * Math.sqrt(m); // packed as a sunflower's seeds
                    point = at.addScaled(r1, d * Maths.cos(a)).addScaled(r2, d * Maths.sin(a))
                        .addScaled(facing, -0.25 * d);
                }
                boolean isFruit = stage[f] == FloralState.Stage.FRUIT;
                if (isFruit || flower == null) {
                    double variation = 1 + fruit.sizeVariation() * 2 * (Hash.random(h, 600 + f) - 0.5);
                    double grown = isFruit ? size[f] : 0.35 * size[f];
                    // a ripe fruit hangs from its stalk under its weight
                    double hang = fruit.hang() * (isFruit ? size[f] : 0.3);
                    Vec3 down = FloraWriter.sink(facing, hang * Maths.acos(Math.clamp(-facing.y(), -1, 1)));
                    double u = flower == null
                        ? (isFruit ? 0.25 + 0.25 * size[f] + 0.5 * ripeness[f] : 0) : ripeness[f];
                    flora.frame(point, down, reference).fruit(fruit, grown * variation, u, fruitPart, detail,
                        Hash.combine(h, f));
                } else {
                    double scale = 1 + 0.12 * (Hash.random(h, 500 + f) - 0.5);
                    state.set(stage[f], open[f], size[f], age[f], 0);
                    writer.leafWeight(0);
                    flora.frame(point, facing, reference).flower(flower, state, palette, scale, detail, 0.6,
                        Hash.combine(h, f));
                }
            }
        }
    }

    /**
     * A compound head's florets at one stalk's end as one shape (simplified levels): a flattened dome of blossom the
     * size of the little umbel, or a small blob of its berries.
     */
    private void umbellet(Fruiting fruiting, int first, int florets, Vec3 at, Vec3 facing, Vec3 across,
        double spacing, Part fruitPart, int seed) {
        int fruits = 0;
        int blooming = 0;
        double ripe = 0;
        double ages = 0;
        double grown = 0;
        for (int m = 0; m < florets; m++) {
            int f = first + m;
            if (stage[f] == FloralState.Stage.FRUIT) {
                fruits++;
                ripe += ripeness[f];
                grown += size[f];
            } else if (stage[f] != FloralState.Stage.NONE) {
                blooming++;
                ages += age[f];
                grown += size[f] * (0.5 + 0.5 * open[f]);
            }
        }
        int shown = fruits + blooming;
        if (shown == 0) {
            return;
        }
        double radius = 0.6 * spacing * Math.sqrt(florets - 1) + (fruiting.flower() == null
            ? fruiting.fruit().radius() : 0.8 * fruiting.flower().reach());
        double scale = Math.sqrt(shown / (double) florets) * grown / shown;
        if (blooming >= fruits) {
            FruitShape dome = new FruitShape(FruitShape.Form.BERRY, radius, 0.4, 0, 0, 0, 0, 0);
            flora.frame(at.addScaled(facing, -0.25 * radius), facing, across)
                .fruit(dome, scale, ages / blooming, Part.PETAL, 2, seed);
        } else {
            FruitShape fruit = fruiting.fruit();
            FruitShape blob = new FruitShape(FruitShape.Form.BERRY, radius, 0.9, 0, 0, 0, 0, 0);
            Vec3 down = FloraWriter.sink(facing, fruit.hang() * Maths.acos(Math.clamp(-facing.y(), -1, 1)));
            flora.frame(at, down, across).fruit(blob, scale, ripe / fruits, fruitPart, 2, seed);
        }
    }

    /** A head of many as one shape: a flattened dome of blossom, or a hanging blob of fruit. */
    private void merged(Fruiting fruiting, Vec3 site, Vec3 axis, Vec3 reference, int fruits, int blooming,
        double meanRipe, double meanAge, Part fruitPart, int detail, int h) {
        Inflorescence inflorescence = fruiting.inflorescence();
        double reach = fruiting.flower() == null ? fruiting.fruit().radius() : fruiting.flower().reach();
        double spread = inflorescence.arrangement() == Inflorescence.Arrangement.CLUSTER
            ? inflorescence.pedicel() * Maths.sin(inflorescence.spread()) : inflorescence.spread();
        Vec3 top = site.addScaled(axis, inflorescence.peduncle() + 0.6 * inflorescence.pedicel());
        if (blooming >= fruits) {
            FruitShape dome = new FruitShape(FruitShape.Form.BERRY, spread + 0.7 * reach, 0.3, 0, 0, 0, 0, 0);
            flora.frame(top.addScaled(axis, -0.3 * dome.radius()), axis, reference)
                .fruit(dome, 1, meanAge, Part.PETAL, detail, h);
        } else {
            FruitShape fruit = fruiting.fruit();
            FruitShape blob = new FruitShape(FruitShape.Form.BERRY, 0.7 * spread + fruit.radius(), 0.9, 0, 0, 0, 0, 0);
            Vec3 down = FloraWriter.sink(axis, fruit.hang() * Maths.acos(Math.clamp(-axis.y(), -1, 1)));
            flora.frame(top, down, reference).fruit(blob, Math.sqrt(fruits / (double) Math.max(fruits + blooming, 1)),
                meanRipe, fruitPart, detail, h);
        }
    }

    private void summarize(Fruiting fruiting, Palette palette, Part fruitPart, int k) {
        for (int j = 0; j < k; j++) {
            if (stage[j] == FloralState.Stage.NONE) {
                continue;
            }
            boolean isFruit = stage[j] == FloralState.Stage.FRUIT || fruiting.flower() == null;
            Albedo albedo;
            double area;
            if (isFruit) {
                double r = fruiting.fruit().radius() * size[j];
                area = Math.PI * r * r * fruiting.fruit().elongation();
                albedo = palette.albedo(fruitPart, fruiting.flower() == null ? 0.25 + 0.75 * ripeness[j]
                    : ripeness[j]);
            } else {
                double r = fruiting.flower().reach() * size[j] * (0.5 + 0.5 * open[j]);
                area = Math.PI * r * r;
                albedo = palette.albedo(Part.PETAL, age[j]);
            }
            summary.add(stage[j], albedo, area);
        }
    }
}
