package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.building.Building;
import com.truesim.common.procedural.building.BuildingGenerator;
import com.truesim.common.procedural.building.BuildingKind;
import com.truesim.common.procedural.building.BuildingOptions;
import com.truesim.common.procedural.building.BuildingSlot;
import com.truesim.common.procedural.building.BuildingSpec;
import com.truesim.common.procedural.building.Buildings;
import com.truesim.common.procedural.building.Footprint;
import com.truesim.common.procedural.building.GroundHeight;
import com.truesim.common.procedural.building.RoofCover;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.tree.Season;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Procedural buildings through the scene's asset system, two ways.
 *
 * <p><b>Recipe files</b> ({@code .building}, see {@link Recipe}): meshes and models like every procedural recipe
 * ({@code #lod/N}, {@code #hierarchy/N}; levels 0 to 3, no billboard), the ground under them a plane of the recipe's
 * grades, and the building's layout ({@link #LAYOUT}: its floor's height over that ground and its fittings) as an asset
 * of its own:
 *
 * <pre>{@code
 * ProceduralAssets procedural = new ProceduralAssets(paths, cache).install();   // .building recipes included
 * BuildingAssets.options(procedural, BuildingSettings.options(settings));       // the settings' tunables
 * AssetRef<ModelData> chalet = paths.ref(RenderAssets.MODEL, "game://village/chalet-a.building");
 * AssetRef<MeshData> far = paths.ref(RenderAssets.MESH, "game://village/chalet-a.building#lod/2");
 * AssetRef<Building> layout = paths.ref(BuildingAssets.LAYOUT, "game://village/chalet-a.building");
 * }</pre>
 *
 * <p><b>Detail:</b> a building's levels are its detail ({@code #lod/0} to {@code #lod/3}): a cluster hierarchy built
 * from a level does not simplify (a building is many small closed solids, their faces split at texture seams, which
 * the simplifier keeps), so its hierarchy is the level itself, drawn per meshlet. The model is level
 * {@link ModelDetail#plantLevel()} (0 by default).
 *
 * <p><b>Buildings on real terrain</b> (a settlement's, generated with {@link Buildings#generate} over the terrain's
 * height): generated references to their meshes and models, keyed by the building itself, so the scene loads,
 * evicts and reloads them as any asset ({@link #mesh}, {@link #model}).
 */
public final class BuildingAssets {

    /** A building's layout (its floor's height and fittings, no meshes), read from a {@code .building} recipe. */
    public static final AssetType<Building> LAYOUT = AssetType.of("procedural-building", Building.class,
        building -> 8192);
    /** The file extension of building recipes. */
    public static final String EXTENSION = "building";

    /** A generated mesh's key: a building, one of its levels, flat or as a cluster hierarchy. */
    record MeshKey(Building building, int level, boolean hierarchy) {
    }

    /** A generated model's key: a building, the level it starts from, its materials by slot. */
    record ModelKey(Building building, int level, boolean hierarchy,
        Map<BuildingSlot, AssetRef<MaterialData>> materials) {
    }

    private static final Generator<MeshKey, MeshData> MESHES = (key, seed) ->
        SceneMeshes.build(key.building().lods().get(key.level()), false, key.hierarchy());
    private static final Generator<ModelKey, ModelData> MODELS = (key, seed) -> buildModel(key);

    private BuildingAssets() {
    }

    /**
     * Buildings of {@code .building} recipes made as {@code options} say from now on (their meshes, models and
     * layouts; {@link BuildingSettings#options} reads them from the settings): recipes imported after this call take
     * them, assets made before keep theirs.
     */
    public static ProceduralAssets options(ProceduralAssets procedural, BuildingOptions options) {
        return procedural.generator(generator(options));
    }

    /** The options {@code procedural}'s buildings are made with. */
    public static BuildingOptions options(ProceduralAssets procedural) {
        return procedural.generator(EXTENSION) instanceof RecipeGenerator g ? g.options() : BuildingOptions.DEFAULTS;
    }

    /** Registers the {@link #LAYOUT} importer of {@code .building} recipes ({@link ProceduralAssets#install} does). */
    @AllowAllocation("setting up")
    static void install(PathAssets paths, ProceduralAssets procedural) {
        paths.importers().add(LAYOUT, EXTENSION, input -> layout(read(input), options(procedural)));
    }

    /** The generator of {@code .building} recipes' meshes, made as {@code options} say. */
    public static ProceduralGenerator generator(BuildingOptions options) {
        return new RecipeGenerator(options);
    }

    /** Buildings from recipes: their levels of detail, no billboard, no wind; the options key the cache. */
    private record RecipeGenerator(BuildingOptions options) implements ProceduralGenerator {

        @Override
        public String id() {
            return EXTENSION;
        }

        @Override
        public int version() {
            // the options change the meshes: they key the generation cache with the version
            return BuildingOptions.DEFAULTS.equals(options) ? BuildingGenerator.VERSION
                : 31 * BuildingGenerator.VERSION + options.hashCode();
        }

        @Override
        public boolean sways() {
            return false;
        }

        @Override
        public boolean billboard() {
            return false;
        }

        @Override
        public List<ProceduralMesh> generate(Recipe recipe) {
            return Buildings.generate(spec(recipe), ground(recipe), options).lods();
        }
    }

    /** The spec of a {@code .building} recipe: its kind (preset), seed and parameters, at the origin, unturned. */
    @AllowAllocation("at load time")
    public static BuildingSpec spec(Recipe recipe) {
        BuildingKind kind = BuildingKind.of(recipe.preset());
        Map<String, String> p = recipe.parameters();
        double width = number(p, "width", kind.width());
        double depth = number(p, "depth", kind.depth());
        int floors = (int) number(p, "floors", 0);
        RoofCover roof = p.containsKey("roof") ? RoofCover.of(p.get("roof")) : null;
        double pitch = number(p, "pitch", Double.NaN);
        return new BuildingSpec(kind, Footprint.of(width, depth), floors, recipe.seed(), roof, pitch);
    }

    /** The ground of a {@code .building} recipe: a plane through 0 at its middle, rising by its grades. */
    public static GroundHeight ground(Recipe recipe) {
        return GroundHeight.plane(0, number(recipe.parameters(), "grade.x", 0),
            number(recipe.parameters(), "grade.z", 0));
    }

    /** The layout of a {@code .building} recipe: its floor's height over its ground, its fittings. */
    public static Building layout(Recipe recipe, BuildingOptions options) {
        return Buildings.layout(spec(recipe), ground(recipe), options);
    }

    /**
     * A recipe for {@code spec} at the origin, its ground a plane of {@code gradeX} and {@code gradeZ}, its materials
     * by slot (missing ones none): for {@link ProceduralAssets#model(Recipe)} and the generation cache, without a file.
     */
    @AllowAllocation("at load time")
    public static Recipe recipe(BuildingSpec spec, double gradeX, double gradeZ, Map<BuildingSlot, String> materials) {
        Map<String, String> p = new HashMap<>();
        p.put("width", Double.toString(spec.footprint().width()));
        p.put("depth", Double.toString(spec.footprint().depth()));
        p.put("floors", Integer.toString(spec.floors()));
        p.put("roof", spec.roof().id());
        p.put("pitch", Double.toString(spec.pitch()));
        p.put("grade.x", Double.toString(gradeX));
        p.put("grade.z", Double.toString(gradeZ));
        List<String> slots = new ArrayList<>();
        for (BuildingSlot slot : BuildingSlot.all()) {
            slots.add(materials.getOrDefault(slot, ""));
        }
        return new Recipe(EXTENSION, spec.kind().id(), spec.seed(), Season.SUMMER, 0, slots, Double.NaN, p);
    }

    /** A generated reference to level {@code level} of {@code building}'s meshes, flat or as a cluster hierarchy. */
    @AllowAllocation("references, at load time")
    public static AssetRef<MeshData> mesh(Building building, int level, boolean hierarchy) {
        if (level < 0 || level >= building.lods().size()) {
            throw new IllegalArgumentException("A building has levels 0 to " + (building.lods().size() - 1));
        }
        return AssetRef.generated(RenderAssets.MESH, MESHES, new MeshKey(building, level, hierarchy),
            building.spec().seed());
    }

    /**
     * A generated reference to {@code building}'s model: one node, its mesh from level {@code level} (a cluster
     * hierarchy where {@code hierarchy}), a material per slot it uses from {@code materials} (all its slots must have
     * one; else none, the renderer's default).
     */
    @AllowAllocation("references, at load time")
    public static AssetRef<ModelData> model(Building building, int level, boolean hierarchy,
        Map<BuildingSlot, AssetRef<MaterialData>> materials) {
        return AssetRef.generated(RenderAssets.MODEL, MODELS, new ModelKey(building, level, hierarchy,
            Map.copyOf(materials)), building.spec().seed());
    }

    @AllowAllocation("at load time")
    private static ModelData buildModel(ModelKey key) {
        ProceduralMesh mesh = key.building().lods().get(key.level());
        List<AssetRef<MaterialData>> slots = new ArrayList<>();
        boolean all = true;
        for (BuildingSlot slot : BuildingSlot.all()) {
            if (SceneMeshes.kept(mesh, slot.ordinal())) {
                AssetRef<MaterialData> material = key.materials().get(slot);
                all &= material != null;
                if (material != null) {
                    slots.add(material);
                }
            }
        }
        return new ModelData(List.of(ModelData.Node.of("building:" + key.building().spec().kind().id(), -1,
            mesh(key.building(), key.level(), key.hierarchy()), all ? slots : List.of())));
    }

    private static double number(Map<String, String> p, String key, double otherwise) {
        String value = p.get(key);
        return value == null ? otherwise : Double.parseDouble(value);
    }

    @AllowAllocation("at load time")
    private static Recipe read(AssetInput input) throws IOException {
        try (InputStream in = input.open()) {
            Recipe recipe = Recipe.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (!EXTENSION.equals(recipe.generator())) {
                throw new IllegalArgumentException("Not a building recipe: " + input.path());
            }
            return recipe;
        }
    }
}
