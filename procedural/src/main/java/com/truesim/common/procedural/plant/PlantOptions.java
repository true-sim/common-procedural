package com.truesim.common.procedural.plant;

import com.truesim.common.procedural.tree.Season;
import java.util.List;

/**
 * What to generate of a plant besides its species and seed: the day, a snow load, the levels of detail.
 *
 * @param day the day of the year (0 is 1 January): flowers, seed heads, the leaves' color
 * @param snowLoad 0 to 1: how far snow presses the plant down
 * @param lods the mesh levels, most detailed first
 */
public record PlantOptions(double day, double snowLoad, List<PlantLod> lods) {

    public PlantOptions {
        lods = List.copyOf(lods);
        if (lods.isEmpty() || !(day >= 0 && day < 366) || snowLoad < 0 || snowLoad > 1) {
            throw new IllegalArgumentException("Bad plant options: day " + day + ", snow " + snowLoad + ", "
                + lods.size() + " levels");
        }
    }

    /** Summer's day, no snow, three levels. */
    public static final PlantOptions DEFAULTS = new PlantOptions(Season.SUMMER.day(), 0,
        List.of(PlantLod.FULL, PlantLod.MEDIUM, PlantLod.LOW));

    public PlantOptions withDay(double value) {
        return new PlantOptions(value, snowLoad, lods);
    }

    /** On the season's day. */
    public PlantOptions withSeason(Season value) {
        return new PlantOptions(value.day(), snowLoad, lods);
    }

    public PlantOptions withSnowLoad(double value) {
        return new PlantOptions(day, value, lods);
    }

    public PlantOptions withLods(List<PlantLod> value) {
        return new PlantOptions(day, snowLoad, value);
    }
}
