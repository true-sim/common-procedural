package com.truesim.common.procedural.building;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Buildings per kind on a slope (all four levels, fittings), on one thread; then a village of 200 (an Alpine mix) on
 * every core with a generator per thread, and the layouts alone. Run with {@code TRUESIM_BENCHMARK=true ./gradlew
 * :common-procedural:test --tests '*BuildingBenchmark'}.
 */
@EnabledIfEnvironmentVariable(named = "TRUESIM_BENCHMARK", matches = "true")
class BuildingBenchmark {

    private static final int VILLAGE = 200;

    static BuildingSpec spec(int i) {
        BuildingKind[] mix = {BuildingKind.HAY_BARN, BuildingKind.HAY_BARN, BuildingKind.HAY_BARN,
            BuildingKind.CHALET, BuildingKind.CHALET, BuildingKind.CHALET, BuildingKind.FARMHOUSE,
            BuildingKind.WOODSHED, BuildingKind.WOODSHED, BuildingKind.INN};
        BuildingKind kind = mix[i % mix.length];
        return BuildingSpec.of(kind, new Footprint(i * 31 % 500, i * 17 % 400, i * 0.7, kind.width(), kind.depth()),
            i);
    }

    @Test
    void aVillage() throws Exception {
        BuildingGenerator warm = new BuildingGenerator();
        for (int i = 0; i < 40; i++) {
            warm.generate(spec(i), BuildingTest.SLOPE, BuildingOptions.DEFAULTS);
        }
        for (BuildingKind kind : BuildingKind.values()) {
            BuildingGenerator generator = new BuildingGenerator();
            int n = 10;
            long start = System.nanoTime();
            int[] triangles = new int[4];
            for (int i = 0; i < n; i++) {
                Building b = generator.generate(BuildingSpec.of(kind, new Footprint(i, 0, 0.3, kind.width(),
                    kind.depth()), i), BuildingTest.SLOPE, BuildingOptions.DEFAULTS);
                for (int l = 0; l < 4; l++) {
                    triangles[l] += b.lods().get(l).triangleCount();
                }
            }
            System.out.printf(Locale.ROOT, "| %s | %.1f ms | %,d / %,d / %,d / %,d |%n", kind.id(),
                (System.nanoTime() - start) / 1e6 / n, triangles[0] / n, triangles[1] / n, triangles[2] / n,
                triangles[3] / n);
        }
        int threads = Runtime.getRuntime().availableProcessors();
        long start = System.nanoTime();
        long total = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<Long>> parts = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int first = t;
                parts.add(executor.submit(() -> {
                    long sum = 0;
                    for (int i = first; i < VILLAGE; i += threads) {
                        sum += Buildings.generate(spec(i), BuildingTest.SLOPE).lods().getFirst().triangleCount();
                    }
                    return sum;
                }));
            }
            for (Future<Long> part : parts) {
                total += part.get();
            }
        }
        double parallel = (System.nanoTime() - start) / 1e9;
        start = System.nanoTime();
        for (int i = 0; i < VILLAGE; i++) {
            Buildings.layout(spec(i), BuildingTest.SLOPE);
        }
        double layouts = (System.nanoTime() - start) / 1e6 / VILLAGE;
        System.out.printf(Locale.ROOT, "A village of %d: %.2f s on %d threads, %,d level 0 triangles; a layout alone "
            + "%.3f ms%n", VILLAGE, parallel, threads, total, layouts);
        assertThat(total).isPositive();
    }
}
