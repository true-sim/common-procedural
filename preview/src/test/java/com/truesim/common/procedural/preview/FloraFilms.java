package com.truesim.common.procedural.preview;

import com.truesim.common.math.random.PoissonDisc;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.plant.Plant;
import com.truesim.common.procedural.plant.PlantOptions;
import com.truesim.common.procedural.plant.PlantSpecies;
import com.truesim.common.procedural.plant.Plants;
import com.truesim.common.procedural.tree.Tree;
import com.truesim.common.procedural.tree.TreeOptions;
import com.truesim.common.procedural.tree.TreeSpecies;
import com.truesim.common.procedural.tree.Trees;
import com.truesim.engine.vulkan.VulkanDevice;
import com.truesim.engine.vulkan.VulkanInstance;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Films of flowers and fruits (as {@link ProceduralFilms}, PNG frames at 30 a second, 1280 x 720): a turntable of every
 * flowering species, in flower (left) and in fruit or late in the year (right); a rowan and an apple tree through the
 * year; a patch of meadow flowers and grass in rising wind. {@code TRUESIM_FLORA_FILMS=true}; {@code -Pfilms=<dir>}.
 * {@code TRUESIM_SCREENSHOTS=true} draws stills of every species in flower and in fruit (on a validated device).
 */
class FloraFilms {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int SUPER = 2;
    /**
     * The tone curve of these films: Khronos PBR Neutral keeps the hue and saturation of an albedo (a rowan berry's
     * orange-red, arnica's yellow); AgX, the stage's default, washes them to pastels at this exposure (the colour
     * chart, {@link #colourChart}, shows both).
     */
    static final String NEUTRAL = System.getenv().getOrDefault("TRUESIM_FLORA_CURVE", "pbr_neutral");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);

    private VulkanInstance instance;
    private VulkanDevice device;

    @BeforeEach
    void start() {
        instance = VulkanInstance.builder().applicationName("flora films").surfaces(false).build();
        device = VulkanDevice.createHeadless(instance.bestGpu());
    }

    @AfterEach
    void stop() {
        device.close();
        instance.close();
    }

    /** A plant on the stage: its levels in the pool, its foliage and flower materials (consecutive). */
    record PlacedPlant(Plant plant, int[] meshes, int material) {
    }

    static PlacedPlant load(Stage stage, Plant plant, float sway) {
        int palette = stage.texture(Looks.palette(plant.palette()));
        float reach = (float) (plant.height() * 0.6 + 0.1);
        // blades a few millimetres wide flutter a millimetre or so, not the trees' leaf cards' centimetres
        int material = stage.paletteMaterial(palette, 0.85f, reach, sway, 0.0002f);
        stage.paletteMaterial(palette, 0.5f, reach, sway, 0.0004f);
        int[] meshes = new int[plant.lods().size()];
        for (int l = 0; l < meshes.length; l++) {
            meshes[l] = stage.add(plant.lods().get(l));
        }
        return new PlacedPlant(plant, meshes, material);
    }

    private static String date(double day) {
        int d = (int) Math.floor(((day % 365) + 365) % 365);
        return LocalDate.ofYearDay(2026, d + 1).format(DATE);
    }

    /** Writes the frame with a caption in its lower left corner. */
    private static void write(BufferedImage big, Path folder, int frame, String caption) throws IOException {
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22 * SUPER));
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(caption, 30 * SUPER + 2, big.getHeight() - 30 * SUPER + 2);
        g.setColor(Color.WHITE);
        g.drawString(caption, 30 * SUPER, big.getHeight() - 30 * SUPER);
        g.dispose();
        ProceduralFilms.write(big, folder, frame);
    }

    private static String title(String name) {
        return switch (name) {
            case "dog-rose" -> "Dog rose";
            case "alpine-aster" -> "Alpine aster";
            case "green-alder" -> "Green alder";
            case "stone-pine" -> "Stone pine";
            case "cotton-grass" -> "Cotton grass";
            case "marsh-marigold" -> "Marsh marigold";
            default -> Character.toUpperCase(name.charAt(0)) + name.substring(1);
        };
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FLORA_FILMS", matches = "true")
    void treeTurntables() throws IOException {
        String only = System.getenv("TRUESIM_FLORA_ONLY");
        for (TreeSpecies species : List.of(TreeSpecies.ROWAN, TreeSpecies.APPLE, TreeSpecies.ELDER,
            TreeSpecies.DOG_ROSE, TreeSpecies.ALPENROSE, TreeSpecies.BLUEBERRY, TreeSpecies.LINGONBERRY,
            TreeSpecies.OAK, TreeSpecies.ASH, TreeSpecies.ALDER, TreeSpecies.GREEN_ALDER, TreeSpecies.WILLOW,
            TreeSpecies.STONE_PINE)) {
            if (only != null && !only.contains(species.name())) {
                continue;
            }
            double bloom = species.fruiting().phenology().peakBloom();
            double ripe = species.fruiting().phenology().ripe();
            // close-ups: the near level with room for every flower (a budget of 400,000 triangles)
            TreeOptions near = TreeOptions.DEFAULTS.withLods(List.of(new com.truesim.common.procedural.tree.TreeLod(
                10, 6, 3, 0.008, 1, 1, 400_000, 0)));
            Tree flowering = Trees.generate(species, near.withDay(bloom), 7);
            Tree fruiting = Trees.generate(species, near.withDay(ripe), 7);
            double size = Math.max(flowering.height(), 2 * flowering.crownRadius());
            Path out = ProceduralFilms.folder("turntable-" + species.name());
            try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, Math.max(40, 20 * size),
                Math.max(8, 6 * size))) {
                ProceduralFilms.Placed left = ProceduralFilms.load(stage, flowering);
                ProceduralFilms.Placed right = ProceduralFilms.load(stage, fruiting);
                double apart = 0.55 * Math.max(2.1 * flowering.crownRadius(), 0.45 * flowering.height());
                int a = ProceduralFilms.place(stage, left, 0, -apart, 0, 0);
                int b = ProceduralFilms.place(stage, right, 0, apart, 0, 0);
                double distance = 1.05 * (2 * apart + size) + 0.2;
                stage.wind(ProceduralFilms.wind(2)).curve(NEUTRAL).frontLit().shade();
                String caption = title(species.name()) + "   left: " + date(bloom) + " in flower   right: "
                    + date(ripe) + " in fruit";
                int frames = 150;
                int f = 0;
                for (; f < frames; f++) {
                    double yaw = 2 * Math.PI * f / frames;
                    stage.move(a, left.meshes()[0], left.material(), -apart, 0, yaw, 1);
                    stage.move(b, right.meshes()[0], right.material(), apart, 0, yaw, 1);
                    stage.look(0, 0.5 * flowering.height() + 0.1 * size, distance, 0, 0.5 * flowering.height(), 0, 40);
                    stage.frames(f == 0 ? 4 : 1, 1 / 30.0);
                    write(stage.shot(), out, f, caption);
                }
                // then close: a head in flower, then one in fruit, the trees still
                stage.move(a, left.meshes()[0], left.material(), -apart, 0, 0, 1);
                stage.move(b, right.meshes()[0], right.material(), apart, 0, 0, 1);
                for (int side = 0; side < 2; side++) {
                    Tree tree = side == 0 ? flowering : fruiting;
                    double[] focus = focus(tree);
                    double fx = focus[0] + (side == 0 ? -apart : apart);
                    double reach = Math.max(0.12, 3 * focus[3]);
                    String close = title(species.name()) + "   " + (side == 0 ? date(bloom) + ", in flower"
                        : date(ripe) + ", in fruit") + "   close";
                    double outward = Math.atan2(focus[0], focus[2]); // from the trunk out through the head
                    for (int k = 0; k < 75; k++, f++) {
                        double angle = outward + 0.5 * (k / 75.0 - 0.5);
                        stage.look(fx + reach * Math.sin(angle), focus[1] + 0.35 * reach, focus[2] + reach
                            * Math.cos(angle), fx, focus[1], focus[2], 40);
                        stage.frames(k == 0 ? 4 : 1, 1 / 30.0);
                        write(stage.shot(), out, f, close);
                    }
                }
            }
        }
    }

    /**
     * A head to look at closely: the attachments' vertices near the one most toward the camera (+z) in the middle of
     * the crown; their centre (x, y, z) and extent.
     */
    private static double[] focus(Tree tree) {
        var mesh = tree.lods().getFirst();
        var position = mesh.vertices().attribute(com.truesim.common.procedural.mesh.ProceduralVertex.POSITION);
        double best = Double.NEGATIVE_INFINITY;
        int pick = -1;
        double h = tree.height();
        for (int t = mesh.submeshStart(Tree.ATTACHMENTS); t < mesh.submeshEnd(Tree.ATTACHMENTS); t++) {
            int v = mesh.indices()[t];
            double y = position.y(v);
            // the outermost, in the middle of the crown, toward the camera's side
            double score = Math.hypot(position.x(v), position.z(v)) + 0.5 * position.z(v) - Math.abs(y - 0.55 * h);
            if (score > best) {
                best = score;
                pick = v;
            }
        }
        if (pick < 0) {
            return new double[] {0, 0.5 * h, tree.crownRadius(), 0.1};
        }
        double px = position.x(pick);
        double py = position.y(pick);
        double pz = position.z(pick);
        double sx = 0;
        double sy = 0;
        double sz = 0;
        double extent = 0;
        int n = 0;
        double radius = 0.1 + 0.02 * h;
        for (int t = mesh.submeshStart(Tree.ATTACHMENTS); t < mesh.submeshEnd(Tree.ATTACHMENTS); t++) {
            int v = mesh.indices()[t];
            double d = Math.sqrt(Math.pow(position.x(v) - px, 2) + Math.pow(position.y(v) - py, 2)
                + Math.pow(position.z(v) - pz, 2));
            if (d < radius) {
                sx += position.x(v);
                sy += position.y(v);
                sz += position.z(v);
                extent = Math.max(extent, d);
                n++;
            }
        }
        return new double[] {sx / n, sy / n, sz / n, extent};
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FLORA_FILMS", matches = "true")
    void plantTurntables() throws IOException {
        String only = System.getenv("TRUESIM_FLORA_ONLY");
        for (PlantSpecies species : PlantSpecies.PRESETS) {
            if (only != null && !only.contains(species.name())) {
                continue;
            }
            double bloom = species.phenology().peakBloom();
            double late = species.grass() ? species.phenology().ripenEnd() + 5 : 285;
            Path out = ProceduralFilms.folder("turntable-" + species.name());
            try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 30, 3)) {
                // a few of them, so a few are in flower whatever their dates
                List<PlacedPlant> early = new ArrayList<>();
                List<PlacedPlant> later = new ArrayList<>();
                double height = 0;
                for (int k = 0; k < 4; k++) {
                    int seed = 11 + k;
                    Plant p = Plants.generate(species, PlantOptions.DEFAULTS.withDay(bloom), seed);
                    height = Math.max(height, p.height());
                    early.add(load(stage, p, 0.08f));
                    later.add(load(stage, Plants.generate(species, PlantOptions.DEFAULTS.withDay(late), seed), 0.08f));
                }
                double size = Math.max(height, 0.12);
                double apart = 0.75 * size + 0.08;
                double[][] offsets = {{-0.25, -0.2}, {0.25, -0.15}, {-0.1, 0.25}, {0.2, 0.22}};
                List<int[]> ids = new ArrayList<>();
                for (int k = 0; k < 4; k++) {
                    for (int side = 0; side < 2; side++) {
                        PlacedPlant p = side == 0 ? early.get(k) : later.get(k);
                        double x = (side == 0 ? -apart : apart) + offsets[k][0] * size;
                        double z = offsets[k][1] * size;
                        ids.add(new int[] {stage.place(p.meshes()[0], p.material(), x, z, k, 1), side, k});
                    }
                }
                double distance = 2.4 * (2 * apart + size);
                stage.wind(ProceduralFilms.wind(3)).look(0, 0.55 * size, distance, 0, 0.35 * size, 0, 40).curve(NEUTRAL).frontLit().shade();
                String caption = title(species.name()) + "   left: " + date(bloom) + "   right: " + date(late);
                int frames = 150;
                for (int f = 0; f < frames; f++) {
                    double yaw = 2 * Math.PI * f / frames;
                    for (int[] id : ids) {
                        PlacedPlant p = id[1] == 0 ? early.get(id[2]) : later.get(id[2]);
                        double cx = id[1] == 0 ? -apart : apart;
                        double ox = offsets[id[2]][0] * size;
                        double oz = offsets[id[2]][1] * size;
                        double c = Math.cos(yaw);
                        double s = Math.sin(yaw);
                        stage.move(id[0], p.meshes()[0], p.material(), cx + c * ox + s * oz, -s * ox + c * oz,
                            yaw + id[2], 1);
                    }
                    stage.frames(f == 0 ? 4 : 1, 1 / 30.0);
                    write(stage.shot(), out, f, caption);
                }
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FLORA_FILMS", matches = "true")
    void throughTheYear() throws IOException {
        for (TreeSpecies species : List.of(TreeSpecies.ROWAN, TreeSpecies.APPLE)) {
            Path out = ProceduralFilms.folder("year-" + species.name());
            try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 80, 30)) {
                double first = 95;
                double step = 4;
                int hold = 5;
                int steps = 92; // into the next March
                Tree tree = null;
                int material = -1;
                int mesh = -1;
                int id = -1;
                stage.wind(ProceduralFilms.wind(2.5));
                int frame = 0;
                for (int k = 0; k < steps; k++) {
                    double day = first + step * k;
                    double d = day % 365;
                    boolean winter = d >= 335 || d < 75;
                    double snow = winter ? 0.6 : 0;
                    tree = Trees.generate(species, TreeOptions.DEFAULTS.withDay(d).withSnowLoad(snow)
                        .withLods(List.of(com.truesim.common.procedural.tree.TreeLod.FULL)), 7);
                    if (id < 0) {
                        // materials and textures once: they are the same every day
                        ProceduralFilms.Placed loaded = ProceduralFilms.load(stage, tree);
                        material = loaded.material();
                        mesh = loaded.meshes()[0];
                        id = ProceduralFilms.place(stage, loaded, 0, 0, 0, 0);
                    } else {
                        int next = stage.add(tree.lods().getFirst());
                        stage.move(id, next, material, 0, 0, 0, 1);
                        stage.remove(mesh);
                        mesh = next;
                    }
                    if (winter) {
                        stage.ground(0.75f, 0.77f, 0.8f);
                    } else {
                        stage.ground(0.06f, 0.08f, 0.03f);
                    }
                    String caption = title(species.name()) + "   " + date(d) + flora(tree);
                    for (int h = 0; h < hold; h++) {
                        double a = 0.6 + 0.9 * frame / (steps * hold);
                        double distance = 1.15 * Math.max(tree.height(), 2 * tree.crownRadius()) + 1;
                        stage.look(distance * Math.sin(a), 0.45 * tree.height() + 0.6, distance * Math.cos(a), 0,
                            0.48 * tree.height(), 0, 45);
                        if (frame == 0) {
                            stage.curve(NEUTRAL).frontLit().shade();
                        }
                        stage.frames(frame == 0 ? 4 : 1, 1 / 30.0);
                        write(stage.shot(), out, frame++, caption);
                    }
                }
            }
        }
    }

    private static String flora(Tree tree) {
        var f = tree.flora();
        StringBuilder s = new StringBuilder();
        if (f.buds() > 0) {
            s.append("   buds ").append(f.buds());
        }
        if (f.flowers() > 0) {
            s.append("   flowers ").append(f.flowers());
        }
        if (f.fruits() > 0) {
            s.append("   fruits ").append(f.fruits());
        }
        return s.toString();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FLORA_FILMS", matches = "true")
    void meadowInWind() throws IOException {
        Path out = ProceduralFilms.folder("meadow-wind");
        double day = 192;
        try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 60,
            com.truesim.engine.render.GeometryPool.Capacity.of(1 << 22, 1 << 18, 128), 1 << 14, 6)) {
            // a sward: mostly grass, the flowers among it
            PlantSpecies[] mix = {PlantSpecies.GRASS, PlantSpecies.GRASS, PlantSpecies.GRASS, PlantSpecies.GRASS,
                PlantSpecies.GRASS, PlantSpecies.GRASS, PlantSpecies.GRASS, PlantSpecies.CLOVER,
                PlantSpecies.CLOVER, PlantSpecies.CLOVER, PlantSpecies.BUTTERCUP, PlantSpecies.BUTTERCUP,
                PlantSpecies.ARNICA, PlantSpecies.ALPINE_ASTER, PlantSpecies.EDELWEISS, PlantSpecies.GENTIAN};
            List<List<PlacedPlant>> variants = new ArrayList<>();
            for (int s = 0; s < mix.length; s++) {
                List<PlacedPlant> some = new ArrayList<>();
                for (int v = 0; v < 3; v++) { // materials: two per plant, 128 in all
                    some.add(load(stage, Plants.generate(mix[s], PlantOptions.DEFAULTS.withDay(day), 100 * s + v),
                        0.07f));
                }
                variants.add(some);
            }
            double[] points = new double[2 * 16000];
            int count = Math.min(15000, PoissonDisc.sample(Rng.seeded(5), 7, 7, 0.065, 30, points));
            Rng rng = Rng.seeded(6);
            for (int i = 0; i < count; i++) {
                double x = points[2 * i] - 3.5;
                double z = points[2 * i + 1] - 5.5;
                List<PlacedPlant> some = variants.get(rng.nextInt(variants.size()));
                PlacedPlant p = some.get(rng.nextInt(some.size()));
                double d = Math.hypot(x, z - 2.2);
                int level = d < 2.5 ? 0 : d < 5 ? 1 : 2;
                stage.place(p.meshes()[level], p.material(), x, z, rng.nextDouble() * 2 * Math.PI,
                    0.85 + 0.3 * rng.nextDouble());
            }
            stage.curve(NEUTRAL).frontLit().shade();
            int frames = 300;
            for (int f = 0; f < frames; f++) {
                double t = f / 30.0;
                double speed = t < 1.5 ? 0.5 : t < 6 ? 0.5 + 7.5 * (t - 1.5) / 4.5 : 8 + 1.5 * Math.sin(t * 1.3);
                stage.wind(ProceduralFilms.wind(speed));
                stage.look(0.2, 0.55, 2.4, -0.1, 0.12, -1.2, 45);
                stage.frames(f == 0 ? 4 : 1, 1 / 30.0);
                write(stage.shot(), out, f, String.format(Locale.ROOT, "Alpine meadow, %s   wind %.1f m/s",
                    date(day), speed));
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_SCREENSHOTS", matches = "true")
    void stills() throws IOException {
        Path out = ProceduralFilms.folder("flora-stills");
        // Validated (TRUESIM_GPU_ASSISTED_VALIDATION=true adds GPU-assisted validation): closing fails on errors
        try (com.truesim.engine.vulkan.testing.TestGpu gpu = com.truesim.engine.vulkan.testing.TestGpu.open();
             Stage stage = new Stage(gpu.device(), WIDTH * SUPER, HEIGHT * SUPER, 120, 40)) {
            List<TreeSpecies> trees = List.of(TreeSpecies.ROWAN, TreeSpecies.APPLE, TreeSpecies.ELDER,
                TreeSpecies.DOG_ROSE, TreeSpecies.ALPENROSE, TreeSpecies.BLUEBERRY, TreeSpecies.LINGONBERRY);
            for (int i = 0; i < trees.size(); i++) {
                TreeSpecies s = trees.get(i);
                double x = (i - 3) * 7.0;
                ProceduralFilms.place(stage, ProceduralFilms.load(stage, Trees.generate(s, TreeOptions.DEFAULTS
                    .withDay(s.fruiting().phenology().peakBloom()), 1)), 0, x, -6, 0);
                ProceduralFilms.place(stage, ProceduralFilms.load(stage, Trees.generate(s, TreeOptions.DEFAULTS
                    .withDay(s.fruiting().phenology().ripe()), 1)), 1, x, 2, 0);
            }
            for (int i = 0; i < PlantSpecies.PRESETS.size(); i++) {
                PlantSpecies s = PlantSpecies.PRESETS.get(i);
                PlacedPlant p = load(stage, Plants.generate(s, PlantOptions.DEFAULTS.withDay(
                    s.phenology().peakBloom()), 1), 0.07f);
                stage.place(p.meshes()[0], p.material(), (i - 3) * 1.2, 9, 0, 3);
            }
            stage.wind(ProceduralFilms.wind(3)).look(0, 7, 32, 0, 2.5, 0, 50).curve(NEUTRAL).frontLit().shade();
            stage.frames(8, 1 / 30.0);
            ProceduralFilms.write(stage.shot(), out, 0);
        }
    }

    /**
     * A colour chart through the stage's light and tone curves: flat patches of known albedo (the flora's) facing the
     * sun, their rendered sRGB printed beside the albedo's own, for agx and pbr_neutral.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_SCREENSHOTS", matches = "true")
    void colourChart() throws IOException {
        String[] names = {"rowan berry", "apple red", "rose hip", "white petal", "pink petal", "gentian", "leaf",
            "arnica"};
        com.truesim.common.procedural.flora.Albedo[] albedos = {
            new com.truesim.common.procedural.flora.Albedo(0.5, 0.05, 0.02),
            new com.truesim.common.procedural.flora.Albedo(0.42, 0.05, 0.035),
            new com.truesim.common.procedural.flora.Albedo(0.45, 0.04, 0.02),
            new com.truesim.common.procedural.flora.Albedo(0.72, 0.7, 0.6),
            new com.truesim.common.procedural.flora.Albedo(0.7, 0.42, 0.48),
            new com.truesim.common.procedural.flora.Albedo(0.025, 0.05, 0.45),
            new com.truesim.common.procedural.flora.Albedo(0.045, 0.1, 0.018),
            new com.truesim.common.procedural.flora.Albedo(0.78, 0.42, 0.04)};
        for (String curve : List.of("agx", "pbr_neutral")) {
            Path out = ProceduralFilms.folder("colour-chart-" + curve);
            try (Stage stage = new Stage(device, WIDTH, HEIGHT, 40, 20)) {
                double[] sun = {-0.45, 0.6, -0.65};
                double l = Math.sqrt(0.45 * 0.45 + 0.36 + 0.65 * 0.65);
                for (int i = 0; i < names.length; i++) {
                    com.truesim.common.procedural.flora.Palette palette = com.truesim.common.procedural.flora.Palette
                        .DEFAULT.with(com.truesim.common.procedural.flora.Palette.Part.FRUIT,
                            com.truesim.common.procedural.flora.Ramp.of(albedos[i]));
                    int texture = stage.texture(Looks.palette(palette));
                    int material = stage.paletteMaterial(texture, 0.6f, 0.1f, 0, 0);
                    com.truesim.common.procedural.mesh.MeshWriter writer = new com.truesim.common.procedural.mesh.MeshWriter();
                    double u = com.truesim.common.procedural.flora.Palette.u(0.5);
                    double v = com.truesim.common.procedural.flora.Palette.v(com.truesim.common.procedural.flora.Palette.Part.FRUIT);
                    // a patch facing the sun, a metre square, on a post
                    double nx = sun[0] / l;
                    double ny = sun[1] / l;
                    double nz = sun[2] / l;
                    double tx = nz;
                    double tz = -nx;
                    double tl = Math.hypot(tx, tz);
                    tx /= tl;
                    tz /= tl;
                    double bx = ny * tz;
                    double by = nz * tx - nx * tz;
                    double bz = -ny * tx;
                    int a0 = -1;
                    for (int k = 0; k < 4; k++) {
                        double su = (k == 1 || k == 2 ? 0.5 : -0.5);
                        double sv = (k >= 2 ? 0.5 : -0.5);
                        int vtx = writer.vertex(su * tx + sv * bx, 1.2 + sv * by, su * tz + sv * bz, nx, ny, nz, tx, 0,
                            tz, u, v);
                        if (k == 0) {
                            a0 = vtx;
                        }
                    }
                    writer.quad(a0, a0 + 1, a0 + 2, a0 + 3);
                    writer.quad(a0, a0 + 3, a0 + 2, a0 + 1);
                    stage.place(stage.add(writer.build(0)), material, (i - 3.5) * 1.3, 0, 0, 1);
                }
                // from the sun's side, the sun behind the camera
                stage.look(9 * sun[0] / l, 1.2 + 9 * sun[1] / l, 9 * sun[2] / l, 0, 1.2, 0, 60)
                    .shade().curve(curve);
                stage.frames(6, 1 / 30.0);
                BufferedImage shot = stage.shot();
                ImageIO.write(shot, "png", out.resolve("chart.png").toFile());
            }
        }
    }

    /**
     * A group from 50 m, as one sees them across a valley: rowans, apple trees and dog roses, first each in flower
     * (on its peak day), then each in fruit; the camera drifting slowly sideways.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_FLORA_FILMS", matches = "true")
    void groupFromFiftyMetres() throws IOException {
        Path out = ProceduralFilms.folder("group-50m");
        int frame = 0;
        for (int state = 0; state < 2; state++) {
            try (Stage stage = new Stage(device, WIDTH * SUPER, HEIGHT * SUPER, 200, 60)) {
                TreeSpecies[] group = {TreeSpecies.ROWAN, TreeSpecies.APPLE, TreeSpecies.DOG_ROSE, TreeSpecies.ROWAN,
                    TreeSpecies.APPLE, TreeSpecies.DOG_ROSE, TreeSpecies.DOG_ROSE};
                double[][] at = {{-9, -2}, {-1, 1}, {5, 4}, {9, -3}, {15, 0}, {-4, 5}, {11, 5}};
                long[] seeds = {7, 3, 7, 8, 10, 9, 3}; // the apples of the red cultivar
                for (int i = 0; i < group.length; i++) {
                    var p = group[i].fruiting().phenology();
                    double day = state == 0 ? p.peakBloom() : p.ripe();
                    Tree tree = Trees.generate(group[i], TreeOptions.DEFAULTS.withDay(day), seeds[i]);
                    ProceduralFilms.place(stage, ProceduralFilms.load(stage, tree), 0, at[i][0], at[i][1], i);
                }
                stage.wind(ProceduralFilms.wind(2)).curve(NEUTRAL).frontLit();
                String caption = state == 0 ? "From 50 m, in flower: rowan " + date(TreeSpecies.ROWAN.fruiting()
                    .phenology().peakBloom()) + ", apple " + date(TreeSpecies.APPLE.fruiting().phenology().peakBloom())
                    + ", dog rose " + date(TreeSpecies.DOG_ROSE.fruiting().phenology().peakBloom())
                    : "From 50 m, in fruit: rowan " + date(TreeSpecies.ROWAN.fruiting().phenology().ripe())
                    + ", apple " + date(TreeSpecies.APPLE.fruiting().phenology().ripe()) + ", dog rose "
                    + date(TreeSpecies.DOG_ROSE.fruiting().phenology().ripe());
                for (int f = 0; f < 120; f++) {
                    double x = 3 - 6.0 * f / 120;
                    stage.look(x, 4, 52, x * 0.5 + 3, 3.5, 0, 30);
                    if (f == 0) {
                        stage.shade();
                    }
                    stage.frames(f == 0 ? 4 : 1, 1 / 30.0);
                    write(stage.shot(), out, frame++, caption);
                }
            }
        }
    }
}
