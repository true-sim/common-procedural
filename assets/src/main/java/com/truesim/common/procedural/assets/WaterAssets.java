package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.tree.Season;
import com.truesim.common.procedural.water.BedStone;
import com.truesim.common.procedural.water.WaterNetwork;
import com.truesim.common.procedural.water.WaterRecipe;
import com.truesim.common.procedural.water.Watercourse;
import com.truesim.common.procedural.water.Watercourses;
import com.truesim.scene.assets.AssetInput;
import com.truesim.scene.assets.AssetRef;
import com.truesim.scene.assets.AssetType;
import com.truesim.scene.assets.Generator;
import com.truesim.scene.assets.PathAssets;
import com.truesim.scene.render.MaterialData;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.render.ModelData;
import com.truesim.scene.render.RenderAssets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Water through the scene's asset system: {@code .water} recipes ({@link WaterRecipe}) and {@code .courses} files of
 * mapped watercourses ({@link Watercourses}) load as assets; a network over a terrain is generated in the background
 * as an asset of its own, and its surfaces, falls and stones are generated meshes and models, cached and streamed
 * like every other.
 *
 * <pre>{@code
 * WaterAssets.install(paths);
 * WaterAssets water = new WaterAssets(paths);
 * AssetRef<WaterRecipe> recipe = paths.ref(WaterAssets.WATER, "game://water/lauterbrunnen.water");
 * // once loaded (and its courses, if it names any: paths.ref(WaterAssets.COURSES, recipe.courses())):
 * AssetRef<WaterNetwork> rivers = water.network(cache.get(recipe), terrain, courses);   // built on the asset lane
 * // once loaded:
 * for (WaterNetwork.WaterChunk chunk : network.chunks()) {
 *     AssetRef<ModelData> surface = water.surface(network, cache.get(recipe), chunk);   // placed at the chunk
 * }
 * AssetRef<ModelData> staubbach = water.fall(network, recipe, network.tallest().id()); // placed at its lip
 * AssetRef<ModelData> stone = water.stone(procedural, recipe, stoneOfTheBed);         // ProceduralAssets' rocks
 * }</pre>
 */
public final class WaterAssets {

    /** Water recipes, read from {@code .water} files. */
    public static final AssetType<WaterRecipe> WATER = AssetType.of("procedural-water", WaterRecipe.class,
        recipe -> 1024);
    /** Mapped watercourses, read from {@code .courses} files. */
    public static final AssetType<Courses> COURSES = AssetType.of("procedural-watercourses", Courses.class,
        courses -> 64L * courses.courses().size() + 16L * courses.points());
    /** Water networks, generated over a terrain. */
    public static final AssetType<WaterNetwork> NETWORK = AssetType.of("procedural-water-network",
        WaterNetwork.class, WaterNetwork::bytes);
    /** The file extension of water recipes. */
    public static final String EXTENSION = "water";
    /** The file extension of mapped watercourses. */
    public static final String COURSES_EXTENSION = "courses";
    /** Rock variants per preset the beds' stones draw from (a stone's seed picks one). */
    public static final int STONE_VARIANTS = 8;

    /**
     * Mapped watercourses, as an asset.
     *
     * @param courses the watercourses, in the terrain's frame
     */
    public record Courses(List<Watercourse> courses) {

        public Courses {
            courses = List.copyOf(courses);
        }

        /** How many points they have. */
        public long points() {
            long n = 0;
            for (Watercourse w : courses) {
                n += w.pointCount();
            }
            return n;
        }
    }

    /** A network's key: the recipe, the terrain (by identity) and the watercourses. */
    record NetworkKey(WaterRecipe recipe, EcologyTerrain terrain, List<Watercourse> courses) {
    }

    /** A surface's key: the network (by identity), the chunk, and as a model the material. */
    record ChunkKey(WaterNetwork network, int x, int z, String material) {
    }

    /** A fall's key: the network, the fall, the sheet's and jets' materials. */
    record FallKey(WaterNetwork network, int fall, String sheet, String jets) {
    }

    private final PathAssets paths;
    private final Generator<NetworkKey, WaterNetwork> networks;
    private final Generator<ChunkKey, MeshData> surfaceMeshes;
    private final Generator<ChunkKey, ModelData> surfaceModels;
    private final Generator<FallKey, MeshData> fallMeshes;
    private final Generator<FallKey, ModelData> fallModels;

    /** Water's generated assets on {@code paths}. */
    @AllowAllocation("setting up")
    public WaterAssets(PathAssets paths) {
        this.paths = paths;
        this.networks = (key, seed) -> WaterNetwork.build(key.terrain(), key.recipe().region(),
            key.recipe().parameters(), key.courses());
        this.surfaceMeshes = (key, seed) -> SceneMeshes.build(key.network().surface(key.x(), key.z()), true, false);
        this.surfaceModels = (key, seed) -> {
            double chunk = key.network().parameters().surface().chunk();
            return model("water:surface", new double[] {key.x() * chunk, 0, key.z() * chunk},
                surfaceMesh(key.network(), key.x(), key.z()), List.of(key.material()));
        };
        this.fallMeshes = (key, seed) -> SceneMeshes.build(key.network().fallMesh(key.fall()), true, false);
        this.fallModels = (key, seed) -> {
            ProceduralMesh mesh = key.network().fallMesh(key.fall());
            List<String> slots = new ArrayList<>();
            if (SceneMeshes.kept(mesh, 0)) {
                slots.add(key.sheet());
            }
            if (SceneMeshes.kept(mesh, 1)) {
                slots.add(key.jets());
            }
            return model("water:fall", key.network().fallOrigin(key.fall()), fallMesh(key.network(), key.fall()),
                slots);
        };
    }

