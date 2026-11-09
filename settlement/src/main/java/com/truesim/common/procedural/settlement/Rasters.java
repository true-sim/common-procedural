package com.truesim.common.procedural.settlement;

import com.truesim.common.procedural.settlement.Settlement.Building;
import com.truesim.common.procedural.settlement.Settlement.Plot;
import com.truesim.common.procedural.settlement.Settlement.Road;
import com.truesim.common.procedural.settlement.Settlement.Square;
import java.util.Arrays;
import java.util.List;

/** Rasterises a plan's roads, square, plots and buildings into its {@link Grading} and {@link Clearing}. */
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

    /** The graded heights: under each road its crown and camber, cut and filled back to the ground; the square's plane. */
    static Grading grading(SettlementParameters p, Field field, List<Road> roads, Square square) {
        Frame f = Frame.of(p, field);
        float[] target = new float[f.n * f.n];
        float[] priority = new float[f.n * f.n];
        Arrays.fill(target, Float.NaN);
        Arrays.fill(priority, Float.POSITIVE_INFINITY);
        double camber = p.surface().camber();
        double batter = p.surface().batter();
        // the terrain's heights, each asked for once
        float[] ground = new float[f.n * f.n];
        Arrays.fill(ground, Float.NaN);
        for (Road r : roads) {
            double hw = r.width() / 2;
            double reach = hw + SHOULDER;
            for (int k = 1; k < r.count(); k++) {
                double ax = r.x()[k - 1];
                double az = r.z()[k - 1];
                double bx = r.x()[k];
                double bz = r.z()[k];
                for (int j = f.j0(Math.min(az, bz) - reach); j <= f.j1(Math.max(az, bz) + reach); j++) {
                    for (int i = f.i0(Math.min(ax, bx) - reach); i <= f.i1(Math.max(ax, bx) + reach); i++) {
                        double x = f.x0 + i * f.cell;
                        double z = f.z0 + j * f.cell;
                        double t = Geometry.project(x, z, ax, az, bx, bz);
                        double d = Geometry.length(ax + t * (bx - ax) - x, az + t * (bz - az) - z);
                        double e = d - hw;
                        int a = j * f.n + i;
                        if (e >= priority[a] || d > reach) {
                            continue;
                        }
                        double crown = r.y()[k - 1] + t * (r.y()[k] - r.y()[k - 1]);
                        if (e <= 0) {
                            target[a] = (float) (crown - camber * d);
                            priority[a] = (float) e;
                        } else {
                            double edge = crown - camber * hw;
                            double h = height(field, ground, a, x, z);
                            double run = Math.abs(h - edge) * batter;
                            if (e < run) {
                                target[a] = (float) (edge + (h - edge) * (e / run));
                                priority[a] = (float) e;
                            }
                        }
                    }
                }
            }
        }
        if (square != null) {
            double[] c = square.corners();
            double reach = SHOULDER;
            double minX = Math.min(Math.min(c[0], c[2]), Math.min(c[4], c[6])) - reach;
            double maxX = Math.max(Math.max(c[0], c[2]), Math.max(c[4], c[6])) + reach;
            double minZ = Math.min(Math.min(c[1], c[3]), Math.min(c[5], c[7])) - reach;
            double maxZ = Math.max(Math.max(c[1], c[3]), Math.max(c[5], c[7])) + reach;
            for (int j = f.j0(minZ); j <= f.j1(maxZ); j++) {
                for (int i = f.i0(minX); i <= f.i1(maxX); i++) {
                    double x = f.x0 + i * f.cell;
                    double z = f.z0 + j * f.cell;
                    double e = square.outside(x, z);
                    int a = j * f.n + i;
                    if (e >= priority[a]) {
                        continue;
                    }
                    double plane = square.height(x, z);
                    if (e <= 0) {
                        target[a] = (float) plane;
                        priority[a] = (float) e;
                    } else {
                        double h = height(field, ground, a, x, z);
                        double run = Math.abs(h - plane) * batter;
                        if (e < run) {
                            target[a] = (float) (plane + (h - plane) * (e / run));
                            priority[a] = (float) e;
                        }
                    }
                }
            }
        }
        return new Grading(f.x0, f.z0, f.cell, f.n, target);
    }

    /** The terrain's height at sample {@code a}, asked for once. */
    private static double height(Field field, float[] ground, int a, double x, double z) {
        if (Float.isNaN(ground[a])) {
            ground[a] = (float) field.heightAt(x, z);
        }
        return ground[a];
    }

    /** The clearings: trees off roads, square, buildings (by the margin) and plots; grass off what is paved or built. */
    static Clearing clearing(SettlementParameters p, Field field, List<Road> roads, Square square, List<Plot> plots,
        List<Building> buildings) {
        Frame f = Frame.of(p, field);
        double margin = p.furniture().treeMargin();
        float[] treeEdge = new float[f.n * f.n];
        float[] grassEdge = new float[f.n * f.n];
        Arrays.fill(treeEdge, Float.POSITIVE_INFINITY);
        Arrays.fill(grassEdge, Float.POSITIVE_INFINITY);
        double reachTrees = margin + TREE_FADE;
        for (Road r : roads) {
            // the paved width: cobbled roads have their kerbs
            double paved = r.width() / 2 + (r.surface() == RoadSurface.COBBLES ? p.surface().kerbWidth() : 0);
            double reach = paved + reachTrees;
            for (int k = 1; k < r.count(); k++) {
                double ax = r.x()[k - 1];
                double az = r.z()[k - 1];
                double bx = r.x()[k];
                double bz = r.z()[k];
                for (int j = f.j0(Math.min(az, bz) - reach); j <= f.j1(Math.max(az, bz) + reach); j++) {
                    for (int i = f.i0(Math.min(ax, bx) - reach); i <= f.i1(Math.max(ax, bx) + reach); i++) {
                        double x = f.x0 + i * f.cell;
                        double z = f.z0 + j * f.cell;
                        double e = Geometry.segmentDistance(x, z, ax, az, bx, bz) - paved;
                        int a = j * f.n + i;
                        treeEdge[a] = (float) Math.min(treeEdge[a], e - margin);
                        grassEdge[a] = (float) Math.min(grassEdge[a], e);
                    }
                }
            }
        }
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
            trees[a] = (byte) Math.round(255 * Geometry.smooth(treeEdge[a] / TREE_FADE));
            grass[a] = (byte) Math.round(255 * Geometry.smooth(grassEdge[a] / GRASS_FADE));
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
                double e = -Geometry.depth(x, z, px, pz);
                int a = j * f.n + i;
                treeEdge[a] = (float) Math.min(treeEdge[a], e - margin);
                if (paved && grassEdge != null) {
                    grassEdge[a] = (float) Math.min(grassEdge[a], e);
                }
            }
        }
    }
}
