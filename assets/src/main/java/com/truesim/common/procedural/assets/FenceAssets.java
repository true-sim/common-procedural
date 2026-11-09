package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.fence.Fence;
import com.truesim.common.procedural.fence.FenceGenerator;
import com.truesim.common.procedural.fence.FenceGround;
import com.truesim.common.procedural.fence.FenceRecipe;
import com.truesim.common.procedural.fence.FenceTuning;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.scene.assets.AssetInput;
import com.truesim.scene.assets.AssetLoader;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procedural fences through the scene's asset system. Fence recipes ({@code .fence} files, {@link FenceRecipe}) under
 * any mount import as meshes and models that scenes reference like any other; the ground they stand on is the
 * recipe's own ({@code ground = flat} or {@code line}) or one the game registers by name:
 *
 * <pre>{@code
 * FenceAssets fences = new FenceAssets(paths, new GenerationCache(cacheDir)).install();
 * fences.ground("valley", terrain::height);                     // recipes say: ground = valley
 * fences.tuning(FenceSettings.tuning(settings)).detail(FenceSettings.detail(settings));
 * AssetRef<ModelData> fence = paths.ref(RenderAssets.MODEL, "game://village/meadow.fence"); // at the line's start
 * AssetRef<MeshData> far = paths.ref(RenderAssets.MESH, "game://village/meadow.fence#lod/2");
 * AssetRef<ModelData> inCode = fences.model(recipe);             // a recipe made in code
 * }</pre>
 *
 * <ul>
 *   <li><b>Fragments:</b> none or {@code #lod/0} the most detailed level, {@code #lod/N} level N (flat),
 *       {@code #hierarchy/N} a cluster hierarchy built from level N. A model is one node, translated to the fence's
 *       origin (the line's first point on the ground: the model stands in the ground's frame as it is), with its mesh
 *       as the {@link ModelDetail} says (by default a hierarchy from level 0) and the recipe's materials of the
 *       submeshes it has (wood, stone, metal).</li>
 *   <li><b>Vertex data:</b> the scene format with UV1 and COLOR ({@link SceneMeshes#FORMAT}): COLOR is the fence's
 *       weathered albedo, UV1 its moss and lichen ({@link Fence}); a metallic-roughness material with a grey-scale
 *       wood or stone texture draws it as it is.</li>
 *   <li><b>Generated once:</b> recipes with their own ground go through the {@link GenerationCache} (keyed by the
 *       recipe, the tuning and the generator's version); over a registered ground they are generated when loaded (a
 *       few milliseconds a fence), as the game's terrain may change between runs.</li>
 * </ul>
 */
public final class FenceAssets {

    /** Fence recipes, read from {@code .fence} files. */
    public static final AssetType<FenceRecipe> FENCE = AssetType.of("procedural-fence", FenceRecipe.class,
        recipe -> 1024);
    /** The file extension of fence recipes. */
    public static final String EXTENSION = "fence";
    /** The generator's id in the generation cache's keys. */
    public static final String ID = "fence";

    /** A generated mesh's key: the recipe over a ground (null: the recipe's), the tuning, the level, flat or not. */
    record MeshKey(FenceRecipe recipe, FenceGround ground, FenceTuning tuning, int level, boolean hierarchy) {
    }

    /** A generated model's key. */
    record ModelKey(FenceRecipe recipe, FenceGround ground, FenceTuning tuning, ModelDetail detail) {
    }

    private final PathAssets paths;
    private final GenerationCache cache;
    private final Map<String, FenceGround> grounds = new ConcurrentHashMap<>();
    private final Generator<MeshKey, MeshData> meshes;
    private final Generator<ModelKey, ModelData> models;
    private volatile FenceTuning tuning = FenceTuning.DEFAULTS;
    private volatile ModelDetail detail = new ModelDetail(true, 0, 0);

    /** Fences on {@code paths}, generating through {@code cache}. */
    @AllowAllocation("setting up")
    public FenceAssets(PathAssets paths, GenerationCache cache) {
        this.paths = paths;
        this.cache = cache;
        this.meshes = (key, seed) -> sceneMesh(key.recipe(), key.ground(), key.tuning(), key.level(),
            key.hierarchy());
        this.models = (key, seed) -> buildModel(key.recipe(), key.ground(), key.tuning(), key.detail());
    }

    /** Registers the importers: recipes, meshes and models from {@code .fence} files. */
    @AllowAllocation("setting up")
    public FenceAssets install() {
        AssetLoader<FenceRecipe> recipes = FenceAssets::readRecipe;
        AssetLoader<MeshData> meshImporter = this::importMesh;
        AssetLoader<ModelData> modelImporter = this::importModel;
        paths.importers().add(FENCE, EXTENSION, recipes).add(RenderAssets.MESH, EXTENSION, meshImporter)
            .add(RenderAssets.MODEL, EXTENSION, modelImporter);
        return this;
    }

    /** Registers {@code ground} under {@code name}, for recipes saying {@code ground = name}. */
    public FenceAssets ground(String name, FenceGround ground) {
        if (name.equals("flat") || name.equals("line")) {
            throw new IllegalArgumentException("'" + name + "' is a recipe's own ground");
        }
        grounds.put(name, Objects.requireNonNull(ground, "ground"));
        return this;
    }

    /** The generator's tuning from now on ({@link FenceSettings#tuning} reads it from the settings). */
    public FenceAssets tuning(FenceTuning newTuning) {
        this.tuning = Objects.requireNonNull(newTuning, "tuning");
        return this;
    }

    /** The generator's tuning. */
    public FenceTuning tuning() {
        return tuning;
    }

    /** How models carry their detail from now on: a hierarchy or flat, from its level ({@code plantLevel}). */
    public FenceAssets detail(ModelDetail newDetail) {
        this.detail = Objects.requireNonNull(newDetail, "detail");
        return this;
    }

    /** How models carry their detail. */
    public ModelDetail detail() {
        return detail;
    }

    /** The ground {@code recipe} stands on: its own, or the one registered by its name. */
    public FenceGround groundOf(FenceRecipe recipe) {
        FenceGround own = recipe.ownGround();
        if (own != null) {
            return own;
        }
        FenceGround registered = grounds.get(recipe.ground());
        if (registered == null) {
            throw new IllegalArgumentException("No ground '" + recipe.ground() + "' registered for fences");
        }
        return registered;
    }

    /** The fence of {@code recipe} over its ground, directly (its pieces, e.g. for collision), not cached. */
    public Fence fence(FenceRecipe recipe) {
        return recipe.generate(groundOf(recipe), tuning);
    }

    /** The meshes of {@code recipe} over {@code ground} ({@code null}: its own or registered one), its levels. */
    @AllowAllocation("at load time")
    public List<ProceduralMesh> meshes(FenceRecipe recipe, FenceGround ground, FenceTuning with) throws Exception {
        if (ground == null && recipe.ownGround() != null) {
            String key = recipe.generationKey() + "tuning=" + with + "\n";
            return cache.get(ID, FenceGenerator.VERSION, key, () -> recipe.generate(groundOf(recipe), with).lods());
        }
        FenceGround over = ground != null ? ground : groundOf(recipe);
        return recipe.generate(over, with).lods();
    }

    /** A generated reference to level {@code level} of {@code recipe}'s fence, a cluster hierarchy or flat. */
    @AllowAllocation("references, at load time")
    public AssetRef<MeshData> mesh(FenceRecipe recipe, int level, boolean hierarchy) {
        return AssetRef.generated(RenderAssets.MESH, meshes, new MeshKey(recipe, null, tuning, level, hierarchy),
            recipe.seed());
    }

    /** A generated reference to the model of {@code recipe} over its own or registered ground. */
    @AllowAllocation("references, at load time")
    public AssetRef<ModelData> model(FenceRecipe recipe) {
        return AssetRef.generated(RenderAssets.MODEL, models, new ModelKey(recipe, null, tuning, detail),
            recipe.seed());
    }

    /** A generated reference to the model of {@code recipe} over {@code ground} (the same ground: the same asset). */
    @AllowAllocation("references, at load time")
    public AssetRef<ModelData> model(FenceRecipe recipe, FenceGround ground) {
        return AssetRef.generated(RenderAssets.MODEL, models, new ModelKey(recipe, Objects.requireNonNull(ground,
            "ground"), tuning, detail), recipe.seed());
    }

    @AllowAllocation("at load time")
    private static FenceRecipe readRecipe(AssetInput input) throws IOException {
        try (InputStream in = input.open()) {
            return FenceRecipe.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @AllowAllocation("at load time")
    private MeshData importMesh(AssetInput input) throws Exception {
        FenceRecipe recipe = readRecipe(input);
        String fragment = input.path().fragment();
        int level;
        boolean hierarchy = false;
        if (fragment == null || fragment.isEmpty()) {
            level = 0;
        } else if (fragment.startsWith("lod/")) {
            level = Integer.parseInt(fragment.substring("lod/".length()));
        } else if (fragment.startsWith("hierarchy/")) {
            level = Integer.parseInt(fragment.substring("hierarchy/".length()));
            hierarchy = true;
        } else {
            throw new IllegalArgumentException("No mesh '#" + fragment + "' in " + input.path());
        }
        return sceneMesh(recipe, null, tuning, level, hierarchy);
    }

    @AllowAllocation("at load time")
    private ModelData importModel(AssetInput input) throws Exception {
        FenceRecipe recipe = readRecipe(input);
        FenceTuning t = tuning;
        ModelDetail d = detail;
        int level = d.level(ID, t.levels());
        String fragment = (d.hierarchy() ? "hierarchy/" : "lod/") + level;
        return model(recipe, null, t, level, paths.ref(RenderAssets.MESH, input.path().file().fragment(fragment)));
    }

    @AllowAllocation("at load time")
    private MeshData sceneMesh(FenceRecipe recipe, FenceGround ground, FenceTuning with, int level,
        boolean hierarchy) throws Exception {
        List<ProceduralMesh> all = meshes(recipe, ground, with);
        if (level < 0 || level >= all.size()) {
            throw new IllegalArgumentException("A fence has levels 0 to " + (all.size() - 1) + ", not " + level);
        }
        return SceneMeshes.build(all.get(level), true, hierarchy);
    }

    @AllowAllocation("at load time")
    private ModelData buildModel(FenceRecipe recipe, FenceGround ground, FenceTuning with, ModelDetail d)
        throws Exception {
        int level = d.level(ID, with.levels());
        AssetRef<MeshData> mesh = AssetRef.generated(RenderAssets.MESH, meshes, new MeshKey(recipe, ground, with,
            level, d.hierarchy()), recipe.seed());
        return model(recipe, ground, with, level, mesh);
    }

    /** One node at the fence's origin: {@code mesh}, with the recipe's materials of the submeshes level has. */
    @AllowAllocation("at load time")
    private ModelData model(FenceRecipe recipe, FenceGround ground, FenceTuning with, int level,
        AssetRef<MeshData> mesh) throws Exception {
        ProceduralMesh source = meshes(recipe, ground, with).get(level);
        List<AssetRef<MaterialData>> materials = new ArrayList<>();
        boolean all = true;
        for (int s = 0; s < source.submeshCount(); s++) {
            if (SceneMeshes.kept(source, s)) {
                String material = recipe.materials().get(s);
                all &= !material.isEmpty();
                if (!material.isEmpty()) {
                    materials.add(paths.ref(RenderAssets.MATERIAL, material));
                }
            }
        }
        List<AssetRef<MaterialData>> slots = all ? materials : List.of();
        FenceGround over = ground != null ? ground : groundOf(recipe);
        double x = recipe.line().get(0);
        double z = recipe.line().get(1);
        double y = over.height(x, z);
        return new ModelData(List.of(new ModelData.Node("fence:" + recipe.type().name(), -1, x, y, z, 0, 0, 0, 1, 1,
            1, 1, mesh, slots)));
    }
}
