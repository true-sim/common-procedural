package com.truesim.common.procedural.tree;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Rng;
import java.util.Arrays;

/**
 * Parametric growth after Weber and Penn ("Creation and rendering of realistic trees", SIGGRAPH 1995): trunks, then
 * level by level the children of every branch, placed along it by its level's {@link Branching} rules. Conifers' habit
 * is that regular (apical dominance, whorls, a crown shaped by branch lengths), so rules reproduce it directly.
 */
final class ParametricGrowth {

    private double[] arc = new double[64];

    /** Grows {@code species}' trunks and branches into {@code skeleton}, {@code height} metres tall. */
    void grow(Skeleton skeleton, TreeSpecies species, double height, Rng rng) {
        Trunks.grow(skeleton, species, height, height, rng);
        for (int b = 0; b < skeleton.branchCount(); b++) {
            int level = skeleton.level(b);
            if (level < species.maxLevel()) {
                children(skeleton, species, species.levels().get(level), b, height, rng);
            }
        }
    }

    @AllowAllocation("growing, until warm")
    private void children(Skeleton skeleton, TreeSpecies species, Branching rule, int parent, double height,
        Rng rng) {
        int first = skeleton.firstNode(parent);
        int count = skeleton.nodeCount(parent);
        if (arc.length < count) {
            arc = Arrays.copyOf(arc, 2 * count);
        }
        arc[0] = 0;
        for (int i = 1; i < count; i++) {
            arc[i] = arc[i - 1] + Axes.distance(skeleton, first + i, first + i - 1);
        }
        double length = arc[count - 1];
        int level = skeleton.level(parent);
        double base = level == 0 ? height : length;
        double from = rule.start() * length;
        double to = rule.end() * length;
        double azimuth = rng.nextDouble() * 360;
        int position = 0;
        for (double s = from; s <= to; s += rule.spacing() * (0.85 + 0.3 * rng.nextDouble())) {
            int node = nearest(arc, count, s);
            double t = (s - from) / Math.max(to - from, 1e-9);
            Vec3 tangent = Axes.tangent(skeleton, parent, first + node);
            azimuth += rule.rotate() + (rng.nextDouble() - 0.5) * 20;
            for (int c = 0; c < rule.perPosition(); c++) {
                double childLength = rule.length() * base * rule.shape().at(t)
                    * (1 + rule.lengthVariation() * (2 * rng.nextDouble() - 1));
                double a = azimuth + 360.0 * c / rule.perPosition() + (rng.nextDouble() - 0.5) * 16;
                double side = (position + c) % 2 == 0 ? 90 : -90;
                a = Maths.lerp(a, side, rule.planar());
                double down = rule.downAngle() + rule.downAngleVariation() * (2 * rng.nextDouble() - 1);
                if (childLength < rule.segment() * 0.5) {
                    continue;
                }
                Vec3 direction = Axes.child(tangent, down, a);
                int child = skeleton.beginBranch(level + 1, parent);
                Axes.grow(skeleton, child, first + node, Axes.position(skeleton, first + node), direction,
                    childLength, rule.segment(), rule.curve(), rule.curveVariation(), rule.droop(), rng);
            }
            position++;
        }
    }

    /** The node whose arc length is nearest {@code s}. */
    private static int nearest(double[] arc, int count, double s) {
        int best = 0;
        for (int i = 1; i < count; i++) {
            if (Math.abs(arc[i] - s) < Math.abs(arc[best] - s)) {
                best = i;
            }
        }
        return best;
    }
}
