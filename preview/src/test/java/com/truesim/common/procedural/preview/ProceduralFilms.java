package com.truesim.common.procedural.preview;

import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.PoissonDisc;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.preview.materials.ProceduralWindParameters;
import com.truesim.common.procedural.rock.Rock;
import com.truesim.common.procedural.rock.RockParams;
import com.truesim.common.procedural.rock.Rocks;
import com.truesim.common.procedural.tree.Season;
import com.truesim.common.procedural.tree.Tree;
import com.truesim.common.procedural.tree.TreeOptions;
import com.truesim.common.procedural.tree.TreeSpecies;
import com.truesim.common.procedural.tree.Trees;
import com.truesim.engine.vulkan.VulkanDevice;
import com.truesim.engine.vulkan.VulkanInstance;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Films of common-procedural's trees through the engine, as PNG frames at 30 per second (1280 x 720, rendered at twice
 * that and filtered down) into {@code build/films/<film>} (or {@code -Pfilms=<dir>}): a turntable of every species,
 * a small forest swaying as the wind rises, the levels of detail side by side, the seasons. The trees sway by the
 * preview's minimal material reading the wind format (proceduralTree.slang). Run with
 * {@code TRUESIM_FILMS=true ./gradlew -Ptruesim.preview=true -Ptruesim.engine=../engine-vegetation :preview:test};
 * encode each with {@code ffmpeg -framerate 30 -i <film>/f%04d.png -c:v libx264 -pix_fmt yuv420p -crf 20 <film>.mp4}.
 * {@code TRUESIM_SCREENSHOTS=true} draws one still of every species only, on a validated device (quick; with
 * {@code TRUESIM_GPU_ASSISTED_VALIDATION=true} GPU-assisted validation checks the shaders' reads and writes).
 */
class ProceduralFilms {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int SUPER = 2;
    private static final Vec3 WIND = new Vec3(1, 0, -0.3);

    private VulkanInstance instance;
    private VulkanDevice device;

    @BeforeEach
    void start() {
        instance = VulkanInstance.builder().applicationName("procedural films").surfaces(false).build();
        device = VulkanDevice.createHeadless(instance.bestGpu());
        System.out.println("procedural films on " + instance.bestGpu().name());
    }

    @AfterEach
    void stop() {
        device.close();
        instance.close();
    }

    /** A tree on the stage: its levels and billboard in the pool, its bark, leaves and billboard materials. */
    record Placed(Tree tree, int[] meshes, int material, int billboardMaterial) {
    }

    static Placed load(Stage stage, Tree tree) {
        float reach = (float) (tree.height() * 0.13 + 3);
        int bark = stage.texture(Looks.bark(tree.species()));
        BufferedImage atlasImage = Looks.leafAtlas(tree.species());
        int atlas = stage.texture(atlasImage);
        int material = stage.treeMaterial(bark, false, 0.9f, reach);
        stage.treeMaterial(atlas, true, 0.92f, reach); // the leaves: the next material
        int capture = stage.texture(Looks.capture(tree, atlasImage, 512));
        int billboard = stage.treeMaterial(capture, true, 0.85f, reach);
        int[] meshes = new int[tree.lods().size() + 1];
        for (int l = 0; l < tree.lods().size(); l++) {
            meshes[l] = stage.add(tree.lods().get(l));
        }
        meshes[tree.lods().size()] = stage.add(tree.billboard());
        return new Placed(tree, meshes, material, billboard);
    }

    /** Places level {@code level} of {@code placed} (the billboard past the last). */
    static int place(Stage stage, Placed placed, int level, double x, double z, double yaw) {
        boolean billboard = level >= placed.tree().lods().size();
        return stage.place(placed.meshes()[Math.min(level, placed.meshes().length - 1)],
            billboard ? placed.billboardMaterial() : placed.material(), x, z, yaw, 1);
    }

    private static Path folder(String film) throws IOException {
        Path out = Path.of(System.getProperty("truesim.films", "build/films"), film);
        Files.createDirectories(out);
        return out;
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

    private static ProceduralWindParameters wind(double speed) {
        return ProceduralWindParameters.DEFAULTS.withDirection(WIND).withSpeed((float) speed);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_SCREENSHOTS", matches = "true")
    void stills() throws IOException {
        Path out = folder("stills");
        // Validated (TRUESIM_GPU_ASSISTED_VALIDATION=true adds GPU-assisted validation): closing fails on errors
        try (com.truesim.engine.vulkan.testing.TestGpu gpu = com.truesim.engine.vulkan.testing.TestGpu.open();
             Stage stage = new Stage(gpu.device(), WIDTH * SUPER, HEIGHT * SUPER, 400)) {
            List<TreeSpecies> all = TreeSpecies.PRESETS;
            for (int i = 0; i < all.size(); i++) {
                Placed placed = load(stage, Trees.generate(all.get(i), 1));
                place(stage, placed, 0, (i % 4 - 1.5) * 16, -(i / 4) * 22, 0);
            }
            stage.wind(wind(5)).look(0, 14, 48, 0, 9, -10, 50).shade();
            stage.frames(8, 1 / 30.0);
            write(stage.shot(), out, 0);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FILMS", matches = "true")
    void turntables() throws IOException {
        for (TreeSpecies species : TreeSpecies.PRESETS) {
            Path out = folder("turntable-" + species.name());
            try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 300)) {
                Tree tree = Trees.generate(species, 7);
                Placed placed = load(stage, tree);
                place(stage, placed, 0, 0, 0, 0);
                double size = Math.max(tree.height(), 2.2 * tree.crownRadius());
                double distance = 1.35 * size + 2;
                stage.wind(wind(4)).shade();
                int frames = 150;
                for (int f = 0; f < frames; f++) {
                    double a = 2 * Math.PI * f / frames;
                    stage.look(distance * Math.sin(a), 0.42 * tree.height() + 1, distance * Math.cos(a), 0,
                        0.45 * tree.height(), 0, 50);
                    stage.frames(f == 0 ? 4 : 1, 1 / 30.0);
                    write(stage.shot(), out, f);
                }
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FILMS", matches = "true")
    void forestInRisingWind() throws IOException {
        Path out = folder("forest-wind");
        try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 600)) {
            TreeSpecies[] mix = {TreeSpecies.SPRUCE, TreeSpecies.SPRUCE, TreeSpecies.SPRUCE, TreeSpecies.LARCH,
                TreeSpecies.LARCH, TreeSpecies.FIR, TreeSpecies.PINE, TreeSpecies.MUGO};
            List<List<Placed>> variants = new ArrayList<>();
            for (int s = 0; s < mix.length; s++) {
                List<Placed> some = new ArrayList<>();
                for (int v = 0; v < 3; v++) {
                    some.add(load(stage, Trees.generate(mix[s], 100 * s + v)));
                }
                variants.add(some);
            }
            double[] points = new double[2 * 400];
            int count = PoissonDisc.sample(Rng.seeded(3), 110, 110, 7, 30, points);
            Rng rng = Rng.seeded(4);
            Vec3 camera = new Vec3(2, 4, 78);
            for (int i = 0; i < count; i++) {
                double x = points[2 * i] - 55;
                double z = points[2 * i + 1] - 75;
                if (Math.abs(x) < 6 && z > 10) {
                    continue; // a clearing in front of the camera
                }
                List<Placed> some = variants.get(rng.nextInt(variants.size()));
                Placed placed = some.get(rng.nextInt(some.size()));
                double d = camera.distance(new Vec3(x, 0, z));
                int level = d < 40 ? 0 : d < 75 ? 1 : 2;
                place(stage, placed, level, x, z, rng.nextDouble() * 2 * Math.PI);
            }
            Rock boulder = Rocks.generate(RockParams.BOULDER, 2);
            int rockMaterial = stage.plainMaterial(stage.texture(Looks.rock()), 0.85f);
            int rockMesh = stage.addStill(boulder.lods().getFirst());
            for (int i = 0; i < 6; i++) {
                stage.place(rockMesh, rockMaterial, -14 + 6 * i, 8 - 5 * (i % 2), i, 0.6 + 0.3 * (i % 3));
            }
            stage.shade();
            int frames = 330;
            for (int f = 0; f < frames; f++) {
                double t = f / 30.0;
                double speed = t < 2 ? 0 : t < 6 ? 11 * (t - 2) / 4 : 11 + 2 * Math.sin(t * 1.7);
                stage.wind(wind(speed));
                // A still camera at the forest's edge, so what moves is the wind
                stage.look(camera.x(), camera.y(), camera.z(), 0, 9, 0, 50);
                stage.frames(f == 0 ? 4 : 1, 1 / 30.0);
                write(stage.shot(), out, f);
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FILMS", matches = "true")
    void levelsOfDetail() throws IOException {
        Path out = folder("lod-chain");
        try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 300)) {
            Placed spruce = load(stage, Trees.generate(TreeSpecies.SPRUCE, 5));
            Placed beech = load(stage, Trees.generate(TreeSpecies.BEECH, 5));
            for (int level = 0; level < 4; level++) {
                place(stage, spruce, level, (level - 1.5) * 15, 0, 0);
                place(stage, beech, level, (level - 1.5) * 17, 24, 0); // the lower beeches in front
            }
            stage.wind(wind(3)).shade();
            int frames = 210;
            for (int f = 0; f < frames; f++) {
                double a = 0.35 * Math.sin(2 * Math.PI * f / frames);
                stage.look(75 * Math.sin(a), 14, 24 + 62 * Math.cos(a), 0, 11, 8, 50);
                stage.frames(f == 0 ? 4 : 1, 1 / 30.0);
                write(stage.shot(), out, f);
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FILMS", matches = "true")
    void seasonsAndSnow() throws IOException {
        Path out = folder("seasons");
        try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 300)) {
            Season[] seasons = Season.values();
            for (int s = 0; s < seasons.length; s++) {
                Tree larch = Trees.generate(TreeSpecies.LARCH, TreeOptions.DEFAULTS.withSeason(seasons[s]), 9);
                place(stage, load(stage, larch), 0, (s - 2) * 13, 0, 0);
            }
            Tree snowy = Trees.generate(TreeSpecies.SPRUCE, TreeOptions.DEFAULTS.withSeason(Season.WINTER)
                .withSnowLoad(1), 9);
            place(stage, load(stage, snowy), 0, 2 * 13, 0, 0);
            stage.wind(wind(3)).shade();
            int frames = 180;
            for (int f = 0; f < frames; f++) {
                double x = -8 + 16.0 * f / frames;
                stage.look(x, 13, 58, x * 0.5, 11, 0, 50);
                stage.frames(f == 0 ? 4 : 1, 1 / 30.0);
                write(stage.shot(), out, f);
            }
        }
    }
}
