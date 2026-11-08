package com.truesim.common.procedural.flora;

/**
 * What flowers and fruits a generated tree or plant shows on its day, counted on its most detailed level: for logs,
 * tests, and for far representations (billboards, impostors) to tint by.
 *
 * @param buds closed buds
 * @param flowers open flowers
 * @param fruits fruits or cones
 * @param albedo their mean albedo, weighted by area (black when there are none)
 * @param area their summed cross sections, square metres: about the area they cover seen from the side
 */
public record FloraSummary(int buds, int flowers, int fruits, Albedo albedo, double area) {

    /** Nothing shown. */
    public static final FloraSummary NONE = new FloraSummary(0, 0, 0, new Albedo(0, 0, 0), 0);

    /** Everything shown: buds, flowers and fruits. */
    public int shown() {
        return buds + flowers + fruits;
    }

    /** Sums attachments as they are written: counts, and albedos weighted by area. Reused. */
    public static final class Builder {

        private int buds;
        private int flowers;
        private int fruits;
        private double r;
        private double g;
        private double b;
        private double area;

        public Builder clear() {
            buds = 0;
            flowers = 0;
            fruits = 0;
            r = 0;
            g = 0;
            b = 0;
            area = 0;
            return this;
        }

        /** One attachment in {@code stage}, of {@code albedo} over {@code area} square metres. */
        public void add(FloralState.Stage stage, Albedo albedo, double area) {
            switch (stage) {
                case BUD -> buds++;
                case FLOWER -> flowers++;
                case FRUIT -> fruits++;
                case NONE -> {
                    return;
                }
            }
            r += albedo.r() * area;
            g += albedo.g() * area;
            b += albedo.b() * area;
            this.area += area;
        }

        public FloraSummary build() {
            if (area <= 0) {
                return new FloraSummary(buds, flowers, fruits, NONE.albedo(), 0);
            }
            return new FloraSummary(buds, flowers, fruits, new Albedo(Math.min(r / area, 1), Math.min(g / area, 1),
                Math.min(b / area, 1)), area);
        }
    }
}
