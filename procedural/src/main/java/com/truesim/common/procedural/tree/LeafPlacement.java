package com.truesim.common.procedural.tree;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.random.Rng;

/**
 * Hangs leaf cards on the skeleton's twigs: along the outer part of every branch of the foliage's levels, one sample
 * every {@code spacing} metres from the tip back (at least one at the tip), each sample cards by the foliage's kind.
 * Cards face away from the crown's center.
 */
final class LeafPlacement {

    private LeafPlacement() {
    }

    static void place(Skeleton skeleton, Foliage foliage, Vec3 crownCenter, Rng rng) {
        int branches = skeleton.branchCount();
        for (int b = 0; b < branches; b++) {
            if (skeleton.level(b) < foliage.fromLevel()) {
                continue;
            }
            double length = Axes.length(skeleton, b);
            double from = length * (1 - foliage.tipFraction());
            int first = skeleton.firstNode(b);
            int count = skeleton.nodeCount(b);
            // Walk the branch from its tip back
            double s = length;
            int segmentEnd = first + count - 1;
            double endArc = length;
            while (s >= from - 1e-9 && segmentEnd > first) {
                int segmentStart = segmentEnd - 1;
                double segmentLength = Axes.distance(skeleton, segmentEnd, segmentStart);
                double startArc = endArc - segmentLength;
                if (s < startArc && segmentStart > first) {
                    segmentEnd = segmentStart;
                    endArc = startArc;
                    continue;
                }
                double t = segmentLength > 0 ? Math.clamp((s - startArc) / segmentLength, 0, 1) : 0;
                Vec3 a = Axes.position(skeleton, segmentStart);
                Vec3 e = Axes.position(skeleton, segmentEnd);
                Vec3 p = a.interpolate(e, t);
                Vec3 tangent = segmentLength > 0 ? e.sub(a).scale(1 / segmentLength) : Axes.UP;
                int node = t > 0.5 ? segmentEnd : segmentStart;
                cards(skeleton, foliage, node, p, tangent, crownCenter, rng);
                s -= foliage.spacing() * (0.8 + 0.4 * rng.nextDouble());
            }
        }
    }

    private static void cards(Skeleton skeleton, Foliage foliage, int node, Vec3 p, Vec3 tangent, Vec3 crownCenter,
        Rng rng) {
        Vec3 outward = p.sub(crownCenter);
        outward = outward.lengthSquared() > 1e-9 ? outward.normalize() : Axes.UP;
        double length = foliage.length() * (0.85 + 0.3 * rng.nextDouble());
        double width = foliage.width() * (0.85 + 0.3 * rng.nextDouble());
        switch (foliage.kind()) {
            case NEEDLE_SPRAY -> {
                // Two cards crossed along the twig, 40 degrees either side of level, ending at p
                Vec3 across = Axes.across(tangent);
                Vec3 start = p.addScaled(tangent, -length);
                for (int k = 0; k < 2; k++) {
                    Vec3 normal = rotate(across, tangent, (k == 0 ? 40 : -40) * Math.PI / 180);
                    card(skeleton, node, start, tangent, normal, outward, length, width);
                }
            }
            case NEEDLE_TUFT -> {
                // A tuft: two crossed cards from the twig, spreading outward along it
                Vec3 direction = Axes.turnToward(tangent, outward, 0.5).normalize();
                direction = Axes.turnRandomly(direction, 0.25, rng);
                Vec3 across = Axes.across(direction);
                double roll = rng.nextDouble() * Math.PI;
                for (int k = 0; k < 2; k++) {
                    Vec3 normal = rotate(across, direction, roll + k * Math.PI / 2);
                    card(skeleton, node, p.addScaled(direction, -0.15 * length), direction, normal, outward,
                        length, width);
                }
            }
            case LEAF_CLUSTER -> {
                // A rosette of three cluster cards from the twig outward and a little down, faces toward the sky
                Vec3 out = Axes.turnToward(tangent, outward, 0.6);
                double turn = rng.nextDouble() * Maths.TAU;
                for (int k = 0; k < 3; k++) {
                    Vec3 tilted = Axes.turnToward(out, Axes.across(out), 0.7);
                    Vec3 direction = rotate(tilted, out, turn + k * Maths.TAU / 3);
                    direction = Axes.turnToward(direction, Axes.UP, -0.3 * rng.nextDouble());
                    direction = Axes.turnRandomly(direction, 0.25, rng);
                    Vec3 normal = Axes.across(direction);
                    normal = rotate(normal, direction, (rng.nextDouble() - 0.5) * 1.2);
                    card(skeleton, node, p, direction, normal, outward, length, width);
                }
            }
        }
    }

    /** {@code v} rotated by {@code radians} about the unit {@code axis} (Rodrigues). */
    static Vec3 rotate(Vec3 v, Vec3 axis, double radians) {
        double c = Maths.cos(radians);
        double s = Maths.sin(radians);
        return v.scale(c).add(axis.cross(v).scale(s)).addScaled(axis, axis.dot(v) * (1 - c));
    }

    private static void card(Skeleton skeleton, int node, Vec3 origin, Vec3 direction, Vec3 normal, Vec3 outward,
        double length, double width) {
        // The card's face toward the crown's outside (the mesher bends its shading normals further out)
        Vec3 n = normal.dot(outward) < 0 ? normal.negate() : normal;
        skeleton.addLeaf(node, origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z(),
            n.x(), n.y(), n.z(), length, width, 1);
    }
}
