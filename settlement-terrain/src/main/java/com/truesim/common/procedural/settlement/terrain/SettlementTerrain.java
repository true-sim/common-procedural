package com.truesim.common.procedural.settlement.terrain;

import com.truesim.common.procedural.settlement.Grading;
import com.truesim.common.procedural.settlement.RoadSurface;
import com.truesim.common.procedural.settlement.Settlement;
import com.truesim.effects.terrain.data.TerrainModifier;

/**
 * A settlement's roads and square in effects-terrain's surface: its terrain modifier.
 *
 * <ul>
 *   <li><b>Height</b>: the settlement's {@link Grading}: under a road the road's own surface height (the function
 *       its meshes are made with; sunk a little under them from the edge's outer strip inward), the road's edge height
 *       over the shoulder beyond, then a smooth cut or fill into the ground the earlier modifiers leave.</li>
 *   <li><b>Materials</b>: beside the roads a worn verge ({@link #DIRT}) and gravel scatter ({@link #GRAVEL}) blending
 *       the seam into the grass, fading where the terrain's spacing is coarser than the verge; under the paved surface
 *       its own material (cobbles, gravel, dirt), for distances the meshes are not drawn at.</li>
 * </ul>
 *
 * <pre>{@code
 * TerrainModifier roads = SettlementTerrain.of(village);
 * Terrain terrain = new Terrain(world, resources, site, material, settings, TerrainModifiers.of(riverBeds).with(roads));
 * }</pre>
 * Pure and thread-safe, as the hook asks.
 */
public final class SettlementTerrain implements TerrainModifier {

    private final Grading grading;
    private final Bounds bounds;
    private final double verge;

    private SettlementTerrain(Settlement settlement) {
        this.grading = settlement.grading();
        this.verge = settlement.parameters().surface().verge();
        double half = settlement.parameters().grid().extent() / 2 + grading.reach();
        this.bounds = new Bounds(settlement.originX() - half, settlement.originZ() - half,
            settlement.originX() + half, settlement.originZ() + half);
    }

    /** The terrain modifier of {@code settlement}'s roads and square. */
    public static SettlementTerrain of(Settlement settlement) {
        return new SettlementTerrain(settlement);
    }

    @Override
    public double height(double x, double z, double footprint, double height) {
        return grading.height(x, z, height);
    }

    @Override
    public double materials(double x, double z, double footprint, double[] cover) {
        RoadSurface paved = grading.surface(x, z);
        if (paved != null) {
            cover[material(paved)] = 1;
            return 1;
        }
        double[] weights = new double[2];
        grading.verge(x, z, weights);
        // a verge narrower than the terrain's spacing fades rather than aliases
        double fade = Math.min(1, 2 * verge / Math.max(footprint, 1e-9));
        double gravel = weights[1] * fade;
        double dirt = Math.max(0, weights[0] - weights[1]) * fade;
        cover[GRAVEL] = gravel;
        cover[DIRT] = dirt;
        return gravel + dirt;
    }

    private static int material(RoadSurface surface) {
        return switch (surface) {
            case COBBLES -> COBBLES;
            case GRAVEL -> GRAVEL;
            case DIRT -> DIRT;
        };
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }
}
