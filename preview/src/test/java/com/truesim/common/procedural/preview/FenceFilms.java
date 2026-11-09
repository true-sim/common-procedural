package com.truesim.common.procedural.preview;

import com.truesim.common.math.color.Color;
import com.truesim.common.math.random.Noise;
import com.truesim.common.procedural.assets.SceneMeshes;
import com.truesim.common.procedural.fence.Fence;
import com.truesim.common.procedural.fence.FenceGround;
import com.truesim.common.procedural.fence.FenceMaterial;
import com.truesim.common.procedural.fence.FenceOptions;
import com.truesim.common.procedural.fence.FenceType;
import com.truesim.common.procedural.fence.Fences;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.engine.render.Material;
import com.truesim.engine.render.materials.MetallicRoughnessParameters;
import com.truesim.engine.vulkan.VulkanDevice;
import com.truesim.engine.vulkan.VulkanInstance;
import com.truesim.scene.assets.mesh.VertexAttribute;
import com.truesim.scene.assets.mesh.VertexFormat;
import com.truesim.scene.meshbuild.MeshBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A film of the procedural fences through the engine: every type, in every material, one after the other along a
 * curving line up a hillside, the sun low, the camera orbiting slowly. PNG frames at 30 per second (1280 x 720,
 * rendered at twice that) into {@code build/films/fences}. Run with
 * {@code TRUESIM_FILMS=true ./gradlew -Ptruesim.preview=true :preview:test --tests '*FenceFilms*'}; encode with
 * {@code ffmpeg -framerate 30 -i fences/f%04d.png -c:v libx264 -pix_fmt yuv420p -crf 22 fences.mp4}.
 */
class FenceFilms {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int SUPER = 2;

    /** A hillside rising to the north-east, with swells. */
    static final FenceGround HILL = (x, z) -> 0.2 * x - 0.1 * z + 0.45 * Math.sin(0.23 * x + 0.4)
        * Math.cos(0.19 * z) + 0.12 * Math.sin(0.7 * x + 0.5 * z);

    /** What each stretch of the line shows: a type, its options. */
    record Stretch(FenceType type, FenceOptions options) {
    }

    static List<Stretch> stretches() {
        FenceOptions o = FenceOptions.DEFAULTS.withDamage(0.06);
        return List.of(
            new Stretch(FenceType.POST_AND_RAIL, o.withWood(FenceMaterial.WEATHERED_SPRUCE).withWeathering(0.85)
                .withMoss(0.5).withGates(List.of(3.6), 1.2)),
            new Stretch(FenceType.POST_AND_THREE_RAILS, o.withWood(FenceMaterial.FRESH_WOOD).withWeathering(0.05)
                .withLichen(0).withMoss(0.05)),
            new Stretch(FenceType.SCHAERHAG, o.withWood(FenceMaterial.WEATHERED_LARCH).withWeathering(0.9)
                .withLichen(0.45).withDamage(0.12)),
            new Stretch(FenceType.PICKET, o.withWood(FenceMaterial.PAINTED_WHITE).withWeathering(0.55)
                .withDamage(0.15)),
            new Stretch(FenceType.WATTLE, o.withWood(FenceMaterial.FRESH_WOOD).withWeathering(0.4).withLichen(0.1)),
            new Stretch(FenceType.BOARD, o.withWood(FenceMaterial.PAINTED_RED).withWeathering(0.6).withDamage(0.1)),
            new Stretch(FenceType.STANGENZAUN, o.withWood(FenceMaterial.WEATHERED_LARCH).withWeathering(0.95)
                .withLichen(0.6).withMoss(0.4)),
            new Stretch(FenceType.DRY_STONE, o.withStone(FenceMaterial.GNEISS).withMoss(0.75).withLichen(0.6)
                .withDamage(0.25)),
            new Stretch(FenceType.WIRE, o.withWood(FenceMaterial.WEATHERED_SPRUCE).withWeathering(0.5)));
    }

