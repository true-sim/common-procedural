package com.truesim.common.procedural.tree;

import com.truesim.common.procedural.flora.FloraSummary;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.List;

/**
 * A generated tree: its meshes from full detail down, a billboard for the far distance and how to capture its
 * impostor. Mesh space is metres, +y up, the trunk's base at the origin.
 *
 * @param species what it is
 * @param seed what made it
 * @param lods the meshes of {@link TreeOptions#lods()}, most detailed first; submesh 0 bark, 1 leaves, 2 flowers,
 *     fruits and cones (colored from {@link #palette()})
 * @param billboard three crossed quads through the trunk, each mapping its whole UV square to a capture of the tree
 *     from the side: the last level before an impostor
 * @param impostor how to capture the octahedral impostor
 * @param height the tree's height, metres
 * @param crownRadius the crown's horizontal radius from the y axis, metres
 * @param flora the flowers and fruits it shows on its day (on its most detailed level)
 * @param palette the colors of its flowers, fruits and cones: the attachments submesh's base color texture
 */
public record Tree(TreeSpecies species, long seed, List<ProceduralMesh> lods, ProceduralMesh billboard,
    ImpostorHints impostor, double height, double crownRadius, FloraSummary flora, Palette palette) {

    public Tree {
        lods = List.copyOf(lods);
    }

    /** The bark submesh. */
    public static final int BARK = 0;
    /** The leaves submesh. */
    public static final int LEAVES = 1;
    /** The flowers, fruits and cones submesh. */
    public static final int ATTACHMENTS = 2;
}
