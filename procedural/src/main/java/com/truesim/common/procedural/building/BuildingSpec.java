package com.truesim.common.procedural.building;

import com.truesim.common.procedural.Seeds;

/**
 * What to build: its kind, footprint, floors, roof and seed. The seed picks everything the spec leaves open (the roof's
 * cover when {@code roof} is null, windows, shutters, flower boxes, which side the woodpile is on, plastered or stone
 * ground floors); the same spec over the same ground always gives the same building, bit for bit.
 *
 * @param kind what it is
 * @param footprint where it stands and how big it is
 * @param floors storeys of walls under the eaves, 1 to 4 (the ground floor's included)
 * @param seed what picks the rest
 * @param roof the roof's cover
 * @param pitch the roof's pitch, degrees, 12 to 50 (the church's tower has its own spire)
 */
public record BuildingSpec(BuildingKind kind, Footprint footprint, int floors, long seed, RoofCover roof,
    double pitch) {

    /** The steepest pitch stones stay on a roof at, degrees: stone-weighted roofs are no steeper. */
    public static final double STONE_ROOF_PITCH = 22;

    public BuildingSpec {
        if (kind == null || footprint == null) {
            throw new IllegalArgumentException("A building needs a kind and a footprint");
        }
        if (floors <= 0) {
            floors = kind.floors();
        }
        if (floors > 4 || (kind == BuildingKind.WOODSHED || kind == BuildingKind.CHURCH) && floors != 1) {
            throw new IllegalArgumentException(kind.id() + ": " + floors + " floors");
        }
        if (roof == null) {
            roof = defaultRoof(kind, seed);
        }
        if (Double.isNaN(pitch)) {
            pitch = roof == RoofCover.STONE_WEIGHTED ? Math.min(kind.pitch(), STONE_ROOF_PITCH) : kind.pitch();
        }
        if (!(pitch >= 12 && pitch <= 50)) {
            throw new IllegalArgumentException("A roof's pitch is 12 to 50 degrees: " + pitch);
        }
    }

    /** A building of {@code kind} with its typical footprint at the origin, its floors, the seed's roof. */
    public static BuildingSpec of(BuildingKind kind, long seed) {
        return of(kind, Footprint.of(kind.width(), kind.depth()), seed);
    }

    /** A building of {@code kind} on {@code footprint}, its kind's floors, the seed's roof and the kind's pitch. */
    public static BuildingSpec of(BuildingKind kind, Footprint footprint, long seed) {
        return new BuildingSpec(kind, footprint, 0, seed, null, Double.NaN);
    }

    /** This on another footprint. */
    public BuildingSpec withFootprint(Footprint newFootprint) {
        return new BuildingSpec(kind, newFootprint, floors, seed, roof, pitch);
    }

    /** This with {@code newFloors} floors. */
    public BuildingSpec withFloors(int newFloors) {
        return new BuildingSpec(kind, footprint, newFloors, seed, roof, pitch);
    }

    /** This with another roof cover (and its pitch, unless set). */
    public BuildingSpec withRoof(RoofCover newRoof) {
        return new BuildingSpec(kind, footprint, floors, seed, newRoof, Double.NaN);
    }

    /** This with another pitch, degrees. */
    public BuildingSpec withPitch(double newPitch) {
        return new BuildingSpec(kind, footprint, floors, seed, roof, newPitch);
    }

    /** This with another seed (the roof and pitch kept). */
    public BuildingSpec withSeed(long newSeed) {
        return new BuildingSpec(kind, footprint, floors, newSeed, roof, pitch);
    }

    /** The cover a kind's roof has by default for a seed. */
    static RoofCover defaultRoof(BuildingKind kind, long seed) {
        double r = (Seeds.derive(seed, 77) >>> 11) * 0x1.0p-53;
        return switch (kind) {
            case CHURCH -> r < 0.5 ? RoofCover.SHINGLES : RoofCover.SLATES;
            case HAY_BARN, WOODSHED -> r < 0.55 ? RoofCover.SHINGLES : RoofCover.STONE_WEIGHTED;
            case FARMHOUSE -> r < 0.5 ? RoofCover.SHINGLES : r < 0.75 ? RoofCover.STONE_WEIGHTED : RoofCover.SLATES;
            default -> r < 0.45 ? RoofCover.SHINGLES : r < 0.7 ? RoofCover.SLATES : RoofCover.STONE_WEIGHTED;
        };
    }
}
