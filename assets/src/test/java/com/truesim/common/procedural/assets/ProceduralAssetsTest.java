package com.truesim.common.procedural.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.jobs.TaskScheduler;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.tree.Season;
import com.truesim.common.procedural.tree.Tree;
import com.truesim.common.procedural.tree.TreeOptions;
import com.truesim.common.procedural.tree.TreeSpecies;
import com.truesim.common.procedural.tree.Trees;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Recipes through the real asset cache: the same meshes as the direct API, generated once, versions, hot reload. */
class ProceduralAssetsTest {

    private static final String SPRUCE = """
        # a spruce for the forest
        generator = tree
        preset = spruce
        seed = 42
        season = summer
        material.bark = game://materials/bark.material
        material.leaves = game://materials/needles.material
        """;

    @TempDir
    Path dir;

    private Path game;
    private TaskScheduler tasks;
    private AssetCache cache;
    private PathAssets paths;
    private GenerationCache generated;
    private ProceduralAssets procedural;

    @BeforeEach
    void setUp() throws Exception {
        game = Files.createDirectories(dir.resolve("game"));
        Files.createDirectories(game.resolve("forest"));
        Files.writeString(game.resolve("forest/spruce.tree"), SPRUCE);
        Files.writeString(game.resolve("forest/stone.rock"), "generator = rock\npreset = block\nseed = 3\n");
        tasks = new TaskScheduler();
        cache = new AssetCache(tasks);
        paths = new PathAssets(cache);
        paths.mounts().add("game", PathSource.folder(game));
        generated = new GenerationCache(dir.resolve("generated"));
        procedural = new ProceduralAssets(paths, generated).install();
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

    private static void assertSameMesh(MeshData a, MeshData b) {
        assertThat(a.vertexCount()).isEqualTo(b.vertexCount());
        assertThat(a.triangleCount()).isEqualTo(b.triangleCount());
        assertThat(a.materialSlots()).isEqualTo(b.materialSlots());
        assertThat(bytes(a.positions())).isEqualTo(bytes(b.positions()));
        assertThat(bytes(a.attributes())).isEqualTo(bytes(b.attributes()));
        assertThat(bytes(a.meshletTriangles())).isEqualTo(bytes(b.meshletTriangles()));
    }

    @Test
    void aRecipeFileLoadsTheSameMeshAsTheDirectApi() throws Exception {
        MeshData loaded = load(paths.ref(RenderAssets.MESH, "game://forest/spruce.tree"));
        assertThat(loaded).isNotNull();
        Tree direct = Trees.generate(TreeSpecies.SPRUCE, 42);
        try (MeshData expected = SceneMeshes.build(direct.lods().getFirst())) {
            assertSameMesh(loaded, expected);
        }
        assertThat(loaded.format()).isEqualTo(SceneMeshes.FORMAT);
        assertThat(loaded.materialSlots()).isEqualTo(2);
        // the other levels and the billboard by fragment
        MeshData far = load(paths.ref(RenderAssets.MESH, "game://forest/spruce.tree#lod/2"));
        assertThat(far.triangleCount()).isEqualTo(direct.lods().get(2).triangleCount());
        MeshData billboard = load(paths.ref(RenderAssets.MESH, "game://forest/spruce.tree#billboard"));
        assertThat(billboard.triangleCount()).isEqualTo(6);
        // the model: one node with level 0 and the recipe's materials
        ModelData model = load(paths.ref(RenderAssets.MODEL, "game://forest/spruce.tree"));
        ModelData.Node node = model.nodes().getFirst();
        assertThat(node.mesh()).isEqualTo(paths.ref(RenderAssets.MESH, "game://forest/spruce.tree#lod/0"));
        assertThat(node.materials()).containsExactly(
            paths.ref(RenderAssets.MATERIAL, "game://materials/bark.material"),
            paths.ref(RenderAssets.MATERIAL, "game://materials/needles.material"));
        // the recipe itself, and a rock
        assertThat(load(paths.ref(ProceduralAssets.RECIPE, "game://forest/spruce.tree")).seed()).isEqualTo(42);
        MeshData stone = load(paths.ref(RenderAssets.MESH, "game://forest/stone.rock#lod/1"));
        assertThat(stone.triangleCount()).isEqualTo(20 * 256);
        assertThat(stone.format()).isEqualTo(SceneMeshes.STILL); // no wind: no vertex color to tint by
        // generated once, for every load of the same recipe
        assertThat(generated.generations()).isEqualTo(2);
    }

    @Test
    void aSecondLoadReadsTheGenerationCache() throws Exception {
        AssetRef<MeshData> ref = paths.ref(RenderAssets.MESH, "game://forest/spruce.tree");
        MeshData first = load(ref);
        byte[] positions = bytes(first.positions());
        assertThat(generated.generations()).isEqualTo(1);
        assertThat(generated.hits()).isZero();
        // released and evicted, as streaming does: loaded again from disk, not generated
        cache.release(ref);
        cache.setBudget(0);
        tasks.drainCompleted();
        assertThat(cache.state(ref)).isNotEqualTo(AssetState.LOADED);
        MeshData again = load(ref);
        assertThat(bytes(again.positions())).isEqualTo(positions);
        assertThat(generated.generations()).isEqualTo(1);
        assertThat(generated.hits()).isEqualTo(1);
        // another process, the same cache folder: no generation either
        GenerationCache other = new GenerationCache(dir.resolve("generated"));
        List<ProceduralMesh> meshes = other.get(ProceduralGenerator.TREES, Recipe.parse(SPRUCE));
        assertThat(other.generations()).isZero();
        assertThat(other.hits()).isEqualTo(1);
        assertThat(meshes.getFirst().vertices().segment().toArray(ValueLayout.JAVA_BYTE)).isEqualTo(
            Trees.generate(TreeSpecies.SPRUCE, 42).lods().getFirst().vertices().segment().toArray(ValueLayout.JAVA_BYTE));
    }

    @Test
    void aNewGeneratorVersionGeneratesAgain() throws Exception {
        Recipe recipe = Recipe.parse(SPRUCE);
        generated.get(ProceduralGenerator.TREES, recipe);
        generated.get(ProceduralGenerator.TREES, recipe);
        assertThat(generated.generations()).isEqualTo(1);
        int[] calls = {0};
        ProceduralGenerator bumped = new ProceduralGenerator() {
            @Override
            public String id() {
                return "tree";
            }

            @Override
            public int version() {
                return ProceduralGenerator.TREES.version() + 1;
            }

            @Override
            public List<ProceduralMesh> generate(Recipe r) throws Exception {
                calls[0]++;
                return ProceduralGenerator.TREES.generate(r);
            }
        };
        procedural.generator(bumped);
        MeshData mesh = load(paths.ref(RenderAssets.MESH, "game://forest/spruce.tree"));
        assertThat(mesh).isNotNull();
        assertThat(calls[0]).isEqualTo(1);
        assertThat(generated.generations()).isEqualTo(2);
        assertThat(GenerationCache.key(bumped, recipe)).isNotEqualTo(GenerationCache.key(ProceduralGenerator.TREES,
            recipe));
        // a recipe that differs only in its materials shares the meshes
        Recipe other = new Recipe("tree", "spruce", 42, Season.SUMMER, 0, List.of("", ""));
        assertThat(GenerationCache.key(bumped, other)).isEqualTo(GenerationCache.key(bumped, recipe));
    }

    @Test
    void recipesMadeInCodeAreGeneratedAssets() throws Exception {
        Recipe winter = new Recipe("tree", "larch", 4, Season.WINTER, 0.5,
            List.of("game://materials/larch-bark.material", "game://materials/larch.material"));
        AssetRef<ModelData> model = procedural.model(winter);
        assertThat(model).isEqualTo(procedural.model(new Recipe("tree", "larch", 4, Season.WINTER, 0.5,
            List.of("game://materials/larch-bark.material", "game://materials/larch.material"))));
        ModelData data = load(model);
        // bare in winter: the leaves' submesh and its material are left out
        assertThat(data.nodes().getFirst().materials()).hasSize(1);
        MeshData mesh = load(data.nodes().getFirst().mesh());
        assertThat(mesh.materialSlots()).isEqualTo(1);
        Tree direct = Trees.generate(TreeSpecies.LARCH, TreeOptions.DEFAULTS.withSeason(Season.WINTER)
            .withSnowLoad(0.5), 4);
        assertThat(mesh.triangleCount()).isEqualTo(direct.lods().getFirst().triangleCount());
        // without materials for every submesh, none: the renderer's default
        ModelData plain = load(procedural.model(new Recipe("rock", "boulder", 1, Season.SUMMER, 0, List.of(""))));
        assertThat(plain.nodes().getFirst().materials()).isEmpty();
    }

    @Test
    void badRecipesAndFragmentsFail() throws Exception {
        Files.writeString(game.resolve("forest/oak.tree"), "generator = tree\npreset = oak\n");
        AssetRef<MeshData> oak = paths.ref(RenderAssets.MESH, "game://forest/oak.tree");
        assertThat(load(oak)).isNull();
        assertThat(cache.state(oak)).isEqualTo(AssetState.FAILED);
        AssetRef<MeshData> missing = paths.ref(RenderAssets.MESH, "game://forest/spruce.tree#lod/9");
        load(missing);
        assertThat(cache.state(missing)).isEqualTo(AssetState.FAILED);
        AssetRef<MeshData> odd = paths.ref(RenderAssets.MESH, "game://forest/stone.rock#billboard");
        load(odd);
        assertThat(cache.state(odd)).isEqualTo(AssetState.FAILED);
        assertThatThrownBy(() -> procedural.meshes(new Recipe("house", "chalet", 1, Season.SUMMER, 0, List.of())))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProceduralGenerator.ROCKS.generate(new Recipe("rock", "pebble", 1, Season.SUMMER,
            0, List.of()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recipesParse() {
        Recipe recipe = Recipe.parse(SPRUCE);
        assertThat(recipe).isEqualTo(new Recipe("tree", "spruce", 42, Season.SUMMER, 0,
            List.of("game://materials/bark.material", "game://materials/needles.material")));
        assertThat(Recipe.parse("generator=rock\npreset=slab\nseed=-7\nmaterial.rock = a://b\n").materials())
            .containsExactly("a://b");
        assertThat(Recipe.parse("generator = tree\npreset = larch\nseason = Autumn\nsnow = 0.25").snow())
            .isEqualTo(0.25);
        for (String bad : new String[] {"generator tree", "generator = tree", "preset = spruce",
            "generator = tree\npreset = spruce\nheight = 3", "generator = house\npreset = chalet",
            "generator = tree\npreset = spruce\nmaterial.rock = x://y"}) {
            assertThatThrownBy(() -> Recipe.parse(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new GenerationCache(null)).satisfies(c -> {
            try {
                c.get(ProceduralGenerator.ROCKS, Recipe.parse("generator = rock\npreset = scree"));
                c.get(ProceduralGenerator.ROCKS, Recipe.parse("generator = rock\npreset = scree"));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            assertThat(c.generations()).isEqualTo(2); // no folder: no caching
        });
    }

    @Test
    void liveRecipesReloadWhenTheirFileChanges() throws Exception {
        LiveRecipe live = procedural.live("game://forest/spruce.tree");
        assertThat(live.path().toString()).isEqualTo("game://forest/spruce.tree");
        List<Recipe> changes = new ArrayList<>();
        List<Exception> failures = new ArrayList<>();
        live.listen(new LiveRecipe.Listener() {
            @Override
            public void changed(LiveRecipe recipe, Recipe before, Recipe after) {
                changes.add(after);
            }

            @Override
            public void failed(LiveRecipe recipe, Exception error) {
                failures.add(error);
            }
        });
        AssetRef<ModelData> before = live.model();
        assertThat(load(before)).isNotNull();
        assertThat(live.poll()).isFalse(); // unchanged
        Files.writeString(game.resolve("forest/spruce.tree"), SPRUCE + "# a comment\n");
        procedural.poll();
        assertThat(changes).isEmpty(); // only a comment: the same recipe
        Files.writeString(game.resolve("forest/spruce.tree"), SPRUCE.replace("seed = 42", "seed = 43"));
        assertThat(live.poll()).isTrue();
        assertThat(changes).hasSize(1);
        assertThat(live.recipe().seed()).isEqualTo(43);
        AssetRef<ModelData> after = live.model();
        assertThat(after).isNotEqualTo(before);
        ModelData reloaded = load(after);
        MeshData mesh = load(reloaded.nodes().getFirst().mesh());
        assertThat(mesh.triangleCount()).isEqualTo(Trees.generate(TreeSpecies.SPRUCE, 43).lods().getFirst()
            .triangleCount());
        assertThat(live.mesh(1)).isEqualTo(procedural.mesh(live.recipe(), 1));
        // a broken edit keeps the recipe
        Files.writeString(game.resolve("forest/spruce.tree"), "generator = tree\n");
        assertThat(live.poll()).isFalse();
        assertThat(failures).hasSize(1);
        assertThat(live.recipe().seed()).isEqualTo(43);
        assertThatThrownBy(() -> procedural.live("game://forest/none.tree")).isInstanceOf(java.io.IOException.class);
    }
}
