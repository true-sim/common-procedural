package com.truesim.common.procedural.ecology.rocks;

import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A map of the rocks below the test's wall, over hillshade, coloured by kind (talus orange, rockfall red, stream
 * blue, piles green, erratics white), each its contact; with counts and sizes by band:
 * {@code TRUESIM_PICTURES=true ./gradlew :common-procedural-ecology:test --tests '*RockMaps'}, into build/rocks.
 */
@EnabledIfEnvironmentVariable(named = "TRUESIM_PICTURES", matches = "true")
class RockMaps {

    @Test
    void wall() throws Exception {
        EcologyTerrain terrain = RockFieldTest.wall();
        RockField field = new RockField(new Ecology(terrain, RockFieldTest.PARAMETERS, AlpineSpecies.ALL),
            RockFieldParameters.DEFAULTS);
        double x0 = -128;
        double z0 = -440;
        double texel = 0.5;
        int w = 512;
        int h = 1280;
        long start = System.nanoTime();
        RockTile tile = field.region(x0, z0, w * texel, h * texel);
        System.out.printf("region %.0f x %.0f m: %.0f ms, %d rocks, %d variants used%n", w * texel, h * texel,
            (System.nanoTime() - start) / 1e6, tile.instances().count(), tile.instances().count() == 0 ? 0
                : java.util.stream.IntStream.range(0, tile.instances().count()).map(tile.instances()::variantIndex)
                    .distinct().count());
        float[] map = tile.footprint(x0, z0, texel, w, h);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        RockInstances rocks = tile.nearby();
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                double x = x0 + (col + 0.5) * texel;
                double z = z0 + (row + 0.5) * texel;
                double dx = terrain.height(x + 1, z) - terrain.height(x - 1, z);
                double dz = terrain.height(x, z + 1) - terrain.height(x, z - 1);
                double shade = Math.clamp(0.75 + 0.25 * (-dx - dz) / 2, 0.2, 1);
                int g = (int) (200 * shade);
                image.setRGB(col, row, g << 16 | g << 8 | g);
            }
        }
        int[] colors = {0xe08a2c, 0xc0302a, 0x3060d0, 0x40a040, 0xffffff};
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                if (map[row * w + col] > 0.5) {
                    double x = x0 + (col + 0.5) * texel;
                    double z = z0 + (row + 0.5) * texel;
                    int best = -1;
                    double bestD = Double.MAX_VALUE;
                    for (int i = 0; i < rocks.count(); i++) {
                        double d = Math.hypot(rocks.x(i) - x, rocks.z(i) - z);
                        if (d < rocks.radius(i) && d < bestD) {
                            bestD = d;
                            best = i;
                        }
                    }
                    image.setRGB(col, row, best < 0 ? 0 : colors[rocks.kind(best).ordinal()]);
                }
            }
        }
        Path out = Path.of("build/rocks/wall.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(image, "png", out.toFile());
        // sizes by band below the foot
        RockInstances inside = tile.instances();
        for (RockKind kind : RockKind.ALL) {
            double[] sum = new double[16];
            int[] n = new int[16];
            for (int i = 0; i < inside.count(); i++) {
                if (inside.kind(i) == kind) {
                    int band = (int) Math.clamp((inside.z(i) - RockFieldTest.FOOT) / 40, 0, 15);
                    sum[band] += inside.size(i);
                    n[band]++;
                }
            }
            StringBuilder line = new StringBuilder(String.format("%-9s", kind));
            for (int b = 0; b < 16; b++) {
                if (n[b] > 0) {
                    line.append(String.format(" %d:%d@%.2f", b * 40, n[b], sum[b] / n[b]));
                }
            }
            System.out.println(line);
        }
    }
}
