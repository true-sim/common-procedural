package com.truesim.common.procedural.preview;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.math.layout.ElementBuffer;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.procedural.building.BuildingSlot;
import com.truesim.common.procedural.building.Buildings;
import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.terrain.TerrainEcology;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import com.truesim.common.procedural.settlement.RoadKind;
import com.truesim.common.procedural.settlement.RoadMeshes;
import com.truesim.common.procedural.settlement.RoadSurface;
import com.truesim.common.procedural.settlement.Settlement;
import com.truesim.common.procedural.settlement.SettlementParameters;
import com.truesim.common.procedural.settlement.Settlements;
import com.truesim.common.procedural.settlement.terrain.SettlementTerrain;
import com.truesim.effects.terrain.data.SurfaceParameters;
import com.truesim.effects.terrain.data.TerrainModifier;
import com.truesim.effects.terrain.data.TerrainModifiers;
import com.truesim.effects.terrain.data.TerrainSite;
import com.truesim.effects.terrain.data.TerrainSurface;
import com.truesim.engine.render.GeometryPool;
import com.truesim.engine.vulkan.VulkanDevice;
import com.truesim.engine.vulkan.VulkanInstance;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.assets.mesh.VertexAttribute;
import com.truesim.scene.assets.mesh.VertexFormat;
import com.truesim.scene.core.ShadowSpec;
import com.truesim.scene.meshbuild.MeshBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Close-ups of the settlement's roads on Lauterbrunnen, rendered by the engine: the terrain made through
 * effects-terrain's modifier hook ({@link SettlementTerrain}: graded to the road meshes, its verges laid into its
 * material), the road meshes (cobbles, kerbs round the junctions' fillets, gravel, dirt, steps) textured, the
 * buildings the buildings generator makes from the plan's specs. Shots: a junction in the core, a road's edge over a
 * cut or fill, a farm track's apron, the core junction again with the roads painted into the terrain only (no
 * meshes), and a rise over the village. {@code TRUESIM_FILMS=true ./gradlew -Ptruesim.preview=true :preview:test
 * --tests '*RoadCloseUps*' -Pfilms=<dir>}, then ffmpeg over the frames.
 */
class RoadCloseUps {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int SUPER = 2;
    private static final double FPS = 30;
    /** The fine terrain about each shot: its side and spacing, metres. */
    private static final double WINDOW = 110;
    private static final double FINE = 0.25;

    private TerrainSurface natural;
    private TerrainSurface modified;
    private Settlement plan;

    /** A shot: where it looks, for how long, the camera at each moment. */
    private record Shot(String name, double seconds, boolean meshes, Camera camera) {
    }

    @FunctionalInterface
    private interface Camera {
        /** The camera at {@code u} (0 to 1 through the shot): x, y, z, then the target's x, y, z. */
        double[] at(double u);
    }

    private void setUp() throws IOException {
        String given = System.getProperty("truesim.terrain.cache");
        Path cache = given != null ? Path.of(given) : Path.of("..", "..", "effects-terrain", "terrain-cache");
        Path folder = cache.resolve("sites").resolve("lauterbrunnen");
        Path file = folder.resolve("site.terrain");
        assumeTrue(Files.isRegularFile(file), "effects-terrain's cache holds no Lauterbrunnen");
        try (InputStream in = Files.newInputStream(file)) {
            TerrainSite site = TerrainSite.read(in, name -> Files.newInputStream(folder.resolve(name)));
            natural = new TerrainSurface(site, new SurfaceParameters(2.5, 64, 6, 1.5, false));
        }
        Ecology ecology = new Ecology(TerrainEcology.of(natural, 1), EcologyParameters.ALPS
            .withLandUse(EcologyParameters.LandUse.MEADOWS).withSeed(11), AlpineSpecies.ALL);
        long start = System.nanoTime();
        plan = Settlements.plan(SettlementParameters.DEFAULTS, ecology, -130, 300, 7);
        plan.roadMeshes();
        modified = natural.withModifiers(TerrainModifiers.of(SettlementTerrain.of(plan)));
        System.out.printf(Locale.ROOT, "plan and road meshes in %.1f s%n", (System.nanoTime() - start) / 1e9);
    }

    private double ground(double x, double z) {
        return modified.height(x, z, FINE);
    }

