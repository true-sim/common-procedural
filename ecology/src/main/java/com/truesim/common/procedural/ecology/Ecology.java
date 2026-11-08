package com.truesim.common.procedural.ecology;

import com.truesim.common.math.Maths;
import com.truesim.common.math.random.Noise;
import com.truesim.common.procedural.Seeds;
import java.util.ArrayList;
import java.util.List;

/**
 * Which plants grow where on a terrain: the conditions at every location, each species' suitability, competition
 * within and between layers, and the result in tiles that stream: fields (abundance and size per species, ground cover,
 * the conditions) and plant instances.
 *
 * <pre>{@code
 * Ecology ecology = new Ecology(terrain, EcologyParameters.ALPS.withSeed(7), AlpineSpecies.ALL);
 * EcologyTile tile = ecology.tile(3, -2);                    // x 768 to 1024, z -512 to -256
 * Instances plants = tile.instances();                       // trees, shrubs, dwarf shrubs, herb clumps
 * float[] spruce = tile.abundance(tile.index("spruce"));
 * float[] meadow = tile.cover(GroundCover.MEADOW);
 * Site site = ecology.conditions(1000, -300);                // the conditions at one point
 * }</pre>
 *
 * <p><b>Conditions</b> ({@link #conditions}): the altitude and its neighbours a field's spacing away give slope and
 * aspect; the sun's paths give the slope's relative insolation ({@link Insolation}); the climate gives the growing
 * season's temperature (lapse rate, aspect) and snow cover; the terrain analysis gives the wetness index, height above
 * drainage, the stream it drains to and the topographic position (wind exposure). From them: open water (rivers wider
 * with their catchment, filled hollows as lakes), moisture on Ellenberg's scale (altitude's precipitation, wetness,
 * nearness to drainage, sunshine, exposure, stony ground, snowmelt), wetlands (saturated and flat), riparian ground
 * (a band along streams, wider and higher with the catchment), the ground (the terrain's layers, or rock on cliffs,
 * scree on steep and frost-shattered ground, snow lying all year), meadows kept open by land use, and the belt.
 *
 * <p><b>Communities:</b> a species' suitability multiplies its responses to temperature, moisture, snow, exposure,
 * substrate, water and riparian ground. Layers are filled tallest first: a layer's cover is its best species'
 * suitability, shared among its species by weight times suitability to the power {@code sharpness} (the best suited
 * dominate, ecotones mix), times a noise per species for groves and clumps; lower layers lose light under the canopy's
 * and the shrubs' cover by their light values. Trees and shrubs shrink toward their cold limit (size falls over a
 * wider range of temperature than density, so the treeline is an ecotone, not a line) and, where able, become
 * krummholz: low and wide.
 *
 * <p><b>Placement:</b> each layer has a jittered candidate grid in world cells, each candidate's random numbers derived
 * from the seed and its cell; a candidate is kept by its layer's cover, takes a species by the abundances and a size
 * from the size field. Then a local hard-core thinning (Matern's type II, iterated): candidates are ordered by layer,
 * crown and a random priority, and a candidate is dropped when a stronger one stands within their crowns' reach (a
 * taller layer's crown reaching only as far as the lower plant does not bear its shade); a few rounds approach greedy
 * thinning while every decision depends only on candidates within a fixed distance. So a tile computes the same
 * plants as any other tile or region covering them: tiles stream in any order, and their seams match exactly.
 *
 * <p>Thread-safe; the terrain analysis is shared and cached.
 */
public final class Ecology {

    private static final long LAYER_SALT = 0x5eed_1a7e_0000L;

    private final EcologyTerrain terrain;
    private final EcologyParameters parameters;
    private final List<Species> species;
    private final Insolation insolation;
    private final TerrainAnalysis analysis;
    private final Noise[] clusters;
    private final Noise patches;
    private final int[][] layers;
    private final double[] maxRadius;
    private final double[] shade;
    private final double[] need;
    private final int apple;
    private final double halo;

