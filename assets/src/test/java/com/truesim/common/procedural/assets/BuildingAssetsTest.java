package com.truesim.common.procedural.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.jobs.TaskScheduler;
import com.truesim.common.procedural.building.Anchor;
import com.truesim.common.procedural.building.Building;
import com.truesim.common.procedural.building.BuildingKind;
import com.truesim.common.procedural.building.BuildingOptions;
import com.truesim.common.procedural.building.BuildingSlot;
import com.truesim.common.procedural.building.BuildingSpec;
import com.truesim.common.procedural.building.Buildings;
import com.truesim.common.procedural.building.Footprint;
import com.truesim.common.procedural.building.GroundHeight;
import com.truesim.common.procedural.building.RoofCover;
import com.truesim.common.procedural.tree.Season;
import com.truesim.engine.settings.EngineSettings;
import com.truesim.scene.assets.AssetCache;
import com.truesim.scene.assets.AssetRef;
import com.truesim.scene.assets.AssetState;
import com.truesim.scene.assets.PathAssets;
import com.truesim.scene.assets.PathSource;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.render.MaterialData;
import com.truesim.scene.render.ModelData;
import com.truesim.scene.render.RenderAssets;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Building recipes and generated buildings through the real asset cache: the same as the direct API. */
class BuildingAssetsTest {

    private static final String CHALET = """
        # a chalet on a slope
        generator = building
        preset = chalet
        seed = 7
        width = 9
        depth = 10.5
        floors = 2
        roof = stone-weighted
        grade.x = 0.05
        grade.z = -0.2
        material.stone = game://materials/stone.material
        material.timber = game://materials/logs.material
        """;

    @TempDir
    Path dir;

    private TaskScheduler tasks;
    private AssetCache cache;
    private PathAssets paths;
    private GenerationCache generated;
    private ProceduralAssets procedural;

