package com.truesim.common.procedural.ecology;

import com.truesim.common.math.Maths;

/** Synthetic terrains for the tests: planes, valleys and mountains given as functions, no data. */
final class Terrains {

    /** Small analysis blocks for quick tests where catchments a kilometre long suffice. */
    static final EcologyParameters.Analysis QUICK = new EcologyParameters.Analysis(8, 512, 512, 150, 40, 32);

    private Terrains() {
    }

    /**
     * A plane of {@code degrees} rising {@code base} metres at z = 0 toward the north (facing south) or toward the south
     * (facing north).
     */
    static EcologyTerrain slope(double degrees, boolean facingSouth, double base) {
        double tan = Maths.tan(Maths.toRadians(degrees));
        return (x, z) -> base + (facingSouth ? -z : z) * tan;
    }

    /**
     * A V-shaped valley draining south along x = 0: sides of slope {@code side} (rise per run) rising to divides at
     * |x| = 1500 m, beyond which the next valleys fall away, and the floor falling {@code fall} per metre south.
     */
    static EcologyTerrain valley(double side, double fall) {
        return (x, z) -> {
            double ax = Math.abs(x);
            double across = ax <= 1500 ? ax : 3000 - ax;
            return 1000 + side * across - fall * z;
        };
    }

    /**
     * A U-shaped valley meandering south: a floor 360 m wide falling 0.5 % south and sloping 2 % toward its channel
     * at x = c(z) = 220 sin(z / 600), sides of 35 degrees above it.
     */
    static EcologyTerrain meanders() {
        return (x, z) -> {
            double channel = channel(z);
            double d = Math.abs(x - channel);
            double floor = 0.02 * Math.min(d, 180);
            double side = d > 180 ? 0.7 * (d - 180) : 0;
            return 800 - 0.005 * z + floor + side;
        };
    }

    /** The meandering valley's channel at {@code z}. */
    static double channel(double z) {
        return 220 * Maths.sin(z / 600);
    }

    /** Rugged mountains: ridges and valleys of a few kilometres, 600 to 2900 m, with steep faces. */
    static EcologyTerrain rugged() {
        return (x, z) -> {
            double a = x * 0.001;
            double b = z * 0.001;
            double ridge = 1 - Math.abs(Maths.sin(a * 0.9 + Maths.sin(b * 0.4) * 1.5));
            return 1300 + 900 * ridge * ridge + 500 * Maths.sin(a * 0.57) * Maths.cos(b * 0.43)
                + 120 * Maths.sin(a * 3.1 + b * 2.3) + 40 * Maths.sin(a * 9.7 - b * 7.1);
        };
    }
}