    /** An ecology of {@code species} on {@code terrain}. */
    public Ecology(EcologyTerrain terrain, EcologyParameters parameters, List<Species> species) {
        this.terrain = terrain;
        this.parameters = parameters;
        this.species = List.copyOf(species);
        this.insolation = new Insolation(parameters.climate().latitude());
        this.analysis = new TerrainAnalysis(terrain, parameters.analysis(), parameters.hydrology());
        int n = this.species.size();
        clusters = new Noise[n];
        shade = new double[n];
        need = new double[n];
        int appleIndex = -1;
        for (int s = 0; s < n; s++) {
            Species sp = this.species.get(s);
            clusters[s] = Noise.seeded(Seeds.derive(parameters.seed(), 7000 + s));
            shade[s] = sp.shadeTolerance();
            need[s] = sp.lightNeed();
            if (sp.id().equals("apple")) {
                appleIndex = s;
            }
        }
        apple = appleIndex;
        patches = Noise.seeded(Seeds.derive(parameters.seed(), 6999));
        layers = new int[Layer.ALL.length][];
        maxRadius = new double[Layer.ALL.length];
        for (Layer layer : Layer.ALL) {
            List<Integer> members = new ArrayList<>();
            for (int s = 0; s < n; s++) {
                if (this.species.get(s).layer() == layer) {
                    members.add(s);
                    maxRadius[layer.ordinal()] = Math.max(maxRadius[layer.ordinal()], this.species.get(s).habit()
                        .crown() * MAX_SIZE * parameters.placement().layers().get(layer.ordinal()).spacing());
                }
            }
            layers[layer.ordinal()] = members.stream().mapToInt(Integer::intValue).toArray();
        }
        double conflict = 0;
        for (Layer layer : Layer.ALL) {
            if (parameters.placement().layers().get(layer.ordinal()).instances()) {
                conflict = Math.max(conflict, 2 * maxRadius[layer.ordinal()]);
            }
        }
        halo = (parameters.placement().iterations() + 1) * conflict;
    }

    /** The largest size a plant takes relative to a grown plant (old plants on the best sites). */
    static final double MAX_SIZE = 1.2;

    public EcologyTerrain terrain() {
        return terrain;
    }

    public EcologyParameters parameters() {
        return parameters;
    }

    /** The species, in the order instances and fields index them. */
    public List<Species> species() {
        return species;
    }

    /** The index of species {@code id}, or -1. */
    public int index(String id) {
        for (int s = 0; s < species.size(); s++) {
            if (species.get(s).id().equals(id)) {
                return s;
            }
        }
        return -1;
    }

    /**
     * How far beyond a region the placement looks, metres: every plant's fate depends only on candidates this close.
     */
    public double halo() {
        return halo;
    }

    /** Terrain-analysis blocks analysed so far. */
    public long analysedBlocks() {
        return analysis.analysed();
    }

    /** Nanoseconds spent analysing the terrain so far. */
    public long analysisNanos() {
        return analysis.nanos();
    }

    // ------------------------------------------------------------------------------------------------ conditions

    /** The conditions at ({@code x}, {@code z}). */
    public Site conditions(double x, double z) {
        Site site = new Site();
        conditions(x, z, site);
        return site;
    }

    /** The conditions at ({@code x}, {@code z}) into {@code site}. */
    public void conditions(double x, double z, Site site) {
        double c = parameters.cell();
        evaluate(x, z, terrain.altitude(x, z), terrain.altitude(x - c, z), terrain.altitude(x + c, z),
            terrain.altitude(x, z - c), terrain.altitude(x, z + c), new double[TerrainAnalysis.FIELDS], site);
    }