    // ------------------------------------------------------------------------------------------------------ shots

    private List<Shot> shots() {
        List<Shot> shots = new ArrayList<>();
        // a lane leaving the main road in the core: cobbles, the junction's rounded corners and kerbs
        // (a lane's junction if there is one, else where the main road leaves the square)
        Settlement.Road lane = null;
        for (Settlement.Road r : plan.roads()) {
            boolean joins = r.surface() == RoadSurface.COBBLES && r.count() > 8 && (r.from() == Settlement.SQUARE
                || r.from() >= 0 && plan.roads().get(r.from()).kind() != r.kind());
            if (joins && (lane == null || lane.kind() == RoadKind.MAIN && r.kind() == RoadKind.LANE)) {
                lane = r;
            }
        }
        System.out.printf(Locale.ROOT, "junction: %s%n", lane == null ? "none" : lane.kind() + " from " + lane.from());
        if (lane != null) {
            double jx = lane.x()[0];
            double jz = lane.z()[0];
            double a0 = Math.atan2(lane.z()[4] - jz, lane.x()[4] - jx) + 2.4;
            shots.add(new Shot("core junction", 7, true, orbit(jx, jz, 13, 4.5, a0, a0 + 1.2)));
            shots.add(new Shot("core junction painted", 4, false, orbit(jx, jz, 13, 4.5, a0 + 1.2, a0 + 1.6)));
        }
        // a gravel road's edge where it cuts or fills most, away from junctions
        double best = 0;
        double[] edge = null;
        for (Settlement.Road r : plan.roads()) {
            if (r.surface() != RoadSurface.GRAVEL || r.count() < 30) {
                continue;
            }
            for (int i = 10; i < r.count() - 10; i++) {
                double dx = r.x()[i + 1] - r.x()[i - 1];
                double dz = r.z()[i + 1] - r.z()[i - 1];
                double l = Math.hypot(dx, dz);
                double nx = dz / l;
                double nz = -dx / l;
                double side = r.width() / 2 + 3;
                double cut = Math.abs(natural.height(r.x()[i] + nx * side, r.z()[i] + nz * side, 1) - r.y()[i]);
                if (cut > best && Math.hypot(r.x()[i] - plan.originX(), r.z()[i] - plan.originZ()) < 450) {
                    best = cut;
                    edge = new double[] {r.x()[i], r.z()[i], dx / l, dz / l, nx, nz, r.width()};
                }
            }
        }
        if (edge != null) {
            double[] e = edge;
            System.out.printf(Locale.ROOT, "edge at (%.0f, %.0f): %.1f m cut or fill%n", e[0], e[1], best);
            shots.add(new Shot("edge", 6, true, u -> {
                // standing on the road by its other edge, looking across at the edge over the cut or fill
                double along = -7 + 10 * u;
                double hw = e[6] / 2;
                double cx = e[0] + e[2] * along - e[4] * hw * 0.7;
                double cz = e[1] + e[3] * along - e[5] * hw * 0.7;
                double tx = e[0] + e[2] * (along + 6) + e[4] * (hw + 1);
                double tz = e[1] + e[3] * (along + 6) + e[5] * (hw + 1);
                return new double[] {cx, ground(cx, cz) + 1.7, cz, tx, ground(tx, tz), tz};
            }));
        }
        // a farm track's apron where it joins
        for (Settlement.Road r : plan.roads()) {
            if (r.kind() == RoadKind.TRACK && r.from() >= 0 && r.count() > 8) {
                double jx = r.x()[0];
                double jz = r.z()[0];
                double a0 = Math.atan2(r.z()[6] - jz, r.x()[6] - jx) + 2.2;
                shots.add(new Shot("track apron", 5, true, orbit(jx, jz, 12, 4, a0, a0 + 0.9)));
                break;
            }
        }
        // a rise over the village from the square
        double sx = plan.square().x();
        double sz = plan.square().z();
        shots.add(new Shot("over the village", 6, true, u -> {
            double s = u * u * (3 - 2 * u);
            double r = 40 + 60 * s;
            double cx = sx - 0.6 * r;
            double cz = sz + 0.8 * r;
            return new double[] {cx, ground(cx, cz) + 6 + 70 * s, cz, sx, ground(sx, sz), sz};
        }));
        return shots;
    }

