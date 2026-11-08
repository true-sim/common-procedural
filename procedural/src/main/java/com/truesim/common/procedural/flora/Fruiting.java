package com.truesim.common.procedural.flora;

/**
 * What a woody species bears and where: flowers in their inflorescences, the fruits they set, cones; when (its
 * phenology) and in which colors (its palette). Placed on the generator's twigs by its rules: at twig tips or along
 * the outer part of shoots, so dense, in the upper crown only if asked.
 *
 * @param placement where on the twigs
 * @param fromLevel the coarsest branch level bearing them
 * @param density {@link Placement#TIPS}: the chance a twig tip bears one; {@link Placement#ALONG}: how many per
 *     metre of the outer {@code tipFraction} of a shoot
 * @param tipFraction {@link Placement#ALONG}: the outer part of each shoot bearing them, as a fraction of its length
 * @param minHeight only above this fraction of the tree's height (spruce and fir bear cones high in the crown)
 * @param inflorescence how the flowers are arranged
 * @param flower the flowers' shape; {@code null} for cones (conifers bear no showy flowers: their young cones show)
 * @param fruit the fruits' (or cones') shape
 * @param phenology when
 * @param fruitSet the fraction of flowers that become fruits
 * @param weightDroop how far a full crop sinks branch tips, as a fraction of their length (apple boughs bow), as
 *     snow does
 * @param palette the colors
 */
public record Fruiting(Placement placement, int fromLevel, double density, double tipFraction, double minHeight,
    Inflorescence inflorescence, FlowerShape flower, FruitShape fruit, Phenology phenology, double fruitSet,
    double weightDroop, Palette palette) {

    /** Where on the twigs. */
    public enum Placement {
        /** At twig tips: corymbs, umbels, solitary roses, cones of spruce. */
        TIPS,
        /** Along the outer part of shoots: apple spurs, blueberry flowers, larch and fir cones. */
        ALONG
    }

    public Fruiting {
        if (density < 0 || tipFraction < 0 || tipFraction > 1 || fruitSet < 0 || fruitSet > 1 || weightDroop < 0
            || minHeight < 0 || minHeight > 1) {
            throw new IllegalArgumentException("Not a fruiting: density " + density + ", fruit set " + fruitSet);
        }
    }

    /** Whether it bears cones (no flowers). */
    public boolean cones() {
        return flower == null;
    }
}