    /** The conditions from the altitude here and a field's spacing west, east, north and south. */
    private void evaluate(double x, double z, double h, double west, double east, double north, double south,
        double[] scratch, Site site) {
        EcologyParameters.Hydrology hy = parameters.hydrology();
        EcologyParameters.Analysis an = parameters.analysis();
        Climate climate = parameters.climate();
        double c = parameters.cell();
        site.x = x;
        site.z = z;
        site.altitude = h;
        double gx = (east - west) / (2 * c);
        double gz = (south - north) / (2 * c);
        double gradient = Math.sqrt(gx * gx + gz * gz);
        site.slope = Maths.toDegrees(Maths.atan(gradient));
        // the downhill direction's azimuth: east -gx, north +gz (z is south)
        double aspect = gradient > 0 ? Maths.toDegrees(Maths.atan2(-gx, gz)) : 0;
        site.aspect = aspect < 0 ? aspect + 360 : aspect;
        site.insolation = gradient > 0 ? insolation.relative(site.slope, site.aspect) : 1;
        site.temperature = climate.temperature(h, site.insolation);
        analysis.sample(x, z, scratch);
        site.position = scratch[TerrainAnalysis.POSITION];
        site.exposure = Range.smoothstep(0, an.exposedPosition(), site.position);
        site.wetness = scratch[TerrainAnalysis.WETNESS];
        site.hand = scratch[TerrainAnalysis.HAND];
        site.streamDistance = scratch[TerrainAnalysis.DISTANCE];
        site.streamArea = scratch[TerrainAnalysis.STREAM_AREA];
        site.streamHeight = scratch[TerrainAnalysis.STREAM_HEIGHT];
        site.riverDistance = scratch[TerrainAnalysis.RIVER_DISTANCE];
        site.riverArea = scratch[TerrainAnalysis.RIVER_AREA];
        // snow
        double snow = terrain.snowDays(x, z);
        if (Double.isNaN(snow)) {
            snow = climate.snowDays(site.temperature) * (1 - 0.3 * site.exposure)
                + 25 * Range.smoothstep(0, -an.exposedPosition(), site.position);
        }
        site.snowDays = Math.clamp(snow, 0, 365);
        // ground
        double[] g = site.ground;
        if (!terrain.ground(x, z, g)) {
            double rock = Range.smoothstep(38, 52, site.slope);
            double scree = Math.max(0.6 * Range.smoothstep(30, 40, site.slope),
                Range.smoothstep(2.5, 0, site.temperature));
            double lying = Range.smoothstep(335, 362, site.snowDays) * (1 - Range.smoothstep(30, 45, site.slope));
            double rest = 1 - lying;
            g[Ground.SNOW] = lying;
            g[Ground.ROCK] = rest * rock;
            g[Ground.SCREE] = rest * (1 - rock) * scree;
            g[Ground.SOIL] = rest * (1 - rock) * (1 - scree);
        }
        // open water: rivers by their catchment, lakes in filled hollows
        double water = 0;
        if (site.riverArea >= hy.riverArea()) {
            double half = 0.5 * hy.riverWidth() * Math.sqrt(site.riverArea / 1e6);
            water = 1 - Range.smoothstep(half - 0.5 * an.cell(), half + 0.5 * an.cell(), site.riverDistance);
        }
        double lake = Range.smoothstep(hy.lakeDepth(), 2 * hy.lakeDepth(), scratch[TerrainAnalysis.FILL])
            * (1 - Range.smoothstep(2, 4, site.slope));
        site.water = Math.max(water, lake);
        // moisture on Ellenberg's scale
        double rocky = g[Ground.ROCK] + g[Ground.SCREE];
        double moisture = Math.clamp(5 + 0.0007 * (h - 600), 4, 7)
            + Math.clamp(0.45 * (site.wetness - hy.wetnessReference()), -1.5, 2.5)
            + 1.5 * (1 - Range.smoothstep(0.5, 3, site.hand))
            - 2 * (site.insolation - 1) - site.exposure - 1.5 * rocky
            + 0.006 * Math.max(0, site.snowDays - 220);
        site.wetland = Range.smoothstep(8, 9.2, moisture)
            * (1 - Range.smoothstep(0.5 * hy.wetlandSlope(), hy.wetlandSlope(), site.slope)) * g[Ground.SOIL]
            * (1 - site.water);
        moisture += 1.5 * site.wetland;
        site.moisture = moisture * (1 - site.water) + 11 * site.water;
        // riparian ground: a band along streams and rivers, wider and higher with the catchment
        site.riparian = Math.max(band(hy, site.streamArea, site.streamDistance, site.streamHeight),
            band(hy, site.riverArea, site.riverDistance, site.streamHeight)) * (1 - site.water);
        // land kept open
        EcologyParameters.LandUse use = parameters.landUse();
        if (use.meadows()) {
            double patch = patches.simplex(x / use.patches(), z / use.patches());
            site.meadow = (1 - Range.smoothstep(use.maxSlope() - 8, use.maxSlope(), site.slope))
                * (1 - Range.smoothstep(use.maxAltitude() - 150, use.maxAltitude() + 150, h))
                * Range.smoothstep(-0.65, -0.45, patch) * (1 - site.water) * g[Ground.SOIL];
        } else {
            site.meadow = 0;
        }
        site.belt = climate.belt(site.temperature);
    }

    /** The riparian band of a stream of catchment {@code area} at {@code distance} and {@code height} above it. */
    private static double band(EcologyParameters.Hydrology hy, double area, double distance, double height) {
        if (area < hy.streamArea()) {
            return 0;
        }
        double root = Math.sqrt(area / 1e6);
        double halfWidth = hy.riparianBase() + hy.riparianWidth() * root;
        double top = hy.riparianHeight() + 0.1 * root;
        return (1 - Range.smoothstep(0.6 * halfWidth, halfWidth, distance))
            * (1 - Range.smoothstep(0.5 * top, top, height));
    }

    // ------------------------------------------------------------------------------------------------ suitability

    /** How suited {@code site} is to each species alone, 0 to 1, into {@code out}. */
    public void suitability(Site site, double[] out) {
        for (int s = 0; s < species.size(); s++) {
            out[s] = suitability(species.get(s).envelope(), site);
        }
    }

    /**
     * The communities at ({@code x}, {@code z}), as a tile's fields have them at a sample: each species' abundance and
     * size into {@code abundance} and {@code size}; returns the canopy's cover.
     */
    public double community(double x, double z, double[] abundance, double[] size) {
        Site site = conditions(x, z);
        int n = species.size();
        double[] suit = new double[n];
        double[] covers = new double[4];
        suitability(site, suit);
        community(site, suit, abundance, size, new double[n], covers);
        return covers[0];
    }

