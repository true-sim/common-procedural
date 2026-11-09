package com.truesim.common.procedural.water;

import com.truesim.common.procedural.ecology.EcologyTerrain;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A water network's recipe: the region, the parameters that differ from a base (the settings', or the defaults), the
 * mapped watercourses to follow and the materials. Recipes are small text files ({@code .water}) of
 * {@code key = value} lines; {@code #} starts a comment; every key but the region is optional:
 *
 * <pre>
 * # game://water/lauterbrunnen.water
 * region = -8000 -3000 6000 13000          # west north east south, metres in the terrain's frame
 * seed = 7
 * cell = 10                                # metres between flow samples
 * subsamples = 3
 * channel-area = 250000                    # square metres of catchment a channel starts from
 * runoff = 0.045                           # cubic metres a second per square kilometre
 * burn-depth = 20
 * min-length = 60
 * width-coefficient = 4                    # width = a Q^b
 * width-exponent = 0.5
 * manning = 0.045
 * fall-height = 8                          # the lowest waterfall, metres
 * stone-density = 0.06                     # stones a square metre
 * chunk = 512
 * spacing = 4
 * across = 4
 * courses = game://water/lauterbrunnen.courses     # mapped watercourses ({@link Watercourses}), followed
 * material.surface = game://water/river.material
 * material.falls = game://water/falls.material
 * material.jets = game://water/jets.material
 * material.stones = game://rocks/stone.material
 * </pre>
 *
 * A recipe is a value: equal recipes (and terrains and watercourses) give equal networks.
 *
 * @param region the region
 * @param parameters the parameters
 * @param courses the path of the mapped watercourses' file, or empty
 * @param materials material paths by slot: {@code surface}, {@code falls}, {@code jets}, {@code stones}
 */
public record WaterRecipe(WaterRegion region, WaterParameters parameters, String courses,
    Map<String, String> materials) {

    /** The material slots. */
    public static final List<String> SLOTS = List.of("surface", "falls", "jets", "stones");

    public WaterRecipe {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(courses, "courses");
        materials = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(materials));
    }

    /** The material of {@code slot}, or empty. */
    public String material(String slot) {
        return materials.getOrDefault(slot, "");
    }

    /** The network over {@code terrain}, from the flow alone. */
    public WaterNetwork network(EcologyTerrain terrain) {
        return WaterNetwork.build(terrain, region, parameters);
    }

    /** The network over {@code terrain} following {@code mapped} watercourses. */
    public WaterNetwork network(EcologyTerrain terrain, List<Watercourse> mapped) {
        return WaterNetwork.build(terrain, region, parameters, mapped);
    }

    /** Reads a recipe's text over the defaults. */
    public static WaterRecipe parse(String text) {
        return parse(text, WaterParameters.DEFAULTS);
    }

    /** Reads a recipe's text over {@code base} (e.g. {@link WaterSettings#parameters}). */
    public static WaterRecipe parse(String text, WaterParameters base) {
        WaterParameters p = base;
        WaterParameters.Flow flow = p.flow();
        WaterParameters.Channels ch = p.channels();
        WaterParameters.Falls falls = p.falls();
        WaterParameters.Stones stones = p.stones();
        WaterParameters.Surface surface = p.surface();
        WaterRegion region = null;
        String courses = "";
        Map<String, String> materials = new LinkedHashMap<>();
        int number = 0;
        for (String raw : text.split("\n")) {
            number++;
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).strip();
            if (line.isEmpty()) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                throw new IllegalArgumentException("Line " + number + " is not 'key = value': " + raw);
            }
            String key = line.substring(0, equals).strip();
            String value = line.substring(equals + 1).strip();
            switch (key) {
                case "region" -> {
                    String[] v = value.split("\\s+");
                    if (v.length != 4) {
                        throw new IllegalArgumentException("Line " + number + ": a region is west north east south");
                    }
                    region = new WaterRegion(Double.parseDouble(v[0]), Double.parseDouble(v[1]),
                        Double.parseDouble(v[2]), Double.parseDouble(v[3]));
                }
                case "seed" -> p = p.withSeed(Long.parseLong(value));
                case "cell" -> flow = new WaterParameters.Flow(number(value), flow.subsamples(), flow.channelArea(),
                    flow.runoff(), flow.burnDepth(), flow.minLength());
                case "subsamples" -> flow = new WaterParameters.Flow(flow.cell(), Integer.parseInt(value),
                    flow.channelArea(), flow.runoff(), flow.burnDepth(), flow.minLength());
                case "channel-area" -> flow = new WaterParameters.Flow(flow.cell(), flow.subsamples(), number(value),
                    flow.runoff(), flow.burnDepth(), flow.minLength());
                case "runoff" -> flow = new WaterParameters.Flow(flow.cell(), flow.subsamples(), flow.channelArea(),
                    number(value), flow.burnDepth(), flow.minLength());
                case "burn-depth" -> flow = new WaterParameters.Flow(flow.cell(), flow.subsamples(),
                    flow.channelArea(), flow.runoff(), number(value), flow.minLength());
                case "min-length" -> flow = new WaterParameters.Flow(flow.cell(), flow.subsamples(),
                    flow.channelArea(), flow.runoff(), flow.burnDepth(), number(value));
                case "width-coefficient" -> ch = new WaterParameters.Channels(number(value), ch.widthExponent(),
                    ch.minWidth(), ch.manning(), ch.minSlope(), ch.maxSpeed(), ch.smoothing(), ch.snap());
                case "width-exponent" -> ch = new WaterParameters.Channels(ch.widthCoefficient(), number(value),
                    ch.minWidth(), ch.manning(), ch.minSlope(), ch.maxSpeed(), ch.smoothing(), ch.snap());
                case "manning" -> ch = new WaterParameters.Channels(ch.widthCoefficient(), ch.widthExponent(),
                    ch.minWidth(), number(value), ch.minSlope(), ch.maxSpeed(), ch.smoothing(), ch.snap());
                case "fall-height" -> falls = new WaterParameters.Falls(falls.minGradient(), falls.freeGradient(),
                    number(value), falls.breakupCoefficient(), falls.breakupExponent(), falls.terminalSpeed(),
                    falls.atomisation(), falls.spread(), falls.jetSpacing(), falls.mistRadius(), falls.mistHeight(),
                    falls.mistDensity());
                case "stone-density" -> stones = new WaterParameters.Stones(number(value), stones.spread(),
                    stones.minSize(), stones.maxSize(), stones.banks(), stones.flood());
                case "chunk" -> surface = new WaterParameters.Surface(number(value), surface.spacing(),
                    surface.across());
                case "spacing" -> surface = new WaterParameters.Surface(surface.chunk(), number(value),
                    surface.across());
                case "across" -> surface = new WaterParameters.Surface(surface.chunk(), surface.spacing(),
                    Integer.parseInt(value));
                case "courses" -> courses = value;
                default -> {
                    if (!key.startsWith("material.") || !SLOTS.contains(key.substring("material.".length()))) {
                        throw new IllegalArgumentException("Line " + number + ": unknown key '" + key + "'");
                    }
                    materials.put(key.substring("material.".length()), value);
                }
            }
        }
        if (region == null) {
            throw new IllegalArgumentException("A water recipe names its region");
        }
        p = p.withFlow(flow).withChannels(ch).withFalls(falls).withStones(stones).withSurface(surface);
        return new WaterRecipe(region, p, courses, materials);
    }

    private static double number(String value) {
        return Double.parseDouble(value);
    }
}
