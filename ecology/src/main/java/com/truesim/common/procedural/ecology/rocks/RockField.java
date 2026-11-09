package com.truesim.common.procedural.ecology.rocks;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.ecology.Ground;
import com.truesim.common.procedural.ecology.Obstacles;
import com.truesim.common.procedural.ecology.Site;
import com.truesim.common.procedural.rock.RockCover;
import com.truesim.common.procedural.rock.RockFootprint;
import com.truesim.common.procedural.rock.RockParams;
import com.truesim.common.procedural.rock.Rocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where rocks lie on a terrain, as its ecology sees it: talus cones at the cliffs' feet, rockfall run out onto the
 * meadows below them, boulders along streams, clearance piles at the meadows' edges and erratics on gentle ground;
 * each rock resting on the ground, leaning with it, its moss and lichen from its site and age. Streams in tiles like
 * the ecology (the same rocks whichever tiles come first), and keeps the ecology's plants off the rocks
 * ({@link Ecology#avoiding}).
 *
 * <pre>{@code
 * Ecology plain = new Ecology(terrain, EcologyParameters.ALPS.withSeed(7), AlpineSpecies.ALL);
 * RockField rocks = new RockField(plain, RockFieldParameters.DEFAULTS);
 * Ecology ecology = plain.avoiding(rocks);                 // the same conditions, plants off the rocks
 * RockTile tile = rocks.tile(3, -2);                       // the ecology's tile size
 * RockInstances here = tile.instances();                   // position, rotation, scale, kind, variant
 * float[] grass = tile.footprint(x0, z0, 0.5, 512, 512);   // 1 under rocks: for the grass' ground map
 * }</pre>
 *
 * <p><b>Cliffs above</b> ({@link RockFieldParameters.Cliffs}): from every field sample the steepest way up is climbed
 * over the terrain's heights; where it meets ground steeper than a cliff's slope, that is the cliff's foot, and where
 * it eases off again its top. A deposit lies below a cliff tall enough, within its <b>shadow angle</b>: rockfall stops
 * where the line from the cliff's top falls below about 27.5 degrees (Evans and Hungr 1993; the "Fahrboeschung" of
 * Heim 1932 is the steeper line from the source). A sample's place in the deposit, {@code t}, is 0 at the foot and 1
 * at the run-out's end, by the tangent of the line to the top. On ground between the talus slope and the angle of
 * repose the deposit is talus, below it run-out.
 *
 * <p><b>Size sorting:</b> on talus small stones stop near the apex and big blocks roll further (fall sorting: Statham
 * 1976, Kirkby and Statham 1975): a rock's mean size grows from its kind's smallest to its largest as
 * {@code t^sorting}, each rock's size spread about it (log-normally).
 *
 * <p><b>Placement</b> as the plants': a jittered candidate per world cell of each kind, its random numbers from the
 * seed and its cell, kept by its kind's density there, then a hard-core thinning (Matern's type II): a rock falls when
 * a bigger one lies within their radii added (times {@link RockFieldParameters#packing}). Every rock depends only on
 * candidates within a fixed halo, so tiles stream in any order and their seams match.
 *
 * <p><b>Active talus:</b> the upper share of a talus ({@link RockFieldParameters.Cliffs#activeTalus}), where rocks
 * still fall and the scree moves, takes no tree or shrub (a plant of crown radius above {@link RockTile#WOODY});
 * herbs and grass grow there, and forest takes the cone's lower part.
 *
 * <p><b>Resting:</b> a rock leans with the ground's normal ({@link RockFieldParameters#tilt}) and is lowered until no
 * point of its contact with the ground (its model's flat cut) lies above the terrain: it touches at its lowest point
 * and is sunk elsewhere.
 *
 * <p><b>Models:</b> rocks share a field's {@link #variants()}: a preset per kind (talus' small stones scree, its
 * blocks and slabs limestone blocks and slabs; run-out blocks and slabs; stream boulders; clearance piles; erratics),
 * a few seeds each, each grown with its moss and lichen (more on damp, shaded ground and old rocks; lichen on lit,
 * exposed ones; talus near its apex fresh), turned to one of a few headings (the moss on the side that ends up facing
 * north) and sunk to one of a few depths, each generated once.
 *
 * <p>Thread-safe.
 */
public final class RockField implements Obstacles {

    private static final long SALT = 0x0c0c_5a1e_0000L;
    /** Recent tiles, by region: an ecology's plants and the caller ask for the same region in turn. */
    private static final int RECENT = 16;

    private final Ecology ecology;
    private final EcologyTerrain terrain;
    private final RockFieldParameters parameters;
    private final long seed;
    private final List<RockParams> presets;
    private final List<RockVariant> variants;
    private final Map<Integer, Shape> shapes = new ConcurrentHashMap<>();
    private final Map<List<Double>, RockTile> recent = Collections.synchronizedMap(new LinkedHashMap<>(RECENT, 0.75f,
        true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<List<Double>, RockTile> eldest) {
            return size() > RECENT;
        }
    });
    private final double maxReach;
    private final double halo;
    private final double streamArea;

    /** The rocks of {@code ecology}'s terrain, placed as {@code parameters} say. */
    public RockField(Ecology ecology, RockFieldParameters parameters) {
        this.ecology = ecology;
        this.terrain = ecology.terrain();
        this.parameters = parameters;
        this.seed = Seeds.derive(ecology.parameters().seed(), SALT);
        this.presets = List.of(RockParams.SCREE, RockParams.BLOCK, RockParams.SLAB, RockParams.BOULDER,
            RockParams.CLUSTER, RockParams.ERRATIC);
        RockFieldParameters.Variants v = parameters.variants();
        List<RockVariant> all = new ArrayList<>();
        for (int p = 0; p < presets.size(); p++) {
            for (int s = 0; s < v.seeds(); s++) {
                long shape = Seeds.derive(seed, 1000L * p + s);
                for (int y = 0; y < v.yawSteps(); y++) {
                    double yaw = Maths.toRadians(360.0 * y / v.yawSteps()); // as a recipe in degrees gives
                    for (int m = 0; m < v.coverSteps(); m++) {
                        for (int l = 0; l < v.coverSteps(); l++) {
                            for (int b = 0; b < v.burialSteps(); b++) {
                                all.add(new RockVariant(presets.get(p), shape, new RockCover(level(m), level(l), yaw,
                                    burial(b))));
                            }
                        }
                    }
                }
            }
        }
        this.variants = List.copyOf(all);
        // the farthest reach: the largest size of a kind times its presets' reach for their size, with room for the
        // generated rocks' own (their footprints are measured, the estimate holds them within a few percent)
        double reach = 0;
        for (RockKind kind : RockKind.ALL) {
            for (RockParams preset : presets(kind)) {
                reach = Math.max(reach, 1.25 * parameters.kind(kind).maxSize() * preset.reach() / preset.size());
            }
        }
        this.maxReach = reach;
        this.halo = 2 * maxReach + 1;
        this.streamArea = ecology.parameters().hydrology().streamArea();
    }

    public Ecology ecology() {
        return ecology;
    }

    public RockFieldParameters parameters() {
        return parameters;
    }

    /** The models every instance indexes: the same list for every tile. */
    public List<RockVariant> variants() {
        return variants;
    }

    /** How far beyond a region the placement looks, metres. */
    public double halo() {
        return halo;
    }

    /** The farthest a rock reaches across the ground from its origin, about, metres. */
    public double maxReach() {
        return maxReach;
    }

    private double level(int step) {
        return step / (double) (parameters.variants().coverSteps() - 1);
    }

    private double burial(int step) {
        int steps = parameters.variants().burialSteps();
        return steps == 1 ? 0.15 : 0.05 + 0.3 * step / (steps - 1);
    }

    /** Tile ({@code tx}, {@code tz}) of the ecology's tile size. */
    public RockTile tile(long tx, long tz) {
        double t = ecology.parameters().tileSize();
        return region(tx * t, tz * t, t, t);
    }

    /** The rocks of any region: the same rocks as the tiles covering it. */
    public RockTile region(double x0, double z0, double width, double depth) {
        List<Double> key = List.of(x0, z0, width, depth);
        RockTile tile = recent.get(key);
        if (tile == null) {
            tile = compute(x0, z0, x0 + width, z0 + depth);
            recent.put(key, tile);
        }
        return tile;
    }

    @Override
    public Obstacles.Region over(double x0, double z0, double x1, double z1) {
        return region(x0, z0, x1 - x0, z1 - z0);
    }

    /**
     * The footprint of the rocks over a ground map ({@link RockTile#footprint}): {@code width} x {@code height}
     * texels of {@code texel} metres from ({@code originX}, {@code originZ}).
     */
    public float[] footprint(double originX, double originZ, double texel, int width, int height) {
        return region(originX, originZ, width * texel, height * texel).footprint(originX, originZ, texel, width,
            height);
    }

    /** Variant {@code index}'s footprint at its preset's size, from a coarse model of it (made once, cached). */
    public RockFootprint footprint(int index) {
        return shape(index).footprint();
    }

    /**
     * A variant's footprint and its contact with the ground: the outline of its coarse model at the ground (x, z
     * pairs), which must not lie above the terrain where it rests.
     */
    private record Shape(RockFootprint footprint, float[] contact) {
    }

    private Shape shape(int index) {
        return shapes.computeIfAbsent(index, i -> {
            RockVariant v = variants.get(i);
            com.truesim.common.procedural.rock.Rock coarse = Rocks.generate(v.params().withSubdivisions(List.of(3)),
                v.cover(), v.seed());
            float[] p = coarse.lods().getFirst().positions();
            // its outline at the ground: the farthest contact point in each of 48 sectors round its origin
            int sectors = 48;
            float[] contact = new float[2 * sectors];
            double[] far = new double[sectors];
            java.util.Arrays.fill(far, -1);
            for (int k = 0; k < p.length; k += 3) {
                if (p[k + 1] <= 0) {
                    double r = p[k] * p[k] + p[k + 2] * p[k + 2];
                    int sector = (int) ((Maths.atan2(p[k + 2], p[k]) / Maths.TAU + 0.5) * sectors) % sectors;
                    if (r > far[sector]) {
                        far[sector] = r;
                        contact[2 * sector] = p[k];
                        contact[2 * sector + 1] = p[k + 2];
                    }
                }
            }
            int n = 0;
            for (int sector = 0; sector < sectors; sector++) {
                if (far[sector] >= 0) {
                    contact[n++] = contact[2 * sector];
                    contact[n++] = contact[2 * sector + 1];
                }
            }
            return new Shape(coarse.footprint(), java.util.Arrays.copyOf(contact, n));
        });
    }

    // ------------------------------------------------------------------------------------------------ the fields

    /** Field samples' indices. */
    private static final int SLOPE = 0;
    private static final int T = 1;
    private static final int CLIFF = 2;
    private static final int MOISTURE = 3;
    private static final int INSOLATION = 4;
    private static final int EXPOSURE = 5;
    private static final int RIPARIAN = 6;
    private static final int MEADOW = 7;
    private static final int WATER = 8;
    private static final int STREAM = 9;
    private static final int SOIL = 10;
    private static final int FIELDS = 11;

    /** The fields over a region and its halo, at world-aligned samples. */
    private final class Fields {
        final long i0;
        final long j0;
        final int width;
        final int depth;
        final double cell;
        final float[][] f;
        // heights for the climb, with the search's margin
        final double climbCell;
        final long hi0;
        final long hj0;
        final int hw;
        final int hd;
        final float[] heights;

        Fields(double x0, double z0, double x1, double z1) {
            cell = parameters.cell();
            i0 = (long) Math.floor((x0 - halo) / cell) - 1;
            j0 = (long) Math.floor((z0 - halo) / cell) - 1;
            width = (int) ((long) Math.ceil((x1 + halo) / cell) + 2 - i0);
            depth = (int) ((long) Math.ceil((z1 + halo) / cell) + 2 - j0);
            // the climb's heights: twice as coarse, out to the search's reach
            climbCell = 2 * cell;
            int pad = (int) Math.ceil(parameters.cliffs().search() / climbCell) + 3;
            hi0 = (long) Math.floor(i0 * cell / climbCell) - pad;
            hj0 = (long) Math.floor(j0 * cell / climbCell) - pad;
            hw = (int) Math.ceil(width * cell / climbCell) + 2 + 2 * pad;
            hd = (int) Math.ceil(depth * cell / climbCell) + 2 + 2 * pad;
            heights = new float[hw * hd];
            java.util.stream.IntStream.range(0, hd).parallel().forEach(j -> {
                double z = (hj0 + j) * climbCell;
                for (int i = 0; i < hw; i++) {
                    heights[j * hw + i] = (float) terrain.altitude((hi0 + i) * climbCell, z);
                }
            });
            f = new float[FIELDS][width * depth];
            java.util.stream.IntStream.range(0, depth).parallel().forEach(j -> {
                Site site = new Site();
                double[] climb = new double[3];
                for (int i = 0; i < width; i++) {
                    double x = (i0 + i) * cell;
                    double z = (j0 + j) * cell;
                    ecology.conditions(x, z, site);
                    int g = j * width + i;
                    f[SLOPE][g] = (float) site.slope;
                    f[MOISTURE][g] = (float) site.moisture;
                    f[INSOLATION][g] = (float) site.insolation;
                    f[EXPOSURE][g] = (float) site.exposure;
                    f[RIPARIAN][g] = (float) site.riparian;
                    f[MEADOW][g] = (float) site.meadow;
                    f[WATER][g] = (float) site.water;
                    f[SOIL][g] = (float) site.ground[Ground.SOIL];
                    double bank = 4 + 1.5 * Math.sqrt(Math.max(0, site.streamArea) / 1e6);
                    f[STREAM][g] = site.streamArea >= streamArea ? (float) Math.max(0, 1 - site.streamDistance
                        / bank) : 0;
                    climb(x, z, climb);
                    f[T][g] = (float) climb[0];
                    f[CLIFF][g] = (float) climb[1];
                }
            });
        }

        /** The climb's heights, bilinear between world-aligned samples. */
        double height(double x, double z) {
            double gx = x / climbCell - hi0;
            double gz = z / climbCell - hj0;
            int i = Math.clamp((int) Math.floor(gx), 0, hw - 2);
            int j = Math.clamp((int) Math.floor(gz), 0, hd - 2);
            double fx = gx - i;
            double fz = gz - j;
            int a = j * hw + i;
            return (1 - fz) * ((1 - fx) * heights[a] + fx * heights[a + 1])
                + fz * ((1 - fx) * heights[a + hw] + fx * heights[a + hw + 1]);
        }

        /**
         * Climbs the steepest way up from (x, z) looking for a cliff: into {@code out} the place in its deposit
         * ({@code t}: 0 at its foot, 1 at the end of its run-out, above 1 beyond; NaN without a cliff) and its height.
         */
        void climb(double x, double z, double[] out) {
            RockFieldParameters.Cliffs c = parameters.cliffs();
            double step = climbCell;
            double cliffTan = Maths.tan(Maths.toRadians(c.slope()));
            double easeTan = Maths.tan(Maths.toRadians(c.slope() - 15));
            double h0 = height(x, z);
            double px = x;
            double pz = z;
            double travelled = 0;
            double footDistance = -1;
            double footHeight = 0;
            double topDistance = -1;
            double topHeight = 0;
            int steps = (int) (c.search() / step);
            for (int s = 0; s <= steps; s++) {
                double gx = (height(px + step, pz) - height(px - step, pz)) / (2 * step);
                double gz = (height(px, pz + step) - height(px, pz - step)) / (2 * step);
                double g = Math.sqrt(gx * gx + gz * gz);
                if (footDistance < 0 && g >= cliffTan) {
                    footDistance = travelled;
                    footHeight = height(px, pz);
                } else if (footDistance >= 0 && g < easeTan) {
                    topDistance = travelled;
                    topHeight = height(px, pz);
                    break;
                }
                if (g < 0.02) {
                    break; // a summit or a flat: nothing above
                }
                px += step * gx / g;
                pz += step * gz / g;
                travelled += step;
            }
            if (footDistance < 0) {
                out[0] = Double.NaN;
                out[1] = 0;
                return;
            }
            if (topDistance < 0) {
                topDistance = travelled;
                topHeight = height(px, pz);
            }
            double cliff = topHeight - footHeight;
            if (cliff < c.minHeight() || topDistance <= footDistance) {
                out[0] = Double.NaN;
                out[1] = cliff;
                return;
            }
            // the tangent of the line from the cliff's top: steep at its foot, the shadow angle at the run-out's end
            double apex = cliff / (topDistance - footDistance);
            double line = (topHeight - h0) / Math.max(topDistance, 1e-6);
            double shadow = Maths.tan(Maths.toRadians(c.shadowAngle()));
            out[0] = apex <= shadow ? Double.NaN : Math.max(0, (apex - line) / (apex - shadow));
            out[1] = cliff;
        }

        void weights(double x, double z, double[] w, int[] corner) {
            double gx = x / cell;
            double gz = z / cell;
            long gi = (long) Math.floor(gx);
            long gj = (long) Math.floor(gz);
            double fx = gx - gi;
            double fz = gz - gj;
            corner[0] = (int) (gj - j0) * width + (int) (gi - i0);
            w[0] = (1 - fx) * (1 - fz);
            w[1] = fx * (1 - fz);
            w[2] = (1 - fx) * fz;
            w[3] = fx * fz;
        }

        double sample(int field, double[] w, int a) {
            float[] v = f[field];
            if (field == T) {
                // NaN (no cliff) anywhere round: none
                if (Float.isNaN(v[a]) || Float.isNaN(v[a + 1]) || Float.isNaN(v[a + width])
                    || Float.isNaN(v[a + width + 1])) {
                    return Double.NaN;
                }
            }
            return w[0] * v[a] + w[1] * v[a + 1] + w[2] * v[a + width] + w[3] * v[a + width + 1];
        }
    }

    // ------------------------------------------------------------------------------------------------ placement

    private RockTile compute(double x0, double z0, double x1, double z1) {
        Fields fields = new Fields(x0, z0, x1, z1);
        Candidates all = new Candidates();
        double[] w = new double[4];
        int[] corner = new int[1];
        RockFieldParameters.Cliffs cliffs = parameters.cliffs();
        for (RockKind kind : RockKind.ALL) {
            RockFieldParameters.KindPlacement kp = parameters.kind(kind);
            if (kp.density() <= 0) {
                continue;
            }
            double g = kp.cell();
            double chance = kp.density() * g * g;
            long ci0 = (long) Math.floor((x0 - halo) / g);
            long ci1 = (long) Math.floor((x1 + halo) / g);
            long cj0 = (long) Math.floor((z0 - halo) / g);
            long cj1 = (long) Math.floor((z1 + halo) / g);
            long kindSeed = Seeds.derive(seed, kind.ordinal());
            for (long cj = cj0; cj <= cj1; cj++) {
                for (long ci = ci0; ci <= ci1; ci++) {
                    long hash = Seeds.derive(kindSeed, ci * 0x1_0000_0001L ^ cj * 0x7FFF_FFFFL);
                    double px = (ci + unit(hash, 1)) * g;
                    double pz = (cj + unit(hash, 2)) * g;
                    if (px < x0 - halo || px >= x1 + halo || pz < z0 - halo || pz >= z1 + halo) {
                        continue;
                    }
                    fields.weights(px, pz, w, corner);
                    int a = corner[0];
                    double slope = fields.sample(SLOPE, w, a);
                    double water = fields.sample(WATER, w, a);
                    if (slope > cliffs.reposeSlope()) {
                        continue;
                    }
                    double density;
                    double t = Double.NaN;
                    double age;
                    switch (kind) {
                        case TALUS -> {
                            t = fields.sample(T, w, a);
                            density = Double.isNaN(t) || t > 1 || slope < cliffs.talusSlope() ? 0
                                : (1 - 0.5 * t) * Maths.smoothstep(cliffs.minHeight(), 3 * cliffs.minHeight(),
                                    fields.sample(CLIFF, w, a)) * (1 - water);
                            age = 0.15 + 0.6 * (Double.isNaN(t) ? 0 : t);
                        }
                        case ROCKFALL -> {
                            t = fields.sample(T, w, a);
                            density = Double.isNaN(t) || t > 1 || slope >= cliffs.talusSlope() ? 0
                                : Maths.pow(1 - t, 1.2) * Maths.smoothstep(cliffs.minHeight(), 3 * cliffs.minHeight(),
                                    fields.sample(CLIFF, w, a)) * (1 - water);
                            age = 0.7;
                        }
                        case STREAM -> {
                            density = fields.sample(STREAM, w, a) * (1 - Maths.smoothstep(0.6, 0.95, water));
                            age = 0.8;
                        }
                        case CLEARANCE -> {
                            double meadow = fields.sample(MEADOW, w, a);
                            density = 4 * meadow * (1 - meadow) * Maths.smoothstep(0.1, 0.3, meadow)
                                * (1 - Maths.smoothstep(18, 26, slope)) * (1 - water);
                            age = 0.9;
                        }
                        default -> {
                            density = (1 - Maths.smoothstep(12, 22, slope)) * fields.sample(SOIL, w, a) * (1 - water);
                            age = 1;
                        }
                    }
                    if (unit(hash, 3) >= density * chance) {
                        continue;
                    }
                    // the size: sorted along the deposit (talus, run-out), else spread about the kind's middle
                    double along = Double.isNaN(t) ? 0.5 : Math.clamp(t, 0, 1);
                    double mean = kp.minSize() + (kp.maxSize() - kp.minSize()) * Maths.pow(along,
                        parameters.sorting());
                    double size = Math.clamp(mean * Maths.exp(parameters.spread() * gaussian(hash)), kp.minSize(),
                        kp.maxSize());
                    RockParams preset = preset(kind, size, unit(hash, 5));
                    // its cover: moss on damp, shaded ground and old rocks; lichen on lit, exposed ones
                    double moisture = fields.sample(MOISTURE, w, a);
                    double insolation = fields.sample(INSOLATION, w, a);
                    double exposure = fields.sample(EXPOSURE, w, a);
                    double riparian = fields.sample(RIPARIAN, w, a);
                    double moss = Math.clamp(age * (0.2 + 0.7 * Maths.smoothstep(4.5, 7.5, moisture))
                        * (1.15 - 0.6 * Math.max(0, insolation - 0.8)) + 0.3 * riparian, 0, 1);
                    double lichen = Math.clamp(age * (0.35 + 0.35 * exposure + 0.3 * Maths.smoothstep(0.85, 1.2,
                        insolation)), 0, 1);
                    double sunk = switch (kind) {
                        case TALUS -> 0.1 * unit(hash, 6);
                        case ROCKFALL, ERRATIC -> 0.15 + 0.25 * unit(hash, 6);
                        case STREAM -> 0.2 + 0.2 * unit(hash, 6);
                        default -> 0.15;
                    };
                    int variant = variant(preset, (int) (unit(hash, 7) * parameters.variants().seeds()),
                        (int) (unit(hash, 8) * parameters.variants().yawSteps()), moss, lichen, sunk);
                    double scale = size / preset.size();
                    double radius = footprint(variant).reach() * scale;
                    all.add(px, pz, radius, unit(hash, 9), kind.ordinal(), variant, scale, hash);
                }
            }
        }
        boolean[] kept = thin(all);
        RockInstances instances = new RockInstances(variants);
        RockInstances nearby = new RockInstances(variants);
        double[] q = new double[4];
        double[] rest = new double[1];
        for (int k = 0; k < all.count; k++) {
            if (!kept[k]) {
                continue;
            }
            double px = all.x[k];
            double pz = all.z[k];
            boolean inside = px >= x0 && px < x1 && pz >= z0 && pz < z1;
            boolean reaching = px >= x0 - maxReach && px < x1 + maxReach && pz >= z0 - maxReach
                && pz < z1 + maxReach;
            if (!reaching) {
                continue;
            }
            RockVariant variant = variants.get(all.variant[k]);
            Shape shape = shape(all.variant[k]);
            RockFootprint footprint = shape.footprint();
            double scale = all.scale[k];
            rest(px, pz, variant.cover().yaw(), shape, scale, q, rest);
            RockKind kind = RockKind.ALL[all.kind[k]];
            double hx = footprint.halfX() * scale;
            double hz = footprint.halfZ() * scale;
            nearby.add(px, rest[0], pz, q, scale, all.radius[k], hx, hz, kind, all.variant[k]);
            if (inside) {
                instances.add(px, rest[0], pz, q, scale, all.radius[k], hx, hz, kind, all.variant[k]);
            }
        }
        // active talus: the upper share of the deposits on talus ground, where no tree or shrub takes root
        int fw = fields.width;
        int fd = fields.depth;
        boolean[] active = new boolean[fw * fd];
        double share = cliffs.activeTalus();
        for (int g = 0; g < fw * fd; g++) {
            float t = fields.f[T][g];
            float slope = fields.f[SLOPE][g];
            active[g] = !Float.isNaN(t) && t <= share && slope >= cliffs.talusSlope() && slope <= cliffs.reposeSlope();
        }
        return new RockTile(x0, z0, x1, z1, instances, nearby, parameters.plantClearance(), new RockTile.Talus(
            fields.i0, fields.j0, fw, fd, fields.cell, active));
    }

    /** The presets rocks of {@code kind} take. */
    private static List<RockParams> presets(RockKind kind) {
        return switch (kind) {
            case TALUS -> List.of(RockParams.SCREE, RockParams.BLOCK, RockParams.SLAB);
            case ROCKFALL -> List.of(RockParams.BLOCK, RockParams.SLAB);
            case STREAM -> List.of(RockParams.BOULDER);
            case CLEARANCE -> List.of(RockParams.CLUSTER);
            case ERRATIC -> List.of(RockParams.ERRATIC);
        };
    }

    /** The preset of a rock of {@code kind} and {@code size}; {@code random} picks between blocks and slabs. */
    private static RockParams preset(RockKind kind, double size, double random) {
        return switch (kind) {
            case TALUS -> size < 0.45 ? RockParams.SCREE : random < 0.7 ? RockParams.BLOCK : RockParams.SLAB;
            case ROCKFALL -> random < 0.75 ? RockParams.BLOCK : RockParams.SLAB;
            case STREAM -> RockParams.BOULDER;
            case CLEARANCE -> RockParams.CLUSTER;
            case ERRATIC -> RockParams.ERRATIC;
        };
    }

    /** The index of the variant nearest to the given cover. */
    private int variant(RockParams preset, int shape, int heading, double moss, double lichen, double sunk) {
        RockFieldParameters.Variants v = parameters.variants();
        int p = presets.indexOf(preset);
        int m = (int) Math.round(moss * (v.coverSteps() - 1));
        int l = (int) Math.round(lichen * (v.coverSteps() - 1));
        int b = v.burialSteps() == 1 ? 0 : (int) Math.round(Math.clamp((sunk - 0.05) / 0.3, 0, 1)
            * (v.burialSteps() - 1));
        return ((((p * v.seeds() + shape) * v.yawSteps() + heading) * v.coverSteps() + m) * v.coverSteps() + l)
            * v.burialSteps() + b;
    }

    /**
     * How a rock at (x, z) rests: its rotation into {@code q} (its heading, then leaning toward the ground's normal by
     * the tilt) and its origin's height into {@code y}: as high as lets no point of its contact lie above the ground.
     */
    private void rest(double x, double z, double yaw, Shape shape, double scale, double[] q, double[] y) {
        RockFootprint footprint = shape.footprint();
        double hx = Math.max(footprint.halfX() * scale, 0.05);
        double hz = Math.max(footprint.halfZ() * scale, 0.05);
        double r = Math.max(hx, hz);
        // the ground's normal over the rock's contact
        double gx = (terrain.height(x + r, z) - terrain.height(x - r, z)) / (2 * r);
        double gz = (terrain.height(x, z + r) - terrain.height(x, z - r)) / (2 * r);
        double nx = -gx;
        double ny = 1;
        double nz = -gz;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double tilt = parameters.tilt();
        double ux = tilt * nx / nl;
        double uy = (1 - tilt) + tilt * ny / nl;
        double uz = tilt * nz / nl;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        // the lean: from +y to u about (y x u)
        double ax = uz;
        double az = -ux;
        double s = Math.sqrt(ax * ax + az * az);
        double lw = Math.sqrt(0.5 * (1 + uy));
        double lx = 0;
        double lz = 0;
        if (s > 1e-12) {
            double half = Math.sqrt(Math.max(0, 0.5 * (1 - uy)));
            lx = ax / s * half;
            lz = az / s * half;
        }
        // the heading about +y
        double hy = Maths.sin(yaw / 2);
        double hw = Maths.cos(yaw / 2);
        // q = lean * heading (lean has no y part, heading only y): (lx, 0, lz, lw) * (0, hy, 0, hw)
        q[0] = lw * 0 + lx * hw + 0 * 0 - lz * hy;
        q[1] = lw * hy - lx * 0 + 0 * hw + lz * 0;
        q[2] = lw * 0 + lx * hy - 0 * 0 + lz * hw;
        q[3] = lw * hw - lx * 0 - 0 * hy - lz * 0;
        // lower it until no point of its contact (its model's section at the ground) lies above the terrain
        double lowest = terrain.height(x, z);
        double[] p = new double[3];
        float[] contact = shape.contact();
        for (int k = 0; k < contact.length; k += 2) {
            rotate(q, contact[k] * scale, 0, contact[k + 1] * scale, p);
            double ground = terrain.height(x + p[0], z + p[2]);
            lowest = Math.min(lowest, ground - p[1]);
        }
        y[0] = lowest;
    }

    /** Rotates (x, y, z) by the unit quaternion {@code q} into {@code out}. */
    static void rotate(double[] q, double x, double y, double z, double[] out) {
        double qx = q[0];
        double qy = q[1];
        double qz = q[2];
        double qw = q[3];
        // t = 2 q.xyz x v; v' = v + w t + q.xyz x t
        double tx = 2 * (qy * z - qz * y);
        double ty = 2 * (qz * x - qx * z);
        double tz = 2 * (qx * y - qy * x);
        out[0] = x + qw * tx + (qy * tz - qz * ty);
        out[1] = y + qw * ty + (qz * tx - qx * tz);
        out[2] = z + qw * tz + (qx * ty - qy * tx);
    }

    /** Matern type II thinning: a candidate stays unless a bigger one lies within their radii added (packed). */
    private boolean[] thin(Candidates all) {
        int n = all.count;
        boolean[] kept = new boolean[n];
        if (n == 0) {
            return kept;
        }
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        double maxR = 0;
        for (int k = 0; k < n; k++) {
            minX = Math.min(minX, all.x[k]);
            minZ = Math.min(minZ, all.z[k]);
            maxX = Math.max(maxX, all.x[k]);
            maxZ = Math.max(maxZ, all.z[k]);
            maxR = Math.max(maxR, all.radius[k]);
        }
        double size = Math.max(2 * maxR, 1);
        int columns = (int) ((maxX - minX) / size) + 1;
        int rows = (int) ((maxZ - minZ) / size) + 1;
        int[] head = new int[columns * rows];
        int[] next = new int[n];
        java.util.Arrays.fill(head, -1);
        for (int k = 0; k < n; k++) {
            int b = (int) ((all.z[k] - minZ) / size) * columns + (int) ((all.x[k] - minX) / size);
            next[k] = head[b];
            head[b] = k;
        }
        double packing = parameters.packing();
        for (int k = 0; k < n; k++) {
            boolean stays = true;
            int ci = (int) ((all.x[k] - minX) / size);
            int cj = (int) ((all.z[k] - minZ) / size);
            search:
            for (int bj = Math.max(0, cj - 1); bj <= Math.min(rows - 1, cj + 1); bj++) {
                for (int bi = Math.max(0, ci - 1); bi <= Math.min(columns - 1, ci + 1); bi++) {
                    for (int o = head[bj * columns + bi]; o >= 0; o = next[o]) {
                        if (o == k || !dominates(all, o, k)) {
                            continue;
                        }
                        double dx = all.x[o] - all.x[k];
                        double dz = all.z[o] - all.z[k];
                        double reach = packing * (all.radius[o] + all.radius[k]);
                        if (dx * dx + dz * dz < reach * reach) {
                            stays = false;
                            break search;
                        }
                    }
                }
            }
            kept[k] = stays;
        }
        return kept;
    }

    private static boolean dominates(Candidates all, int a, int b) {
        if (all.radius[a] != all.radius[b]) {
            return all.radius[a] > all.radius[b];
        }
        if (all.priority[a] != all.priority[b]) {
            return all.priority[a] > all.priority[b];
        }
        return Long.compareUnsigned(all.hash[a], all.hash[b]) > 0;
    }

    /** A uniform number in [0, 1) from {@code hash}'s stream {@code k}. */
    private static double unit(long hash, int k) {
        return (Seeds.mix(hash + 0x9E3779B97F4A7C15L * k) >>> 11) * 0x1.0p-53;
    }

    /** A standard normal number from {@code hash} (Box-Muller). */
    private static double gaussian(long hash) {
        double u = Math.max(unit(hash, 10), 1e-12);
        double v = unit(hash, 11);
        return Math.sqrt(-2 * Maths.log(u)) * Maths.cos(Maths.TAU * v);
    }

    /** Placement candidates, as parallel arrays. */
    private static final class Candidates {
        int count;
        double[] x = new double[256];
        double[] z = new double[256];
        double[] radius = new double[256];
        double[] priority = new double[256];
        double[] scale = new double[256];
        int[] kind = new int[256];
        int[] variant = new int[256];
        long[] hash = new long[256];

        void add(double px, double pz, double r, double p, int k, int v, double s, long h) {
            if (count == x.length) {
                int n = 2 * count;
                x = java.util.Arrays.copyOf(x, n);
                z = java.util.Arrays.copyOf(z, n);
                radius = java.util.Arrays.copyOf(radius, n);
                priority = java.util.Arrays.copyOf(priority, n);
                scale = java.util.Arrays.copyOf(scale, n);
                kind = java.util.Arrays.copyOf(kind, n);
                variant = java.util.Arrays.copyOf(variant, n);
                hash = java.util.Arrays.copyOf(hash, n);
            }
            x[count] = px;
            z[count] = pz;
            radius[count] = r;
            priority[count] = p;
            kind[count] = k;
            variant[count] = v;
            scale[count] = s;
            hash[count] = h;
            count++;
        }
    }
}
