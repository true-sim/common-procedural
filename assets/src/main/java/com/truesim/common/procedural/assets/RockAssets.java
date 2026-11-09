package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.procedural.ecology.rocks.RockInstances;
import com.truesim.common.procedural.ecology.rocks.RockVariant;
import com.truesim.common.procedural.rock.RockCover;
import com.truesim.common.procedural.tree.Season;
import com.truesim.scene.assets.AssetRef;
import com.truesim.scene.render.ModelData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A rock field's rocks through the asset manager: each variant its recipe, so its model is generated once (in the
 * background, through the generation cache) and drawn by every rock of it, placed by its instance.
 *
 * <pre>{@code
 * RockTile tile = rocks.tile(3, -2);
 * Map<Integer, AssetRef<ModelData>> models = RockAssets.models(procedural, tile.instances(),
 *     "game://materials/limestone.material", "");        // fresh fractures: the same material
 * for (int i = 0; i < tile.instances().count(); i++) {
 *     AssetRef<ModelData> model = models.get(tile.instances().variantIndex(i));
 *     // an instance of model at (x, y, z), rotated by (qx, qy, qz, qw), scaled by scale(i)
 * }
 * }</pre>
 */
public final class RockAssets {

    private RockAssets() {
    }

    /**
     * The recipe of {@code variant}: its preset, seed and cover ({@code moss}, {@code lichen}, {@code yaw} in degrees,
     * {@code burial}), with {@code rock} as the weathered surface's material and {@code fresh} as the fractures' (the
     * rock's when empty). It generates the same meshes as {@code Rocks.generate(variant.params(), variant.cover(),
     * variant.seed())}.
     */
    @AllowAllocation("at load time")
    public static Recipe recipe(RockVariant variant, String rock, String fresh) {
        RockCover cover = variant.cover();
        Map<String, String> options = new LinkedHashMap<>();
        options.put("moss", Double.toString(cover.moss()));
        options.put("lichen", Double.toString(cover.lichen()));
        // headings are whole fractions of a turn: degrees round-trip exactly (Maths.toRadians on both sides)
        options.put("yaw", Double.toString(Math.round(Maths.toDegrees(cover.yaw()) * 1e6) / 1e6));
        if (!Double.isNaN(cover.buried())) {
            options.put("burial", Double.toString(cover.buried()));
        }
        return new Recipe("rock", variant.params().name(), variant.seed(), Season.SUMMER, 0,
            List.of(rock, fresh.isEmpty() ? rock : fresh), Double.NaN, options);
    }

    /** The model of every variant {@code rocks} draw, by variant index, with these materials. */
    @AllowAllocation("at load time")
    public static Map<Integer, AssetRef<ModelData>> models(ProceduralAssets procedural, RockInstances rocks,
        String rock, String fresh) {
        Map<Integer, AssetRef<ModelData>> models = new LinkedHashMap<>();
        for (int i = 0; i < rocks.count(); i++) {
            int v = rocks.variantIndex(i);
            if (!models.containsKey(v)) {
                models.put(v, procedural.model(recipe(rocks.variants().get(v), rock, fresh)));
            }
        }
        return models;
    }
}
