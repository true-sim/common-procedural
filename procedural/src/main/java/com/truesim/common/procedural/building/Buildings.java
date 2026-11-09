package com.truesim.common.procedural.building;

/**
 * Procedural Alpine buildings, the direct way: a spec (kind, footprint, floors, seed) and the ground in, a
 * {@link Building} out (its meshes, the height of its floor, its fittings), deterministic and engine-agnostic.
 *
 * <pre>{@code
 * Footprint plot = new Footprint(120, -40, 0.3, 9.5, 11);              // world x, z, yaw; width, depth
 * Building chalet = Buildings.generate(BuildingSpec.of(BuildingKind.CHALET, plot, 7), terrain::height);
 * ProceduralMesh near = chalet.lods().getFirst();                      // a submesh per BuildingSlot
 * Anchor fire = chalet.fittings().first(Anchor.Kind.FIRE);             // in the building's frame
 * Vec3 at = chalet.world(fire.position());                              // in the world
 * Building plan = Buildings.layout(spec, terrain::height);             // fittings and floor only, no meshes
 * }</pre>
 */
public final class Buildings {

    private static final ThreadLocal<BuildingGenerator> GENERATORS = ThreadLocal.withInitial(BuildingGenerator::new);

    private Buildings() {
    }

    /** The building {@code spec} describes on {@code ground}, with the default options. */
    public static Building generate(BuildingSpec spec, GroundHeight ground) {
        return generate(spec, ground, BuildingOptions.DEFAULTS);
    }

    /** The building {@code spec} describes on {@code ground}, made as {@code options} say. */
    public static Building generate(BuildingSpec spec, GroundHeight ground, BuildingOptions options) {
        return GENERATORS.get().generate(spec, ground, options);
    }

    /** The building on level ground at height 0. */
    public static Building generate(BuildingSpec spec) {
        return generate(spec, GroundHeight.flat(0));
    }

    /** The building's layout (floor height and fittings, no meshes), with the default options. */
    public static Building layout(BuildingSpec spec, GroundHeight ground) {
        return BuildingGenerator.layout(spec, ground, BuildingOptions.DEFAULTS);
    }

    /** The building's layout (floor height and fittings, no meshes). */
    public static Building layout(BuildingSpec spec, GroundHeight ground, BuildingOptions options) {
        return BuildingGenerator.layout(spec, ground, options);
    }
}
