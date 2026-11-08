# common-procedural

Procedural assets for true-sim, generated deterministically from parameters and a seed: trees first, rocks, and more
to come ([roadmap](#roadmap)), and the [ecology](#ecology-which-plants-grow-where) that decides which plants grow where.
The generators are engine-agnostic (common-math only); a second module brings them into the scene's asset system as
recipe files.

| Module | Package | Contents |
|---|---|---|
| `common-procedural` | `com.truesim.common.procedural` | the generators, the direct Java API: trees, rocks, their meshes |
| `common-procedural-ecology` | `com.truesim.common.procedural.ecology` | which plants grow where: conditions, species' envelopes, competition, streaming tiles of fields and instances |
| `common-procedural-ecology-terrain` | `com.truesim.common.procedural.ecology.terrain` | the ecology over effects-terrain's surface and layers (optional) |
| `common-procedural-assets` | `com.truesim.common.procedural.assets` | recipe files (trees, rocks, ecologies) imported as scene meshes, models and recipes, generated in the background, cached |
| `preview` (not published) | | pictures and films through the engine, with a minimal tree material reading the wind format |

```kotlin
dependencies {
    implementation("com.truesim.common:common-procedural:0.1.0")          // tools, servers, tests: no engine
    implementation("com.truesim.common:common-procedural-assets:0.1.0")   // games: through the asset cache
    implementation("com.truesim.common:common-procedural-ecology:0.2.0")  // which plants grow where
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

## Ecology: which plants grow where

`common-procedural-ecology` decides what grows at every location of a terrain, from published Alpine ecology: the
conditions there (climate by altitude and aspect, hydrology, snow, wind exposure, substrate, land use), each species'
suitability, competition within and between layers, and the result as streaming tiles of fields and plant instances.
Engine-agnostic (common-procedural's `Seeds` and common-math only); the terrain comes in through `EcologyTerrain`, a
function of x and z.

```java
Ecology ecology = new Ecology(terrain, EcologyParameters.ALPS.withSeed(7)
    .withLandUse(EcologyParameters.LandUse.MEADOWS), AlpineSpecies.ALL);
EcologyTile tile = ecology.tile(3, -2);                  // 256 m: x 768 to 1024, z -512 to -256
Instances plants = tile.instances();                     // position, yaw, height and width scale, species, seed, age, health
float[] spruce = tile.abundance(tile.index("spruce"));   // 65 x 65 samples, every 4 m
float[] meadow = tile.cover(GroundCover.MEADOW);         // for the terrain's material
Site site = ecology.conditions(1000, -300);              // everything at one point
```

Over effects-terrain (optional module `common-procedural-ecology-terrain`, on `effects-terrain-data` only, no GPU):

```java
TerrainSurface surface = new TerrainSurface(site, surfaceParameters);           // effects-terrain's CPU surface
EcologyTerrain ground = TerrainEcology.of(surface, 2);                          // heightAt / normalAt's triangles
EcologyTerrain layered = TerrainEcology.of(surface, 2, terrain::layers);        // and its layers as substrate
```

The altitude is effects-terrain's `heightAt` plus the curvature's fall (the climate follows the altitude above sea
level), plants stand at `heightAt`; `layers` (rock, grass, scree, snow) become rock, soil, scree and snow, so
`vegetation` (the grass layer) is the soil plants root in. Without layers the ecology works the ground out itself.

### Conditions at a location (`Site`)

| Input | How | Source |
|---|---|---|
| **Temperature** | the growing season's mean: sea-level value minus 0.6 degrees per 100 m, plus the aspect's warming (`Climate`) | lapse rate: Rolland 2003 (0.54-0.58 per 100 m in the Alps); treeline at 6.4 degrees: Koerner and Paulsen 2004, Koerner 2012 |
| **Aspect, insolation** | clear-sky beam and diffuse sky on the slope over the sun's paths at the latitude, mid-April to mid-October, relative to level ground (`Insolation`); 2.5 degrees per unit, so a 25 degree south slope is 0.2 warmer and a north slope 0.6 colder than level ground | declination: Cooper 1969 |
| **Hydrology** | Priority-Flood filling (lakes from its depth), multiple flow directions for the topographic wetness index `ln(a / tan slope)`, single (steepest) directions for channels, height above the nearest drainage, the nearest stream and river (`TerrainAnalysis`) | Barnes, Lehman and Mulla 2014; Quinn et al. 1991; Beven and Kirkby 1979; O'Callaghan and Mark 1984; Renno et al. 2008 (HAND) |
| **Moisture** | Ellenberg's scale (1 dry to 12 in water): 5 at 600 m rising with altitude's precipitation, wetter with the wetness index and near drainage and under long snow, drier on sunny, exposed and stony ground | Ellenberg et al. 1991 |
| **Rivers, wetlands, riparian ground** | open water where a river's channel (2 km2 and up; 3 m wide per square-root square kilometre) or a filled hollow is; wetland where moisture reaches 8 to 9.2 on ground flatter than 4 degrees; a riparian band along streams, wider (8 m + 6 m per root km2) and higher above the channel (3 m + 0.1 per root km2) with the catchment | fens, mires, floodplains: Delarze et al. 2015 |
| **Snow** | days of cover a year from the temperature (Swiss stations: about 40 days in the lowlands, 165 at Davos, 255 at Weissfluhjoch, all year near 2900 m), fewer on exposed ridges, more in hollows; or the terrain's own (`snowDays`: effects-weather's) | Laternser and Schneebeli 2003 |
| **Wind exposure** | the topographic position index over 150 m, 40 m above the neighbourhood fully exposed | Weiss 2001 |
| **Substrate** | the terrain's layers, else rock on slopes from 38 to 52 degrees, scree on steep and on frost-shattered ground above the alpine meadows, snow where it lies all year | |
| **Land use** | optionally meadows kept open below 1850 m on slopes under 26 degrees, forest left in patches and on steep ground, orchards in them where apples grow | |
| **Belt** | colline, montane, subalpine, alpine, nival, bounded at 800, 1500, 2200 and 3000 m of level ground as temperatures, so aspect shifts them | Ozenda 1985; Ellenberg and Leuschner 2010 |

Every field is a pure function of the point: the analysis runs in world-aligned 2 km blocks with 2 km margins (8 m
samples), each block's fields bilinearly blended into its neighbours', so a tile's conditions never depend on which
tiles came before. A block takes about a second (its heights sampled on all cores); the 16 kept take about 265 MB.

### Species (`AlpineSpecies`)

34 species in six layers, each with an envelope (smooth trapezoids over temperature, moisture and snow days; tolerances
of wind exposure and substrate; a liking for riparian ground) and a habit (height, crown, competitive weight, groves
and clumps, krummholz), with their Ellenberg indicator values (light, temperature, moisture, reaction) and the source
of each envelope in the code:

| Layer | Species (ids) |
|---|---|
| canopy | `spruce` `fir` `larch` `stone-pine` `pine` `beech` `oak` `birch` `rowan` `apple` `ash` `alder` |
| shrubs | `willow` `green-alder` `hazel` `elder` `dog-rose` `mugo` |
| dwarf shrubs | `alpenrose` `blueberry` `lingonberry` |
| herbs | `gentian` `arnica` `alpine-aster` `edelweiss` `buttercup` `clover` `marsh-marigold` `sedge` `cotton-grass` `rush` |
| grass, ground | `grass`; `moss` (Sphagnum), `lichen` |

Temperature envelopes follow the belts (Ozenda 1985; Ellenberg and Leuschner 2010; Landolt 2003) through the default
climate (800 m is 14.2 degrees, 1500 m 10.0, 2200 m 5.8, 3000 m 1.0): oak and apple colline, beech colline-montane,
fir montane, spruce montane to the treeline (6.4 degrees, about 2100 m on level ground), larch and stone pine slightly
above it, mountain pine krummholz, alpenrose and the dwarf shrubs higher, gentian, alpine aster and edelweiss alpine,
lichens nival. Moisture ranges centre on the Ellenberg moisture value (Ellenberg et al. 1991), wide where a species is
indifferent; wetland species (sedges, cotton grass, marsh marigold, Sphagnum) take 8.5 to 10.5. The light value sets
the light a species needs under taller layers (about 2 % of daylight at 1, 10 % at 5, 50 % at 9). The indicator values
are Ellenberg's for central Europe as transcribed; for the Alpine specialists (mountain and stone pine, green alder,
alpenrose, gentian, alpine aster, edelweiss) they are approximate. Ids are common-procedural's tree presets and the
flora's (`rowan elder dog-rose apple blueberry lingonberry alpenrose gentian arnica alpine-aster edelweiss buttercup
clover grass`); the others have no generator yet and are placed all the same, for a renderer to draw them or a
stand-in.

### Communities, the treeline, placement

- **Suitability** multiplies a species' responses (temperature, moisture, snow, exposure, substrate, water, riparian).
- **Layers** fill tallest first: a layer's cover is its best species' suitability, shared among its species by weight
  times suitability cubed (the best suited dominate, ecotones mix) times a noise per species for groves and clumps;
  lower layers get the light the canopy's and the shrubs' cover leaves.
- **The treeline is an ecotone:** density falls as the temperature nears a tree's cold limit, size falls over a range a
  degree wider (so trees shrink before they thin out), stems thin with size, and trees able to (spruce, larch, stone
  pine) grow as krummholz, low and wide, when small; mountain pine and alpenrose take over above.
- **Placement** (instances for trees, shrubs, dwarf shrubs and herb clumps; grass and ground as fields): a jittered
  candidate per world cell of each layer (4, 2.5, 2 and 2.5 m), each candidate's random numbers derived from the seed and
  its cell, kept by its layer's cover, given a species by the abundances and a size from the size field. Then an
  iterated hard-core thinning (Matern's type II, a few rounds): candidates rank by layer, crown and a random priority,
  and one falls when a stronger one still standing is within reach, the radii of a layer added, a taller plant's crown
  counting only as far as the lower plant does not bear its shade. Every plant's fate depends only on candidates within
  a fixed halo, so a tile, a region, or any order of tiles gives exactly the same plants, and each plant belongs to
  exactly one tile.

### Outputs (`EcologyTile`)

- **Fields**, every `cell` metres with the tile's edges (neighbours share their edge samples exactly): per species
  abundance (its share of its layer's ground, 0 to 1: densities for grass fields), size and suitability; ground cover
  (`MEADOW`, `FOREST_FLOOR`, `HEATH`, `SCREE`, `ROCK`, `SNOW`, `WETLAND`, `WATER`, summing to 1) for effects-terrain's
  material; the conditions (altitude, temperature, moisture, snow days, insolation, exposure, wetness, HAND, water,
  wetland, riparian, meadow, canopy cover, slope, wet soil, belt): `WATER` and `WET_SOIL` are hints for a water
  renderer and darker, glossier ground.
- **Instances** (`Instances`): position (on the terrain's height), yaw, height and width scale relative to the
  species' grown plant (wider than high as krummholz), species, variant seed, age and health, for effects-vegetation to
  scatter and draw.

### As an asset

`.ecology` recipe files load through the asset manager like the trees' recipes (`EcologyAssets.install(paths)`, type
`EcologyAssets.ECOLOGY`), and give the same ecology as the direct API (tested):

```
# game://ecology/lauterbrunnen.ecology
seed = 7
latitude = 46.6
temperature = 19.0              # the growing season's mean at sea level
lapse = 0.006
meadows = true
species = spruce fir larch beech hazel mugo alpenrose blueberry gentian arnica grass
weight.beech = 1.4
model.spruce = game://forest/spruce-a.tree         # what each species' instances draw
model.gentian = game://flora/gentian.plant
```

`EcologyAssets.models(paths, recipe)` gives each species' model reference.

### Tested (each fails without its feature)

- **Belts on a synthetic slope, shifted by aspect:** up a 25 degree plane the belts change at their altitudes, beech
  and oak low, beech with fir and spruce montane, spruce subalpine, larch at the treeline, dwarf shrubs and krummholz
  above, alpine grassland with gentians, nothing on snow-covered nival ground; the south slope's treeline (cover below
  0.1) and alpine belt lie 80 to 400 m above the north slope's.
- **The treeline gradient:** the canopy's cover changes by less than 0.06 per 5 m from 1700 to 2400 m; trees placed in
  100 m bands from 1850 m get fewer and smaller band by band, the ecotone's band has trees of intermediate size, the
  last none, and some grow as krummholz.
- **Wetness index on a known valley:** V-shaped sides rising 0.4 to divides 1500 m out drain in parallel, so the
  index is `ln((1500 - x) / tan)`: matched within 0.15 from 200 to 1300 m; HAND is 0.4 x; the thalweg is a stream.
- **Wetlands and the riparian band:** in a meandering U-shaped valley the riparian band follows the channel (above 0.5
  beside it, under 0.1 150 m away on either side) and the river is open water; wetlands only where the ground is flatter
  than 4 degrees and moisture is 8 or more; sedges, cotton grass, marsh marigold in them, alder and willow by the
  river.
- **Spacing:** no two plants of a layer closer than their radii added, none under a taller crown it cannot bear, and
  shade-tolerant plants under the trees.
- **Streaming determinism:** four tiles in one order and in reverse on another ecology (a smaller block cache, so blocks
  are analysed again) give identical plants and fields; the four together hold exactly the plants of the region they
  cover; shared edge samples are equal.
- **Recipe asset path equals direct API:** an `.ecology` file through the real asset cache gives identical tiles.
- Also: ground cover sums to 1, meadows halve the forest on gentle slopes, insolation, ranges, climate, recipes' errors;
  effects-terrain's adapter (heights, layers) and, where its cache holds Lauterbrunnen, the Weisse Luetschine on the
  valley floor with riparian ground by it and the cliffs bare.

### Benchmark

BENCHMARK

### Pictures and films

FILMS

### What it does not do (yet)

- Shadows of the surrounding terrain on the insolation (a deep valley's floor gets less sun than its slope alone says).
- Avalanches (tracks keep green alder and grass where trees would stand), grazing beyond the meadows' rule, settlements.
- Hydrology across block margins: a catchment is cut 2 km beyond its block, so a big river's catchment, and so its
  width, is underestimated far from its source; flat valley floors drain in braids (the data's levees and ditches).
- Calcareous against siliceous ground (alpine aster and edelweiss on limestone, alpenrose on acid soils): no bedrock
  input yet.

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
build-logic's README for credentials). effects-terrain (for the ecology's adapter) is always fetched: its own build
includes the engine's. The Lauterbrunnen tests, benchmark and maps read effects-terrain's cache
(`-Ptruesim.terrain.cache=<dir>`, else `../effects-terrain/terrain-cache`, filled by its `fetchTerrain`) and are skipped
where it is empty.

## Licence

MIT, see [LICENSE](LICENSE).
