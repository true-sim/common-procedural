package com.truesim.common.procedural.fence;

import com.truesim.annotations.AllowAllocation;

/**
 * A piece as the layout describes it and the mesher draws it: a beam (a cross-section swept along a centre line) or a
 * stone (a rounded, lumpy block in a frame of its own). Positions in the ground's frame.
 */
@AllowAllocation("generation, at load time")
final class Piece {

    /** A beam's cross-section. */
    enum Section {
        /** Round (elliptical with unequal half sizes). */
        ROUND,
        /** Sawn: a rectangle. */
        RECT,
        /** A split rail: a quarter of a log. */
        SPLIT
    }

    /** How a beam ends. */
    enum End {
        /** Not closed (inside a post, or under the ground). */
        OPEN,
        /** Cut square. */
        FLAT,
        /** A point (pickets, posts cut to a cone). */
        POINT,
        /** Broken: a splintered, lopsided point. */
        JAGGED
    }

    FencePiece.Role role;
    int part;
    FenceMaterial material;
    // a beam
    Section section = Section.ROUND;
    double hn;
    double hb;
    double[] path = new double[0];
    double upX;
    double upY = 1;
    double upZ;
    double roll;
    End start = End.OPEN;
    End end = End.FLAT;
    double tip;
    double jagN;
    double jagB;
    boolean closed;
    /** Fewer sides than the posts' (rods, wire, lashings). */
    boolean thin;
    // a stone: its bottom centre, its frame (along, up, across: unit vectors) and half sizes
    boolean stone;
    final double[] frame = new double[9];
    double cx;
    double cy;
    double cz;
    double sx;
    double sy;
    double sz;
    double roundness;
    long shape;
    // its look
    double v0;
    double tone;
    double wear;
    // the levels it is drawn at
    int minLevel;
    int maxLevel = Integer.MAX_VALUE;

    Piece(FencePiece.Role role, FenceMaterial material) {
        this.role = role;
        this.material = material;
        this.part = material.part();
    }

    /** A beam along {@code points} (x, y, z triples). */
    Piece beam(Section shape, double halfN, double halfB, double[] points) {
        this.section = shape;
        this.hn = halfN;
        this.hb = halfB;
        this.path = points;
        return this;
    }

    Piece up(double x, double y, double z) {
        upX = x;
        upY = y;
        upZ = z;
        return this;
    }

    Piece ends(End first, End last) {
        start = first;
        end = last;
        return this;
    }

    Piece levels(int min, int max) {
        minLevel = min;
        maxLevel = max;
        return this;
    }

    int points() {
        return path.length / 3;
    }

    boolean shownAt(int level) {
        return level >= minLevel && level <= maxLevel;
    }

    /** Its centre line (a stone's bottom and top centres), for {@link FencePiece}. */
    double[] centreLine() {
        if (!stone) {
            return path;
        }
        return new double[] {cx, cy, cz, cx + frame[3] * 2 * sy, cy + frame[4] * 2 * sy, cz + frame[5] * 2 * sy};
    }

    /** Points along a straight line from a to b, at most {@code step} apart (at least {@code min} segments). */
    static double[] line(double ax, double ay, double az, double bx, double by, double bz, double step, int min) {
        double l = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        int n = Math.max(min, (int) Math.ceil(l / step));
        double[] out = new double[3 * (n + 1)];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            out[3 * i] = ax + t * (bx - ax);
            out[3 * i + 1] = ay + t * (by - ay);
            out[3 * i + 2] = az + t * (bz - az);
        }
        return out;
    }
}
