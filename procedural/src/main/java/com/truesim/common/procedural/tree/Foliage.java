package com.truesim.common.procedural.tree;

/**
 * A species' leaves or needles as textured cards, and where on its leaf atlas they are. Cards hang from the twigs of
 * {@link #fromLevel()} and finer, along the outer {@link #tipFraction()} of each.
 *
 * <p>The atlas is a grid of {@code atlasColumns} x {@code atlasRows} tiles; each season shows one tile
 * ({@link #tile}), its texture filling the tile (u across the card, v from where it is attached to its far end).
 * The presets use a 2 x 2 atlas per species: spring, summer, autumn, winter in reading order.
 *
 * @param kind how cards are laid out
 * @param length a card's length, metres
 * @param width a card's width, metres
 * @param spacing metres between cards along a twig
 * @param fromLevel the coarsest branch level carrying cards
 * @param tipFraction the outer part of each twig carrying cards, as a fraction of its length
 * @param flutter the leaf weight at a card's far edge, 0 to 1 (birch leaves flutter, needles hardly)
 * @param deciduous whether the tree is bare in winter
 * @param atlasColumns tiles across the atlas
 * @param atlasRows tiles down the atlas
 * @param springTile the tile shown in spring
 * @param summerTile the tile shown in summer
 * @param autumnTile the tile shown in autumn
 * @param winterTile the tile shown in winter
 */
public record Foliage(Kind kind, double length, double width, double spacing, int fromLevel, double tipFraction,
    double flutter, boolean deciduous, int atlasColumns, int atlasRows, int springTile, int summerTile, int autumnTile,
    int winterTile) {

    /** Card layouts. */
    public enum Kind {
        /** Two cards crossed along each twig segment, a textured spray of needles: spruce, fir. */
        NEEDLE_SPRAY,
        /** Tufts of two crossed cards at points along twigs: larch's short shoots, pine's needle bundles. */
        NEEDLE_TUFT,
        /** Cards of leaf clusters, each from a twig node outward: broadleaf trees and shrubs. */
        LEAF_CLUSTER
    }

    /** The tile shown in {@code season}. */
    public int tile(Season season) {
        return switch (season) {
            case SPRING -> springTile;
            case SUMMER -> summerTile;
            case AUTUMN -> autumnTile;
            case WINTER -> winterTile;
        };
    }

    /** Whether cards are shown in {@code season}. */
    public boolean shown(Season season) {
        return !(deciduous && season == Season.WINTER);
    }
}
