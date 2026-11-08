package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.tree.Season;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 * season = summer          # spring, summer, autumn, winter (trees)
 * snow = 0.3               # 0 to 1 (trees)
 * material.bark = game://materials/spruce-bark.material
 * material.leaves = game://materials/spruce-needles.material
 * </pre>
 *
 * Equal recipes are one asset: a recipe is a value, so it keys generated assets and the generation cache.
 *
 * @param generator the generator's id: {@code tree} or {@code rock}
 * @param preset the generator's preset, by name: a {@code TreeSpecies} or {@code RockParams} preset
 * @param seed the seed
 * @param season trees: the season
 * @param snow trees: the snow load
 * @param materials the material paths of the submeshes, by slot (trees: bark, leaves; rocks: rock); empty strings for
 *     none
 */
public record Recipe(String generator, String preset, long seed, Season season, double snow, List<String> materials) {

    /** The material slots of each generator, in submesh order. */
    static final List<String> TREE_SLOTS = List.of("bark", "leaves");
    static final List<String> ROCK_SLOTS = List.of("rock");

    public Recipe {
        materials = List.copyOf(materials);
    }

    /** Reads a recipe's text. */
    @AllowAllocation("parsing, at load time")
    public static Recipe parse(String text) {
        String generator = null;
        String preset = null;
        long seed = 0;
        Season season = Season.SUMMER;
        double snow = 0;
        List<String[]> slots = new ArrayList<>();
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
                default -> {
                    if (!key.startsWith("material.")) {
                        throw new IllegalArgumentException("Line " + number + ": unknown key '" + key + "'");
                    }
                    slots.add(new String[] {key.substring("material.".length()), value});
                }
            }
        }
        if (generator == null || preset == null) {
            throw new IllegalArgumentException("A recipe names its generator and preset");
        }
        List<String> names = switch (generator) {
            case "tree" -> TREE_SLOTS;
            case "rock" -> ROCK_SLOTS;
            default -> throw new IllegalArgumentException("Unknown generator '" + generator + "'");
        };
        String[] materials = new String[names.size()];
        java.util.Arrays.fill(materials, "");
        for (String[] slot : slots) {
            int index = names.indexOf(slot[0]);
            if (index < 0) {
                throw new IllegalArgumentException("A " + generator + " has no material slot '" + slot[0] + "'");
            }
            materials[index] = slot[1];
        }
        return new Recipe(generator, preset, seed, season, snow, List.of(materials));
    }

    /**
     * What the generated meshes depend on, as canonical text: the generator, preset, seed and options, not the
     * materials. Equal keys give identical meshes (for a given generator version).
     */
    @AllowAllocation("at load time")
    public String generationKey() {
        return "generator=" + generator + "\npreset=" + preset + "\nseed=" + seed + "\nseason=" + season.name()
            + "\nsnow=" + Double.doubleToLongBits(snow) + "\n";
    }
}
