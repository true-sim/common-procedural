package com.truesim.common.procedural.ecology.terrain;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.EcologyTile;
import com.truesim.common.procedural.ecology.Layer;
import com.truesim.effects.terrain.data.TerrainSurface;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The ecology of Lauterbrunnen's near area (effects-terrain's fetched site: swissALTI3D at 2 m round the village, from
 * its cache): 2 by 2 km round the church, the valley floor and both sides up to about 1500 m, in 256 m tiles with 4 m
 * fields and every instance layer. Prints the time per square kilometre on one thread and on all, with the terrain
 * analysis apart (it is shared by all tiles of its 2 km blocks). {@code TRUESIM_BENCHMARK=true ./gradlew
 * :common-procedural-ecology-terrain:test --tests '*LauterbrunnenBenchmark'}.
 */
class LauterbrunnenBenchmark {

    private static final int TILES = 8; // 8 x 256 m = 2 km a side

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_BENCHMARK", matches = "true")
    void nearArea() throws Exception {
        TerrainSurface surface = Sites.lauterbrunnenSurface();
        assumeTrue(surface != null, "effects-terrain's cache holds no Lauterbrunnen");
        EcologyParameters parameters = EcologyParameters.ALPS.withLandUse(EcologyParameters.LandUse.MEADOWS);
        int threads = Runtime.getRuntime().availableProcessors();
        System.out.printf(Locale.ROOT, "Lauterbrunnen near area: %d x %d tiles of %.0f m, %d threads%n", TILES,
            TILES, parameters.tileSize(), threads);
        // warm up on a separate ecology (JIT), then measure on fresh ones
        run(new Ecology(TerrainEcology.of(surface, 2), parameters, AlpineSpecies.ALL), 1, 2);
        for (int round = 0; round < 2; round++) {
            Ecology one = new Ecology(TerrainEcology.of(surface, 2), parameters, AlpineSpecies.ALL);
            long analysis = analyse(one);
            long start = System.nanoTime();
            long[] plants = run(one, 1, TILES);
            double single = (System.nanoTime() - start) / 1e9;
            Ecology all = new Ecology(TerrainEcology.of(surface, 2), parameters, AlpineSpecies.ALL);
            analyse(all);
            start = System.nanoTime();
            run(all, threads, TILES);
            double parallel = (System.nanoTime() - start) / 1e9;
            double km2 = Math.pow(TILES * parameters.tileSize() / 1000, 2);
            System.out.printf(Locale.ROOT,
                "round %d: analysis %.2f s (%d blocks of 2 km, %.2f s each); tiles %.2f s per km2 on one thread, "
                    + "%.3f s per km2 on %d; per km2: %d trees, %d shrubs, %d dwarf shrubs, %d herb clumps%n",
                round, analysis / 1e9, one.analysedBlocks(), analysis / 1e9 / one.analysedBlocks(), single / km2,
                parallel / km2, threads, (long) (plants[0] / km2), (long) (plants[1] / km2),
                (long) (plants[2] / km2), (long) (plants[3] / km2));
        }
    }

    /** Analyses the blocks the area needs (by asking a point in each), returning the nanoseconds it took. */
    private static long analyse(Ecology ecology) {
        long before = ecology.analysisNanos();
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                ecology.conditions(i * 900, j * 900);
            }
        }
        return ecology.analysisNanos() - before;
    }

    /** Generates the area's tiles on {@code threads}; returns the plants per layer. */
    private static long[] run(Ecology ecology, int threads, int tiles) throws Exception {
        long[] plants = new long[Layer.values().length];
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<EcologyTile>> results = new ArrayList<>();
            for (int j = 0; j < tiles; j++) {
                for (int i = 0; i < tiles; i++) {
                    long tx = i - tiles / 2;
                    long tz = j - tiles / 2;
                    results.add(pool.submit(() -> ecology.tile(tx, tz)));
                }
            }
            for (Future<EcologyTile> result : results) {
                EcologyTile tile = result.get();
                for (int k = 0; k < tile.instances().count(); k++) {
                    plants[ecology.species().get(tile.instances().species(k)).layer().ordinal()]++;
                }
            }
        }
        return plants;
    }
}