    private static double suitability(Species.Envelope e, Site site) {
        double v = e.temperature().response(site.temperature);
        if (v == 0) {
            return 0;
        }
        v *= e.moisture().response(site.moisture) * e.snowDays().response(site.snowDays);
        v *= 1 - Range.smoothstep(e.exposure(), e.exposure() + 0.35, site.exposure);
        double[] g = site.ground;
        v *= Math.min(1, g[Ground.SOIL] * e.soil() + g[Ground.SCREE] * e.scree() + g[Ground.ROCK] * e.rock());
        v *= 1 - site.water;
        if (e.riparian() < 0) {
            v *= 1 + 0.8 * e.riparian() * site.riparian;
        }
        return v;
    }

    /**
     * The communities at a site: from the species' suitabilities {@code suit}, their abundances and sizes into
     * {@code abundance} and {@code size}; returns the canopy's cover, the heath's in {@code covers[1]}, the grass'
     * in {@code covers[2]}, the shrubs' in {@code covers[3]}.
     */
    private void community(Site site, double[] suit, double[] abundance, double[] size, double[] share,
        double[] covers) {
        double k = parameters.sharpness();
        double canopy = 0;
        double shrubs = 0;
        for (Layer layer : Layer.ALL) {
            int[] members = layers[layer.ordinal()];
            double best = 0;
            double total = 0;
            for (int s : members) {
                Species sp = species.get(s);
                double v = suit[s];
                if (v <= 0) {
                    share[s] = 0;
                    abundance[s] = 0;
                    size[s] = 0;
                    continue;
                }
                if (layer != Layer.CANOPY) {
                    // the light reaching the layer: through the canopy, then the shrubs
                    double light = (1 - 0.97 * Maths.pow(canopy, 0.4))
                        * (layer == Layer.SHRUB ? 1 : 1 - 0.7 * shrubs);
                    v *= Range.smoothstep(0.4 * need[s], 1.2 * need[s], light);
                }
                double weight = sp.habit().weight() * (1 + 2 * Math.max(0, sp.envelope().riparian()) * site.riparian);
                if (site.meadow > 0 && (layer == Layer.CANOPY || layer == Layer.SHRUB)) {
                    if (s == apple) {
                        v *= 1 - 0.82 * site.meadow;
                        weight *= 1 + 30 * site.meadow;
                    } else {
                        v *= 1 - 0.95 * site.meadow;
                    }
                }
                Species.Habit habit = sp.habit();
                if (habit.cluster() > 0 && habit.clumping() > 0) {
                    weight *= Maths.exp(1.6 * habit.clumping()
                        * clusters[s].simplex(site.x / habit.cluster(), site.z / habit.cluster()));
                }
                best = Math.max(best, v);
                share[s] = weight * Maths.pow(v, k);
                total += share[s];
                // size: trees and shrubs shrink toward their cold limit over a wider range than they thin
                Range t = sp.envelope().temperature();
                double rest = suit[s] / Math.max(t.response(site.temperature), 1e-9);
                if (layer == Layer.CANOPY || layer == Layer.SHRUB) {
                    double ramp = Range.smoothstep(t.min(), t.low() + 1.0, site.temperature);
                    size[s] = (0.12 + 0.88 * ramp) * (0.65 + 0.35 * Math.min(1, rest));
                } else {
                    size[s] = 0.6 + 0.4 * v;
                }
            }
            double cover = 0;
            for (int s : members) {
                abundance[s] = total > 0 ? best * share[s] / total : 0;
                cover += abundance[s] * Math.min(1, size[s]);
            }
            if (layer == Layer.CANOPY) {
                canopy = cover;
            } else if (layer == Layer.SHRUB) {
                shrubs = cover;
                covers[3] = cover;
            } else if (layer == Layer.DWARF_SHRUB) {
                covers[1] = best;
            } else if (layer == Layer.GRASS) {
                covers[2] = best;
            }
        }
        covers[0] = canopy;
    }

    // ------------------------------------------------------------------------------------------------ tiles

    /** Tile ({@code tx}, {@code tz}): x from {@code tx * tileSize}, z from {@code tz * tileSize}. */
    public EcologyTile tile(long tx, long tz) {
        double t = parameters.tileSize();
        return region(tx * t, tz * t, t, t);
    }

