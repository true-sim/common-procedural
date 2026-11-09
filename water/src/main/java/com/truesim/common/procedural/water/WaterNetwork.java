package com.truesim.common.procedural.water;

import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The rivers, streams and waterfalls of a region: the direct Java API (the asset system serves the same meshes,
 * {@code WaterAssets} in {@code common-procedural-assets}).
 *
 * <pre>{@code
 * WaterNetwork rivers = WaterNetwork.build(terrain, new WaterRegion(-8000, -3000, 6000, 13000),
 *     WaterParameters.DEFAULTS);                                   // or WaterSettings.parameters(settings)
 * for (WaterChunk chunk : rivers.chunks()) {
 *     ProceduralMesh surface = rivers.surface(chunk);              // at chunk.originX(), 0, chunk.originZ()
 *     List<BedStone> stones = rivers.stones(chunk);
 * }
 * Waterfall staubbach = rivers.tallest();                         // its sheet: rivers.fallMesh(staubbach.id())
 * double ground = rivers.field().carve(x, z, terrain.height(x, z));
 * }</pre>
 *
 * <ol>
 *   <li><b>Flow</b>: the terrain sampled every {@link WaterParameters.Flow#cell} metres (a cell's lowest
 *       subsample), mapped watercourses burned in, depressions filled (Priority-Flood), single flow directions and
 *       catchments.</li>
 *   <li><b>Channels</b> where the catchment passes {@link WaterParameters.Flow#channelArea} (and along mapped
 *       watercourses), cut into reaches at confluences, short headwater stubs dropped; Strahler orders.</li>
 *   <li><b>Lines</b>: corner-cut, moved to the lowest ground across the flow, smoothed, evenly spaced; the surface the
 *       monotone least-squares fit to the ground along it, never rising downstream, also across confluences.</li>
 *   <li><b>Hydraulics</b>: discharge from the catchment ({@link WaterParameters.Flow#runoff}), width, depth and speed
 *       ({@link WaterParameters.Channels}).</li>
 *   <li><b>Falls</b> where the surface drops steeply ({@link Waterfall}); <b>stones</b> in the beds
 *       ({@link BedStone}); <b>surfaces</b> in chunks ({@link WaterVertex}), made when asked for.</li>
 * </ol>
 *
 * Deterministic: the same terrain, region, parameters and watercourses give the same network. Thread-safe once built.
 */
public final class WaterNetwork {

    /**
     * A square of the surfaces' chunking with water in it.
     *
     * @param x its column: its west edge at {@code x * chunk}
     * @param z its row: its north edge at {@code z * chunk}
     * @param originX the west edge, metres: the surface mesh's origin
     * @param originZ the north edge
     */
    public record WaterChunk(int x, int z, double originX, double originZ) {
    }

    private final WaterParameters parameters;
    private final WaterRegion region;
    private final float[] catchment;
    private final double gridX0;
    private final double gridZ0;
    private final double cell;
    private final int nx;
    private final int nz;
    private final List<Channel> channels;
    private final List<Waterfall> falls;
    private final List<BedStone> stones;
    private final WaterField field;
    private final Map<Long, int[]> chunkSegments;
    private final Map<Long, List<BedStone>> chunkStones;
    private final List<WaterChunk> chunks;
    private final WaterMeshes meshes;

    private WaterNetwork(WaterParameters parameters, WaterRegion region, FlowGrid grid, List<Channel> channels,
        List<Waterfall> falls, List<BedStone> stones) {
        this.parameters = parameters;
        this.region = region;
        // the routing's catchments are kept (for maps and queries), not its working arrays
        this.catchment = new float[grid.count()];
        for (int a = 0; a < catchment.length; a++) {
            catchment[a] = (float) grid.area[a];
        }
        this.gridX0 = grid.x0;
        this.gridZ0 = grid.z0;
        this.cell = grid.cell;
        this.nx = grid.nx;
        this.nz = grid.nz;
        this.channels = Collections.unmodifiableList(channels);
        this.falls = Collections.unmodifiableList(falls);
        this.stones = Collections.unmodifiableList(stones);
        this.field = new WaterField(channels, parameters.banks());
        double size = parameters.surface().chunk();
        Map<Long, List<Integer>> segments = new TreeMap<>();
        for (Channel c : channels) {
            for (int i = 0; i + 1 < c.count(); i++) {
                if (c.fall(i) < 0) {
                    long key = chunkKey(Math.floor(0.5 * (c.x(i) + c.x(i + 1)) / size),
                        Math.floor(0.5 * (c.z(i) + c.z(i + 1)) / size));
                    List<Integer> list = segments.computeIfAbsent(key, k -> new ArrayList<>());
                    list.add(c.id());
                    list.add(i);
                }
            }
        }
        this.chunkSegments = new TreeMap<>();
        List<WaterChunk> list = new ArrayList<>();
        for (Map.Entry<Long, List<Integer>> e : segments.entrySet()) {
            int[] a = new int[e.getValue().size()];
            for (int k = 0; k < a.length; k++) {
                a[k] = e.getValue().get(k);
            }
            chunkSegments.put(e.getKey(), a);
            int cx = (int) (e.getKey() >> 32);
            int cz = (int) (long) e.getKey();
            list.add(new WaterChunk(cx, cz, cx * size, cz * size));
        }
        this.chunks = Collections.unmodifiableList(list);
        this.chunkStones = new TreeMap<>();
        for (BedStone s : stones) {
            chunkStones.computeIfAbsent(chunkKey(Math.floor(s.x() / size), Math.floor(s.z() / size)),
                k -> new ArrayList<>()).add(s);
        }
        this.meshes = new WaterMeshes(this);
    }

    private static long chunkKey(double cx, double cz) {
        return ((long) cx << 32) | ((long) cz & 0xFFFFFFFFL);
    }

    /** The network over {@code terrain} in {@code region}, from the flow alone. */
    public static WaterNetwork build(EcologyTerrain terrain, WaterRegion region, WaterParameters parameters) {
        return build(terrain, region, parameters, List.of());
    }

    /** The network following the mapped watercourses of {@code source} (from the flow where it has none). */
    public static WaterNetwork build(EcologyTerrain terrain, WaterRegion region, WaterParameters parameters,
        Watercourses.Source source) throws IOException {
        return build(terrain, region, parameters, source.courses(region));
    }

    /** The network following {@code courses} (from the flow where they are none). */
    public static WaterNetwork build(EcologyTerrain terrain, WaterRegion region, WaterParameters parameters,
        List<Watercourse> courses) {
        List<Watercourse> mapped = List.copyOf(courses);
        FlowGrid grid = FlowGrid.build(terrain, region, parameters.flow(), mapped);
        NetworkBuilder builder = new NetworkBuilder(terrain, parameters, grid, mapped);
        List<Channel> channels = builder.channels(builder.reaches());
        List<Waterfall> falls = builder.falls(channels);
        List<BedStone> stones = builder.stones(channels);
        return new WaterNetwork(parameters, region, grid, channels, falls, stones);
    }

    public WaterParameters parameters() {
        return parameters;
    }

    public WaterRegion region() {
        return region;
    }

    /** Its channels: reaches between confluences, upstream before downstream. */
    public List<Channel> channels() {
        return channels;
    }

    /** Its waterfalls, free falls and cascades. */
    public List<Waterfall> falls() {
        return falls;
    }

    /** The tallest free fall, or null if there is none. */
    public Waterfall tallest() {
        Waterfall best = null;
        for (Waterfall f : falls) {
            if (f.free() && (best == null || f.height() > best.height())) {
                best = f;
            }
        }
        return best;
    }

    /** The stones in its beds and on its banks. */
    public List<BedStone> stones() {
        return stones;
    }

    /** The stones whose base lies in {@code chunk}. */
    public List<BedStone> stones(WaterChunk chunk) {
        return stones(chunk.x(), chunk.z());
    }

    /** The stones whose base lies in chunk ({@code x}, {@code z}). */
    public List<BedStone> stones(int x, int z) {
        List<BedStone> list = chunkStones.get(chunkKey(x, z));
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /** The water at any point: beds and banks to carve, grass to thin, surfaces and depths. */
    public WaterField field() {
        return field;
    }

    /** The chunks with water surfaces, by column then row. */
    public List<WaterChunk> chunks() {
        return chunks;
    }

    /** The water surface of {@code chunk}, relative to its origin (y absolute). */
    public ProceduralMesh surface(WaterChunk chunk) {
        return surface(chunk.x(), chunk.z());
    }

    /**
     * The water surface of chunk ({@code x}, {@code z}), relative to its north-west corner ({@code x * chunk}, 0,
     * {@code z * chunk}); empty where it has no water. Free falls are left out: their sheets are {@link #fallMesh}.
     */
    public ProceduralMesh surface(int x, int z) {
        int[] segments = chunkSegments.get(chunkKey(x, z));
        double size = parameters.surface().chunk();
        List<BedStone> nearby = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                nearby.addAll(stones(x + dx, z + dz));
            }
        }
        return meshes.surface(segments == null ? new int[0] : segments, x * size, z * size, nearby);
    }

    /**
     * Free fall {@code id}'s sheet (submesh 0) and the jets it breaks into (submesh 1), relative to its lip (the lip
     * point's x and z, and its surface's height); a cascade's is empty: its channel's surface runs down the rock.
     */
    public ProceduralMesh fallMesh(int id) {
        Waterfall fall = falls.get(id);
        return fall.free() ? meshes.fall(fall) : meshes.fall(withoutPath(fall));
    }

    /** The point where fall {@code id}'s mesh has its origin: x, surface height and z of its lip. */
    public double[] fallOrigin(int id) {
        Waterfall fall = falls.get(id);
        Channel c = channels.get(fall.channel());
        return new double[] {c.x(fall.lip()), c.surface(fall.lip()), c.z(fall.lip())};
    }

    private static Waterfall withoutPath(Waterfall fall) {
        Waterfall.Path none = new Waterfall.Path(new double[0], new double[0], new double[0], new double[0],
            new double[0], new double[0], new double[0], new double[0]);
        return new Waterfall(fall.id(), fall.channel(), fall.lip(), fall.foot(), false, fall.height(), fall.width(),
            fall.discharge(), fall.lipSpeed(), fall.thickness(), fall.breakup(), fall.atomised(), none, fall.spray(),
            fall.mist());
    }

    /** The catchment at ({@code x}, {@code z}) of the flow routing, square metres (0 outside the region). */
    public double catchment(double x, double z) {
        int i = (int) Math.floor((x - gridX0) / cell);
        int j = (int) Math.floor((z - gridZ0) / cell);
        return i < 0 || j < 0 || i >= nx || j >= nz ? 0 : catchment[j * nx + i];
    }

    /** Metres between the flow routing's samples. */
    public double cell() {
        return cell;
    }

    /** About how many bytes it holds: for asset caches' budgets. */
    public long bytes() {
        long bytes = 4L * catchment.length;
        for (Channel c : channels) {
            bytes += 17L * 8 * c.count();
        }
        return bytes + 96L * stones.size() + 4096L * falls.size();
    }
}
