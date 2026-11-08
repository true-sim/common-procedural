package com.truesim.common.procedural.flora;

/**
 * A diffuse albedo: how much light of each linear sRGB primary a surface reflects, 0 to 1. Physical values keep the
 * renderer's global illumination and snow honest (a white petal returns about 0.7 of the light, a ripe elderberry about
 * 0.03); the presets keep within what natural surfaces do, between about 0.02 and 0.9 (fresh snow, the brightest
 * natural surface, about 0.9: Warren, "Optical properties of snow", Rev. Geophys. 1982).
 *
 * @param r red, linear
 * @param g green, linear
 * @param b blue, linear
 */
public record Albedo(double r, double g, double b) {

    public Albedo {
        if (!(r >= 0 && r <= 1 && g >= 0 && g <= 1 && b >= 0 && b <= 1)) {
            throw new IllegalArgumentException("An albedo is within 0 and 1: " + r + ", " + g + ", " + b);
        }
    }

    /** The albedo of an sRGB-encoded color, e.g. {@code 0x8fc24d}. */
    public static Albedo srgb(int rgb) {
        return new Albedo(decode((rgb >> 16) & 255), decode((rgb >> 8) & 255), decode(rgb & 255));
    }

    /** The mean of the three primaries' albedos. */
    public double mean() {
        return (r + g + b) / 3;
    }

    /** {@code this} toward {@code other} by {@code t} (0 this, 1 other), in linear light. */
    public Albedo mix(Albedo other, double t) {
        return new Albedo(r + (other.r - r) * t, g + (other.g - g) * t, b + (other.b - b) * t);
    }

    /** The sRGB encoding of a linear value in [0, 1], as a byte value 0 to 255. */
    public static int encode(double linear) {
        double c = Math.clamp(linear, 0, 1);
        double s = c <= 0.0031308 ? 12.92 * c : 1.055 * com.truesim.common.math.Maths.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.round(s * 255);
    }

    /** The linear value of an sRGB-encoded byte value 0 to 255. */
    public static double decode(int srgb) {
        double s = srgb / 255.0;
        return s <= 0.04045 ? s / 12.92 : com.truesim.common.math.Maths.pow((s + 0.055) / 1.055, 2.4);
    }

    /** As an sRGB-encoded {@code 0xRRGGBB}. */
    public int toSrgb() {
        return encode(r) << 16 | encode(g) << 8 | encode(b);
    }
}