    /**
     * Any world-aligned region (its corner and sides whole numbers of cells): the same fields and plants as the tiles
     * covering it.
     */
    public EcologyTile region(double x0, double z0, double width, double depth) {
        double c = parameters.cell();
        long i0 = Math.round(x0 / c);
        long j0 = Math.round(z0 / c);
        int nx = (int) Math.round(width / c);
        int nz = (int) Math.round(depth / c);
        if (Math.abs(i0 * c - x0) > 1e-9 * Math.max(1, Math.abs(x0)) || Math.abs(j0 * c - z0) > 1e-9
            * Math.max(1, Math.abs(z0)) || Math.abs(nx * c - width) > 1e-9 || Math.abs(nz * c - depth) > 1e-9) {
            throw new IllegalArgumentException("A region's corner and sides are whole numbers of cells");
        }
        int pad = (int) Math.ceil(halo / c) + 2;
        Grid grid = new Grid(i0 - pad, j0 - pad, nx + 1 + 2 * pad, nz + 1 + 2 * pad, c, species.size());
        fill(grid);
        int samples = Math.max(nx, nz) + 1;
        Instances instances = new Instances();
        place(grid, x0, z0, x0 + nx * c, z0 + nz * c, instances);
        EcologyTile tile = new EcologyTile(i0 * c, j0 * c, c, samples, species, instances);
        if (nx != nz) {
            // non-square regions keep a square sample grid of the larger side; the rest stays zero
            crop(grid, pad, nx + 1, nz + 1, samples, tile);
        } else {
            crop(grid, pad, samples, samples, samples, tile);
        }
        return tile;
    }

