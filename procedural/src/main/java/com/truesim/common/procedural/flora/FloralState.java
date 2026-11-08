package com.truesim.common.procedural.flora;

/**
 * What one flower, or the fruit it became, looks like on a day ({@link Phenology#state}): mutable, reused.
 */
public final class FloralState {

    /** The stages of a flower's year. */
    public enum Stage {
        /** Nothing shows: before its bud, after its petals fell without fruit, after its fruit fell. */
        NONE,
        /** A closed bud. */
        BUD,
        /** An open (or opening) flower. */
        FLOWER,
        /** A fruit. */
        FRUIT
    }

    private Stage stage = Stage.NONE;
    private double open;
    private double size;
    private double age;
    private double ripeness;

    /** Nothing shows. */
    public FloralState none() {
        return set(Stage.NONE, 0, 0, 0, 0);
    }

    /** Sets every value (for meshing a state made elsewhere). */
    public FloralState set(Stage stage, double open, double size, double age, double ripeness) {
        this.stage = stage;
        this.open = open;
        this.size = size;
        this.age = age;
        this.ripeness = ripeness;
        return this;
    }

    public Stage stage() {
        return stage;
    }

    /** 0 closed to 1 open (flowers). */
    public double open() {
        return open;
    }

    /** The size as a fraction of the full size: buds and young fruits are smaller. */
    public double size() {
        return size;
    }

    /** The petals' age: 0 a young bud, 0.5 just open, 1 fading (the petal ramp's u). */
    public double age() {
        return age;
    }

    /** 0 green to 1 ripe (fruits). */
    public double ripeness() {
        return ripeness;
    }

    /** Whether anything shows. */
    public boolean shown() {
        return stage != Stage.NONE;
    }
}
