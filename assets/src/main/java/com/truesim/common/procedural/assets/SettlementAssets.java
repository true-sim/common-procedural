package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.settlement.RoadMeshes;
import com.truesim.common.procedural.settlement.Settlement;
import com.truesim.common.procedural.settlement.SettlementParameters;
import com.truesim.common.procedural.settlement.SettlementRecipe;
import com.truesim.scene.assets.AssetInput;
import com.truesim.scene.assets.AssetRef;
import com.truesim.scene.assets.AssetType;
import com.truesim.scene.assets.Generator;
import com.truesim.scene.assets.PathAssets;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.render.MaterialData;
import com.truesim.scene.render.ModelData;
import com.truesim.scene.render.RenderAssets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Settlements through the scene's asset system: a recipe file ({@code .settlement}, {@link SettlementRecipe}) loads as
 * an asset; its plan on an ecology's terrain is a generated asset made in the background (on the asset lane, like
 * every load); its road meshes are generated models, one per tile, with the recipe's materials.
 *
 * <pre>{@code
 * SettlementAssets.install(paths);
 * AssetRef<SettlementRecipe> recipe = paths.ref(SettlementAssets.SETTLEMENT, "game://village/lauterbrunnen.settlement");
 * cache.acquire(recipe);                                                       // read in the background
 * AssetRef<Settlement> plan = SettlementAssets.plan(cache.get(recipe), ecology,
 *     SettlementSettings.parameters(settings));                               // planned in the background
 * cache.acquire(plan);
 * for (SettlementAssets.RoadModel road : SettlementAssets.roads(paths, cache.get(recipe), cache.get(plan))) {
 *     cache.acquire(road.model());                                            // place it at road.x(), y(), z()
 * }
 * }</pre>
 */
public final class SettlementAssets {

    /** Settlement recipes, read from {@code .settlement} files. */
    public static final AssetType<SettlementRecipe> SETTLEMENT = AssetType.of("procedural-settlement",
        SettlementRecipe.class, recipe -> 512);
    /** Settlements' plans, generated. */
    public static final AssetType<Settlement> PLAN = AssetType.of("procedural-settlement-plan", Settlement.class,
        Settlement::bytes);
    /** The file extension of settlement recipes. */
    public static final String EXTENSION = "settlement";

    /** A plan's key: the recipe, the ecology it stands on (by identity) and the parameters. */
    record PlanKey(SettlementRecipe recipe, Ecology ecology, SettlementParameters parameters) {
    }

    /** A road tile's model's key: the plan (by identity), the tile, the materials. */
    record TileKey(Settlement plan, int tile, List<String> materials) {
    }

    /** A road tile's mesh's key. */
    record MeshKey(Settlement plan, int tile) {
    }

    /**
     * A tile of a plan's road meshes as a model: place it at ({@code x}, {@code y}, {@code z}).
     *
     * @param i the tile's column
     * @param j the tile's row
     * @param x its origin, east
     * @param y its origin's height
     * @param z its origin, south
     * @param model its model: one node, the tile's mesh with the recipe's materials
     */
    public record RoadModel(int i, int j, double x, double y, double z, AssetRef<ModelData> model) {
    }

    private static final Generator<PlanKey, Settlement> PLANS = (key, seed) -> {
        Settlement plan = key.recipe().plan(key.ecology(), key.parameters());
        plan.roadMeshes(); // the road meshes too, here in the background
        return plan;
    };
    private static final Generator<MeshKey, MeshData> MESHES = (key, seed) -> SceneMeshes.build(
        key.plan().roadMeshes().get(key.tile()).mesh(), false);

    private SettlementAssets() {
    }

    /** Registers the importer of {@code .settlement} files. */
    @AllowAllocation("setting up")
    public static void install(PathAssets paths) {
        paths.importers().add(SETTLEMENT, EXTENSION, SettlementAssets::read);
    }

    /**
     * A generated reference to the plan of {@code recipe} on {@code ecology}'s terrain with {@code parameters}
     * ({@link SettlementSettings#parameters}): made on the asset lane, its road meshes with it.
     */
    @AllowAllocation("references, at load time")
    public static AssetRef<Settlement> plan(SettlementRecipe recipe, Ecology ecology, SettlementParameters parameters) {
        return AssetRef.generated(PLAN, PLANS, new PlanKey(recipe, ecology, parameters), recipe.seed());
    }

    /** The road meshes of a loaded {@code plan} as models, a tile each, with {@code recipe}'s materials. */
    @AllowAllocation("references, at load time")
    public static List<RoadModel> roads(PathAssets paths, SettlementRecipe recipe, Settlement plan) {
        List<RoadMeshes.Tile> tiles = plan.roadMeshes();
        List<RoadModel> models = new ArrayList<>();
        Generator<TileKey, ModelData> generator = (key, seed) -> model(paths, key);
        for (int t = 0; t < tiles.size(); t++) {
            RoadMeshes.Tile tile = tiles.get(t);
            models.add(new RoadModel(tile.i(), tile.j(), tile.x(), tile.y(), tile.z(), AssetRef.generated(
                RenderAssets.MODEL, generator, new TileKey(plan, t, recipe.materials()), recipe.seed())));
        }
        return models;
    }

    /** A generated reference to tile {@code tile}'s mesh of a loaded {@code plan} (no materials). */
    @AllowAllocation("references, at load time")
    public static AssetRef<MeshData> mesh(Settlement plan, int tile) {
        return AssetRef.generated(RenderAssets.MESH, MESHES, new MeshKey(plan, tile), plan.seed());
    }

    /** A road tile's model: its mesh, the materials of the slots it has triangles in (all named, or none). */
    @AllowAllocation("at load time")
    private static ModelData model(PathAssets paths, TileKey key) {
        ProceduralMesh mesh = key.plan().roadMeshes().get(key.tile()).mesh();
        List<AssetRef<MaterialData>> materials = new ArrayList<>();
        boolean all = true;
        for (int s = 0; s < mesh.submeshCount(); s++) {
            if (SceneMeshes.kept(mesh, s)) {
                String material = key.materials().get(s);
                all &= !material.isEmpty();
                if (!material.isEmpty()) {
                    materials.add(paths.ref(RenderAssets.MATERIAL, material));
                }
            }
        }
        return new ModelData(List.of(ModelData.Node.of("settlement:roads/" + key.tile(), -1,
            mesh(key.plan(), key.tile()), all ? materials : List.of())));
    }

    @AllowAllocation("at load time")
    private static SettlementRecipe read(AssetInput input) throws IOException {
        try (InputStream in = input.open()) {
            return SettlementRecipe.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
