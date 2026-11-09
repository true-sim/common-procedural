package com.truesim.common.procedural.settlement;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.ecology.Site;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Pictures of plans from above, to look at: hillshaded ground (open water tinted blue, cleared ground lighter), roads
 * by surface, plots, footprints (a dot at the front), fences, lamps and props.
 * {@code TRUESIM_PICTURES=true ./gradlew :common-procedural-settlement:test --tests '*PlanPictures*'
 * -Ppictures=<dir>}.
 */
class PlanPictures {

    static void draw(Settlement s, Ecology ecology, Path file, int size) throws Exception {
        double extent = s.parameters().grid().extent();
        double x0 = s.originX() - extent / 2;
        double z0 = s.originZ() - extent / 2;
        double scale = size / extent;
        EcologyTerrain terrain = ecology.terrain();
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Site site = new Site();
        int step = 2;
        for (int py = 0; py < size; py += step) {
            for (int px = 0; px < size; px += step) {
                double x = x0 + (px + 0.5) / scale;
                double z = z0 + (py + 0.5) / scale;
                double e = 2;
                double gx = (terrain.height(x + e, z) - terrain.height(x - e, z)) / (2 * e);
                double gz = (terrain.height(x, z + e) - terrain.height(x, z - e)) / (2 * e);
                double l = Math.sqrt(gx * gx + gz * gz + 1);
                double shade = Math.clamp((0.6 * gx + 0.6 * gz + 1) / l / 1.2, 0.15, 1);
                double r = 0.45 * shade;
                double g = 0.62 * shade;
                double b = 0.35 * shade;
                if ((px / step + py / step) % 4 == 0) {
                    ecology.conditions(x, z, site);
                }
                double water = site.water > 0.1 ? 1 : 0;
                r = r * (1 - 0.6 * water);
                g = g * (1 - 0.3 * water);
                b = b + 0.4 * water;
                double open = s.clearing().trees(x, z);
                r = r + (1 - open) * 0.12;
                g = g + (1 - open) * 0.1;
                int rgb = (int) (255 * Math.clamp(r, 0, 1)) << 16 | (int) (255 * Math.clamp(g, 0, 1)) << 8
                    | (int) (255 * Math.clamp(b, 0, 1));
                for (int j = 0; j < step && py + j < size; j++) {
                    for (int i = 0; i < step && px + i < size; i++) {
                        image.setRGB(px + i, py + j, rgb);
                    }
                }
            }
        }
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Settlement.Plot plot : s.plots()) {
            Path2D path = polygon(plot.x(), plot.z(), x0, z0, scale);
            g.setColor(new Color(220, 200, 120, 70));
            g.fill(path);
            g.setColor(new Color(240, 220, 140, 200));
            g.setStroke(new BasicStroke(1f));
            g.draw(path);
        }
        double[] c = s.square().corners();
        g.setColor(new Color(170, 165, 160));
        g.fill(polygon(new double[] {c[0], c[2], c[4], c[6]}, new double[] {c[1], c[3], c[5], c[7]}, x0, z0, scale));
        for (Settlement.Road r : s.roads()) {
            g.setColor(switch (r.surface()) {
                case COBBLES -> new Color(150, 145, 140);
                case GRAVEL -> new Color(215, 200, 170);
                case DIRT -> new Color(150, 110, 70);
            });
            g.setStroke(new BasicStroke((float) Math.max(1.5, r.width() * scale), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            g.draw(line(r.x(), r.z(), x0, z0, scale));
        }
        g.setStroke(new BasicStroke(1f));
        for (Settlement.Fence f : s.fences()) {
            g.setColor(f.kind() == Settlement.FenceKind.GARDEN ? new Color(120, 80, 40) : new Color(70, 40, 20));
            g.draw(line(f.x(), f.z(), x0, z0, scale));
        }
        for (Settlement.Building b : s.buildings()) {
            double[] k = b.corners();
            g.setColor(switch (b.spec().kind()) {
                case CHURCH -> new Color(240, 240, 240);
                case INN -> new Color(220, 90, 60);
                case HOUSE -> new Color(160, 70, 50);
                case FARMHOUSE -> new Color(130, 90, 60);
                case HAY_BARN -> new Color(100, 70, 40);
            });
            g.fill(polygon(new double[] {k[0], k[2], k[4], k[6]}, new double[] {k[1], k[3], k[5], k[7]}, x0, z0,
                scale));
            double[] d = new double[2];
            b.door(d);
            g.setColor(Color.YELLOW);
            g.fillOval((int) ((d[0] - x0) * scale) - 1, (int) ((d[1] - z0) * scale) - 1, 3, 3);
        }
        g.setColor(new Color(255, 240, 150));
        for (Settlement.Lamp l : s.lamps()) {
            g.fillOval((int) ((l.x() - x0) * scale) - 2, (int) ((l.z() - z0) * scale) - 2, 4, 4);
        }
        g.setColor(Color.CYAN);
        for (Settlement.Prop p : s.props()) {
            g.fillRect((int) ((p.x() - x0) * scale) - 1, (int) ((p.z() - z0) * scale) - 1, 2, 2);
        }
        g.dispose();
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private static Path2D polygon(double[] x, double[] z, double x0, double z0, double scale) {
        Path2D path = line(x, z, x0, z0, scale);
        path.closePath();
        return path;
    }

    private static Path2D line(double[] x, double[] z, double x0, double z0, double scale) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < x.length; i++) {
            double px = (x[i] - x0) * scale;
            double py = (z[i] - z0) * scale;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        return path;
    }

    static Path folder() {
        return Path.of(System.getProperty("truesim.pictures", "build/pictures"));
    }

    private static void report(String name, Settlement s, long start) {
        System.out.printf(Locale.ROOT, "%s: %d roads, %d plots, %d buildings, %d props, %d lamps, %d fences in %.1f s%n",
            name, s.roads().size(), s.plots().size(), s.buildings().size(), s.props().size(), s.lamps().size(),
            s.fences().size(), (System.nanoTime() - start) / 1e9);
        for (Settlement.Road r : s.roads()) {
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            for (double y : r.y()) {
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
            System.out.printf(Locale.ROOT, "  road %d %s %s from %d to %d: %.0f m, rises %.0f m, grade %.3f%n", r.id(),
                r.kind(), r.surface(), r.from(), r.to(), r.length(), hi - lo, r.maxGrade());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_PICTURES", matches = "true")
    void valley() throws Exception {
        Ecology ecology = Valleys.ecology(Valleys.valley());
        long start = System.nanoTime();
        Settlement s = Settlements.plan(SettlementParameters.DEFAULTS, ecology, -60, 0, 7);
        report("valley", s, start);
        draw(s, ecology, folder().resolve("valley.png"), 1400);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_PICTURES", matches = "true")
    void lauterbrunnen() throws Exception {
        Ecology ecology = Valleys.lauterbrunnenEcology();
        assumeTrue(ecology != null, "effects-terrain's cache holds no Lauterbrunnen");
        long start = System.nanoTime();
        Settlement s = Settlements.plan(SettlementParameters.DEFAULTS, ecology, -130, 300, 7);
        report("lauterbrunnen", s, start);
        draw(s, ecology, folder().resolve("lauterbrunnen.png"), 1400);
    }
}
