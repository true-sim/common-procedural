package com.truesim.common.procedural.ecology;

import static org.assertj.core.api.Assertions.assertThat;

import com.truesim.common.math.Maths;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The conditions are a function of the point only: asked in any order, from one thread or many, while the terrain's
 * blocks are first analysed, they are the same bit for bit. Blocks either side of the origin once shared a cache key,
 * so the block analysed first answered for both.
 */
class ConcurrencyTest {

    /** A valley meandering across the blocks round the origin. */
    private static final EcologyTerrain VALLEY = (x, z) -> {
        double river = 150 * Maths.sin(z / 700);
        double d = Math.abs(x - river);
        return 800 + 0.03 * Math.min(d, 250) + 0.006 * z + (d > 250 ? 0.62 * (d - 250) : 0);
    };

    private static final int N = 120;

    private static double[] sample(boolean parallel, boolean reverse) {
        Ecology ecology = new Ecology(VALLEY, EcologyParameters.ALPS.withAnalysis(Terrains.QUICK).withSeed(3),
            AlpineSpecies.ALL);
        double[] out = new double[N * N * 6];
        IntStream rows = IntStream.range(0, N).map(j -> reverse ? N - 1 - j : j);
        (parallel ? rows.parallel() : rows).forEach(j -> {
            Site site = new Site();
            for (int i = 0; i < N; i++) {
                ecology.conditions(-600 + i * 10, -600 + j * 10, site);
                int a = 6 * (j * N + i);
                out[a] = site.hand;
                out[a + 1] = site.wetness;
                out[a + 2] = site.streamDistance;
                out[a + 3] = site.position;
                out[a + 4] = site.riverDistance;
                out[a + 5] = site.meadow;
            }
        });
        return out;
    }

    @Test
    void conditionsAreTheSameInAnyOrderAndFromManyThreads() {
        double[] forward = sample(false, false);
        assertThat(sample(false, true)).isEqualTo(forward);
        for (int run = 0; run < 3; run++) {
            assertThat(sample(true, run % 2 == 1)).isEqualTo(forward);
        }
    }
}
