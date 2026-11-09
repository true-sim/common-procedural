package com.truesim.common.procedural.preview;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.Site;
import com.truesim.common.procedural.ecology.terrain.TerrainEcology;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.settlement.BuildingKind;
import com.truesim.common.procedural.settlement.BuildingSpec;
import com.truesim.common.procedural.settlement.RoadKind;
import com.truesim.common.procedural.settlement.RoadMeshes;
import com.truesim.common.procedural.settlement.Settlement;
import com.truesim.common.procedural.settlement.SettlementParameters;
import com.truesim.common.procedural.settlement.Settlements;
import com.truesim.effects.terrain.data.SurfaceParameters;
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
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
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
 * The settlement's film on Lauterbrunnen (effects-terrain's cache): the village's plan growing from above (the main
 * road along the valley, the square, lanes, tracks and footpaths, plots, footprints, the barns and their paths), then
 * a low flight along the main road over the road meshes (cobbles and kerbs in the core, gravel beyond, steps on the
 * footpaths) on the graded terrain, the footprints as plain blocks. {@code TRUESIM_FILMS=true ./gradlew
 * -Ptruesim.preview=true :preview:test --tests '*SettlementFilm*' -Pfilms=<dir>}, then ffmpeg over the frames.
 */
class SettlementFilm {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int SUPER = 2;
    private static final double FPS = 30;
    private static final double ORIGIN_X = -130;
    private static final double ORIGIN_Z = 300;
    /** The plan's growth, seconds, and the hold after it. */
    private static final double GROWTH = 11;
    private static final double HOLD = 1.5;
    /** The flight: its speed along the main road (m/s), its length (m) and the rise at its end (s). */
    private static final double SPEED = 15;
    private static final double FLIGHT = 330;
    private static final double RISE = 5;

    private TerrainSurface surface;
    private Ecology ecology;
    private Settlement plan;

    private void setUp() throws IOException {
        String given = System.getProperty("truesim.terrain.cache");
        Path cache = given != null ? Path.of(given) : Path.of("..", "..", "effects-terrain", "terrain-cache");
        Path folder = cache.resolve("sites").resolve("lauterbrunnen");
        Path file = folder.resolve("site.terrain");
        assumeTrue(Files.isRegularFile(file), "effects-terrain's cache holds no Lauterbrunnen");
        try (InputStream in = Files.newInputStream(file)) {
            TerrainSite site = TerrainSite.read(in, name -> Files.newInputStream(folder.resolve(name)));
            surface = new TerrainSurface(site, new SurfaceParameters(2.5, 64, 6, 1.5, true));
        }
        ecology = new Ecology(TerrainEcology.of(surface, 2), EcologyParameters.ALPS
            .withLandUse(EcologyParameters.LandUse.MEADOWS).withSeed(11), AlpineSpecies.ALL);
        long start = System.nanoTime();
        plan = Settlements.plan(SettlementParameters.DEFAULTS, ecology, ORIGIN_X, ORIGIN_Z, 7);
        System.out.printf(Locale.ROOT, "plan: %d roads, %d plots, %d buildings in %.1f s%n", plan.roads().size(),
            plan.plots().size(), plan.buildings().size(), (System.nanoTime() - start) / 1e9);
    }

    private double height(double x, double z) {
        return surface.heightAt(x, z, 2);
    }

    private double graded(double x, double z) {
        return plan.grading().height(x, z, height(x, z));
    }

    // ------------------------------------------------------------------------------------------------ the plan

    /** The map's frame: its centre and scale (pixels a metre), framing the buildings and roads. */
    private double mapX;
    private double mapZ;
    private double mapScale;

