package com.truesim.common.procedural.tree;

import static org.assertj.core.api.Assertions.assertThat;

import com.truesim.common.procedural.Seeds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A forest of 1,000 distinct trees, an Alpine mix (spruce 40 %, larch 25 %, fir 15 %, pine 10 %, beech and birch 5 %
 * each), each with all three levels, billboard and impostor hints: on one thread, and on every core with a generator
 * per thread. Run with {@code TRUESIM_BENCHMARK=true ./gradlew :common-procedural:test --tests '*ForestBenchmark'}.
 */
@EnabledIfEnvironmentVariable(named = "TRUESIM_BENCHMARK", matches = "true")
class ForestBenchmark {

    private static final int TREES = 1000;

    static TreeSpecies speciesOf(int i) {
        int pick = (int) Long.remainderUnsigned(Seeds.derive(99, i), 20);
        return pick < 8 ? TreeSpecies.SPRUCE : pick < 13 ? TreeSpecies.LARCH : pick < 16 ? TreeSpecies.FIR
            : pick < 18 ? TreeSpecies.PINE : pick < 19 ? TreeSpecies.BEECH : TreeSpecies.BIRCH;
    }

    @Test
    void aThousandTrees() throws Exception {
        // Warm up the JIT
        TreeGenerator warm = new TreeGenerator();
        for (int i = 0; i < 60; i++) {
            warm.generate(speciesOf(i), 10_000 + i);
        }
        for (TreeSpecies species : TreeSpecies.PRESETS) {
            TreeGenerator generator = new TreeGenerator();
            long start = System.nanoTime();
            long triangles = 0;
            for (int i = 0; i < 20; i++) {
                triangles += generator.generate(species, i).lods().getFirst().triangleCount();
            }
            System.out.printf(Locale.ROOT, "| %s | %.1f ms | %,d |%n", species.name(),
                (System.nanoTime() - start) / 1e6 / 20, triangles / 20);
        }
        TreeGenerator single = new TreeGenerator();
        long start = System.nanoTime();
        long triangles = 0;
        for (int i = 0; i < TREES; i++) {
            triangles += single.generate(speciesOf(i), i).lods().getFirst().triangleCount();
        }
        double serial = (System.nanoTime() - start) / 1e9;
        int threads = Runtime.getRuntime().availableProcessors();
        start = System.nanoTime();
        long parallelTriangles = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<Long>> parts = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int first = t;
                parts.add(executor.submit(() -> {
                    long sum = 0;
                    for (int i = first; i < TREES; i += threads) {
                        sum += Trees.generate(speciesOf(i), i).lods().getFirst().triangleCount();
                    }
                    return sum;
                }));
            }
            for (Future<Long> part : parts) {
                parallelTriangles += part.get();
            }
        }
        double parallel = (System.nanoTime() - start) / 1e9;
        System.out.printf(Locale.ROOT, "A forest of %,d trees (%,d LOD0 triangles): %.1f s on one thread, %.1f s on "
            + "%d threads%n", TREES, triangles, serial, parallel, threads);
        assertThat(parallelTriangles).isEqualTo(triangles);
    }

    /**
     * Flowering and fruiting species on their bloom and ripe days (all levels, billboard, impostor hints), and meadow
     * plants: per tree or clump, one thread, after a warm-up; then a meadow of 10,000 clumps on every core.
     */
    @Test
    void flora() throws Exception {
        TreeGenerator warm = new TreeGenerator();
        for (int i = 0; i < 40; i++) {
            TreeSpecies s = TreeSpecies.PRESETS.get(i % TreeSpecies.PRESETS.size());
            warm.generate(s, TreeOptions.DEFAULTS.withDay(150 + i * 3), 500 + i);
        }
        System.out.println("| Species | in flower | in fruit | LOD 0 triangles (flower / fruit) |");
        for (TreeSpecies species : TreeSpecies.PRESETS) {
            if (species.fruiting() == null) {
                continue;
            }
            double[] ms = new double[2];
            long[] tris = new long[2];
            double[] days = {species.fruiting().phenology().peakBloom(), species.fruiting().phenology().ripe()};
            TreeGenerator generator = new TreeGenerator();
            for (int d = 0; d < 2; d++) {
                TreeOptions options = TreeOptions.DEFAULTS.withDay(days[d]);
                long start = System.nanoTime();
                for (int i = 0; i < 10; i++) {
                    tris[d] += generator.generate(species, options, i).lods().getFirst().triangleCount();
                }
                ms[d] = (System.nanoTime() - start) / 1e6 / 10;
            }
            System.out.printf(Locale.ROOT, "| %s | %.1f ms | %.1f ms | %,d / %,d |%n", species.name(), ms[0], ms[1],
                tris[0] / 10, tris[1] / 10);
        }
        com.truesim.common.procedural.plant.PlantGenerator plants = new com.truesim.common.procedural.plant.PlantGenerator();
        for (int i = 0; i < 400; i++) {
            var s = com.truesim.common.procedural.plant.PlantSpecies.PRESETS.get(i % 7);
            plants.generate(s, com.truesim.common.procedural.plant.PlantOptions.DEFAULTS.withDay(150 + i % 100), i);
        }
        System.out.println("| Plant | per clump | LOD 0 triangles |");
        for (var species : com.truesim.common.procedural.plant.PlantSpecies.PRESETS) {
            var options = com.truesim.common.procedural.plant.PlantOptions.DEFAULTS.withDay(species.phenology().peakBloom());
            long start = System.nanoTime();
            long triangles = 0;
            for (int i = 0; i < 200; i++) {
                triangles += plants.generate(species, options, i).lods().getFirst().triangleCount();
            }
            System.out.printf(Locale.ROOT, "| %s | %.3f ms | %,d |%n", species.name(),
                (System.nanoTime() - start) / 1e6 / 200, triangles / 200);
        }
        int clumps = 10_000;
        int threads = Runtime.getRuntime().availableProcessors();
        long start = System.nanoTime();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<Long>> parts = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int first = t;
                parts.add(executor.submit(() -> {
                    long sum = 0;
                    for (int i = first; i < clumps; i += threads) {
                        var s = com.truesim.common.procedural.plant.PlantSpecies.PRESETS.get(
                            (int) Long.remainderUnsigned(Seeds.derive(7, i), 7));
                        sum += com.truesim.common.procedural.plant.Plants.generate(s,
                            com.truesim.common.procedural.plant.PlantOptions.DEFAULTS.withDay(192), i).lods().getFirst()
                            .triangleCount();
                    }
                    return sum;
                }));
            }
            long sum = 0;
            for (Future<Long> part : parts) {
                sum += part.get();
            }
            System.out.printf(Locale.ROOT, "A meadow of %,d clumps (%,d LOD0 triangles): %.2f s on %d threads%n",
                clumps, sum, (System.nanoTime() - start) / 1e9, threads);
        }
    }
}