    /** A spiral from radius 8.5 to 14 m: the stretch {@code i} of {@code n}, points every half metre. */
    static double[] spiral(int i, int n, double gap) {
        double turns = 1.3;
        double total = 0;
        // its length by sampling
        int samples = 4000;
        double[] arc = new double[samples + 1];
        double px = 0;
        double pz = 0;
        for (int k = 0; k <= samples; k++) {
            double t = k / (double) samples;
            double a = 2 * Math.PI * turns * t;
            double r = 6 + 4.5 * t;
            double x = r * Math.cos(a);
            double z = r * Math.sin(a);
            if (k > 0) {
                total += Math.hypot(x - px, z - pz);
            }
            arc[k] = total;
            px = x;
            pz = z;
        }
        double each = (total - (n - 1) * gap) / n;
        double from = i * (each + gap);
        double to = from + each;
        List<Double> out = new ArrayList<>();
        int steps = Math.max(2, (int) Math.ceil((to - from) / 0.5));
        int k = 0;
        for (int step = 0; step <= steps; step++) {
            double s = from + (to - from) * step / steps;
            while (k < samples - 1 && arc[k + 1] < s) {
                k++;
            }
            double t = (k + (s - arc[k]) / Math.max(1e-12, arc[k + 1] - arc[k])) / samples;
            double a = 2 * Math.PI * turns * t;
            double r = 6 + 4.5 * t;
            out.add(r * Math.cos(a));
            out.add(r * Math.sin(a));
        }
        double[] line = new double[out.size()];
        for (int q = 0; q < line.length; q++) {
            line[q] = out.get(q);
        }
        return line;
    }

