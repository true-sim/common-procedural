package com.truesim.common.procedural.fence;

import java.util.List;

/**
 * What a fence is: its kind and dimensions. All lengths in metres. Some fields mean different things for different
 * kinds; the presets show each.
 *
 * @param name a name, e.g. for recipes ({@code post-and-rail})
 * @param kind how it is built
 * @param height the height above the ground (posts' tops; a wall's coping)
 * @param spacing between posts (stakes for wattle, crossings for split rails, post pairs for a Stangenzaun); a wall's
 *     mean stone length
 * @param postRadius posts' (stakes') radius, half their width when square; a wall's half thickness at its foot
 * @param squarePosts sawn square posts rather than round ones
 * @param rails rails (strands of wire, stacked split rails, boards, wattle rows; 0 fills the height); a wall's courses
 * @param railRadius rails' radius (half the height of sawn rails, half a board's width, rods', wires', poles' radius)
 * @param pieceWidth pickets' width; the zigzag's half amplitude; the gap between a Stangenzaun's poles along the fence;
 *     a wall's half thickness at its top
 * @param pieceGap the gap between pickets, boards or wattle rows
 * @param sag how far rails and wires sag at their middle, as a fraction of their span
 * @param angle degrees: the zigzag's angle to the line; the slant of a Stangenzaun's poles over the ground
 */
public record FenceType(String name, FenceKind kind, double height, double spacing, double postRadius,
    boolean squarePosts, int rails, double railRadius, double pieceWidth, double pieceGap, double sag,
    double angle) {

    public FenceType {
        if (!(height > 0) || !(spacing > 0) || !(postRadius > 0) || rails < 0 || !(railRadius > 0)) {
            throw new IllegalArgumentException(name + ": needs a height, spacing, post and rail size");
        }
    }

    /** Round posts and two round rails: the common pasture fence. */
    public static final FenceType POST_AND_RAIL = new FenceType("post-and-rail", FenceKind.POST_AND_RAIL, 1.15, 2.8,
        0.075, false, 2, 0.05, 0, 0, 0.012, 0);
    /** Round posts and three round rails. */
    public static final FenceType POST_AND_THREE_RAILS = new FenceType("post-and-three-rails",
        FenceKind.POST_AND_RAIL, 1.25, 2.6, 0.08, false, 3, 0.048, 0, 0, 0.012, 0);
    /** The Swiss Schärhag: split rails in a zigzag, six to a bay, staked and lashed at the crossings. */
    public static final FenceType SCHAERHAG = new FenceType("schaerhag", FenceKind.SPLIT_RAIL, 1.15, 3.0, 0.035,
        false, 6, 0.085, 0.42, 0, 0.006, 16);
    /** Square posts, sawn rails, pointed pickets. */
    public static final FenceType PICKET = new FenceType("picket", FenceKind.PICKET, 1.0, 2.4, 0.05, true, 2, 0.04,
        0.075, 0.055, 0.004, 0);
    /** Hazel or willow rods woven between stakes. */
    public static final FenceType WATTLE = new FenceType("wattle", FenceKind.WATTLE, 0.95, 0.45, 0.03, false, 0,
        0.015, 0, 0.005, 0, 0);
    /** Square posts, horizontal boards across their front. */
    public static final FenceType BOARD = new FenceType("board", FenceKind.BOARD, 1.35, 2.6, 0.06, true, 0, 0.08,
        0, 0.025, 0.003, 0);
    /** The Alpine Stangenzaun: slanted poles between lashed post pairs. */
    public static final FenceType STANGENZAUN = new FenceType("stangenzaun", FenceKind.STANGENZAUN, 1.25, 2.2, 0.045,
        false, 0, 0.038, 0.34, 0, 0, 38);
    /** A dry-stone wall of gneiss blocks, about a metre high. */
    public static final FenceType DRY_STONE = new FenceType("dry-stone", FenceKind.DRY_STONE, 1.0, 0.42, 0.34, false,
        4, 0.1, 0.24, 0, 0, 0);
    /** Round posts and four strands of galvanised wire. */
    public static final FenceType WIRE = new FenceType("wire", FenceKind.WIRE, 1.2, 3.6, 0.06, false, 4, 0.002, 0,
        0, 0.012, 0);

    /** Every preset. */
    public static final List<FenceType> PRESETS = List.of(POST_AND_RAIL, POST_AND_THREE_RAILS, SCHAERHAG, PICKET,
        WATTLE, BOARD, STANGENZAUN, DRY_STONE, WIRE);

    /** The preset named {@code name}. */
    public static FenceType preset(String name) {
        for (FenceType preset : PRESETS) {
            if (preset.name().equals(name)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("No fence type '" + name + "'");
    }

    /** This type with {@code count} rails (strands, boards, rows, courses). */
    public FenceType withRails(int count) {
        return new FenceType(name, kind, height, spacing, postRadius, squarePosts, count, railRadius, pieceWidth,
            pieceGap, sag, angle);
    }

    /** This type {@code newHeight} metres high. */
    public FenceType withHeight(double newHeight) {
        return new FenceType(name, kind, newHeight, spacing, postRadius, squarePosts, rails, railRadius, pieceWidth,
            pieceGap, sag, angle);
    }

    /** This type with posts (stakes, crossings, stones) {@code newSpacing} apart. */
    public FenceType withSpacing(double newSpacing) {
        return new FenceType(name, kind, height, newSpacing, postRadius, squarePosts, rails, railRadius, pieceWidth,
            pieceGap, sag, angle);
    }
}
