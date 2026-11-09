package com.truesim.common.procedural.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.truesim.common.jobs.TaskScheduler;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.water.BedStone;
import com.truesim.common.procedural.water.WaterNetwork;
import com.truesim.common.procedural.water.WaterRecipe;
import com.truesim.common.procedural.water.Watercourse;
import com.truesim.scene.assets.AssetCache;
import com.truesim.scene.assets.AssetRef;
import com.truesim.scene.assets.PathAssets;
import com.truesim.scene.assets.PathSource;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.render.ModelData;
import com.truesim.scene.render.RenderAssets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Water recipes and networks through the real asset cache: the same network and meshes as the direct API. */
class WaterAssetsTest {

    private static final String RECIPE = """
        # a valley with a cliff: a stream falls over it
        region = -1000 0 1000 2500
        seed = 3
        courses = game://water/valley.courses
        material.surface = game://water/river.material
        material.falls = game://water/falls.material
        material.jets = game://water/jets.material
        material.stones = game://rocks/stone.material
        """;

    private static final String COURSES = """
        course Bach
        width = 2
        -600 100
        -300 400
        """;

    /** A plateau falling 2 % south to a cliff 150 m high at z = 1000, a trough along x = 0. */
    private static final EcologyTerrain CLIFF = (x, z) -> {
        double across = 0.04 * Math.min(Math.abs(x), 400) + 0.2 * Math.max(0, Math.abs(x) - 400);
        double base = z < 1000 ? 2000 - 0.02 * z : z < 1030 ? 1980 - 5 * (z - 1000) : 1830 - 0.01 * (z - 1030);
        return base + across;
    };

    @TempDir
    Path dir;

    private TaskScheduler tasks;
    private AssetCache cache;
    private PathAssets paths;

    @BeforeEach
    void setUp() throws Exception {
        Path game = Files.createDirectories(dir.resolve("game/water"));
        Files.writeString(game.resolve("valley.water"), RECIPE);
        Files.writeString(game.resolve("valley.courses"), COURSES);
        Files.writeString(game.resolve("broken.water"), "seed = 3\n");
        tasks = new TaskScheduler();
        cache = new AssetCache(tasks);
        paths = new PathAssets(cache);
        paths.mounts().add("game", PathSource.folder(dir.resolve("game")));
        WaterAssets.install(paths);
    }

    @AfterEach
    void tearDown() {
        cache.close();
        tasks.close();
    }

    private <T> T load(AssetRef<T> ref) throws InterruptedException {
        cache.acquire(ref);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (cache.loadingCount() > 0) {
            tasks.drainCompleted();
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(1);
        }
        return cache.get(ref);
    }

    @Test
    void aRecipeGivesTheDirectApisNetworkAndMeshes() throws Exception {
        WaterRecipe recipe = load(paths.ref(WaterAssets.WATER, "game://water/valley.water"));
        assertThat(recipe).isEqualTo(WaterRecipe.parse(RECIPE));
        WaterAssets.Courses courses = load(paths.ref(WaterAssets.COURSES, recipe.courses()));
        assertThat(courses.courses()).hasSize(1);
        assertThat(courses.points()).isEqualTo(2);
        List<Watercourse> mapped = courses.courses();
        WaterAssets water = new WaterAssets(paths);
        WaterNetwork network = load(water.network(recipe, CLIFF, mapped));
        assertThat(network).isNotNull();
        assertThat(network.bytes()).isPositive();
        WaterNetwork direct = WaterNetwork.build(CLIFF, recipe.region(), recipe.parameters(), mapped);
        assertThat(network.channels()).hasSameSizeAs(direct.channels());
        assertThat(network.stones()).isEqualTo(direct.stones());
        assertThat(network.channels().stream().anyMatch(c -> c.name().equals("Bach"))).isTrue();
        assertThat(water.network(recipe, CLIFF)).isNotEqualTo(water.network(recipe, CLIFF, mapped));
        // a chunk's surface: the same triangles as the direct API's, as a model placed at the chunk with its material
        WaterNetwork.WaterChunk chunk = network.chunks().getFirst();
        ProceduralMesh surface = network.surface(chunk);
        MeshData mesh = load(water.surfaceMesh(network, chunk.x(), chunk.z()));
        assertThat(mesh.vertexCount()).isEqualTo(surface.vertexCount());
        ModelData model = load(water.surface(network, recipe, chunk));
        ModelData.Node node = model.nodes().getFirst();
        assertThat(node.tx()).isEqualTo(chunk.originX());
        assertThat(node.tz()).isEqualTo(chunk.originZ());
        assertThat(node.materials()).containsExactly(paths.ref(RenderAssets.MATERIAL, "game://water/river.material"));
        // the fall: sheet and jets, at its lip
        int fall = network.tallest().id();
        MeshData sheet = load(water.fallMesh(network, fall));
        assertThat(sheet.vertexCount()).isEqualTo(network.fallMesh(fall).vertexCount());
        ModelData falls = load(water.fall(network, recipe, fall));
        assertThat(falls.nodes().getFirst().ty()).isEqualTo(network.fallOrigin(fall)[1]);
        assertThat(falls.nodes().getFirst().materials()).hasSize(2);
        // without materials: the renderer's default
        WaterRecipe bare = WaterRecipe.parse("region = -1000 0 1000 2500\n");
        ModelData plain = load(water.surface(network, bare, chunk));
        assertThat(plain.nodes().getFirst().materials()).isEmpty();
        // a stone's rock: one of a few variants of its preset, from ProceduralAssets
        ProceduralAssets procedural = new ProceduralAssets(paths, new GenerationCache(dir.resolve("generated")))
            .install();
        BedStone stone = network.stones().getFirst();
        AssetRef<ModelData> rock = WaterAssets.stone(procedural, recipe, stone);
        assertThat(rock).isEqualTo(procedural.model(new Recipe("rock", stone.preset(),
            Math.floorMod(stone.seed(), WaterAssets.STONE_VARIANTS), com.truesim.common.procedural.tree.Season.SUMMER,
            0, List.of("game://rocks/stone.material"))));
    }

    @Test
    void brokenRecipesFailAsAssetsDo() throws Exception {
        assertThat(load(paths.ref(WaterAssets.WATER, "game://water/broken.water"))).isNull();
    }
}
