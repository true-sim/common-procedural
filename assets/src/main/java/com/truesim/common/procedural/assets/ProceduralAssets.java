package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.flora.Palette;
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
import com.truesim.scene.render.TextureData;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Procedural assets through the scene's asset system. Recipe files ({@code .tree}, {@code .plant}, {@code .rock},
 * {@code .building}; see {@link Recipe}) under any mount import as meshes and models that scenes reference like any
 * other:
 *
 * <pre>{@code
 * PathAssets paths = new PathAssets(cache);
 * paths.mounts().add("game", PathSource.folder(gameDir));
 * new ProceduralAssets(paths, new GenerationCache(cacheDir)).install();
 * AssetRef<ModelData> spruce = paths.ref(RenderAssets.MODEL, "game://forest/spruce-a.tree");   // hierarchy, materials
 * AssetRef<MeshData> far = paths.ref(RenderAssets.MESH, "game://forest/spruce-a.tree#lod/2");
 * AssetRef<MeshData> card = paths.ref(RenderAssets.MESH, "game://forest/spruce-a.tree#billboard");
 * cache.acquire(spruce);                         // generated in the background, on the asset lane
 * }</pre>
 *
 * <ul>
 *   <li><b>Fragments</b> of a recipe's path: none or {@code #lod/0} the most detailed level, {@code #lod/N} level N
 *       (one flat level each), {@code #hierarchy/N} a cluster hierarchy built from level N, {@code #billboard} a
 *       tree's or plant's billboard; as a texture, {@code #palette} the colors of its flowers, fruits and small parts
 *       (the base color of the attachments, foliage and flowers slots). A model is one node with the recipe's mesh
 *       as its {@link ModelDetail} says (by default a cluster hierarchy from a tree's level 1, a plant's level 0: the
 *       engine draws far trees with few meshlets) and the materials of that level's submeshes.</li>
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
    public static final List<String> EXTENSIONS = List.of("tree", "plant", "rock", "building");

    /** A generated mesh's key: the recipe, which of its meshes, and whether as a cluster hierarchy. */
    record MeshKey(Recipe recipe, int index, boolean hierarchy) {
    }

    /** A generated model's key: the recipe, and how its detail is carried. */
    record ModelKey(Recipe recipe, ModelDetail detail) {
    }

    private final PathAssets paths;
    private final GenerationCache cache;
    private final Map<String, ProceduralGenerator> generators = new ConcurrentHashMap<>();
    private final List<LiveRecipe> live = new CopyOnWriteArrayList<>();
    private final Generator<MeshKey, MeshData> meshes;
    private final Generator<ModelKey, ModelData> models;
    private volatile ModelDetail detail = ModelDetail.DEFAULT;

    /** Procedural assets on {@code paths}, generating through {@code cache}. */
    @AllowAllocation("setting up")
    public ProceduralAssets(PathAssets paths, GenerationCache cache) {
        this.meshes = (key, seed) -> sceneMesh(key.recipe(), key.index(), key.hierarchy());
        this.models = (key, seed) -> buildModel(key.recipe(), key.detail());
        this.paths = paths;
        this.cache = cache;
        generator(ProceduralGenerator.TREES);
        generator(ProceduralGenerator.PLANTS);
        generator(ProceduralGenerator.ROCKS);
        generator(BuildingAssets.generator(com.truesim.common.procedural.building.BuildingOptions.DEFAULTS));
    }

    /** Uses {@code generator} for recipes naming its id, in place of any before. */
    public ProceduralAssets generator(ProceduralGenerator generator) {
        generators.put(generator.id(), generator);
        return this;
    }

    /**
     * How models carry their detail from now on ({@link ModelDetail#of} reads it from the settings): references and
     * recipe files imported after this call take it, models made before keep theirs.
     */
    public ProceduralAssets detail(ModelDetail newDetail) {
        this.detail = java.util.Objects.requireNonNull(newDetail, "detail");
        return this;
    }

    /** How models carry their detail. */
    public ModelDetail detail() {
        return detail;
    }

    /** The generator of recipes naming {@code id}, or null. */
    ProceduralGenerator generator(String id) {
        return generators.get(id);
    }

    /**
     * Registers the importers: recipes, meshes and models from recipe files; buildings' layouts
     * ({@link BuildingAssets#LAYOUT}).
     */
    @AllowAllocation("setting up")
    public ProceduralAssets install() {
        AssetLoader<Recipe> recipes = ProceduralAssets::readRecipe;
        AssetLoader<MeshData> meshImporter = this::importMesh;
        AssetLoader<ModelData> modelImporter = this::importModel;
        AssetLoader<TextureData> textureImporter = this::importTexture;
        for (String extension : EXTENSIONS) {
            paths.importers().add(RECIPE, extension, recipes).add(RenderAssets.MESH, extension, meshImporter)
                .add(RenderAssets.MODEL, extension, modelImporter).add(RenderAssets.TEXTURE, extension,
                    textureImporter);
        }
        BuildingAssets.install(paths, this);
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

    /** A generated reference to mesh {@code index} of {@code recipe} (its levels, then a tree's billboard), flat. */
    @AllowAllocation("references, at load time")
    public AssetRef<MeshData> mesh(Recipe recipe, int index) {
        return mesh(recipe, index, false);
    }

    /** A generated reference to mesh {@code index} of {@code recipe}, as a cluster hierarchy or flat. */
    @AllowAllocation("references, at load time")
    public AssetRef<MeshData> mesh(Recipe recipe, int index, boolean hierarchy) {
        return AssetRef.generated(RenderAssets.MESH, meshes, new MeshKey(recipe, index, hierarchy), recipe.seed());
    }

    /** A generated reference to the model of {@code recipe}, its detail as {@link #detail()} says, with its materials. */
    @AllowAllocation("references, at load time")
    public AssetRef<ModelData> model(Recipe recipe) {
        return model(recipe, detail);
    }

    /** A generated reference to the model of {@code recipe}, its detail as {@code modelDetail} says. */
    @AllowAllocation("references, at load time")
    public AssetRef<ModelData> model(Recipe recipe, ModelDetail modelDetail) {
        return AssetRef.generated(RenderAssets.MODEL, models, new ModelKey(recipe, modelDetail), recipe.seed());
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
        boolean hierarchy = false;
        if (fragment == null || fragment.isEmpty()) {
            index = 0;
        } else if (fragment.equals("billboard") && generators.containsKey(recipe.generator())
            && generators.get(recipe.generator()).billboard()) {
            index = meshes(recipe).size() - 1;
        } else if (fragment.startsWith("lod/")) {
            index = Integer.parseInt(fragment.substring("lod/".length()));
        } else if (fragment.startsWith("hierarchy/")) {
            index = Integer.parseInt(fragment.substring("hierarchy/".length()));
            hierarchy = true;
        } else {
            throw new IllegalArgumentException("No mesh '#" + fragment + "' in " + input.path());
        }
        return sceneMesh(recipe, index, hierarchy);
    }

    /** {@code #palette}: the colors of a tree's or plant's flowers, fruits and small parts, as an sRGB texture. */
    @AllowAllocation("at load time")
    private TextureData importTexture(AssetInput input) throws Exception {
        Recipe recipe = readRecipe(input);
        if (!"palette".equals(input.path().fragment())) {
            throw new IllegalArgumentException("No texture '#" + input.path().fragment() + "' in " + input.path());
        }
        return palette(recipe);
    }

    /** The palette texture of {@code recipe}: its flowers', fruits' and small parts' colors. */
    @AllowAllocation("at load time")
    public TextureData palette(Recipe recipe) {
        ProceduralGenerator generator = generators.get(recipe.generator());
        Palette palette = generator == null ? null : generator.palette(recipe);
        if (palette == null) {
            throw new IllegalArgumentException("A " + recipe.generator() + " has no palette");
        }
        MemorySegment texels = Arena.ofAuto().allocate(4L * palette.width() * palette.height());
        MemorySegment.copy(MemorySegment.ofArray(palette.texels()), 0, texels, 0, texels.byteSize());
        return new TextureData(palette.width(), palette.height(), TextureData.ColorSpace.SRGB, texels);
    }

    @AllowAllocation("at load time")
    private ModelData importModel(AssetInput input) throws Exception {
        Recipe recipe = readRecipe(input);
        ModelDetail d = detail;
        int level = level(recipe, d);
        String fragment = (d.hierarchy() ? "hierarchy/" : "lod/") + level;
        return model(recipe, level, paths.ref(RenderAssets.MESH, input.path().file().fragment(fragment)));
    }

    /** The generated level a model of {@code recipe} starts from, as {@code d} says. */
    private int level(Recipe recipe, ModelDetail d) throws Exception {
        int count = meshes(recipe).size();
        ProceduralGenerator generator = generators.get(recipe.generator());
        int levels = generator != null && !generator.billboard() ? count : Math.max(1, count - 1); // but a billboard
        return d.level(recipe.generator(), levels);
    }

    @AllowAllocation("at load time")
    private MeshData sceneMesh(Recipe recipe, int index, boolean hierarchy) throws Exception {
        List<ProceduralMesh> all = meshes(recipe);
        if (index < 0 || index >= all.size()) {
            throw new IllegalArgumentException("A " + recipe.generator() + " has meshes 0 to " + (all.size() - 1)
                + ", not " + index);
        }
        ProceduralGenerator generator = generators.get(recipe.generator());
        return SceneMeshes.build(all.get(index), generator.sways(), generator.tinted(), hierarchy);
    }

    @AllowAllocation("at load time")
    private ModelData buildModel(Recipe recipe, ModelDetail d) throws Exception {
        int level = level(recipe, d);
        return model(recipe, level, mesh(recipe, level, d.hierarchy()));
    }

    /** A model of one node: {@code mesh}, made from level {@code level}, with the recipe's materials of its submeshes. */
    @AllowAllocation("at load time")
    private ModelData model(Recipe recipe, int level, AssetRef<MeshData> mesh) throws Exception {
        ProceduralMesh source = meshes(recipe).get(level);
        List<AssetRef<MaterialData>> materials = new ArrayList<>();
        boolean all = true;
        for (int s = 0; s < source.submeshCount(); s++) {
            if (SceneMeshes.kept(source, s)) {
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
