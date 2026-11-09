package com.truesim.common.procedural.settlement;

import com.truesim.common.math.geometry.Polygons;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.settlement.Settlement.Road;
import com.truesim.common.procedural.settlement.Settlement.Square;
import java.util.Arrays;
import java.util.List;

/**
 * The road network as a signed distance field with heights: everything the road meshes and the terrain beside them are
 * made from, so the two meet exactly.
 *
 * <ul>
 *   <li><b>Distance</b> to the paved surface, negative inside: each road a chain of segments (capsules of its half
 *       width; flat-capped where a flight of steps begins or ends), the pieces of one road (split where its surface
 *       changes) joined by a plain union, different roads and the square by a smooth union of the larger of their
 *       classes' fillet radii: rounded corners at every junction, kerb radii in the core, an apron where a track
 *       joins (Quilez's polynomial smooth minimum).</li>
 *   <li><b>Height</b> of the road's surface at any point near it: each road's crown along it less its camber across
 *       (level beyond its edge), roads blended by their distances where they meet, so the camber of one eases into
 *       the other across a junction; the square's plane.</li>
 *   <li><b>Surface</b>: the nearest road piece's.</li>
 * </ul>
 * A pure function of the point (the same at a tile's border from either side); thread-safe through per-call
 * {@link Sample}s.
 */
final class RoadField {

    /** A road segment's relation to steps: steep (a flight's, not paved), or flat-capped at its start or end. */
    private static final byte STEEP = 1;
    private static final byte CAP_START = 2;
    private static final byte CAP_END = 4;
    /** How far heights blend between roads, metres. */
    private static final double BLEND = 1.0;
    /** The bucket grid's cell, metres. */
    private static final double BUCKET = 8;

    /** What a point of the field holds. */
    static final class Sample {
        /** The distance to the paved surface (negative inside), metres; +infinity far from roads. */
        double paved;
        /** The distance to the graded surface (the paved and the steps), metres. */
        double graded;
        /** The surface's height there (beyond the edge: the edge's, continued). */
        double height;
        /** The nearest paved piece's surface. */
        RoadSurface surface;
        /** The nearest road (or {@link Settlement#SQUARE}). */
        int road;
        /** The distance across the nearest road from its centre line, metres. */
        double lateral;
        /** Whether the nearest graded segment is a flight of steps' (the terrain lies lower under them). */
        boolean steps;

        private double[] chainPaved = new double[0];
        private double[] chainGraded = new double[0];
        private double[] chainHeight = new double[0];
        private int[] touched = new int[0];
    }

    private final List<Road> roads;
    private final Square square;
    private final double camber;
    private final double reach;
    // segments, as parallel arrays
    private final int count;
    private final double[] ax;
    private final double[] az;
    private final double[] bx;
    private final double[] bz;
    private final double[] ay;
    private final double[] by;
    private final double[] half;
    private final int[] road;
    private final int[] chain;
    private final byte[] flags;
    /** Each chain's fillet radius; the square's chain last. */
    private final double[] fillet;
    private final int chains;
    private final int squareChain;
    /** Each road's first segment. */
    private final int[] firstSegment;
    // buckets
    private final double x0;
    private final double z0;
    private final int nx;
    private final int nz;
    private final int[] start;
    private final int[] entries;

