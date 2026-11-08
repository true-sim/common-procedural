package com.truesim.common.procedural.ecology.terrain;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Belt;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.EcologyTile;
import com.truesim.common.procedural.ecology.GroundCover;
import com.truesim.common.procedural.ecology.Layer;
import com.truesim.common.procedural.ecology.Species;
import com.truesim.effects.terrain.data.TerrainSurface;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * Map views of the ecology over Lauterbrunnen (effects-terrain's cache): 8 by 8 km from the Schilthorn to Wengen and
 * from Lauterbrunnen to Stechelberg, over hillshade: the conditions (belts, temperature, insolation, snow, wetness,
 * exposure, moisture), the hydrology (rivers, riparian ground, wetlands along the Weisse Luetschine), the ground
 * cover, the dominant canopy, and every species' abundance. Frames of 1280 x 720 into {@code -Ppictures=<dir>}
 * (default {@code build/maps}); {@code ffmpeg -framerate 1/3 -i map%03d.png -vf fps=30 ...} makes the film.
 */
class LauterbrunnenMaps {

    private static final double X0 = -6400;
    private static final double Z0 = -1600;
    private static final double SIZE = 8192;
    private static final double CELL = 8;
    private static final int MAP = 680;

    private int n;
    private float[][] species;
    private float[][] fields;
    private float[][] cover;
    private List<Species> all;
    private float[] shade;
    private double viewX0 = X0;
    private double viewZ0 = Z0;
    private double viewSize = SIZE;

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_PICTURES", matches = "true")
    void maps() throws Exception {
        TerrainSurface surface = Sites.lauterbrunnenSurface();
        assumeTrue(surface != null, "effects-terrain's cache holds no Lauterbrunnen");
        Path out = Path.of(System.getProperty("truesim.pictures", "build/maps"));
        Files.createDirectories(out);
        EcologyParameters.Placement fieldsOnly = EcologyParameters.Placement.DEFAULTS;
        for (Layer layer : Layer.values()) {
            fieldsOnly = fieldsOnly.withInstances(layer, false);
        }
        EcologyParameters parameters = EcologyParameters.ALPS.withLandUse(EcologyParameters.LandUse.MEADOWS)
            .withTiles(512, CELL).withPlacement(fieldsOnly)
            .withAnalysis(new EcologyParameters.Analysis(8, 2048, 2048, 150, 40, 40));
        Ecology ecology = new Ecology(TerrainEcology.of(surface, 2), parameters, AlpineSpecies.ALL);
        all = ecology.species();
        assemble(ecology);
        hillshade();
        int frame = 0;
        frame = write(out, frame, "Vegetation belts", "by the growing season's temperature: altitude, lapse rate 0.6 C "
            + "per 100 m, and aspect (sunny slopes warmer)", beltMap(), belts());
        frame = write(out, frame, "Growing-season temperature", "mean, degrees Celsius; treeline at about 6.4 C",
            scalar(field(EcologyTile.Field.TEMPERATURE), -2, 18, false), ramp("-2 C", "18 C", false));
        frame = write(out, frame, "Insolation", "growing-season sunshine relative to level ground, from the sun's "
            + "paths at 46.6 N", scalar(field(EcologyTile.Field.INSOLATION), 0.3, 1.3, false), ramp("0.3", "1.3",
            false));
        frame = write(out, frame, "Snow cover", "days a year, from temperature, wind exposure and hollows",
            scalar(field(EcologyTile.Field.SNOW_DAYS), 0, 365, false), ramp("0 days", "365 days", false));
        frame = write(out, frame, "Topographic wetness index", "ln(a / tan slope): multiple flow directions over "
            + "Priority-Flood filled heights", scalar(field(EcologyTile.Field.WETNESS), 4, 16, true),
            ramp("4", "16", true));
        frame = write(out, frame, "Wind exposure", "from the topographic position index (150 m): ridges exposed",
            scalar(field(EcologyTile.Field.EXPOSURE), 0, 1, false), ramp("sheltered", "ridges", false));
        frame = write(out, frame, "Moisture", "on Ellenberg's scale: 3 dry, 5 fresh, 7 damp, 9 wet, 11 water",
            scalar(field(EcologyTile.Field.MOISTURE), 3, 11, true), ramp("3", "11", true));
        frame = write(out, frame, "Rivers, riparian ground and wetlands", "the Weisse Luetschine and its streams: "
            + "water blue, riparian band green, wetlands (saturated, flat) violet", hydrologyMap(), hydrology());
        frame = write(out, frame, "Ground cover", "for the terrain's material: meadow, forest floor, heath, scree, "
            + "rock, snow, wetland, water", coverMap(), covers());
        frame = write(out, frame, "Dominant canopy", "the most abundant tree where trees grow", dominantMap(),
            dominantLegend());
        // the valley floor from Lauterbrunnen south, closer
        viewX0 = -1700;
        viewZ0 = -300;
        viewSize = 2500;
        frame = write(out, frame, "The valley floor: hydrology", "the Weisse Luetschine between the cliffs, its "
            + "riparian band and the wetlands by it", hydrologyMap(), hydrology());
        frame = write(out, frame, "The valley floor: ground cover", "meadows kept open below 1850 m on gentle "
            + "slopes, forest patches, cliffs", coverMap(), covers());
        frame = write(out, frame, "The valley floor: dominant canopy", "beech and ash low, spruce up the sides, "
            + "alder and willow along the river", dominantMap(), dominantLegend());
        viewX0 = X0;
        viewZ0 = Z0;
        viewSize = SIZE;
        for (int s = 0; s < all.size(); s++) {
            Species sp = all.get(s);
            frame = write(out, frame, sp.id() + "  (" + sp.name() + ")", sp.layer().name().toLowerCase(Locale.ROOT)
                .replace('_', ' ') + "; abundance 0 to 1", abundanceMap(s), greens());
        }
        System.out.println("maps: " + frame + " frames into " + out.toAbsolutePath());
    }

    private void assemble(Ecology ecology) throws Exception {
        int tiles = (int) (SIZE / 512);
        int per = (int) (512 / CELL);
        n = tiles * per;
        species = new float[all.size()][n * n];
        fields = new float[EcologyTile.Field.values().length][n * n];
        cover = new float[GroundCover.values().length][n * n];
        try (ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            List<Future<EcologyTile>> results = new ArrayList<>();
            for (int j = 0; j < tiles; j++) {
                for (int i = 0; i < tiles; i++) {
                    double x = X0 + i * 512;
                    double z = Z0 + j * 512;
                    results.add(pool.submit(() -> ecology.region(x, z, 512, 512)));
                }
            }
            for (int k = 0; k < results.size(); k++) {
                EcologyTile tile = results.get(k).get();
                int ti = k % tiles;
                int tj = k / tiles;
                int samples = tile.samples();
                for (int j = 0; j < per; j++) {
                    for (int i = 0; i < per; i++) {
                        int from = j * samples + i;
                        int to = (tj * per + j) * n + ti * per + i;
                        for (int s = 0; s < all.size(); s++) {
                            species[s][to] = tile.abundance(s)[from];
                        }
                        for (EcologyTile.Field f : EcologyTile.Field.values()) {
                            fields[f.ordinal()][to] = tile.field(f)[from];
                        }
                        for (GroundCover c : GroundCover.values()) {
                            cover[c.ordinal()][to] = tile.cover(c)[from];
                        }
                    }
                }
            }
        }
    }

    private float[] field(EcologyTile.Field f) {
        return fields[f.ordinal()];
    }

    private void hillshade() {
        float[] h = field(EcologyTile.Field.ALTITUDE);
        shade = new float[n * n];
        double lx = -0.5;
        double ly = 0.6;
        double lz = -0.62;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int a = j * n + i;
                double gx = (h[j * n + Math.min(i + 1, n - 1)] - h[j * n + Math.max(i - 1, 0)]) / (2 * CELL);
                double gz = (h[Math.min(j + 1, n - 1) * n + i] - h[Math.max(j - 1, 0) * n + i]) / (2 * CELL);
                double l = Math.sqrt(gx * gx + 1 + gz * gz);
                double d = (-gx * lx + ly - gz * lz) / l / Math.sqrt(lx * lx + ly * ly + lz * lz);
                shade[a] = (float) Math.clamp(0.25 + 0.85 * d, 0, 1.1);
            }
        }
    }

    /** A map's colours (RGB 0 to 1) per sample. */
    private interface Painter {
        double[] color(int a);
    }

    private Painter scalar(float[] f, double lo, double hi, boolean wet) {
        return a -> palette(Math.clamp((f[a] - lo) / (hi - lo), 0, 1), wet);
    }

    private static double[] palette(double t, boolean wet) {
        // perceptual-ish ramps: dark blue to yellow (viridis-like), or sand to deep blue for water
        double[][] stops = wet
            ? new double[][] {{0.85, 0.75, 0.55}, {0.55, 0.75, 0.6}, {0.2, 0.55, 0.7}, {0.05, 0.2, 0.55}}
            : new double[][] {{0.27, 0.0, 0.33}, {0.23, 0.32, 0.55}, {0.13, 0.57, 0.55}, {0.37, 0.79, 0.38},
                {0.99, 0.91, 0.14}};
        double p = t * (stops.length - 1);
        int k = Math.min((int) p, stops.length - 2);
        double f = p - k;
        return new double[] {stops[k][0] + f * (stops[k + 1][0] - stops[k][0]),
            stops[k][1] + f * (stops[k + 1][1] - stops[k][1]), stops[k][2] + f * (stops[k + 1][2] - stops[k][2])};
    }

    private static final double[][] BELTS = {{0.85, 0.75, 0.35}, {0.25, 0.55, 0.25}, {0.1, 0.35, 0.25},
        {0.6, 0.75, 0.45}, {0.92, 0.92, 0.95}};

    private Painter beltMap() {
        float[] b = field(EcologyTile.Field.BELT);
        return a -> BELTS[(int) b[a]];
    }

    private Painter hydrologyMap() {
        float[] water = field(EcologyTile.Field.WATER);
        float[] riparian = field(EcologyTile.Field.RIPARIAN);
        float[] wetland = field(EcologyTile.Field.WETLAND);
        return a -> {
            double[] c = {0.82, 0.8, 0.75};
            c = mix(c, new double[] {0.2, 0.65, 0.25}, riparian[a]);
            c = mix(c, new double[] {0.55, 0.25, 0.75}, wetland[a]);
            return mix(c, new double[] {0.05, 0.3, 0.85}, water[a]);
        };
    }

    private static final double[][] COVERS = {{0.55, 0.75, 0.3}, {0.12, 0.3, 0.12}, {0.5, 0.35, 0.45},
        {0.62, 0.6, 0.55}, {0.45, 0.42, 0.4}, {0.97, 0.97, 1.0}, {0.55, 0.25, 0.75}, {0.1, 0.3, 0.85}};

    private Painter coverMap() {
        return a -> {
            double[] c = new double[3];
            for (int k = 0; k < COVERS.length; k++) {
                for (int e = 0; e < 3; e++) {
                    c[e] += cover[k][a] * COVERS[k][e];
                }
            }
            return c;
        };
    }

    private static double[] speciesColor(int s, int count) {
        Color c = Color.getHSBColor((float) ((s * 0.618034) % 1), 0.75f, 0.9f);
        return new double[] {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
    }

    private Painter dominantMap() {
        float[] canopy = field(EcologyTile.Field.CANOPY);
        return a -> {
            int best = -1;
            for (int s = 0; s < all.size(); s++) {
                if (all.get(s).layer() == Layer.CANOPY && (best < 0 || species[s][a] > species[best][a])) {
                    best = s;
                }
            }
            double[] c = {0.85, 0.85, 0.82};
            return best < 0 ? c : mix(c, speciesColor(best, all.size()), Math.min(1, 1.5 * canopy[a]));
        };
    }

    private Painter abundanceMap(int s) {
        float[] f = species[s];
        return a -> mix(new double[] {0.86, 0.86, 0.84}, new double[] {0.05, 0.45, 0.12}, Math.min(1, f[a]));
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[] {a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]), a[2] + t * (b[2] - a[2])};
    }

    /** A legend: drawn into the panel from its top-left corner. */
    private interface Legend {
        void draw(Graphics2D g, int x, int y);
    }

    private static Legend ramp(String low, String high, boolean wet) {
        return (g, x, y) -> {
            for (int k = 0; k < 300; k++) {
                double[] c = palette(k / 299.0, wet);
                g.setColor(new Color((float) c[0], (float) c[1], (float) c[2]));
                g.fillRect(x + k, y, 1, 18);
            }
            g.setColor(Color.DARK_GRAY);
            g.drawString(low, x, y + 38);
            g.drawString(high, x + 300 - g.getFontMetrics().stringWidth(high), y + 38);
        };
    }

    private static Legend greens() {
        return (g, x, y) -> {
            for (int k = 0; k < 300; k++) {
                double[] c = mix(new double[] {0.86, 0.86, 0.84}, new double[] {0.05, 0.45, 0.12}, k / 299.0);
                g.setColor(new Color((float) c[0], (float) c[1], (float) c[2]));
                g.fillRect(x + k, y, 1, 18);
            }
            g.setColor(Color.DARK_GRAY);
            g.drawString("absent", x, y + 38);
            g.drawString("dominant", x + 300 - g.getFontMetrics().stringWidth("dominant"), y + 38);
        };
    }

    private static Legend swatches(String[] names, double[][] colors) {
        return (g, x, y) -> {
            for (int k = 0; k < names.length; k++) {
                double[] c = colors[k];
                g.setColor(new Color((float) c[0], (float) c[1], (float) c[2]));
                g.fillRect(x, y + 26 * k, 20, 20);
                g.setColor(Color.DARK_GRAY);
                g.drawString(names[k], x + 30, y + 26 * k + 16);
            }
        };
    }

    private static Legend belts() {
        String[] names = new String[Belt.values().length];
        for (Belt b : Belt.values()) {
            names[b.ordinal()] = b.name().toLowerCase(Locale.ROOT);
        }
        return swatches(names, BELTS);
    }

    private static Legend covers() {
        String[] names = new String[GroundCover.values().length];
        for (GroundCover c : GroundCover.values()) {
            names[c.ordinal()] = c.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        return swatches(names, COVERS);
    }

    private static Legend hydrology() {
        return swatches(new String[] {"open water", "riparian", "wetland"},
            new double[][] {{0.05, 0.3, 0.85}, {0.2, 0.65, 0.25}, {0.55, 0.25, 0.75}});
    }

    private Legend dominantLegend() {
        List<String> names = new ArrayList<>();
        List<double[]> colors = new ArrayList<>();
        for (int s = 0; s < all.size(); s++) {
            if (all.get(s).layer() == Layer.CANOPY) {
                names.add(all.get(s).id());
                colors.add(speciesColor(s, all.size()));
            }
        }
        return swatches(names.toArray(String[]::new), colors.toArray(double[][]::new));
    }

    private int write(Path out, int frame, String title, String caption, Painter painter, Legend legend)
        throws Exception {
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0.96f, 0.95f, 0.93f));
        g.fillRect(0, 0, 1280, 720);
        int ox = 20;
        int oy = 20;
        for (int y = 0; y < MAP; y++) {
            for (int x = 0; x < MAP; x++) {
                int i = Math.clamp((int) ((viewX0 + (x + 0.5) / MAP * viewSize - X0) / CELL), 0, n - 1);
                int j = Math.clamp((int) ((viewZ0 + (y + 0.5) / MAP * viewSize - Z0) / CELL), 0, n - 1);
                int a = j * n + i;
                double[] c = painter.color(a);
                double s = shade[a];
                int r = (int) Math.clamp(255 * c[0] * s, 0, 255);
                int gg = (int) Math.clamp(255 * c[1] * s, 0, 255);
                int b = (int) Math.clamp(255 * c[2] * s, 0, 255);
                image.setRGB(ox + x, oy + y, r << 16 | gg << 8 | b);
            }
        }
        // places, north up: x east, z south from the church
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        place(g, ox, oy, 0, 0, "Lauterbrunnen");
        place(g, ox, oy, -1239, 3856, "Muerren");
        place(g, ox, oy, -5636, 3979, "Schilthorn");
        place(g, ox, oy, 994, -1311, "Wengen");
        place(g, ox, oy, -436, 5523, "Stechelberg");
        g.setColor(Color.DARK_GRAY);
        g.setStroke(new BasicStroke(1));
        g.drawRect(ox, oy, MAP, MAP);
        int px = ox + MAP + 40;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        g.drawString(title, px, 70);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        int line = 0;
        for (String part : wrap(caption, 46)) {
            g.drawString(part, px, 108 + 22 * line++);
        }
        legend.draw(g, px, 140 + 22 * line);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(String.format(Locale.ROOT, "%.1f x %.1f km", viewSize / 1000, viewSize / 1000)
            + ", north up; terrain: swisstopo swissALTI3D, Copernicus GLO-30", px, 680);
        g.drawString("true-sim common-procedural-ecology", px, 700);
        g.dispose();
        ImageIO.write(image, "png", out.resolve(String.format(Locale.ROOT, "map%03d.png", frame)).toFile());
        return frame + 1;
    }

    private void place(Graphics2D g, int ox, int oy, double x, double z, String name) {
        int px = ox + (int) ((x - viewX0) / viewSize * MAP);
        int py = oy + (int) ((z - viewZ0) / viewSize * MAP);
        if (px < ox || py < oy || px > ox + MAP - 60 || py > oy + MAP) {
            return;
        }
        g.setColor(Color.BLACK);
        g.fillOval(px - 3, py - 3, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(name, px + 6, py - 4);
        g.setColor(Color.BLACK);
        g.drawString(name, px + 5, py - 5);
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        lines.add(line.toString());
        return lines;
    }
}
