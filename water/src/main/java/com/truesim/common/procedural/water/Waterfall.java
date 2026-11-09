package com.truesim.common.procedural.water;

import java.util.List;

/**
 * A waterfall: where a channel drops at least {@link WaterParameters.Falls#minHeight} metres over ground steeper than
 * {@link WaterParameters.Falls#minGradient}. A <b>free</b> fall (mean gradient past {@link
 * WaterParameters.Falls#freeGradient}) leaves its lip as a sheet: it falls against quadratic drag toward the terminal
 * speed, hugging the face where the ground leans out further than the fall carries it, breaks up after {@link
 * #breakup()} metres into jets, and atomises, reaching its foot mostly as spray and mist when it is high. A
 * cascade (not free) runs down the rock as the channel's own surface, white with foam.
 *
 * <p>Its {@link #path() path} is the sheet's middle from the lip to the foot, sampled by fall; renderers draw the
 * sheet and the jets from {@link WaterNetwork#fallMesh(int)}, emit spray from {@link #spray()} and put a fog volume
 * where {@link #mist()} says (effects-atmosphere's {@code FogVolume.ellipsoid}, lit by the atmosphere).
 *
 * @param id its index in the network's falls
 * @param channel the channel it is on
 * @param lip the point of the channel at its lip
 * @param foot the point of the channel at its foot
 * @param free whether it falls free (else it cascades)
 * @param height metres from the lip's surface to the foot's
 * @param width the channel's width at the lip, metres
 * @param discharge cubic metres a second
 * @param lipSpeed the speed it leaves the lip at, metres a second (the brink's: critical flow)
 * @param thickness the sheet's thickness at the lip, metres
 * @param breakup metres of fall after which the sheet breaks up into jets (Horeni)
 * @param atomised the fraction of its water reaching the foot as spray and mist
 * @param path the sheet's middle, lip to foot
 * @param spray where spray comes off it, top down, the foot's plunge last
 * @param mist the mist at its foot
 */
public record Waterfall(int id, int channel, int lip, int foot, boolean free, double height, double width,
    double discharge, double lipSpeed, double thickness, double breakup, double atomised, Path path,
    List<Spray> spray, Mist mist) {

    public Waterfall {
        spray = List.copyOf(spray);
    }

    /**
     * The sheet's middle line, sampled every few metres of fall: positions, the velocity there, the time since the lip
     * and the sheet's width.
     *
     * @param x east, metres
     * @param y up
     * @param z south
     * @param vx the velocity, metres a second
     * @param vy the velocity's vertical part (negative: falling)
     * @param vz the velocity's south part
     * @param time seconds since the lip
     * @param width the sheet's width, metres
     */
    public record Path(double[] x, double[] y, double[] z, double[] vx, double[] vy, double[] vz, double[] time,
        double[] width) {

        public int count() {
            return x.length;
        }

        /** Metres fallen at sample {@code i}. */
        public double fallen(int i) {
            return y[0] - y[i];
        }

        /** The speed at sample {@code i}. */
        public double speed(int i) {
            return Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
        }
    }

    /**
     * A source of spray: water leaving the falling sheet as drops, around a point.
     *
     * @param x east, metres
     * @param y up
     * @param z south
     * @param radius metres round the point it comes off over
     * @param rate cubic metres of water a second
     * @param speed the falling water's speed there, metres a second
     */
    public record Spray(double x, double y, double z, double radius, double rate, double speed) {
    }

    /**
     * The mist at a fall's foot: an ellipsoid of fog, densest at its centre's height and thinning upward, for a fog
     * volume (effects-atmosphere's {@code FogVolume}: {@code ELLIPSOID}, these radii, density and falloff).
     *
     * @param x its centre, east, metres
     * @param y its centre's height
     * @param z its centre, south
     * @param radiusX its radius east-west, metres
     * @param radiusY its vertical radius
     * @param radiusZ its radius north-south
     * @param density extinction per metre at the centre's height
     * @param falloff per metre of height above the centre: the density times {@code exp(-falloff * rise)}
     */
    public record Mist(double x, double y, double z, double radiusX, double radiusY, double radiusZ, double density,
        double falloff) {
    }
}
