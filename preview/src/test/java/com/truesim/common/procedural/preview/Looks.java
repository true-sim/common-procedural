package com.truesim.common.procedural.preview;

import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.random.Noise;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import com.truesim.common.procedural.tree.Foliage;
import com.truesim.common.procedural.tree.Tree;
import com.truesim.common.procedural.tree.TreeSpecies;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Stand-in textures for the preview, drawn on the CPU: a leaf atlas per species (2 x 2 tiles: spring, summer, autumn,
 * winter, as the presets' foliage says), a bark texture per species (furrows along v, so its continuity along branches
 * shows; birch white with dark marks), a rock texture, and a billboard's capture of a tree from the side.
 */
final class Looks {

    private static final int TILE = 256;

    private Looks() {
    }

    /** The species' needle or leaf colors per season, as sRGB. */
    private static int[] seasonColors(TreeSpecies species) {
        return switch (species.name()) {
            case "spruce", "mugo" -> new int[] {0x3d6b2a, 0x24451c, 0x24451c, 0x1d3a18};
            case "fir" -> new int[] {0x427035, 0x284d24, 0x284d24, 0x213f1d};
            case "pine" -> new int[] {0x4f7a45, 0x3b5f3a, 0x3b5f3a, 0x33533a};
            case "larch" -> new int[] {0x8cbf4a, 0x5f8f35, 0xd99a2b, 0x5f8f35};
            case "beech" -> new int[] {0x8fc24d, 0x4d7a2a, 0xb8642a, 0x4d7a2a};
            case "birch" -> new int[] {0x9ccc58, 0x5d8f30, 0xd8b23a, 0x5d8f30};
            default -> new int[] {0x86b84a, 0x4f7f2b, 0xc79a35, 0x4f7f2b};
        };
    }

