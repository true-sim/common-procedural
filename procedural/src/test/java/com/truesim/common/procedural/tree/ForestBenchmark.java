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
}
