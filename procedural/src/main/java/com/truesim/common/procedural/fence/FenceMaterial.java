package com.truesim.common.procedural.fence;

import java.util.List;

/**
 * What a fence's pieces are made of, as the colors its vertices carry: the albedo when new and when fully weathered
 * (linear sRGB), which submesh it draws in, and whether it is paint over wood. Weathering, lichen and moss are the
 * fence's ({@link FenceOptions}); each piece varies around them.
 *
 * <p>Albedos are rough values for rendering from published reflectances: fresh spruce sapwood about 0.5 to 0.6 in the
 * red, weathered (silvered) softwood a neutral grey about 0.3 (lignin washed out of the surface, Williams 2005,
 * "Weathering of wood"), gneiss and granite 0.25 to 0.35, limestone 0.4 to 0.5, zinc-coated steel about 0.6.
 *
 * @param name a name, e.g. for recipes ({@code weathered-spruce})
 * @param part the submesh it draws in: {@link Fence#WOOD}, {@link Fence#STONE} or {@link Fence#METAL}
 * @param fresh the albedo when new, linear r, g, b
 * @param weathered the albedo when fully weathered
 * @param painted paint over wood: weathering fades and peels it, showing grey wood
 */
public record FenceMaterial(String name, int part, double[] fresh, double[] weathered, boolean painted) {

    public FenceMaterial {
        if (part < 0 || part > 2 || fresh.length != 3 || weathered.length != 3) {
            throw new IllegalArgumentException(name + ": a part 0 to 2 and two r, g, b albedos");
        }
        fresh = fresh.clone();
        weathered = weathered.clone();
    }

    @Override
    public double[] fresh() {
        return fresh.clone();
    }

    @Override
    public double[] weathered() {
        return weathered.clone();
    }

    double fresh(int channel) {
        return fresh[channel];
    }

    double weathered(int channel) {
        return weathered[channel];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FenceMaterial m && m.name.equals(name) && m.part == part && m.painted == painted
            && java.util.Arrays.equals(m.fresh, fresh) && java.util.Arrays.equals(m.weathered, weathered);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + java.util.Arrays.hashCode(fresh);
    }

    @Override
    public String toString() {
        return "FenceMaterial[" + name + "]";
    }

    /** Spruce: pale yellow when sawn, silver grey when weathered (about 0.26). */
    public static final FenceMaterial WEATHERED_SPRUCE = new FenceMaterial("weathered-spruce", Fence.WOOD,
        new double[] {0.58, 0.43, 0.25}, new double[] {0.27, 0.26, 0.245}, false);
    /** Larch: reddish when sawn, a darker, warmer silver-brown when weathered. */
    public static final FenceMaterial WEATHERED_LARCH = new FenceMaterial("weathered-larch", Fence.WOOD,
        new double[] {0.52, 0.30, 0.16}, new double[] {0.20, 0.18, 0.16}, false);
    /** Freshly cut spruce: weathering turns it grey slowly (use a low weathering). */
    public static final FenceMaterial FRESH_WOOD = new FenceMaterial("fresh-wood", Fence.WOOD,
        new double[] {0.62, 0.46, 0.27}, new double[] {0.36, 0.33, 0.29}, false);
    /** Oxblood red paint, the barns' and fences' red of the Alps and Scandinavia. */
    public static final FenceMaterial PAINTED_RED = new FenceMaterial("painted-red", Fence.WOOD,
        new double[] {0.26, 0.045, 0.03}, new double[] {0.30, 0.10, 0.08}, true);
    /** White paint. */
    public static final FenceMaterial PAINTED_WHITE = new FenceMaterial("painted-white", Fence.WOOD,
        new double[] {0.70, 0.70, 0.67}, new double[] {0.56, 0.55, 0.52}, true);
    /** Dark green paint. */
    public static final FenceMaterial PAINTED_GREEN = new FenceMaterial("painted-green", Fence.WOOD,
        new double[] {0.04, 0.12, 0.06}, new double[] {0.10, 0.16, 0.11}, true);
    /** Gneiss, the grey stone of the Lauterbrunnen valley's walls. */
    public static final FenceMaterial GNEISS = new FenceMaterial("gneiss", Fence.STONE,
        new double[] {0.25, 0.245, 0.23}, new double[] {0.21, 0.205, 0.19}, false);
    /** Pale limestone. */
    public static final FenceMaterial LIMESTONE = new FenceMaterial("limestone", Fence.STONE,
        new double[] {0.52, 0.50, 0.45}, new double[] {0.42, 0.41, 0.38}, false);
    /** Zinc-coated (galvanised) steel wire: bright when new, dull grey and spotted with rust when old. */
    public static final FenceMaterial GALVANISED = new FenceMaterial("galvanised", Fence.METAL,
        new double[] {0.62, 0.63, 0.64}, new double[] {0.40, 0.40, 0.40}, false);

    /** Every preset. */
    public static final List<FenceMaterial> PRESETS = List.of(WEATHERED_SPRUCE, WEATHERED_LARCH, FRESH_WOOD,
        PAINTED_RED, PAINTED_WHITE, PAINTED_GREEN, GNEISS, LIMESTONE, GALVANISED);

    /** The preset named {@code name}. */
    public static FenceMaterial preset(String name) {
        for (FenceMaterial preset : PRESETS) {
            if (preset.name().equals(name)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("No fence material '" + name + "'");
    }
}
