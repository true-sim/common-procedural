package com.truesim.common.procedural.building;

import com.truesim.common.math.Vec3;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.List;

/**
 * A generated building: its meshes from full detail down and its fittings, in its own frame (x across, z along, +z its
 * front, y up from its ground floor), standing at {@link #floor()} on its footprint.
 *
 * <p>Each mesh has a submesh per {@link BuildingSlot} in order (empty where unused). Every part is a closed solid
 * (walls with their openings cut through, beams, boards, panes, stones); parts overlap rather than join, as a tree's
 * branches do. Levels: 0 everything (rooms, carving, shingle stones, flowers, logs, mullions); 1 without the small
 * carving, sash bars, flowers and single logs; 2 closed walls with dark panes ({@link BuildingSlot#WINDOW}), no rooms,
 * plain balconies; 3 the volumes (walls, roof, chimney, tower). Their {@code error} is how far each may differ from
 * level 0, metres. There is no wind: the vertex wind data is zero.
 *
 * @param spec what was built
 * @param floor the world height of the ground floor: the frame's y = 0
 * @param lods the meshes, most detailed first
 * @param fittings anchors, rooms, openings
 */
public record Building(BuildingSpec spec, double floor, List<ProceduralMesh> lods, Fittings fittings) {

    public Building {
        lods = List.copyOf(lods);
    }

    /** The world position of {@code local}, a point in the building's frame. */
    public Vec3 world(Vec3 local) {
        Footprint f = spec.footprint();
        return new Vec3(f.worldX(local.x(), local.z()), floor + local.y(), f.worldZ(local.x(), local.z()));
    }

    /** The world yaw of a local yaw (an anchor's). */
    public double worldYaw(double localYaw) {
        return spec.footprint().yaw() + localYaw;
    }
}
