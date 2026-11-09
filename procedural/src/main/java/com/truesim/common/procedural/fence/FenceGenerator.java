package com.truesim.common.procedural.fence;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Noise;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates fences along a line over the ground ({@link FenceLayout} lays the pieces out, {@link FenceMesher} draws
 * them at each level, {@link FenceColors} weathers them). The same type, options, tuning, line, ground and seed always
 * give bit-identical meshes.
 *
 * <p><b>Levels of detail</b> ({@link FenceTuning#levels()}): 0 everything; 1 round pieces with fewer sides (8, 5, 3 by
 * default), rings at every other point, stones less rounded; 2 posts and rails as few-sided prisms, wattle as woven
 * panels, a wall as two bands of blocks following the ground, lashings gone. Each level's error is the largest
 * distance its simplification moves a surface (a polygon's sag under a round piece's radius, a dropped piece's
 * thickness, a stone's rounding), never less than the finer level's.
 *
 * <p>Keeps its working memory from fence to fence; not thread-safe ({@link Fences} keeps one per thread).
 */
@AllowAllocation("generation, at load time")
public final class FenceGenerator {

    /** Version of the generated output: changes whenever the same inputs would give different meshes. */
    public static final int VERSION = 1;

    private final FenceMesher mesher = new FenceMesher();

    /**
     * A fence of {@code type} as {@code options} say, along {@code line} (x, z pairs in the ground's frame, metres) over
     * {@code ground}, made from {@code seed}.
     */
    public Fence generate(FenceType type, FenceOptions options, FenceTuning tuning, double[] line, FenceGround ground,
        long seed) {
        FencePath path = new FencePath(line);
        FenceLayout layout = new FenceLayout(type, options, tuning, path, ground, Seeds.derive(seed, 0));
        layout.build();
        List<Piece> pieces = layout.pieces;
        double ox = path.x(0);
        double oz = path.z(0);
        double oy = ground.height(ox, oz);
        FenceColors colors = new FenceColors(options, ground, Seeds.derive(seed, 1));
        Noise stones = Noise.seeded(Seeds.derive(seed, 2));
        List<ProceduralMesh> lods = new ArrayList<>(tuning.levels());
        List<FencePiece> drawn = new ArrayList<>();
        double error = 0;
        for (int level = 0; level < tuning.levels(); level++) {
            error = Math.max(error, level == 0 ? 0 : error(pieces, level, tuning));
            lods.add(mesher.mesh(pieces, level, colors, stones, ox, oy, oz, tuning.sides(), error));
            if (level == 0) {
                for (int i = 0; i < pieces.size(); i++) {
                    Piece p = pieces.get(i);
                    if (p.shownAt(0)) {
                        drawn.add(new FencePiece(p.role, p.part, p.centreLine(), 2 * (p.stone ? p.sx : p.hb),
                            2 * (p.stone ? p.sz : p.hn), mesher.ranges[2 * i], mesher.ranges[2 * i + 1]));
                    }
                }
            }
        }
        return new Fence(type, options, seed, new Vec3(ox, oy, oz), path.length(), drawn, lods);
    }

    /** The largest distance level {@code level} moves a surface of level 0. */
    private double error(List<Piece> pieces, int level, FenceTuning tuning) {
        double worst = 0;
        for (Piece p : pieces) {
            if (!p.shownAt(0)) {
                continue;
            }
            if (!p.shownAt(level)) {
                worst = Math.max(worst, 2 * Math.min(p.hn, p.hb) + (p.stone ? 2 * Math.min(p.sy, p.sz) : 0));
                continue;
            }
            if (p.stone) {
                int m0 = FenceMesher.stoneSubdivisions(p, 0);
                int m = FenceMesher.stoneSubdivisions(p, level);
                double size = Math.min(p.sx, Math.min(p.sy, p.sz));
                worst = Math.max(worst, size * (1.0 / m - 1.0 / m0) * 0.35);
            } else if (p.section != Piece.Section.RECT) {
                int k = FenceMesher.roundSides(p, level, tuning.sides());
                double r = Math.max(p.hn, p.hb);
                worst = Math.max(worst, r * (1 - Maths.cos(Maths.PI / k)));
            }
        }
        return worst;
    }
}
