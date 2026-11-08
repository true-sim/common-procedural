# common-procedural

Procedural assets for true-sim, generated deterministically from parameters and a seed: trees first, rocks, and more
to come ([roadmap](#roadmap)). The generators are engine-agnostic (common-math only); a second module brings them
into the scene's asset system as recipe files.

| Module | Package | Contents |
|---|---|---|
| `common-procedural` | `com.truesim.common.procedural` | the generators, the direct Java API: trees, rocks, their meshes |
| `common-procedural-assets` | `com.truesim.common.procedural.assets` | recipe files imported as scene meshes and models, generated in the background, cached |
| `preview` (not published) | | pictures and films through the engine, with a minimal tree material reading the wind format |

```kotlin
dependencies {
    implementation("com.truesim.common:common-procedural:0.1.0")          // tools, servers, tests: no engine
    implementation("com.truesim.common:common-procedural-assets:0.1.0")   // games: through the asset cache
}
```

General math lives in [common-math](https://github.com/true-sim/common-math) (polyline frames, octahedral maps, mesh
normals and closedness, ridged and Worley noise, Poisson-disc sampling); this repository holds only generation.

## Two ways in

**Directly**, anywhere (tools, servers, tests, a bake step):

```java
Tree spruce = Trees.generate(TreeSpecies.SPRUCE, 42);
Tree larch = Trees.generate(TreeSpecies.LARCH, TreeOptions.DEFAULTS.withSeason(Season.AUTUMN).withSnowLoad(0.3), 7);
ProceduralMesh near = spruce.lods().getFirst();      // submesh 0 bark, 1 leaves
ProceduralMesh far = spruce.billboard();
ImpostorHints impostor = spruce.impostor();

Rock boulder = Rocks.generate(RockParams.BOULDER, 3);

TreeGenerator generator = new TreeGenerator();      // one per thread keeps its working memory: for forests
for (int i = 0; i < 1000; i++) forest.add(generator.generate(species(i), Seeds.derive(forestSeed, i)));
```

**Through the asset manager**, as any model: a recipe file is a few lines, its path a model or a mesh.

```
# game://forest/spruce-a.tree
generator = tree
preset = spruce               # a TreeSpecies preset: spruce fir larch pine beech birch hazel mugo
seed = 42
season = summer               # spring summer autumn winter
snow = 0.3                    # 0 to 1
material.bark = game://materials/spruce-bark.material
material.leaves = game://materials/spruce-needles.material
```

```java
PathAssets paths = new PathAssets(cache);
paths.mounts().add("game", PathSource.folder(gameDir));
ProceduralAssets procedural = new ProceduralAssets(paths, new GenerationCache(cacheDir)).install();

AssetRef<ModelData> spruce = paths.ref(RenderAssets.MODEL, "game://forest/spruce-a.tree"); // level 0 + materials
AssetRef<MeshData> far = paths.ref(RenderAssets.MESH, "game://forest/spruce-a.tree#lod/2");
AssetRef<MeshData> card = paths.ref(RenderAssets.MESH, "game://forest/spruce-a.tree#billboard");
AssetRef<MeshData> stone = paths.ref(RenderAssets.MESH, "game://rocks/block.rock#lod/1");
AssetRef<ModelData> inCode = procedural.model(new Recipe("tree", "larch", 7, Season.AUTUMN, 0, List.of("", "")));
```

- **In the background:** importers run on the asset cache's lane (the job system); loads finish at the frame
  boundary like every asset. A recipe imports as a `MODEL` (one node: level 0 with its materials, or none if a slot
  has none), a `MESH` (fragments `#lod/N` and `#billboard`), and a `RECIPE`.
- **Generated once:** `GenerationCache` keeps generated meshes on disk keyed by SHA-256 of the generator's id and
  version and the recipe's generation key (generator, preset, seed, season, snow; not the materials). A recipe or a
  generator version that changes makes a new key: regeneration happens only on change, across runs; files are written
  whole and moved into place, so concurrent loads are safe.
- **Streaming:** the meshes are ordinary assets: cells acquire and release them, the cache evicts them under its budget
  (closing their memory) and loads them again, from the generation cache, when acquired again.
- **Hot reload:** `procedural.live(path)` follows a recipe file; `procedural.poll()` (each second, or on a file event)
  reads it again and tells listeners when the recipe changed. A new recipe's generated references are new assets:
  acquire the new model, swap it in when loaded, release the old. Comment-only edits change nothing; a broken edit
  keeps the old recipe and reports the error.
- **Tested** (`ProceduralAssetsTest`): a recipe file through the real `AssetCache`, `PathAssets` and importers gives
  meshes byte-identical to the direct API's (positions, attributes, meshlets); a second load after eviction reads the
  generation cache (no generation; also from a new cache on the same folder, as a new run would); a bumped generator
  version generates again; fragments, missing presets and levels fail as assets do; live recipes reload on change.

## Trees

Eight presets, tuned by eye to their species' habit, for the Alpine showcase first:

| Preset | Species | Growth | Foliage |
|---|---|---|---|
| `SPRUCE` | Norway spruce (*Picea abies*) | parametric: whorls, conical, tips curving up, hanging side shoots | needle sprays |
| `FIR` | silver fir (*Abies alba*) | parametric: straighter, cylindrical, flat horizontal sprays | needle sprays |
| `LARCH` | European larch (*Larix decidua*) | parametric: spiral, open cone, hanging twigs; golden in autumn, bare in winter | needle tufts |
| `PINE` | Scots pine (*Pinus sylvestris*) | parametric: tall bare trunk, flat irregular crown | needle tufts at the tips |
| `BEECH` | European beech (*Fagus sylvatica*) | space colonization in an ellipsoid | leaf clusters |
| `BIRCH` | silver birch (*Betula pendula*) | space colonization in an egg, drooping | leaf clusters |
| `HAZEL` | hazel (*Corylus avellana*), shrub | space colonization over six stems | leaf clusters |
| `MUGO` | mountain pine (*Pinus mugo*), shrub | parametric over seven creeping, rising stems | needle tufts |

### How a tree is made

1. **Growth.** Two published methods, each where it fits:
   - **Parametric** (Weber and Penn, "Creation and rendering of realistic trees", SIGGRAPH 1995) for conifers: their
     structure is regular (strong apical dominance, whorls or spirals, a crown shaped by branch lengths), so per-level
     rules (`Branching`: start and end along the parent, spacing, whorl size, rotation, down angle, length by a crown
     shape, curve, droop, planarity) reproduce it directly and cheaply.
   - **Space colonization** (Runions, Lane and Prusinkiewicz, "Modeling trees with a space colonization algorithm",
     2007) for broadleaves and shrubs: attraction points fill the crown's envelope (`Crown`), the nearest node within
     the influence distance grows toward each point's pull, points are used up within the kill distance. Branches
     compete for space and fill the crown irregularly, as beech and birch do. Nearest-node queries go through a grid.
     The node graph is smoothed and split into branches, each continuing into its heaviest child; the trunk ends at the
     crown's base and the limbs above become its main branches (so wind pivots stay on the trunk's axis).
2. **Snow.** A snow load sinks every branch by `snowDroop * snowLoad * s² / length` along it, children riding their
   parents (the snow itself is the material's: the engine's snow cover).
3. **Leaves.** Cards along the outer part of every branch from the foliage's level: needle sprays (two cards crossed
   along the shoot), needle tufts (two crossed cards spreading out), leaf clusters (rosettes of three cards), facing
   out of the crown. Placed for every season, shown or not, so the wood is the same in every season.
