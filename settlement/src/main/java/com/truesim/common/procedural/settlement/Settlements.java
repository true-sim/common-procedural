package com.truesim.common.procedural.settlement;

import com.truesim.common.procedural.ecology.Ecology;

/**
 * The direct API: a settlement's plan on an ecology's terrain, its square at an origin.
 *
 * <pre>{@code
 * Ecology ecology = new Ecology(terrain, EcologyParameters.ALPS, AlpineSpecies.ALL);   // heights and hydrology
 * Settlement village = Settlements.plan(SettlementParameters.DEFAULTS, ecology, -130, 300, 7);
 * for (Settlement.Building b : village.buildings()) { ... b.spec() to the buildings generator, placed at b ... }
 * List<RoadMeshes.Tile> roads = RoadMeshes.build(village);                            // surfaces, kerbs, steps
 * double open = village.clearing().trees(x, z);                                       // keep the ecology's trees off
 * }</pre>
 *
 * The ecology gives the terrain's heights ({@link Ecology#terrain()}) and what its hydrology and land use say of each
 * point (open water, wetland, the height above the nearest drainage, meadows); the plan keeps buildings off flood-prone
 * ground and puts hay barns on its meadows. Deterministic: the same parameters, terrain, origin and seed give the same
 * plan. Takes some seconds (the terrain's analysis, a few dozen least-cost paths): make it in the background.
 */
public final class Settlements {

    private Settlements() {
    }

    /** The plan of a settlement whose square lies at ({@code originX}, {@code originZ}) of {@code ecology}'s terrain. */
    public static Settlement plan(SettlementParameters parameters, Ecology ecology, double originX, double originZ,
        long seed) {
        return new Planner(parameters, ecology, originX, originZ, seed).plan();
    }
}