    private void frame() {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Settlement.Building b : plan.buildings()) {
            minX = Math.min(minX, b.x());
            maxX = Math.max(maxX, b.x());
            minZ = Math.min(minZ, b.z());
            maxZ = Math.max(maxZ, b.z());
        }
        double margin = 60;
        mapX = (minX + maxX) / 2;
        mapZ = (minZ + maxZ) / 2;
        mapScale = Math.min(WIDTH / (maxZ - minZ + 2 * margin), HEIGHT / (maxX - minX + 2 * margin));
    }

    private double scale() {
        return mapScale;
    }

    /** The picture's x of ground plan ({@code x}, {@code z}): the map turned so the valley runs across, north left. */
    private double px(double x, double z) {
        return WIDTH / 2.0 + (z - mapZ) * scale();
    }

    /** The picture's y of ground plan ({@code x}, {@code z}): east up. */
    private double py(double x, double z) {
        return HEIGHT / 2.0 - (x - mapX) * scale();
    }

    /** The hillshaded valley under the plan, the river blue. */
    private BufferedImage background() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Site site = new Site();
        double[] water = new double[(WIDTH / 4 + 2) * (HEIGHT / 4 + 2)];
        for (int y = 0; y < HEIGHT / 4 + 2; y++) {
            for (int x = 0; x < WIDTH / 4 + 2; x++) {
                ecology.conditions(mapX - (y * 4 - HEIGHT / 2.0) / scale(), mapZ + (x * 4 - WIDTH / 2.0)
                    / scale(), site);
                water[y * (WIDTH / 4 + 2) + x] = site.water;
            }
        }
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                double wx = mapX - (y + 0.5 - HEIGHT / 2.0) / scale();
                double wz = mapZ + (x + 0.5 - WIDTH / 2.0) / scale();
                double e = 1.5;
                double gx = (height(wx + e, wz) - height(wx - e, wz)) / (2 * e);
                double gz = (height(wx, wz + e) - height(wx, wz - e)) / (2 * e);
                double l = Math.sqrt(gx * gx + gz * gz + 1);
                double shade = Math.clamp((0.55 * gx + 0.55 * gz + 1) / l / 1.15, 0.12, 1);
                double steep = Math.clamp((Math.sqrt(gx * gx + gz * gz) - 0.7) / 0.6, 0, 1);
                double r = (0.40 + 0.18 * steep) * shade;
                double g = (0.55 - 0.02 * steep) * shade;
                double b = (0.30 + 0.22 * steep) * shade;
                double w = water[(y / 4) * (WIDTH / 4 + 2) + x / 4];
                r = r * (1 - 0.7 * w) + 0.10 * w;
                g = g * (1 - 0.7 * w) + 0.25 * w;
                b = b * (1 - 0.7 * w) + 0.55 * w;
                image.setRGB(x, y, rgb(r, g, b));
            }
        }
        return image;
    }

    private static int rgb(double r, double g, double b) {
        return (int) (255 * Math.clamp(r, 0, 1)) << 16 | (int) (255 * Math.clamp(g, 0, 1)) << 8
            | (int) (255 * Math.clamp(b, 0, 1));
    }

    /** Frames of the plan growing: each element at its step, roads drawn along, plots fading in, footprints popping. */
    private int planFrames(Path out, int first) throws IOException {
        frame();
        BufferedImage background = background();
        int steps = 0;
        for (int k = 0; k < plan.roads().size(); k++) {
            steps = Math.max(steps, plan.order(0, k) + 1);
        }
        for (int k = 0; k < plan.plots().size(); k++) {
            steps = Math.max(steps, plan.order(1, k) + 1);
        }
        for (int k = 0; k < plan.buildings().size(); k++) {
            steps = Math.max(steps, plan.order(2, k) + 1);
        }
        int frames = (int) ((GROWTH + HOLD) * FPS);
        Font title = new Font(Font.SANS_SERIF, Font.BOLD, 26);
        Font label = new Font(Font.SANS_SERIF, Font.PLAIN, 19);
        for (int f = 0; f < frames; f++) {
            double t = f / FPS;
            // step k appears at 0.6 + k / steps of the growth
            double now = (t - 0.6) / (GROWTH - 1.6) * steps;
            BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frame.createGraphics();
            g.drawImage(background, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // the plan's area
            g.setColor(new Color(255, 255, 255, 60));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] {6, 6}, 0));
            double half = plan.parameters().grid().extent() / 2;
            g.drawRect((int) px(ORIGIN_X + half, ORIGIN_Z - half), (int) py(ORIGIN_X + half, ORIGIN_Z - half),
                (int) (2 * half * scale()), (int) (2 * half * scale()) - 1);
            String stage = "";
            for (Settlement.Plot plot : plan.plots()) {
                double a = Math.clamp((now - plan.order(1, plot.id())) / 3, 0, 1);
                if (a > 0) {
                    Path2D path = polygon(plot.x(), plot.z());
                    g.setColor(new Color(235, 215, 140, (int) (85 * a)));
                    g.fill(path);
                    g.setColor(new Color(250, 235, 160, (int) (220 * a)));
                    g.setStroke(new BasicStroke(1f));
                    g.draw(path);
                    if (a < 1) {
                        stage = "plots: parcels along the roads, off flood-prone and steep ground";
                    }
                }
            }
            if (now >= 0) {
                double[] c = plan.square().corners();
                g.setColor(new Color(185, 180, 172));
                g.fill(polygon(new double[] {c[0], c[2], c[4], c[6]}, new double[] {c[1], c[3], c[5], c[7]}));
            }
            for (Settlement.Road r : plan.roads()) {
                double a = Math.clamp((now - plan.order(0, r.id())) / 2.5, 0, 1);
                if (a <= 0) {
                    continue;
                }
                g.setColor(switch (r.surface()) {
                    case COBBLES -> new Color(165, 160, 152);
                    case GRAVEL -> new Color(232, 220, 190);
                    case DIRT -> new Color(160, 115, 70);
                });
                g.setStroke(new BasicStroke((float) Math.max(r.kind() == RoadKind.FOOTPATH ? 2 : 3,
                    r.width() * scale()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(partial(r, a));
                if (a < 1) {
                    stage = switch (r.kind()) {
                        case MAIN -> "the main road: least-cost paths along the valley";
                        case LANE -> "village lanes from the square";
                        case TRACK -> "farm tracks out to the farmsteads";
                        case FOOTPATH -> "footpaths: switchbacks on steep ground";
                    };
                }
            }
            for (Settlement.Fence fence : plan.fences()) {
                if (now < steps) {
                    continue;
                }
                double a = Math.clamp((t - (GROWTH - 1)) / 0.8, 0, 1);
                g.setColor(new Color(90, 55, 25, (int) (230 * a)));
                g.setStroke(new BasicStroke(1f));
                g.draw(line(fence.x(), fence.z()));
            }
            for (Settlement.Building b : plan.buildings()) {
                double a = Math.clamp((now - plan.order(2, b.id())) / 1.5, 0, 1);
                if (a <= 0) {
                    continue;
                }
                double[] k = b.corners();
                Path2D path = polygon(new double[] {k[0], k[2], k[4], k[6]}, new double[] {k[1], k[3], k[5], k[7]});
                double cx = px(b.x(), b.z());
                double cy = py(b.x(), b.z());
                double s = 0.4 + 0.6 * a;
                Graphics2D gb = (Graphics2D) g.create();
                gb.transform(AffineTransform.getTranslateInstance(cx, cy));
                gb.transform(AffineTransform.getScaleInstance(s, s));
                gb.transform(AffineTransform.getTranslateInstance(-cx, -cy));
                gb.setColor(switch (b.spec().kind()) {
                    case CHURCH -> new Color(245, 245, 240);
                    case INN -> new Color(225, 95, 60);
                    case HOUSE -> new Color(170, 72, 50);
                    case FARMHOUSE -> new Color(140, 95, 60);
                    case HAY_BARN -> new Color(105, 72, 40);
                });
                gb.fill(path);
                gb.dispose();
                if (a < 1) {
                    stage = b.spec().kind() == BuildingKind.HAY_BARN && b.plot() < 0
                        ? "hay barns on the meadows, each with its path"
                        : "footprints facing the valley and the sun";
                }
            }
            if (now >= steps) {
                for (Settlement.Lamp lamp : plan.lamps()) {
                    g.setColor(new Color(255, 236, 150));
                    g.fillOval((int) px(lamp.x(), lamp.z()) - 2, (int) py(lamp.x(), lamp.z()) - 2, 4, 4);
                }
                stage = "lamps along the lanes, fences round gardens and pastures";
            }
            // captions
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(16, 14, 600, 72, 12, 12);
            g.setColor(Color.WHITE);
            g.setFont(title);
            g.drawString("A village planned on Lauterbrunnen's valley floor", 28, 44);
            g.setFont(label);
            g.setColor(new Color(235, 235, 220));
            g.drawString(stage, 28, 72);
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(WIDTH - 118, HEIGHT - 50, 102, 34, 10, 10);
            g.setColor(Color.WHITE);
            g.drawString("← north", WIDTH - 106, HEIGHT - 27);
            g.dispose();
            ImageIO.write(frame, "png", out.resolve(String.format(Locale.ROOT, "f%04d.png", first + f)).toFile());
        }
        return frames;
    }

    private Path2D partial(Settlement.Road r, double share) {
        double length = r.length() * share;
        Path2D path = new Path2D.Double();
        path.moveTo(px(r.x()[0], r.z()[0]), py(r.x()[0], r.z()[0]));
        double s = 0;
        for (int i = 1; i < r.count(); i++) {
            double dx = r.x()[i] - r.x()[i - 1];
            double dz = r.z()[i] - r.z()[i - 1];
            double l = Math.sqrt(dx * dx + dz * dz);
            if (s + l >= length) {
                double u = l > 0 ? (length - s) / l : 0;
                double x = r.x()[i - 1] + u * dx;
                double z = r.z()[i - 1] + u * dz;
                path.lineTo(px(x, z), py(x, z));
                break;
            }
            path.lineTo(px(r.x()[i], r.z()[i]), py(r.x()[i], r.z()[i]));
            s += l;
        }
        return path;
    }

    private Path2D polygon(double[] x, double[] z) {
        Path2D path = line(x, z);
        path.closePath();
        return path;
    }

    private Path2D line(double[] x, double[] z) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < x.length; i++) {
            if (i == 0) {
                path.moveTo(px(x[i], z[i]), py(x[i], z[i]));
            } else {
                path.lineTo(px(x[i], z[i]), py(x[i], z[i]));
            }
        }
        return path;
    }

    // ---------------------------------------------------------------------------------------------- the flight

    /** Triangles with normals and colours, for a vertex-coloured mesh. */
    private static final class Geo {
        final List<float[]> positions = new ArrayList<>();
        final List<float[]> normals = new ArrayList<>();
        final List<float[]> colors = new ArrayList<>();

        /** A flat-shaded triangle facing the side its winding gives (counter-clockwise from the front). */
        void triangle(double[] a, double[] b, double[] c, float[] color) {
            double ux = b[0] - a[0];
            double uy = b[1] - a[1];
            double uz = b[2] - a[2];
            double vx = c[0] - a[0];
            double vy = c[1] - a[1];
            double vz = c[2] - a[2];
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-12) {
                return;
            }
            float[] n = {(float) (nx / l), (float) (ny / l), (float) (nz / l)};
            for (double[] p : new double[][] {a, b, c}) {
                positions.add(new float[] {(float) p[0], (float) p[1], (float) p[2]});
                normals.add(n);
                colors.add(color);
            }
        }

        /** A quad a, b, c, d facing along {@code out} (flipped to face it). */
        void quad(double[] a, double[] b, double[] c, double[] d, double[] out, float[] color) {
            double ux = b[0] - a[0];
            double uy = b[1] - a[1];
            double uz = b[2] - a[2];
            double vx = c[0] - a[0];
            double vy = c[1] - a[1];
            double vz = c[2] - a[2];
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            if (nx * out[0] + ny * out[1] + nz * out[2] >= 0) {
                triangle(a, b, c, color);
                triangle(a, c, d, color);
            } else {
                triangle(a, c, b, color);
                triangle(a, d, c, color);
            }
        }

        MeshData build() {
            int n = positions.size();
            float[] p = new float[3 * n];
            float[] nn = new float[3 * n];
            float[] c = new float[4 * n];
            int[] indices = new int[n];
            for (int i = 0; i < n; i++) {
                System.arraycopy(positions.get(i), 0, p, 3 * i, 3);
                System.arraycopy(normals.get(i), 0, nn, 3 * i, 3);
                System.arraycopy(colors.get(i), 0, c, 4 * i, 4);
                indices[i] = i;
            }
            return new MeshBuilder(VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.COLOR), n).positions(p)
                .attribute(VertexAttribute.NORMAL, nn).attribute(VertexAttribute.COLOR, c).submesh(indices)
                .buildFlat();
        }
    }

    private static float[] color(double r, double g, double b) {
        return new float[] {(float) r, (float) g, (float) b, 1};
    }

    /** A hash of a triangle's place, 0 to 1: the stones' shades. */
    private static double hash(double x, double z) {
        long h = Double.doubleToLongBits(Math.floor(x * 3.1) * 7.13 + Math.floor(z * 3.7) * 1.31) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (h & 0xffff) / 65535.0;
    }

    /** The road tiles as vertex-coloured meshes: the slots' colours, each triangle's shade varied a little. */
    private void roads(Stage stage, int material) {
        float[][] slots = {
            {0.20f, 0.19f, 0.17f}, // cobbles
            {0.42f, 0.38f, 0.31f}, // gravel
            {0.20f, 0.14f, 0.09f}, // dirt
            {0.40f, 0.40f, 0.39f}, // kerbs
            {0.34f, 0.33f, 0.31f}, // steps
        };
        for (RoadMeshes.Tile tile : plan.roadMeshes()) {
            ProceduralMesh mesh = tile.mesh();
            float[] positions = mesh.positions();
            Geo geo = new Geo();
            for (int s = 0; s < RoadMeshes.SLOTS; s++) {
                for (int t = mesh.submeshStart(s); t < mesh.submeshEnd(s); t += 3) {
                    double[][] v = new double[3][];
                    for (int k = 0; k < 3; k++) {
                        int i = mesh.indices()[t + k];
                        v[k] = new double[] {positions[3 * i], positions[3 * i + 1], positions[3 * i + 2]};
                    }
                    double shade = 0.8 + 0.4 * hash(tile.x() + v[0][0] + v[1][0], tile.z() + v[0][2] + v[2][2]);
                    if (s == RoadMeshes.GRAVEL || s == RoadMeshes.DIRT) {
                        shade = 0.92 + 0.16 * shade - 0.16;
                    }
                    float[] c = slots[s];
                    geo.triangle(v[0], v[1], v[2], color(c[0] * shade, c[1] * shade, c[2] * shade));
                }
            }
            if (!geo.positions.isEmpty()) {
                int id = stage.addMesh(geo.build());
                stage.set(stage.id(), id, material, tile.x(), tile.y(), tile.z(), 0, 1, 1, 1);
            }
        }
    }

    /**
     * The terrain: within the plan every metre, graded under the roads and the square, meadow where the ecology keeps
     * it open (and the village's clearings), forest floor elsewhere, bare earth where grass does not grow; beyond the
     * plan every 16 m.
     */
    private void terrain(Stage stage, int material) {
        double extent = plan.parameters().grid().extent();
        double x0 = ORIGIN_X - extent / 2;
        double z0 = ORIGIN_Z - extent / 2;
        int tile = 128;
        int tiles = (int) (extent / tile);
        Site site = new Site();
        for (int tj = 0; tj < tiles; tj++) {
            for (int ti = 0; ti < tiles; ti++) {
                double ox = x0 + ti * tile;
                double oz = z0 + tj * tile;
                int n = tile + 1;
                float[] positions = new float[3 * n * n];
                float[] colors = new float[4 * n * n];
                double[] heights = new double[(n + 2) * (n + 2)];
                for (int j = -1; j <= n; j++) {
                    for (int i = -1; i <= n; i++) {
                        heights[(j + 1) * (n + 2) + i + 1] = graded(ox + i, oz + j);
                    }
                }
                float[] normals = new float[3 * n * n];
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        int a = j * n + i;
                        double x = ox + i;
                        double z = oz + j;
                        double y = heights[(j + 1) * (n + 2) + i + 1];
                        positions[3 * a] = i;
                        positions[3 * a + 1] = (float) y;
                        positions[3 * a + 2] = j;
                        double gx = (heights[(j + 1) * (n + 2) + i + 2] - heights[(j + 1) * (n + 2) + i]) / 2;
                        double gz = (heights[(j + 2) * (n + 2) + i + 1] - heights[j * (n + 2) + i + 1]) / 2;
                        double l = Math.sqrt(gx * gx + gz * gz + 1);
                        normals[3 * a] = (float) (-gx / l);
                        normals[3 * a + 1] = (float) (1 / l);
                        normals[3 * a + 2] = (float) (-gz / l);
                        if ((i & 3) == 0 && (j & 3) == 0 || i == n - 1 || j == n - 1) {
                            ecology.conditions(x, z, site);
                        }
                        double open = Math.max(site.meadow, 1 - plan.clearing().trees(x, z));
                        double grass = plan.clearing().grass(x, z);
                        double steep = Math.clamp((Math.sqrt(gx * gx + gz * gz) - 0.7) / 0.5, 0, 1);
                        double noise = 0.9 + 0.2 * hash(x * 0.37, z * 0.41);
                        double r = (0.05 * open + 0.022 * (1 - open)) * noise;
                        double g = (0.095 * open + 0.035 * (1 - open)) * noise;
                        double b = (0.022 * open + 0.014 * (1 - open)) * noise;
                        // bare earth where grass does not grow: the road's verges under the surfaces
                        r = r * grass + 0.09 * (1 - grass);
                        g = g * grass + 0.07 * (1 - grass);
                        b = b * grass + 0.05 * (1 - grass);
                        r = r * (1 - steep) + 0.12 * steep;
                        g = g * (1 - steep) + 0.115 * steep;
                        b = b * (1 - steep) + 0.105 * steep;
                        double w = site.water;
                        colors[4 * a] = (float) (r * (1 - w) + 0.02 * w);
                        colors[4 * a + 1] = (float) (g * (1 - w) + 0.045 * w);
                        colors[4 * a + 2] = (float) (b * (1 - w) + 0.06 * w);
                        colors[4 * a + 3] = 1;
                    }
                }
                stage.set(stage.id(), stage.addMesh(grid(n, positions, normals, colors, null)), material, ox, 0, oz,
                    0, 1, 1, 1);
            }
        }
        // the valley beyond, every 16 m, lowered a little under the plan
        double far = 8192;
        double fx0 = ORIGIN_X - far / 2;
        double fz0 = ORIGIN_Z - far / 2;
        int cells = 128;
        double step = 16;
        for (int tj = 0; tj < far / (cells * step); tj++) {
            for (int ti = 0; ti < far / (cells * step); ti++) {
                double ox = fx0 + ti * cells * step;
                double oz = fz0 + tj * cells * step;
                int n = cells + 1;
                float[] positions = new float[3 * n * n];
                float[] normals = new float[3 * n * n];
                float[] colors = new float[4 * n * n];
                boolean[] skip = new boolean[n * n];
                double[] normal = new double[3];
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        int a = j * n + i;
                        double x = ox + i * step;
                        double z = oz + j * step;
                        boolean inside = x > x0 + step && x < x0 + extent - step && z > z0 + step
                            && z < z0 + extent - step;
                        skip[a] = inside;
                        positions[3 * a] = (float) (i * step);
                        positions[3 * a + 1] = (float) (height(x, z) - (inside ? 3 : 0));
                        positions[3 * a + 2] = (float) (j * step);
                        surface.normalAt(x, z, step, normal);
                        normals[3 * a] = (float) normal[0];
                        normals[3 * a + 1] = (float) normal[1];
                        normals[3 * a + 2] = (float) normal[2];
                        double steep = Math.clamp((Math.sqrt(1 - normal[1] * normal[1]) / normal[1] - 0.7) / 0.5, 0,
                            1);
                        double alt = Math.clamp((height(x, z) - 1900) / 600, 0, 1);
                        double snow = Math.clamp((height(x, z) - 2600) / 300, 0, 1) * (1 - steep * 0.6);
                        double r = 0.03 * (1 - steep) + 0.12 * steep;
                        double g = 0.06 * (1 - steep) + 0.115 * steep;
                        double b = 0.02 * (1 - steep) + 0.105 * steep;
                        r = r * (1 - alt) + 0.09 * alt;
                        g = g * (1 - alt) + 0.09 * alt;
                        b = b * (1 - alt) + 0.08 * alt;
                        colors[4 * a] = (float) (r * (1 - snow) + 0.75 * snow);
                        colors[4 * a + 1] = (float) (g * (1 - snow) + 0.77 * snow);
                        colors[4 * a + 2] = (float) (b * (1 - snow) + 0.82 * snow);
                        colors[4 * a + 3] = 1;
                    }
                }
                MeshData mesh = grid(n, positions, normals, colors, skip);
                if (mesh != null) {
                    stage.set(stage.id(), stage.addMesh(mesh), material, false, ox, 0, oz, 0, 1, 1, 1);
                }
            }
        }
    }

    /** A grid of {@code n} by {@code n} vertices as a mesh, quads whose corners are all skipped left out. */
    private static MeshData grid(int n, float[] positions, float[] normals, float[] colors, boolean[] skip) {
        int[] indices = new int[6 * (n - 1) * (n - 1)];
        int k = 0;
        for (int j = 0; j < n - 1; j++) {
            for (int i = 0; i < n - 1; i++) {
                int a = j * n + i;
                if (skip != null && skip[a] && skip[a + 1] && skip[a + n] && skip[a + n + 1]) {
                    continue;
                }
                indices[k++] = a;
                indices[k++] = a + n;
                indices[k++] = a + 1;
                indices[k++] = a + 1;
                indices[k++] = a + n;
                indices[k++] = a + n + 1;
            }
        }
        if (k == 0) {
            return null;
        }
        return new MeshBuilder(VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.COLOR), n * n)
            .positions(positions).attribute(VertexAttribute.NORMAL, normals).attribute(VertexAttribute.COLOR, colors)
            .submesh(java.util.Arrays.copyOf(indices, k)).buildFlat();
    }

    /**
     * The buildings as plain blocks (the buildings generator's work stands in for them later): walls from the
     * foundation to the eaves, a gable roof along the ridge the spec asks for; lamps as posts; fences as rails.
     */
    private void furniture(Stage stage, int material) {
        Geo geo = new Geo();
        for (Settlement.Building b : plan.buildings()) {
            BuildingSpec spec = b.spec();
            double hw = spec.width() / 2;
            double hd = spec.depth() / 2;
            double eaves = b.y() + (spec.kind() == BuildingKind.CHURCH ? 9 : spec.kind() == BuildingKind.HAY_BARN
                ? 3.2 : 2.8 * spec.floors() + 0.4);
            double bottom = b.foundation() - 0.3;
            boolean across = spec.ridge() == BuildingSpec.Ridge.ACROSS;
            double pitch = spec.kind() == BuildingKind.CHURCH ? 0.9 : 0.5;
            double rise = (across ? hd : hw) * pitch + 0.3;
            float[] wall = switch (spec.kind()) {
                case CHURCH -> color(0.6, 0.6, 0.58);
                case INN -> color(0.55, 0.45, 0.33);
                case HOUSE, FARMHOUSE -> color(0.24, 0.13, 0.06);
                case HAY_BARN -> color(0.16, 0.10, 0.05);
            };
            float[] roof = spec.kind() == BuildingKind.CHURCH ? color(0.08, 0.08, 0.09) : color(0.13, 0.11, 0.1);
            double[][] base = new double[4][];
            double[][] top = new double[4][];
            double[][] at = {{-hw, -hd}, {hw, -hd}, {hw, hd}, {-hw, hd}};
            double[] q = new double[2];
            for (int k = 0; k < 4; k++) {
                b.toWorld(at[k][0], at[k][1], q);
                base[k] = new double[] {q[0], bottom, q[1]};
                top[k] = new double[] {q[0], eaves, q[1]};
            }
            for (int k = 0; k < 4; k++) {
                int m = (k + 1) % 4;
                double[] out = {(top[k][0] + top[m][0]) / 2 - b.x(), 0, (top[k][2] + top[m][2]) / 2 - b.z()};
                geo.quad(base[k], base[m], top[m], top[k], out, wall);
            }
            // the ridge: along the width (across) or the depth (along), over the eaves' middle
            double[] r0 = new double[3];
            double[] r1 = new double[3];
            double o = 0.7; // the eaves' overhang
            if (across) {
                b.toWorld(-hw - o, 0, q);
                r0 = new double[] {q[0], eaves + rise, q[1]};
                b.toWorld(hw + o, 0, q);
                r1 = new double[] {q[0], eaves + rise, q[1]};
            } else {
                b.toWorld(0, -hd - o, q);
                r0 = new double[] {q[0], eaves + rise, q[1]};
                b.toWorld(0, hd + o, q);
                r1 = new double[] {q[0], eaves + rise, q[1]};
            }
            double[][] eave = new double[4][];
            double[][] grow = {{-hw - o, -hd - o}, {hw + o, -hd - o}, {hw + o, hd + o}, {-hw - o, hd + o}};
            for (int k = 0; k < 4; k++) {
                b.toWorld(grow[k][0], grow[k][1], q);
                eave[k] = new double[] {q[0], eaves - 0.25, q[1]};
            }
            double[] up = {0, 1, 0};
            if (across) {
                geo.quad(eave[0], eave[1], r1, r0, up, roof);
                geo.quad(eave[2], eave[3], r0, r1, up, roof);
                geo.triangle(eave[1], eave[2], r1, wall);
                geo.triangle(eave[3], eave[0], r0, wall);
                geo.triangle(eave[2], eave[1], r1, wall);
                geo.triangle(eave[0], eave[3], r0, wall);
            } else {
                geo.quad(eave[1], eave[2], r1, r0, up, roof);
                geo.quad(eave[3], eave[0], r0, r1, up, roof);
                geo.triangle(eave[0], eave[1], r0, wall);
                geo.triangle(eave[2], eave[3], r1, wall);
                geo.triangle(eave[1], eave[0], r0, wall);
                geo.triangle(eave[3], eave[2], r1, wall);
            }
        }
        float[] post = color(0.03, 0.03, 0.03);
        float[] glow = color(1.6, 1.3, 0.8);
        for (Settlement.Lamp lamp : plan.lamps()) {
            box(geo, lamp.x(), lamp.y() - 0.2, lamp.z(), 0.07, 3.4, post);
            box(geo, lamp.x(), lamp.y() + 3.2, lamp.z(), 0.18, 0.35, glow);
        }
        float[] wood = color(0.13, 0.08, 0.04);
        for (Settlement.Fence fence : plan.fences()) {
            for (int i = 1; i < fence.x().length; i++) {
                double ax = fence.x()[i - 1];
                double az = fence.z()[i - 1];
                double bx = fence.x()[i];
                double bz = fence.z()[i];
                double ay = graded(ax, az);
                double by = graded(bx, bz);
                for (double h : new double[] {0.45, 0.95}) {
                    rail(geo, ax, ay + h, az, bx, by + h, bz, wood);
                }
                box(geo, ax, ay - 0.2, az, 0.06, 1.3, wood);
            }
        }
        // in pieces the geometry pool takes
        stage.set(stage.id(), stage.addMesh(geo.build()), material, 0, 0, 0, 0, 1, 1, 1);
    }

    private static void box(Geo geo, double x, double y, double z, double half, double height, float[] color) {
        double[][] b = {{x - half, y, z - half}, {x + half, y, z - half}, {x + half, y, z + half},
            {x - half, y, z + half}};
        double[][] t = new double[4][];
        for (int k = 0; k < 4; k++) {
            t[k] = new double[] {b[k][0], y + height, b[k][2]};
        }
        for (int k = 0; k < 4; k++) {
            int m = (k + 1) % 4;
            double[] out = {(b[k][0] + b[m][0]) / 2 - x, 0, (b[k][2] + b[m][2]) / 2 - z};
            geo.quad(b[k], b[m], t[m], t[k], out, color);
        }
        geo.quad(t[0], t[1], t[2], t[3], new double[] {0, 1, 0}, color);
    }

    private static void rail(Geo geo, double ax, double ay, double az, double bx, double by, double bz,
        float[] color) {
        double dx = bx - ax;
        double dz = bz - az;
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-6) {
            return;
        }
        double nx = -dz / l * 0.03;
        double nz = dx / l * 0.03;
        double[] a0 = {ax + nx, ay, az + nz};
        double[] a1 = {ax - nx, ay, az - nz};
        double[] b0 = {bx + nx, by, bz + nz};
        double[] b1 = {bx - nx, by, bz - nz};
        double[] a2 = {ax, ay + 0.08, az};
        double[] b2 = {bx, by + 0.08, bz};
        geo.quad(a0, b0, b2, a2, new double[] {nx, 0.5, nz}, color);
        geo.quad(a1, b1, b2, a2, new double[] {-nx, 0.5, -nz}, color);
    }

    /** The main road's centre line, through the square, as points every metre (x, z). */
    private List<double[]> mainRoad() {
        List<double[]> points = new ArrayList<>();
        for (Settlement.Road r : plan.roads()) {
            if (r.kind() != RoadKind.MAIN) {
                continue;
            }
            for (int i = 0; i < r.count(); i++) {
                points.add(new double[] {r.x()[i], r.z()[i]});
            }
        }
        List<double[]> even = new ArrayList<>();
        even.add(points.getFirst());
        for (int k = 1; k < points.size(); k++) {
            double[] a = even.getLast();
            double[] b = points.get(k);
            double l = Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]));
            int n = (int) Math.floor(l);
            for (int i = 1; i <= n; i++) {
                even.add(new double[] {a[0] + (b[0] - a[0]) * i / l, a[1] + (b[1] - a[1]) * i / l});
            }
        }
        // smoothed over 25 m either side: a camera's line, not the road's every bend
        List<double[]> smooth = new ArrayList<>();
        int w = 25;
        for (int k = 0; k < even.size(); k++) {
            double sx = 0;
            double sz = 0;
            int c = 0;
            for (int i = Math.max(0, k - w); i <= Math.min(even.size() - 1, k + w); i++) {
                sx += even.get(i)[0];
                sz += even.get(i)[1];
                c++;
            }
            smooth.add(new double[] {sx / c, sz / c});
        }
        return smooth;
    }

    private int flightFrames(Path out, int first, VulkanDevice device) throws Exception {
        try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 0,
            GeometryPool.Capacity.of(1 << 23, 1 << 20, 512), 1 << 12, 2500)) {
            stage.sun(0.55f, 0.62f, 0.55f, ShadowSpec.cascades(4, 4096, 2500));
            int material = stage.vertexColorMaterial(0.9f);
            long start = System.nanoTime();
            terrain(stage, material);
            roads(stage, material);
            furniture(stage, material);
            System.out.printf(Locale.ROOT, "flight scene in %.1f s%n", (System.nanoTime() - start) / 1e9);
            stage.shade();
            List<double[]> line = mainRoad();
            // start the flight north of the square, toward it, on along the road south
            int square = 0;
            double best = Double.MAX_VALUE;
            for (int k = 0; k < line.size(); k++) {
                double d = Math.hypot(line.get(k)[0] - ORIGIN_X, line.get(k)[1] - ORIGIN_Z);
                if (d < best) {
                    best = d;
                    square = k;
                }
            }
            boolean southward = line.getFirst()[1] < line.getLast()[1];
            int dir = southward ? 1 : -1;
            int from = Math.clamp(square - dir * (int) (FLIGHT * 0.45), 0, line.size() - 1);
            int frames = (int) ((FLIGHT / SPEED + RISE) * FPS);
            for (int f = 0; f < frames; f++) {
                double t = f / FPS;
                double s = Math.min(t, FLIGHT / SPEED) * SPEED;
                int k = Math.clamp(from + dir * (int) s, 0, line.size() - 1);
                int ahead = Math.clamp(k + dir * 45, 0, line.size() - 1);
                double[] p = line.get(k);
                double[] q = line.get(ahead);
                // the camera's height: 7 m over the ground's mean about it, rising at the end
                double ground = 0;
                for (int i = -15; i <= 15; i += 5) {
                    double[] r = line.get(Math.clamp(k + i, 0, line.size() - 1));
                    ground += graded(r[0], r[1]) / 7;
                }
                double u = Math.clamp((t - FLIGHT / SPEED) / RISE, 0, 1);
                u = u * u * (3 - 2 * u);
                double y = ground + 7 + 110 * u;
                double ty = graded(q[0], q[1]) + 1.5 - 60 * u;
                double back = 80 * u;
                double dx = q[0] - p[0];
                double dz = q[1] - p[1];
                double l = Math.max(1e-6, Math.hypot(dx, dz));
                stage.look(p[0] - dx / l * back, y, p[1] - dz / l * back, q[0], ty, q[1], 55);
                stage.frames(f == 0 ? 6 : 1, 1 / FPS);
                write(stage.shot(), out, first + f);
            }
            return frames;
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

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FILMS", matches = "true")
    void aVillageGrowsOnLauterbrunnen() throws Exception {
        setUp();
        Path out = Path.of(System.getProperty("truesim.films", "build/films"), "settlement");
        Files.createDirectories(out);
        int frames = planFrames(out, 0);
        System.out.printf(Locale.ROOT, "%d frames of the plan%n", frames);
        if ("plan".equals(System.getenv("TRUESIM_SETTLEMENT_PART"))) {
            return;
        }
        try (VulkanInstance instance = VulkanInstance.builder().applicationName("settlement film").surfaces(false)
            .build(); VulkanDevice device = VulkanDevice.createHeadless(instance.bestGpu())) {
            System.out.println("settlement film on " + instance.bestGpu().name());
            int flight = flightFrames(out, frames, device);
            System.out.printf(Locale.ROOT, "%d frames of the flight%n", flight);
        }
    }
}
