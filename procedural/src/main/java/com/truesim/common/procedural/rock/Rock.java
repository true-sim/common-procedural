package com.truesim.common.procedural.rock;

import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.List;

/**
 * A generated rock: closed meshes from full detail down, one submesh each. Mesh space is metres, +y up, the ground at
 * y 0 (the rock's flat cut a little below it). Its wind data is all zero; UV is a planar projection from above in
 * metres (rock materials usually map triplanar).
 *
 * @param params what it is
 * @param seed what made it
 * @param lods the meshes, most detailed first
 */
public record Rock(RockParams params, long seed, List<ProceduralMesh> lods) {

    public Rock {
        lods = List.copyOf(lods);
    }
}
