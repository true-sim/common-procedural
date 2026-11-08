package com.truesim.common.procedural.tree;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Rng;

/** A species' trunks or stems from the ground: one per stem, as level-0 branches. */
final class Trunks {

    private Trunks() {
    }

    /**
     * Grows the stems of {@code species}, each {@code length} metres long (shrubs' a little shorter at random) for a
     * tree {@code height} metres tall.
     */
    static void grow(Skeleton skeleton, TreeSpecies species, double height, double length, Rng rng) {
        int stems = species.stems();
        for (int k = 0; k < stems; k++) {
            Vec3 base = Vec3.ZERO;
            Vec3 direction = Axes.UP;
            double stemLength = length;
            if (stems > 1) {
                double angle = Maths.TAU * k / stems + (rng.nextDouble() - 0.5) * 0.6;
                double r = species.stemSpread() * (0.3 + 0.7 * rng.nextDouble());
                Vec3 outward = new Vec3(Maths.cos(angle), 0, Maths.sin(angle));
                base = outward.scale(r);
                direction = Axes.turnToward(Axes.UP, outward,
                    species.stemTilt() * (0.7 + 0.6 * rng.nextDouble()) * Math.PI / 180);
                stemLength = length * (0.75 + 0.25 * rng.nextDouble());
            }
            // Lean: a small tilt in a random direction (the wind format puts pivots on the y axis)
            double leanAngle = rng.nextDouble() * Maths.TAU;
            Vec3 leanTo = new Vec3(Maths.cos(leanAngle), 0, Maths.sin(leanAngle));
            direction = Axes.turnToward(direction, leanTo, species.lean() * rng.nextDouble());
            int branch = skeleton.beginBranch(0, -1);
            // Stems of shrubs bow back toward the sky as they rise; a tree's trunk keeps straightening its wobble
            double curve = stems > 1 ? species.stemTilt() * 0.5 : species.wobble() * stemLength * 0.5;
            Axes.grow(skeleton, branch, -1, base, direction, stemLength, species.trunkSegment(), curve,
                species.wobble(), 0, rng);
        }
    }
}
