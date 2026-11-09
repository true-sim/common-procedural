package com.truesim.common.procedural.water;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import com.truesim.effects.terrain.data.TerrainSurface;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A film of the water network over Lauterbrunnen (effects-terrain's cache), 1280 x 720 at 30 frames a second into
 * {@code -Ppictures=<dir>} (default {@code build/film}): the channel network over the valley with its flow, the
 * Weisse Luetschine on the valley floor, the Staubbach's stream and surfaces close up, and the Staubbach Fall's sheet,
 * jets, spray and mist from the side. {@code ffmpeg -framerate 30 -i frame%04d.png -c:v libx264 -crf 20 -pix_fmt
 * yuv420p water.mp4} makes the film.
 */
class LauterbrunnenFilm {

    private static final int W = 1280;
    private static final int H = 720;
    private static final int FPS = 30;

    /** A hillshade over a square, at a resolution. */
    private record Shade(double x0, double z0, double size, BufferedImage image) {
    }

    /** A view: its centre and the metres across the frame. */
    private record View(double x, double z, double width) {

        double mpp() {
            return width / W;
        }

        double x0() {
            return x - width / 2;
        }

        double z0() {
            return z - width * H / W / 2;
        }

        static View lerp(View a, View b, double t) {
            double s = t * t * (3 - 2 * t);
            double w = Math.exp(Math.log(a.width) + s * (Math.log(b.width) - Math.log(a.width)));
            // keep the zoom's fixed point moving smoothly with the width
            double f = (a.width - w) / (a.width - b.width + 1e-9);
            return new View(a.x + f * (b.x - a.x), a.z + f * (b.z - a.z), w);
        }
    }

    private EcologyTerrain terrain;
    private WaterNetwork network;
    private final List<Shade> shades = new ArrayList<>();
    private double[][] travel;
    private Waterfall staubbach;
    private final java.util.Map<WaterNetwork.WaterChunk, ProceduralMesh> surfaces = new java.util.HashMap<>();

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_PICTURES", matches = "true")
    void film() throws Exception {
        TerrainSurface surface = Lauterbrunnen.surface();
        assumeTrue(surface != null, "effects-terrain's cache holds no Lauterbrunnen");
        Path out = Path.of(System.getProperty("truesim.pictures", "build/film"));
        Files.createDirectories(out);
        terrain = Lauterbrunnen.terrain(surface);
        network = WaterNetwork.build(terrain, Lauterbrunnen.REGION, WaterParameters.DEFAULTS);
        for (Waterfall f : network.falls()) {
            Channel c = network.channels().get(f.channel());
            double dx = c.x(f.lip()) + 280;
            double dz = c.z(f.lip()) - 500;
            if (f.free() && dx * dx + dz * dz < 500 * 500 && (staubbach == null || f.height() > staubbach.height())) {
                staubbach = f;
            }
        }
        travel = new double[network.channels().size()][];
        for (Channel c : network.channels()) {
            double[] t = new double[c.count()];
            for (int i = 1; i < c.count(); i++) {
                double v = 0.5 * (c.speed(i) + c.speed(i - 1));
                t[i] = t[i - 1] + (c.distance(i) - c.distance(i - 1)) / v;
            }
            travel[c.id()] = t;
        }
        WaterRegion r = Lauterbrunnen.REGION;
        shades.add(shade(r.x0(), r.z0(), Math.max(r.width(), r.depth()), 10));
        shades.add(shade(-2400, -1200, 4800, 2.5));
        Channel sc = network.channels().get(staubbach.channel());
        double sx = sc.x(staubbach.lip());
        double sz = sc.z(staubbach.lip());
        shades.add(shade(sx - 450, sz - 450, 900, 0.6));
        View overview = new View(-500, 3600, 15000);
        View valley = new View(-200, 600, 3600);
        View close = new View(sx + 90, sz + 10, 520);
        int frame = 0;
        for (int k = 0; k < 5 * FPS; k++) {
            frame = map(out, frame, overview, k, 120, "The channel network over Lauterbrunnen", String.format(
                Locale.ROOT, "%d reaches, %.0f km, from the terrain's flow (Priority-Flood, catchments over %.2f km2);"
                + " width by discharge, flow 120x", network.channels().size(), length() / 1000,
                WaterParameters.DEFAULTS.flow().channelArea() / 1e6));
        }
        for (int k = 0; k < 3 * FPS; k++) {
            frame = map(out, frame, View.lerp(overview, valley, k / (3.0 * FPS - 1)), 5 * FPS + k, 120 - 100.0 * k
                / (3 * FPS), "Into the valley", "the Weisse Luetschine and its side streams");
        }
        Channel main = mainRiver();
        for (int k = 0; k < 5 * FPS; k++) {
            frame = map(out, frame, valley, 8 * FPS + k, 20, "The valley floor: the Weisse Luetschine", String.format(
                Locale.ROOT, "%.1f m3/s at the north edge, %.0f m wide; colour by speed; Staubbach Fall marked;"
                + " flow 20x", main.discharge(main.count() - 1), main.width(main.count() - 1)));
        }
        View river = riverView();
        shades.add(shade(river.x() - 200, river.z() - 200, 400, 0.25));
        for (int k = 0; k < 3 * FPS; k++) {
            frame = map(out, frame, View.lerp(valley, river, k / (3.0 * FPS - 1)), 13 * FPS + k, 20 - 17.0 * k
                / (3 * FPS), "Onto the river", "its surface meshes, chunk by chunk");
        }
        for (int k = 0; k < 5 * FPS; k++) {
            frame = map(out, frame, river, 16 * FPS + k, 3, "The Weisse Luetschine's surface and bed stones",
                "triangles coloured by speed, white by foam (Froude number, emergent stones); stones sized by the "
                + "flow's shear (Shields); flow 3x");
        }
        for (int k = 0; k < 3 * FPS; k++) {
            frame = map(out, frame, View.lerp(river, close, k / (3.0 * FPS - 1)), 21 * FPS + k, 3,
                "To the Staubbach", "the stream off the Muerren terrace and over the cliff");
        }
        for (int k = 0; k < 4 * FPS; k++) {
            frame = map(out, frame, close, 24 * FPS + k, 2, "The Staubbach's lip, foot and mist",
                "the stream's surface (white: foam), the fall's line, where spray comes off it and the mist at its "
                + "foot; flow 2x");
        }
        for (int k = 0; k < 9 * FPS; k++) {
            frame = profile(out, frame, k);
        }
        System.out.println("film: " + frame + " frames into " + out.toAbsolutePath());
    }

    private Shade shade(double x0, double z0, double size, double cell) {
        int n = (int) Math.round(size / cell);
        float[] h = new float[(n + 2) * (n + 2)];
        IntStream.range(0, n + 2).parallel().forEach(j -> {
            for (int i = 0; i < n + 2; i++) {
                h[j * (n + 2) + i] = (float) terrain.height(x0 + (i - 0.5) * cell, z0 + (j - 0.5) * cell);
            }
        });
        BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        double lx = -0.5;
        double ly = 0.7;
        double lz = -0.5;
        double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int a = (j + 1) * (n + 2) + i + 1;
                double gx = (h[a + 1] - h[a - 1]) / (2 * cell);
                double gz = (h[a + n + 2] - h[a - n - 2]) / (2 * cell);
                double nl = Math.sqrt(gx * gx + 1 + gz * gz);
                double lit = Math.max(0, (-gx * lx + ly - gz * lz) / (nl * ll));
                double alt = h[a];
                double[] tint = alt < 1100 ? mix(new double[] {0.55, 0.66, 0.42}, new double[] {0.47, 0.58, 0.38},
                    (alt - 700) / 400) : alt < 2000 ? mix(new double[] {0.47, 0.58, 0.38},
                    new double[] {0.62, 0.58, 0.48}, (alt - 1100) / 900) : mix(new double[] {0.62, 0.58, 0.48},
                    new double[] {0.93, 0.94, 0.96}, (alt - 2000) / 1200);
                double shadeValue = 0.35 + 0.85 * lit;
                image.setRGB(i, j, rgb(tint[0] * shadeValue, tint[1] * shadeValue, tint[2] * shadeValue));
            }
        }
        return new Shade(x0, z0, size, image);
    }

    private static double[] mix(double[] a, double[] b, double t) {
        double s = Math.clamp(t, 0, 1);
        return new double[] {a[0] + s * (b[0] - a[0]), a[1] + s * (b[1] - a[1]), a[2] + s * (b[2] - a[2])};
    }

    private static int rgb(double r, double g, double b) {
        int ri = (int) Math.round(255 * Math.clamp(r, 0, 1));
        int gi = (int) Math.round(255 * Math.clamp(g, 0, 1));
        int bi = (int) Math.round(255 * Math.clamp(b, 0, 1));
        return ri << 16 | gi << 8 | bi;
    }

    private double length() {
        double length = 0;
        for (Channel c : network.channels()) {
            length += c.distance(c.count() - 1);
        }
        return length;
    }

    /** A stretch of the main river near the village, close. */
    private View riverView() {
        Channel best = null;
        int at = 0;
        double nearest = Double.POSITIVE_INFINITY;
        for (Channel c : network.channels()) {
            for (int i = 0; i < c.count(); i++) {
                double d = Math.abs(c.z(i) - 300);
                if (c.discharge(i) > 3 && d < nearest) {
                    nearest = d;
                    best = c;
                    at = i;
                }
            }
        }
        return new View(best.x(at), best.z(at), 150);
    }

    private Channel mainRiver() {
        Channel best = null;
        for (Channel c : network.channels()) {
            if (best == null || c.discharge(c.count() - 1) > best.discharge(best.count() - 1)) {
                best = c;
            }
        }
        return best;
    }

    private static Color speedColor(double v, float alpha) {
        double t = Math.clamp(v / 4, 0, 1);
        return new Color((float) (0.10 + 0.7 * t), (float) (0.35 + 0.6 * t), (float) (0.85 + 0.15 * t), alpha);
    }

    private int map(Path out, int frame, View view, int tick, double timeLapse, String title, String subtitle)
        throws Exception {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(new Color(40, 44, 48));
        g.fillRect(0, 0, W, H);
        double mpp = view.mpp();
        AffineTransform world = new AffineTransform(1 / mpp, 0, 0, 1 / mpp, -view.x0() / mpp, -view.z0() / mpp);
        for (Shade s : shades) {
            double px = s.image().getWidth() / s.size();
            if (s.size() / mpp < 40) {
                continue;
            }
            AffineTransform t = new AffineTransform(world);
            t.translate(s.x0(), s.z0());
            t.scale(1 / px, 1 / px);
            g.drawImage(s.image(), t, null);
        }
        boolean meshes = mpp < 1.2;
        double time = tick / (double) FPS * timeLapse;
        Rectangle2D visible = new Rectangle2D.Double(view.x0() - 50, view.z0() - 50, view.width() + 100,
            view.width() * H / W + 100);
        if (meshes) {
            drawSurfaces(g, view, visible);
        } else {
            drawChannels(g, world, mpp, visible);
        }
        drawParticles(g, view, visible, time, mpp);
        drawFalls(g, view, mpp, meshes);
        caption(g, title, subtitle);
        scaleBar(g, mpp);
        g.dispose();
        ImageIO.write(image, "png", out.resolve(String.format(Locale.ROOT, "frame%04d.png", frame)).toFile());
        return frame + 1;
    }

    private void drawChannels(Graphics2D g, AffineTransform world, double mpp, Rectangle2D visible) {
        for (Channel c : network.channels()) {
            double minPx = 0.6 + 0.45 * c.order();
            for (int i = 0; i + 1 < c.count(); i++) {
                if (!visible.contains(c.x(i), c.z(i)) && !visible.contains(c.x(i + 1), c.z(i + 1))) {
                    continue;
                }
                double px = Math.max(minPx, c.width(i) / mpp);
                g.setStroke(new BasicStroke((float) px, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(c.fall(i) >= 0 ? new Color(255, 160, 60) : speedColor(c.speed(i), 1));
                Shape line = world.createTransformedShape(new Line2D.Double(c.x(i), c.z(i), c.x(i + 1), c.z(i + 1)));
                g.draw(line);
            }
        }
    }

    private void drawSurfaces(Graphics2D g, View view, Rectangle2D visible) {
        double chunk = network.parameters().surface().chunk();
        double mpp = view.mpp();
        for (WaterNetwork.WaterChunk ch : network.chunks()) {
            if (!visible.intersects(ch.originX(), ch.originZ(), chunk, chunk)) {
                continue;
            }
            ProceduralMesh mesh = surfaces.computeIfAbsent(ch, network::surface);
            Vec3Attribute p = mesh.vertices().attribute(ProceduralVertex.POSITION);
            Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
            Vec4Attribute color = mesh.vertices().attribute(ProceduralVertex.WIND);
            int[] idx = mesh.indices();
            for (int t = 0; t < idx.length; t += 3) {
                Path2D tri = new Path2D.Double();
                double foam = 0;
                double speed = 0;
                for (int k = 0; k < 3; k++) {
                    int v = idx[t + k];
                    double x = (p.x(v) + ch.originX() - view.x0()) / mpp;
                    double y = (p.z(v) + ch.originZ() - view.z0()) / mpp;
                    if (k == 0) {
                        tri.moveTo(x, y);
                    } else {
                        tri.lineTo(x, y);
                    }
                    foam += color.x(v) / 3;
                    speed += uv.x(v) / 3;
                }
                tri.closePath();
                Color base = speedColor(speed, 1);
                float f = (float) Math.clamp(foam, 0, 1);
                g.setColor(new Color(base.getRed() / 255f * (1 - f) + f, base.getGreen() / 255f * (1 - f) + f,
                    base.getBlue() / 255f * (1 - f) + f));
                g.fill(tri);
            }
            g.setColor(new Color(0, 0, 0, 40));
            g.setStroke(new BasicStroke(0.5f));
            for (int t = 0; t < idx.length; t += 3) {
                Path2D tri = new Path2D.Double();
                for (int k = 0; k < 3; k++) {
                    int v = idx[t + k];
                    double x = (p.x(v) + ch.originX() - view.x0()) / mpp;
                    double y = (p.z(v) + ch.originZ() - view.z0()) / mpp;
                    if (k == 0) {
                        tri.moveTo(x, y);
                    } else {
                        tri.lineTo(x, y);
                    }
                }
                tri.closePath();
                g.draw(tri);
            }
        }
        // stones: grey discs, the emergent ones outlined white
        for (BedStone s : network.stones()) {
            if (!visible.contains(s.x(), s.z())) {
                continue;
            }
            double r = Math.max(2, 0.5 * s.size() / mpp);
            double x = (s.x() - view.x0()) / mpp;
            double y = (s.z() - view.z0()) / mpp;
            g.setColor(new Color(120, 112, 104));
            g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
            g.setColor(s.emergent() ? Color.WHITE : new Color(60, 56, 52));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
    }

    private void drawParticles(Graphics2D g, View view, Rectangle2D visible, double time, double mpp) {
        double spacing = Math.max(6, 22 * mpp);
        double radius = Math.max(1.3, Math.min(3, 0.3 / mpp));
        for (Channel c : network.channels()) {
            double[] t = travel[c.id()];
            double total = t[t.length - 1];
            double length = c.distance(c.count() - 1);
            int n = Math.max(1, (int) (length / spacing));
            SplittableRandom random = new SplittableRandom(c.id() * 31L + 7);
            for (int k = 0; k < n; k++) {
                double phase = (k + 0.5 * random.nextDouble()) / n;
                double across = (random.nextDouble() * 2 - 1) * 0.7;
                double tau = (phase * total + time) % total;
                int i = search(t, tau);
                if (c.fall(i) >= 0) {
                    continue;
                }
                double f = t[i + 1] > t[i] ? (tau - t[i]) / (t[i + 1] - t[i]) : 0;
                double x = c.x(i) + f * (c.x(i + 1) - c.x(i)) - c.directionZ(i) * across * c.width(i) / 2;
                double z = c.z(i) + f * (c.z(i + 1) - c.z(i)) + c.directionX(i) * across * c.width(i) / 2;
                if (!visible.contains(x, z)) {
                    continue;
                }
                double px = (x - view.x0()) / mpp;
                double py = (z - view.z0()) / mpp;
                g.setColor(new Color(1f, 1f, 1f, 0.85f));
                g.fill(new Ellipse2D.Double(px - radius, py - radius, 2 * radius, 2 * radius));
            }
        }
    }

    private static int search(double[] t, double tau) {
        int lo = 0;
        int hi = t.length - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (t[mid] <= tau) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private void drawFalls(Graphics2D g, View view, double mpp, boolean detail) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        for (Waterfall f : network.falls()) {
            if (!f.free() || f.height() < 40 && f != staubbach) {
                continue;
            }
            Channel c = network.channels().get(f.channel());
            double lx = (c.x(f.lip()) - view.x0()) / mpp;
            double ly = (c.z(f.lip()) - view.z0()) / mpp;
            double fx = (c.x(f.foot()) - view.x0()) / mpp;
            double fy = (c.z(f.foot()) - view.z0()) / mpp;
            if (lx < -200 || ly < -200 || lx > W + 200 || ly > H + 200) {
                continue;
            }
            if (detail || f == staubbach) {
                Waterfall.Mist m = f.mist();
                double rx = m.radiusX() / mpp;
                double cx = (m.x() - view.x0()) / mpp;
                double cy = (m.z() - view.z0()) / mpp;
                g.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) Math.max(2, rx),
                    new float[] {0, 1}, new Color[] {new Color(1f, 1f, 1f, detail ? 0.55f : 0.35f),
                        new Color(1f, 1f, 1f, 0f)}));
                g.fill(new Ellipse2D.Double(cx - rx, cy - rx, 2 * rx, 2 * rx));
            }
            if (detail) {
                for (Waterfall.Spray s : f.spray()) {
                    double r = Math.clamp(3 + 60 * s.rate(), 3, 9);
                    double sx = (s.x() - view.x0()) / mpp;
                    double sy = (s.z() - view.z0()) / mpp;
                    g.setColor(new Color(0.85f, 0.95f, 1f, 0.8f));
                    g.fill(new Ellipse2D.Double(sx - r, sy - r, 2 * r, 2 * r));
                }
                Waterfall.Path path = f.path();
                Path2D line = new Path2D.Double();
                for (int k = 0; k < path.count(); k++) {
                    double px = (path.x()[k] - view.x0()) / mpp;
                    double py = (path.z()[k] - view.z0()) / mpp;
                    if (k == 0) {
                        line.moveTo(px, py);
                    } else {
                        line.lineTo(px, py);
                    }
                }
                g.setColor(new Color(255, 170, 70));
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(line);
            }
            g.setColor(new Color(255, 160, 60));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Line2D.Double(lx, ly, fx, fy));
            Path2D tri = new Path2D.Double();
            tri.moveTo(lx, ly - 7);
            tri.lineTo(lx - 6, ly + 5);
            tri.lineTo(lx + 6, ly + 5);
            tri.closePath();
            g.fill(tri);
            if (f == staubbach || detail) {
                String label = (f == staubbach ? "Staubbach " : "") + String.format(Locale.ROOT, "%.0f m", f.height());
                g.setColor(new Color(0, 0, 0, 160));
                g.drawString(label, (float) lx + 10, (float) ly - 7);
                g.setColor(Color.WHITE);
                g.drawString(label, (float) lx + 9, (float) ly - 8);
            }
        }
    }

    /** The Staubbach Fall from the side: the cliff's profile, the sheet, its jets, drops, spray and mist. */
    private int profile(Path out, int frame, int tick) throws Exception {
        Channel c = network.channels().get(staubbach.channel());
        double lx = c.x(staubbach.lip());
        double ly = c.surface(staubbach.lip());
        double lz = c.z(staubbach.lip());
        double dx = c.directionX(staubbach.lip());
        double dz = c.directionZ(staubbach.lip());
        Waterfall.Path path = staubbach.path();
        double footY = path.y()[path.count() - 1];
        double top = ly + 40;
        double bottom = footY - 30;
        double mpp = (top - bottom) / (H - 120.0);
        double s0 = -0.45 * W * mpp;
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // sky
        for (int y = 0; y < H; y++) {
            double t = y / (double) H;
            g.setColor(new Color(rgb(0.45 + 0.3 * t, 0.62 + 0.25 * t, 0.85 + 0.1 * t)));
            g.drawLine(0, y, W, y);
        }
        // the ground along the fall's direction
        Path2D ground = new Path2D.Double();
        ground.moveTo(0, H);
        for (int px = 0; px <= W; px += 2) {
            double s = s0 + px * mpp;
            double h = terrain.height(lx + dx * s, lz + dz * s);
            ground.lineTo(px, 60 + (top - h) / mpp);
        }
        ground.lineTo(W, H);
        ground.closePath();
        g.setColor(new Color(92, 98, 84));
        g.fill(ground);
        g.setColor(new Color(60, 64, 56));
        g.setStroke(new BasicStroke(2f));
        g.draw(ground);
        // mist: a soft ellipse at the foot
        Waterfall.Mist m = staubbach.mist();
        double ms = (m.x() - lx) * dx + (m.z() - lz) * dz;
        double mx = (ms - s0) / mpp;
        double my = 60 + (top - m.y()) / mpp;
        double rx = m.radiusX() / mpp;
        double ry = m.radiusY() / mpp;
        double pulse = 1 + 0.05 * Math.sin(tick / 20.0);
        g.setPaint(new RadialGradientPaint((float) mx, (float) my, (float) (rx * pulse), new float[] {0, 0.6f, 1},
            new Color[] {new Color(1f, 1f, 1f, 0.6f), new Color(1f, 1f, 1f, 0.25f), new Color(1f, 1f, 1f, 0f)}));
        AffineTransform keep = g.getTransform();
        g.translate(mx, my);
        g.scale(1, ry / rx);
        g.translate(-mx, -my);
        g.fill(new Ellipse2D.Double(mx - rx * pulse, my - rx * pulse, 2 * rx * pulse, 2 * rx * pulse));
        g.setTransform(keep);
        // the coherent sheet down to its break-up, then drops falling along the path's timing, thinning as they
        // atomise; the path's time gives each drop's place
        double total = path.time()[path.count() - 1];
        double now = tick / (double) FPS;
        SplittableRandom random = new SplittableRandom(11);
        int drops = 2600;
        for (int k = 0; k < drops; k++) {
            double born = random.nextDouble() * total;
            double lateral = Math.abs(random.nextGaussian());
            double life = (now + born) % total;
            int i = timeIndex(path, life);
            double fallen = path.fallen(i);
            double atom = 1 - Math.exp(-fallen / WaterParameters.DEFAULTS.falls().atomisation());
            double spreadOut = Math.max(0, fallen - staubbach.breakup()) * 0.08;
            double s = (path.x()[i] - lx) * dx + (path.z()[i] - lz) * dz + lateral * spreadOut;
            double px = (s - s0) / mpp;
            double py = 60 + (top - path.y()[i]) / mpp;
            boolean mist = random.nextDouble() < atom;
            float alpha = mist ? 0.25f : 0.9f;
            double r = mist ? 2.5 : 1.3;
            g.setColor(new Color(1f, 1f, 1f, alpha));
            g.fill(new Ellipse2D.Double(px - r, py - r, 2 * r, 2 * r));
        }
        Path2D sheet = new Path2D.Double();
        for (int k = 0; k < path.count() && path.fallen(k) <= staubbach.breakup() * 1.5; k++) {
            double s = (path.x()[k] - lx) * dx + (path.z()[k] - lz) * dz;
            double px = (s - s0) / mpp;
            double py = 60 + (top - path.y()[k]) / mpp;
            if (k == 0) {
                sheet.moveTo(px, py);
            } else {
                sheet.lineTo(px, py);
            }
        }
        g.setColor(new Color(235, 245, 255));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(sheet);
        // the stream to the lip
        Path2D stream = new Path2D.Double();
        boolean started = false;
        for (int i = Math.max(0, staubbach.lip() - 40); i <= staubbach.lip(); i++) {
            double s = (c.x(i) - lx) * dx + (c.z(i) - lz) * dz;
            double px = (s - s0) / mpp;
            double py = 60 + (top - c.surface(i)) / mpp;
            if (!started) {
                stream.moveTo(px, py);
                started = true;
            } else {
                stream.lineTo(px, py);
            }
        }
        g.setColor(new Color(60, 130, 220));
        g.draw(stream);
        g.setComposite(AlphaComposite.SrcOver);
        caption(g, "The Staubbach Fall, from the side", String.format(Locale.ROOT,
            "%.0f m free fall; %.2f m3/s leaving the lip at %.1f m/s; the sheet breaks up after %.1f m (Horeni); "
            + "%.0f %% reaches the foot as spray and mist; real time", staubbach.height(), staubbach.discharge(),
            staubbach.lipSpeed(), staubbach.breakup(), 100 * staubbach.atomised()));
        // a height scale
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        double lipPy = 60 + (top - ly) / mpp;
        double footPy = 60 + (top - footY) / mpp;
        g.draw(new Line2D.Double(W - 70, lipPy, W - 70, footPy));
        g.draw(new Line2D.Double(W - 76, lipPy, W - 64, lipPy));
        g.draw(new Line2D.Double(W - 76, footPy, W - 64, footPy));
        g.drawString(String.format(Locale.ROOT, "%.0f m", staubbach.height()), W - 62, (float) (lipPy + footPy) / 2);
        g.dispose();
        ImageIO.write(image, "png", out.resolve(String.format(Locale.ROOT, "frame%04d.png", frame)).toFile());
        return frame + 1;
    }

    private static int timeIndex(Waterfall.Path path, double t) {
        int lo = 0;
        int hi = path.count() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (path.time()[mid] <= t) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static void caption(Graphics2D g, String title, String subtitle) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, W, 54);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString(title, 16, 24);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(subtitle, 16, 44);
    }

    private static void scaleBar(Graphics2D g, double mpp) {
        double[] steps = {10, 20, 50, 100, 200, 500, 1000, 2000, 5000};
        double metres = steps[0];
        for (double s : steps) {
            if (s / mpp <= 220) {
                metres = s;
            }
        }
        int px = (int) Math.round(metres / mpp);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(12, H - 40, px + 90, 28);
        g.setColor(Color.WHITE);
        g.fillRect(20, H - 24, px, 4);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(metres >= 1000 ? (int) (metres / 1000) + " km" : (int) metres + " m", 28 + px, H - 18);
    }
}
