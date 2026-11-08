package com.truesim.common.procedural.ecology;

import com.truesim.common.math.Maths;

/**
 * A slope's growing-season sunshine relative to level ground's, from the sun's paths at a latitude: every sixth day
 * from mid-April to mid-October, the sun's declination (Cooper 1969) and its hour angle 240 times a day give its
 * direction; the clear-sky direct beam (transmittance 0.7 per air mass) falls on the slope by the cosine of incidence,
 * the diffuse sky (isotropic, 15 % of the beam's scale) by the part of the sky the slope sees. Shadows of the
 * surrounding terrain are left out (a deep valley's horizon lowers both). Worked out once per latitude into a table
 * over slope (every 2 degrees) and aspect (every 5), read bilinearly.
 */
public final class Insolation {

    private static final int SLOPES = 46;
    private static final int ASPECTS = 73;
    private static final double TRANSMITTANCE = 0.7;
    private static final double DIFFUSE = 0.15;
    private static final int HOURS = 240;

    private final double latitude;
    private final double[] table = new double[SLOPES * ASPECTS];

    /** The table for {@code latitude} degrees north. */
    public Insolation(double latitude) {
        this.latitude = latitude;
        double phi = Maths.toRadians(latitude);
        double sinPhi = Maths.sin(phi);
        double cosPhi = Maths.cos(phi);
        double[] sun = new double[32 * HOURS * 4];
        int count = 0;
        double diffuseFlat = 0;
        for (int day = 105; day <= 288; day += 6) {
            double delta = Maths.toRadians(23.45) * Maths.sin(Maths.TAU * (284 + day) / 365.0);
            double sinDelta = Maths.sin(delta);
            double cosDelta = Maths.cos(delta);
            for (int k = 0; k < HOURS; k++) {
                double omega = Maths.TAU * (k + 0.5) / HOURS - Maths.PI;
                double up = sinPhi * sinDelta + cosPhi * cosDelta * Maths.cos(omega);
                if (up <= 0.01) {
                    continue;
                }
                sun[4 * count] = -cosDelta * Maths.sin(omega); // east
                sun[4 * count + 1] = up;
                sun[4 * count + 2] = sinDelta * cosPhi - cosDelta * Maths.cos(omega) * sinPhi; // north
                sun[4 * count + 3] = Maths.pow(TRANSMITTANCE, 1 / up);
                diffuseFlat += DIFFUSE * up;
                count++;
            }
        }
        double flat = diffuseFlat;
        for (int s = 0; s < count; s++) {
            flat += sun[4 * s + 3] * sun[4 * s + 1];
        }
        for (int i = 0; i < SLOPES; i++) {
            double slope = Maths.toRadians(2.0 * i);
            double sinS = Maths.sin(slope);
            double cosS = Maths.cos(slope);
            for (int j = 0; j < ASPECTS; j++) {
                double aspect = Maths.toRadians(5.0 * j);
                double ne = sinS * Maths.sin(aspect);
                double nn = sinS * Maths.cos(aspect);
                double total = diffuseFlat * (1 + cosS) / 2;
                for (int s = 0; s < count; s++) {
                    double cos = ne * sun[4 * s] + cosS * sun[4 * s + 1] + nn * sun[4 * s + 2];
                    if (cos > 0) {
                        total += sun[4 * s + 3] * cos;
                    }
                }
                table[i * ASPECTS + j] = total / flat;
            }
        }
    }

    public double latitude() {
        return latitude;
    }

    /**
     * The relative insolation of a slope of {@code slope} degrees facing {@code aspect} degrees (clockwise from north:
     * 180 faces south): 1 on level ground, above 1 on slopes facing the sun.
     */
    public double relative(double slope, double aspect) {
        double si = Math.clamp(slope / 2, 0, SLOPES - 1.000001);
        double a = aspect - 360 * Math.floor(aspect / 360);
        double aj = Math.clamp(a / 5, 0, ASPECTS - 1.000001);
        int i = (int) si;
        int j = (int) aj;
        double fi = si - i;
        double fj = aj - j;
        double v00 = table[i * ASPECTS + j];
        double v01 = table[i * ASPECTS + j + 1];
        double v10 = table[(i + 1) * ASPECTS + j];
        double v11 = table[(i + 1) * ASPECTS + j + 1];
        return (v00 * (1 - fj) + v01 * fj) * (1 - fi) + (v10 * (1 - fj) + v11 * fj) * fi;
    }
}
