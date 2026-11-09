package com.truesim.common.procedural.settlement;

import com.truesim.common.math.Maths;

import com.truesim.common.math.random.Noise;

/**
 * How the terrain adapts to a settlement's roads and square: the terrain modifier for effects-terrain's hook (heights
 * and surface weights), a pure function of the point.
 *
 * <ul>
 *   <li><b>Height</b> ({@link #height}): under a road the road's own surface height (its crown, camber and the
 *       blending across junctions: the very function the road meshes are made with), sunk a little under it so the
 *       terrain never shows through; at the road's edge exactly the edge's height, level for a shoulder, then a smooth
 *       cut or fill (an S-curve with zero slope at both ends) to the natural ground over a run of the batter's slope
 *       (at least {@link SettlementParameters.Surface#minRun}). The square likewise, on its plane.</li>
 *   <li><b>Surface</b> ({@link #verge}): beside the edge a worn verge and gravel scatter, their edge broken by noise,
 *       to blend the seam into the grass; {@link #grass} fades with them.</li>
 * </ul>
 */
public final class Grading {

    /** Over how many metres inside the edge the terrain sinks under the road. */
    private static final double SINK_RAMP = 0.25;
    /**
     * How far inside the edge the terrain keeps the road's own height: a terrain vertex there is at the road's
     * height, so between it and the shoulder's the terrain meets the road's edge exactly at the finest tiles' spacing.
     */
    static final double EDGE = 0.6;

    private final RoadField field;
    private final SettlementParameters.Surface surface;
    private final Noise noise;
    private final ThreadLocal<RoadField.Sample> samples = ThreadLocal.withInitial(RoadField.Sample::new);

    Grading(RoadField field, SettlementParameters.Surface surface, long seed) {
        this.field = field;
        this.surface = surface;
        this.noise = Noise.seeded(seed);
    }

    RoadField field() {
        return field;
    }

    /**
     * The terrain's height at ({@code x}, {@code z}) where its natural height is {@code terrain}: the road's surface
     * under it (less the sink), the road's edge height over the shoulder, then the cut or fill to the natural ground.
     */
    public double height(double x, double z, double terrain) {
        RoadField.Sample s = samples.get();
        field.sample(x, z, s);
        double d = s.graded;
        if (!(d < Double.POSITIVE_INFINITY) || Double.isNaN(s.height)) {
            return terrain;
        }
        double road = s.height;
        if (d <= 0) {
            // by the depth into the paved surface where steps lie beside it (their cuts are its edge)
            double depth = s.paved > d + 1e-9 ? -s.paved : -d;
            double sink = surface.sink() * Maths.smoothstep(0, 1, (depth - EDGE) / SINK_RAMP);
            if (s.paved > 0) {
                // off the paved surface but under a flight of steps: the steps' cut edges keep the road's height,
                // under the treads lower still (they step above and below the flight's line by half a rise)
                double off = Maths.smoothstep(0, 1, (s.paved - EDGE) / SINK_RAMP);
                sink = (surface.sink() + surface.stepRise()) * Math.min(off, Maths.smoothstep(0, 1, (-d - EDGE)
                    / SINK_RAMP));
            }
            return road - sink;
        }
        if (d <= surface.shoulder()) {
            return road;
        }
        double delta = terrain - road;
        double run = Math.max(surface.minRun(), Math.abs(delta) * surface.batter());
        double e = (d - surface.shoulder()) / run;
        if (e >= 1) {
            return terrain;
        }
        // smootherstep: level at the shoulder, level into the natural ground
        double t = e * e * e * (e * (e * 6 - 15) + 10);
        return road + delta * t;
    }

    /** Whether the terrain at ({@code x}, {@code z}) is changed: under or near a road or the square. */
    public boolean graded(double x, double z, double terrain) {
        return height(x, z, terrain) != terrain;
    }

    /** The distance from ({@code x}, {@code z}) to the paved surface (negative on it), metres. */
    public double distance(double x, double z) {
        RoadField.Sample s = samples.get();
        field.sample(x, z, s);
        return s.paved;
    }

    /** What the road surface at ({@code x}, {@code z}) is made of, or null off the paved surface. */
    public RoadSurface surface(double x, double z) {
        RoadField.Sample s = samples.get();
        field.sample(x, z, s);
        return s.paved <= 0 ? s.surface : null;
    }

    /** How far beyond the roads' and the square's edges the grading reaches at most, metres. */
    public double reach() {
        return field.reach();
    }

    /**
     * The terrain's surface weights beside the roads at ({@code x}, {@code z}) into {@code out}: [0] a worn verge
     * (bare, trodden earth), [1] gravel scattered from a gravel road, each 0 to 1, their outer edge broken by noise;
     * both 0 away from roads (and on the paved surface, which the meshes cover).
     */
    public void verge(double x, double z, double[] out) {
        RoadField.Sample s = samples.get();
        field.sample(x, z, s);
        out[0] = 0;
        out[1] = 0;
        double d = s.paved;
        if (!(d < surface.verge() * 1.6) || s.surface == null) {
            return;
        }
        double broken = d / surface.verge() + 0.3 * noise.simplex(x / 1.3, z / 1.3);
        double worn = 1 - Maths.smoothstep(0, 1, broken);
        out[0] = worn;
        if (s.surface == RoadSurface.GRAVEL) {
            out[1] = worn * (0.55 + 0.45 * noise.simplex(x / 0.4 + 31, z / 0.4));
        }
    }

    /** How much grass grows at ({@code x}, {@code z}) for the roads: 0 on them, fading in across the verge. */
    public double grass(double x, double z) {
        RoadField.Sample s = samples.get();
        field.sample(x, z, s);
        double d = s.graded;
        if (!(d < surface.verge() * 1.6)) {
            return 1;
        }
        return Maths.smoothstep(0, 1, d / surface.verge() + 0.3 * noise.simplex(x / 1.3, z / 1.3) - 0.05);
    }

    long bytes() {
        return 1 << 16;
    }
}
