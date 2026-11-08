package com.truesim.common.procedural.plant;

import com.truesim.common.procedural.flora.FloraSummary;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.List;

/**
 * A generated small plant or clump: its meshes from full detail down and a billboard. Mesh space is metres, +y up, the
 * clump's foot at the origin. Both submeshes take their color from {@link #palette()} by texture coordinates; the wind
 * data sways every stem, leaf and blade about the foot (the format's pivot at height 0, reach the plant's height, no
 * trunk weight) and flutters leaves and petals.
 *
 * @param species what it is
 * @param seed what made it
 * @param lods the meshes of {@link PlantOptions#lods()}, most detailed first; submesh 0 stems, leaves and blades,
 *     1 flowers and seed heads
 * @param billboard three crossed quads through the foot, each mapping its whole UV square to a capture from the side
 * @param height its height, metres
 * @param radius its horizontal radius from the y axis, metres
 * @param flora the flowers and seed heads it shows on its day
 * @param palette its colors: the base color texture of both submeshes
 */
public record Plant(PlantSpecies species, long seed, List<ProceduralMesh> lods, ProceduralMesh billboard,
    double height, double radius, FloraSummary flora, Palette palette) {

    public Plant {
        lods = List.copyOf(lods);
    }

    /** The stems, leaves and blades. */
    public static final int FOLIAGE = 0;
    /** The flowers and seed heads. */
    public static final int FLOWERS = 1;
}
