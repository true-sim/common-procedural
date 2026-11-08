package com.truesim.common.procedural.ecology;

import java.util.Arrays;
import java.util.Objects;

/**
 * A region's climate, as the ecology needs it: the growing season's mean air temperature at sea level and how fast it
 * falls with altitude, the latitude the sun's paths are worked out for, how much a slope's sunshine warms it, and how
 * long snow lies at a given temperature.
 *
 * <ul>
 *   <li><b>Temperature:</b> {@code seasonTemperature - lapseRate * altitude + aspectWarming * (insolation - 1)}, the
 *       growing season's mean. Trees end where it is about 6.4 degrees Celsius (Koerner and Paulsen 2004, worldwide
 *       treelines; Koerner 2012), so the defaults put the treeline at about 2100 m on level ground in the northern
 *       Alps and higher on sunny slopes. The lapse rate is 0.6 degrees per 100 m (Rolland 2003 measured 0.54 to 0.58
 *       a year in the Alps, steeper in summer).</li>
 *   <li><b>Aspect:</b> a slope's growing-season sunshine relative to level ground ({@link Insolation}, from the sun's
 *       paths at the latitude) warms or cools it by {@code aspectWarming} degrees per unit: soil and canopy
 *       temperatures differ by more than the air's, and the treeline follows them (higher on south slopes by roughly
 *       100 to 200 m in the Alps).</li>
 *   <li><b>Snow:</b> days of snow cover a year, piecewise linear in that temperature through {@code snowKnots}
 *       (temperature, days) pairs, warmest first: by default about 40 days in the lowlands, about 165 at 1560 m, about
 *       255 at 2540 m (approximate long-term values of Swiss stations, Bern, Davos, Weissfluhjoch; Laternser and
 *       Schneebeli 2003), lying all year near 2900 m.</li>
 *   <li><b>Belts:</b> {@link Belt} boundaries at {@code beltAltitudes} on level ground: colline, montane, subalpine,
 *       alpine, nival (800, 1500, 2200, 3000 m; Ozenda 1985), as temperatures, so aspect shifts them.</li>
 * </ul>
 *
 * @param latitude degrees north
 * @param seasonTemperature the growing season's mean at sea level, degrees Celsius
 * @param lapseRate degrees per metre
 * @param aspectWarming degrees per unit of relative insolation above level ground's
 * @param snowKnots (temperature, days) pairs, temperatures falling
 * @param beltAltitudes the four belt boundaries on level ground, metres, rising
 */
public record Climate(double latitude, double seasonTemperature, double lapseRate, double aspectWarming,
    double[] snowKnots, double[] beltAltitudes) {

    /** The northern Alps (Bernese Oberland, 46.6 degrees north). */
    public static final Climate NORTHERN_ALPS = new Climate(46.6, 19.0, 0.006, 2.5,
        new double[] {15.7, 40, 9.64, 165, 3.76, 255, 1.6, 365}, new double[] {800, 1500, 2200, 3000});

    public Climate {
        snowKnots = snowKnots.clone();
        beltAltitudes = beltAltitudes.clone();
        if (snowKnots.length < 4 || snowKnots.length % 2 != 0) {
            throw new IllegalArgumentException("Snow knots are at least two (temperature, days) pairs");
        }
        if (beltAltitudes.length != Belt.ALL.length - 1) {
            throw new IllegalArgumentException("Four belt boundaries");
        }
    }

    @Override
    public double[] snowKnots() {
        return snowKnots.clone();
    }

    @Override
    public double[] beltAltitudes() {
        return beltAltitudes.clone();
    }

    /** This climate at another latitude. */
    public Climate withLatitude(double degrees) {
        return new Climate(degrees, seasonTemperature, lapseRate, aspectWarming, snowKnots, beltAltitudes);
    }

    /** This climate with another sea-level growing-season temperature. */
    public Climate withSeasonTemperature(double celsius) {
        return new Climate(latitude, celsius, lapseRate, aspectWarming, snowKnots, beltAltitudes);
    }

    /** This climate with another lapse rate, degrees per metre. */
    public Climate withLapseRate(double perMetre) {
        return new Climate(latitude, seasonTemperature, perMetre, aspectWarming, snowKnots, beltAltitudes);
    }

    /** This climate with another warming per unit of relative insolation. */
    public Climate withAspectWarming(double degrees) {
        return new Climate(latitude, seasonTemperature, lapseRate, degrees, snowKnots, beltAltitudes);
    }

    /** The growing season's mean temperature at {@code altitude} with relative insolation {@code insolation}. */
    public double temperature(double altitude, double insolation) {
        return seasonTemperature - lapseRate * altitude + aspectWarming * (insolation - 1);
    }

    /** The altitude where level ground has growing-season temperature {@code celsius}. */
    public double altitudeOf(double celsius) {
        return (seasonTemperature - celsius) / lapseRate;
    }

    /** Days of snow cover a year where the growing season's mean is {@code celsius}. */
    public double snowDays(double celsius) {
        int n = snowKnots.length / 2;
        if (celsius >= snowKnots[0]) {
            double slope = (snowKnots[3] - snowKnots[1]) / (snowKnots[2] - snowKnots[0]);
            return Math.max(0, snowKnots[1] + (celsius - snowKnots[0]) * slope);
        }
        for (int k = 0; k < n - 1; k++) {
            double t1 = snowKnots[2 * k + 2];
            if (celsius >= t1) {
                double t0 = snowKnots[2 * k];
                double f = (celsius - t0) / (t1 - t0);
                return snowKnots[2 * k + 1] + f * (snowKnots[2 * k + 3] - snowKnots[2 * k + 1]);
            }
        }
        return Math.min(365, snowKnots[snowKnots.length - 1]);
    }

    /** The belt where the growing season's mean is {@code celsius}. */
    public Belt belt(double celsius) {
        for (int b = 0; b < beltAltitudes.length; b++) {
            if (celsius > temperature(beltAltitudes[b], 1)) {
                return Belt.ALL[b];
            }
        }
        return Belt.NIVAL;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Climate c && latitude == c.latitude && seasonTemperature == c.seasonTemperature
            && lapseRate == c.lapseRate && aspectWarming == c.aspectWarming && Arrays.equals(snowKnots, c.snowKnots)
            && Arrays.equals(beltAltitudes, c.beltAltitudes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, seasonTemperature, lapseRate, aspectWarming, Arrays.hashCode(snowKnots),
            Arrays.hashCode(beltAltitudes));
    }

    @Override
    public String toString() {
        return "Climate[latitude=" + latitude + ", seasonTemperature=" + seasonTemperature + ", lapseRate="
            + lapseRate + ", aspectWarming=" + aspectWarming + ", snowKnots=" + Arrays.toString(snowKnots)
            + ", beltAltitudes=" + Arrays.toString(beltAltitudes) + "]";
    }
}
