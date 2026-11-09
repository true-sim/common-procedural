package com.truesim.common.procedural.building;

import com.truesim.annotations.AllowAllocation;

import java.util.ArrayList;
import java.util.List;

/**
 * How buildings are generated, the same for every building: tunables that are engine settings on the asset side
 * (common-procedural-assets' {@code BuildingSettings}). Every value changes the meshes, so they key the generation
 * cache with the spec.
 *
 * @param cell the largest face cell, metres: texture coordinates restart in every cell, so they stay under
 *     {@code 1 + cell / repeat}
 * @param clearance how far the ground floor stands above the highest ground under the footprint, metres
 * @param embed how far foundations, steps and woodpiles reach below the ground, metres
 * @param step the longest run of foundation at one depth, metres: the foundation steps down the slope in runs this
 *     long at most, each reaching {@code embed} below the lowest ground along it
 * @param interiors whether to build rooms (floors, ceilings, inner walls, partitions, the hearth): off, windows
 *     look into dark wall cavities
 * @param repeats the metres a texture repeat covers per slot ({@link BuildingSlot} order)
 */
public record BuildingOptions(double cell, double clearance, double embed, double step, boolean interiors,
    List<Double> repeats) {

    /** The defaults: 2 m cells, 0.3 m clearance, 0.5 m embedding, 1.6 m steps, interiors, the slots' repeats. */
    public static final BuildingOptions DEFAULTS = new BuildingOptions(2.0, 0.3, 0.5, 1.6, true, defaultRepeats());

    public BuildingOptions {
        repeats = List.copyOf(repeats);
        if (repeats.size() != BuildingSlot.all().size()) {
            throw new IllegalArgumentException("A repeat for each of the " + BuildingSlot.all().size() + " slots");
        }
        for (double r : repeats) {
            if (!(r >= 0.05)) {
                throw new IllegalArgumentException("Repeats from 5 cm: " + r);
            }
        }
        if (!(cell >= 0.25 && cell <= 8) || !(clearance >= 0.05) || !(embed >= 0.05) || !(step >= 0.3)) {
            throw new IllegalArgumentException("Cell 0.25 to 8 m, clearance, embed from 5 cm, step from 0.3 m");
        }
    }

    /** The metres one repeat of {@code slot}'s texture covers. */
    public double repeat(BuildingSlot slot) {
        return repeats.get(slot.ordinal());
    }

    /** These with {@code slot}'s repeat {@code metres}. */
    @AllowAllocation("options, at set-up")
    public BuildingOptions withRepeat(BuildingSlot slot, double metres) {
        List<Double> r = new ArrayList<>(repeats);
        r.set(slot.ordinal(), metres);
        return new BuildingOptions(cell, clearance, embed, step, interiors, r);
    }

    /** These with cells of {@code metres}. */
    public BuildingOptions withCell(double metres) {
        return new BuildingOptions(metres, clearance, embed, step, interiors, repeats);
    }

    /** These with the ground floor {@code metres} above the highest ground. */
    public BuildingOptions withClearance(double metres) {
        return new BuildingOptions(cell, metres, embed, step, interiors, repeats);
    }

    /** These with foundations {@code metres} into the ground. */
    public BuildingOptions withEmbed(double metres) {
        return new BuildingOptions(cell, clearance, metres, step, interiors, repeats);
    }

    /** These with foundation runs of at most {@code metres}. */
    public BuildingOptions withStep(double metres) {
        return new BuildingOptions(cell, clearance, embed, metres, interiors, repeats);
    }

    /** These with interiors or without. */
    public BuildingOptions withInteriors(boolean on) {
        return new BuildingOptions(cell, clearance, embed, step, on, repeats);
    }

    @AllowAllocation("options, at set-up")
    private static List<Double> defaultRepeats() {
        List<Double> r = new ArrayList<>();
        for (BuildingSlot slot : BuildingSlot.all()) {
            r.add(slot.repeat());
        }
        return r;
    }
}
