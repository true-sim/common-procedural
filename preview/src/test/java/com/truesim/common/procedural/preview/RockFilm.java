package com.truesim.common.procedural.preview;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.math.color.Color;
import com.truesim.common.procedural.assets.SceneMeshes;
import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.EcologyTile;
import com.truesim.common.procedural.ecology.GroundCover;
import com.truesim.common.procedural.ecology.Instances;
import com.truesim.common.procedural.ecology.Layer;
import com.truesim.common.procedural.ecology.Species;
import com.truesim.common.procedural.ecology.rocks.RockField;
import com.truesim.common.procedural.ecology.rocks.RockFieldParameters;
import com.truesim.common.procedural.ecology.rocks.RockInstances;
import com.truesim.common.procedural.ecology.rocks.RockTile;
import com.truesim.common.procedural.ecology.rocks.RockVariant;
import com.truesim.common.procedural.ecology.terrain.TerrainEcology;
import com.truesim.common.procedural.rock.Rock;
import com.truesim.common.procedural.rock.RockCover;
import com.truesim.common.procedural.rock.RockParams;
import com.truesim.common.procedural.rock.Rocks;
import com.truesim.common.procedural.tree.TreeSpecies;
import com.truesim.common.procedural.tree.Trees;
import com.truesim.effects.terrain.data.SurfaceParameters;
import com.truesim.effects.terrain.data.TerrainSite;
import com.truesim.effects.terrain.data.TerrainSurface;
import com.truesim.engine.render.GeometryPool;
import com.truesim.engine.render.Material;
import com.truesim.engine.render.materials.MetallicRoughnessParameters;
import com.truesim.engine.vulkan.VulkanDevice;
import com.truesim.engine.vulkan.VulkanInstance;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.assets.mesh.VertexAttribute;
import com.truesim.scene.assets.mesh.VertexFormat;
import com.truesim.scene.core.ShadowSpec;
import com.truesim.scene.meshbuild.MeshBuilder;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A film of the rocks (1280 x 720, 30 frames a second): the talus cones at the foot of Lauterbrunnen's western wall
 * on effects-terrain's terrain (its cache), their blocks growing downslope, rockfall on the meadows beyond, the trees
 * kept off them; then close-ups of the presets, bare and grown with moss and lichen.
 * {@code TRUESIM_FILMS=true ./gradlew -Ptruesim.preview=true -Ptruesim.engine=false :preview:test --tests
 * '*RockFilm*' -Pfilms=<dir>}, then {@code ffmpeg -framerate 30 -i f%04d.png ...} per folder.
 */
class RockFilm {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int SUPER = 2;
    private static final double FPS = 30;
    /** TRUESIM_ROCK_STILLS=true: a frame from each shot's middle only, to frame the shots. */
    private static final boolean STILLS = "true".equals(System.getenv("TRUESIM_ROCK_STILLS"));
    /** The near area round the talus: terrain every 2 m, rocks and trees. */
    private static final double X0 = -768;
    private static final double Z0 = -256;
    private static final double SIZE = 1024;
    private static final double CELL = 2;
    /** The far terrain round it, every 24 m. */
    private static final double FAR_X0 = -9216;
    private static final double FAR_Z0 = -9216;
    private static final double FAR_SIZE = 18432;
    private static final double FAR_CELL = 24;

    /**
     * The shots, relative to the talus cone's centre (x, z offsets, metres; heights above the ground): camera from
     * (x, z, height) to (x, z, height), looking from (x, z, height) to (x, z, height), seconds, field of view. The
     * wall is to the west (-x).
     */
    private static final double[][] SHOTS = {
        {240, 90, 85, 150, 45, 60, -15, 0, 12, -15, 0, 8, 7, 34},
        {80, 35, 60, 40, 15, 36, -25, 0, 2, -25, 0, 3, 7, 50},
        {14, 24, 3.5, 10, -22, 3.0, -12, 8, -1, -12, -8, -1, 7, 55},
        {-25, 12, 1.9, -20, -12, 1.9, 60, 8, -4, 60, -8, -4, 6, 50},
    };
    /** The talus cone's centre the shots are framed on: found from the rocks (set in build). */
    private double coneX;
    private double coneZ;

