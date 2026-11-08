package com.truesim.common.procedural.flora;

/**
 * How flowers are arranged where they are borne (botany's inflorescences), each flower on a stalk (pedicel) from a
 * common stalk (peduncle). Metres and radians.
 *
 * @param arrangement the arrangement
 * @param minFlowers the fewest flowers (or stalks of a compound head) in one
 * @param maxFlowers the most flowers (or stalks of a compound head) in one
 * @param spread a flat or domed head's radius ({@link Arrangement#CORYMB}, {@link Arrangement#UMBEL}); a raceme's
 *     length; a cluster's cone half-angle in radians
 * @param peduncle the common stalk's length
 * @param pedicel each flower's own stalk
 * @param erect radians the head turns from its shoot's direction toward the sky (corymbs are held up)
 * @param nodding radians each flower turns toward the ground (bells nod)
 * @param fruitDroop radians the head sinks toward the ground under ripe fruit
 * @param florets flowers at the end of each stalk: 1 for a simple head, more for a compound one (elder's and rowan's
 *     heads of small umbels of small flowers), packed around the stalk's end without stalks of their own
 */
public record Inflorescence(Arrangement arrangement, int minFlowers, int maxFlowers, double spread, double peduncle,
    double pedicel, double erect, double nodding, double fruitDroop, int florets) {

    /** Arrangements. */
    public enum Arrangement {
        /** One flower: dog rose, gentian. */
        SOLITARY,
        /** A few flowers on equal stalks from one point, spreading in a cone: apple spurs, alpenrose. */
        CLUSTER,
        /** Stalks from one point to a dome: elder's flat-topped heads. */
        UMBEL,
        /** Stalks from along a short axis, the lower longer, to a flat top: rowan. */
        CORYMB,
        /** Flowers on short stalks along an axis: lingonberry. */
        RACEME
    }

    public Inflorescence {
        if (minFlowers < 1 || maxFlowers < minFlowers || spread < 0 || peduncle < 0 || pedicel < 0 || florets < 1) {
            throw new IllegalArgumentException("Not an inflorescence: " + minFlowers + " to " + maxFlowers
                + " flowers");
        }
    }

    /** One flower at the end of a stalk {@code stalk} metres long. */
    public static Inflorescence solitary(double stalk, double erect, double nodding, double fruitDroop) {
        return new Inflorescence(Arrangement.SOLITARY, 1, 1, 0, 0, stalk, erect, nodding, fruitDroop, 1);
    }
}
