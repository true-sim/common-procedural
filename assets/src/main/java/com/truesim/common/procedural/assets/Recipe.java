package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.tree.Season;
import com.truesim.common.procedural.building.BuildingSlot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A procedural asset's recipe: which generator, which of its presets, the seed and the options, and the materials of
 * its submeshes. Recipes are small text files ({@code .tree}, {@code .rock}) of {@code key = value} lines; {@code #}
 * starts a comment:
 *
 * <pre>
 * # forest/spruce-a.tree
 * generator = tree
 * preset = spruce
 * seed = 42
 * season = summer          # spring, summer, autumn, winter (trees, plants)
 * day = 160                # or a day of the year, 0 to 365: flowers and fruits (trees, plants)
 * snow = 0.3               # 0 to 1 (trees, plants)
 * material.bark = game://materials/spruce-bark.material
 * material.leaves = game://materials/spruce-needles.material
 * material.attachments = game://materials/spruce-cones.material
 * </pre>
 *
 * A plant ({@code .plant}, {@code generator = plant}, a {@code PlantSpecies} preset) has the slots {@code foliage}
 * and {@code flowers}. A building ({@code .building}, {@code generator = building}, a {@code BuildingKind} preset)
 * has a slot per {@code BuildingSlot} and parameters of its own ({@link #BUILDING_PARAMETERS}):
 *
 * <pre>
 * # village/chalet-a.building
 * generator = building
 * preset = chalet          # chalet farmhouse hay-barn church inn woodshed
 * seed = 7
 * width = 9.5              # across the front gable, metres (the kind's by default)
 * depth = 11               # along the ridge
 * floors = 2
 * roof = stone-weighted    # shingles slates stone-weighted (the seed's by default)
 * pitch = 22               # degrees
 * grade.x = 0.1            # the ground's slope under it: rise per metre across and along (0: level)
 * grade.z = -0.25
 * material.stone = game://materials/old-stone-wall.material
 * material.timber = game://materials/larch-logs.material
 * </pre>
 *
 * Equal recipes are one asset: a recipe is a value, so it keys generated assets and the generation cache.
 *
 * @param generator the generator's id: {@code tree}, {@code plant}, {@code rock} or {@code building}
 * @param preset the generator's preset, by name: a {@code TreeSpecies}, {@code PlantSpecies} or {@code RockParams}
 *     preset, or a {@code BuildingKind}
 * @param seed the seed
 * @param season trees: the season
 * @param snow trees: the snow load
 * @param materials the material paths of the submeshes, by slot (trees: bark, leaves, attachments; plants: foliage,
 *     flowers; rocks: rock; buildings: their {@code BuildingSlot}s); empty strings for none
 * @param day trees and plants: the day of the year, or NaN for the season's
 * @param parameters the generator's own parameters, by name (buildings: {@link #BUILDING_PARAMETERS}); empty for
 *     trees, plants and rocks
 */
public record Recipe(String generator, String preset, long seed, Season season, double snow, List<String> materials,
    double day, Map<String, String> parameters) {

    /** The material slots of each generator, in submesh order. */
    static final List<String> TREE_SLOTS = List.of("bark", "leaves", "attachments");
    static final List<String> PLANT_SLOTS = List.of("foliage", "flowers");
    static final List<String> ROCK_SLOTS = List.of("rock");
    static final List<String> BUILDING_SLOTS = BuildingSlot.names();
    /** A building recipe's own parameters. */
    public static final Set<String> BUILDING_PARAMETERS = Set.of("width", "depth", "floors", "roof", "pitch",
        "grade.x", "grade.z");

    public Recipe {
        materials = List.copyOf(materials);
        parameters = Collections.unmodifiableMap(new TreeMap<>(parameters));
        if (!Double.isNaN(day) && !(day >= 0 && day < 366)) {
            throw new IllegalArgumentException("A day of the year is 0 to 365: " + day);
        }
    }

    /** A recipe on its season's day. */
    public Recipe(String generator, String preset, long seed, Season season, double snow, List<String> materials) {
        this(generator, preset, seed, season, snow, materials, Double.NaN);
    }

    /** A recipe without parameters of its generator's own. */
    public Recipe(String generator, String preset, long seed, Season season, double snow, List<String> materials,
        double day) {
        this(generator, preset, seed, season, snow, materials, day, Map.of());
    }

    /** The day of the year it is generated for: its day, else its season's. */
    public double dayOfYear() {
        return Double.isNaN(day) ? season.day() : day;
    }

    /** Reads a recipe's text. */
    @AllowAllocation("parsing, at load time")
    public static Recipe parse(String text) {
        String generator = null;
        String preset = null;
        long seed = 0;
        Season season = Season.SUMMER;
        double snow = 0;
        double day = Double.NaN;
        List<String[]> slots = new ArrayList<>();
        List<String[]> own = new ArrayList<>();
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
                case "generator" -> generator = value;
                case "preset" -> preset = value;
                case "seed" -> seed = Long.parseLong(value);
                case "season" -> season = Season.valueOf(value.toUpperCase(Locale.ROOT));
                case "snow" -> snow = Double.parseDouble(value);
                case "day" -> day = Double.parseDouble(value);
                default -> {
                    if (key.startsWith("material.")) {
                        slots.add(new String[] {key.substring("material.".length()), value});
                    } else {
                        own.add(new String[] {key, value, Integer.toString(number)});
                    }
                }
            }
        }
        if (generator == null || preset == null) {
            throw new IllegalArgumentException("A recipe names its generator and preset");
        }
        List<String> names = switch (generator) {
            case "tree" -> TREE_SLOTS;
            case "plant" -> PLANT_SLOTS;
            case "rock" -> ROCK_SLOTS;
            case "building" -> BUILDING_SLOTS;
            default -> throw new IllegalArgumentException("Unknown generator '" + generator + "'");
        };
        Map<String, String> parameters = new TreeMap<>();
        for (String[] parameter : own) {
            if (!"building".equals(generator) || !BUILDING_PARAMETERS.contains(parameter[0])) {
                throw new IllegalArgumentException("Line " + parameter[2] + ": unknown key '" + parameter[0] + "'");
            }
            parameters.put(parameter[0], parameter[1]);
        }
        String[] materials = new String[names.size()];
        java.util.Arrays.fill(materials, "");
        for (String[] slot : slots) {
            int index = names.indexOf(slot[0]);
            if (index < 0) {
                throw new IllegalArgumentException("A " + generator + " has no material slot '" + slot[0] + "'");
            }
            materials[index] = slot[1];
        }
        return new Recipe(generator, preset, seed, season, snow, List.of(materials), day, parameters);
    }

    /**
     * What the generated meshes depend on, as canonical text: the generator, preset, seed, options and parameters
     * (in name order), not the materials. Equal keys give identical meshes (for a given generator version).
     */
    @AllowAllocation("at load time")
    public String generationKey() {
        StringBuilder own = new StringBuilder();
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            own.append(parameter.getKey()).append('=').append(parameter.getValue()).append('\n');
        }
        return "generator=" + generator + "\npreset=" + preset + "\nseed=" + seed + "\nseason=" + season.name()
            + "\nsnow=" + Double.doubleToLongBits(snow) + "\n"
            + (Double.isNaN(day) ? "" : "day=" + Double.doubleToLongBits(day) + "\n") + own;
    }
}
