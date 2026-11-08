package com.truesim.common.procedural;

import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * A small CPU rasterizer for looking at generated meshes in tests, without a GPU: perspective, a depth buffer,
 * Lambert light from the sun plus sky, bark brown and leaves green, leaf cards cut to a leaf shape by their UVs.
 */
public final class SoftwareRender {

    private final int width;
    private final int height;
    private final float[] depth;
    private final int[] color;
    private final int[] tag;
    private int currentTag;
    // camera
    private double ex;
    private double ey;
    private double ez;
    private double[] right = new double[3];
    private double[] up = new double[3];
    private double[] forward = new double[3];
    private double focal;
    private boolean flip;

    public SoftwareRender(int width, int height) {
        this.width = width;
        this.height = height;
        this.depth = new float[width * height];
        this.color = new int[width * height];
        this.tag = new int[width * height];
    }

    /** Clears to a sky gradient and sets the camera at e looking at t, vertical field of view fov radians. */
    public void camera(double ex, double ey, double ez, double tx, double ty, double tz, double fov) {
        this.ex = ex;
        this.ey = ey;
        this.ez = ez;
        double fx = tx - ex;
        double fy = ty - ey;
        double fz = tz - ez;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        forward = new double[] {fx / fl, fy / fl, fz / fl};
        right = normalize(cross(forward, new double[] {0, 1, 0}));
        up = cross(right, forward);
        focal = height / 2.0 / Math.tan(fov / 2);
        for (int y = 0; y < height; y++) {
            double t = (double) y / height;
            int r = (int) (120 + 90 * t);
            int g = (int) (160 + 70 * t);
            int b = (int) (220 + 20 * t);
            for (int x = 0; x < width; x++) {
                color[y * width + x] = r << 16 | g << 8 | b;
                depth[y * width + x] = Float.POSITIVE_INFINITY;
                tag[y * width + x] = 0;
            }
        }
    }

    /** A ground plane at y 0, a grassy colour, out to {@code extent} metres. */
    public void ground(double extent) {
        currentTag = -1;
        int tiles = 48;
        double step = 2 * extent / tiles;
        for (int i = 0; i < tiles; i++) {
            for (int j = 0; j < tiles; j++) {
                float x0 = (float) (-extent + i * step);
                float z0 = (float) (-extent + j * step);
                float x1 = (float) (x0 + step);
                float z1 = (float) (z0 + step);
                float[] p = {x0, 0, z0, x0, 0, z1, x1, 0, z1, x1, 0, z0};
                triangle(p, 0, 1, 2, 0x5a7a3a, null, 0, 1, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, 0);
                triangle(p, 0, 2, 3, 0x5a7a3a, null, 0, 1, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, 0);
            }
        }
    }

    /**
     * Draws {@code mesh} moved by (ox, 0, oz) and turned by {@code yaw} radians about y; {@code displace} (or null)
     * moves each vertex by its index first.
     */
    public void draw(ProceduralMesh mesh, double ox, double oz, double yaw, Displace displace) {
        draw(mesh, ox, oz, yaw, displace, null, Integer.MAX_VALUE);
    }

    /**
     * Draws {@code mesh} as {@link #draw(ProceduralMesh, double, double, double, Displace)} does, its submeshes from
     * {@code paletteFrom} on colored from {@code palette} by their texture coordinates, two-sided.
     */
    public void draw(ProceduralMesh mesh, double ox, double oz, double yaw, Displace displace,
        com.truesim.common.procedural.flora.Palette palette, int paletteFrom) {
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        Vec3Attribute normal = mesh.vertices().attribute(ProceduralVertex.NORMAL);
        Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
        int n = mesh.vertexCount();
        float[] p = new float[3 * n];
        float[] nn = new float[3 * n];
        float[] uvs = new float[2 * n];
        double c = Math.cos(yaw);
        double s = Math.sin(yaw);
        double[] moved = new double[3];
        for (int i = 0; i < n; i++) {
            double x = position.x(i);
            double y = position.y(i);
            double z = position.z(i);
            if (displace != null) {
                displace.move(i, x, y, z, moved);
                x = moved[0];
                y = moved[1];
                z = moved[2];
            }
            p[3 * i] = (float) (c * x + s * z + ox);
            p[3 * i + 1] = (float) y;
            p[3 * i + 2] = (float) (-s * x + c * z + oz);
            nn[3 * i] = (float) (c * normal.x(i) + s * normal.z(i));
            nn[3 * i + 1] = (float) normal.y(i);
            nn[3 * i + 2] = (float) (-s * normal.x(i) + c * normal.z(i));
            uvs[2 * i] = (float) uv.x(i);
            uvs[2 * i + 1] = (float) uv.y(i);
        }
        int[] indices = mesh.indices();
        int bark = mesh.submeshCount() == 1 ? 0x8a8580 : 0x6b4f3a; // rocks grey
        for (int sub = 0; sub < mesh.submeshCount(); sub++) {
            boolean colored = palette != null && sub >= paletteFrom;
            boolean leaves = sub == 1 && !colored;
            for (int t = mesh.submeshStart(sub); t < mesh.submeshEnd(sub); t += 3) {
                int a = indices[t];
                int b = indices[t + 1];
                int d = indices[t + 2];
                int base = leaves ? 0x3f7a2c : bark;
                if (colored) {
                    base = palette.albedo(com.truesim.common.procedural.flora.Palette.part(uvs[2 * a + 1]),
                        com.truesim.common.procedural.flora.Palette.t(uvs[2 * a])).toSrgb();
                }
                flip = colored;
                currentTag = sub + 1;
                triangle(p, a, b, d, base, nn, 0, 0, 0, a, b, d, 0, 0, 0, leaves ? uvs : null, 0, 0, 0);
                flip = false;
            }
        }
    }

