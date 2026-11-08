package com.truesim.common.procedural.preview;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.EcologyTile;
import com.truesim.common.procedural.ecology.GroundCover;
import com.truesim.common.procedural.ecology.Instances;
import com.truesim.common.procedural.ecology.Layer;
import com.truesim.common.procedural.ecology.Species;
import com.truesim.common.procedural.ecology.terrain.TerrainEcology;
import com.truesim.common.procedural.tree.TreeSpecies;
import com.truesim.common.procedural.tree.Trees;
import com.truesim.effects.terrain.data.SurfaceParameters;
import com.truesim.effects.terrain.data.TerrainSite;
import com.truesim.effects.terrain.data.TerrainSurface;
import com.truesim.engine.render.GeometryPool;
import com.truesim.engine.vulkan.VulkanDevice;
import com.truesim.engine.vulkan.VulkanInstance;
import com.truesim.engine.vulkan.testing.TestGpu;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.assets.mesh.VertexAttribute;
import com.truesim.scene.assets.mesh.VertexFormat;
import com.truesim.scene.core.ShadowSpec;
import com.truesim.scene.meshbuild.MeshBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A flight up Lauterbrunnen's western side, from the valley floor by the Weisse Luetschine over the cliffs and the
 * Muerren terrace to the Schilthorn, through the belts the ecology grows: the terrain (effects-terrain's surface from
 * its cache) coloured by the ecology's ground cover (meadow, forest floor, heath, scree, rock, snow, wetland, water),
 * and its trees and shrubs placed as common-procedural's trees, each species its own preset (by its id). Dwarf shrubs, herbs and grass show only as the ground's colour (no vegetation renderer yet).
 * {@code TRUESIM_FILMS=true ./gradlew -Ptruesim.preview=true -Ptruesim.engine=../engine-vegetation :preview:test
 * --tests '*EcologyFlight*' -Pfilms=<dir>}; {@code TRUESIM_SCREENSHOTS=true} draws three stills on a validated
 * device.
 */
class EcologyFlight {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int SUPER = 2;
    /** The near area: fields every 8 m and the plants. */
    private static final double X0 = -7168;
    private static final double Z0 = -512;
    private static final double SIZE = 8192;
    private static final double CELL = 8;
    /** The far terrain round it: fields every 32 m. */
    private static final double FAR_X0 = -28672;
    private static final double FAR_Z0 = -20480;
    private static final double FAR_SIZE = 49152;
    private static final double FAR_CELL = 32;

    /** Keyframes: x, z, height (absolute, or above ground where negative), seconds. */
    private static final double[][] PATH = {
        {-60, 250, -22, 0},
        {-330, 1500, -45, 6},
        {-760, 2750, 1180, 12},
        {-1250, 3650, 1790, 18},
        {-2450, 3880, 2150, 24},
        {-3800, 3950, 2460, 30},
        {-5150, 3975, 3060, 36},
        {-5560, 3980, 3150, 40},
        {-5560, 3980, 3150, 47},
    };

    /** The preset of each woody species: every tree and shrub the ecology names has a generator now. */
    private static final Map<String, TreeSpecies> PRESETS = presets();

    private static Map<String, TreeSpecies> presets() {
        Map<String, TreeSpecies> map = new java.util.HashMap<>();
        for (TreeSpecies species : TreeSpecies.PRESETS) {
            map.put(species.name(), species);
        }
        return Map.copyOf(map);
    }

    private TerrainSurface surface;
    private TerrainEcology ground;

