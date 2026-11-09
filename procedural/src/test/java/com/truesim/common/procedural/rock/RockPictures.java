package com.truesim.common.procedural.rock;

import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Pictures of every rock preset, bare and with moss and lichen, from the CPU (vertex colors times a limestone albedo,
 * fresh fractures in their own tone), for a quick look without a GPU: {@code TRUESIM_PICTURES=true ./gradlew
 * :common-procedural:test --tests '*RockPictures'}, into build/probe/rocks.
 */
@EnabledIfEnvironmentVariable(named = "TRUESIM_PICTURES", matches = "true")
class RockPictures {

    @Test
    void presets() throws Exception {
        for (RockParams params : RockParams.PRESETS) {
            int w = 1200;
            int h = 300;
            BufferedImage sheet = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int k = 0; k < 4; k++) {
                RockCover cover = k < 2 ? RockCover.BARE : new RockCover(0.8, 0.6, 0, Double.NaN);
                long start = System.nanoTime();
                Rock rock = Rocks.generate(params, cover, 3 + k);
                double ms = (System.nanoTime() - start) / 1e6;
                System.out.printf("%-8s seed %d: %.1f ms, %d triangles, fresh %d, footprint %s%n", params.name(),
                    3 + k, ms, rock.lods().getFirst().triangleCount(), (rock.lods().getFirst().submeshEnd(1)
                        - rock.lods().getFirst().submeshStart(1)) / 3, rock.footprint());
                // the north side (-z) toward the camera for the mossy ones
                double s = params.reach();
                double ez = k < 2 ? 2.4 * s : -2.4 * s;
                draw(sheet, k * 300, 300, 300, rock.lods().getFirst(), 1.6 * s, 1.3 * s, ez, 0.3 * s);
            }
            Path out = Path.of("build/probe/rocks", params.name() + ".png");
            Files.createDirectories(out.getParent());
            ImageIO.write(sheet, "png", out.toFile());
        }
    }

    /** Draws {@code mesh} into a {@code size}-pixel square of {@code image} at x {@code left}, from (ex, ey, ez). */
    static void draw(BufferedImage image, int left, int width, int height, ProceduralMesh mesh, double ex, double ey,
        double ez, double ty) {
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        Vec3Attribute normal = mesh.vertices().attribute(ProceduralVertex.NORMAL);
        Vec4Attribute tint = mesh.vertices().attribute(ProceduralVertex.WIND);
        double[] f = norm(-ex, ty - ey, -ez);
        double[] r = norm(f[2], 0, -f[0]);
        r = new double[] {-r[0], -r[1], -r[2]};
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        double focal = 0.5 * height / Math.tan(0.45);
        int n = mesh.vertexCount();
        double[] sx = new double[n];
        double[] sy = new double[n];
        double[] sz = new double[n];
        for (int i = 0; i < n; i++) {
            double dx = position.x(i) - ex;
            double dy = position.y(i) - ey;
            double dz = position.z(i) - ez;
            sz[i] = dx * f[0] + dy * f[1] + dz * f[2];
            sx[i] = width / 2.0 + focal * (dx * r[0] + dy * r[1] + dz * r[2]) / sz[i];
            sy[i] = height / 2.0 - focal * (dx * u[0] + dy * u[1] + dz * u[2]) / sz[i];
        }
        float[] depth = new float[width * height];
        Arrays.fill(depth, Float.MAX_VALUE);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(left + x, y, y > height * 0.62 ? 0x4a5a32 : 0x9cb8d8);
            }
        }
        double[] sun = norm(0.5, 0.8, 0.4);
        int[] idx = mesh.indices();
        for (int t = 0; t < idx.length; t += 3) {
            boolean fresh = t >= mesh.submeshStart(Rock.FRESH);
            int a = idx[t];
            int b = idx[t + 1];
            int c = idx[t + 2];
            double area = (sx[b] - sx[a]) * (sy[c] - sy[a]) - (sy[b] - sy[a]) * (sx[c] - sx[a]);
            if (area >= 0 || sz[a] < 0.05 || sz[b] < 0.05 || sz[c] < 0.05) {
                continue; // back-facing (screen y is down)
            }
            int x0 = (int) Math.max(0, Math.floor(Math.min(sx[a], Math.min(sx[b], sx[c]))));
            int x1 = (int) Math.min(width - 1, Math.ceil(Math.max(sx[a], Math.max(sx[b], sx[c]))));
            int y0 = (int) Math.max(0, Math.floor(Math.min(sy[a], Math.min(sy[b], sy[c]))));
            int y1 = (int) Math.min(height - 1, Math.ceil(Math.max(sy[a], Math.max(sy[b], sy[c]))));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    double px = x + 0.5;
                    double py = y + 0.5;
                    double w0 = ((sx[b] - px) * (sy[c] - py) - (sy[b] - py) * (sx[c] - px)) / area;
                    double w1 = ((sx[c] - px) * (sy[a] - py) - (sy[c] - py) * (sx[a] - px)) / area;
                    double w2 = 1 - w0 - w1;
                    if (w0 < 0 || w1 < 0 || w2 < 0) {
                        continue;
                    }
                    double z = w0 * sz[a] + w1 * sz[b] + w2 * sz[c];
                    if (z >= depth[y * width + x]) {
                        continue;
                    }
                    depth[y * width + x] = (float) z;
                    double nx = w0 * normal.x(a) + w1 * normal.x(b) + w2 * normal.x(c);
                    double ny = w0 * normal.y(a) + w1 * normal.y(b) + w2 * normal.y(c);
                    double nz = w0 * normal.z(a) + w1 * normal.z(b) + w2 * normal.z(c);
                    double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    double light = 0.3 + 0.9 * Math.max(0, (nx * sun[0] + ny * sun[1] + nz * sun[2]) / l);
                    double[] albedo = fresh ? new double[] {0.62, 0.6, 0.55} : new double[] {0.5, 0.49, 0.46};
                    int rgb = 0;
                    for (int k = 0; k < 3; k++) {
                        double tk = k == 0 ? w0 * tint.x(a) + w1 * tint.x(b) + w2 * tint.x(c)
                            : k == 1 ? w0 * tint.y(a) + w1 * tint.y(b) + w2 * tint.y(c)
                                : w0 * tint.z(a) + w1 * tint.z(b) + w2 * tint.z(c);
                        double linear = albedo[k] * tk * light;
                        double srgb = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
                        rgb = rgb << 8 | (int) Math.round(255 * Math.min(1, srgb));
                    }
                    image.setRGB(left + x, y, rgb);
                }
            }
        }
    }

    private static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / l, y / l, z / l};
    }
}