    /** Moves vertex {@code i} at (x, y, z) to out. */
    public interface Displace {
        void move(int i, double x, double y, double z, double[] out);
    }

    private void triangle(float[] p, int a, int b, int c, int base, float[] normals, double fnx, double fny,
        double fnz, int na, int nb, int nc, int u1, int u2, int u3, float[] uvs, int x1, int x2, int x3) {
        double[] sa = project(p, a);
        double[] sb = project(p, b);
        double[] sc = project(p, c);
        if (sa == null || sb == null || sc == null) {
            return;
        }
        int minX = (int) Math.max(0, Math.floor(Math.min(sa[0], Math.min(sb[0], sc[0]))));
        int maxX = (int) Math.min(width - 1, Math.ceil(Math.max(sa[0], Math.max(sb[0], sc[0]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(sa[1], Math.min(sb[1], sc[1]))));
        int maxY = (int) Math.min(height - 1, Math.ceil(Math.max(sa[1], Math.max(sb[1], sc[1]))));
        double area = (sb[0] - sa[0]) * (sc[1] - sa[1]) - (sb[1] - sa[1]) * (sc[0] - sa[0]);
        if (Math.abs(area) < 1e-12 || minX > maxX || minY > maxY) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double w0 = ((sb[0] - px) * (sc[1] - py) - (sb[1] - py) * (sc[0] - px)) / area;
                double w1 = ((sc[0] - px) * (sa[1] - py) - (sc[1] - py) * (sa[0] - px)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) {
                    continue;
                }
                // perspective-correct weights
                double iz = w0 / sa[2] + w1 / sb[2] + w2 / sc[2];
                double z = 1 / iz;
                int pixel = y * width + x;
                if (z >= depth[pixel]) {
                    continue;
                }
                double q0 = w0 / sa[2] * z;
                double q1 = w1 / sb[2] * z;
                double q2 = w2 / sc[2] * z;
                if (uvs != null) {
                    double u = q0 * uvs[2 * a] + q1 * uvs[2 * b] + q2 * uvs[2 * c];
                    double v = q0 * uvs[2 * a + 1] + q1 * uvs[2 * b + 1] + q2 * uvs[2 * c + 1];
                    double lu = (u * 2) % 1;
                    double lv = (v * 2) % 1;
                    // a leaf-ish mask: a lens, with gaps between fronds
                    double half = 0.5 * Math.sin(Math.PI * lv);
                    if (Math.abs(lu - 0.5) > half || ((int) (lv * 9 + lu * 3)) % 3 == 0) {
                        continue;
                    }
                }
                double nx = fnx;
                double ny = fny;
                double nz = fnz;
                if (normals != null) {
                    nx = q0 * normals[3 * a] + q1 * normals[3 * b] + q2 * normals[3 * c];
                    ny = q0 * normals[3 * a + 1] + q1 * normals[3 * b + 1] + q2 * normals[3 * c + 1];
                    nz = q0 * normals[3 * a + 2] + q1 * normals[3 * b + 2] + q2 * normals[3 * c + 2];
                    double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    nx /= l;
                    ny /= l;
                    nz /= l;
                }
                // two-sided: face the camera
                double vx = ex - p[3 * a];
                double vy = ey - p[3 * a + 1];
                double vz = ez - p[3 * a + 2];
                if (nx * vx + ny * vy + nz * vz < 0 && (uvs != null || flip)) {
                    nx = -nx;
                    ny = -ny;
                    nz = -nz;
                }
                double light = 0.35 + 0.65 * Math.max(0, nx * 0.4 + ny * 0.8 + nz * 0.45) + 0.1 * ny;
                int r = Math.min(255, (int) (((base >> 16) & 255) * light));
                int g = Math.min(255, (int) (((base >> 8) & 255) * light));
                int bl = Math.min(255, (int) ((base & 255) * light));
                depth[pixel] = (float) z;
                color[pixel] = r << 16 | g << 8 | bl;
                tag[pixel] = currentTag;
            }
        }
    }

    private double[] project(float[] p, int i) {
        double dx = p[3 * i] - ex;
        double dy = p[3 * i + 1] - ey;
        double dz = p[3 * i + 2] - ez;
        double z = dx * forward[0] + dy * forward[1] + dz * forward[2];
        if (z < 0.05) {
            return null;
        }
        double x = dx * right[0] + dy * right[1] + dz * right[2];
        double y = dx * up[0] + dy * up[1] + dz * up[2];
        return new double[] {width / 2.0 + focal * x / z, height / 2.0 - focal * y / z, z};
    }

    /** Pixels whose nearest surface is submesh {@code submesh} of a drawn mesh (-1: none of them, the ground's). */
    public int pixels(int submesh) {
        int n = 0;
        for (int t : tag) {
            n += t == submesh + 1 ? 1 : 0;
        }
        return n;
    }

    /** Pixels covered by any drawn mesh (not sky, not ground). */
    public int covered() {
        int n = 0;
        for (int t : tag) {
            n += t > 0 ? 1 : 0;
        }
        return n;
    }

    /** The color at a pixel, 0xRRGGBB. */
    public int rgb(int x, int y) {
        return color[y * width + x];
    }

    /** The submesh (plus one) seen at a pixel: 0 sky, -1 ground. */
    public int tag(int x, int y) {
        return tag[y * width + x];
    }

    public BufferedImage image() {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, width, height, color, 0, width);
        return out;
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        ImageIO.write(image(), "png", file.toFile());
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] {v[0] / l, v[1] / l, v[2] / l};
    }
}
