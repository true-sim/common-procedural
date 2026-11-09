package com.truesim.common.procedural.water;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.effects.terrain.data.TerrainSurface;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The network over Lauterbrunnen: its size, rivers and falls, printed (TRUESIM_PICTURES=true). */
class LauterbrunnenStats {

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_PICTURES", matches = "true")
    void stats() throws Exception {
        TerrainSurface surface = Lauterbrunnen.surface();
        assumeTrue(surface != null, "effects-terrain's cache holds no Lauterbrunnen");
        long start = System.nanoTime();
        WaterNetwork network = WaterNetwork.build(Lauterbrunnen.terrain(surface), Lauterbrunnen.REGION,
            WaterParameters.DEFAULTS);
        long ms = (System.nanoTime() - start) / 1_000_000;
        double length = 0;
        int points = 0;
        for (Channel c : network.channels()) {
            length += c.distance(c.count() - 1);
            points += c.count();
        }
        System.out.printf(Locale.ROOT, "built in %d ms: %d channels, %.1f km, %d points, %d stones, %d falls, "
            + "%d chunks%n", ms, network.channels().size(), length / 1000, points, network.stones().size(),
            network.falls().size(), network.chunks().size());
        List<Channel> big = new ArrayList<>(network.channels());
        big.sort(Comparator.comparingDouble((Channel c) -> -c.discharge(c.count() - 1)));
        for (Channel c : big.subList(0, Math.min(8, big.size()))) {
            int e = c.count() - 1;
            System.out.printf(Locale.ROOT, "channel %d order %d: (%.0f, %.0f) -> (%.0f, %.0f), Q %.2f m3/s, "
                + "w %.1f m, d %.2f m, v %.2f m/s, %.0f m%n", c.id(), c.order(), c.x(0), c.z(0), c.x(e), c.z(e),
                c.discharge(e), c.width(e), c.depth(e), c.speed(e), c.distance(e));
        }
        List<Waterfall> falls = new ArrayList<>(network.falls());
        falls.sort(Comparator.comparingDouble(f -> -f.height()));
        for (Waterfall f : falls.subList(0, Math.min(15, falls.size()))) {
            Channel c = network.channels().get(f.channel());
            System.out.printf(Locale.ROOT, "fall %d %s: lip (%.0f, %.0f, %.0f) foot (%.0f, %.0f, %.0f), %.0f m, "
                + "Q %.3f, w %.1f, breakup %.1f m, atomised %.2f, mist r %.0f d %.4f%n", f.id(),
                f.free() ? "free" : "cascade", c.x(f.lip()), c.surface(f.lip()), c.z(f.lip()), c.x(f.foot()),
                c.surface(f.foot()), c.z(f.foot()), f.height(), f.discharge(), f.width(), f.breakup(), f.atomised(),
                f.mist().radiusX(), f.mist().density());
        }
    }
}
