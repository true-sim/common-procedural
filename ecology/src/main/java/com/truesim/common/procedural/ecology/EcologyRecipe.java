package com.truesim.common.procedural.ecology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An ecology's recipe: its parameters, its species and the model each species' instances draw. Recipes are small text
 * files ({@code .ecology}) of {@code key = value} lines; {@code #} starts a comment; every key is optional:
 *
 * <pre>
 * # game://ecology/lauterbrunnen.ecology
 * seed = 7
 * latitude = 46.6                 # degrees north
 * temperature = 19.0              # the growing season's mean at sea level, degrees Celsius
 * lapse = 0.006                   # degrees per metre
 * aspect-warming = 2.5            # degrees per unit of relative insolation
 * tile = 256                      # metres
 * cell = 4                        # metres between field samples
 * meadows = true                  # land kept open (false: the potential natural vegetation)
 * meadows.slope = 26              # degrees
 * meadows.altitude = 1850         # metres
 * species = spruce fir larch beech mugo alpenrose gentian grass    # AlpineSpecies ids; all of them by default
 * weight.beech = 1.4              # a species' competitive weight
 * model.spruce = game://forest/spruce-a.tree                       # what its instances draw
 * model.gentian = game://flora/gentian.plant
 * </pre>
 *
 * A recipe is a value: equal recipes give equal ecologies.
 *
 * @param parameters the ecology's parameters
 * @param species its species, in order
 * @param models model paths by species id (species without one have none)
 */
public record EcologyRecipe(EcologyParameters parameters, List<Species> species, Map<String, String> models) {

    public EcologyRecipe {
        species = List.copyOf(species);
        models = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(models));
    }

    /** Reads a recipe's text. */
    public static EcologyRecipe parse(String text) {
        EcologyParameters p = EcologyParameters.ALPS;
        Climate climate = p.climate();
        EcologyParameters.LandUse use = p.landUse();
        double tile = p.tileSize();
        double cell = p.cell();
        List<String> ids = null;
        Map<String, Double> weights = new LinkedHashMap<>();
        Map<String, String> models = new LinkedHashMap<>();
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
                case "seed" -> p = p.withSeed(Long.parseLong(value));
                case "latitude" -> climate = climate.withLatitude(Double.parseDouble(value));
                case "temperature" -> climate = climate.withSeasonTemperature(Double.parseDouble(value));
                case "lapse" -> climate = climate.withLapseRate(Double.parseDouble(value));
                case "aspect-warming" -> climate = climate.withAspectWarming(Double.parseDouble(value));
                case "tile" -> tile = Double.parseDouble(value);
                case "cell" -> cell = Double.parseDouble(value);
                case "meadows" -> use = new EcologyParameters.LandUse(Boolean.parseBoolean(value), use.maxSlope(),
                    use.maxAltitude(), use.patches());
                case "meadows.slope" -> use = new EcologyParameters.LandUse(use.meadows(),
                    Double.parseDouble(value), use.maxAltitude(), use.patches());
                case "meadows.altitude" -> use = new EcologyParameters.LandUse(use.meadows(), use.maxSlope(),
                    Double.parseDouble(value), use.patches());
                case "species" -> ids = List.of(value.split("\\s+"));
                default -> {
                    if (key.startsWith("weight.")) {
                        weights.put(key.substring("weight.".length()), Double.parseDouble(value));
                    } else if (key.startsWith("model.")) {
                        models.put(key.substring("model.".length()), value);
                    } else {
                        throw new IllegalArgumentException("Line " + number + ": unknown key '" + key + "'");
                    }
                }
            }
        }
        List<Species> species = new ArrayList<>();
        if (ids == null) {
            species.addAll(AlpineSpecies.ALL);
        } else {
            for (String id : ids) {
                species.add(AlpineSpecies.byId(id));
            }
        }
        for (Map.Entry<String, Double> weight : weights.entrySet()) {
            int at = indexOf(species, weight.getKey());
            species.set(at, species.get(at).withWeight(weight.getValue()));
        }
        for (String id : models.keySet()) {
            indexOf(species, id);
        }
        return new EcologyRecipe(p.withClimate(climate).withTiles(tile, cell).withLandUse(use), species, models);
    }

    private static int indexOf(List<Species> species, String id) {
        for (int s = 0; s < species.size(); s++) {
            if (species.get(s).id().equals(id)) {
                return s;
            }
        }
        throw new IllegalArgumentException("The recipe has no species '" + id + "'");
    }

    /** The ecology of this recipe on {@code terrain}. */
    public Ecology ecology(EcologyTerrain terrain) {
        return new Ecology(terrain, parameters, species);
    }
}