    /** Registers the importers of {@code .water} and {@code .courses} files. */
    @AllowAllocation("setting up")
    public static void install(PathAssets paths) {
        paths.importers().add(WATER, EXTENSION, WaterAssets::readRecipe).add(COURSES, COURSES_EXTENSION,
            WaterAssets::readCourses);
    }

    /** The network of {@code recipe} over {@code terrain}, from the flow alone, generated in the background. */
    @AllowAllocation("references, at load time")
    public AssetRef<WaterNetwork> network(WaterRecipe recipe, EcologyTerrain terrain) {
        return network(recipe, terrain, List.of());
    }

    /** The network of {@code recipe} over {@code terrain} following {@code courses}, generated in the background. */
    @AllowAllocation("references, at load time")
    public AssetRef<WaterNetwork> network(WaterRecipe recipe, EcologyTerrain terrain, List<Watercourse> courses) {
        return AssetRef.generated(NETWORK, networks, new NetworkKey(recipe, terrain, List.copyOf(courses)),
            recipe.parameters().seed());
    }

    /** The surface mesh of {@code network}'s chunk ({@code x}, {@code z}), relative to its corner. */
    @AllowAllocation("references, at load time")
    public AssetRef<MeshData> surfaceMesh(WaterNetwork network, int x, int z) {
        return AssetRef.generated(RenderAssets.MESH, surfaceMeshes, new ChunkKey(network, x, z, ""),
            network.parameters().seed());
    }

    /** The surface of {@code chunk} as a model with the recipe's {@code surface} material, placed at its corner. */
    @AllowAllocation("references, at load time")
    public AssetRef<ModelData> surface(WaterNetwork network, WaterRecipe recipe, WaterNetwork.WaterChunk chunk) {
        return AssetRef.generated(RenderAssets.MODEL, surfaceModels, new ChunkKey(network, chunk.x(), chunk.z(),
            recipe.material("surface")), network.parameters().seed());
    }

    /** Free fall {@code fall}'s sheet and jets as a mesh, relative to its lip ({@link WaterNetwork#fallOrigin}). */
    @AllowAllocation("references, at load time")
    public AssetRef<MeshData> fallMesh(WaterNetwork network, int fall) {
        return AssetRef.generated(RenderAssets.MESH, fallMeshes, new FallKey(network, fall, "", ""),
            network.parameters().seed());
    }

    /**
     * Free fall {@code fall} as a model with the recipe's {@code falls} (sheet) and {@code jets} materials, its node at
     * the lip ({@link WaterNetwork#fallOrigin}).
     */
    @AllowAllocation("references, at load time")
    public AssetRef<ModelData> fall(WaterNetwork network, WaterRecipe recipe, int fall) {
        return AssetRef.generated(RenderAssets.MODEL, fallModels, new FallKey(network, fall, recipe.material("falls"),
            recipe.material("jets")), network.parameters().seed());
    }

    /**
     * The rock model a bed stone draws: {@link ProceduralAssets}' rock of its preset, one of {@link #STONE_VARIANTS}
     * by its seed, with the recipe's {@code stones} material; scale it to the stone's size and turn it by its yaw.
     */
    @AllowAllocation("references, at load time")
    public static AssetRef<ModelData> stone(ProceduralAssets procedural, WaterRecipe recipe, BedStone stone) {
        String material = recipe.material("stones");
        return procedural.model(new Recipe("rock", stone.preset(), Math.floorMod(stone.seed(), STONE_VARIANTS),
            Season.SUMMER, 0, List.of(material)));
    }

    @AllowAllocation("at load time")
    private ModelData model(String name, double[] at, AssetRef<MeshData> mesh, List<String> materials) {
        List<AssetRef<MaterialData>> slots = new ArrayList<>();
        for (String material : materials) {
            if (material.isEmpty()) {
                // a material for every submesh, or none (the renderer's default)
                slots.clear();
                break;
            }
            slots.add(paths.ref(RenderAssets.MATERIAL, material));
        }
        return new ModelData(List.of(new ModelData.Node(name, -1, at[0], at[1], at[2], 0, 0, 0, 1, 1, 1, 1, mesh,
            slots)));
    }

    @AllowAllocation("at load time")
    private static WaterRecipe readRecipe(AssetInput input) throws IOException {
        try (InputStream in = input.open()) {
            return WaterRecipe.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @AllowAllocation("at load time")
    private static Courses readCourses(AssetInput input) throws IOException {
        try (InputStream in = input.open()) {
            return new Courses(Watercourses.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }
}
