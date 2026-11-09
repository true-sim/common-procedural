package com.truesim.common.procedural.water;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.ecology.EcologyTerrain;

/** Synthetic terrains for the tests, as functions. */
final class Landscapes {

    /** The valley's region: 3 km across, 4 km down. */
    static final WaterRegion VALLEY = new WaterRegion(-1500, 0, 1500, 4000);

    private Landscapes() {
    }

    /**
     * A valley draining south along x = 0 (its floor falling 2 % south, 1 % toward its middle), its sides of 30 %
     * furrowed by gullies every 600 m that make tributaries.
     */
    static EcologyTerrain valley() {
        return (x, z) -> {
            double ax = Math.abs(x);
            double gully = 25 * Maths.sin(z / 600 * Maths.TAU) * Maths.smoothstep(100, 400, ax);
            return 1500 - 0.02 * z + 0.01 * Math.min(ax, 150) + 0.3 * Math.max(0, ax - 150) + gully;
        };
    }

    /**
     * A plateau draining south at 2 % to a cliff at z = 1000, where the ground drops {@code drop} metres over
     * {@code run} metres to a floor falling 1 % south; a shallow trough (1 % across) keeps the stream along x = 0.
     */
    static EcologyTerrain cliff(double drop, double run) {
        return (x, z) -> {
            double across = 0.04 * Math.min(Math.abs(x), 400) + 0.2 * Math.max(0, Math.abs(x) - 400);
            double base;
            if (z < 1000) {
                base = 2000 - 0.02 * z;
            } else if (z < 1000 + run) {
                base = 1980 - drop * (z - 1000) / run;
            } else {
                base = 1980 - drop - 0.01 * (z - 1000 - run);
            }
            return base + across;
        };
    }

    /** A plane falling 1 % east and 0.5 % south, with no valleys of its own: watercourses make the streams. */
    static EcologyTerrain plane() {
        return (x, z) -> 1000 - 0.01 * x - 0.005 * z;
    }
}
