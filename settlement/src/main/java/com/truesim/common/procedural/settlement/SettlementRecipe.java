package com.truesim.common.procedural.settlement;

import java.util.ArrayList;
import java.util.List;

/**
 * A settlement's recipe: where its square lies, its seed and size, and the materials of its road meshes' slots. Small
 * text files ({@code .settlement}) of {@code key = value} lines; {@code #} starts a comment; every key but the origin
 * is optional. The tunables are settings ({@code SettlementSettings} in common-procedural-assets), not the recipe's.
 *
 * <pre>
 * # game://village/lauterbrunnen.settlement
 * origin = -130 300                 # the square's centre, x east and z south, metres
 * seed = 7
 * extent = 1024                     # the plan's side, metres
 * material.cobbles = game://materials/cobbles.material
 * material.gravel = game://materials/gravel.material
 * material.dirt = game://materials/trail.material
 * material.kerb = game://materials/granite.material
 * material.steps = game://materials/granite.material
 * </pre>
 *
 * A recipe is a value: equal recipes on the same terrain with the same parameters give equal plans.
 *
 * @param originX the square's centre, east
 * @param originZ the square's centre, south
 * @param seed the seed
 * @param extent the plan's side, metres (NaN: the parameters')
 * @param materials material paths by slot ({@link RoadMeshes#SLOT_NAMES}; empty where none)
 */
public record SettlementRecipe(double originX, double originZ, long seed, double extent, List<String> materials) {

    public SettlementRecipe {
        materials = List.copyOf(materials);
        if (materials.size() != RoadMeshes.SLOTS) {
            throw new IllegalArgumentException("A material path (or none) per slot: " + RoadMeshes.SLOTS);
        }
    }

    /** Reads a recipe's text. */
    public static SettlementRecipe parse(String text) {
        Double x = null;
        double z = 0;
        long seed = 0;
        double extent = Double.NaN;
        List<String> materials = new ArrayList<>(List.of("", "", "", "", ""));
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
                case "origin" -> {
                    String[] parts = value.split("\s+");
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("Line " + number + ": the origin is 'x z'");
                    }
                    x = Double.parseDouble(parts[0]);
                    z = Double.parseDouble(parts[1]);
                }
                case "seed" -> seed = Long.parseLong(value);
                case "extent" -> extent = Double.parseDouble(value);
                default -> {
                    int slot = key.startsWith("material.") ? RoadMeshes.SLOT_NAMES.indexOf(
                        key.substring("material.".length())) : -1;
                    if (slot < 0) {
                        throw new IllegalArgumentException("Line " + number + ": unknown key '" + key + "'");
                    }
                    materials.set(slot, value);
                }
            }
        }
        if (x == null) {
            throw new IllegalArgumentException("A settlement needs an origin");
        }
        return new SettlementRecipe(x, z, seed, extent, materials);
    }

    /** {@code parameters} with this recipe's extent, if it gives one. */
    public SettlementParameters parameters(SettlementParameters parameters) {
        if (Double.isNaN(extent)) {
            return parameters;
        }
        SettlementParameters.Grid g = parameters.grid();
        return parameters.withGrid(new SettlementParameters.Grid(extent, g.cell(), g.raster()));
    }

    /** The plan of this recipe on {@code ecology}'s terrain with {@code parameters} (the recipe's extent). */
    public Settlement plan(com.truesim.common.procedural.ecology.Ecology ecology, SettlementParameters parameters) {
        return Settlements.plan(parameters(parameters), ecology, originX, originZ, seed);
    }
}
