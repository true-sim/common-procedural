package com.truesim.common.procedural.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.truesim.common.jobs.TaskScheduler;
import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.settlement.RoadMeshes;
import com.truesim.common.procedural.settlement.Settlement;
import com.truesim.common.procedural.settlement.SettlementParameters;
import com.truesim.common.procedural.settlement.SettlementRecipe;
import com.truesim.common.procedural.settlement.Settlements;
import com.truesim.engine.settings.EngineSettings;
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

/** Settlement recipes through the real asset cache: the plan as the direct API's, its road meshes as models. */
class SettlementAssetsTest {

    private static final String VILLAGE = """
        # a valley village
        origin = 0 0
        seed = 5
        extent = 512
        material.cobbles = game://materials/cobbles.material
        material.gravel = game://materials/gravel.material
        material.dirt = game://materials/trail.material
        material.kerb = game://materials/granite.material
        material.steps = game://materials/granite.material
        """;

    /** A valley floor falling north, its sides rising east and west. */
    private static final EcologyTerrain VALLEY = (x, z) -> 900 + 0.01 * z + 0.03 * Math.min(Math.abs(x), 120)
        + 0.5 * Math.max(0, Math.abs(x) - 120);

    @TempDir
    Path dir;

    private TaskScheduler tasks;
    private AssetCache cache;
    private PathAssets paths;

    @BeforeEach
    void setUp() throws Exception {
        Path game = Files.createDirectories(dir.resolve("game/village"));
        Files.writeString(game.resolve("valley.settlement"), VILLAGE);
        Files.writeString(game.resolve("broken.settlement"), "seed = 3\n");
        tasks = new TaskScheduler();
        cache = new AssetCache(tasks);
        paths = new PathAssets(cache);
        paths.mounts().add("game", PathSource.folder(dir.resolve("game")));
        SettlementAssets.install(paths);
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

    private static Ecology ecology() {
        return new Ecology(VALLEY, EcologyParameters.ALPS.withAnalysis(new EcologyParameters.Analysis(8, 512, 512,
            150, 40, 32)).withLandUse(EcologyParameters.LandUse.MEADOWS).withSeed(3), AlpineSpecies.ALL);
    }

    @Test
    void aRecipeFileGivesTheSamePlanAsTheDirectApiAndItsRoadsAsModels() throws Exception {
        SettlementRecipe recipe = load(paths.ref(SettlementAssets.SETTLEMENT, "game://village/valley.settlement"));
        assertThat(recipe).isEqualTo(SettlementRecipe.parse(VILLAGE));
        SettlementParameters parameters = SettlementSettings.parameters(new EngineSettings());
        assertThat(parameters).isEqualTo(SettlementParameters.DEFAULTS);
        Ecology ecology = ecology();
        Settlement plan = load(SettlementAssets.plan(recipe, ecology, parameters));
        assertThat(plan).isNotNull();
        Settlement direct = Settlements.plan(recipe.parameters(parameters), ecology(), 0, 0, 5);
        assertThat(plan.buildings()).isEqualTo(direct.buildings());
        assertThat(plan.roads()).hasSameSizeAs(direct.roads());
        assertThat(plan.parameters().grid().extent()).isEqualTo(512);
        // the roads: a model a tile, with the five materials
        List<SettlementAssets.RoadModel> roads = SettlementAssets.roads(paths, recipe, plan);
        assertThat(roads).hasSameSizeAs(plan.roadMeshes());
        SettlementAssets.RoadModel first = roads.getFirst();
        RoadMeshes.Tile tile = plan.roadMeshes().getFirst();
        assertThat(first.x()).isEqualTo(tile.x());
        assertThat(first.y()).isEqualTo(tile.y());
        assertThat(first.z()).isEqualTo(tile.z());
        assertThat(first.i()).isEqualTo(tile.i());
        assertThat(first.j()).isEqualTo(tile.j());
        ModelData model = load(first.model());
        assertThat(model).isNotNull();
        assertThat(model.nodes()).hasSize(1);
        MeshData mesh = load(SettlementAssets.mesh(plan, 0));
        assertThat(mesh).isNotNull();
        // without materials: none at all (the renderer's default)
        SettlementRecipe bare = SettlementRecipe.parse("origin = 0 0\nseed = 5\nextent = 512\n");
        ModelData plain = load(SettlementAssets.roads(paths, bare, plan).getFirst().model());
        assertThat(plain).isNotNull();
    }

    @Test
    void settingsMakeTheParameters() {
        EngineSettings settings = new EngineSettings();
        settings.set(SettlementSettings.LANE_COUNT, 3);
        settings.set(SettlementSettings.MAIN_MAX_GRADE, 0.08f);
        settings.set(SettlementSettings.BUILDINGS_EAVES_TO_VALLEY, false);
        SettlementParameters p = SettlementSettings.parameters(settings);
        assertThat(p.lane().count()).isEqualTo(3);
        assertThat(p.main().maxGrade()).isEqualTo(0.08);
        assertThat(p.buildings().eavesToValley()).isFalse();
        assertThat(SettlementSettings.ALL).hasSize(88).doesNotHaveDuplicates();
    }

    @Test
    void brokenRecipesFailAsAssetsDo() throws Exception {
        AssetRef<SettlementRecipe> broken = paths.ref(SettlementAssets.SETTLEMENT, "game://village/broken.settlement");
        assertThat(load(broken)).isNull();
        assertThat(paths.ref(RenderAssets.MODEL, "game://village/valley.settlement")).isNotNull();
    }
}
