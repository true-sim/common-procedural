package com.truesim.common.procedural.rock;

import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.List;

/**
 * A generated rock: closed meshes from full detail down. Mesh space is metres, +y up, the ground at y 0 (the rock's
 * flat cut a little below it, its buried part gone), the origin at its centre on the ground.
 *
 * <ul>
 *   <li><b>Submeshes</b> (material slots): {@link #ROCK} the weathered surface, {@link #FRESH} fresh fractures (chipped
 *       corners); a level may have no fresh triangles.</li>
 *   <li><b>Vertex color</b> (the wind format's {@code COLOR}): a linear tint of the material's albedo, alpha 1:
 *       weathered stone about 0.86, fresh 1, then moss, lichen and the soil stain as its {@link RockCover} says
 *       (metallic-roughness materials multiply their base color by it). Rocks do not sway: UV1 is zero.</li>
 *   <li><b>UV</b>: a planar projection from above in metres (rock materials usually map triplanar).</li>
 * </ul>
 *
 * @param params what it is
 * @param cover what grows on it, how it faces and how deep it lies
 * @param seed what made it
 * @param lods the meshes, most detailed first
 * @param footprint where it meets the ground
 */
public record Rock(RockParams params, RockCover cover, long seed, List<ProceduralMesh> lods, RockFootprint footprint) {

    /** The weathered surface's submesh. */
    public static final int ROCK = 0;
    /** Fresh fractures' submesh. */
    public static final int FRESH = 1;

    public Rock {
        lods = List.copyOf(lods);
    }
}
