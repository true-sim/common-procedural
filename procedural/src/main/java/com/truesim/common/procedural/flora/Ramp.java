package com.truesim.common.procedural.flora;

/**
 * Three albedos a part passes through as a value goes from 0 to 1 (age, ripeness, maturity, senescence), linear in
 * between: e.g. a rowan berry green at 0, orange at 0.5, scarlet at 1.
 *
 * @param start the albedo at 0
 * @param middle the albedo at 0.5
 * @param end the albedo at 1
 */
public record Ramp(Albedo start, Albedo middle, Albedo end) {

    /** A part that keeps one albedo. */
    public static Ramp of(Albedo albedo) {
        return new Ramp(albedo, albedo, albedo);
    }

    /** From {@code start} to {@code end} through their mean. */
    public static Ramp of(Albedo start, Albedo end) {
        return new Ramp(start, start.mix(end, 0.5), end);
    }

    /** The albedo at {@code t} in [0, 1]. */
    public Albedo at(double t) {
        double c = Math.clamp(t, 0, 1);
        return c <= 0.5 ? start.mix(middle, 2 * c) : middle.mix(end, 2 * c - 1);
    }
}
