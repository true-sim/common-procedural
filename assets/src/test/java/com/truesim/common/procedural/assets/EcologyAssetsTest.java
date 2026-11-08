package com.truesim.common.procedural.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.jobs.TaskScheduler;
import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.EcologyRecipe;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.ecology.EcologyTile;
import com.truesim.common.procedural.ecology.GroundCover;
import com.truesim.common.procedural.ecology.Instances;
import com.truesim.common.procedural.ecology.Species;
import com.truesim.scene.assets.AssetCache;
import com.truesim.scene.assets.AssetRef;
import com.truesim.scene.assets.PathAssets;
import com.truesim.scene.assets.PathSource;
import com.truesim.scene.render.ModelData;
import com.truesim.scene.render.RenderAssets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ecology recipes through the real asset cache: the same ecology as the direct API, and the species' models. */
class EcologyAssetsTest {

    private static final String VALLEY = """
        # a valley's ecology
        seed = 7
        latitude = 46.6
        temperature = 19.0
        lapse = 0.006
        aspect-warming = 2.5
        tile = 128
        cell = 4
        meadows = true
        meadows.slope = 24
        meadows.altitude = 1700
        species = spruce larch beech hazel mugo blueberry gentian grass
        weight.beech = 1.4
        model.spruce = game://forest/spruce.tree
        model.beech = game://forest/beech.tree
        """;

    /** A slope rising north from 900 m: montane forest and meadows. */
    private static final EcologyTerrain SLOPE = (x, z) -> 900 - 0.3 * z + 20 * Math.sin(x / 90);

    @TempDir
    Path dir;

    private TaskScheduler tasks;
    private AssetCache cache;
    private PathAssets paths;

    @BeforeEach
    void setUp() throws Exception {
        Path game = Files.createDirectories(dir.resolve("game/ecology"));
        Files.writeString(game.resolve("valley.ecology"), VALLEY);
        Files.writeString(game.resolve("broken.ecology"), "species = spruce palm\n");
        tasks = new TaskScheduler();
        cache = new AssetCache(tasks);
        paths = new PathAssets(cache);
        paths.mounts().add("game", PathSource.folder(dir.resolve("game")));
        EcologyAssets.install(paths);
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
    void aRecipeFileGivesTheSameEcologyAsTheDirectApi() throws Exception {
        EcologyRecipe recipe = load(paths.ref(EcologyAssets.ECOLOGY, "game://ecology/valley.ecology"));
        assertThat(recipe).isNotNull();
        // the direct API with the same choices
        EcologyParameters parameters = EcologyParameters.ALPS.withSeed(7).withTiles(128, 4)
            .withLandUse(new EcologyParameters.LandUse(true, 24, 1700, 180));
        List<Species> species = new ArrayList<>();
        for (String id : List.of("spruce", "larch", "beech", "hazel", "mugo", "blueberry", "gentian", "grass")) {
            species.add(id.equals("beech") ? AlpineSpecies.BEECH.withWeight(1.4) : AlpineSpecies.byId(id));
        }
        assertThat(recipe.parameters()).isEqualTo(parameters);
        assertThat(recipe.species()).isEqualTo(species);
        Ecology fromAsset = recipe.ecology(SLOPE);
        Ecology direct = new Ecology(SLOPE, parameters, species);
        for (long t = 0; t < 2; t++) {
            EcologyTile a = fromAsset.tile(t, -2 - t);
            EcologyTile b = direct.tile(t, -2 - t);
            assertSameInstances(a.instances(), b.instances());
            assertThat(a.instances().count()).isGreaterThan(100);
            for (int s = 0; s < species.size(); s++) {
                assertThat(a.abundance(s)).isEqualTo(b.abundance(s));
                assertThat(a.size(s)).isEqualTo(b.size(s));
            }
            for (GroundCover c : GroundCover.values()) {
                assertThat(a.cover(c)).isEqualTo(b.cover(c));
            }
        }
        // the species' models as render assets
        Map<String, AssetRef<ModelData>> models = EcologyAssets.models(paths, recipe);
        assertThat(models.keySet()).containsExactly("spruce", "beech");
        assertThat(models.get("spruce")).isEqualTo(paths.ref(RenderAssets.MODEL, "game://forest/spruce.tree"));
    }

    @Test
    void brokenRecipesFailAsAssetsDo() throws Exception {
        AssetRef<EcologyRecipe> broken = paths.ref(EcologyAssets.ECOLOGY, "game://ecology/broken.ecology");
        assertThat(load(broken)).isNull();
        assertThatThrownBy(() -> EcologyRecipe.parse("seed 7")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EcologyRecipe.parse("colour = green")).hasMessageContaining("unknown key");
        assertThatThrownBy(() -> EcologyRecipe.parse("model.palm = game://x.tree"))
            .hasMessageContaining("no species");
        EcologyRecipe all = EcologyRecipe.parse("# defaults\nmeadows = false\n");
        assertThat(all.species()).isEqualTo(AlpineSpecies.ALL);
        assertThat(all.parameters()).isEqualTo(EcologyParameters.ALPS);
    }

    private static void assertSameInstances(Instances a, Instances b) {
        assertThat(a.count()).isEqualTo(b.count());
        for (int i = 0; i < a.count(); i++) {
            assertThat(a.x(i)).isEqualTo(b.x(i));
            assertThat(a.y(i)).isEqualTo(b.y(i));
            assertThat(a.z(i)).isEqualTo(b.z(i));
            assertThat(a.yaw(i)).isEqualTo(b.yaw(i));
            assertThat(a.height(i)).isEqualTo(b.height(i));
            assertThat(a.width(i)).isEqualTo(b.width(i));
            assertThat(a.species(i)).isEqualTo(b.species(i));
            assertThat(a.variant(i)).isEqualTo(b.variant(i));
            assertThat(a.age(i)).isEqualTo(b.age(i));
            assertThat(a.health(i)).isEqualTo(b.health(i));
        }
    }
}
