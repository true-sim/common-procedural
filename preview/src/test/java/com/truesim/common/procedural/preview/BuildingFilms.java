package com.truesim.common.procedural.preview;

import com.truesim.common.math.color.Color;
import com.truesim.common.procedural.assets.SceneMeshes;
import com.truesim.common.procedural.building.Building;
import com.truesim.common.procedural.building.BuildingKind;
import com.truesim.common.procedural.building.BuildingOptions;
import com.truesim.common.procedural.building.BuildingSlot;
import com.truesim.common.procedural.building.BuildingSpec;
import com.truesim.common.procedural.building.Buildings;
import com.truesim.common.procedural.building.Footprint;
import com.truesim.common.procedural.building.GroundHeight;
import com.truesim.common.procedural.building.RoofCover;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.engine.render.GeometryPool;
import com.truesim.engine.render.Material;
import com.truesim.engine.render.TextureFormat;
import com.truesim.engine.render.Textures;
import com.truesim.engine.render.materials.MetallicRoughnessParameters;
import com.truesim.engine.vulkan.VulkanDevice;
import com.truesim.engine.vulkan.VulkanInstance;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.assets.mesh.VertexAttribute;
import com.truesim.scene.assets.mesh.VertexFormat;
import com.truesim.scene.meshbuild.MeshBuilder;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A film of the buildings on a hillside (1280 x 720, 30 frames a second, rendered at twice that and filtered down,
 * PNG frames into {@code build/films/buildings} or {@code -Pfilms=<dir>}): each kind in a slow orbit, a chalet's
 * details close up, the levels of detail side by side, the hamlet. Poly Haven's scans from {@code ~/.truesim/polyhaven}
 * where cached (plain colours where not) are bound to the slots as the showcase binds them. Run with
 * {@code TRUESIM_BUILDING_FILMS=true ./gradlew -Ptruesim.preview=true -Ptruesim.engine=false :preview:test --tests
 * '*BuildingFilms*'}; encode with {@code ffmpeg -framerate 30 -i buildings/f%04d.png -c:v libx264 -pix_fmt yuv420p
 * -crf 22 buildings.mp4}. {@code TRUESIM_BUILDING_STILLS=true} draws one still per kind instead.
 */
class BuildingFilms {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int SUPER = 2;
    private static final Path POLYHAVEN = Path.of(System.getProperty("user.home"), ".truesim", "polyhaven");

    /** A hillside rising to the north (-z), rolling a little. */
    static final GroundHeight HILL = (x, z) -> -0.17 * z + 0.05 * x + 1.1 * Math.sin(x * 0.045) * Math.cos(z * 0.06);

    private VulkanInstance instance;
    private VulkanDevice device;

    @BeforeEach
    void start() {
        instance = VulkanInstance.builder().applicationName("building films").surfaces(false).build();
        device = VulkanDevice.createHeadless(instance.bestGpu());
        System.out.println("building films on " + instance.bestGpu().name());
    }

    @AfterEach
    void stop() {
        device.close();
        instance.close();
    }

    /** A building placed on the stage. */
    record Placed(Building building, int[] meshes, int[] materials) {
    }

