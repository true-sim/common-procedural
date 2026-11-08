package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.scene.assets.AssetInput;
import com.truesim.scene.assets.AssetLoader;
import com.truesim.scene.assets.AssetPath;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Procedural assets through the scene's asset system. Recipe files ({@code .tree}, {@code .rock}; see
 * {@link Recipe}) under any mount import as meshes and models that scenes reference like any other:
 *
 * <pre>{@code
 * PathAssets paths = new PathAssets(cache);
 * paths.mounts().add("game", PathSource.folder(gameDir));
 * new ProceduralAssets(paths, new GenerationCache(cacheDir)).install();
 * AssetRef<ModelData> spruce = paths.ref(RenderAssets.MODEL, "game://forest/spruce-a.tree");   // LOD 0, materials
 * AssetRef<MeshData> far = paths.ref(RenderAssets.MESH, "game://forest/spruce-a.tree#lod/2");
 * AssetRef<MeshData> card = paths.ref(RenderAssets.MESH, "game://forest/spruce-a.tree#billboard");
 * cache.acquire(spruce);                         // generated in the background, on the asset lane
 * }</pre>
 *
 * <ul>
 *   <li><b>Fragments</b> of a recipe's path: none or {@code #lod/0} the most detailed level, {@code #lod/N} level N,
 *       {@code #billboard} a tree's billboard. A model is one node with the recipe's level 0 and its materials.</li>
 *   <li><b>In the background:</b> the cache runs importers on its asset lane (the job system), so generating never
 *       blocks the frame; loads finish at the frame boundary as all assets do.</li>
 *   <li><b>Generated once:</b> meshes go through a {@link GenerationCache} keyed by the recipe and the generator's
 *       version, so a recipe is generated once until it or the generator changes, across runs.</li>
 *   <li><b>Streaming:</b> they are ordinary assets: acquired and released by cells, evicted under the cache's budget
 *       (closing their memory), loaded again from the generation cache when acquired again.</li>
 *   <li><b>Without files:</b> {@link #model(Recipe)} and {@link #mesh(Recipe, int)} give generated references
 *       keyed by the recipe itself, for recipes made in code.</li>
 *   <li><b>Hot reload:</b> {@link #live(String)} follows a recipe file; when its text changes ({@link #poll()}),
 *       listeners get the new recipe, whose generated references are new assets: acquire them and swap.</li>
 * </ul>
 */
public final class ProceduralAssets {

    /** Recipes, read from recipe files. */
    public static final AssetType<Recipe> RECIPE = AssetType.of("procedural-recipe", Recipe.class, recipe -> 256);
    /** The file extensions of recipes. */
    public static final List<String> EXTENSIONS = List.of("tree", "rock");

    /** A generated mesh's key: the recipe and which of its meshes. */
    record MeshKey(Recipe recipe, int index) {
    }

    private final PathAssets paths;
    private final GenerationCache cache;
    private final Map<String, ProceduralGenerator> generators = new ConcurrentHashMap<>();
    private final List<LiveRecipe> live = new CopyOnWriteArrayList<>();
    private final Generator<MeshKey, MeshData> meshes;
    private final Generator<Recipe, ModelData> models;

    /** Procedural assets on {@code paths}, generating through {@code cache}. */
    @AllowAllocation("setting up")
    public ProceduralAssets(PathAssets paths, GenerationCache cache) {
        this.meshes = (key, seed) -> sceneMesh(key.recipe(), key.index());
        this.models = (recipe, seed) -> model(recipe, mesh(recipe, 0));
        this.paths = paths;
        this.cache = cache;
        generator(ProceduralGenerator.TREES);
        generator(ProceduralGenerator.ROCKS);
    }

    /** Uses {@code generator} for recipes naming its id, in place of any before. */
    public ProceduralAssets generator(ProceduralGenerator generator) {
        generators.put(generator.id(), generator);
        return this;
    }

    /** Registers the importers: recipes, meshes and models from {@code .tree} and {@code .rock} files. */
    @AllowAllocation("setting up")
    public ProceduralAssets install() {
        AssetLoader<Recipe> recipes = ProceduralAssets::readRecipe;
        AssetLoader<MeshData> meshImporter = this::importMesh;
        AssetLoader<ModelData> modelImporter = this::importModel;
        for (String extension : EXTENSIONS) {
            paths.importers().add(RECIPE, extension, recipes).add(RenderAssets.MESH, extension, meshImporter)
                .add(RenderAssets.MODEL, extension, modelImporter);
        }
        return this;
    }

    /** The meshes of {@code recipe}, through the generation cache: levels of detail, then extras (a billboard). */
    public List<ProceduralMesh> meshes(Recipe recipe) throws Exception {
        ProceduralGenerator generator = generators.get(recipe.generator());
        if (generator == null) {
            throw new IllegalArgumentException("No generator '" + recipe.generator() + "'");
        }
        return cache.get(generator, recipe);
    }

    /** A generated reference to mesh {@code index} of {@code recipe} (its levels, then a tree's billboard). */
    @AllowAllocation("references, at load time")
    public AssetRef<MeshData> mesh(Recipe recipe, int index) {
        return AssetRef.generated(RenderAssets.MESH, meshes, new MeshKey(recipe, index), recipe.seed());
    }

    /** A generated reference to the model of {@code recipe}: its level 0 with its materials. */
    @AllowAllocation("references, at load time")
    public AssetRef<ModelData> model(Recipe recipe) {
        return AssetRef.generated(RenderAssets.MODEL, models, recipe, recipe.seed());
    }

    /** Follows the recipe file at {@code path} for hot reload: read now, and again on each {@link #poll()}. */
    @AllowAllocation("setting up")
    public LiveRecipe live(String path) throws IOException {
        LiveRecipe recipe = new LiveRecipe(this, AssetPath.parse(path));
        live.add(recipe);
        return recipe;
    }

    /** Reads every live recipe's file again and tells listeners of those that changed. */
    public void poll() throws IOException {
        for (LiveRecipe recipe : live) {
            recipe.poll();
        }
    }

    /** The text of the file at {@code path}, through the mounts. */
    @AllowAllocation("at load time")
    String read(AssetPath path) throws IOException {
        paths.remember(path);
        try (InputStream in = paths.source().open(path.id(), com.truesim.scene.assets.AssetSource.MAIN)) {
            if (in == null) {
                throw new java.io.FileNotFoundException(path.toString());
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AllowAllocation("at load time")
    private static Recipe readRecipe(AssetInput input) throws IOException {
        try (InputStream in = input.open()) {
            return Recipe.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @AllowAllocation("at load time")
    private MeshData importMesh(AssetInput input) throws Exception {
        Recipe recipe = readRecipe(input);
        String fragment = input.path().fragment();
        int index;
        if (fragment == null || fragment.isEmpty()) {
            index = 0;
        } else if (fragment.equals("billboard") && recipe.generator().equals("tree")) {
            index = meshes(recipe).size() - 1;
        } else if (fragment.startsWith("lod/")) {
            index = Integer.parseInt(fragment.substring("lod/".length()));
        } else {
            throw new IllegalArgumentException("No mesh '#" + fragment + "' in " + input.path());
        }
        return sceneMesh(recipe, index);
    }

    @AllowAllocation("at load time")
    private ModelData importModel(AssetInput input) throws Exception {
        Recipe recipe = readRecipe(input);
        return model(recipe, paths.ref(RenderAssets.MESH, input.path().file().fragment("lod/0")));
    }

    @AllowAllocation("at load time")
    private MeshData sceneMesh(Recipe recipe, int index) throws Exception {
        List<ProceduralMesh> all = meshes(recipe);
        if (index < 0 || index >= all.size()) {
            throw new IllegalArgumentException("A " + recipe.generator() + " has meshes 0 to " + (all.size() - 1)
                + ", not " + index);
        }
        return SceneMeshes.build(all.get(index), generators.get(recipe.generator()).sways());
    }

    /** A model of one node: {@code mesh} with the recipe's materials of the submeshes level 0 has. */
    @AllowAllocation("at load time")
    private ModelData model(Recipe recipe, AssetRef<MeshData> mesh) throws Exception {
        ProceduralMesh first = meshes(recipe).getFirst();
        List<AssetRef<MaterialData>> materials = new ArrayList<>();
        boolean all = true;
        for (int s = 0; s < first.submeshCount(); s++) {
            if (SceneMeshes.kept(first, s)) {
                String material = s < recipe.materials().size() ? recipe.materials().get(s) : "";
                all &= !material.isEmpty();
                if (!material.isEmpty()) {
                    materials.add(paths.ref(RenderAssets.MATERIAL, material));
                }
            }
        }
        // A material for every submesh, or none (the renderer's default)
        List<AssetRef<MaterialData>> slots = all ? materials : List.of();
        return new ModelData(List.of(ModelData.Node.of(recipe.generator() + ":" + recipe.preset(), -1, mesh, slots)));
    }
}
