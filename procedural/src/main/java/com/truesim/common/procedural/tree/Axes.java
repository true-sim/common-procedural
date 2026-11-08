package com.truesim.common.procedural.tree;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Rng;

/** Growing one branch axis node by node, and the turns it makes: shared by both growth methods. */
final class Axes {

    static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double DEGREES = Math.PI / 180;

    private Axes() {
    }

    /** {@code d} turned toward {@code target} by {@code radians} (away for negative), in the plane they span. */
    static Vec3 turnToward(Vec3 d, Vec3 target, double radians) {
        Vec3 perpendicular = target.sub(d.scale(target.dot(d)));
        double length = perpendicular.length();
        if (length < 1e-9) {
            return d;
        }
        return d.scale(Maths.cos(radians)).addScaled(perpendicular, Maths.sin(radians) / length).normalize();
    }

    /** {@code d} turned by {@code radians} toward a random direction perpendicular to it. */
    static Vec3 turnRandomly(Vec3 d, double radians, Rng rng) {
        return turnToward(d, rng.nextVec3OnSphere(), radians);
    }

    /** A unit vector perpendicular to {@code t}: the sky's part across it, else x's. */
    static Vec3 across(Vec3 t) {
        Vec3 up = UP.sub(t.scale(t.y()));
        if (up.lengthSquared() > 1e-6) {
            return up.normalize();
        }
        return new Vec3(1, 0, 0).sub(t.scale(t.x())).normalize();
    }

    /** A child's direction: {@code down} degrees off {@code t}, turned {@code azimuth} degrees about it from across. */
    static Vec3 child(Vec3 t, double down, double azimuth) {
        Vec3 n = across(t);
        Vec3 b = t.cross(n);
        double a = azimuth * DEGREES;
        double p = down * DEGREES;
        Vec3 side = n.scale(Maths.cos(a)).addScaled(b, Maths.sin(a));
        return t.scale(Maths.cos(p)).addScaled(side, Maths.sin(p)).normalize();
    }

    /**
     * Grows {@code branch} (the one begun last) from {@code start} toward {@code direction} for {@code length}
     * metres in steps of about {@code segment}: each step turning {@code curve} degrees in all toward the sky,
     * randomly {@code wobble} degrees per metre, and sinking by {@code droop} (its tip lowered by about that fraction
     * of the length, more toward the tip). The first node lies at {@code start}, child of {@code parentNode}.
     */
    static void grow(Skeleton skeleton, int branch, int parentNode, Vec3 start, Vec3 direction, double length,
        double segment, double curve, double wobble, double droop, Rng rng) {
        int steps = Math.max(1, (int) Math.ceil(length / segment));
        double step = length / steps;
        double droopAngle = 2 * droop;
        Vec3 d = direction.normalize();
        Vec3 p = start;
        int node = skeleton.addNode(branch, p.x(), p.y(), p.z(), parentNode);
        for (int i = 1; i <= steps; i++) {
            d = turnToward(d, UP, curve * DEGREES / steps);
            d = turnRandomly(d, wobble * DEGREES * step * (rng.nextDouble() * 2 - 1), rng);
            d = turnToward(d, UP, -droopAngle * (2 * i - 1) / ((double) steps * steps));
            p = p.addScaled(d, step);
            node = skeleton.addNode(branch, p.x(), p.y(), p.z(), node);
        }
    }

    /** The length of {@code branch}: the sum of its segments. */
    static double length(Skeleton skeleton, int branch) {
        int first = skeleton.firstNode(branch);
        double total = 0;
        for (int n = first + 1; n < first + skeleton.nodeCount(branch); n++) {
            total += distance(skeleton, n, n - 1);
        }
        return total;
    }

    static double distance(Skeleton skeleton, int a, int b) {
        double dx = skeleton.x(a) - skeleton.x(b);
        double dy = skeleton.y(a) - skeleton.y(b);
        double dz = skeleton.z(a) - skeleton.z(b);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static Vec3 position(Skeleton skeleton, int node) {
        return new Vec3(skeleton.x(node), skeleton.y(node), skeleton.z(node));
    }

    /** The branch's direction at node {@code n}: toward the next node, or from the previous at its end. */
    static Vec3 tangent(Skeleton skeleton, int branch, int n) {
        int first = skeleton.firstNode(branch);
        int last = first + skeleton.nodeCount(branch) - 1;
        int a = n < last ? n : n - 1;
        Vec3 d = position(skeleton, a + 1).sub(position(skeleton, a));
        return d.lengthSquared() > 0 ? d.normalize() : UP;
    }
}
