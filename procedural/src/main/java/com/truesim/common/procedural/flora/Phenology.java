package com.truesim.common.procedural.flora;

/**
 * When a species flowers and fruits, in days of the year (1 January is day 0; days past 365 reach into the next year,
 * for fruits that hang through the winter). Every flower has its own dates within the windows (drawn from its own
 * hashes, so a tree flowers over weeks, not on one day):
 *
 * <ol>
 *   <li><b>Bud</b> from {@code budDays} before it opens: closed, growing.</li>
 *   <li><b>Flower</b> from its opening day, somewhere in {@code bloomStart} to {@code bloomEnd} less its life, for
 *       {@code flowerDays}: opening over a day and a half, then aging.</li>
 *   <li>Then its petals fall. A flower that sets fruit ({@link Fruiting#fruitSet()} of them) becomes a <b>fruit</b>:
 *       growing to full size by {@code ripenStart}, ripening (green to the species' color) over {@code ripenDays} from
 *       its own day in {@code ripenStart} to {@code ripenEnd}, falling on its own day in {@code dropStart} to
 *       {@code dropEnd}.</li>
 * </ol>
 *
 * Flowering dates are typical of the montane belt of the Alps (about 800 to 1,500 m) and shift with altitude by about
 * four days per 100 m, which callers can do by shifting the day they ask for.
 *
 * @param budDays days a closed bud shows before it opens
 * @param bloomStart the first flowers open
 * @param bloomEnd the last flowers fade
 * @param flowerDays days one flower lasts
 * @param ripenStart fruits have their full size, the first begin to color
 * @param ripenEnd the last fruits begin to color
 * @param ripenDays days one fruit takes from green to ripe
 * @param dropStart the first fruits fall
 * @param dropEnd the last fruits fall
 */
public record Phenology(double budDays, double bloomStart, double bloomEnd, double flowerDays, double ripenStart,
    double ripenEnd, double ripenDays, double dropStart, double dropEnd) {

    public Phenology {
        if (!(budDays >= 0 && bloomStart <= bloomEnd && flowerDays > 0 && bloomEnd <= ripenStart
            && ripenStart <= ripenEnd && ripenDays > 0 && ripenStart <= dropStart && dropStart <= dropEnd
            && dropEnd - bloomStart < 2 * 365)) {
            throw new IllegalArgumentException("Not a phenology: buds " + budDays + ", bloom " + bloomStart + " to "
                + bloomEnd + ", ripening " + ripenStart + " to " + ripenEnd + ", drop " + dropStart + " to " + dropEnd);
        }
    }

    /** The day most flowers are open: the middle of flowering. */
    public double peakBloom() {
        return 0.5 * (bloomStart + bloomEnd);
    }

    /** A day most fruits are ripe and still hanging (wrapped into 0 to 365). */
    public double ripe() {
        double day = Math.min(ripenEnd + 0.5 * ripenDays, dropStart + 0.25 * (dropEnd - dropStart));
        return day % 365;
    }

    /**
     * The state on {@code day} of one flower (and its fruit) whose hashes are {@code open}, {@code ripen} and
     * {@code drop} (each in [0, 1)), and which sets fruit when {@code sets}; written into {@code out}. This year's
     * flower wins over last year's fruit where both would show.
     */
    public FloralState state(double day, double open, double ripen, double drop, boolean sets, FloralState out) {
        double d = ((day % 365) + 365) % 365;
        evaluate(d, open, ripen, drop, sets, out);
        if (out.stage() == FloralState.Stage.NONE && dropEnd > 365) {
            evaluate(d + 365, open, ripen, drop, sets, out); // last year's fruit, still hanging
        }
        return out;
    }

    private void evaluate(double d, double open, double ripen, double drop, boolean sets, FloralState out) {
        double opens = bloomStart + open * Math.max(0, bloomEnd - bloomStart - flowerDays);
        double falls = opens + flowerDays;
        if (d < opens - budDays) {
            out.none();
        } else if (d < falls) {
            double opening = smooth(opens - 1, opens + 0.5, d);
            double grown = budDays > 0 ? smooth(opens - budDays, opens, d) : 1;
            double age = d < opens ? 0.5 * grown : 0.5 + 0.5 * (d - opens) / flowerDays;
            out.set(opening > 0 ? FloralState.Stage.FLOWER : FloralState.Stage.BUD, opening, 0.5 + 0.5 * grown, age,
                0);
        } else if (sets && d < dropStart + drop * (dropEnd - dropStart)) {
            double size = 0.25 + 0.75 * smooth(falls, Math.max(ripenStart, falls + 1), d);
            double starts = ripenStart + ripen * (ripenEnd - ripenStart);
            double ripeness = Math.clamp((d - starts) / ripenDays, 0, 1);
            out.set(FloralState.Stage.FRUIT, 0, size, 1, ripeness);
        } else {
            out.none();
        }
    }

    /** 0 below {@code a}, 1 above {@code b}, smooth (Hermite) between. */
    public static double smooth(double a, double b, double x) {
        double t = Math.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
