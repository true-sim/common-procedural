package com.truesim.common.procedural.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.jobs.TaskScheduler;
import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.rocks.RockField;
import com.truesim.common.procedural.ecology.rocks.RockFieldParameters;
import com.truesim.common.procedural.ecology.rocks.RockInstances;
import com.truesim.common.procedural.ecology.rocks.RockVariant;
import com.truesim.common.procedural.rock.Rock;
import com.truesim.common.procedural.rock.RockCover;
import com.truesim.common.procedural.rock.RockParams;
import com.truesim.common.procedural.rock.Rocks;
import com.truesim.engine.settings.EngineSettings;
import com.truesim.scene.assets.AssetCache;
import com.truesim.scene.assets.AssetRef;
import com.truesim.scene.assets.PathAssets;
import com.truesim.scene.assets.PathSource;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.render.ModelData;
import com.truesim.scene.render.RenderAssets;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Rocks through the asset manager: recipes with their cover, a field's variants as models, the settings. */
class RockAssetsTest {

    @TempDir
    Path dir;

    private TaskScheduler tasks;
    private AssetCache cache;
    private PathAssets paths;
    private ProceduralAssets procedural;

    @BeforeEach
    void setUp() throws Exception {
        Path game = Files.createDirectories(dir.resolve("game/rocks"));
        Files.writeString(game.resolve("mossy.rock"), """
            generator = rock
            preset = boulder
            seed = 7
            moss = 0.6
            lichen = 0.3
            yaw = 90
            burial = 0.25
            size = 1.6
            material.rock = game://materials/limestone.material
            material.fresh = game://materials/fresh.material
            """);
        tasks = new TaskScheduler();
        cache = new AssetCache(tasks);
        paths = new PathAssets(cache);
        paths.mounts().add("game", PathSource.folder(dir.resolve("game")));
        procedural = new ProceduralAssets(paths, new GenerationCache(dir.resolve("generated"))).install()
            .detail(ModelDetail.FLAT);
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

    @Test
    void aRockRecipeCarriesItsCoverAndGivesTheDirectApisMeshes() throws Exception {
        Recipe recipe = load(paths.ref(ProceduralAssets.RECIPE, "game://rocks/mossy.rock"));
        assertThat(recipe.parameters()).containsEntry("moss", "0.6").containsEntry("yaw", "90").hasSize(5);
        assertThat(recipe.materials()).containsExactly("game://materials/limestone.material",
            "game://materials/fresh.material");
        assertThat(recipe.generationKey()).contains("moss=").contains("size=");
        MeshData mesh = load(paths.ref(RenderAssets.MESH, "game://rocks/mossy.rock#lod/1"));
        Rock direct = Rocks.generate(RockParams.BOULDER.withSize(1.6), new RockCover(0.6, 0.3, Math.PI / 2, 0.25),
            7);
        MeshData expected = SceneMeshes.build(direct.lods().get(1), false, true, false);
        assertThat(mesh.format()).isEqualTo(SceneMeshes.TINTED);
        assertThat(bytes(mesh.attributes())).isEqualTo(bytes(expected.attributes()));
        assertThat(bytes(mesh.positions())).isEqualTo(bytes(expected.positions()));
        ModelData model = load(paths.ref(RenderAssets.MODEL, "game://rocks/mossy.rock"));
        // a boulder has no chips: its fresh submesh is empty and left out, the rock's material stays
        assertThat(model.nodes().getFirst().materials()).containsExactly(
            paths.ref(RenderAssets.MATERIAL, "game://materials/limestone.material"));
        // options belong to rocks; numbers only
        assertThatThrownBy(() -> Recipe.parse("generator = tree\npreset = spruce\nmoss = 1"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("moss");
        assertThatThrownBy(() -> Recipe.parse("generator = rock\npreset = block\nmoss = lots"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Recipe.parse("generator = rock\npreset = block\nheight = 2"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(recipe.number("absent", 3)).isEqualTo(3);
        assertThat(recipe.number("size", 0)).isEqualTo(1.6);
    }

    @Test
    void aFieldsVariantsLoadAsModelsIdenticalToTheDirectApis() throws Exception {
        Ecology ecology = new Ecology((x, z) -> 1000 - 0.08 * z, EcologyParameters.ALPS.withAnalysis(
            new EcologyParameters.Analysis(8, 512, 512, 150, 40, 32)), AlpineSpecies.ALL);
        RockField field = new RockField(ecology, RockFieldParameters.DEFAULTS.withKind(
            com.truesim.common.procedural.ecology.rocks.RockKind.ERRATIC, RockFieldParameters.KINDS.get(4)
                .withDensity(0.002)));
        RockInstances rocks = field.region(0, 0, 128, 128).instances();
        assertThat(rocks.count()).isPositive();
        Map<Integer, AssetRef<ModelData>> models = RockAssets.models(procedural, rocks,
            "game://materials/limestone.material", "");
        assertThat(models).isNotEmpty();
        int index = models.keySet().iterator().next();
        RockVariant variant = rocks.variants().get(index);
        ModelData model = load(models.get(index));
        MeshData mesh = load(model.nodes().getFirst().mesh());
        Rock direct = Rocks.generate(variant.params(), variant.cover(), variant.seed());
        MeshData expected = SceneMeshes.build(direct.lods().getFirst(), false, true, false);
        assertThat(bytes(mesh.attributes())).isEqualTo(bytes(expected.attributes()));
        Recipe recipe = RockAssets.recipe(variant, "a://rock", "b://fresh");
        assertThat(recipe.materials()).containsExactly("a://rock", "b://fresh");
        assertThat(RockAssets.recipe(new RockVariant(RockParams.SLAB, 3, RockCover.BARE), "a://rock", "")
            .materials()).containsExactly("a://rock", "a://rock");
    }

    @Test
    void settingsGiveTheDefaultsAndTheirChanges() {
        EngineSettings settings = new EngineSettings();
        assertThat(RockSettings.parameters(settings)).isEqualTo(RockFieldParameters.DEFAULTS);
        settings.set(RockSettings.SHADOW_ANGLE, 30f);
        settings.set(RockSettings.DENSITY.get(0), 0.1f);
        settings.set(RockSettings.SEEDS, 2);
        RockFieldParameters p = RockSettings.parameters(settings);
        assertThat(p.cliffs().shadowAngle()).isEqualTo(30);
        assertThat(p.kinds().getFirst().density()).isEqualTo(0.1);
        assertThat(p.variants().seeds()).isEqualTo(2);
        assertThat(RockSettings.ALL).hasSize(17 + 4 * 5);
        assertThat(RockSettings.ALL.stream().map(s -> s.name()).distinct().count()).isEqualTo(RockSettings.ALL.size());
    }

    private static byte[] bytes(java.lang.foreign.MemorySegment segment) {
        return segment.toArray(ValueLayout.JAVA_BYTE);
    }
}
