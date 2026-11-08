package com.truesim.common.procedural.flora;

/**
 * A flower's shape, meshed by {@link FloraWriter#flower}: petals around a centre, a fused bell, a composite head of
 * rays around a disc, a globe of florets or a star of bracts; sepals below; open or closed by its state. Metres.
 *
 * @param form the kind of flower
 * @param petals petals ({@link Form#SIMPLE}), lobes ({@link Form#BELL}), rays ({@link Form#COMPOSITE}), bracts
 *     ({@link Form#STAR}), lumps ({@link Form#GLOBE})
 * @param length a petal's or ray's length; a bell's tube; a globe's radius
 * @param width a petal's or ray's widest width; a bell's mouth radius
 * @param tip the petals' tip
 * @param cupping {@link Form#SIMPLE}: 0 flat to 1 an upright cup; {@link Form#BELL}: how far the mouth flares;
 *     {@link Form#COMPOSITE}, {@link Form#STAR}: negative for rays hanging, positive for raised
 * @param waist {@link Form#BELL}: 0 a straight tube or trumpet, toward 1 an urn narrowing at the mouth
 * @param sepals sepals or phyllaries below, 0 for none
 * @param sepalLength their length
 * @param centreRadius the centre's (disc's, stamens') radius
 * @param centreHeight how far the centre domes up
 * @param heads {@link Form#STAR}: small heads in the middle
 */
public record FlowerShape(Form form, int petals, double length, double width, Tip tip, double cupping, double waist,
    int sepals, double sepalLength, double centreRadius, double centreHeight, int heads) {

    /** Kinds of flowers. */
    public enum Form {
        /** Free petals around a centre of stamens: rose, apple, rowan, elder, buttercup, alpenrose. */
        SIMPLE,
        /** A fused corolla, a tube or bell ending in lobes: gentian, blueberry, lingonberry. */
        BELL,
        /** A composite head: rays around a domed disc of florets: arnica, aster. */
        COMPOSITE,
        /** A globe of florets: clover. */
        GLOBE,
        /** A star of woolly bracts around a few small heads: edelweiss. */
        STAR
    }

    /** Petal tips. */
    public enum Tip {
        /** Rounded: apple, buttercup. */
        ROUND,
        /** Pointed: gentian lobes, edelweiss bracts. */
        POINTED,
        /** Heart-shaped, a notch in the middle: dog rose. */
        NOTCHED,
        /** Three small teeth: arnica's rays. */
        TOOTHED
    }

    public FlowerShape {
        if (petals < 0 || length <= 0 || width <= 0 || sepals < 0 || waist < 0 || waist >= 1 || heads < 0
            || centreRadius < 0) {
            throw new IllegalArgumentException("Not a flower: " + petals + " petals of " + length + " by " + width);
        }
    }

    /** How far the flower reaches from its centre, metres. */
    public double reach() {
        return switch (form) {
            case SIMPLE, COMPOSITE, STAR -> centreRadius + length;
            case BELL -> Math.max(length, width);
            case GLOBE -> length;
        };
    }
}
