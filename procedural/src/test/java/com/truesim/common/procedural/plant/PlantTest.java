package com.truesim.common.procedural.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import com.truesim.common.procedural.tree.Season;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Meadow flowers and grass clumps: deterministic, flowering on their days, swaying about their foot, in budget. */
class PlantTest {

    static List<PlantSpecies> species() {
        return PlantSpecies.PRESETS;
    }

    private static byte[] bytes(ProceduralMesh mesh) {
        return mesh.vertices().segment().toArray(ValueLayout.JAVA_BYTE);
    }

    private static PlantOptions bloom(PlantSpecies species) {
        return PlantOptions.DEFAULTS.withDay(species.phenology().peakBloom());
    }

    @ParameterizedTest
    @MethodSource("species")
    void theSameSeedGivesBitIdenticalPlants(PlantSpecies species) throws Exception {
        PlantGenerator generator = new PlantGenerator();
        Plant first = generator.generate(species, bloom(species), 42);
        generator.generate(species, PlantOptions.DEFAULTS, 7);
        Plant again = generator.generate(species, bloom(species), 42);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Plant> other = executor.submit(() -> Plants.generate(species, bloom(species), 42));
            for (Plant p : List.of(again, other.get())) {
                for (int l = 0; l < first.lods().size(); l++) {
                    assertThat(bytes(p.lods().get(l))).isEqualTo(bytes(first.lods().get(l)));
                    assertThat(p.lods().get(l).indices()).isEqualTo(first.lods().get(l).indices());
                }
                assertThat(bytes(p.billboard())).isEqualTo(bytes(first.billboard()));
                assertThat(p.flora()).isEqualTo(first.flora());
            }
        }
        assertThat(bytes(generator.generate(species, bloom(species), 43).lods().getFirst()))
            .isNotEqualTo(bytes(first.lods().getFirst()));
    }

    @ParameterizedTest
    @MethodSource("species")
    void mostPlantsFlowerOnTheirPeakDayAndNoneInWinter(PlantSpecies species) {
        int flowering = 0;
        int seeds = 24;
        for (int seed = 0; seed < seeds; seed++) {
            Plant plant = Plants.generate(species, bloom(species), seed);
            flowering += plant.flora().flowers() + plant.flora().buds() > 0 ? 1 : 0;
            assertThat(Plants.generate(species, PlantOptions.DEFAULTS.withSeason(Season.WINTER), seed).flora()
                .shown()).isZero();
        }
        assertThat(flowering).isGreaterThan(seeds / 2);
        // the flowers' colors are the petal ramp's, the foliage the leaf (or blade) ramp's
        Plant plant = Plants.generate(species, bloom(species), 1);
        ProceduralMesh mesh = plant.lods().getFirst();
        Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
        Part foliage = species.grass() ? Part.BLADE : Part.LEAF;
        for (int t = 0; t < mesh.submeshEnd(Plant.FOLIAGE); t++) {
            assertThat(Palette.part(uv.y(mesh.indices()[t]))).isEqualTo(foliage);
        }
        assertThat(plant.palette()).isSameAs(species.palette());
    }

    @Test
    void leavesWitherInAutumnAndSnowPressesPlantsDown() {
        Plant summer = Plants.generate(PlantSpecies.GRASS, PlantOptions.DEFAULTS, 3);
        Plant autumn = Plants.generate(PlantSpecies.GRASS, PlantOptions.DEFAULTS.withDay(320), 3);
        assertThat(meanU(autumn.lods().getFirst())).isGreaterThan(meanU(summer.lods().getFirst()) + 0.5);
        Plant snowy = Plants.generate(PlantSpecies.GRASS, PlantOptions.DEFAULTS.withSnowLoad(1), 3);
        assertThat(snowy.height()).isLessThan(0.7 * summer.height());
        // grass: green spikes in June, straw in August
        Plant june = Plants.generate(PlantSpecies.GRASS, PlantOptions.DEFAULTS.withDay(172), 3);
        Plant august = Plants.generate(PlantSpecies.GRASS, PlantOptions.DEFAULTS.withDay(230), 3);
        assertThat(june.flora().shown()).isPositive();
        assertThat(august.flora().fruits()).isPositive();
        assertThat(august.flora().albedo().r()).isGreaterThan(june.flora().albedo().r());
    }

    private static double meanU(ProceduralMesh mesh) {
        Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
        double sum = 0;
        int n = 0;
        for (int t = 0; t < mesh.submeshEnd(Plant.FOLIAGE); t++) {
            sum += Palette.t(uv.x(mesh.indices()[t]));
            n++;
        }
        return sum / n;
    }

    @ParameterizedTest
    @MethodSource("species")
    void plantsSwayAboutTheirFoot(PlantSpecies species) {
        Plant plant = Plants.generate(species, bloom(species), 5);
        ProceduralMesh mesh = plant.lods().getFirst();
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        Vec4Attribute wind = mesh.vertices().attribute(ProceduralVertex.WIND);
        Vec2Attribute pivot = mesh.vertices().attribute(ProceduralVertex.WIND_PIVOT);
        double topBranch = 0;
        double topY = 0;
        for (int i = 0; i < mesh.vertexCount(); i++) {
            assertThat(wind.x(i)).isZero();              // no trunk
            assertThat(pivot.x(i)).isZero();             // the pivot at the foot
            assertThat(pivot.y(i)).isPositive();
            assertThat(wind.y(i)).isBetween(0.0, 1.0);
            assertThat(ProceduralVertex.leaf(wind.z(i))).isLessThanOrEqualTo(species.flutter() + 1 / 30.0);
            assertThat(wind.w(i)).isEqualTo(1);
            assertThat(position.y(i)).isGreaterThan(-0.01);
            if (position.y(i) > topY) {
                topY = position.y(i);
                topBranch = wind.y(i);
            }
        }
        // the highest point swings the most: well up its weight
        assertThat(topBranch).isGreaterThan(0.3);
        assertThat(plant.radius()).isPositive();
        assertThat(plant.billboard().triangleCount()).isEqualTo(6);
    }

    @ParameterizedTest
    @MethodSource("species")
    void levelsKeepTheirBudgets(PlantSpecies species) {
        Plant plant = Plants.generate(species, bloom(species), 8);
        for (int l = 0; l < plant.lods().size(); l++) {
            ProceduralMesh mesh = plant.lods().get(l);
            assertThat(mesh.triangleCount()).isLessThanOrEqualTo(PlantOptions.DEFAULTS.lods().get(l).triangleBudget());
            assertThat(mesh.submeshCount()).isEqualTo(2);
            if (l > 0) {
                assertThat(mesh.triangleCount()).isLessThanOrEqualTo(plant.lods().get(l - 1).triangleCount());
                assertThat(mesh.error()).isGreaterThanOrEqualTo(plant.lods().get(l - 1).error());
            }
        }
        Plant tight = Plants.generate(species, bloom(species).withLods(List.of(new PlantLod(0, 1, 60))), 8);
        assertThat(tight.lods().getFirst().triangleCount())
            .isLessThan(plant.lods().getFirst().triangleCount() + 1);
    }

    @Test
    void badOptionsAreRefused() {
        assertThatThrownBy(() -> new PlantLod(3, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlantOptions.DEFAULTS.withDay(400)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlantOptions.DEFAULTS.withSnowLoad(2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlantOptions.DEFAULTS.withLods(List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LeafSpec(2, 1, 1, 1, com.truesim.common.procedural.flora.Outline.BLADE, 0, 0, 0,
            1, 0)).isInstanceOf(IllegalArgumentException.class);
        PlantSpecies g = PlantSpecies.GRASS;
        assertThatThrownBy(() -> new PlantSpecies("x", 1, 0, 0, 1, 1, 0, 0, 0.001, 0, g.basal(), g.stemLeaves(),
            null, null, 0, g.phenology(), 0, 100, 200, 0, true, g.palette()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(LeafSpec.NONE.maxCount()).isZero();
        assertThat(Plants.generate(PlantSpecies.CLOVER, 1).species()).isSameAs(PlantSpecies.CLOVER);
    }
}
