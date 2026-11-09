package com.truesim.common.procedural.settlement;

import com.truesim.common.math.Maths;

import com.truesim.common.math.geometry.Polygons;

import com.truesim.common.procedural.settlement.Settlement.Building;
import com.truesim.common.procedural.settlement.Settlement.Plot;
import com.truesim.common.procedural.settlement.Settlement.Road;
import com.truesim.common.procedural.settlement.Settlement.Square;
import java.util.Arrays;
import java.util.List;

/** A plan's terrain modifier ({@link Grading}) and its roads, square, plots and buildings rasterised into its {@link Clearing}. */
final class Rasters {

    /** How far a cut or fill reaches beyond a road's edge at most, metres. */
    private static final double SHOULDER = 14;
    /** How wide the trees' clearing fades, metres. */
    private static final double TREE_FADE = 2;
    /** How wide the grass's mask fades, metres. */
    private static final double GRASS_FADE = 0.6;

    private Rasters() {
    }

    /** The raster's geometry over the plan: its first sample and samples a side. */
    private record Frame(double x0, double z0, double cell, int n) {

        static Frame of(SettlementParameters p, Field field) {
            double cell = p.grid().raster();
            double extent = (field.n - 1) * field.cell;
            int n = (int) Math.round(extent / cell) + 1;
            return new Frame(field.x0, field.z0, cell, n);
        }

        int i0(double x) {
            return Math.max(0, (int) Math.floor((x - x0) / cell));
        }

        int i1(double x) {
            return Math.min(n - 1, (int) Math.ceil((x - x0) / cell));
        }

        int j0(double z) {
            return Math.max(0, (int) Math.floor((z - z0) / cell));
        }

        int j1(double z) {
            return Math.min(n - 1, (int) Math.ceil((z - z0) / cell));
        }
    }

    /** The terrain modifier of the roads and the square: their distance field, heights and verges. */
    static Grading grading(SettlementParameters p, Field field, List<Road> roads, Square square, long seed) {
        double extent = (field.n - 1) * field.cell;
        RoadField roadField = new RoadField(roads, square, p, field.x0, field.z0, extent);
        return new Grading(roadField, p.surface(), seed);
    }

    /** The clearings: trees off roads, square, buildings (by the margin) and plots; grass off what is paved or built. */
    static Clearing clearing(SettlementParameters p, Field field, Grading grading, Square square, List<Plot> plots,
        List<Building> buildings) {
        Frame f = Frame.of(p, field);
        double margin = p.furniture().treeMargin();
        float[] treeEdge = new float[f.n * f.n];
        float[] grassEdge = new float[f.n * f.n];
        Arrays.fill(treeEdge, Float.POSITIVE_INFINITY);
        Arrays.fill(grassEdge, Float.POSITIVE_INFINITY);
        double reachTrees = margin + TREE_FADE;
        // the roads (and the square) from their distance field: trees kept off by the margin, grass by the verge
        float[] grassRoads = new float[f.n * f.n];
        java.util.stream.IntStream.range(0, f.n).parallel().forEach(j -> {
            RoadField.Sample sample = new RoadField.Sample();
            for (int i = 0; i < f.n; i++) {
                double x = f.x0 + i * f.cell;
                double z = f.z0 + j * f.cell;
                grading.field().sample(x, z, sample);
                int a = j * f.n + i;
                treeEdge[a] = (float) Math.min(treeEdge[a], sample.graded - margin);
                grassRoads[a] = (float) grading.grass(x, z);
            }
        });
        double[] c = square.corners();
        polygon(f, new double[] {c[0], c[2], c[4], c[6]}, new double[] {c[1], c[3], c[5], c[7]}, margin,
            reachTrees, treeEdge, grassEdge, true);
        for (Plot plot : plots) {
            polygon(f, plot.x(), plot.z(), 0, TREE_FADE, treeEdge, null, false);
        }
        for (Building b : buildings) {
            double[] k = b.corners();
            polygon(f, new double[] {k[0], k[2], k[4], k[6]}, new double[] {k[1], k[3], k[5], k[7]}, margin,
                reachTrees, treeEdge, grassEdge, true);
        }
        byte[] trees = new byte[f.n * f.n];
        byte[] grass = new byte[f.n * f.n];
        for (int a = 0; a < trees.length; a++) {
            trees[a] = (byte) Math.round(255 * Maths.smoothstep(0, 1, treeEdge[a] / TREE_FADE));
            grass[a] = (byte) Math.round(255 * Math.min(grassRoads[a], Maths.smoothstep(0, 1, grassEdge[a] / GRASS_FADE)));
        }
        return new Clearing(f.x0, f.z0, f.cell, f.n, trees, grass);
    }

    /** A convex polygon into the edge rasters: the trees' less {@code margin}, the grass's (if given). */
    private static void polygon(Frame f, double[] px, double[] pz, double margin, double reach, float[] treeEdge,
        float[] grassEdge, boolean paved) {
        double minX = Arrays.stream(px).min().orElseThrow() - reach;
        double maxX = Arrays.stream(px).max().orElseThrow() + reach;
        double minZ = Arrays.stream(pz).min().orElseThrow() - reach;
        double maxZ = Arrays.stream(pz).max().orElseThrow() + reach;
        for (int j = f.j0(minZ); j <= f.j1(maxZ); j++) {
            for (int i = f.i0(minX); i <= f.i1(maxX); i++) {
                double x = f.x0 + i * f.cell;
                double z = f.z0 + j * f.cell;
                double e = -Polygons.convexDepth(x, z, px, pz);
                int a = j * f.n + i;
                treeEdge[a] = (float) Math.min(treeEdge[a], e - margin);
                if (paved && grassEdge != null) {
                    grassEdge[a] = (float) Math.min(grassEdge[a], e);
                }
            }
        }
    }
}
