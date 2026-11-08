package com.truesim.common.procedural.ecology;

/**
 * A plant species as the ecology sees it: where it can grow (its {@link Envelope}), how it grows (its {@link Habit}),
 * its indicator values, and where those come from. Its {@code id} names it to the generators and renderers (a
 * common-procedural tree preset such as {@code spruce}, a flora preset such as {@code gentian}); a recipe maps it to
 * the model its instances draw.
 *
 * @param id the species' id: lower case, words joined by '-'
 * @param name its scientific name
 * @param layer the layer it grows in
 * @param envelope its tolerances
 * @param habit its size and growth
 * @param indicators its Ellenberg indicator values
 * @param source where its envelope comes from
 */
public record Species(String id, String name, Layer layer, Envelope envelope, Habit habit, Indicators indicators,
    String source) {

    /**
     * A species' tolerances: each a response from 0 to 1, multiplied into its suitability.
     *
     * @param temperature the growing season's mean temperature, degrees Celsius
     * @param moisture the site's moisture on Ellenberg's scale (1 very dry, 5 fresh, 7 damp, 9 wet, 10 shallow water,
     *     11 to 12 in water)
     * @param snowDays days of snow cover a year
     * @param exposure how much wind exposure it bears, 0 (sheltered only) to 1 (ridges)
     * @param soil how well it grows on soil, 0 to 1
     * @param scree on scree
     * @param rock on rock (in crevices)
     * @param riparian how much it favours (above 0) or shuns (below 0) riparian ground along streams, -1 to 1
     */
    public record Envelope(Range temperature, Range moisture, Range snowDays, double exposure, double soil,
        double scree, double rock, double riparian) {
    }

    /**
     * A species' growth.
     *
     * @param height a grown plant's height, metres
     * @param crown its crown's radius, metres (a herb's clump's)
     * @param weight how strongly it competes in its layer where it is suited (1 typical)
     * @param cluster the scale of its groups, metres (groves, clumps); 0 for none
     * @param clumping how strongly it groups, 0 (evenly) to 1 (in distinct groups)
     * @param krummholz whether it grows stunted and spreading near its cold limit (trees at the treeline)
     */
    public record Habit(double height, double crown, double weight, double cluster, double clumping,
        boolean krummholz) {
    }

    /**
     * Ellenberg's indicator values (Ellenberg et al. 1991), 0 where the species is indifferent ('x'): light (1 deep
     * shade to 9 full light), temperature (1 alpine-nival to 9 warm), moisture (1 to 12) and reaction (1 acid to 9
     * basic). Light sets how much shade it bears under taller layers.
     */
    public record Indicators(int light, int temperature, int moisture, int reaction) {
    }

    /** This species competing with weight {@code weight}. */
    public Species withWeight(double weight) {
        Habit h = habit;
        return new Species(id, name, layer, envelope,
            new Habit(h.height, h.crown, weight, h.cluster, h.clumping, h.krummholz), indicators, source);
    }

    /**
     * The light it needs, as a fraction of full daylight, from its light value (Ellenberg's scale is roughly
     * logarithmic in relative illumination: about 2 % at 1, 10 % at 5, 50 % at 9; indifferent counts as 6).
     */
    public double lightNeed() {
        int light = indicators.light == 0 ? 6 : indicators.light;
        return 0.02 * com.truesim.common.math.Maths.pow(1.5, light - 1);
    }

    /**
     * How much of the shade of a closed taller layer it bears, 0 (none: full-light plants) to 1 (shade plants), from
     * its light value (indifferent counts as 6).
     */
    public double shadeTolerance() {
        int light = indicators.light == 0 ? 6 : indicators.light;
        return Math.clamp((8 - light) / 5.0, 0, 1);
    }
}