    RoadField(List<Road> roads, Square square, SettlementParameters p, double x0, double z0, double extent) {
        this.roads = roads;
        this.square = square;
        this.camber = p.surface().camber();
        double maxRun = p.surface().shoulder() + Math.max(p.surface().minRun(), 12 * p.surface().batter());
        this.reach = maxRun + 6;
        // chains: a road's pieces (split where the surface changes) are one road
        int[] parent = new int[roads.size()];
        for (int r = 0; r < parent.length; r++) {
            parent[r] = r;
        }
        for (Road r : roads) {
            if (r.to() >= 0 && roads.get(r.to()).from() == r.id() && roads.get(r.to()).kind() == r.kind()) {
                parent[find(parent, r.to())] = find(parent, r.id());
            }
        }
        int[] chainOf = new int[roads.size()];
        Arrays.fill(chainOf, -1);
        int c = 0;
        for (int r = 0; r < roads.size(); r++) {
            int root = find(parent, r);
            if (chainOf[root] < 0) {
                chainOf[root] = c++;
            }
            chainOf[r] = chainOf[root];
        }
        chains = c + 1;
        squareChain = c;
        fillet = new double[chains];
        for (Road r : roads) {
            fillet[chainOf[r.id()]] = Math.max(fillet[chainOf[r.id()]], p.road(r.kind()).fillet());
        }
        fillet[squareChain] = p.road(RoadKind.LANE).fillet();
        int n = 0;
        for (Road r : roads) {
            n += r.count() - 1;
        }
        count = n;
        ax = new double[n];
        az = new double[n];
        bx = new double[n];
        bz = new double[n];
        ay = new double[n];
        by = new double[n];
        half = new double[n];
        road = new int[n];
        chain = new int[n];
        flags = new byte[n];
        int k = 0;
        double stepGrade = p.surface().stepGrade();
        firstSegment = new int[roads.size()];
        for (Road r : roads) {
            int first = k;
            firstSegment[r.id()] = first;
            for (int i = 1; i < r.count(); i++, k++) {
                ax[k] = r.x()[i - 1];
                az[k] = r.z()[i - 1];
                bx[k] = r.x()[i];
                bz[k] = r.z()[i];
                ay[k] = r.y()[i - 1];
                by[k] = r.y()[i];
                half[k] = r.width() / 2;
                road[k] = r.id();
                chain[k] = chainOf[r.id()];
                double run = Polygons.length(bx[k] - ax[k], bz[k] - az[k]);
                boolean inner = i > 1 && i < r.count() - 1;
                if (r.kind() == RoadKind.FOOTPATH && inner && run > 0 && Math.abs(by[k] - ay[k]) / run > stepGrade) {
                    flags[k] = STEEP;
                }
            }
            // a flight must stand clear of other roads (its cuts are the surface's own edge, and nothing may join it
            // halfway): a flight in or by a junction is paved as a ramp instead
            for (int s = first; s < k; s++) {
                if ((flags[s] & STEEP) == 0 || s > first && (flags[s - 1] & STEEP) != 0) {
                    continue;
                }
                int e = s;
                while (e + 1 < k && (flags[e + 1] & STEEP) != 0) {
                    e++;
                }
                boolean free = clear(r, ax[s], az[s], p);
                for (int q = s; q <= e && free; q++) {
                    free = clear(r, bx[q], bz[q], p);
                }
                if (!free) {
                    for (int q = s; q <= e; q++) {
                        flags[q] = 0;
                    }
                }
            }
            // a paved segment beside a flight is cut flat across where the steps begin or end
            for (int s = first; s < k; s++) {
                if ((flags[s] & STEEP) != 0) {
                    continue;
                }
                if (s > first && (flags[s - 1] & STEEP) != 0) {
                    flags[s] |= CAP_START;
                }
                if (s + 1 < k && (flags[s + 1] & STEEP) != 0) {
                    flags[s] |= CAP_END;
                }
            }
        }
        // buckets of segments, each over its reach
        this.x0 = x0 - reach;
        this.z0 = z0 - reach;
        nx = (int) Math.ceil((extent + 2 * reach) / BUCKET) + 1;
        nz = nx;
        int[] counts = new int[nx * nz + 1];
        int[] cursor = null;
        int[] list = null;
        for (int pass = 0; pass < 2; pass++) {
            for (int s = 0; s < count; s++) {
                double r = half[s] + reach;
                int i0 = Math.max(0, (int) Math.floor((Math.min(ax[s], bx[s]) - r - this.x0) / BUCKET));
                int i1 = Math.min(nx - 1, (int) Math.floor((Math.max(ax[s], bx[s]) + r - this.x0) / BUCKET));
                int j0 = Math.max(0, (int) Math.floor((Math.min(az[s], bz[s]) - r - this.z0) / BUCKET));
                int j1 = Math.min(nz - 1, (int) Math.floor((Math.max(az[s], bz[s]) + r - this.z0) / BUCKET));
                for (int j = j0; j <= j1; j++) {
                    for (int i = i0; i <= i1; i++) {
                        int b = j * nx + i;
                        if (pass == 0) {
                            counts[b + 1]++;
                        } else {
                            list[cursor[b]++] = s;
                        }
                    }
                }
            }
            if (pass == 0) {
                for (int b = 0; b < nx * nz; b++) {
                    counts[b + 1] += counts[b];
                }
                cursor = counts.clone();
                list = new int[counts[nx * nz]];
            }
        }
        this.start = counts;
        this.entries = list;
    }