    /** The hillside, its fences and their materials on {@code stage}; returns each stretch's middle (x, z). */
    static double[][] setUp(Stage stage) {
        stage.sun(-0.55f, 0.42f, -0.5f, com.truesim.scene.core.ShadowSpec.cascades(4, 2048, 60));
        int ground = stage.addMesh(hillside(600, 2));
        int grass = stage.materials.add(new Material(MetallicRoughnessParameters.DEFAULTS
            .withBaseColorTexture(stage.texture(grassTexture())).withRoughness(0.95f).withMetallic(0)));
        stage.set(stage.id(), ground, grass, false, 0, 0, 0, 0, 1, 1, 1); // casting no shadow: no acne on the slopes
        // the materials: wood, stone, metal one after the other (and wood, metal), tinted by the vertex colors
        int woodTexture = stage.texture(woodTexture());
        int stoneTexture = stage.texture(stoneTexture());
        int wood = wood(stage, woodTexture);
        stage.materials.add(stone(stoneTexture));
        stage.materials.add(metal());
        int woodThenMetal = wood(stage, woodTexture);
        stage.materials.add(metal());
        List<Stretch> all = stretches();
        double[][] middles = new double[all.size()][];
        int triangles = 0;
        for (int i = 0; i < all.size(); i++) {
            Stretch stretch = all.get(i);
            double[] line = spiral(i, all.size(), 1.2);
            int m = line.length / 4 * 2;
            middles[i] = new double[] {line[m], line[m + 1]};
            Fence fence = Fences.generate(stretch.type(), stretch.options(), line, HILL, 40 + i);
            ProceduralMesh mesh = fence.lods().getFirst();
            triangles += mesh.triangleCount();
            boolean woodPart = SceneMeshes.kept(mesh, Fence.WOOD);
            boolean stonePart = SceneMeshes.kept(mesh, Fence.STONE);
            boolean metalPart = SceneMeshes.kept(mesh, Fence.METAL);
            int material = woodPart && metalPart ? woodThenMetal : woodPart ? wood : stonePart ? wood + 1 : wood + 2;
            int id = stage.addMesh(SceneMeshes.build(mesh, true, false));
            stage.scene.set(stage.id(), id, material, fence.origin().x(), fence.origin().y(), fence.origin().z(), 0,
                0, 0, 1, 1, 1, 1);
            System.out.println(stretch.type().name() + ": " + mesh.triangleCount() + " triangles, levels "
                + fence.lods().stream().map(l -> l.triangleCount() + "").toList());
        }
        System.out.println("fences: " + triangles + " triangles at level 0");
        stage.shade();
        return middles;
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FILMS", matches = "true")
    void fencesUpAHillside() throws IOException {
        Path out = ProceduralFilms.folder("fences");
        try (VulkanInstance instance = VulkanInstance.builder().applicationName("fence films").surfaces(false).build();
             VulkanDevice device = VulkanDevice.createHeadless(instance.bestGpu());
             Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 0, 60)) {
            setUp(stage);
            int frames = Integer.parseInt(System.getenv().getOrDefault("TRUESIM_FENCE_FRAMES", "720"));
            for (int f = 0; f < frames; f++) {
                double turn = frames < 720 ? f / (double) frames : f / 720.0;
                double a = Math.toRadians(-100) + 2 * Math.PI * turn;
                double distance = 15.5 + Math.sin(2 * Math.PI * turn);
                double x = distance * Math.cos(a);
                double z = distance * Math.sin(a);
                double y = Math.max(HILL.height(x, z) + 2.2, HILL.height(0, 0) + 2.8);
                stage.look(x, y, z, 0.3 * x, HILL.height(0.3 * x, 0.3 * z) + 0.4, 0.3 * z, 48);
                stage.frames(f == 0 ? 4 : 1, 1 / 30.0);
                ProceduralFilms.write(stage.shot(), out, f);
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_SCREENSHOTS", matches = "true")
    void closeUps() throws IOException {
        Path out = ProceduralFilms.folder("fence-close-ups");
        try (VulkanInstance instance = VulkanInstance.builder().applicationName("fence stills").surfaces(false).build();
             VulkanDevice device = VulkanDevice.createHeadless(instance.bestGpu());
             Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 0, 60)) {
            double[][] middles = setUp(stage);
            for (int i = 0; i < middles.length; i++) {
                double mx = middles[i][0];
                double mz = middles[i][1];
                double l = Math.hypot(mx, mz);
                double x = mx - 4.2 * mx / l + 1.2 * mz / l;
                double z = mz - 4.2 * mz / l - 1.2 * mx / l;
                stage.look(x, HILL.height(x, z) + 1.7, z, mx, HILL.height(mx, mz) + 0.5, mz, 50);
                stage.frames(i == 0 ? 4 : 2, 1 / 30.0);
                ProceduralFilms.write(stage.shot(), out, i);
            }
        }
    }

    private static int wood(Stage stage, int texture) {
        return stage.materials.add(new Material(MetallicRoughnessParameters.DEFAULTS.withBaseColorTexture(texture)
            .withRoughness(0.82f).withMetallic(0)));
    }

    private static Material stone(int texture) {
        return new Material(MetallicRoughnessParameters.DEFAULTS.withBaseColorTexture(texture).withRoughness(0.9f)
            .withMetallic(0));
    }

    private static Material metal() {
        return new Material(MetallicRoughnessParameters.DEFAULTS.withBaseColor(new Color(1, 1, 1, 1))
            .withRoughness(0.42f).withMetallic(1));
    }

    /** The hillside: {@code size} metres square, {@code cell} metres a quad, normals from the slope. */
    static com.truesim.scene.assets.mesh.MeshData hillside(double size, double cell) {
        int n = (int) Math.round(size / cell);
        int count = (n + 1) * (n + 1);
        float[] positions = new float[3 * count];
        float[] normals = new float[3 * count];
        float[] uvs = new float[2 * count];
        for (int j = 0; j <= n; j++) {
            for (int i = 0; i <= n; i++) {
                int v = j * (n + 1) + i;
                double x = -0.5 * size + i * cell;
                double z = -0.5 * size + j * cell;
                positions[3 * v] = (float) x;
                positions[3 * v + 1] = (float) backdrop(x, z);
                positions[3 * v + 2] = (float) z;
                double e = 0.05;
                double dx = (backdrop(x + e, z) - backdrop(x - e, z)) / (2 * e);
                double dz = (backdrop(x, z + e) - backdrop(x, z - e)) / (2 * e);
                double l = Math.sqrt(dx * dx + 1 + dz * dz);
                normals[3 * v] = (float) (-dx / l);
                normals[3 * v + 1] = (float) (1 / l);
                normals[3 * v + 2] = (float) (-dz / l);
                uvs[2 * v] = (float) (x / 3);
                uvs[2 * v + 1] = (float) ((z + backdrop(x, z)) / 3);
            }
        }
        int[] indices = new int[6 * n * n];
        int at = 0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int a = j * (n + 1) + i;
                int[] quad = {a, a + n + 1, a + 1, a + 1, a + n + 1, a + n + 2};
                System.arraycopy(quad, 0, indices, at, 6);
                at += 6;
            }
        }
        return new MeshBuilder(VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.UV0), count)
            .positions(positions).attribute(VertexAttribute.NORMAL, normals).attribute(VertexAttribute.UV0, uvs)
            .submesh(indices).buildFlat();
    }

    /** The hillside where the fences are, rising into a valley's sides beyond 40 m: the film's backdrop. */
    static double backdrop(double x, double z) {
        double d = Math.max(0, Math.hypot(x, z) - 40);
        double w = Math.clamp((Math.hypot(x, z) - 25) / 20, 0, 1); // swells near, a smooth valley side beyond
        return (1 - w) * HILL.height(x, z) + w * (0.2 * x - 0.1 * z) + 0.0012 * d * d;
    }

    /** Grey-scale wood: fibres along v, a little lighter than mid grey on average (the vertex colors carry the hue). */
    static BufferedImage woodTexture() {
        Noise noise = Noise.seeded(5);
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                double u = x / 256.0;
                double v = y / 256.0;
                double fibre = noise.fractal(u * 40, v * 3, 3) * 0.5 + noise.simplex(u * 90, v * 1.5) * 0.25;
                double crack = Math.max(0, Math.abs(noise.simplex(u * 14, v * 0.8)) < 0.04 ? 0.35 : 0);
                double k = Math.clamp(0.93 + 0.18 * fibre - crack, 0.4, 1);
                image.setRGB(x, y, 0xff000000 | gray(k));
            }
        }
        return image;
    }

    /** Grey-scale stone: speckled grains and darker veins. */
    static BufferedImage stoneTexture() {
        Noise noise = Noise.seeded(6);
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                double u = x / 256.0;
                double v = y / 256.0;
                double grain = noise.simplex(u * 120, v * 120) * 0.12 + noise.fractal(u * 8, v * 8, 4) * 0.2;
                double band = Math.abs(noise.simplex(u * 3 + v * 6, v * 2)) < 0.05 ? -0.15 : 0;
                image.setRGB(x, y, 0xff000000 | gray(Math.clamp(0.92 + grain + band, 0.4, 1)));
            }
        }
        return image;
    }

    /** Alpine pasture: green, flecked. */
    static BufferedImage grassTexture() {
        Noise noise = Noise.seeded(7);
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                double u = x / 256.0;
                double v = y / 256.0;
                double k = 0.5 * noise.simplex(u * 60, v * 60) + 0.5 * noise.fractal(u * 6, v * 6, 3);
                int r = (int) Math.clamp(62 + 22 * k, 0, 255);
                int g = (int) Math.clamp(92 + 28 * k, 0, 255);
                int b = (int) Math.clamp(34 + 10 * k, 0, 255);
                image.setRGB(x, y, 0xff000000 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    private static int gray(double linear) {
        double srgb = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        int c = (int) Math.round(Math.clamp(srgb, 0, 1) * 255);
        return c << 16 | c << 8 | c;
    }
}
