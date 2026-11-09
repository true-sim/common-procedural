package com.truesim.common.procedural.water;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/** Builds a {@link WaterNetwork}: channels from the flow grid, their lines, hydraulics, falls and stones. */
final class NetworkBuilder {

    static final double G = 9.81;
    /** Shields' parameter at the threshold of motion. */
    private static final double SHIELDS = 0.047;
    /** The submerged specific gravity of quartz. */
    private static final double SUBMERGED = 1.65;

    private final EcologyTerrain terrain;
    private final WaterParameters p;
    private final FlowGrid grid;
    private final List<Watercourse> courses;

    NetworkBuilder(EcologyTerrain terrain, WaterParameters p, FlowGrid grid, List<Watercourse> courses) {
        this.terrain = terrain;
        this.p = p;
        this.grid = grid;
        this.courses = courses;
    }

    /** The channels' cells, as reaches from a head or confluence to the next confluence (included) or outlet. */
    List<int[]> reaches() {
        int count = grid.count();
        boolean[] channel = new boolean[count];
        for (int a = 0; a < count; a++) {
            channel[a] = grid.area[a] >= p.flow().channelArea() || grid.course[a] >= 0;
        }
        // whatever a channel flows into is a channel
        for (int k = count - 1; k >= 0; k--) {
            int a = grid.order[k];
            if (channel[a] && grid.receiver[a] >= 0) {
                channel[grid.receiver[a]] = true;
            }
        }
        int[] donors = donors(channel);
        // short headwater stubs off a larger channel are dropped (not mapped ones)
        for (int a = 0; a < count; a++) {
            if (channel[a] && donors[a] == 0) {
                double length = 0;
                boolean mapped = false;
                int c = a;
                while (grid.receiver[c] >= 0 && donors[c] < 2) {
                    mapped |= grid.course[c] >= 0;
                    length += step(c, grid.receiver[c]);
                    c = grid.receiver[c];
                }
                if (donors[c] >= 2 && !mapped && length < p.flow().minLength()) {
                    for (int s = a; s != c; s = grid.receiver[s]) {
                        channel[s] = false;
                    }
                }
            }
        }
        donors = donors(channel);
        List<int[]> reaches = new ArrayList<>();
        int[] path = new int[count];
        for (int k = count - 1; k >= 0; k--) {
            int a = grid.order[k];
            if (!channel[a] || donors[a] == 1) {
                continue;
            }
            int n = 0;
            int c = a;
            path[n++] = c;
            while (grid.receiver[c] >= 0) {
                c = grid.receiver[c];
                path[n++] = c;
                if (donors[c] != 1) {
                    break;
                }
            }
            if (n >= 2) {
                reaches.add(Arrays.copyOf(path, n));
            }
        }
        return reaches;
    }

    private int[] donors(boolean[] channel) {
        int[] donors = new int[grid.count()];
        for (int a = 0; a < grid.count(); a++) {
            if (channel[a] && grid.receiver[a] >= 0) {
                donors[grid.receiver[a]]++;
            }
        }
        return donors;
    }