    /** Whether ({@code x}, {@code z}) on road {@code r} is clear of every other road and the square. */
    private boolean clear(Road r, double x, double z, SettlementParameters p) {
        double margin = r.width() / 2 + 1;
        for (Road o : roads) {
            if (o == r) {
                continue;
            }
            if (o.distance(x, z) < o.width() / 2 + p.road(o.kind()).fillet() + margin) {
                return false;
            }
        }
        return square.outside(x, z) > p.road(RoadKind.LANE).fillet() + margin;
    }

    private static int find(int[] parent, int a) {
        while (parent[a] != a) {
            parent[a] = parent[parent[a]];
            a = parent[a];
        }
        return a;
    }

    /** Polynomial smooth minimum of radius {@code k} (Quilez): {@code min(a, b)} rounded where they are within k. */
    static double smin(double a, double b, double k) {
        if (k <= 0 || a == Double.POSITIVE_INFINITY || b == Double.POSITIVE_INFINITY) {
            return Math.min(a, b);
        }
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    /** The field at ({@code x}, {@code z}) into {@code out}. */
    void sample(double x, double z, Sample out) {
        if (out.chainPaved.length < chains) {
            out.chainPaved = new double[chains];
            out.chainGraded = new double[chains];
            out.chainHeight = new double[chains];
            out.touched = new int[chains];
            Arrays.fill(out.chainPaved, Double.POSITIVE_INFINITY);
            Arrays.fill(out.chainGraded, Double.POSITIVE_INFINITY);
        }
        double[] cp = out.chainPaved;
        double[] cg = out.chainGraded;
        double[] ch = out.chainHeight;
        int[] touched = out.touched;
        int nt = 0;
        double bestPaved = Double.POSITIVE_INFINITY;
        double bestGraded = Double.POSITIVE_INFINITY;
        boolean steep = false;
        int bestSegment = -1;
        double bestLateral = 0;
        int i = (int) Math.floor((x - x0) / BUCKET);
        int j = (int) Math.floor((z - z0) / BUCKET);
        if (i >= 0 && j >= 0 && i < nx && j < nz) {
            int b = j * nx + i;
            for (int e = start[b]; e < start[b + 1]; e++) {
                int s = entries[e];
                double ex = bx[s] - ax[s];
                double ez = bz[s] - az[s];
                double l2 = ex * ex + ez * ez;
                double u = l2 > 0 ? ((x - ax[s]) * ex + (z - az[s]) * ez) / l2 : 0;
                double t = Math.clamp(u, 0, 1);
                double px = ax[s] + t * ex - x;
                double pz = az[s] + t * ez - z;
                double dist = Math.sqrt(px * px + pz * pz);
                double graded = dist - half[s];
                double paved = graded;
                byte f = flags[s];
                if ((f & STEEP) != 0) {
                    paved = Double.POSITIVE_INFINITY;
                } else if ((f & (CAP_START | CAP_END)) != 0) {
                    // cut flat across the path where the steps meet it: a half-plane at the capped end
                    double l = Math.sqrt(l2);
                    double across = Math.abs((x - ax[s]) * ez - (z - az[s]) * ex) / l;
                    if ((f & CAP_START) != 0 && u < 0 || (f & CAP_END) != 0 && u > 1) {
                        double beyond = u < 0 ? -u * l : (u - 1) * l;
                        paved = Math.max(across - half[s], beyond);
                    }
                    if ((f & CAP_END) != 0) {
                        paved = Math.max(paved, (u - 1) * l);
                    }
                    if ((f & CAP_START) != 0) {
                        paved = Math.max(paved, -u * l);
                    }
                }
                int c = chain[s];
                if (cg[c] == Double.POSITIVE_INFINITY && cp[c] == Double.POSITIVE_INFINITY) {
                    touched[nt++] = c;
                }
                if (graded < cg[c]) {
                    cg[c] = graded;
                    // the crown less the camber across, level beyond the edge
                    ch[c] = ay[s] + t * (by[s] - ay[s]) - camber * Math.min(dist, half[s]);
                }
                cp[c] = Math.min(cp[c], paved);
                if (graded < bestGraded) {
                    bestGraded = graded;
                    steep = (f & STEEP) != 0;
                }
                if (paved < bestPaved) {
                    bestPaved = paved;
                    bestSegment = s;
                    bestLateral = dist;
                }
            }
        }
        // the square
        double sq = square.outside(x, z);
        int c = squareChain;
        boolean squareNear = sq < reach;
        if (squareNear) {
            touched[nt++] = c;
            cp[c] = sq;
            cg[c] = sq;
            ch[c] = square.height(x, z);
        }
        // the union, rounded between every two chains by the larger of their fillets: the least of the chains'
        // distances and of every pair's smooth minimum, so continuous and the same in any order; heights blended
        // by distance
        sort(touched, nt, cg);
        double paved = Double.POSITIVE_INFINITY;
        double graded = Double.POSITIVE_INFINITY;
        double weight = 0;
        double height = 0;
        double nearest = nt > 0 ? Math.max(cg[touched[0]], 0) : 0;
        for (int k = 0; k < nt; k++) {
            int t = touched[k];
            paved = Math.min(paved, cp[t]);
            graded = Math.min(graded, cg[t]);
            for (int q = k + 1; q < nt; q++) {
                int u = touched[q];
                double r = Math.max(fillet[t], fillet[u]);
                paved = Math.min(paved, smin(cp[t], cp[u], r));
                graded = Math.min(graded, smin(cg[t], cg[u], r));
            }
            double w = Maths.exp(-(Math.max(cg[t], 0) - nearest) / BLEND * 3);
            weight += w;
            height += w * ch[t];
        }
        for (int k = 0; k < nt; k++) {
            cp[touched[k]] = Double.POSITIVE_INFINITY;
            cg[touched[k]] = Double.POSITIVE_INFINITY;
        }
        out.paved = paved;
        out.graded = graded;
        out.steps = steep && paved > 0 && !(squareNear && sq <= bestGraded);
        out.height = weight > 0 ? height / weight : Double.NaN;
        if (squareNear && sq <= bestPaved) {
            out.surface = RoadSurface.COBBLES;
            out.road = Settlement.SQUARE;
            out.lateral = 0;
        } else if (bestSegment >= 0) {
            out.surface = roads.get(road[bestSegment]).surface();
            out.road = road[bestSegment];
            out.lateral = bestLateral;
        } else {
            out.surface = null;
            out.road = Settlement.NONE;
            out.lateral = Double.POSITIVE_INFINITY;
        }
    }

    /** Insertion sort of the first {@code n} chains by their distance, ties by index. */
    private static void sort(int[] chains, int n, double[] by) {
        for (int a = 1; a < n; a++) {
            int v = chains[a];
            int b = a - 1;
            while (b >= 0 && (by[chains[b]] > by[v] || by[chains[b]] == by[v] && chains[b] > v)) {
                chains[b + 1] = chains[b];
                b--;
            }
            chains[b + 1] = v;
        }
    }

    /** Whether segment {@code i} (from point i to i + 1) of road {@code r} is a flight of steps'. */
    boolean steep(int r, int i) {
        return (flags[firstSegment[r] + i] & STEEP) != 0;
    }

    /** The fillet radius of road {@code r}'s chain, metres. */
    double fillet(int r) {
        return fillet[chain[firstSegment[r]]];
    }

    /** How far beyond a road's edge the field reaches (heights for the terrain's cuts and fills), metres. */
    double reach() {
        return reach;
    }
}