    private Camera orbit(double x, double z, double radius, double height, double a0, double a1) {
        return u -> {
            double a = a0 + (a1 - a0) * u;
            double cx = x + radius * Math.cos(a);
            double cz = z + radius * Math.sin(a);
            return new double[] {cx, ground(cx, cz) + height, cz, x, ground(x, z) + 0.3, z};
        };
    }

    // ---------------------------------------------------------------------------------------------- textures

    private static BufferedImage image(int size, Texel texel) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double[] c = texel.at(x, y);
                image.setRGB(x, y, 0xff000000 | (int) (255 * Math.clamp(c[0], 0, 1)) << 16
                    | (int) (255 * Math.clamp(c[1], 0, 1)) << 8 | (int) (255 * Math.clamp(c[2], 0, 1)));
            }
        }
        return image;
    }

    @FunctionalInterface
    private interface Texel {
        double[] at(int x, int y);
    }

    /** Value noise in [0, 1), tiling every {@code period} texels. */
    private static double noise(double x, double y, int period, long seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        double sx = fx * fx * (3 - 2 * fx);
        double sy = fy * fy * (3 - 2 * fy);
        double a = hash(Math.floorMod(x0, period), Math.floorMod(y0, period), seed);
        double b = hash(Math.floorMod(x0 + 1, period), Math.floorMod(y0, period), seed);
        double c = hash(Math.floorMod(x0, period), Math.floorMod(y0 + 1, period), seed);
        double d = hash(Math.floorMod(x0 + 1, period), Math.floorMod(y0 + 1, period), seed);
        return (1 - sy) * ((1 - sx) * a + sx * b) + sy * ((1 - sx) * c + sx * d);
    }

    private static double hash(int x, int y, long seed) {
        long h = (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ seed;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Fractal noise over a 512 texture, tiling. */
    private static double fbm(int x, int y, int base, long seed) {
        double v = 0;
        double amp = 0.5;
        int period = base;
        double scale = base / 512.0;
        for (int o = 0; o < 4; o++) {
            v += amp * noise(x * scale, y * scale, period, seed + o);
            amp *= 0.5;
            period *= 2;
            scale *= 2;
        }
        return v / 0.9375;
    }

    /** Cobbles: rounded setts in staggered rows, dark joints, each stone its own shade. */
    private static BufferedImage cobbles() {
        int size = 512;
        int rows = 8;
        double h = size / (double) rows;
        double w = h * 1.25;
        return image(size, (x, y) -> {
            int row = (int) (y / h);
            double offset = (row % 2) * w / 2;
            int col = (int) Math.floor((x + offset) / w);
            double lx = (x + offset) / w - col;
            double ly = y / h - row;
            double edge = Math.min(Math.min(lx, 1 - lx) * w, Math.min(ly, 1 - ly) * h);
            double joint = Math.clamp(edge / 7, 0, 1);
            double shade = 0.75 + 0.5 * hash(Math.floorMod(col, 10), row, 31);
            double grain = 0.85 + 0.3 * fbm(x, y, 64, 3);
            double rounded = 0.75 + 0.25 * Math.sqrt(joint);
            double g = shade * grain * rounded * (0.25 + 0.75 * joint);
            return new double[] {0.42 * g, 0.40 * g, 0.37 * g};
        });
    }

    private static BufferedImage gravel() {
        return image(512, (x, y) -> {
            double stone = noise(x / 3.0, y / 3.0, 512 / 3, 11);
            double fine = fbm(x, y, 64, 12);
            double g = 0.55 + 0.35 * stone + 0.2 * fine;
            double warm = 0.95 + 0.1 * noise(x / 9.0, y / 9.0, 512 / 9, 13);
            return new double[] {0.48 * g * warm, 0.45 * g, 0.40 * g / warm};
        });
    }

    private static BufferedImage dirt() {
        return image(512, (x, y) -> {
            double g = 0.6 + 0.5 * fbm(x, y, 32, 21) + 0.15 * noise(x / 2.0, y / 2.0, 256, 22);
            return new double[] {0.30 * g, 0.22 * g, 0.15 * g};
        });
    }

    private static BufferedImage granite() {
        return image(256, (x, y) -> {
            double speck = hash(x, y, 41) > 0.85 ? 0.6 : hash(x, y, 42) > 0.9 ? 1.4 : 1;
            double g = (0.75 + 0.25 * fbm(2 * x, 2 * y, 32, 43)) * speck;
            return new double[] {0.55 * g, 0.54 * g, 0.52 * g};
        });
    }

    /** The ground's grain, near white: the terrain's vertex colours tint it. */
    private static BufferedImage grain() {
        return image(512, (x, y) -> {
            double g = 0.72 + 0.28 * fbm(x, y, 64, 51) + 0.12 * (noise(x / 1.5, y / 1.5, 341, 52) - 0.5);
            return new double[] {g, g, g};
        });
    }

    /** A building slot's texture: its colour with a little grain (timber with its grain along, shingles in rows). */
    private static BufferedImage slot(BuildingSlot slot) {
        double[] c = switch (slot) {
            case STONE -> new double[] {0.45, 0.43, 0.40};
            case PLASTER -> new double[] {0.85, 0.82, 0.75};
            case TIMBER -> new double[] {0.30, 0.18, 0.09};
            case TRIM -> new double[] {0.38, 0.25, 0.13};
            case SHINGLES -> new double[] {0.28, 0.22, 0.18};
            case SLATES -> new double[] {0.18, 0.18, 0.20};
            case INTERIOR -> new double[] {0.70, 0.62, 0.50};
            case FLOOR -> new double[] {0.45, 0.30, 0.18};
            case GLASS -> new double[] {0.10, 0.12, 0.14};
            case WINDOW -> new double[] {0.08, 0.09, 0.10};
            case HEARTH -> new double[] {0.35, 0.33, 0.30};
            case SHUTTERS -> new double[] {0.18, 0.38, 0.20};
            case DOOR -> new double[] {0.32, 0.20, 0.10};
            case METAL -> new double[] {0.25, 0.25, 0.27};
            case FIREWOOD -> new double[] {0.50, 0.38, 0.24};
            case FOLIAGE -> new double[] {0.12, 0.25, 0.08};
            case FLOWERS -> new double[] {0.75, 0.15, 0.20};
        };
        boolean wood = slot == BuildingSlot.TIMBER || slot == BuildingSlot.TRIM || slot == BuildingSlot.FLOOR
            || slot == BuildingSlot.DOOR || slot == BuildingSlot.SHUTTERS;
        return image(128, (x, y) -> {
            double g = wood ? 0.8 + 0.4 * noise(x / 16.0, y / 1.5, 8, slot.ordinal()) : 0.85 + 0.3 * fbm(4 * x,
                4 * y, 32, slot.ordinal());
            if (slot == BuildingSlot.SHINGLES && y % 16 < 2) {
                g *= 0.5;
            }
            return new double[] {c[0] * g, c[1] * g, c[2] * g};
        });
    }

    // -------------------------------------------------------------------------------------------------- meshes

    /** The submeshes of {@code mesh} as scene meshes (normals, tangents, texture coordinates), null where empty. */
    private static MeshData[] split(ProceduralMesh mesh) {
        int n = mesh.vertexCount();
        ElementBuffer vertices = mesh.vertices();
        Vec3Attribute normal = vertices.attribute(ProceduralVertex.NORMAL);
        Vec3Attribute tangent = vertices.attribute(ProceduralVertex.TANGENT);
        Vec2Attribute uv = vertices.attribute(ProceduralVertex.UV);
        float[] normals = new float[3 * n];
        float[] tangents = new float[4 * n];
        float[] uvs = new float[2 * n];
        for (int i = 0; i < n; i++) {
            normals[3 * i] = (float) normal.x(i);
            normals[3 * i + 1] = (float) normal.y(i);
            normals[3 * i + 2] = (float) normal.z(i);
            tangents[4 * i] = (float) tangent.x(i);
            tangents[4 * i + 1] = (float) tangent.y(i);
            tangents[4 * i + 2] = (float) tangent.z(i);
            tangents[4 * i + 3] = 1;
            uvs[2 * i] = (float) uv.x(i);
            uvs[2 * i + 1] = (float) uv.y(i);
        }
        MeshData[] out = new MeshData[mesh.submeshCount()];
        for (int s = 0; s < mesh.submeshCount(); s++) {
            if (mesh.submeshEnd(s) > mesh.submeshStart(s)) {
                out[s] = new MeshBuilder(VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.TANGENT,
                    VertexAttribute.UV0), n).positions(mesh.positions()).attribute(VertexAttribute.NORMAL, normals)
                    .attribute(VertexAttribute.TANGENT, tangents).attribute(VertexAttribute.UV0, uvs)
                    .submesh(mesh.submeshIndices(s)).buildFlat();
            }
        }
        return out;
    }

    /**
     * The terrain about ({@code cx}, {@code cz}) every {@link #FINE} metres from the modified surface: heights through
     * the modifier hook, its material weights tinting the ground's grain (grass, the worn verge, gravel scatter;
     * the roads' own under their meshes, for the painted shot).
     */
    private void window(Stage stage, int material, double cx, double cz, List<double[]> windows) {
        double x0 = Math.floor((cx - WINDOW / 2) / FINE) * FINE;
        double z0 = Math.floor((cz - WINDOW / 2) / FINE) * FINE;
        windows.add(new double[] {x0, z0});
        int tile = 160;
        int tiles = (int) Math.ceil(WINDOW / FINE / tile);
        double[] cover = new double[TerrainModifier.MATERIALS];
        for (int tj = 0; tj < tiles; tj++) {
            for (int ti = 0; ti < tiles; ti++) {
                int n = tile + 1;
                double ox = x0 + ti * tile * FINE;
                double oz = z0 + tj * tile * FINE;
                float[] positions = new float[3 * n * n];
                float[] normals = new float[3 * n * n];
                float[] tangents = new float[4 * n * n];
                float[] uvs = new float[2 * n * n];
                float[] colors = new float[4 * n * n];
                double[] h = new double[(n + 2) * (n + 2)];
                for (int j = -1; j <= n; j++) {
                    for (int i = -1; i <= n; i++) {
                        h[(j + 1) * (n + 2) + i + 1] = ground(ox + i * FINE, oz + j * FINE);
                    }
                }
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        int a = j * n + i;
                        double x = ox + i * FINE;
                        double z = oz + j * FINE;
                        positions[3 * a] = (float) (i * FINE);
                        positions[3 * a + 1] = (float) h[(j + 1) * (n + 2) + i + 1];
                        positions[3 * a + 2] = (float) (j * FINE);
                        double gx = (h[(j + 1) * (n + 2) + i + 2] - h[(j + 1) * (n + 2) + i]) / (2 * FINE);
                        double gz = (h[(j + 2) * (n + 2) + i + 1] - h[j * (n + 2) + i + 1]) / (2 * FINE);
                        double l = Math.sqrt(gx * gx + gz * gz + 1);
                        normals[3 * a] = (float) (-gx / l);
                        normals[3 * a + 1] = (float) (1 / l);
                        normals[3 * a + 2] = (float) (-gz / l);
                        tangents[4 * a] = 1;
                        tangents[4 * a + 3] = 1;
                        uvs[2 * a] = (float) ((x - x0) / 1.6);
                        uvs[2 * a + 1] = (float) (-(z - z0) / 1.6);
                        java.util.Arrays.fill(cover, 0);
                        double total = modified.materials(x, z, FINE, cover);
                        double meadow = 0.8 + 0.4 * noise(x / 3.0, z / 3.0, 1 << 20, 61);
                        double[] grass = {0.22 * meadow, 0.36 * meadow, 0.10 * meadow};
                        double[][] mats = {{0.55, 0.52, 0.46}, {0.48, 0.46, 0.43}, {0.42, 0.31, 0.21},
                            {0.62, 0.56, 0.44}};
                        double r = grass[0] * (1 - total);
                        double g = grass[1] * (1 - total);
                        double b = grass[2] * (1 - total);
                        for (int k = 0; k < TerrainModifier.MATERIALS; k++) {
                            r += cover[k] * mats[k][0];
                            g += cover[k] * mats[k][1];
                            b += cover[k] * mats[k][2];
                        }
                        double steep = Math.clamp((Math.sqrt(gx * gx + gz * gz) - 0.8) / 0.5, 0, 1);
                        colors[4 * a] = (float) (r * (1 - steep) + 0.45 * steep);
                        colors[4 * a + 1] = (float) (g * (1 - steep) + 0.44 * steep);
                        colors[4 * a + 2] = (float) (b * (1 - steep) + 0.42 * steep);
                        colors[4 * a + 3] = 1;
                    }
                }
                int[] indices = new int[6 * (n - 1) * (n - 1)];
                int k = 0;
                for (int j = 0; j < n - 1; j++) {
                    for (int i = 0; i < n - 1; i++) {
                        int a = j * n + i;
                        indices[k++] = a;
                        indices[k++] = a + n;
                        indices[k++] = a + 1;
                        indices[k++] = a + 1;
                        indices[k++] = a + n;
                        indices[k++] = a + n + 1;
                    }
                }
                MeshData mesh = new MeshBuilder(VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.TANGENT,
                    VertexAttribute.UV0, VertexAttribute.COLOR), n * n).positions(positions)
                    .attribute(VertexAttribute.NORMAL, normals).attribute(VertexAttribute.TANGENT, tangents)
                    .attribute(VertexAttribute.UV0, uvs).attribute(VertexAttribute.COLOR, colors)
                    .submesh(indices).buildFlat();
                stage.set(stage.id(), stage.addMesh(mesh), material, ox, 0, oz, 0, 1, 1, 1);
            }
        }
    }

    /** The valley beyond the windows every 4 m (and coarser further), lowered where a window lies. */
    private void valley(Stage stage, int material, List<double[]> windows) {
        double[][] rings = {{4, 1536}, {16, 12288}};
        double sx = plan.square().x();
        double sz = plan.square().z();
        double[] normal = new double[3];
        for (int ring = 0; ring < rings.length; ring++) {
            double step = rings[ring][0];
            double size = rings[ring][1];
            double inner = ring == 0 ? 0 : rings[ring - 1][1];
            int cells = 128;
            double tileSize = cells * step;
            int tiles = (int) (size / tileSize);
            double fx0 = Math.floor((sx - size / 2) / tileSize) * tileSize;
            double fz0 = Math.floor((sz - size / 2) / tileSize) * tileSize;
            double innerX0 = ring == 0 ? 0 : Math.floor((sx - inner / 2) / (rings[ring - 1][0] * 128))
                * rings[ring - 1][0] * 128;
            double innerZ0 = ring == 0 ? 0 : Math.floor((sz - inner / 2) / (rings[ring - 1][0] * 128))
                * rings[ring - 1][0] * 128;
            for (int tj = 0; tj < tiles; tj++) {
                for (int ti = 0; ti < tiles; ti++) {
                    double ox = fx0 + ti * tileSize;
                    double oz = fz0 + tj * tileSize;
                    if (ring > 0 && ox >= innerX0 && ox + tileSize <= innerX0 + inner && oz >= innerZ0
                        && oz + tileSize <= innerZ0 + inner) {
                        continue;
                    }
                    int n = cells + 1;
                    float[] positions = new float[3 * n * n];
                    float[] normals = new float[3 * n * n];
                    float[] tangents = new float[4 * n * n];
                    float[] uvs = new float[2 * n * n];
                    float[] colors = new float[4 * n * n];
                    for (int j = 0; j < n; j++) {
                        for (int i = 0; i < n; i++) {
                            int a = j * n + i;
                            double x = ox + i * step;
                            double z = oz + j * step;
                            double drop = ring > 0 ? 2 : 0;
                            for (double[] w : windows) {
                                if (x > w[0] + step && x < w[0] + WINDOW - step && z > w[1] + step
                                    && z < w[1] + WINDOW - step) {
                                    drop = 1.5;
                                }
                            }
                            double y = modified.height(x, z, step) - drop;
                            positions[3 * a] = (float) (i * step);
                            positions[3 * a + 1] = (float) y;
                            positions[3 * a + 2] = (float) (j * step);
                            modified.normalAt(x, z, step, normal);
                            normals[3 * a] = (float) normal[0];
                            normals[3 * a + 1] = (float) normal[1];
                            normals[3 * a + 2] = (float) normal[2];
                            tangents[4 * a] = 1;
                            tangents[4 * a + 3] = 1;
                            uvs[2 * a] = (float) (i * step / 1.6);
                            uvs[2 * a + 1] = (float) (-j * step / 1.6);
                            double steep = Math.clamp((Math.sqrt(1 - normal[1] * normal[1]) / normal[1] - 0.7) / 0.5,
                                0, 1);
                            double alt = Math.clamp((y - 1900) / 600, 0, 1);
                            double snow = Math.clamp((y - 2600) / 300, 0, 1) * (1 - steep * 0.6);
                            double r = 0.22 * (1 - steep) + 0.45 * steep;
                            double g = 0.36 * (1 - steep) + 0.44 * steep;
                            double b = 0.10 * (1 - steep) + 0.42 * steep;
                            r = r * (1 - alt) + 0.4 * alt;
                            g = g * (1 - alt) + 0.4 * alt;
                            b = b * (1 - alt) + 0.36 * alt;
                            colors[4 * a] = (float) (r * (1 - snow) + 1.2 * snow);
                            colors[4 * a + 1] = (float) (g * (1 - snow) + 1.2 * snow);
                            colors[4 * a + 2] = (float) (b * (1 - snow) + 1.25 * snow);
                            colors[4 * a + 3] = 1;
                        }
                    }
                    int[] indices = new int[6 * (n - 1) * (n - 1)];
                    int k = 0;
                    for (int j = 0; j < n - 1; j++) {
                        for (int i = 0; i < n - 1; i++) {
                            int a = j * n + i;
                            indices[k++] = a;
                            indices[k++] = a + n;
                            indices[k++] = a + 1;
                            indices[k++] = a + 1;
                            indices[k++] = a + n;
                            indices[k++] = a + n + 1;
                        }
                    }
                    MeshData mesh = new MeshBuilder(VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.TANGENT,
                        VertexAttribute.UV0, VertexAttribute.COLOR), n * n).positions(positions)
                        .attribute(VertexAttribute.NORMAL, normals).attribute(VertexAttribute.TANGENT, tangents)
                        .attribute(VertexAttribute.UV0, uvs).attribute(VertexAttribute.COLOR, colors)
                        .submesh(indices).buildFlat();
                    stage.set(stage.id(), stage.addMesh(mesh), material, false, ox, 0, oz, 0, 1, 1, 1);
                }
            }
        }
    }

    // --------------------------------------------------------------------------------------------------- film

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FILMS", matches = "true")
    void roadEdgesAndJunctions() throws Exception {
        setUp();
        Path out = Path.of(System.getProperty("truesim.films", "build/films"), "road-close-ups");
        Files.createDirectories(out);
        List<Shot> shots = shots();
        try (VulkanInstance instance = VulkanInstance.builder().applicationName("road close-ups").surfaces(false)
            .build(); VulkanDevice device = VulkanDevice.createHeadless(instance.bestGpu());
            Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 0,
                GeometryPool.Capacity.of(1 << 22, 1 << 20, 512), 1 << 14, 600)) {
            System.out.println("road close-ups on " + instance.bestGpu().name());
            stage.sun(-0.45f, 0.55f, 0.6f, ShadowSpec.cascades(4, 2048, 600));
            int groundMaterial = stage.plainMaterial(stage.texture(grain()), 0.95f);
            // the road meshes' slots: cobbles, gravel, dirt, kerb, steps
            int[] roadMaterials = {stage.plainMaterial(stage.texture(cobbles()), 0.85f),
                stage.plainMaterial(stage.texture(gravel()), 0.95f), stage.plainMaterial(stage.texture(dirt()), 0.95f),
                stage.plainMaterial(stage.texture(granite()), 0.7f), stage.plainMaterial(stage.texture(granite()),
                0.75f)};
            int[] buildingMaterials = new int[BuildingSlot.values().length];
            for (BuildingSlot slot : BuildingSlot.values()) {
                buildingMaterials[slot.ordinal()] = stage.plainMaterial(stage.texture(slot(slot)),
                    slot == BuildingSlot.GLASS ? 0.1f : 0.85f);
            }
            List<double[]> windows = new ArrayList<>();
            List<double[]> centres = new ArrayList<>();
            for (Shot shot : shots) {
                double[] c = shot.camera().at(0.5);
                double[] t = shot.camera().at(0);
                centres.add(new double[] {(c[3] + t[3]) / 2, (c[5] + t[5]) / 2});
            }
            long start = System.nanoTime();
            for (int s = 0; s < shots.size(); s++) {
                double[] c = centres.get(s);
                boolean done = false;
                for (double[] w : windows) {
                    done |= Math.abs(w[0] + WINDOW / 2 - c[0]) < 20 && Math.abs(w[1] + WINDOW / 2 - c[1]) < 20;
                }
                if (!done && !shots.get(s).name().equals("over the village")) {
                    window(stage, groundMaterial, c[0], c[1], windows);
                }
            }
            valley(stage, groundMaterial, windows);
            // the road meshes, a scene mesh per tile and slot (kept as ids to hide them for the painted shot)
            List<int[]> roadInstances = new ArrayList<>();
            for (RoadMeshes.Tile tile : plan.roadMeshes()) {
                MeshData[] parts = split(tile.mesh());
                for (int s = 0; s < parts.length; s++) {
                    if (parts[s] != null) {
                        int mesh = stage.addMesh(parts[s]);
                        int id = stage.id();
                        stage.set(id, mesh, roadMaterials[s], tile.x(), tile.y(), tile.z(), 0, 1, 1, 1);
                        roadInstances.add(new int[] {id, mesh, roadMaterials[s], (int) tile.x(), (int) tile.z(),
                            (int) Math.round(tile.y())});
                    }
                }
            }
            // the buildings, as the buildings generator makes them from the plan's specs
            int built = 0;
            for (Settlement.Building b : plan.buildings()) {
                if (Math.hypot(b.x() - plan.square().x(), b.z() - plan.square().z()) > 320) {
                    continue;
                }
                com.truesim.common.procedural.building.Building building = Buildings.generate(b.spec(),
                    (x, z) -> ground(x, z));
                ProceduralMesh mesh = building.lods().get(Math.min(1, building.lods().size() - 1));
                MeshData[] parts = split(mesh);
                for (int s = 0; s < parts.length; s++) {
                    if (parts[s] != null) {
                        stage.set(stage.id(), stage.addMesh(parts[s]), buildingMaterials[s], b.x(), building.floor(),
                            b.z(), b.yaw(), 1, 1, 1);
                    }
                }
                built++;
            }
            System.out.printf(Locale.ROOT, "scene: %d windows, %d road parts, %d buildings in %.1f s%n",
                windows.size(), roadInstances.size(), built, (System.nanoTime() - start) / 1e9);
            stage.shade();
            int frame = 0;
            boolean shown = true;
            for (Shot shot : shots) {
                if (shot.meshes() != shown) {
                    for (int[] r : roadInstances) {
                        // hidden: scaled to nothing far below; shown: back in place
                        stage.set(r[0], r[1], r[2], shot.meshes() ? r[3] : 0, shot.meshes() ? r[5] : -10000,
                            shot.meshes() ? r[4] : 0, 0, shot.meshes() ? 1 : 1e-6, shot.meshes() ? 1 : 1e-6,
                            shot.meshes() ? 1 : 1e-6);
                    }
                    shown = shot.meshes();
                }
                int frames = (int) (shot.seconds() * FPS);
                System.out.printf(Locale.ROOT, "shot %s: %d frames from %d%n", shot.name(), frames, frame);
                for (int f = 0; f < frames; f++) {
                    double[] c = shot.camera().at(f / (frames - 1.0));
                    stage.look(c[0], c[1], c[2], c[3], c[4], c[5], 50);
                    stage.frames(f == 0 ? 6 : 1, 1 / FPS);
                    write(stage.shot(), out, frame++);
                }
            }
        }
    }

    private static void write(BufferedImage big, Path folder, int frame) throws IOException {
        BufferedImage small = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                for (int j = 0; j < SUPER; j++) {
                    for (int i = 0; i < SUPER; i++) {
                        int p = big.getRGB(SUPER * x + i, SUPER * y + j);
                        r += p >> 16 & 0xff;
                        g += p >> 8 & 0xff;
                        b += p & 0xff;
                    }
                }
                int n = SUPER * SUPER;
                small.setRGB(x, y, (r / n) << 16 | (g / n) << 8 | b / n);
            }
        }
        ImageIO.write(small, "png", folder.resolve(String.format(Locale.ROOT, "f%04d.png", frame)).toFile());
    }
}
