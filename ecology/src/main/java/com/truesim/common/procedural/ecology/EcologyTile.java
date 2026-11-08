package com.truesim.common.procedural.ecology;

import java.util.List;

/**
 * One tile (or any world-aligned region) of the ecology: fields sampled every {@code cell} metres from its corner
 * ({@code samples} a side, its edges included, so neighbouring tiles share their edge samples exactly), and the plant
 * instances standing in it (x and z in [x0, x0 + width), [z0, z0 + depth): each plant belongs to exactly one tile).
 *
 * <p>Fields, each {@code samples * samples} floats, row by row from the corner (index {@code j * samples + i} at
 * {@code (x0 + i * cell, z0 + j * cell)}):
 * <ul>
 *   <li>per species ({@link #abundance}, {@link #size}, {@link #suitability}): the share of the ground it covers in its
 *       layer (0 to 1; densities for grass fields), its plants' size relative to a grown plant, and how suited the
 *       site is to it alone;</li>
 *   <li>per {@link GroundCover} class ({@link #cover}): fractions summing to 1, for the terrain's material;</li>
 *   <li>per {@link Field}: the conditions (altitude, temperature, moisture, snow, insolation, exposure, wetness,
 *       height above drainage, water, wetland, riparian, meadow, canopy cover, slope, wet soil) and the belt.</li>
 * </ul>
 */
public final class EcologyTile {

    /** The conditions and summaries a tile keeps as fields. */
    public enum Field {
        /** Altitude, metres. */
        ALTITUDE,
        /** The growing season's mean temperature, degrees Celsius. */
        TEMPERATURE,
        /** Moisture on Ellenberg's scale. */
        MOISTURE,
        /** Days of snow cover a year. */
        SNOW_DAYS,
        /** Relative insolation. */
        INSOLATION,
        /** Wind exposure, 0 to 1. */
        EXPOSURE,
        /** Topographic wetness index. */
        WETNESS,
        /** Height above the nearest drainage, metres. */
        HAND,
        /** Open water, 0 to 1: a hint for the water renderer and the terrain's material. */
        WATER,
        /** Wetland, 0 to 1. */
        WETLAND,
        /** Riparian ground, 0 to 1. */
        RIPARIAN,
        /** Land kept open as meadow, 0 to 1. */
        MEADOW,
        /** The canopy's cover, 0 to 1. */
        CANOPY,
        /** Slope, degrees. */
        SLOPE,
        /** Wet soil, 0 to 1: a hint for the terrain's material (darker, glossier ground). */
        WET_SOIL,
        /** The belt's ordinal ({@link Belt}). */
        BELT;

        static final Field[] ALL = values();
    }

    private final double x0;
    private final double z0;
    private final double cell;
    private final int samples;
    private final List<Species> species;
    final float[][] abundance;
    final float[][] size;
    final float[][] suitability;
    final float[][] cover;
    final float[][] fields;
    final Instances instances;

    EcologyTile(double x0, double z0, double cell, int samples, List<Species> species, Instances instances) {
        this.x0 = x0;
        this.z0 = z0;
        this.cell = cell;
        this.samples = samples;
        this.species = species;
        this.instances = instances;
        int n = samples * samples;
        abundance = new float[species.size()][n];
        size = new float[species.size()][n];
        suitability = new float[species.size()][n];
        cover = new float[GroundCover.ALL.length][n];
        fields = new float[Field.ALL.length][n];
    }

    /** The tile's corner's x, metres. */
    public double x0() {
        return x0;
    }

    /** The tile's corner's z, metres. */
    public double z0() {
        return z0;
    }

    /** The fields' spacing, metres. */
    public double cell() {
        return cell;
    }

    /** Samples a side. */
    public int samples() {
        return samples;
    }

    /** The species, in the ecology's order. */
    public List<Species> species() {
        return species;
    }

    /** Species {@code s}'s abundance: the share of its layer's ground it covers, 0 to 1 (read only). */
    public float[] abundance(int s) {
        return abundance[s];
    }

    /** Species {@code s}'s plants' size relative to a grown plant (read only). */
    public float[] size(int s) {
        return size[s];
    }

    /** How suited the sites are to species {@code s} alone, 0 to 1 (read only). */
    public float[] suitability(int s) {
        return suitability[s];
    }

    /** The fraction of ground-cover class {@code c} (read only). */
    public float[] cover(GroundCover c) {
        return cover[c.ordinal()];
    }

    /** Field {@code f} (read only). */
    public float[] field(Field f) {
        return fields[f.ordinal()];
    }

    /** The belt at sample ({@code i}, {@code j}). */
    public Belt belt(int i, int j) {
        return Belt.ALL[(int) fields[Field.BELT.ordinal()][j * samples + i]];
    }

    /** The plants standing in the tile. */
    public Instances instances() {
        return instances;
    }

    /** The index of species {@code id}, or -1. */
    public int index(String id) {
        for (int s = 0; s < species.size(); s++) {
            if (species.get(s).id().equals(id)) {
                return s;
            }
        }
        return -1;
    }
}