    /** The fields over a grid with its halo: conditions, suitabilities, communities, ground cover. */
    private void fill(Grid grid) {
        int w = grid.width;
        int d = grid.depth;
        double c = grid.cell;
        // altitudes with a ring for the slopes
        double[] h = new double[(w + 2) * (d + 2)];
        for (int j = 0; j < d + 2; j++) {
            double z = (grid.j0 - 1 + j) * c;
            for (int i = 0; i < w + 2; i++) {
                h[j * (w + 2) + i] = terrain.altitude((grid.i0 - 1 + i) * c, z);
            }
        }
        Site site = new Site();
        int n = species.size();
        double[] scratch = new double[TerrainAnalysis.FIELDS];
        double[] suit = new double[n];
        double[] abundance = new double[n];
        double[] size = new double[n];
        double[] share = new double[n];
        double[] covers = new double[4];
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int a = (j + 1) * (w + 2) + i + 1;
                double x = (grid.i0 + i) * c;
                double z = (grid.j0 + j) * c;
                evaluate(x, z, h[a], h[a - 1], h[a + 1], h[a - (w + 2)], h[a + (w + 2)], scratch, site);
                suitability(site, suit);
                community(site, suit, abundance, size, share, covers);
                int g = j * w + i;
                for (int s = 0; s < n; s++) {
                    grid.abundance[s][g] = (float) abundance[s];
                    grid.size[s][g] = (float) size[s];
                    grid.suitability[s][g] = (float) suit[s];
                }
                store(grid, g, site, covers);
            }
        }
    }

    private static void store(Grid grid, int g, Site site, double[] covers) {
        float[][] f = grid.fields;
        f[EcologyTile.Field.ALTITUDE.ordinal()][g] = (float) site.altitude;
        f[EcologyTile.Field.TEMPERATURE.ordinal()][g] = (float) site.temperature;
        f[EcologyTile.Field.MOISTURE.ordinal()][g] = (float) site.moisture;
        f[EcologyTile.Field.SNOW_DAYS.ordinal()][g] = (float) site.snowDays;
        f[EcologyTile.Field.INSOLATION.ordinal()][g] = (float) site.insolation;
        f[EcologyTile.Field.EXPOSURE.ordinal()][g] = (float) site.exposure;
        f[EcologyTile.Field.WETNESS.ordinal()][g] = (float) site.wetness;
        f[EcologyTile.Field.HAND.ordinal()][g] = (float) site.hand;
        f[EcologyTile.Field.WATER.ordinal()][g] = (float) site.water;
        f[EcologyTile.Field.WETLAND.ordinal()][g] = (float) site.wetland;
        f[EcologyTile.Field.RIPARIAN.ordinal()][g] = (float) site.riparian;
        f[EcologyTile.Field.MEADOW.ordinal()][g] = (float) site.meadow;
        f[EcologyTile.Field.CANOPY.ordinal()][g] = (float) covers[0];
        f[EcologyTile.Field.SLOPE.ordinal()][g] = (float) site.slope;
        f[EcologyTile.Field.WET_SOIL.ordinal()][g] = (float) Math.max(Math.max(site.wetland, 0.6 * site.riparian),
            Range.smoothstep(7, 9, site.moisture) * (1 - site.water));
        f[EcologyTile.Field.BELT.ordinal()][g] = site.belt.ordinal();
        // ground cover
        double[] ground = site.ground;
        double water = site.water;
        double soil = ground[Ground.SOIL] * (1 - water);
        double wet = soil * site.wetland;
        double dry = soil - wet;
        double forest = dry * Math.min(1, 1.2 * covers[0]);
        double heath = (dry - forest) * Math.min(1, covers[1]);
        double open = dry - forest - heath;
        double meadow = open * Math.min(1, 1.5 * covers[2]);
        float[][] cover = grid.cover;
        cover[GroundCover.MEADOW.ordinal()][g] = (float) meadow;
        cover[GroundCover.FOREST_FLOOR.ordinal()][g] = (float) forest;
        cover[GroundCover.HEATH.ordinal()][g] = (float) heath;
        cover[GroundCover.SCREE.ordinal()][g] = (float) (ground[Ground.SCREE] * (1 - water) + open - meadow);
        cover[GroundCover.ROCK.ordinal()][g] = (float) (ground[Ground.ROCK] * (1 - water));
        cover[GroundCover.SNOW.ordinal()][g] = (float) (ground[Ground.SNOW] * (1 - water));
        cover[GroundCover.WETLAND.ordinal()][g] = (float) wet;
        cover[GroundCover.WATER.ordinal()][g] = (float) water;
    }

    private void crop(Grid grid, int pad, int width, int depth, int samples, EcologyTile tile) {
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                int g = (j + pad) * grid.width + i + pad;
                int t = j * samples + i;
                for (int s = 0; s < species.size(); s++) {
                    tile.abundance[s][t] = grid.abundance[s][g];
                    tile.size[s][t] = grid.size[s][g];
                    tile.suitability[s][t] = grid.suitability[s][g];
                }
                for (int f = 0; f < tile.fields.length; f++) {
                    tile.fields[f][t] = grid.fields[f][g];
                }
                for (int k = 0; k < tile.cover.length; k++) {
                    tile.cover[k][t] = grid.cover[k][g];
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ placement

    /** Places the instance layers' plants of [x0, x1) x [z0, z1) into {@code out}. */
    private void place(Grid grid, double x0, double z0, double x1, double z1, Instances out) {
        Candidates all = new Candidates();
        double[] weights = new double[4];
        int[] corner = new int[1];
        for (Layer layer : Layer.ALL) {
            EcologyParameters.LayerPlacement lp = parameters.placement().layers().get(layer.ordinal());
            int[] members = layers[layer.ordinal()];
            if (!lp.instances() || members.length == 0) {
                continue;
            }
            double g = lp.cell();
            long ci0 = (long) Math.floor((x0 - halo) / g);
            long ci1 = (long) Math.floor((x1 + halo) / g);
            long cj0 = (long) Math.floor((z0 - halo) / g);
            long cj1 = (long) Math.floor((z1 + halo) / g);
            long layerSeed = Seeds.derive(parameters.seed(), LAYER_SALT + layer.ordinal());
            for (long cj = cj0; cj <= cj1; cj++) {
                for (long ci = ci0; ci <= ci1; ci++) {
                    long hash = Seeds.derive(layerSeed, ci * 0x1_0000_0001L ^ cj * 0x7FFF_FFFFL);
                    double px = (ci + unit(hash, 1)) * g;
                    double pz = (cj + unit(hash, 2)) * g;
                    if (px < x0 - halo || px >= x1 + halo || pz < z0 - halo || pz >= z1 + halo) {
                        continue;
                    }
                    grid.weights(px, pz, weights, corner);
                    double cover = 0;
                    for (int s : members) {
                        cover += grid.sample(grid.abundance[s], weights, corner[0]);
                    }
                    double accept = unit(hash, 3);
                    if (accept >= cover * lp.fill()) {
                        continue;
                    }
                    double target = unit(hash, 4) * cover;
                    int chosen = members[members.length - 1];
                    double sum = 0;
                    for (int s : members) {
                        sum += grid.sample(grid.abundance[s], weights, corner[0]);
                        if (target < sum) {
                            chosen = s;
                            break;
                        }
                    }
                    Species sp = species.get(chosen);
                    double sizeField = grid.sample(grid.size[chosen], weights, corner[0]);
                    if (layer == Layer.CANOPY || layer == Layer.SHRUB) {
                        // stunted stands are sparser too: fewer stems where trees stay small
                        double stems = Maths.pow(Math.min(1, sizeField), 1.5);
                        if (accept >= cover * lp.fill() * stems) {
                            continue;
                        }
                    }
                    double ageRandom = unit(hash, 5);
                    double grown = Math.clamp(sizeField * (0.8 + 0.4 * ageRandom), 0.05, MAX_SIZE);
                    double form = sp.habit().krummholz() ? 1 - Range.smoothstep(0.25, 0.55, sizeField) : 0;
                    double widthScale = Math.min(MAX_SIZE, grown * (1 + 1.5 * form));
                    double radius = sp.habit().crown() * widthScale * lp.spacing();
                    double health = grid.sample(grid.suitability[chosen], weights, corner[0]);
                    all.add(px, pz, radius, unit(hash, 6), layer.ordinal(), chosen, grown, widthScale,
                        unit(hash, 7) * Maths.TAU, ageRandom, health, hash);
                }
            }
        }
        boolean[] kept = thin(all);
        for (int k = 0; k < all.count; k++) {
            if (kept[k] && all.x[k] >= x0 && all.x[k] < x1 && all.z[k] >= z0 && all.z[k] < z1) {
                out.add(all.x[k], terrain.height(all.x[k], all.z[k]), all.z[k], all.yaw[k], all.size[k],
                    all.width[k], all.species[k], all.variant[k], all.age[k], all.health[k]);
            }
        }
    }

    /** A uniform number in [0, 1) from {@code hash}'s stream {@code k}. */
    private static double unit(long hash, int k) {
        return (Seeds.mix(hash + 0x9E3779B97F4A7C15L * k) >>> 11) * 0x1.0p-53;
    }

    /**
     * The iterated hard-core thinning: which candidates stay. A candidate is dominated by those of taller layers, of
     * larger crowns in its layer, and then of higher priority; it falls when a dominating candidate still standing is
     * within reach.
     */
    private boolean[] thin(Candidates all) {
        int n = all.count;
        // buckets per layer, cells as large as the layer's widest reach
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            minX = Math.min(minX, all.x[k]);
            minZ = Math.min(minZ, all.z[k]);
            maxX = Math.max(maxX, all.x[k]);
            maxZ = Math.max(maxZ, all.z[k]);
        }
        int layerCount = Layer.ALL.length;
        Buckets[] buckets = new Buckets[layerCount];
        for (int l = 0; l < layerCount; l++) {
            double size = Math.max(2 * maxRadius[l], 0.5);
            buckets[l] = new Buckets(minX, minZ, maxX, maxZ, size, n);
        }
        for (int k = 0; k < n; k++) {
            buckets[all.layer[k]].add(k, all.x[k], all.z[k]);
        }
        // the dominators within reach of each candidate
        int[] start = new int[n + 1];
        IntList dominators = new IntList();
        for (int k = 0; k < n; k++) {
            start[k] = dominators.size;
            double x = all.x[k];
            double z = all.z[k];
            for (int l = 0; l <= all.layer[k]; l++) {
                double reach = all.radius[k] + maxRadius[l];
                Buckets b = buckets[l];
                int bi0 = b.column(x - reach);
                int bi1 = b.column(x + reach);
                int bj0 = b.row(z - reach);
                int bj1 = b.row(z + reach);
                for (int bj = bj0; bj <= bj1; bj++) {
                    for (int bi = bi0; bi <= bi1; bi++) {
                        for (int o = b.head[bj * b.columns + bi]; o >= 0; o = b.next[o]) {
                            if (o == k || !dominates(all, o, k)) {
                                continue;
                            }
                            double dx = all.x[o] - x;
                            double dz = all.z[o] - z;
                            double distance = distance(all, o, k);
                            if (dx * dx + dz * dz < distance * distance) {
                                dominators.add(o);
                            }
                        }
                    }
                }
            }
        }
        start[n] = dominators.size;
        boolean[] kept = new boolean[n];
        boolean[] next = new boolean[n];
        java.util.Arrays.fill(kept, true);
        int rounds = parameters.placement().iterations() + 1;
        for (int round = 0; round < rounds; round++) {
            for (int k = 0; k < n; k++) {
                boolean stays = true;
                for (int e = start[k]; e < start[k + 1]; e++) {
                    if (kept[dominators.values[e]]) {
                        stays = false;
                        break;
                    }
                }
                next[k] = stays;
            }
            if (round == rounds - 1) {
                // the last round only removes: a candidate stands if it stood and nothing standing dominates it
                for (int k = 0; k < n; k++) {
                    next[k] &= kept[k];
                }
            }
            boolean[] swap = kept;
            kept = next;
            next = swap;
        }
        return kept;
    }

    /** Whether candidate {@code a} dominates {@code b}. */
    private static boolean dominates(Candidates all, int a, int b) {
        if (all.layer[a] != all.layer[b]) {
            return all.layer[a] < all.layer[b];
        }
        if (all.radius[a] != all.radius[b]) {
            return all.radius[a] > all.radius[b];
        }
        if (all.priority[a] != all.priority[b]) {
            return all.priority[a] > all.priority[b];
        }
        return Long.compareUnsigned(all.variant[a], all.variant[b]) > 0;
    }

    /**
     * How close dominating candidate {@code a} may stand to {@code b}: their radii added in a layer; from a taller
     * layer, the lower plant's radius plus as much of the crown's as it cannot bear the shade of (a stem's worth at
     * least).
     */
    private double distance(Candidates all, int a, int b) {
        if (all.layer[a] == all.layer[b]) {
            return all.radius[a] + all.radius[b];
        }
        return all.radius[b] + all.radius[a] * Math.max(0.15, 1 - shade[all.species[b]]);
    }

    /** The fields over a region and its halo, at world-aligned samples. */
    private static final class Grid {
        final long i0;
        final long j0;
        final int width;
        final int depth;
        final double cell;
        final float[][] abundance;
        final float[][] size;
        final float[][] suitability;
        final float[][] fields;
        final float[][] cover;

        Grid(long i0, long j0, int width, int depth, double cell, int species) {
            this.i0 = i0;
            this.j0 = j0;
            this.width = width;
            this.depth = depth;
            this.cell = cell;
            int n = width * depth;
            abundance = new float[species][n];
            size = new float[species][n];
            suitability = new float[species][n];
            fields = new float[EcologyTile.Field.ALL.length][n];
            cover = new float[GroundCover.ALL.length][n];
        }

        /**
         * The bilinear weights of the samples round ({@code x}, {@code z}) into {@code weights} and their first
         * sample's index into {@code corner}: from the global sample index, so every region interpolates alike.
         */
        void weights(double x, double z, double[] weights, int[] corner) {
            double gx = x / cell;
            double gz = z / cell;
            long gi = (long) Math.floor(gx);
            long gj = (long) Math.floor(gz);
            double fx = gx - gi;
            double fz = gz - gj;
            corner[0] = (int) (gj - j0) * width + (int) (gi - i0);
            weights[0] = (1 - fx) * (1 - fz);
            weights[1] = fx * (1 - fz);
            weights[2] = (1 - fx) * fz;
            weights[3] = fx * fz;
        }

        double sample(float[] field, double[] weights, int a) {
            return weights[0] * field[a] + weights[1] * field[a + 1] + weights[2] * field[a + width]
                + weights[3] * field[a + width + 1];
        }
    }

    /** Placement candidates, as parallel arrays. */
    private static final class Candidates {
        int count;
        double[] x = new double[1024];
        double[] z = new double[1024];
        double[] radius = new double[1024];
        double[] priority = new double[1024];
        int[] layer = new int[1024];
        int[] species = new int[1024];
        double[] size = new double[1024];
        double[] width = new double[1024];
        double[] yaw = new double[1024];
        double[] age = new double[1024];
        double[] health = new double[1024];
        long[] variant = new long[1024];

        void add(double px, double pz, double r, double p, int l, int s, double grown, double w, double turn,
            double a, double h, long v) {
            if (count == x.length) {
                int n = 2 * count;
                x = java.util.Arrays.copyOf(x, n);
                z = java.util.Arrays.copyOf(z, n);
                radius = java.util.Arrays.copyOf(radius, n);
                priority = java.util.Arrays.copyOf(priority, n);
                layer = java.util.Arrays.copyOf(layer, n);
                species = java.util.Arrays.copyOf(species, n);
                size = java.util.Arrays.copyOf(size, n);
                width = java.util.Arrays.copyOf(width, n);
                yaw = java.util.Arrays.copyOf(yaw, n);
                age = java.util.Arrays.copyOf(age, n);
                health = java.util.Arrays.copyOf(health, n);
                variant = java.util.Arrays.copyOf(variant, n);
            }
            x[count] = px;
            z[count] = pz;
            radius[count] = r;
            priority[count] = p;
            layer[count] = l;
            species[count] = s;
            size[count] = grown;
            width[count] = w;
            yaw[count] = turn;
            age[count] = a;
            health[count] = h;
            variant[count] = v;
            count++;
        }
    }

    /** Candidates bucketed in a uniform grid, as linked lists. */
    private static final class Buckets {
        final double x0;
        final double z0;
        final double size;
        final int columns;
        final int rows;
        final int[] head;
        final int[] next;

        Buckets(double minX, double minZ, double maxX, double maxZ, double size, int capacity) {
            this.x0 = minX;
            this.z0 = minZ;
            this.size = size;
            this.columns = Math.max(1, (int) ((maxX - minX) / size) + 1);
            this.rows = Math.max(1, (int) ((maxZ - minZ) / size) + 1);
            this.head = new int[columns * rows];
            this.next = new int[Math.max(capacity, 1)];
            java.util.Arrays.fill(head, -1);
        }

        int column(double x) {
            return Math.clamp((int) Math.floor((x - x0) / size), 0, columns - 1);
        }

        int row(double z) {
            return Math.clamp((int) Math.floor((z - z0) / size), 0, rows - 1);
        }

        void add(int k, double x, double z) {
            int b = row(z) * columns + column(x);
            next[k] = head[b];
            head[b] = k;
        }
    }

    /** A growable list of ints. */
    private static final class IntList {
        int size;
        int[] values = new int[1024];

        void add(int v) {
            if (size == values.length) {
                values = java.util.Arrays.copyOf(values, 2 * size);
            }
            values[size++] = v;
        }
    }
}