    private double step(int a, int b) {
        double dx = grid.x(a) - grid.x(b);
        double dz = grid.z(a) - grid.z(b);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The network's channels from {@code reaches} (upstream first, as {@link #reaches()} gives them). */
    List<Channel> channels(List<int[]> reaches) {
        int n = reaches.size();
        // which reach starts at a cell, and each reach's downstream reach
        java.util.Map<Integer, Integer> starts = new java.util.HashMap<>();
        for (int r = 0; r < n; r++) {
            starts.put(reaches.get(r)[0], r);
        }
        int[] downstream = new int[n];
        int[] order = new int[n];
        int[] maxIn = new int[n];
        int[] countMax = new int[n];
        for (int r = 0; r < n; r++) {
            int[] cells = reaches.get(r);
            Integer d = starts.get(cells[cells.length - 1]);
            downstream[r] = d == null || d == r ? -1 : d;
        }
        // Strahler orders, upstream first: reaches come in drainage order from the top
        for (int r = 0; r < n; r++) {
            order[r] = maxIn[r] == 0 ? 1 : countMax[r] >= 2 ? maxIn[r] + 1 : maxIn[r];
            int d = downstream[r];
            if (d >= 0) {
                if (order[r] > maxIn[d]) {
                    maxIn[d] = order[r];
                    countMax[d] = 1;
                } else if (order[r] == maxIn[d]) {
                    countMax[d]++;
                }
            }
        }
        Line[] lines = new Line[n];
        for (int r = 0; r < n; r++) {
            lines[r] = line(reaches.get(r));
        }
        // surfaces never rise downstream, also across confluences
        double[] inflow = new double[n];
        Arrays.fill(inflow, Double.POSITIVE_INFINITY);
        for (int r = 0; r < n; r++) {
            double[] y = lines[r].surface;
            for (int i = 0; i < y.length; i++) {
                y[i] = Math.min(y[i], inflow[r]);
            }
            if (downstream[r] >= 0) {
                inflow[downstream[r]] = Math.min(inflow[downstream[r]], y[y.length - 1]);
            }
        }
        for (int r = 0; r < n; r++) {
            if (downstream[r] >= 0) {
                double[] y = lines[r].surface;
                y[y.length - 1] = lines[downstream[r]].surface[0];
            }
        }
        List<Channel> channels = new ArrayList<>(n);
        for (int r = 0; r < n; r++) {
            channels.add(hydraulics(r, downstream[r], order[r], lines[r]));
        }
        return channels;
    }

    /** A reach's line: its points, ground, catchment, the course it follows, and its surface before confluences. */
    private static final class Line {
        double[] x;
        double[] z;
        double[] ground;
        double[] area;
        int[] course;
        double[] surface;
    }

    private Line line(int[] cells) {
        int m = cells.length;
        double[] cx = new double[m];
        double[] cz = new double[m];
        for (int k = 0; k < m; k++) {
            cx[k] = grid.x(cells[k]);
            cz[k] = grid.z(cells[k]);
        }
        double spacing = p.surface().spacing();
        // corner cutting smooths the grid's 45 degree steps; then even spacing, the thalweg, a running mean
        double[][] smooth = chaikin(chaikin(new double[][] {cx, cz}));
        double[][] even = resample(smooth[0], smooth[1], spacing);
        double[][] snapped = snap(even[0], even[1]);
        double[][] line = resample(snapped[0], snapped[1], spacing);
        Line out = new Line();
        out.x = line[0];
        out.z = line[1];
        int count = out.x.length;
        out.ground = new double[count];
        out.area = new double[count];
        out.course = new int[count];
        // each point's catchment from the cells, by its fraction along the line
        double[] cellDistance = cumulative(cx, cz);
        double[] distance = cumulative(out.x, out.z);
        double total = distance[count - 1];
        double cellTotal = cellDistance[m - 1];
        int k = 0;
        for (int i = 0; i < count; i++) {
            out.ground[i] = terrain.height(out.x[i], out.z[i]);
            double s = total > 0 ? distance[i] / total * cellTotal : 0;
            while (k + 1 < m - 1 && cellDistance[k + 1] < s) {
                k++;
            }
            double t = cellDistance[k + 1] > cellDistance[k]
                ? Maths.clamp01((s - cellDistance[k]) / (cellDistance[k + 1] - cellDistance[k])) : 0;
            int a = cells[k];
            int b = cells[Math.min(k + 1, m - 1)];
            out.area[i] = grid.area[a] + t * (grid.area[b] - grid.area[a]);
            out.course[i] = grid.course[t < 0.5 ? a : b];
        }
        double offset = p.banks().surfaceOffset();
        double[] target = new double[count];
        for (int i = 0; i < count; i++) {
            target[i] = out.ground[i] - offset;
        }
        out.surface = monotone(target);
        return out;
    }

    /** One round of Chaikin's corner cutting, the ends kept. */
    static double[][] chaikin(double[][] line) {
        double[] x = line[0];
        double[] z = line[1];
        int n = x.length;
        if (n < 3) {
            return line;
        }
        double[] ox = new double[2 * n - 2];
        double[] oz = new double[2 * n - 2];
        int k = 0;
        ox[k] = x[0];
        oz[k++] = z[0];
        for (int i = 0; i + 1 < n; i++) {
            if (i > 0) {
                ox[k] = 0.75 * x[i] + 0.25 * x[i + 1];
                oz[k++] = 0.75 * z[i] + 0.25 * z[i + 1];
            }
            if (i + 2 < n) {
                ox[k] = 0.25 * x[i] + 0.75 * x[i + 1];
                oz[k++] = 0.25 * z[i] + 0.75 * z[i + 1];
            }
        }
        ox[k] = x[n - 1];
        oz[k++] = z[n - 1];
        return new double[][] {Arrays.copyOf(ox, k), Arrays.copyOf(oz, k)};
    }

    /** Metres along the line at each point. */
    static double[] cumulative(double[] x, double[] z) {
        double[] d = new double[x.length];
        for (int i = 1; i < x.length; i++) {
            double dx = x[i] - x[i - 1];
            double dz = z[i] - z[i - 1];
            d[i] = d[i - 1] + Math.sqrt(dx * dx + dz * dz);
        }
        return d;
    }

    /** The line resampled at even steps of about {@code spacing}, its ends kept. */
    static double[][] resample(double[] x, double[] z, double spacing) {
        double[] d = cumulative(x, z);
        double total = d[d.length - 1];
        int segments = Math.max(1, (int) Math.round(total / spacing));
        double[] ox = new double[segments + 1];
        double[] oz = new double[segments + 1];
        int k = 0;
        for (int i = 0; i <= segments; i++) {
            double s = total * i / segments;
            while (k + 1 < d.length - 1 && d[k + 1] < s) {
                k++;
            }
            double span = d[k + 1] - d[k];
            double t = span > 0 ? Maths.clamp01((s - d[k]) / span) : 0;
            ox[i] = x[k] + t * (x[k + 1] - x[k]);
            oz[i] = z[k] + t * (z[k + 1] - z[k]);
        }
        ox[segments] = x[x.length - 1];
        oz[segments] = z[z.length - 1];
        return new double[][] {ox, oz};
    }

    /**
     * Each inner point moved across the flow, within the snap distance, to the lowest ground (the thalweg, where the
     * terrain has finer data than the grid), then a running mean over the smoothing length; the ends stay.
     */
    private double[][] snap(double[] x, double[] z) {
        int n = x.length;
        double reach = p.channels().snap();
        double[] sx = x.clone();
        double[] sz = z.clone();
        if (reach > 0) {
            int steps = Math.max(1, (int) Math.ceil(reach));
            for (int i = 1; i + 1 < n; i++) {
                double dx = x[i + 1] - x[i - 1];
                double dz = z[i + 1] - z[i - 1];
                double length = Math.sqrt(dx * dx + dz * dz);
                if (length == 0) {
                    continue;
                }
                double rx = -dz / length;
                double rz = dx / length;
                double best = terrain.height(x[i], z[i]);
                double offset = 0;
                for (int s = -steps; s <= steps; s++) {
                    double o = reach * s / steps;
                    double h = terrain.height(x[i] + rx * o, z[i] + rz * o);
                    if (h < best - 1e-9) {
                        best = h;
                        offset = o;
                    }
                }
                sx[i] = x[i] + rx * offset;
                sz[i] = z[i] + rz * offset;
            }
        }
        int half = (int) Math.round(p.channels().smoothing() / (2 * p.surface().spacing()));
        if (half < 1) {
            return new double[][] {sx, sz};
        }
        double[] mx = sx.clone();
        double[] mz = sz.clone();
        for (int i = 1; i + 1 < n; i++) {
            int h = Math.min(half, Math.min(i, n - 1 - i));
            double ax = 0;
            double az = 0;
            for (int k = i - h; k <= i + h; k++) {
                ax += sx[k];
                az += sz[k];
            }
            mx[i] = ax / (2 * h + 1);
            mz[i] = az / (2 * h + 1);
        }
        return new double[][] {mx, mz};
    }

    /**
     * The least-squares fit to {@code values} that never rises along them (pool adjacent violators): pits are filled
     * half way, bumps cut half way, as water levels out over a profile.
     */
    static double[] monotone(double[] values) {
        int n = values.length;
        double[] sum = new double[n];
        int[] size = new int[n];
        int blocks = 0;
        for (double v : values) {
            sum[blocks] = v;
            size[blocks] = 1;
            blocks++;
            while (blocks >= 2 && sum[blocks - 2] / size[blocks - 2] < sum[blocks - 1] / size[blocks - 1]) {
                sum[blocks - 2] += sum[blocks - 1];
                size[blocks - 2] += size[blocks - 1];
                blocks--;
            }
        }
        double[] out = new double[n];
        int k = 0;
        for (int b = 0; b < blocks; b++) {
            double mean = sum[b] / size[b];
            for (int s = 0; s < size[b]; s++) {
                out[k++] = mean;
            }
        }
        return out;
    }

    /** Discharge, width, depth, speed, slope and direction along a reach's line. */
    private Channel hydraulics(int id, int downstream, int order, Line line) {
        int n = line.x.length;
        double[] distance = cumulative(line.x, line.z);
        double[] discharge = new double[n];
        double[] width = new double[n];
        double[] depth = new double[n];
        double[] speed = new double[n];
        double[] slope = new double[n];
        double[] dirX = new double[n];
        double[] dirZ = new double[n];
        WaterParameters.Channels c = p.channels();
        String name = "";
        int named = -1;
        for (int i = 0; i < n; i++) {
            int course = line.course[i];
            Watercourse w = course >= 0 ? courses.get(course) : null;
            if (w != null && named < 0) {
                named = course;
                name = w.name();
            }
            double q = w != null && !Double.isNaN(w.discharge()) ? w.discharge()
                : p.flow().runoff() * line.area[i] * 1e-6;
            discharge[i] = q;
            width[i] = w != null && !Double.isNaN(w.width()) ? w.width()
                : Math.max(c.minWidth(), c.widthCoefficient() * Maths.pow(q, c.widthExponent()));
        }
        for (int i = 0; i < n; i++) {
            // the slope over a window a few widths long, so riffles do not flicker
            double window = Math.max(c.smoothing(), Math.max(2 * width[i], 2 * p.surface().spacing()));
            int a = i;
            int b = i;
            while (a > 0 && distance[i] - distance[a] < window / 2) {
                a--;
            }
            while (b < n - 1 && distance[b] - distance[i] < window / 2) {
                b++;
            }
            double run = distance[b] - distance[a];
            double s = run > 0 ? (line.surface[a] - line.surface[b]) / run : 0;
            slope[i] = Math.max(c.minSlope(), s);
            double d = Maths.pow(discharge[i] * c.manning() / (width[i] * Math.sqrt(slope[i])), 0.6);
            double v = discharge[i] / (width[i] * d);
            if (v > c.maxSpeed()) {
                v = c.maxSpeed();
                d = discharge[i] / (width[i] * v);
            }
            depth[i] = d;
            speed[i] = v;
            int i0 = Math.max(0, i - 1);
            int i1 = Math.min(n - 1, i + 1);
            double dx = line.x[i1] - line.x[i0];
            double dz = line.z[i1] - line.z[i0];
            double length = Math.sqrt(dx * dx + dz * dz);
            dirX[i] = length > 0 ? dx / length : 1;
            dirZ[i] = length > 0 ? dz / length : 0;
        }
        int[] fall = new int[n];
        Arrays.fill(fall, -1);
        return new Channel(id, downstream, order, name, line.x, line.z, line.surface, line.ground, distance, line.area,
            discharge, width, depth, speed, slope, dirX, dirZ, fall);
    }

    /** The waterfalls along {@code channels}, marking the free falls' segments on their channels. */
    List<Waterfall> falls(List<Channel> channels) {
        WaterParameters.Falls f = p.falls();
        List<Waterfall> falls = new ArrayList<>();
        for (Channel channel : channels) {
            int n = channel.count();
            int i = 0;
            while (i + 1 < n) {
                if (gradient(channel, i) < f.minGradient()) {
                    i++;
                    continue;
                }
                // a run of steep segments, gaps of one gentler segment bridged
                int start = i;
                int end = i + 1;
                int j = i + 1;
                while (j + 1 < n) {
                    if (gradient(channel, j) >= f.minGradient()) {
                        end = j + 1;
                        j++;
                    } else if (j + 2 < n && gradient(channel, j + 1) >= f.minGradient()) {
                        j++;
                    } else {
                        break;
                    }
                }
                double height = channel.surface(start) - channel.surface(end);
                double run = channel.distance(end) - channel.distance(start);
                if (height >= f.minHeight()) {
                    boolean free = height / run >= f.freeGradient();
                    Waterfall fall = fall(falls.size(), channel, start, end, free, height);
                    falls.add(fall);
                    if (free) {
                        for (int k = start; k < end; k++) {
                            channel.markFall(k, fall.id());
                        }
                    }
                }
                i = end;
            }
        }
        return falls;
    }

    private static double gradient(Channel channel, int i) {
        double run = channel.distance(i + 1) - channel.distance(i);
        return run > 0 ? (channel.surface(i) - channel.surface(i + 1)) / run : 0;
    }

    private Waterfall fall(int id, Channel channel, int lip, int foot, boolean free, double height) {
        WaterParameters.Falls f = p.falls();
        double q = channel.discharge(lip);
        double w = channel.width(lip);
        double perWidth = q / w;
        double critical = Maths.cbrt(perWidth * perWidth / G);
        double lipSpeed = Math.max(channel.speed(lip), perWidth / (0.715 * critical));
        double thickness = perWidth / lipSpeed;
        double breakup = f.breakupCoefficient() * Maths.pow(perWidth, f.breakupExponent());
        double atomised = free ? 1 - Maths.exp(-height / f.atomisation()) : 0;
        Waterfall.Path path = free ? freePath(channel, lip, foot, lipSpeed, breakup)
            : cascadePath(channel, lip, foot);
        List<Waterfall.Spray> spray = new ArrayList<>();
        double footX = path.x()[path.count() - 1];
        double footY = path.y()[path.count() - 1];
        double footZ = path.z()[path.count() - 1];
        if (free) {
            double last = 0;
            double every = Math.max(2, height / 12);
            double next = breakup;
            for (int k = 0; k < path.count(); k++) {
                double fallen = path.fallen(k);
                if (fallen >= next || k == path.count() - 1) {
                    double atom = 1 - Maths.exp(-fallen / f.atomisation());
                    if (atom > last) {
                        spray.add(new Waterfall.Spray(path.x()[k], path.y()[k], path.z()[k],
                            0.5 * path.width()[k] + 1, q * (atom - last), path.speed(k)));
                    }
                    last = atom;
                    next = fallen + every;
                }
            }
        }
        double plunge = q * (1 - atomised);
        spray.add(new Waterfall.Spray(footX, footY, footZ, Math.max(1, w), plunge,
            path.speed(path.count() - 1)));
        double sprayRate = q * atomised + 0.2 * plunge;
        double radius = Math.max(w, f.mistRadius() * height);
        double radiusY = Math.max(1, f.mistHeight() * height);
        Waterfall.Mist mist = new Waterfall.Mist(footX, footY + 0.5 * radiusY, footZ, radius, radiusY, radius,
            f.mistDensity() * Math.sqrt(sprayRate), 1 / radiusY);
        return new Waterfall(id, channel.id(), lip, foot, free, height, w, q, lipSpeed, thickness, breakup, atomised,
            path, spray, mist);
    }

    /**
     * A free fall's sheet: ballistic from the lip at the brink's speed, against quadratic drag toward the terminal
     * speed once broken up, held off the face where the ground leans out further than the fall carries it.
     */
    private Waterfall.Path freePath(Channel channel, int lip, int foot, double lipSpeed, double breakup) {
        WaterParameters.Falls f = p.falls();
        double dx = channel.directionX(lip);
        double dz = channel.directionZ(lip);
        double x0 = channel.x(lip);
        double z0 = channel.z(lip);
        double top = channel.surface(lip);
        double bottom = channel.surface(foot);
        double height = top - bottom;
        double drag = G / (f.terminalSpeed() * f.terminalSpeed());
        double spread = Maths.tan(Maths.toRadians(f.spread()));
        double every = Math.max(1, height / 96);
        double w0 = channel.width(lip);
        Samples out = new Samples();
        double s = 0;
        double y = top;
        double vh = lipSpeed;
        double vy = 0;
        double t = 0;
        double dt = 0.02;
        double recorded = Double.NEGATIVE_INFINITY;
        double reach = channel.distance(foot) - channel.distance(lip) + 50;
        while (true) {
            double fallen = top - y;
            double clearance = Math.max(0.5, 0.02 * fallen);
            // held off the face: as little further out as the ground allows
            double hx = s;
            while (hx < reach && terrain.height(x0 + dx * hx, z0 + dz * hx) + clearance > y) {
                hx += 0.25;
            }
            s = hx;
            boolean done = y <= bottom || t > 120;
            if (fallen - recorded >= every || done) {
                double width = w0 + 2 * Math.min(fallen, breakup) * spread
                    + 2 * Math.max(0, fallen - breakup) * 3 * spread;
                out.add(x0 + dx * s, Math.max(y, bottom), z0 + dz * s, dx * vh, vy, dz * vh, t, width);
                recorded = fallen;
            }
            if (done) {
                break;
            }
            // drag acts once the sheet has broken up; before, it falls freely
            double k = fallen >= breakup ? drag : 0;
            double speed = Math.sqrt(vh * vh + vy * vy);
            vh -= k * speed * vh * dt;
            vy -= (G + k * speed * vy) * dt;
            s += vh * dt;
            y += vy * dt;
            t += dt;
        }
        return out.path();
    }

    /** A cascade's path: down the channel's own line, speeding up as it falls, at most the fastest flow. */
    private Waterfall.Path cascadePath(Channel channel, int lip, int foot) {
        Samples out = new Samples();
        double t = 0;
        for (int i = lip; i <= foot; i++) {
            double fallen = channel.surface(lip) - channel.surface(i);
            double v = Math.max(channel.speed(lip),
                Math.min(p.channels().maxSpeed(), Math.sqrt(2 * G * fallen)));
            if (i > lip) {
                double dx = channel.x(i) - channel.x(i - 1);
                double dz = channel.z(i) - channel.z(i - 1);
                double dy = channel.surface(i) - channel.surface(i - 1);
                t += Math.sqrt(dx * dx + dy * dy + dz * dz) / v;
            }
            double run = Math.max(1e-6, channel.distance(Math.min(i + 1, foot)) - channel.distance(Math.max(i - 1,
                lip)));
            double drop = channel.surface(Math.max(i - 1, lip)) - channel.surface(Math.min(i + 1, foot));
            double norm = Math.sqrt(run * run + drop * drop);
            out.add(channel.x(i), channel.surface(i), channel.z(i), channel.directionX(i) * v * run / norm,
                -v * drop / norm, channel.directionZ(i) * v * run / norm, t, channel.width(i));
        }
        return out.path();
    }

    /** The stones in the channels' beds and on their banks. */
    List<BedStone> stones(List<Channel> channels) {
        WaterParameters.Stones s = p.stones();
        WaterParameters.Banks banks = p.banks();
        List<BedStone> stones = new ArrayList<>();
        if (s.density() <= 0) {
            return stones;
        }
        for (Channel channel : channels) {
            SplittableRandom random = new SplittableRandom(Seeds.derive(p.seed(), channel.id()));
            for (int i = 0; i + 1 < channel.count(); i++) {
                if (channel.fall(i) >= 0) {
                    continue;
                }
                double w = channel.width(i);
                double length = channel.distance(i + 1) - channel.distance(i);
                double spanWidth = w * (1 + 2 * s.banks());
                double expected = s.density() * spanWidth * length;
                int count = (int) expected + (random.nextDouble() < expected - (int) expected ? 1 : 0);
                double median = s.flood() * channel.depth(i) * channel.slope(i) / (SUBMERGED * SHIELDS);
                for (int k = 0; k < count; k++) {
                    double t = random.nextDouble();
                    double across = (random.nextDouble() - 0.5) * spanWidth;
                    double size = median * Maths.exp(s.spread() * random.nextGaussian());
                    double yaw = random.nextDouble() * Maths.TAU;
                    long seed = random.nextLong();
                    size = Math.min(size, Math.min(s.maxSize(), Math.max(s.minSize(), 0.8 * w)));
                    if (size < s.minSize()) {
                        continue;
                    }
                    double cx = channel.x(i) + t * (channel.x(i + 1) - channel.x(i));
                    double cz = channel.z(i) + t * (channel.z(i + 1) - channel.z(i));
                    double surface = channel.surface(i) + t * (channel.surface(i + 1) - channel.surface(i));
                    double depth = channel.depth(i) + t * (channel.depth(i + 1) - channel.depth(i));
                    double rx = -channel.directionZ(i);
                    double rz = channel.directionX(i);
                    double u = 2 * Math.abs(across) / w;
                    double bed = u <= 1 ? surface - 1.5 * depth * (1 - u * u)
                        : surface + (Math.abs(across) - w / 2) * banks.bankSlope();
                    double base = bed - size / 3;
                    stones.add(new BedStone(cx + rx * across, base, cz + rz * across, size, yaw,
                        BedStone.preset(size), seed, channel.id(), base + size > surface));
                }
            }
        }
        return stones;
    }

    /** A growing list of path samples. */
    private static final class Samples {
        private final double[][] values = new double[8][64];
        private int count;

        void add(double... v) {
            if (count == values[0].length) {
                for (int k = 0; k < values.length; k++) {
                    values[k] = Arrays.copyOf(values[k], 2 * count);
                }
            }
            for (int k = 0; k < values.length; k++) {
                values[k][count] = v[k];
            }
            count++;
        }

        Waterfall.Path path() {
            double[][] v = new double[8][];
            for (int k = 0; k < 8; k++) {
                v[k] = Arrays.copyOf(values[k], count);
            }
            return new Waterfall.Path(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
        }
    }
}