    private static TerrainSurface lauterbrunnen() throws IOException {
        String given = System.getProperty("truesim.terrain.cache");
        Path cache = given != null ? Path.of(given) : Path.of("..", "..", "effects-terrain", "terrain-cache");
        Path folder = cache.resolve("sites").resolve("lauterbrunnen");
        Path file = folder.resolve("site.terrain");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            TerrainSite site = TerrainSite.read(in, name -> Files.newInputStream(folder.resolve(name)));
            return new TerrainSurface(site, new SurfaceParameters(2.5, 64, 6, 1.5, true));
        }
    }

    /** A plant to draw: its tree's meshes and materials, and where. */
    record Plant(int kind, double x, double y, double z, double yaw, double sy, double sxz) {
    }

    /** What the flight shows: terrain meshes placed, plants with their LOD state. */
    private final class World {
        final Stage stage;
        final List<ProceduralFilms.Placed> kinds = new ArrayList<>();
        final List<Plant> plants = new ArrayList<>();
        int[] ids;
        int[] levels;

        World(Stage stage) {
            this.stage = stage;
        }
    }

    private World build(Stage stage) throws Exception {
        surface = lauterbrunnen();
        assumeTrue(surface != null, "effects-terrain's cache holds no Lauterbrunnen");
        ground = TerrainEcology.of(surface, 2);
        World world = new World(stage);
        EcologyParameters.Placement placement = EcologyParameters.Placement.DEFAULTS
            .withInstances(Layer.DWARF_SHRUB, false).withInstances(Layer.HERB, false);
        EcologyParameters near = EcologyParameters.ALPS.withLandUse(EcologyParameters.LandUse.MEADOWS)
            .withTiles(512, CELL).withPlacement(placement).withSeed(11);
        Ecology ecology = new Ecology(ground, near, AlpineSpecies.ALL);
        EcologyParameters.Placement none = placement.withInstances(Layer.CANOPY, false)
            .withInstances(Layer.SHRUB, false);
        EcologyParameters far = near.withTiles(2048, FAR_CELL).withPlacement(none)
            .withAnalysis(new EcologyParameters.Analysis(32, 8192, 8192, 300, 60, 16));
        Ecology farEcology = new Ecology(ground, far, AlpineSpecies.ALL);
        long start = System.nanoTime();
        Ecology fieldsOnly = new Ecology(ground, near.withPlacement(none), AlpineSpecies.ALL);
        List<EcologyTile> nearTiles = tiles(ecology, fieldsOnly, X0, Z0, SIZE, 512);
        List<EcologyTile> farTiles = tiles(farEcology, farEcology, FAR_X0, FAR_Z0, FAR_SIZE, 2048);
        System.out.printf(Locale.ROOT, "ecology: %d near and %d far tiles in %.1f s%n", nearTiles.size(),
            farTiles.size(), (System.nanoTime() - start) / 1e9);
        int terrain = stage.vertexColorMaterial(0.92f);
        terrainMeshes(stage, terrain, farTiles, FAR_X0, FAR_Z0, FAR_SIZE, FAR_CELL, 2048, true);
        terrainMeshes(stage, terrain, nearTiles, X0, Z0, SIZE, CELL, 512, false);
        // the plants: two variants of each preset in use
        Map<Integer, Integer> kindIndex = new HashMap<>();
        List<Species> species = ecology.species();
        int[] kindOf = new int[species.size() * 2];
        double[] heightOf = new double[species.size() * 2];
        for (int s = 0; s < species.size(); s++) {
            TreeSpecies preset = PRESETS.get(species.get(s).id());
            for (int v = 0; v < 2; v++) {
                if (preset == null) {
                    kindOf[2 * s + v] = -1;
                    continue;
                }
                int key = TreeSpecies.PRESETS.indexOf(preset) * 2 + v;
                if (!kindIndex.containsKey(key)) {
                    world.kinds.add(ProceduralFilms.load(stage, Trees.generate(preset, 31 + 7 * v)));
                    kindIndex.put(key, world.kinds.size() - 1);
                }
                kindOf[2 * s + v] = kindIndex.get(key);
                heightOf[2 * s + v] = world.kinds.get(kindOf[2 * s + v]).tree().height();
            }
        }
        for (EcologyTile tile : nearTiles) {
            Instances plants = tile.instances();
            for (int i = 0; i < plants.count(); i++) {
                int s = plants.species(i);
                int v = (int) (plants.variant(i) & 1);
                int kind = kindOf[2 * s + v];
                if (kind < 0) {
                    continue;
                }
                double scale = species.get(s).habit().height() / heightOf[2 * s + v];
                world.plants.add(new Plant(kind, plants.x(i), plants.y(i), plants.z(i), plants.yaw(i),
                    plants.height(i) * scale, plants.width(i) * scale));
            }
        }
        world.ids = new int[world.plants.size()];
        world.levels = new int[world.plants.size()];
        java.util.Arrays.fill(world.levels, -1);
        for (int p = 0; p < world.ids.length; p++) {
            world.ids[p] = stage.id();
        }
        System.out.printf(Locale.ROOT, "%d plants of %d kinds%n", world.plants.size(), world.kinds.size());
        return world;
    }

    /** The distance from (x, z) to the flight's path, metres. */
    private static double fromPath(double x, double z) {
        double best = Double.MAX_VALUE;
        for (int k = 0; k + 1 < PATH.length; k++) {
            double ax = PATH[k][0];
            double az = PATH[k][1];
            double bx = PATH[k + 1][0] - ax;
            double bz = PATH[k + 1][1] - az;
            double l = bx * bx + bz * bz;
            double t = l == 0 ? 0 : Math.clamp(((x - ax) * bx + (z - az) * bz) / l, 0, 1);
            double dx = x - ax - t * bx;
            double dz = z - az - t * bz;
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    /**
     * The ecology's tiles over a square, on all threads: from {@code plants} (with instances) near the path, from
     * {@code fields} (the same fields, no instances) elsewhere.
     */
    private static List<EcologyTile> tiles(Ecology plants, Ecology fields, double x0, double z0, double size,
        double tile) throws Exception {
        int count = (int) (size / tile);
        List<EcologyTile> all = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            List<Future<EcologyTile>> results = new ArrayList<>();
            for (int j = 0; j < count; j++) {
                for (int i = 0; i < count; i++) {
                    double x = x0 + i * tile;
                    double z = z0 + j * tile;
                    Ecology ecology = fromPath(x + tile / 2, z + tile / 2) < 1100 ? plants : fields;
                    results.add(pool.submit(() -> ecology.region(x, z, tile, tile)));
                }
            }
            for (Future<EcologyTile> result : results) {
                all.add(result.get());
            }
        }
        return all;
    }

    /** The linear colour of the ground from a sample's cover. */
    private static void groundColor(EcologyTile tile, int a, float[] out, int at) {
        double temperature = tile.field(EcologyTile.Field.TEMPERATURE)[a];
        double alpine = Math.clamp((13 - temperature) / 9, 0, 1);
        double[][] colors = {
            {0.05 + 0.04 * alpine, 0.11 - 0.025 * alpine, 0.012 + 0.012 * alpine}, // meadow, valley to alpine
            {0.025, 0.032, 0.014}, // forest floor
            {0.055, 0.04, 0.03}, // heath
            {0.15, 0.145, 0.135}, // scree
            {0.1, 0.095, 0.09}, // rock
            {0.78, 0.8, 0.85}, // snow
            {0.05, 0.07, 0.03}, // wetland
            {0.02, 0.04, 0.05}, // water
        };
        double r = 0;
        double g = 0;
        double b = 0;
        for (GroundCover c : GroundCover.values()) {
            double w = tile.cover(c)[a];
            r += w * colors[c.ordinal()][0];
            g += w * colors[c.ordinal()][1];
            b += w * colors[c.ordinal()][2];
        }
        out[at] = (float) r;
        out[at + 1] = (float) g;
        out[at + 2] = (float) b;
        out[at + 3] = 1;
    }

    /**
     * The terrain as meshes, one per tile, every vertex at a field sample with the ground's colour; the far terrain
     * leaves out what the near covers (overlapping it by a sample and lowered 4 m there, hiding cracks).
     */
    private void terrainMeshes(Stage stage, int material, List<EcologyTile> tiles, double x0, double z0, double size,
        double cell, double tile, boolean far) {
        for (EcologyTile t : tiles) {
            int n = t.samples();
            float[] positions = new float[3 * n * n];
            float[] normals = new float[3 * n * n];
            float[] colors = new float[4 * n * n];
            boolean[] skip = new boolean[n * n];
            double[] normal = new double[3];
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    int a = j * n + i;
                    double x = t.x0() + i * cell;
                    double z = t.z0() + j * cell;
                    boolean inside = far && x > X0 + cell && x < X0 + SIZE - cell && z > Z0 + cell
                        && z < Z0 + SIZE - cell;
                    skip[a] = inside;
                    double y = surface.heightAt(x, z, cell) - (far && x >= X0 - cell && x <= X0 + SIZE + cell
                        && z >= Z0 - cell && z <= Z0 + SIZE + cell ? 4 : 0);
                    positions[3 * a] = (float) (i * cell);
                    positions[3 * a + 1] = (float) y;
                    positions[3 * a + 2] = (float) (j * cell);
                    surface.normalAt(x + 0.25 * cell, z + 0.25 * cell, cell, normal);
                    normals[3 * a] = (float) normal[0];
                    normals[3 * a + 1] = (float) normal[1];
                    normals[3 * a + 2] = (float) normal[2];
                    groundColor(t, a, colors, 4 * a);
                }
            }
            List<Integer> indices = new ArrayList<>();
            for (int j = 0; j < n - 1; j++) {
                for (int i = 0; i < n - 1; i++) {
                    int a = j * n + i;
                    if (skip[a] && skip[a + 1] && skip[a + n] && skip[a + n + 1]) {
                        continue;
                    }
                    indices.addAll(List.of(a, a + n, a + 1, a + 1, a + n, a + n + 1));
                }
            }
            if (indices.isEmpty()) {
                continue;
            }
            MeshData mesh = new MeshBuilder(VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.COLOR), n * n)
                .positions(positions).attribute(VertexAttribute.NORMAL, normals)
                .attribute(VertexAttribute.COLOR, colors)
                .submesh(indices.stream().mapToInt(Integer::intValue).toArray()).buildFlat();
            int id = stage.addMesh(mesh);
            stage.set(stage.id(), id, material, t.x0(), 0, t.z0(), 0, 1, 1, 1);
        }
    }

    /** The camera's position at {@code t} seconds: a Catmull-Rom spline through the keyframes, above the ground. */
    private double[] camera(double t) {
        int k = 0;
        while (k < PATH.length - 2 && PATH[k + 1][3] <= t) {
            k++;
        }
        double u = Math.clamp((t - PATH[k][3]) / (PATH[k + 1][3] - PATH[k][3]), 0, 1);
        double[] out = new double[3];
        for (int c = 0; c < 3; c++) {
            int e = c; // x, z, then the height
            double p0 = value(Math.max(k - 1, 0), e);
            double p1 = value(k, e);
            double p2 = value(k + 1, e);
            double p3 = value(Math.min(k + 2, PATH.length - 1), e);
            out[c] = 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u
                + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
        }
        return out;
    }

    private double value(int k, int e) {
        if (e == 2 && PATH[k][2] < 0) {
            return surface.heightAt(PATH[k][0], PATH[k][1], 2) - PATH[k][2];
        }
        return PATH[k][e];
    }

    /** The camera's heights over the flight, kept 12 m above the ground within 25 m, smoothed. */
    private double[] heights(int frames, double fps) {
        double[] y = new double[frames];
        for (int f = 0; f < frames; f++) {
            double[] p = camera(f / fps);
            double floor = 0;
            for (int k = 0; k < 9; k++) {
                double dx = k == 0 ? 0 : 25 * Math.cos(k * Math.PI / 4);
                double dz = k == 0 ? 0 : 25 * Math.sin(k * Math.PI / 4);
                floor = Math.max(floor, surface.heightAt(p[0] + dx, p[1] + dz, 2) + 12);
            }
            y[f] = Math.max(p[2], floor);
        }
        // a running maximum over two seconds either side, then a mean over one second
        double[] lifted = new double[frames];
        int w = (int) (2 * fps);
        for (int f = 0; f < frames; f++) {
            double m = 0;
            for (int g = Math.max(0, f - w); g <= Math.min(frames - 1, f + w); g++) {
                m = Math.max(m, g == f ? y[g] : Math.min(y[g], y[f] + 25 * Math.abs(g - f) / fps));
            }
            lifted[f] = m;
        }
        double[] smooth = new double[frames];
        int s = (int) (fps / 2);
        for (int f = 0; f < frames; f++) {
            double sum = 0;
            int n = 0;
            for (int g = Math.max(0, f - s); g <= Math.min(frames - 1, f + s); g++) {
                sum += lifted[g];
                n++;
            }
            smooth[f] = Math.max(sum / n, y[f]);
        }
        return smooth;
    }

    /** Places every plant at the level its distance to the camera asks for (only those that changed). */
    private static void levels(World world, double cx, double cy, double cz) {
        for (int p = 0; p < world.plants.size(); p++) {
            Plant plant = world.plants.get(p);
            double dx = plant.x() - cx;
            double dy = plant.y() - cy;
            double dz = plant.z() - cz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / Math.max(plant.sy(), 0.3);
            int level = d < 20 ? 0 : d < 60 ? 1 : d < 170 ? 2 : 3;
            if (level != world.levels[p]) {
                world.levels[p] = level;
                ProceduralFilms.Placed kind = world.kinds.get(plant.kind());
                boolean billboard = level >= kind.tree().lods().size();
                // only the nearer plants cast shadows: the cascades would otherwise draw every tree of the valley
                world.stage.set(world.ids[p], kind.meshes()[Math.min(level, kind.meshes().length - 1)],
                    billboard ? kind.billboardMaterial() : kind.material(), level < 2, plant.x(), plant.y(), plant.z(),
                    plant.yaw(), plant.sxz(), plant.sy(), plant.sxz());
            }
        }
    }

    private static Stage stage(VulkanDevice device) {
        return new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 0,
            GeometryPool.Capacity.of(1 << 23, 1 << 20, 512), 1 << 19)
            .sun(0.35f, 0.62f, 0.7f, ShadowSpec.cascades(4, 4096, 2500));
    }

    private void look(Stage stage, double t, double[] heights, int frame, double fps, double frames) {
        double[] p = camera(t);
        double y = heights[Math.min(frame, heights.length - 1)];
        double ahead = Math.min(t + 3, PATH[PATH.length - 2][3]);
        double[] q = camera(ahead);
        double tx = q[0];
        double tz = q[1];
        double ty = Math.max(q[2], surface.heightAt(q[0], q[1], 2)) - 40;
        double end = PATH[PATH.length - 2][3];
        if (t > end) {
            // at the summit, turn east to the Jungfrau over the valley
            double u = Math.clamp((t - end) / (PATH[PATH.length - 1][3] - end), 0, 1);
            u = u * u * (3 - 2 * u);
            double[] from = {tx, ty, tz};
            double[] to = {4122, 3600, 6323};
            tx = from[0] + u * (to[0] - from[0]);
            ty = from[1] + u * (to[1] - from[1]);
            tz = from[2] + u * (to[2] - from[2]);
        }
        if (Math.abs(tx - p[0]) + Math.abs(tz - p[1]) < 1) {
            tx = p[0] - 100;
        }
        stage.look(p[0], y, p[1], tx, ty, tz, 55);
    }

    private static void write(BufferedImage big, Path folder, int frame) throws IOException {
        BufferedImage small = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                for (int j = 0; j < SUPER; j++) {
                    for (int i = 0; i < SUPER; i++) {
                        int p = big.getRGB(SUPER * x + i, SUPER * y + j);
                        r += p >> 16 & 0xff;
                        g += p >> 8 & 0xff;
                        b += p & 0xff;
                    }
                }
                int n = SUPER * SUPER;
                small.setRGB(x, y, (r / n) << 16 | (g / n) << 8 | b / n);
            }
        }
        ImageIO.write(small, "png", folder.resolve(String.format(Locale.ROOT, "f%04d.png", frame)).toFile());
    }

    private static Path folder(String film) throws IOException {
        Path out = Path.of(System.getProperty("truesim.films", "build/films"), film);
        Files.createDirectories(out);
        return out;
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FILMS", matches = "true")
    void flightThroughTheBelts() throws Exception {
        try (VulkanInstance instance = VulkanInstance.builder().applicationName("ecology flight").surfaces(false)
            .build(); VulkanDevice device = VulkanDevice.createHeadless(instance.bestGpu())) {
            System.out.println("ecology flight on " + instance.bestGpu().name());
            try (Stage stage = stage(device)) {
                World world = build(stage);
                stage.shade();
                Path out = folder("ecology-flight");
                double fps = 30;
                int frames = (int) (PATH[PATH.length - 1][3] * fps);
                double[] heights = heights(frames, fps);
                long start = System.nanoTime();
                for (int f = 0; f < frames; f++) {
                    double t = f / fps;
                    look(stage, t, heights, f, fps, frames);
                    double[] p = camera(t);
                    levels(world, p[0], heights[f], p[1]);
                    stage.frames(f == 0 ? 4 : 1, 1 / fps);
                    write(stage.shot(), out, f);
                }
                System.out.printf(Locale.ROOT, "%d frames in %.1f s%n", frames, (System.nanoTime() - start) / 1e9);
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_SCREENSHOTS", matches = "true")
    void stills() throws Exception {
        // Validated (TRUESIM_GPU_ASSISTED_VALIDATION=true adds GPU-assisted validation): closing fails on errors
        try (TestGpu gpu = TestGpu.open(); Stage stage = stage(gpu.device())) {
            World world = build(stage);
            stage.shade();
            Path out = folder("ecology-stills");
            double fps = 30;
            int frames = (int) (PATH[PATH.length - 1][3] * fps);
            double[] heights = heights(frames, fps);
            double[] times = {3, 15, 27, 44};
            for (int k = 0; k < times.length; k++) {
                int f = (int) (times[k] * fps);
                look(stage, times[k], heights, f, fps, frames);
                double[] p = camera(times[k]);
                levels(world, p[0], heights[f], p[1]);
                stage.frames(4, 1 / fps);
                write(stage.shot(), out, k);
            }
        }
    }
}
