package com.truesim.common.procedural.fence;

import com.truesim.common.math.Maths;
import com.truesim.common.math.random.Noise;

/**
 * The albedo at a point of a piece: its material weathered as much as the fence and the piece say, with lichen in
 * patches and moss at the foot and on tops, all as vertex colors (linear sRGB). Weathering is stronger on faces
 * turned up (rain and sun), wood is darker where it stays damp near the ground; paint fades and peels to grey wood;
 * zinc dulls and rusts.
 */
final class FenceColors {

    private static final double[] MOSS = {0.045, 0.085, 0.018};
    private static final double[] MOSS_DRY = {0.11, 0.12, 0.04};
    private static final double[] LICHEN_GREY = {0.40, 0.43, 0.35};
    private static final double[] LICHEN_ORANGE = {0.52, 0.28, 0.05};
    private static final double[] LICHEN_DARK = {0.06, 0.06, 0.055};
    private static final double[] RUST = {0.22, 0.075, 0.025};
    private static final double[] BARE_WOOD = {0.30, 0.29, 0.27};

    private final FenceOptions options;
    private final FenceGround ground;
    private final Noise noise;
    double r;
    double g;
    double b;
    double moss;
    double lichen;

    FenceColors(FenceOptions options, FenceGround ground, long seed) {
        this.options = options;
        this.ground = ground;
        this.noise = Noise.seeded(seed);
    }

    /** The albedo at (x, y, z) of {@code piece}, its surface facing (nx, ny, nz): into r, g, b, moss, lichen. */
    void shade(Piece piece, double x, double y, double z, double nx, double ny, double nz) {
        FenceMaterial m = piece.material;
        double above = y - ground.height(x, z);
        double grain = noise.simplex(x * 3.1, y * 3.1, z * 3.1);
        double w = Maths.clamp01(options.weathering() + piece.wear + 0.12 * Math.max(ny, 0) - 0.08 * Math.max(-ny, 0));
        r = m.fresh(0) + w * (m.weathered(0) - m.fresh(0));
        g = m.fresh(1) + w * (m.weathered(1) - m.fresh(1));
        b = m.fresh(2) + w * (m.weathered(2) - m.fresh(2));
        double tone = 1 + 0.14 * piece.tone + 0.07 * grain;
        if (m.painted()) {
            // faded and peeled board by board (finer than the vertices could show is the material's)
            double peel = 0.55 * Maths.smoothstep(0.55, 0.95, w + 0.3 * piece.tone);
            mix(BARE_WOOD, peel);
        }
        r *= tone;
        g *= tone;
        b *= tone;
        if (piece.part == Fence.METAL) {
            double rust = Maths.smoothstep(0.55, 0.85, 0.7 * w + 0.45 * noise.simplex(x * 7, y * 7, z * 7));
            mix(RUST, rust * Maths.smoothstep(0.4, 0.9, w));
            moss = 0;
            lichen = 0;
            return;
        }
        // damp near the ground: darker wood (stone less so)
        double damp = 1 - Maths.smoothstep(0, 0.35, above);
        double dark = 1 - (piece.part == Fence.WOOD ? 0.3 : 0.15) * damp;
        r *= dark;
        g *= dark;
        b *= dark;
        // lichen: patches, more on stone, up the piece rather than at its foot
        double stoneFactor = piece.part == Fence.STONE ? 1.25 : 1;
        double patch = 0.5 + 0.5 * noise.simplex(x * 4.3 + 17, y * 4.3, z * 4.3);
        double cover = Math.min(1, options.lichen() * stoneFactor * (m.painted() ? 0.3 : 1));
        lichen = cover <= 0 ? 0 : Maths.smoothstep(1 - 0.75 * cover, 1.08 - 0.75 * cover, patch)
            * Maths.smoothstep(0.05, 0.3, above);
        double kind = noise.simplex(x * 1.7 + 41, y * 1.7, z * 1.7);
        double[] lichenColor = kind > 0.65 ? LICHEN_ORANGE : kind < -0.45 && piece.part == Fence.STONE ? LICHEN_DARK
            : LICHEN_GREY;
        mix(lichenColor, 0.75 * lichen);
        // moss: at the foot, higher with more moss; on faces turned up when there is much
        double reach = 0.08 + 0.5 * options.moss();
        double foot = 1 - Maths.smoothstep(0.02, reach, above);
        double tops = Maths.smoothstep(0.45, 0.9, ny) * Maths.smoothstep(0.35, 0.8, options.moss())
            * (piece.part == Fence.STONE ? 1 : 0.6);
        double clumps = Maths.clamp01(0.55 + 0.8 * noise.simplex(x * 2.6 + 5, y * 2.6, z * 2.6));
        moss = options.moss() <= 0 ? 0 : Maths.clamp01(Math.max(foot, tops) * clumps * Math.min(1, 1.6 * options.moss()));
        double fresh = 0.5 + 0.5 * noise.simplex(x * 1.1 - 9, y * 1.1, z * 1.1);
        double mr = MOSS[0] + fresh * (MOSS_DRY[0] - MOSS[0]);
        double mg = MOSS[1] + fresh * (MOSS_DRY[1] - MOSS[1]);
        double mb = MOSS[2] + fresh * (MOSS_DRY[2] - MOSS[2]);
        r += moss * (mr - r);
        g += moss * (mg - g);
        b += moss * (mb - b);
    }

    private void mix(double[] color, double amount) {
        r += amount * (color[0] - r);
        g += amount * (color[1] - g);
        b += amount * (color[2] - b);
    }
}
