package com.truesim.common.procedural.ecology.rocks;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.rock.RockCover;
import com.truesim.common.procedural.rock.RockParams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The scree between a talus' blocks, cheaply: small stones scattered densely over the field's talus cover
 * ({@link RockField#talus}), by a power law of sizes (many small, few large), with no thinning and no search for a
 * resting place (each lies along the ground's slope, cut flat a little into it). Laid out in world cells: each cell has
 * its stones (near the camera, each an instance of one of a few small variants) and a patch (farther away, one
 * instance of a {@link RockParams#SCREE_PATCH} model, several dozen stones in one, whose coarse levels serve far
 * away). The same cells and stones whatever region is asked.
 *
 * <pre>{@code
 * RockScatter scatter = new RockScatter(rocks, RockScatter.Parameters.DEFAULTS);
 * RockScatter.Tile tile = scatter.region(x0, z0, 256, 256);
 * for (int c = 0; c < tile.cells(); c++) {
 *     if (distance(tile.cellX(c), tile.cellZ(c)) < 60) {
 *         for (int s = tile.first(c); s < tile.end(c); s++) { ... tile.stones(): variant, x, y, z, rotation, scale }
 *     } else {
 *         ... tile.patches(), instance c
 *     }
 * }
 * }</pre>
 */
public final class RockScatter {

    private static final long SALT = 0x5c4e_e000L;

    /**
     * The scatter's tunables (common-procedural-assets' {@code RockSettings} declares them as engine settings).
     *
     * @param cell metres a side of a cell (a patch covers one)
     * @param density stones per square metre where the talus is full
     * @param minSize the smallest stone's size (mean radius), metres
     * @param maxSize the largest
     * @param fractal the power law: the number larger than a size falls as the size to the minus this
     * @param variants small stones' and patches' models each
     */
    public record Parameters(double cell, double density, double minSize, double maxSize, double fractal,
        int variants) {

        public static final Parameters DEFAULTS = new Parameters(1.1, 24, 0.02, 0.14, 2, 8);

        public Parameters {
            if (!(cell > 0) || !(density >= 0) || !(minSize > 0) || !(maxSize >= minSize) || variants < 1) {
                throw new IllegalArgumentException("A cell, a density, sizes and variants");
            }
        }
    }

    private final RockField field;
    private final EcologyTerrain terrain;
    private final Parameters parameters;
    private final long seed;
    private final List<RockVariant> stoneVariants;
    private final List<RockVariant> patchVariants;

    /** The scatter over {@code field}'s talus, on its ecology's terrain. */
    public RockScatter(RockField field, Parameters parameters) {
        this(field, parameters, field.ecology().terrain());
    }

    /**
     * The scatter over {@code field}'s talus, its stones on {@code ground}: the terrain as drawn, when it differs from
     * the field's (a {@code TalusModifier}'s roughness: small stones follow the drawn surface).
     */
    public RockScatter(RockField field, Parameters parameters, EcologyTerrain ground) {
        this.field = field;
        this.terrain = ground;
        this.parameters = parameters;
        this.seed = Seeds.derive(field.ecology().parameters().seed(), SALT);
        List<RockVariant> stones = new ArrayList<>();
        List<RockVariant> patches = new ArrayList<>();
        for (int v = 0; v < parameters.variants(); v++) {
            RockCover cover = new RockCover(0, 0.25 * (v % 3), 0, 0.15);
            stones.add(new RockVariant(RockParams.SCREE, Seeds.derive(seed, v),
                cover));
            patches.add(new RockVariant(RockParams.SCREE_PATCH, Seeds.derive(seed, 1000 + v), cover));
        }
        this.stoneVariants = List.copyOf(stones);
        this.patchVariants = List.copyOf(patches);
    }

    public Parameters parameters() {
        return parameters;
    }

    /** The small stones' models (scree), which {@link Tile#stones()} index. */
    public List<RockVariant> stoneVariants() {
        return stoneVariants;
    }

    /** The patches' models, which {@link Tile#patches()} index. */
    public List<RockVariant> patchVariants() {
        return patchVariants;
    }

    /** The cells, stones and patches whose cell's corner lies in [x0, x0 + width) x [z0, z0 + depth). */
    public Tile region(double x0, double z0, double width, double depth) {
        double c = parameters.cell();
        long i0 = (long) Math.ceil(x0 / c);
        long i1 = (long) Math.ceil((x0 + width) / c);
        long j0 = (long) Math.ceil(z0 / c);
        long j1 = (long) Math.ceil((z0 + depth) / c);
        Tile tile = new Tile(stoneVariants, patchVariants);
        double[] q = new double[4];
        double[] up = new double[3];
        for (long j = j0; j < j1; j++) {
            for (long i = i0; i < i1; i++) {
                double cx = (i + 0.5) * c;
                double cz = (j + 0.5) * c;
                double cover = field.talus(cx, cz);
                if (cover <= 0.02) {
                    continue;
                }
                long hash = Seeds.derive(seed, i * 0x1_0000_0001L ^ j * 0x7FFF_FFFFL);
                normal(cx, cz, c, up);
                // the patch: the cell's stones as one model, its cover thinning it by scale
                double patchYaw = unit(hash, 1) * Maths.TAU;
                lean(up, patchYaw, q);
                int patch = (int) (unit(hash, 2) * patchVariants.size());
                double patchScale = c / (2 * RockParams.SCREE_PATCH.cluster().spread()
                    * RockParams.SCREE_PATCH.size()) * (0.6 + 0.4 * cover);
                int start = tile.stones.count;
                // the stones: a Poisson number of them by the cover
                double mean = parameters.density() * c * c * cover;
                int count = poisson(mean, unit(hash, 3));
                for (int s = 0; s < count; s++) {
                    long h = Seeds.derive(hash, s);
                    double x = (i + unit(h, 1)) * c;
                    double z = (j + unit(h, 2)) * c;
                    double size = RockField.powerLaw(parameters.minSize(), parameters.maxSize(), parameters.fractal(),
                        unit(h, 3));
                    double yaw = unit(h, 4) * Maths.TAU;
                    lean(up, yaw, q);
                    int v = (int) (unit(h, 5) * stoneVariants.size());
                    double scale = size / RockParams.SCREE.size();
                    tile.stones.add(x, terrain.height(x, z), z, q, scale, v);
                }
                lean(up, patchYaw, q);
                tile.patches.add(cx, terrain.height(cx, cz), cz, q, patchScale, patch);
                tile.addCell(cx, cz, cover, start, tile.stones.count);
            }
        }
        return tile;
    }

    /** The ground's unit normal at (x, z) over {@code d} metres into {@code out}. */
    private void normal(double x, double z, double d, double[] out) {
        double gx = (terrain.height(x + 0.5 * d, z) - terrain.height(x - 0.5 * d, z)) / d;
        double gz = (terrain.height(x, z + 0.5 * d) - terrain.height(x, z - 0.5 * d)) / d;
        double l = Math.sqrt(gx * gx + 1 + gz * gz);
        out[0] = -gx / l;
        out[1] = 1 / l;
        out[2] = -gz / l;
    }

    /** The rotation turning by {@code yaw} about +y, then leaning +y onto {@code up}, into {@code q}. */
    private static void lean(double[] up, double yaw, double[] q) {
        double ux = up[0];
        double uy = up[1];
        double uz = up[2];
        double ax = uz;
        double az = -ux;
        double s = Math.sqrt(ax * ax + az * az);
        double lw = Math.sqrt(0.5 * (1 + uy));
        double half = Math.sqrt(Math.max(0, 0.5 * (1 - uy)));
        double lx = s > 1e-12 ? ax / s * half : 0;
        double lz = s > 1e-12 ? az / s * half : 0;
        double hy = Maths.sin(yaw / 2);
        double hw = Maths.cos(yaw / 2);
        q[0] = lx * hw - lz * hy;
        q[1] = lw * hy;
        q[2] = lx * hy + lz * hw;
        q[3] = lw * hw;
    }

    /** A Poisson number of mean {@code mean} from the uniform {@code u} (its inverse distribution). */
    static int poisson(double mean, double u) {
        double p = Maths.exp(-mean);
        double sum = p;
        int k = 0;
        while (u > sum && k < 10_000) {
            k++;
            p *= mean / k;
            sum += p;
            if (p < 1e-12 && k > mean) {
                break;
            }
        }
        return k;
    }

    private static double unit(long hash, int k) {
        return (Seeds.mix(hash + 0x9E3779B97F4A7C15L * k) >>> 11) * 0x1.0p-53;
    }

    /** Scattered instances: position, rotation, uniform scale of their variant's model, variant. */
    public static final class Placed {
        private int count;
        private double[] position = new double[3 * 256];
        private float[] rotation = new float[4 * 256];
        private float[] scale = new float[256];
        private int[] variant = new int[256];

        void add(double x, double y, double z, double[] q, double s, int v) {
            if (count == scale.length) {
                int n = 2 * count;
                position = Arrays.copyOf(position, 3 * n);
                rotation = Arrays.copyOf(rotation, 4 * n);
                scale = Arrays.copyOf(scale, n);
                variant = Arrays.copyOf(variant, n);
            }
            position[3 * count] = x;
            position[3 * count + 1] = y;
            position[3 * count + 2] = z;
            for (int k = 0; k < 4; k++) {
                rotation[4 * count + k] = (float) q[k];
            }
            scale[count] = (float) s;
            variant[count] = v;
            count++;
        }

        public int count() {
            return count;
        }

        public double x(int i) {
            return position[3 * i];
        }

        /** Its model's origin: the ground under it (the model cut flat a little below). */
        public double y(int i) {
            return position[3 * i + 1];
        }

        public double z(int i) {
            return position[3 * i + 2];
        }

        /** Its rotation (a unit quaternion x, y, z, w: model to world). */
        public float q(int i, int component) {
            return rotation[4 * i + component];
        }

        public float scale(int i) {
            return scale[i];
        }

        public int variant(int i) {
            return variant[i];
        }
    }

    /**
     * A region's scatter: per cell its centre, talus cover, its stones (a range of {@link #stones()}) and its patch
     * (instance {@code c} of {@link #patches()}).
     */
    public static final class Tile {
        private final List<RockVariant> stoneVariants;
        private final List<RockVariant> patchVariants;
        final Placed stones = new Placed();
        final Placed patches = new Placed();
        private int cells;
        private double[] centre = new double[2 * 64];
        private float[] cover = new float[64];
        private int[] range = new int[2 * 64];

        Tile(List<RockVariant> stoneVariants, List<RockVariant> patchVariants) {
            this.stoneVariants = stoneVariants;
            this.patchVariants = patchVariants;
        }

        void addCell(double x, double z, double c, int start, int end) {
            if (cells == cover.length) {
                int n = 2 * cells;
                centre = Arrays.copyOf(centre, 2 * n);
                cover = Arrays.copyOf(cover, n);
                range = Arrays.copyOf(range, 2 * n);
            }
            centre[2 * cells] = x;
            centre[2 * cells + 1] = z;
            cover[cells] = (float) c;
            range[2 * cells] = start;
            range[2 * cells + 1] = end;
            cells++;
        }

        /** How many cells hold scree. */
        public int cells() {
            return cells;
        }

        public double cellX(int c) {
            return centre[2 * c];
        }

        public double cellZ(int c) {
            return centre[2 * c + 1];
        }

        /** The talus cover at the cell's centre. */
        public float cover(int c) {
            return cover[c];
        }

        /** The first of cell {@code c}'s stones. */
        public int first(int c) {
            return range[2 * c];
        }

        /** The end (exclusive) of cell {@code c}'s stones. */
        public int end(int c) {
            return range[2 * c + 1];
        }

        /** Every cell's small stones, cell by cell; variants index {@link #stoneVariants()}. */
        public Placed stones() {
            return stones;
        }

        /** Every cell's patch, instance {@code c} for cell {@code c}; variants index {@link #patchVariants()}. */
        public Placed patches() {
            return patches;
        }

        public List<RockVariant> stoneVariants() {
            return stoneVariants;
        }

        public List<RockVariant> patchVariants() {
            return patchVariants;
        }
    }
}