    /** The species' leaf atlas: 512 x 512, alpha 0 or 1. */
    static BufferedImage leafAtlas(TreeSpecies species) {
        BufferedImage atlas = new BufferedImage(2 * TILE, 2 * TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int[] colors = seasonColors(species);
        Foliage foliage = species.foliage();
        for (int tile = 0; tile < 4; tile++) {
            AffineTransform saved = g.getTransform();
            // Tile (col, row); v = 0 (attached) at the tile's top in texture space, the card's far end at its bottom
            g.translate((tile % 2) * TILE, (tile / 2) * TILE);
            Random random = new Random(17 + tile);
            switch (foliage.kind()) {
                case NEEDLE_SPRAY -> spray(g, colors[tile], random);
                case NEEDLE_TUFT -> tuft(g, colors[tile], random);
                case LEAF_CLUSTER -> cluster(g, colors[tile], random);
            }
            g.setTransform(saved);
        }
        g.dispose();
        return atlas;
    }

    private static java.awt.Color shade(int rgb, double factor) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * factor);
        int gg = (int) Math.min(255, ((rgb >> 8) & 255) * factor);
        int b = (int) Math.min(255, (rgb & 255) * factor);
        return new java.awt.Color(r, gg, b);
    }

    /** A shoot along the tile, needles to both sides. */
    private static void spray(Graphics2D g, int color, Random random) {
        g.setStroke(new BasicStroke(3));
        g.setColor(shade(0x4a3020, 1));
        g.draw(new Line2D.Double(TILE / 2.0, 4, TILE / 2.0, TILE - 4));
        g.setStroke(new BasicStroke(4));
        for (int i = 0; i < 70; i++) {
            double y = 8 + (TILE - 24) * i / 70.0;
            double width = (TILE / 2.0 - 10) * (0.55 + 0.45 * Math.sin(Math.PI * Math.min(1, y / (TILE * 0.9))));
            for (int side = -1; side <= 1; side += 2) {
                g.setColor(shade(color, 0.8 + 0.4 * random.nextDouble()));
                double length = width * (0.7 + 0.3 * random.nextDouble());
                g.draw(new Line2D.Double(TILE / 2.0, y, TILE / 2.0 + side * length, y + 0.45 * length));
            }
        }
    }

    /** Needles fanning out from where the tuft is attached. */
    private static void tuft(Graphics2D g, int color, Random random) {
        g.setStroke(new BasicStroke(3.5f));
        for (int i = 0; i < 90; i++) {
            double angle = (random.nextDouble() - 0.5) * 1.6;
            double length = TILE * (0.55 + 0.4 * random.nextDouble());
            double x0 = TILE / 2.0 + (random.nextDouble() - 0.5) * 30;
            g.setColor(shade(color, 0.75 + 0.5 * random.nextDouble()));
            g.draw(new Line2D.Double(x0, 6, x0 + Math.sin(angle) * length, 6 + Math.cos(angle) * length));
        }
    }

    /** Leaves on a twig: ellipses fanning out along it. */
    private static void cluster(Graphics2D g, int color, Random random) {
        g.setStroke(new BasicStroke(3));
        g.setColor(shade(0x4a3020, 1));
        g.draw(new Line2D.Double(TILE / 2.0, 2, TILE / 2.0, TILE * 0.8));
        for (int i = 0; i < 16; i++) {
            double y = 20 + (TILE - 70) * random.nextDouble();
            double angle = (random.nextDouble() - 0.5) * 2.4;
            double length = 70 + 40 * random.nextDouble();
            AffineTransform saved = g.getTransform();
            g.translate(TILE / 2.0, y);
            g.rotate(-angle);
            g.setColor(shade(color, 0.75 + 0.5 * random.nextDouble()));
            g.fill(new Ellipse2D.Double(-length * 0.22, 0, length * 0.44, length));
            g.setTransform(saved);
        }
    }

    /** Bark: 256 x 256, furrows along v. */
    static BufferedImage bark(TreeSpecies species) {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Noise noise = Noise.seeded(species.name().hashCode());
        boolean birch = species.name().equals("birch");
        int base = switch (species.name()) {
            case "spruce" -> 0x6a4a38;
            case "fir" -> 0x8a8478;
            case "larch" -> 0x7a4632;
            case "pine" -> 0x8a5536;
            case "beech" -> 0x8f8d86;
            case "birch" -> 0xe8e4dc;
            default -> 0x6b5545;
        };
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                double u = x / 256.0;
                double v = y / 256.0;
                // tileable: noise on a torus
                double cu = Math.cos(2 * Math.PI * u);
                double su = Math.sin(2 * Math.PI * u);
                double cv = Math.cos(2 * Math.PI * v);
                double sv = Math.sin(2 * Math.PI * v);
                double n = noise.fractal(cu * 3, su * 3, cv * 0.8 + sv * 0.8, 3);
                double furrow = 0.5 + 0.5 * Math.sin(2 * Math.PI * (6 * u + 0.6 * noise.simplex(cv * 0.5, sv * 0.5, cu)));
                double value = birch ? 1 - 0.7 * Math.max(0, noise.simplex(cu * 1.5, su * 1.5, cv * 4 + sv * 4) - 0.35)
                    : 0.55 + 0.3 * furrow + 0.25 * n;
                image.setRGB(x, y, 0xff000000 | shade(base, value).getRGB() & 0xffffff);
            }
        }
        return image;
    }

    /** Rock: lichen-flecked grey granite. */
    static BufferedImage rock() {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Noise noise = Noise.seeded(5);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                double cu = Math.cos(2 * Math.PI * x / 256.0);
                double su = Math.sin(2 * Math.PI * x / 256.0);
                double cv = Math.cos(2 * Math.PI * y / 256.0);
                double sv = Math.sin(2 * Math.PI * y / 256.0);
                double n = noise.fractal(cu * 2 + 9, su * 2, cv * 2 + sv * 2, 4);
                double lichen = noise.simplex(cu * 1.2, su * 1.2 + 5, cv * 1.2 - sv);
                int base = lichen > 0.45 ? 0x8a8a52 : 0x8e8b86;
                image.setRGB(x, y, 0xff000000 | shade(base, 0.8 + 0.3 * n).getRGB() & 0xffffff);
            }
        }
        return image;
    }

    /**
     * The tree from the side (looking along -z), orthographic, over the billboard quads' extent: its bark in the bark
     * texture's mean color, its leaf cards cut by the atlas's alpha, simply lit; transparent elsewhere.
     */
    static BufferedImage capture(Tree tree, BufferedImage atlas, int size) {
        ProceduralMesh mesh = tree.lods().getFirst();
        double radius = tree.crownRadius();
        double top = tree.height();
        int w = size;
        int h = size;
        float[] depth = new float[w * h];
        java.util.Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        int[] color = new int[w * h];
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        Vec3Attribute normal = mesh.vertices().attribute(ProceduralVertex.NORMAL);
        Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
        int[] idx = mesh.indices();
        for (int t = 0; t < idx.length; t += 3) {
            boolean leaf = t >= mesh.submeshStart(1);
            double[] sx = new double[3];
            double[] sy = new double[3];
            double[] sz = new double[3];
            for (int k = 0; k < 3; k++) {
                int v = idx[t + k];
                sx[k] = (position.x(v) / radius * 0.5 + 0.5) * w;
                sy[k] = position.y(v) / top * h; // row 0 at the ground: the billboard's v 0
                sz[k] = position.z(v);
            }
            double area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sy[1] - sy[0]) * (sx[2] - sx[0]);
            if (Math.abs(area) < 1e-9) {
                continue;
            }
            int x0 = (int) Math.max(0, Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
            int x1 = (int) Math.min(w - 1, Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
            int y0 = (int) Math.max(0, Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
            int y1 = (int) Math.min(h - 1, Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    double px = x + 0.5;
                    double py = y + 0.5;
                    double a = ((sx[1] - px) * (sy[2] - py) - (sy[1] - py) * (sx[2] - px)) / area;
                    double b = ((sx[2] - px) * (sy[0] - py) - (sy[2] - py) * (sx[0] - px)) / area;
                    double c = 1 - a - b;
                    if (a < 0 || b < 0 || c < 0) {
                        continue;
                    }
                    double z = a * sz[0] + b * sz[1] + c * sz[2];
                    if (z <= depth[y * w + x]) {
                        continue;
                    }
                    int va = idx[t];
                    int vb = idx[t + 1];
                    int vc = idx[t + 2];
                    int rgb;
                    if (leaf) {
                        double u = a * uv.x(va) + b * uv.x(vb) + c * uv.x(vc);
                        double v = a * uv.y(va) + b * uv.y(vb) + c * uv.y(vc);
                        int texel = atlas.getRGB((int) Math.clamp(u * atlas.getWidth(), 0, atlas.getWidth() - 1),
                            (int) Math.clamp(v * atlas.getHeight(), 0, atlas.getHeight() - 1));
                        if ((texel >>> 24) < 128) {
                            continue;
                        }
                        rgb = texel;
                    } else {
                        rgb = 0x5a4434;
                    }
                    double ny = a * normal.y(va) + b * normal.y(vb) + c * normal.y(vc);
                    double nz = a * normal.z(va) + b * normal.z(vb) + c * normal.z(vc);
                    double light = 0.6 + 0.25 * ny + 0.25 * Math.abs(nz);
                    depth[y * w + x] = (float) z;
                    color[y * w + x] = 0xff000000 | shade(rgb & 0xffffff, light).getRGB() & 0xffffff;
                }
            }
        }
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, w, h, color, 0, w);
        return image;
    }
}