    @BeforeEach
    void setUp() throws Exception {
        Path game = Files.createDirectories(dir.resolve("game/village"));
        Files.writeString(game.resolve("chalet.building"), CHALET);
        Files.writeString(game.resolve("barn.building"), "generator = building\npreset = hay-barn\nseed = 3\n");
        tasks = new TaskScheduler();
        cache = new AssetCache(tasks);
        paths = new PathAssets(cache);
        paths.mounts().add("game", PathSource.folder(dir.resolve("game")));
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

    private static BuildingSpec chaletSpec() {
        return new BuildingSpec(BuildingKind.CHALET, Footprint.of(9, 10.5), 2, 7, RoofCover.STONE_WEIGHTED,
            Double.NaN);
    }

    @Test
    void aBuildingRecipeLoadsTheSameMeshesAndLayoutAsTheDirectApi() throws Exception {
        Building direct = Buildings.generate(chaletSpec(), GroundHeight.plane(0, 0.05, -0.2));
        for (int level = 0; level < 4; level++) {
            MeshData loaded = load(paths.ref(RenderAssets.MESH, "game://village/chalet.building#lod/" + level));
            try (MeshData expected = SceneMeshes.build(direct.lods().get(level), false)) {
                assertThat(bytes(loaded.positions())).isEqualTo(bytes(expected.positions()));
                assertThat(bytes(loaded.attributes())).isEqualTo(bytes(expected.attributes()));
                assertThat(loaded.materialSlots()).isEqualTo(expected.materialSlots());
            }
            assertThat(loaded.format()).isEqualTo(SceneMeshes.STILL);
        }
        // no billboard: the last mesh is a level
        AssetRef<MeshData> billboard = paths.ref(RenderAssets.MESH, "game://village/chalet.building#billboard");
        load(billboard);
        assertThat(cache.state(billboard)).isEqualTo(AssetState.FAILED);
        // the layout: the floor over the recipe's plane, the fittings
        Building layout = load(paths.ref(BuildingAssets.LAYOUT, "game://village/chalet.building"));
        assertThat(layout.floor()).isEqualTo(direct.floor());
        assertThat(layout.fittings()).isEqualTo(direct.fittings());
        assertThat(layout.fittings().first(Anchor.Kind.FIRE)).isNotNull();
        // the model: a hierarchy from level 0, the materials of the slots it uses only when all are named (not here)
        ModelData model = load(paths.ref(RenderAssets.MODEL, "game://village/chalet.building"));
        assertThat(model.nodes().getFirst().mesh()).isEqualTo(paths.ref(RenderAssets.MESH,
            "game://village/chalet.building#hierarchy/0"));
        assertThat(model.nodes().getFirst().materials()).isEmpty();
        // the defaults: a recipe of a kind and seed only
        Building barn = load(paths.ref(BuildingAssets.LAYOUT, "game://village/barn.building"));
        assertThat(barn.spec()).isEqualTo(BuildingSpec.of(BuildingKind.HAY_BARN, 3));
        assertThat(generated.generations()).isEqualTo(1);
        // the flower boxes' palette, for the foliage and flowers slots
        com.truesim.scene.render.TextureData palette = load(paths.ref(RenderAssets.TEXTURE,
            "game://village/chalet.building#palette"));
        assertThat(palette.width()).isEqualTo(com.truesim.common.procedural.building.Geraniums.palette().width());
    }

    @Test
    void recipesInCodeAndBuildingsOnTerrainAreGeneratedAssets() throws Exception {
        procedural.detail(ModelDetail.FLAT);
        Map<BuildingSlot, String> named = new HashMap<>();
        for (BuildingSlot slot : BuildingSlot.all()) {
            named.put(slot, "game://materials/" + slot.id() + ".material");
        }
        Recipe recipe = BuildingAssets.recipe(chaletSpec(), 0.05, -0.2, named);
        assertThat(BuildingAssets.spec(recipe)).isEqualTo(chaletSpec());
        ModelData model = load(procedural.model(recipe));
        Building direct = Buildings.generate(chaletSpec(), GroundHeight.plane(0, 0.05, -0.2));
        MeshData mesh = load(model.nodes().getFirst().mesh());
        assertThat(mesh.triangleCount()).isEqualTo(direct.lods().getFirst().triangleCount());
        // a material for each slot the building uses, in slot order
        long used = BuildingSlot.all().stream().filter(s -> SceneMeshes.kept(direct.lods().getFirst(),
            s.ordinal())).count();
        assertThat(model.nodes().getFirst().materials()).hasSize((int) used);
        assertThat(model.nodes().getFirst().materials().getFirst()).isEqualTo(paths.ref(RenderAssets.MATERIAL,
            "game://materials/stone.material"));
        // a building generated over any terrain: its meshes and model by the building itself
        Building onTerrain = Buildings.generate(BuildingSpec.of(BuildingKind.INN, new Footprint(40, -12, 0.4, 13, 12),
            5), (x, z) -> 800 + 0.1 * x - 0.15 * z);
        AssetRef<MeshData> ref = BuildingAssets.mesh(onTerrain, 1, true);
        assertThat(ref).isEqualTo(BuildingAssets.mesh(onTerrain, 1, true));
        MeshData hierarchy = load(ref);
        // a building's hierarchy does not simplify (closed solids, seams): its levels are its detail
        assertThat(hierarchy.triangleCount()).isEqualTo(onTerrain.lods().get(1).triangleCount());
        Map<BuildingSlot, AssetRef<MaterialData>> materials = new HashMap<>();
        for (BuildingSlot slot : BuildingSlot.all()) {
            materials.put(slot, paths.ref(RenderAssets.MATERIAL, named.get(slot)));
        }
        ModelData placed = load(BuildingAssets.model(onTerrain, 2, false, materials));
        assertThat(placed.nodes().getFirst().mesh()).isEqualTo(BuildingAssets.mesh(onTerrain, 2, false));
        assertThat(placed.nodes().getFirst().materials()).isNotEmpty();
        ModelData bare = load(BuildingAssets.model(onTerrain, 3, false, Map.of()));
        assertThat(bare.nodes().getFirst().materials()).isEmpty();
        assertThatThrownBy(() -> BuildingAssets.mesh(onTerrain, 4, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildingRecipesParseTheirOwnParameters() {
        Recipe recipe = Recipe.parse(CHALET);
        assertThat(recipe.parameters()).containsEntry("roof", "stone-weighted").containsEntry("grade.z", "-0.2");
        assertThat(recipe.materials()).hasSize(BuildingSlot.all().size());
        assertThat(recipe.materials().get(BuildingSlot.TIMBER.ordinal())).isEqualTo("game://materials/logs.material");
        assertThat(recipe.generationKey()).contains("width=9\n").contains("grade.x=0.05\n");
        // trees keep their keys: no parameters of their own
        assertThat(Recipe.parse("generator = tree\npreset = spruce").generationKey()).doesNotContain("width");
        for (String bad : new String[] {"generator = building\npreset = chalet\nheight = 3",
            "generator = tree\npreset = spruce\nwidth = 3",
            "generator = building\npreset = chalet\nmaterial.bark = x"}) {
            assertThatThrownBy(() -> Recipe.parse(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new Recipe("tree", "spruce", 1, Season.SUMMER, 0, List.of(), Double.NaN).parameters()).isEmpty();
    }

    @Test
    void settingsMakeTheOptionsAndKeyTheCache() throws Exception {
        EngineSettings settings = new EngineSettings();
        BuildingOptions defaults = BuildingSettings.options(settings);
        assertThat(defaults.cell()).isEqualTo(2.0);
        assertThat(defaults.interiors()).isTrue();
        assertThat(BuildingSettings.ALL).hasSize(5);
        settings.set(BuildingSettings.CELL, 1.0f).set(BuildingSettings.INTERIORS, false);
        BuildingOptions finer = BuildingSettings.options(settings);
        assertThat(finer.cell()).isEqualTo(1.0);
        assertThat(finer.interiors()).isFalse();
        ProceduralGenerator a = BuildingAssets.generator(BuildingOptions.DEFAULTS);
        ProceduralGenerator b = BuildingAssets.generator(finer);
        assertThat(a.version()).isNotEqualTo(b.version());
        assertThat(a.sways()).isFalse();
        assertThat(a.billboard()).isFalse();
        assertThat(GenerationCache.key(a, Recipe.parse(CHALET))).isNotEqualTo(GenerationCache.key(b,
            Recipe.parse(CHALET)));
        // in use: the finer generator's meshes and layouts
        assertThat(BuildingAssets.options(procedural)).isEqualTo(BuildingOptions.DEFAULTS);
        BuildingAssets.options(procedural, finer);
        assertThat(BuildingAssets.options(procedural)).isEqualTo(finer);
        Building direct = Buildings.generate(chaletSpec(), GroundHeight.plane(0, 0.05, -0.2), finer);
        MeshData loaded = load(paths.ref(RenderAssets.MESH, "game://village/chalet.building"));
        assertThat(loaded.triangleCount()).isEqualTo(direct.lods().getFirst().triangleCount());
        Building layout = load(paths.ref(BuildingAssets.LAYOUT, "game://village/chalet.building"));
        assertThat(layout.fittings().first(Anchor.Kind.FIRE)).isNull(); // no rooms, no hearth's fire
    }
}
