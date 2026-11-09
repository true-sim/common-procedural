package com.truesim.common.procedural.ecology.terrain;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.rocks.RockField;
import com.truesim.common.procedural.ecology.rocks.RockFieldParameters;
import com.truesim.common.procedural.ecology.rocks.RockInstances;
import com.truesim.common.procedural.ecology.rocks.RockKind;
import com.truesim.common.procedural.ecology.rocks.RockTile;
import com.truesim.effects.terrain.data.TerrainSurface;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The rocks of Lauterbrunnen's valley round the church, from effects-terrain's cache: a map over hillshade, each rock
 * its contact coloured by kind (talus orange, rockfall red, stream blue, piles green, erratics white), with timings
 * and the densest talus: {@code TRUESIM_PICTURES=true ./gradlew :common-procedural-ecology-terrain:test --tests
 * '*LauterbrunnenRocks'}, into build/rocks (or {@code -Ppictures=<dir>}).
 */
@EnabledIfEnvironmentVariable(named = "TRUESIM_PICTURES", matches = "true")
class LauterbrunnenRocks {

    private static final double X0 = -1024;
    private static final double Z0 = -1024;
    private static final double SIZE = 2048;
    private static final double TILE = 256;

    @Test
    void valley() throws Exception {
        TerrainSurface surface = Sites.lauterbrunnenSurface();
        assumeTrue(surface != null, "effects-terrain's cache holds no Lauterbrunnen");
        TerrainEcology ground = TerrainEcology.of(surface, 2);
        Ecology ecology = new Ecology(ground, EcologyParameters.ALPS.withSeed(11)
            .withLandUse(EcologyParameters.LandUse.MEADOWS), AlpineSpecies.ALL);
        RockField field = new RockField(ecology, RockFieldParameters.DEFAULTS);
        long start = System.nanoTime();
        int count = (int) (SIZE / TILE);
        List<RockTile> tiles = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            List<Future<RockTile>> results = new ArrayList<>();
            for (int j = 0; j < count; j++) {
                for (int i = 0; i < count; i++) {
                    double x = X0 + i * TILE;
                    double z = Z0 + j * TILE;
                    results.add(pool.submit(() -> field.region(x, z, TILE, TILE)));
                }
            }
            for (Future<RockTile> r : results) {
                tiles.add(r.get());
            }
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        int[] kinds = new int[RockKind.ALL.length];
        // the densest talus: 64 m cells
        int cells = (int) (SIZE / 64);
        int[] talus = new int[cells * cells];
        int total = 0;
        for (RockTile tile : tiles) {
            RockInstances rocks = tile.instances();
            total += rocks.count();
            for (int i = 0; i < rocks.count(); i++) {
                kinds[rocks.kind(i).ordinal()]++;
                if (rocks.kind(i) == RockKind.TALUS) {
                    int c = (int) ((rocks.x(i) - X0) / 64);
                    int r = (int) ((rocks.z(i) - Z0) / 64);
                    talus[r * cells + c]++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "%.0f x %.0f m in %.1f s (%d threads): %d rocks", SIZE, SIZE, seconds,
            Runtime.getRuntime().availableProcessors(), total);
        for (RockKind kind : RockKind.ALL) {
            System.out.printf(Locale.ROOT, ", %s %d", kind.name().toLowerCase(Locale.ROOT), kinds[kind.ordinal()]);
        }
        System.out.println();
        for (int k = 0; k < 6; k++) {
            int best = 0;
            for (int c = 1; c < talus.length; c++) {
                if (talus[c] > talus[best]) {
                    best = c;
                }
            }
            double x = X0 + (best % cells + 0.5) * 64;
            double z = Z0 + (best / cells + 0.5) * 64;
            System.out.printf(Locale.ROOT, "talus %d rocks round (%.0f, %.0f), height %.0f m%n", talus[best], x, z,
                ground.height(x, z));
            talus[best] = 0;
        }
        // the map: 1 m texels
        int n = (int) SIZE;
        BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                double x = X0 + col + 0.5;
                double z = Z0 + row + 0.5;
                double dx = ground.height(x + 2, z) - ground.height(x - 2, z);
                double dz = ground.height(x, z + 2) - ground.height(x, z - 2);
                double shade = Math.clamp(0.7 + 0.18 * (-dx - dz) / 4, 0.15, 1);
                int g = (int) (210 * shade);
                image.setRGB(col, row, g << 16 | g << 8 | g);
            }
        }
        int[] colors = {0xe08a2c, 0xd0302a, 0x3070e0, 0x30b030, 0xffffff};
        for (RockTile tile : tiles) {
            RockInstances rocks = tile.instances();
            for (int i = 0; i < rocks.count(); i++) {
                double r = Math.max(rocks.radius(i), 0.5);
                for (int row = (int) (rocks.z(i) - r - Z0); row <= (int) (rocks.z(i) + r - Z0); row++) {
                    for (int col = (int) (rocks.x(i) - r - X0); col <= (int) (rocks.x(i) + r - X0); col++) {
                        if (row >= 0 && row < n && col >= 0 && col < n && tile.cover(X0 + col + 0.5, Z0 + row + 0.5,
                            0.5) > 0.5) {
                            image.setRGB(col, row, colors[rocks.kind(i).ordinal()]);
                        }
                    }
                }
            }
        }
        Path out = Path.of(System.getProperty("truesim.pictures", "build/rocks"), "lauterbrunnen-rocks.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(image, "png", out.toFile());
    }
}