    private static final Map<String, TreeSpecies> PRESETS = presets();

    private static Map<String, TreeSpecies> presets() {
        Map<String, TreeSpecies> map = new HashMap<>();
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

    /** A placed thing with levels of detail: rocks (variant meshes) or trees. */
    record Thing(int[] meshes, int material, boolean tree, int kind, double x, double y, double z, float[] q,
        double sx, double sy, double sz, double size) {
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FILMS", matches = "true")
    void talusBelowTheWall() throws Exception {
        surface = lauterbrunnen();
        assumeTrue(surface != null, "effects-terrain's cache holds no Lauterbrunnen");
        ground = TerrainEcology.of(surface, 2);
        try (VulkanInstance instance = VulkanInstance.builder().applicationName("rock film").surfaces(false)
            .build(); VulkanDevice device = VulkanDevice.createHeadless(instance.bestGpu())) {
            System.out.println("rock film on " + instance.bestGpu().name());
            try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 0,
                GeometryPool.Capacity.of(1 << 23, 1 << 20, 2048), 1 << 19)
                .sun(0.62f, 0.5f, 0.35f, ShadowSpec.cascades(4, 4096, 500))) {
                List<Thing> things = build(stage);
                stage.shade();
                Path out = ProceduralFilms.folder("rocks-talus");
                int frame = 0;
                int[] levels = new int[things.size()];
                java.util.Arrays.fill(levels, -1);
                int[] ids = new int[things.size()];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = stage.id();
                }
                long start = System.nanoTime();
                for (double[] shot : SHOTS) {
                    int frames = (int) (shot[12] * FPS);
                    for (int f = STILLS ? frames / 2 : 0; f < (STILLS ? frames / 2 + 1 : frames); f++) {
                        double u = (double) f / (frames - 1);
                        u = u * u * (3 - 2 * u);
                        double cx = coneX + lerp(shot[0], shot[3], u);
                        double cz = coneZ + lerp(shot[1], shot[4], u);
                        double cy = ground.height(cx, cz) + lerp(shot[2], shot[5], u);
                        double tx = coneX + lerp(shot[6], shot[9], u);
                        double tz = coneZ + lerp(shot[7], shot[10], u);
                        double ty = ground.height(tx, tz) + lerp(shot[8], shot[11], u);
                        stage.look(cx, cy, cz, tx, ty, tz, shot[13]);
                        levels(stage, things, ids, levels, cx, cy, cz);
                        stage.frames(f == 0 || STILLS ? 4 : 1, 1 / FPS);
                        ProceduralFilms.write(stage.shot(), out, frame++);
                    }
                }
                System.out.printf(Locale.ROOT, "%d frames in %.1f s%n", frame, (System.nanoTime() - start) / 1e9);
            }
        }
    }

    /** The terrain, the rocks and the trees of the near area, and the far terrain. */
    private List<Thing> build(Stage stage) throws Exception {
        EcologyParameters parameters = EcologyParameters.ALPS.withSeed(11)
            .withLandUse(EcologyParameters.LandUse.MEADOWS).withPlacement(EcologyParameters.Placement.DEFAULTS
                .withInstances(Layer.DWARF_SHRUB, false).withInstances(Layer.HERB, false));
        Ecology plain = new Ecology(ground, parameters, AlpineSpecies.ALL);
        RockField rocks = new RockField(plain, RockFieldParameters.DEFAULTS);
        Ecology ecology = plain.avoiding(rocks);
        long start = System.nanoTime();
        List<EcologyTile> tiles = new ArrayList<>();
        List<RockTile> rockTiles = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            List<Future<EcologyTile>> results = new ArrayList<>();
            List<Future<RockTile>> rockResults = new ArrayList<>();
            for (double z = Z0; z < Z0 + SIZE; z += 256) {
                for (double x = X0; x < X0 + SIZE; x += 256) {
                    double tx = x;
                    double tz = z;
                    rockResults.add(pool.submit(() -> rocks.region(tx, tz, 256, 256)));
                    results.add(pool.submit(() -> ecology.region(tx, tz, 256, 256)));
                }
            }
            for (Future<RockTile> r : rockResults) {
                rockTiles.add(r.get());
            }
            for (Future<EcologyTile> r : results) {
                tiles.add(r.get());
            }
        }
        System.out.printf(Locale.ROOT, "ecology and rocks: %.1f s%n", (System.nanoTime() - start) / 1e9);
        int terrain = stage.vertexColorMaterial(0.92f);
        int detailed = stage.materials.add(new Material(MetallicRoughnessParameters.DEFAULTS
            .withBaseColorTexture(stage.texture(groundDetail())).withRoughness(0.92f).withMetallic(0)));
        nearTerrain(stage, detailed, tiles);
        farTerrain(stage, terrain);
        List<Thing> things = new ArrayList<>();
        // rocks: every variant in use, its levels in the pool; weathered limestone, then fresh fractures
        int weathered = rockMaterials(stage);
        Map<Integer, int[]> meshes = new HashMap<>();
        int count = 0;
        for (RockTile tile : rockTiles) {
            RockInstances r = tile.instances();
            for (int i = 0; i < r.count(); i++) {
                int[] levels = meshes.computeIfAbsent(r.variantIndex(i), v -> {
                    RockVariant variant = r.variants().get(v);
                    Rock rock = Rocks.generate(variant.params(), variant.cover(), variant.seed());
                    int[] ids = new int[rock.lods().size()];
                    for (int l = 0; l < ids.length; l++) {
                        ids[l] = stage.addMesh(SceneMeshes.build(rock.lods().get(l), false, true, false));
                    }
                    return ids;
                });
                float s = r.scale(i);
                things.add(new Thing(levels, weathered, false, -1, r.x(i), r.y(i), r.z(i),
                    new float[] {r.qx(i), r.qy(i), r.qz(i), r.qw(i)}, s, s, s, r.size(i)));
                count++;
            }
        }
        System.out.printf(Locale.ROOT, "%d rocks of %d variants%n", count, meshes.size());
        // the cone: the centre of the talus rocks on active talus at the wall's foot, about (-215, 225)
        double sx = 0;
        double sz = 0;
        int n = 0;
        for (RockTile tile : rockTiles) {
            RockInstances r = tile.instances();
            for (int i = 0; i < r.count(); i++) {
                if (r.kind(i) == com.truesim.common.procedural.ecology.rocks.RockKind.TALUS
                    && Math.hypot(r.x(i) + 215, r.z(i) - 225) < 70 && r.x(i) > -250
                    && tile.activeTalus(r.x(i), r.z(i))) {
                    sx += r.x(i);
                    sz += r.z(i);
                    n++;
                }
            }
        }
        coneX = n == 0 ? -215 : sx / n;
        coneZ = n == 0 ? 225 : sz / n;
        System.out.printf(Locale.ROOT, "the cone: %d rocks round (%.0f, %.0f)%n", n, coneX, coneZ);
        // trees and shrubs: two variants of each preset in use
        Map<Integer, ProceduralFilms.Placed> kinds = new HashMap<>();
        List<Species> species = ecology.species();
        int trees = 0;
        for (EcologyTile tile : tiles) {
            Instances plants = tile.instances();
            for (int i = 0; i < plants.count(); i++) {
                TreeSpecies preset = PRESETS.get(species.get(plants.species(i)).id());
                if (preset == null) {
                    continue;
                }
                int key = TreeSpecies.PRESETS.indexOf(preset) * 2 + (int) (plants.variant(i) & 1);
                ProceduralFilms.Placed placed = kinds.computeIfAbsent(key, k -> ProceduralFilms.load(stage,
                    Trees.generate(preset, 31 + 7 * (k & 1))));
                double scale = species.get(plants.species(i)).habit().height() / placed.tree().height();
                float qy = (float) Math.sin(plants.yaw(i) / 2);
                float qw = (float) Math.cos(plants.yaw(i) / 2);
                things.add(new Thing(placed.meshes(), placed.material(), true, placed.billboardMaterial(),
                    plants.x(i), plants.y(i), plants.z(i), new float[] {0, qy, 0, qw}, plants.width(i) * scale,
                    plants.height(i) * scale, plants.width(i) * scale, plants.height(i) * scale
                        * placed.tree().height()));
                trees++;
            }
        }
        System.out.printf(Locale.ROOT, "%d trees and shrubs of %d kinds%n", trees, kinds.size());
        return things;
    }

    /**
     * The rock materials, textured limestone tinted by the vertex colour: weathered (grey), then fresh fractures
     * (lighter, creamier) as the next id.
     */
    private static int rockMaterials(Stage stage) {
        int weathered = stage.texture(limestone(3, 0.34, 0.33, 0.31));
        int fresh = stage.texture(limestone(4, 0.46, 0.44, 0.39));
        int first = stage.materials.add(new Material(MetallicRoughnessParameters.DEFAULTS
            .withBaseColorTexture(weathered).withRoughness(0.88f).withMetallic(0)));
        stage.materials.add(new Material(MetallicRoughnessParameters.DEFAULTS.withBaseColorTexture(fresh)
            .withRoughness(0.8f).withMetallic(0)));
        return first;
    }

    /** Places every thing at the level its distance asks for (only those that changed). */
    private static void levels(Stage stage, List<Thing> things, int[] ids, int[] levels, double cx, double cy,
        double cz) {
        for (int p = 0; p < things.size(); p++) {
            Thing t = things.get(p);
            double dx = t.x() - cx;
            double dy = t.y() - cy;
            double dz = t.z() - cz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / Math.max(t.size(), 0.1);
            int level = t.tree() ? (d < 5 ? 0 : d < 15 ? 1 : d < 45 ? 2 : 3)
                : (d < 15 ? 0 : d < 45 ? 1 : d < 140 ? 2 : 3);
            if (level == levels[p]) {
                continue;
            }
            levels[p] = level;
            int mesh = t.meshes()[Math.min(level, t.meshes().length - 1)];
            boolean billboard = t.tree() && level >= t.meshes().length - 1;
            int material = billboard ? t.kind() : t.material();
            boolean shadows = level < 2;
            float[] q = t.q();
            stage.scene.set(ids[p], mesh, material, 1, shadows, (float) t.x(), (float) t.y(), (float) t.z(), q[0],
                q[1], q[2], q[3], (float) t.sx(), (float) t.sy(), (float) t.sz());
        }
    }

    /** The near terrain every 2 m (the height plants and rocks stand on), coloured by the ecology's ground cover. */
    private void nearTerrain(Stage stage, int material, List<EcologyTile> tiles) {
        for (EcologyTile t : tiles) {
            int fields = t.samples();
            double fieldCell = t.cell();
            int n = (int) Math.round((fields - 1) * fieldCell / CELL) + 1;
            float[] positions = new float[3 * n * n];
            float[] normals = new float[3 * n * n];
            float[] colors = new float[4 * n * n];
            float[] uvs = new float[2 * n * n];
            float[] color = new float[4];
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    int a = j * n + i;
                    double x = t.x0() + i * CELL;
                    double z = t.z0() + j * CELL;
                    double h = ground.height(x, z);
                    positions[3 * a] = (float) (i * CELL);
                    positions[3 * a + 1] = (float) h;
                    positions[3 * a + 2] = (float) (j * CELL);
                    uvs[2 * a] = (float) (x / 3);
                    uvs[2 * a + 1] = (float) (z / 3);
                    double nx = ground.height(x - 1, z) - ground.height(x + 1, z);
                    double nz = ground.height(x, z - 1) - ground.height(x, z + 1);
                    double l = Math.sqrt(nx * nx + 4 + nz * nz);
                    normals[3 * a] = (float) (nx / l);
                    normals[3 * a + 1] = (float) (2 / l);
                    normals[3 * a + 2] = (float) (nz / l);
                    // the ground's colour, bilinear between the field's samples
                    double gx = i * CELL / fieldCell;
                    double gz = j * CELL / fieldCell;
                    int fi = Math.min((int) gx, fields - 2);
                    int fj = Math.min((int) gz, fields - 2);
                    double fx = gx - fi;
                    double fz = gz - fj;
                    for (int c = 0; c < 4; c++) {
                        colors[4 * a + c] = 0;
                    }
                    int[] corners = {fj * fields + fi, fj * fields + fi + 1, (fj + 1) * fields + fi,
                        (fj + 1) * fields + fi + 1};
                    double[] w = {(1 - fx) * (1 - fz), fx * (1 - fz), (1 - fx) * fz, fx * fz};
                    for (int k = 0; k < 4; k++) {
                        groundColor(t, corners[k], color);
                        for (int c = 0; c < 4; c++) {
                            colors[4 * a + c] += (float) (w[k] * color[c]);
                        }
                    }
                }
            }
            int[] indices = new int[6 * (n - 1) * (n - 1)];
            int at = 0;
            for (int j = 0; j < n - 1; j++) {
                for (int i = 0; i < n - 1; i++) {
                    int a = j * n + i;
                    int[] quad = {a, a + n, a + 1, a + 1, a + n, a + n + 1};
                    System.arraycopy(quad, 0, indices, at, 6);
                    at += 6;
                }
            }
            MeshData mesh = new MeshBuilder(VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.UV0,
                VertexAttribute.COLOR), n * n).positions(positions).attribute(VertexAttribute.NORMAL, normals)
                .attribute(VertexAttribute.UV0, uvs).attribute(VertexAttribute.COLOR, colors).submesh(indices)
                .buildFlat();
            stage.set(stage.id(), stage.addMesh(mesh), material, t.x0(), 0, t.z0(), 0, 1, 1, 1);
        }
    }

    /** The linear colour of the ground from a sample's cover. */
    private static void groundColor(EcologyTile tile, int a, float[] out) {
        double temperature = tile.field(EcologyTile.Field.TEMPERATURE)[a];
        double alpine = Math.clamp((13 - temperature) / 9, 0, 1);
        double[][] colors = {
            {0.05 + 0.04 * alpine, 0.11 - 0.025 * alpine, 0.012 + 0.012 * alpine},
            {0.025, 0.032, 0.014},
            {0.055, 0.04, 0.03},
            {0.16, 0.155, 0.145},
            {0.14, 0.135, 0.125},
            {0.78, 0.8, 0.85},
            {0.05, 0.07, 0.03},
            {0.02, 0.04, 0.05},
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
        out[0] = (float) r;
        out[1] = (float) g;
        out[2] = (float) b;
        out[3] = 1;
    }

    /**
     * The far terrain every 24 m, coloured by slope and altitude (rock, meadow, forest, snow), leaving out the near
     * area and lowered under its edge.
     */
    private void farTerrain(Stage stage, int material) {
        int tile = 64;
        int tiles = (int) (FAR_SIZE / FAR_CELL / tile);
        double[] normal = new double[3];
        for (int tj = 0; tj < tiles; tj++) {
            for (int ti = 0; ti < tiles; ti++) {
                double x0 = FAR_X0 + ti * tile * FAR_CELL;
                double z0 = FAR_Z0 + tj * tile * FAR_CELL;
                int n = tile + 1;
                float[] positions = new float[3 * n * n];
                float[] normals = new float[3 * n * n];
                float[] colors = new float[4 * n * n];
                boolean[] skip = new boolean[n * n];
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        int a = j * n + i;
                        double x = x0 + i * FAR_CELL;
                        double z = z0 + j * FAR_CELL;
                        boolean near = x >= X0 - FAR_CELL && x <= X0 + SIZE + FAR_CELL && z >= Z0 - FAR_CELL
                            && z <= Z0 + SIZE + FAR_CELL;
                        skip[a] = x > X0 + FAR_CELL && x < X0 + SIZE - FAR_CELL && z > Z0 + FAR_CELL
                            && z < Z0 + SIZE - FAR_CELL;
                        double h = surface.heightAt(x, z, FAR_CELL) - (near ? 3 : 0);
                        positions[3 * a] = (float) (i * FAR_CELL);
                        positions[3 * a + 1] = (float) h;
                        positions[3 * a + 2] = (float) (j * FAR_CELL);
                        surface.normalAt(x, z, FAR_CELL, normal);
                        normals[3 * a] = (float) normal[0];
                        normals[3 * a + 1] = (float) normal[1];
                        normals[3 * a + 2] = (float) normal[2];
                        double rock = Math.clamp((0.82 - normal[1]) / 0.12, 0, 1);
                        double snow = Math.clamp((h - 2500) / 300, 0, 1) * (1 - 0.7 * rock);
                        double forest = Math.clamp((1900 - h) / 200, 0, 1) * Math.clamp((h - 900) / 100, 0, 1) * 0.7;
                        double[] green = {0.05 * (1 - forest) + 0.025 * forest, 0.1 * (1 - forest) + 0.035 * forest,
                            0.015};
                        double[] c = new double[3];
                        for (int k = 0; k < 3; k++) {
                            double base = green[k] + rock * ((k == 2 ? 0.125 : 0.135) - green[k]);
                            c[k] = base + snow * (new double[] {0.78, 0.8, 0.85}[k] - base);
                        }
                        colors[4 * a] = (float) c[0];
                        colors[4 * a + 1] = (float) c[1];
                        colors[4 * a + 2] = (float) c[2];
                        colors[4 * a + 3] = 1;
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
                stage.set(stage.id(), stage.addMesh(mesh), material, x0, 0, z0, 0, 1, 1, 1);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ close-ups

    /** Close-ups of every preset: each a pair, bare and grown with moss and lichen, turning slowly. */
    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FILMS", matches = "true")
    void closeUps() throws Exception {
        try (VulkanInstance instance = VulkanInstance.builder().applicationName("rock presets").surfaces(false)
            .build(); VulkanDevice device = VulkanDevice.createHeadless(instance.bestGpu());
            Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 600, 40)) {
            stage.ground(0.07f, 0.085f, 0.04f);
            int weathered = rockMaterials(stage);
            List<RockParams> order = List.of(RockParams.BLOCK, RockParams.SLAB, RockParams.BOULDER,
                RockParams.ERRATIC, RockParams.CLUSTER, RockParams.SCREE);
            double spacing = 40;
            for (int p = 0; p < order.size(); p++) {
                RockParams params = order.get(p);
                double show = 1.1 / params.size(); // all shown about a metre across
                for (int k = 0; k < 3; k++) {
                    RockCover cover = k == 0 ? RockCover.BARE : k == 1 ? new RockCover(0.75, 0.35, 0, Double.NaN)
                        : new RockCover(0.25, 0.9, 0, Double.NaN);
                    Rock rock = Rocks.generate(params, cover, 5 + k);
                    int mesh = stage.addMesh(SceneMeshes.build(rock.lods().getFirst(), false, true, false));
                    double x = p * spacing + (k - 1) * 2.9;
                    stage.scene.set(stage.id(), mesh, weathered, 1, true, (float) x, 0, 0, 0, 0, 0, 1,
                        (float) show, (float) show, (float) show);
                }
            }
            stage.shade();
            Path out = ProceduralFilms.folder("rocks-presets");
            int frame = 0;
            double seconds = 2.6;
            for (int p = 0; p < order.size(); p++) {
                int frames = (int) (seconds * FPS);
                for (int f = STILLS ? frames / 2 : 0; f < (STILLS ? frames / 2 + 1 : frames); f++) {
                    double u = (double) f / frames;
                    double angle = Math.toRadians(-25 + 50 * u);
                    double cx = p * spacing;
                    // from the south (the bare one left), turning a little: the mossy one's north side shows later
                    double r = 6.2;
                    stage.look(cx + r * Math.sin(angle), 2.4, r * Math.cos(angle), cx, 0.45, 0, 45);
                    stage.frames(f == 0 || STILLS ? 4 : 1, 1 / FPS);
                    ProceduralFilms.write(stage.shot(), out, frame++);
                }
            }
            // then round to the north: moss on the north sides
            for (int f = STILLS ? 60 : 0; f < (STILLS ? 61 : (int) (4 * FPS)); f++) {
                double u = f / (4 * FPS);
                double angle = Math.toRadians(150 + 60 * u);
                double cx = 0;
                stage.look(cx + 6.2 * Math.sin(angle), 2.4, 6.2 * Math.cos(angle), cx, 0.45, 0, 45);
                stage.frames(f == 0 || STILLS ? 4 : 1, 1 / FPS);
                ProceduralFilms.write(stage.shot(), out, frame++);
            }
        }
    }

    /**
     * Limestone: a tileable grain of fine and coarse noise with darker pits, grey-cream, about 0.5 albedo; the rock
     * material's base colour texture, which the vertex tint (moss, lichen, fresh fractures) multiplies.
     */
    static java.awt.image.BufferedImage limestone(long seed, double r, double g, double b) {
        int size = 256;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        com.truesim.common.math.random.Noise noise = com.truesim.common.math.random.Noise.seeded(seed);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double cu = Math.cos(2 * Math.PI * x / size);
                double su = Math.sin(2 * Math.PI * x / size);
                double cv = Math.cos(2 * Math.PI * y / size);
                double sv = Math.sin(2 * Math.PI * y / size);
                double coarse = noise.fractal(cu * 1.5 + 3, su * 1.5, cv * 1.5 + sv * 1.5, 4);
                double fine = noise.fractal(cu * 9, su * 9 + 4, cv * 9 - sv * 9, 3);
                double pit = noise.cellular(cu * 5 + 2, su * 5, cv * 5 + sv * 5) < 0.12 ? 0.75 : 1;
                double k = (0.82 + 0.16 * coarse + 0.1 * fine) * pit;
                image.setRGB(x, y, 0xff000000 | srgb(r * k) << 16 | srgb(g * k) << 8 | srgb(b * k));
            }
        }
        return image;
    }

    /** A ground detail texture: grains and pebbles about 1, multiplying the ground's vertex colour. */
    static java.awt.image.BufferedImage groundDetail() {
        int size = 256;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        com.truesim.common.math.random.Noise noise = com.truesim.common.math.random.Noise.seeded(17);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double cu = Math.cos(2 * Math.PI * x / size);
                double su = Math.sin(2 * Math.PI * x / size);
                double cv = Math.cos(2 * Math.PI * y / size);
                double sv = Math.sin(2 * Math.PI * y / size);
                double n = noise.fractal(cu * 6, su * 6 + 2, cv * 6 + sv * 6, 4);
                double pebble = noise.cellular(cu * 14, su * 14 + 1, cv * 14 - sv * 14);
                double k = Math.clamp(0.8 + 0.25 * n + (pebble < 0.25 ? 0.15 : 0), 0, 1);
                int v = srgb(k);
                image.setRGB(x, y, 0xff000000 | v << 16 | v << 8 | v);
            }
        }
        return image;
    }

    private static int srgb(double linear) {
        double c = Math.clamp(linear, 0, 1);
        double s = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.round(255 * s);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