    /** The hamlet: where each building stands. */
    static List<Building> hamlet(BuildingOptions options) {
        List<Building> out = new ArrayList<>();
        out.add(Buildings.generate(BuildingSpec.of(BuildingKind.CHALET, new Footprint(0, 0, 0.15, 9.5, 11), 7)
            .withRoof(RoofCover.SHINGLES), HILL, options));
        out.add(Buildings.generate(BuildingSpec.of(BuildingKind.FARMHOUSE, new Footprint(36, 8, -0.12, 12, 17), 3)
            .withRoof(RoofCover.STONE_WEIGHTED), HILL, options));
        out.add(Buildings.generate(BuildingSpec.of(BuildingKind.HAY_BARN, new Footprint(-30, 22, 0.4, 5.5, 6.5), 5)
            .withRoof(RoofCover.STONE_WEIGHTED), HILL, options));
        out.add(Buildings.generate(BuildingSpec.of(BuildingKind.CHURCH, new Footprint(8, -48, 0.05, 9, 18), 2)
            .withRoof(RoofCover.SLATES), HILL, options));
        out.add(Buildings.generate(BuildingSpec.of(BuildingKind.INN, new Footprint(-34, -30, 0.25, 13, 12), 4)
            .withRoof(RoofCover.SHINGLES), HILL, options));
        out.add(Buildings.generate(BuildingSpec.of(BuildingKind.WOODSHED, new Footprint(11, 12, 0.5, 3.5, 5), 6),
            HILL, options));
        out.add(Buildings.generate(BuildingSpec.of(BuildingKind.HAY_BARN, new Footprint(62, 40, -0.3, 5, 6), 9)
            .withRoof(RoofCover.SHINGLES), HILL, options));
        out.add(Buildings.generate(BuildingSpec.of(BuildingKind.HAY_BARN, new Footprint(-58, -6, 0.9, 6, 7), 12),
            HILL, options));
        out.add(Buildings.generate(BuildingSpec.of(BuildingKind.CHALET, new Footprint(-10, -62, -0.1, 9, 10), 21)
            .withRoof(RoofCover.SLATES), HILL, options));
        return out;
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_BUILDING_STILLS", matches = "true")
    void stills() throws IOException {
        film(true);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_BUILDING_FILMS", matches = "true")
    void film() throws IOException {
        film(false);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_GERANIUM_FILM", matches = "true")
    void geraniums() throws IOException {
        stills = false;
        try (Stage stage = stage()) {
            Map<BuildingSlot, Material> slots = slots(stage);
            List<Building> hamlet = hamlet(options());
            List<Placed> placed = place(stage, slots, hamlet, 0);
            stage.shade();
            Path out = ProceduralFilms.folder("geraniums");
            int[] frame = {0};
            Building chalet = placed.get(0).building();
            Building inn = placed.get(4).building();
            // along the chalet's balcony, close: the boxes on the rail, the geraniums trailing over them
            closeUp(stage, out, frame, chalet, 150, 1.0, 1.6, 4.2, 4.0);
            // the inn's front: both balconies and the window boxes, pulling back
            for (int i = 0; i < 150; i++) {
                double t = i / 149.0;
                double e = t * t * (3 - 2 * t);
                Footprint f = inn.spec().footprint();
                double lx = -2 + 4 * e;
                double lz = f.depth() / 2 + 4.5 + 7 * e;
                double ly = 4.2 + 1.5 * e;
                look(stage, inn, lx, ly, lz, lx * 0.5, 4.6, f.depth() / 2, 50 - 8 * e);
                stage.frames(1, 1 / 30.0);
                ProceduralFilms.write(stage.shot(), out, frame[0]++);
            }
            System.out.println("geranium film: " + frame[0] + " frames into " + out);
        }
    }

    /** Along a building's front at {@code distance} from it, from the left to the right, at the balcony's rail. */
    private static void closeUp(Stage stage, Path out, int[] frame, Building b, int frames, double from, double to,
        double distance, double height) throws IOException {
        Footprint f = b.spec().footprint();
        for (int i = 0; i < frames; i++) {
            double t = i / (double) (frames - 1);
            double lx = -f.width() / 2 * from + f.width() * 0.5 * (to - from + 0.8) * t;
            double lz = f.depth() / 2 + distance;
            look(stage, b, lx, height, lz, lx + 1.6, height - 0.3, f.depth() / 2 + 1.0, 42);
            stage.frames(1, 1 / 30.0);
            ProceduralFilms.write(stage.shot(), out, frame[0]++);
        }
    }

    /** The camera at a point of the building's frame looking at another. */
    private static void look(Stage stage, Building b, double ex, double ey, double ez, double tx, double ty, double tz,
        double fov) {
        Footprint f = b.spec().footprint();
        double c = Math.cos(f.yaw());
        double s = Math.sin(f.yaw());
        stage.look(f.x() + ex * c + ez * s, b.floor() + ey, f.z() - ex * s + ez * c, f.x() + tx * c + tz * s,
            b.floor() + ty, f.z() - tx * s + tz * c, fov);
    }

    /** Only the middle frame of every shot, for a look before the film. */
    private static boolean stills;

    private void film(boolean onlyStills) throws IOException {
        stills = onlyStills;
        try (Stage stage = stage()) {
            Map<BuildingSlot, Material> slots = slots(stage);
            BuildingOptions options = options();
            List<Building> hamlet = hamlet(options);
            List<Placed> placed = place(stage, slots, hamlet, 0);
            // the levels of detail side by side, away from the hamlet
            for (int level = 0; level < 4; level++) {
                Building b = Buildings.generate(BuildingSpec.of(BuildingKind.CHALET, new Footprint(-112 + level * 16,
                    78, 0, 9.5, 11), 7).withRoof(RoofCover.SHINGLES), HILL, options);
                place(stage, slots, List.of(b), level);
                System.out.println("level " + level + ": " + b.lods().get(level).triangleCount() + " triangles");
            }
            for (Building b : hamlet) {
                System.out.println(b.spec().kind() + ": " + b.lods().stream().map(m -> m.triangleCount()).toList());
            }
            stage.shade();
            Path out = ProceduralFilms.folder(onlyStills ? "building-stills" : "buildings");
            int[] frame = {0};
            // each kind in a slow orbit
            shot(stage, out, frame, placed.get(0).building(), 210, -0.6, 0.55, 1.0);
            detail(stage, out, frame, placed.get(0).building(), 150);
            shot(stage, out, frame, placed.get(1).building(), 180, -0.4, 0.5, 1.0);
            shot(stage, out, frame, placed.get(2).building(), 165, 0.2, 0.75, 1.2);
            shot(stage, out, frame, placed.get(3).building(), 195, -0.5, 0.45, 1.25);
            shot(stage, out, frame, placed.get(4).building(), 180, -0.3, 0.55, 1.0);
            shot(stage, out, frame, placed.get(5).building(), 135, -0.6, 0.6, 1.5);
            // the levels: 0 to 3 left to right
            for (int f = 0; f < 120; f++) {
                if (skip(f, 120)) {
                    continue;
                }
                double t = f / 119.0;
                double cx = -112 + 24 + 2 * t;
                double cz = 78;
                double y = HILL.height(cx, cz) + 7;
                stage.look(cx - 6, y + 2, cz + 46, cx, y - 1, cz, 34);
                stage.frames(stills ? 2 : 1, 1 / 30.0);
                ProceduralFilms.write(stage.shot(), out, frame[0]++);
            }
            // the hamlet from above, slowly
            for (int f = 0; f < 180; f++) {
                if (skip(f, 180)) {
                    continue;
                }
                double t = f / 179.0;
                double a = -0.9 + 0.6 * t;
                double cx = 5;
                double cz = -10;
                double r = 110;
                double ex = cx + r * Math.sin(a);
                double ez = cz + r * Math.cos(a);
                stage.look(ex, HILL.height(ex, ez) + 38, ez, cx, HILL.height(cx, cz) + 2, cz, 42);
                stage.frames(stills ? 2 : 1, 1 / 30.0);
                ProceduralFilms.write(stage.shot(), out, frame[0]++);
            }
            System.out.println("building film: " + frame[0] + " frames into " + out);
        }
    }

    /** Whether frame {@code f} of {@code n} is left out (for stills, all but the middle one). */
    private static boolean skip(int f, int n) {
        return stills && f != n / 2;
    }

    private static BuildingOptions options() {
        // the showcase's scans' real sizes (Poly Haven's dimensions)
        return BuildingOptions.DEFAULTS.withRepeat(BuildingSlot.SHINGLES, 3).withRepeat(BuildingSlot.SLATES, 3)
            .withRepeat(BuildingSlot.TIMBER, 2).withRepeat(BuildingSlot.TRIM, 2).withRepeat(BuildingSlot.DOOR, 2)
            .withRepeat(BuildingSlot.FIREWOOD, 2);
    }

    private Stage stage() {
        return new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 0, GeometryPool.Capacity.of(1 << 23, 1 << 19, 128),
            1 << 12, 120).frontLit();
    }

    /** An orbit about the building's middle: {@code frames} long, starting at angle {@code a0} from its front. */
    private static void shot(Stage stage, Path out, int[] frame, Building b, int frames, double a0, double sweep,
        double distance) throws IOException {
        for (int f = 0; f < frames; f++) {
            if (skip(f, frames)) {
                continue;
            }
            double t = f / (double) (frames - 1);
            double eased = t * t * (3 - 2 * t) * 0.6 + t * 0.4;
            orbitAt(stage, b, a0 + sweep * eased, distance);
            stage.frames(stills ? 2 : 1, 1 / 30.0);
            ProceduralFilms.write(stage.shot(), out, frame[0]++);
        }
    }

    private static void orbit(Stage stage, Building b, double angle, double distance) {
        orbitAt(stage, b, angle, distance);
    }

    private static void orbitAt(Stage stage, Building b, double angle, double distance) {
        Footprint f = b.spec().footprint();
        double size = Math.max(f.width(), f.depth());
        double height = b.lods().getFirst().bounds().max().y();
        double r = (size * 1.35 + 9) * distance;
        double yaw = f.yaw() + angle;
        double ex = f.x() + r * Math.sin(yaw);
        double ez = f.z() + r * Math.cos(yaw);
        double ty = b.floor() + Math.min(height * 0.42, 6);
        double ey = Math.max(HILL.height(ex, ez) + 1.7, ty + r * 0.18);
        stage.look(ex, ey, ez, f.x(), ty, f.z(), b.spec().kind() == BuildingKind.CHURCH ? 46 : 40);
    }

    /** Close up along the chalet's front: the balcony's cut-out boards, log ends, windows, shutters, flowers. */
    private static void detail(Stage stage, Path out, int[] frame, Building b, int frames) throws IOException {
        Footprint f = b.spec().footprint();
        for (int i = 0; i < frames; i++) {
            if (skip(i, frames)) {
                continue;
            }
            double t = i / (double) (frames - 1);
            double lx = -f.width() / 2 - 1.5 + (f.width() * 0.7) * t;
            double lz = f.depth() / 2 + 5.2;
            double ly = 3.4;
            double tx = lx + 2.2;
            double tz = f.depth() / 2;
            double ty = 3.2;
            double c = Math.cos(f.yaw());
            double s = Math.sin(f.yaw());
            stage.look(f.x() + lx * c + lz * s, b.floor() + ly, f.z() - lx * s + lz * c, f.x() + tx * c + tz * s,
                b.floor() + ty, f.z() - tx * s + tz * c, 44);
            stage.frames(stills ? 2 : 1, 1 / 30.0);
            ProceduralFilms.write(stage.shot(), out, frame[0]++);
        }
    }

    /** Places the buildings (their level {@code level}) and the hillside. */
    private static List<Placed> place(Stage stage, Map<BuildingSlot, Material> slots, List<Building> buildings,
        int level) {
        if (level == 0 && buildings.size() > 1) {
            stage.set(stage.id(), stage.addMesh(hillside(340, 2)), stage.materials.add(ground(stage)), true, 0, 0, 0, 0,
                1, 1, 1);
        }
        List<Placed> out = new ArrayList<>();
        for (Building b : buildings) {
            ProceduralMesh mesh = b.lods().get(level);
            int material = -1;
            for (BuildingSlot slot : BuildingSlot.all()) {
                if (SceneMeshes.kept(mesh, slot.ordinal())) {
                    int id = stage.materials.add(slots.get(slot));
                    material = material < 0 ? id : material;
                }
            }
            int id = stage.addMesh(SceneMeshes.build(mesh, false));
            Footprint f = b.spec().footprint();
            stage.scene.set(stage.id(), id, material, f.x(), b.floor(), f.z(), 0, (float) Math.sin(f.yaw() / 2), 0,
                (float) Math.cos(f.yaw() / 2), 1, 1, 1);
            out.add(new Placed(b, new int[] {id}, new int[] {material}));
        }
        return out;
    }

    // ---------------------------------------------------------------- materials

    private static final Map<String, int[]> LOADED = new HashMap<>();

    /** The slots' materials: Poly Haven's scans where cached, as the showcase binds them. */
    private static Map<BuildingSlot, Material> slots(Stage stage) {
        Map<BuildingSlot, Material> m = new HashMap<>();
        m.put(BuildingSlot.STONE, scan(stage, "old_stone_wall", 1, 1, 1, 1));
        m.put(BuildingSlot.PLASTER, scan(stage, "white_plaster_02", 0.97f, 0.95f, 0.9f, 1));
        m.put(BuildingSlot.TIMBER, scan(stage, "wood_cabinet_worn_long", 0.8f, 0.72f, 0.66f, 1));
        m.put(BuildingSlot.TRIM, scan(stage, "rough_wood", 0.85f, 0.75f, 0.65f, 1));
        m.put(BuildingSlot.SHINGLES, scan(stage, "roof_slates_02", 0.95f, 0.88f, 0.8f, 1));
        m.put(BuildingSlot.SLATES, scan(stage, "roof_slates_03", 1, 1, 1, 1));
        m.put(BuildingSlot.INTERIOR, scan(stage, "white_plaster_02", 0.98f, 0.9f, 0.78f, 1));
        m.put(BuildingSlot.FLOOR, scan(stage, "plank_flooring_02", 1, 1, 1, 0.7f));
        m.put(BuildingSlot.GLASS, plain(0.02f, 0.025f, 0.03f, 0.05f, 0));
        m.put(BuildingSlot.WINDOW, plain(0.03f, 0.035f, 0.04f, 0.15f, 0));
        m.put(BuildingSlot.HEARTH, scan(stage, "granite_wall", 0.6f, 0.6f, 0.6f, 1));
        m.put(BuildingSlot.SHUTTERS, scan(stage, "rough_wood", 0.35f, 0.95f, 0.5f, 1));
        m.put(BuildingSlot.DOOR, scan(stage, "weathered_planks", 1.1f, 0.95f, 0.85f, 1));
        m.put(BuildingSlot.METAL, plain(0.85f, 0.65f, 0.3f, 0.3f, 1));
        m.put(BuildingSlot.FIREWOOD, scan(stage, "bark_brown_02", 1, 1, 1, 1));
        // the geraniums: colored from their palette, leaves and petals single sheets
        int palette = stage.texture(Looks.palette(com.truesim.common.procedural.building.Geraniums.palette()));
        m.put(BuildingSlot.FOLIAGE, new Material(MetallicRoughnessParameters.DEFAULTS.withBaseColorTexture(palette)
            .withRoughness(0.6f).withMetallic(0)).doubleSided(true));
        m.put(BuildingSlot.FLOWERS, new Material(MetallicRoughnessParameters.DEFAULTS.withBaseColorTexture(palette)
            .withRoughness(0.55f).withMetallic(0)).doubleSided(true));
        return m;
    }

    private static Material ground(Stage stage) {
        return scan(stage, "sparse_grass", 0.82f, 1.15f, 1.0f, 1);
    }

    private static Material plain(float r, float g, float b, float roughness, float metallic) {
        return new Material(MetallicRoughnessParameters.DEFAULTS.withBaseColor(new Color(r, g, b, 1))
            .withRoughness(roughness).withMetallic(metallic));
    }

    /** A scanned surface: base colour (tinted), packed occlusion-roughness-metalness, OpenGL normals. */
    private static Material scan(Stage stage, String asset, float r, float g, float b, float roughness) {
        int[] t = LOADED.computeIfAbsent(asset, a -> load(stage, a));
        if (t == null) {
            return plain(0.4f * r, 0.36f * g, 0.32f * b, 0.85f, 0);
        }
        return new Material(MetallicRoughnessParameters.DEFAULTS.withBaseColor(new Color(r, g, b, 1))
            .withBaseColorTexture(t[0]).withOrmTexture(t[1]).withNormalTexture(t[2]).withRoughness(roughness)
            .withMetallic(1));
    }

    private static int[] load(Stage stage, String asset) {
        File dir = POLYHAVEN.resolve(asset).toFile();
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        File diff = null;
        File arm = null;
        File nor = null;
        for (File f : files) {
            String n = f.getName();
            if (n.contains("_diff_")) {
                diff = f;
            } else if (n.contains("_arm_")) {
                arm = f;
            } else if (n.contains("_nor_gl_")) {
                nor = f;
            }
        }
        if (diff == null || arm == null || nor == null) {
            return null;
        }
        try {
            return new int[] {texture(stage, diff, TextureFormat.RGBA8_SRGB), texture(stage, arm, TextureFormat.RGBA8),
                texture(stage, nor, TextureFormat.RGBA8)};
        } catch (IOException e) {
            return null;
        }
    }

    private static int texture(Stage stage, File file, TextureFormat format) throws IOException {
        BufferedImage source = ImageIO.read(file);
        int size = Math.min(2048, source.getWidth());
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        MemorySegment texels = Arena.ofAuto().allocate(4L * size * size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int p = image.getRGB(x, y);
                long at = 4L * (y * (long) size + x);
                texels.set(ValueLayout.JAVA_BYTE, at, (byte) (p >> 16));
                texels.set(ValueLayout.JAVA_BYTE, at + 1, (byte) (p >> 8));
                texels.set(ValueLayout.JAVA_BYTE, at + 2, (byte) p);
                texels.set(ValueLayout.JAVA_BYTE, at + 3, (byte) 255);
            }
        }
        return stage.textures.add(format, size, size, Textures.GENERATE, texels);
    }

    // ---------------------------------------------------------------- the hillside

    /** The hillside, {@code size} metres across in cells of {@code cell}, in 2 m texture repeats, each cell its own. */
    static MeshData hillside(double size, double cell) {
        int n = (int) Math.round(size / cell);
        int quads = n * n;
        float[] positions = new float[quads * 4 * 3];
        float[] normals = new float[quads * 4 * 3];
        float[] tangents = new float[quads * 4 * 4];
        float[] uvs = new float[quads * 4 * 2];
        int[] indices = new int[quads * 6];
        int v = 0;
        int k = 0;
        double repeat = 2.0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double x0 = -size / 2 + i * cell;
                double z0 = -size / 2 + j * cell;
                double ou = Math.floor(x0 / repeat);
                double ov = Math.floor(z0 / repeat);
                int base = v;
                double[][] corners = {{x0, z0}, {x0, z0 + cell}, {x0 + cell, z0 + cell}, {x0 + cell, z0}};
                for (double[] c : corners) {
                    double h = HILL.height(c[0], c[1]);
                    double e = 0.25;
                    double nx = -(HILL.height(c[0] + e, c[1]) - HILL.height(c[0] - e, c[1])) / (2 * e);
                    double nz = -(HILL.height(c[0], c[1] + e) - HILL.height(c[0], c[1] - e)) / (2 * e);
                    double l = Math.sqrt(nx * nx + 1 + nz * nz);
                    positions[3 * v] = (float) c[0];
                    positions[3 * v + 1] = (float) h;
                    positions[3 * v + 2] = (float) c[1];
                    normals[3 * v] = (float) (nx / l);
                    normals[3 * v + 1] = (float) (1 / l);
                    normals[3 * v + 2] = (float) (nz / l);
                    tangents[4 * v] = 1;
                    tangents[4 * v + 3] = 1;
                    uvs[2 * v] = (float) (c[0] / repeat - ou);
                    uvs[2 * v + 1] = (float) (c[1] / repeat - ov);
                    v++;
                }
                int[] quad = {base, base + 1, base + 2, base, base + 2, base + 3};
                System.arraycopy(quad, 0, indices, k, 6);
                k += 6;
            }
        }
        return new MeshBuilder(VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.TANGENT, VertexAttribute.UV0),
            v).positions(positions).attribute(VertexAttribute.NORMAL, normals)
            .attribute(VertexAttribute.TANGENT, tangents).attribute(VertexAttribute.UV0, uvs).submesh(indices)
            .buildFlat();
    }

    static {
        try {
            Files.createDirectories(Path.of("build"));
        } catch (IOException ignored) {
            // the films' folder is made when written
        }
    }
}
