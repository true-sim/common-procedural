package com.truesim.common.procedural.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.jobs.TaskScheduler;
import com.truesim.common.procedural.fence.Fence;
import com.truesim.common.procedural.fence.FenceGround;
import com.truesim.common.procedural.fence.FenceRecipe;
import com.truesim.common.procedural.fence.FenceTuning;
import com.truesim.common.procedural.fence.Fences;
import com.truesim.engine.settings.EngineSettings;
import com.truesim.scene.assets.AssetCache;
import com.truesim.scene.assets.AssetRef;
import com.truesim.scene.assets.AssetState;
import com.truesim.scene.assets.PathAssets;
import com.truesim.scene.assets.PathSource;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.render.ModelData;
import com.truesim.scene.render.RenderAssets;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fence recipes through the real asset cache: the direct API's meshes, models at the origin, grounds, settings. */
class FenceAssetsTest {

    private static final String MEADOW = """
        # a Schärhag up the meadow, with its own heights
        type = schaerhag
        seed = 7
        line = 100 40 200, 112 43 203, 120 46 209
        wood = weathered-larch
        material.wood = game://materials/larch.material
        """;

    private static final String VALLEY = """
        type = wire
        seed = 3
        line = 0 0, 10 4, 18 2
        ground = valley
        material.wood = game://materials/post.material
        material.metal = game://materials/wire.material
        """;

    static final FenceGround VALLEY_GROUND = (x, z) -> 800 + 0.2 * x - 0.05 * z;

    @TempDir
    Path dir;

    private TaskScheduler tasks;
    private AssetCache cache;
    private PathAssets paths;
    private GenerationCache generated;
    private FenceAssets fences;

    @BeforeEach
    void setUp() throws Exception {
        Path game = Files.createDirectories(dir.resolve("game/village"));
        Files.writeString(game.resolve("meadow.fence"), MEADOW);
        Files.writeString(game.resolve("valley.fence"), VALLEY);
        Files.writeString(game.resolve("nowhere.fence"), VALLEY.replace("valley", "nowhere"));
        tasks = new TaskScheduler();
        cache = new AssetCache(tasks);
        paths = new PathAssets(cache);
        paths.mounts().add("game", PathSource.folder(dir.resolve("game")));
        generated = new GenerationCache(dir.resolve("generated"));
        fences = new FenceAssets(paths, generated).install().ground("valley", VALLEY_GROUND);
    }

    @AfterEach
    void tearDown() {
        cache.close();
        tasks.close();
    }

    private <T> T load(AssetRef<T> ref) throws InterruptedException {
        cache.acquire(ref);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (cache.loadingCount() > 0) {
            tasks.drainCompleted();
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(1);
        }
        return cache.get(ref);
    }

    private static byte[] bytes(java.lang.foreign.MemorySegment segment) {
        return segment.toArray(ValueLayout.JAVA_BYTE);
    }

    @Test
    void aFenceFileLoadsTheDirectApisMeshesAndAModelAtItsOrigin() throws Exception {
        FenceRecipe recipe = load(paths.ref(FenceAssets.FENCE, "game://village/meadow.fence"));
        Fence direct = Fences.generate(recipe.type(), recipe.options(), recipe.points(), recipe.ownGround(), 7);
        MeshData near = load(paths.ref(RenderAssets.MESH, "game://village/meadow.fence"));
        try (MeshData expected = SceneMeshes.build(direct.lods().getFirst(), true, false)) {
            assertThat(near.vertexCount()).isEqualTo(expected.vertexCount());
            assertThat(bytes(near.positions())).isEqualTo(bytes(expected.positions()));
            assertThat(bytes(near.attributes())).isEqualTo(bytes(expected.attributes()));
        }
        assertThat(near.format()).isEqualTo(SceneMeshes.FORMAT); // COLOR: the albedo, UV1: moss and lichen
        assertThat(near.materialSlots()).isEqualTo(1); // wood only
        MeshData far = load(paths.ref(RenderAssets.MESH, "game://village/meadow.fence#lod/2"));
        assertThat(far.triangleCount()).isEqualTo(direct.lods().get(2).triangleCount());
        ModelData model = load(paths.ref(RenderAssets.MODEL, "game://village/meadow.fence"));
        ModelData.Node node = model.nodes().getFirst();
        assertThat(node.mesh()).isEqualTo(paths.ref(RenderAssets.MESH, "game://village/meadow.fence#hierarchy/0"));
        assertThat(node.tx()).isEqualTo(direct.origin().x());
        assertThat(node.ty()).isEqualTo(direct.origin().y());
        assertThat(node.tz()).isEqualTo(direct.origin().z());
        assertThat(node.materials()).containsExactly(paths.ref(RenderAssets.MATERIAL,
            "game://materials/larch.material"));
        MeshData hierarchy = load(node.mesh());
        assertThat(hierarchy.triangleCount()).isGreaterThanOrEqualTo(direct.lods().getFirst().triangleCount());
        // generated once: the model and meshes read the generation cache after the first
        assertThat(generated.generations()).isEqualTo(1);
        assertThat(generated.hits()).isGreaterThan(0);
    }

    @Test
    void aRegisteredGroundCarriesTheFence() throws Exception {
        ModelData model = load(paths.ref(RenderAssets.MODEL, "game://village/valley.fence"));
        ModelData.Node node = model.nodes().getFirst();
        assertThat(node.ty()).isEqualTo(800.0);
        assertThat(node.materials()).hasSize(2); // posts, wire
        FenceRecipe recipe = load(paths.ref(FenceAssets.FENCE, "game://village/valley.fence"));
        Fence direct = fences.fence(recipe);
        MeshData mesh = load(paths.ref(RenderAssets.MESH, "game://village/valley.fence#lod/1"));
        assertThat(mesh.triangleCount()).isEqualTo(direct.lods().get(1).triangleCount());
        assertThat(generated.generations()).isZero(); // a registered ground's fences are not cached on disk
        // an unknown ground fails as assets do
        AssetRef<MeshData> nowhere = paths.ref(RenderAssets.MESH, "game://village/nowhere.fence");
        load(nowhere);
        assertThat(cache.state(nowhere)).isEqualTo(AssetState.FAILED);
        assertThatThrownBy(() -> fences.ground("flat", VALLEY_GROUND)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recipesInCodeAndTheSettings() throws Exception {
        EngineSettings settings = new EngineSettings();
        settings.set(FenceSettings.SIDES, 6);
        settings.set(FenceSettings.LEVELS, 2);
        settings.set(FenceSettings.LEVEL, 1);
        settings.set(ProceduralSettings.HIERARCHY, false);
        FenceTuning tuning = FenceSettings.tuning(settings);
        assertThat(tuning.sides()).isEqualTo(6);
        assertThat(tuning.levels()).isEqualTo(2);
        fences.tuning(tuning).detail(FenceSettings.detail(settings));
        FenceRecipe recipe = FenceRecipe.parse(MEADOW);
        ModelData model = load(fences.model(recipe));
        MeshData mesh = load(model.nodes().getFirst().mesh());
        Fence direct = recipe.generate(recipe.ownGround(), tuning);
        assertThat(direct.lods()).hasSize(2);
        assertThat(mesh.triangleCount()).isEqualTo(direct.lods().get(1).triangleCount());
        // over a ground given in code
        ModelData over = load(fences.model(recipe, FenceGround.FLAT));
        assertThat(over.nodes().getFirst().ty()).isZero();
    }
}