4. **Pipe model.** Radii follow the pipe model (Shinozaki et al. 1964; da Vinci's rule): every node carries what is
   above it, `r^e = sum of the children's r^e + its leaves' pipes`, with the species' exponent `e` (2 to 2.3); a bare
   tip one pipe, a leaf card one. All radii then scale so the trunk's base has the species' radius (scaled with the
   tree's height), which keeps the relation.
5. **Meshing.** Every branch is a generalized cylinder: rings swept along it at its nodes with rotation-minimizing
   frames (common-math `PolylineFrames`), normals tilted by the radius' slope, closed by a cone past the tip and a fan
   at the base. **Junctions:** a branch starts on its parent's axis, inside it, so the surfaces meet without gaps at
   any angle, and flares by a third where it leaves the parent's surface, easing back over two radii (a collar), with
   normals following the flare; trunks flare at the roots. Each branch is a closed, consistently wound surface;
   branches overlap their parents rather than being joined into one surface. **Bark UVs:** u around (a whole number of
   tiles by the branch's girth), v along in tiles of `barkTileLength`, continuing from where the branch leaves its
   parent; v restarts two tiles lower on a duplicated ring whenever it passes 4, so float16 UVs stay precise (about
   1/500 of a tile). **Leaf UVs:** each season's tile of the species' atlas (`Foliage`: 2 x 2 by default, spring,
   summer, autumn, winter in reading order), u across the card, v from where it is attached (0) to its far end (1).
6. **Levels of detail** (`TreeLod`): fewer sides per branch (trunk, main branches, twigs), rings every n-th node,
   branches thinner than a radius left to the leaves, one leaf card in n kept and scaled up by √n to cover as much.
   Each level has a **triangle budget**; over it, the level drops more cards and twigs until it fits. Its `error`
   (metres) says how far it may differ from the whole tree (the thickest dropped branch, the cards' growth), never
   less than the finer level's, for an LOD selection to project to pixels. Defaults: 90,000, 25,000 and 8,000
   triangles. Then a **billboard** (three quads crossed at 60 degrees through the trunk, each mapping its whole UV
   square to a side capture of the tree) and **impostor hints** (`ImpostorHints`: the bounding sphere, an 8 x 8
   upper-hemisphere octahedral grid of view directions via common-math `Octahedral`, 256 pixels per view, camera
   positions; baking is #7).
7. **Meshlet-friendly order:** vertices in the order the triangles first use them, triangles ring by ring along a
   branch and card by card, so neighbours share vertices; scene-meshbuild then cuts meshlets (`SceneMeshes`).

All of it is deterministic: the same species, options and seed give bit-identical meshes on every platform (seeds are
split per stage with `Seeds.derive`; math is common-math's `Maths`, never `java.lang.Math`'s inexact functions).

## The vertex format and the wind data

Every generated mesh is `ProceduralMesh`: vertices in a common-math `ElementBuffer` of `ProceduralVertex.LAYOUT`
(32 bytes), triangles as `int` indices, submeshes as index ranges, bounds and the LOD error. Every attribute after the
position has the encoding of a scene mesh attribute, so it maps onto `MeshBuilder` one to one (`SceneMeshes.FORMAT`:
`NORMAL, TANGENT, UV0, UV1, COLOR`, 20 bytes per vertex in the engine):

| Field | Format | Scene attribute | Contents |
|---|---|---|---|
| `POSITION` | FLOAT3 | positions | mesh space, metres, +y up, the trunk's base at the origin |
| `NORMAL` | OCT16 | `NORMAL` | unit normal (leaf cards: bent half out of the crown for lighting as a volume) |
| `TANGENT` | OCT16 | `TANGENT` | unit tangent along +u; bitangent `cross(normal, tangent)` along +v, handedness always +1 |
| `UV` | HALF2 | `UV0` | bark tiles, or the leaf atlas tile |
| `WIND_PIVOT` | HALF2 | `UV1` | x: the main branch's pivot height; y: its reach (metres) |
| `WIND` | UNORM8_4 | `COLOR` | r: trunk weight, g: branch weight, b: leaf weight and phase (a nibble each), a: 1 |

**Wind** follows the usual hierarchy of vegetation animation (trunk bending, branch swaying, leaf flutter; Sousa,
"Vegetation procedural animation and shading in Crysis", GPU Gems 3, ch. 16), all in mesh space:

- **Trunk weight** (`COLOR.r`, 0 to 1): how much the vertex follows its trunk's bend: `(h / height)^1.5` up the trunk
  (each stem of a shrub its own); every vertex of a branch takes the value at the branch's pivot, so branches ride the
  trunk rigidly. Bend the trunk about its foot by an angle times this weight.
- **Branch weight** (`COLOR.g`, 0 to 1): how far the vertex swings with its **main branch** (the trunk's child it
  belongs to, sub-branches and leaves included): `(s / reach)^1.5` of the path `s` from the pivot, 1 at the reach and
  beyond; 0 on the trunk.
- **Pivot** (`UV1.x`): the main branch sways about `(0, UV1.x, 0)`, where it leaves the trunk. Trunks are near the mesh's
  y axis (their lean is kept within a few percent of the height, tested to 5 %; broadleaf trunks end at the crown's
  base), so the pivot is that close. On the trunk it is 0: the trunk bends about its base. The branch's direction at a
  vertex is `normalize(position - pivot)`: turning the vertex about the pivot by an angle proportional to the branch
  weight keeps branch lengths.
- **Reach** (`UV1.y`): the main branch's length in metres (the trunk's height on the trunk): long branches can sway
  slower, as a cantilever's natural frequency drops with its length.
- **Leaf weight and phase** (`COLOR.b`): the byte `b = 16 * round(15 * leaf) + floor(16 * phase)`
  (`ProceduralVertex.packLeafAndPhase`). The leaf weight is the flutter, 0 on wood, on a card 0 where it is attached
  and the species' flutter (birch 1, needles about 0.2) at its far end. The phase (16 steps) is the main branch's, so a
  branch sways as one; 0 on the trunk.
- **Alpha** (`COLOR.a`) is always 1: alpha-masked materials multiply their mask by the vertex color's alpha.
- **Materials** reading the format must not tint by the vertex color (metallic-roughness does: the preview's material
  and effects-vegetation's plant material set it to white for shading). Meshes that do not sway (rocks) leave UV1 and
  COLOR out of their scene mesh (`SceneMeshes.STILL`), so plain materials draw them as they are.

Decoding in a material's `displace()` (the preview's `proceduralTree.slang` does this):

```slang
float trunk = in.color.r;
float branch = in.color.g;
uint packed = uint(round(saturate(in.color.b) * 255));
float leaf = float(packed >> 4) / 15;
float phase = float(packed & 15u) / 16;
float3 pivot = float3(0, in.uv1.x, 0);
float reach = max(in.uv1.y, 1e-3);
// branch: rotate (position - pivot) toward the wind by sway * branch * sin(t * f(reach) + TAU * phase), length kept
// trunk: lean about the foot by lean * trunk, length kept; leaves: position += normal * flutter * leaf * sin(...)
```

The weights are tested (`TreeTest.windDataIsInRangeAndFollowsTheHierarchy`): every channel in range, alpha 1, wood
without flutter, trunk vertices at `(h / height)^1.5` of their stem, main branches' pivots within 5 % of the height of
the axis, all 16 phases used, branch weights reaching 1. effects-vegetation's plant material reads this convention
(its `windColors` 1).

## Rocks

`Rocks.generate(RockParams, seed)`: a subdivided icosahedron whose every direction is pushed to the rock's surface,
so all levels sample one surface: an ellipsoid cut by random planes (cleavage: flat faces, sharp edges), lumped by
fractal noise, carved by ridged noise (crests and gullies, as weathering leaves), cracked along the borders of Worley
cells, banded by strata, and cut flat where buried. Presets `BOULDER`, `BLOCK`, `SLAB`, `SCREE`; four levels (20 · 4^n
triangles), each closed and wound outward, its error the largest gap between its edges and the surface. UV is a planar
projection from above in metres (rock materials usually map triplanar); wind is zero. More is #1.

## Tests

`./gradlew build` runs them all (with 80 % line and branch coverage, the platform-independence and allocation checks):

- **Determinism:** the same species, options and seed give bit-identical vertex bytes, indices, submeshes, billboard
  and impostor, after another tree in between, on another thread, through the facade; a different seed differs. Rocks
  alike.
- **Pipe model:** at every node of every preset, `r^e` equals its children's `r^e` plus its leaves' pipes to 1e-9; no
  node is thicker than its parent; the base has the species' radius.
- **Closed branches:** at every level of every preset, the bark submesh welded by position has no open or non-manifold
  edge (common-math `Meshes.openEdges`): every branch is a closed, consistently wound surface. Rocks: closed, outward.
- **Budgets:** every level within its triangle budget, fewer triangles and no smaller error than the level before, the
  far level under a quarter of the near one; a tight budget is met by dropping cards and twigs.
- **Wind:** ranges and hierarchy as above.
- **Seasons and snow:** deciduous trees bare in winter with the same wood; leaf UVs in the season's tile; snow lowers
  the leaves and leaves the trunk.
- **Bark UVs and frames:** v below 4.1 everywhere (float16 precision), unit normals, tangents across them.
- **Impostor hints:** the sphere holds the tree; all view directions unit, above the horizon; cameras at twice the
  radius.
- **Assets:** see above.

`TRUESIM_PICTURES=true ./gradlew :common-procedural:test --tests '*ProbeTest'` draws every species and rock at every
level on the CPU into `procedural/build/probe`, without a GPU.

## Benchmark

`ForestBenchmark` (`TRUESIM_BENCHMARK=true ./gradlew :common-procedural:test --tests '*ForestBenchmark'`): a forest of
1,000 distinct trees, an Alpine mix (spruce 40 %, larch 25 %, fir 15 %, pine 10 %, beech and birch 5 % each), each
with three levels, a billboard and impostor hints.

Measured on a 32-thread desktop CPU (JDK 27), after a warm-up:

| Species | per tree, one thread | LOD 0 triangles |
|---|---|---|
| spruce | 40.5 ms | 67,044 |
| fir | 36.0 ms | 67,295 |
| larch | 13.4 ms | 70,574 |
| pine | 54.6 ms | 74,059 |
| beech | 26.7 ms | 27,682 |
| birch | 11.8 ms | 14,302 |
| hazel | 5.8 ms | 7,834 |
| mugo | 3.3 ms | 22,482 |

**A forest of 1,000 trees: 25.3 s on one thread, 2.6 s on 32 threads** (a generator per thread), 64.6 million LOD 0
triangles in all. Through the asset path a tree is generated once per recipe and generator version, then read from the
generation cache.

## Preview: pictures and films through the engine

The `preview` module renders generated assets with the engine's renderer (a test-scope fixture, `Stage`), so the
library never depends on the engine. Its minimal tree material (`proceduralTree.slang`) shades as metallic-roughness
and sways by the wind format in `displace()` (the engine's per-material displacement), driven by its geometry hook
`procedural.wind`. Leaf atlases, bark and the billboard capture are drawn on the CPU (`Looks`) until procedural textures
(#5) exist. Until the engine releases per-material displacement, build it against that branch:

```bash
TRUESIM_SCREENSHOTS=true ./gradlew -Ptruesim.preview=true -Ptruesim.engine=../engine-vegetation :preview:test   # stills
TRUESIM_FILMS=true ./gradlew -Ptruesim.preview=true -Ptruesim.engine=../engine-vegetation :preview:test        # films
```

Films (1280 x 720, 30 frames a second): a turntable of every species, a small forest in rising wind, the levels of
detail side by side (spruce and beech: levels 0-2 and the billboard), the seasons (larch through the year, a spruce
under snow).

## Roadmap

Trees and rocks are in. Next, each an issue:

- [#1](https://github.com/true-sim/common-procedural/issues/1) Rocks: erosion-like detail beyond noise, clusters and
  scree fields
- [#2](https://github.com/true-sim/common-procedural/issues/2) Plants, flowers and grass clumps
- [#3](https://github.com/true-sim/common-procedural/issues/3) Alpine buildings: chalets with roofs and balconies
- [#4](https://github.com/true-sim/common-procedural/issues/4) Fences and paths
- [#5](https://github.com/true-sim/common-procedural/issues/5) Procedural textures: bark, rock and tileable materials
- [#6](https://github.com/true-sim/common-procedural/issues/6) Terrain detail
- [#7](https://github.com/true-sim/common-procedural/issues/7) Octahedral impostor baking

## Building

```bash
./gradlew build
```

Uses `../build-logic`, `../common-math`, `../common-jobs` and `../scene` automatically when checked out next to this
repository (override with `-Ptruesim.<repo>=<path>`, or `false`), or fetches them from GitHub Packages (see
build-logic's README for credentials).

## Licence

MIT, see [LICENSE](LICENSE).
