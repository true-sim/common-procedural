package com.truesim.common.procedural.flora;

import com.truesim.annotations.AllowAllocation;
import java.util.ArrayList;
import java.util.List;

/**
 * The colors of a species' small parts (flowers, fruits, cones, stalks, a plant's leaves and blades) as albedo ramps,
 * and the small texture they are drawn from. The vertex format's color carries the wind, so these parts take their
 * color from texture coordinates instead: v picks the part's row, u the place along its ramp (a petal's age, a fruit's
 * ripeness, a cone's maturity, a leaf's senescence). A material samples {@link #texels()} as its base color.
 *
 * <p>The texture is {@link #WIDTH} texels wide, {@link #ROW} texels per part tall, sRGB-encoded: u at texel centres
 * reproduces the ramp under linear filtering, and a row tall enough keeps parts apart in the first mip levels (a part's
 * u and v hardly change across a triangle, so it samples the top levels).
 *
 * @param ramps one per {@link Part}, in its order
 */
public record Palette(List<Ramp> ramps) {

    /** The parts, one row each. */
    public enum Part {
        /** Petals, rays, bracts, a corolla: u the age, 0 a closed bud, 0.5 just open, 1 fading. */
        PETAL,
        /** A flower's centre: disc florets, stamens, a head's middle. */
        CENTRE,
        /** Sepals, a calyx, phyllaries: u 0 fresh, 1 dried (a fruit's calyx). */
        SEPAL,
        /** Stalks: peduncles, pedicels, a plant's stems: u 0 in flower, 1 under ripe fruit. */
        STALK,
        /** Fruits and berries: u the ripeness, 0 unripe, 1 ripe. */
        FRUIT,
        /** A second fruit color: another cultivar (green apples beside red ones). */
        FRUIT_ALT,
        /** A plant's leaves: u the senescence, 0 summer green, 1 autumn. */
        LEAF,
        /** Grass blades: u the senescence, 0 green, 1 straw. */
        BLADE,
        /** Cones: u the maturity, 0 a young (female) cone, 1 ripe and open. */
        CONE;

        private static final Part[] ALL = values();

        /** The parts in order. */
        static Part of(int ordinal) {
            return ALL[ordinal];
        }
    }

    /** Texels across a ramp. */
    public static final int WIDTH = 16;
    /** Texels down per part. */
    public static final int ROW = 4;

    public Palette {
        ramps = List.copyOf(ramps);
        if (ramps.size() != Part.ALL.length) {
            throw new IllegalArgumentException("A palette has a ramp per part: " + ramps.size());
        }
    }

    private static final Albedo LEAF_GREEN = new Albedo(0.045, 0.1, 0.018);
    private static final Albedo STEM_GREEN = new Albedo(0.07, 0.12, 0.03);
    private static final Albedo DRY = new Albedo(0.12, 0.075, 0.04);

    /**
     * Greens and browns for every part: typical leaf albedo in the visible (about 0.05 to 0.12, peaking in the green:
     * Jacquemoud and Baret, "PROSPECT: a model of leaf optical properties spectra", 1990), straw and dry bracts
     * brighter and yellower, cones mid brown.
     */
    public static final Palette DEFAULT = new Palette(List.of(
        Ramp.of(new Albedo(0.6, 0.6, 0.55)),                                                    // petal
        Ramp.of(new Albedo(0.6, 0.45, 0.08)),                                                   // centre
        new Ramp(STEM_GREEN, STEM_GREEN, DRY),                                                  // sepal
        Ramp.of(STEM_GREEN),                                                                    // stalk
        Ramp.of(new Albedo(0.1, 0.16, 0.04), new Albedo(0.3, 0.05, 0.03)),                     // fruit
        Ramp.of(new Albedo(0.1, 0.16, 0.04), new Albedo(0.3, 0.32, 0.06)),                     // fruit alt
        new Ramp(LEAF_GREEN, new Albedo(0.09, 0.11, 0.025), new Albedo(0.28, 0.17, 0.04)),     // leaf
        new Ramp(new Albedo(0.06, 0.12, 0.025), new Albedo(0.17, 0.17, 0.06), new Albedo(0.36, 0.3, 0.16)), // blade
        new Ramp(new Albedo(0.22, 0.04, 0.07), new Albedo(0.12, 0.14, 0.04), new Albedo(0.17, 0.09, 0.045)))); // cone

    /** This palette with {@code ramp} for {@code part}. */
    @AllowAllocation("making species")
    public Palette with(Part part, Ramp ramp) {
        List<Ramp> copy = new ArrayList<>(ramps);
        copy.set(part.ordinal(), ramp);
        return new Palette(copy);
    }

    /** The ramp of {@code part}. */
    public Ramp ramp(Part part) {
        return ramps.get(part.ordinal());
    }

    /** The albedo of {@code part} at {@code t}. */
    public Albedo albedo(Part part, double t) {
        return ramp(part).at(t);
    }

    /** The texture coordinate u of {@code t} in [0, 1] along a ramp: texel centres from the first to the last. */
    public static double u(double t) {
        return (0.5 + Math.clamp(t, 0, 1) * (WIDTH - 1)) / WIDTH;
    }

    /** The texture coordinate v of {@code part}'s row: its middle. */
    public static double v(Part part) {
        return (part.ordinal() + 0.5) / Part.ALL.length;
    }

    /** The ramp position of a texture coordinate u (the inverse of {@link #u}). */
    public static double t(double u) {
        return Math.clamp((u * WIDTH - 0.5) / (WIDTH - 1), 0, 1);
    }

    /** The part of a texture coordinate v. */
    public static Part part(double v) {
        return Part.of(Math.clamp((int) Math.floor(v * Part.ALL.length), 0, Part.ALL.length - 1));
    }

    /** The texture's width in texels. */
    public int width() {
        return WIDTH;
    }

    /** The texture's height in texels. */
    public int height() {
        return ROW * Part.ALL.length;
    }

    /** The texture: {@link #width()} x {@link #height()} texels of sRGB-encoded RGBA bytes, rows top down, alpha 255. */
    @AllowAllocation("a texture, at load time")
    public byte[] texels() {
        int w = width();
        int h = height();
        byte[] out = new byte[4 * w * h];
        for (int y = 0; y < h; y++) {
            Ramp ramp = ramps.get(y / ROW);
            for (int x = 0; x < w; x++) {
                Albedo a = ramp.at((double) x / (WIDTH - 1));
                int o = 4 * (y * w + x);
                out[o] = (byte) Albedo.encode(a.r());
                out[o + 1] = (byte) Albedo.encode(a.g());
                out[o + 2] = (byte) Albedo.encode(a.b());
                out[o + 3] = (byte) 255;
            }
        }
        return out;
    }
}
