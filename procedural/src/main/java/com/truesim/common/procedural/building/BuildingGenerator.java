package com.truesim.common.procedural.building;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates buildings: a {@link Plan} from the spec, the ground and the options (the floor's height, walls, openings,
 * rooms, fittings), then each level of detail built from it ({@link Builder}). The same spec, ground and options always
 * give bit-identical meshes. Keeps its mesh writer from building to building; not thread-safe ({@link Buildings}
 * keeps one per thread).
 */
@AllowAllocation("generating, at load time")
public final class BuildingGenerator {

    /** Version of the generated output: changes whenever the same inputs would give different meshes. */
    public static final int VERSION = 1;

    private final MeshWriter writer = new MeshWriter();
    private final List<int[]> parts = new ArrayList<>();

    /** The building {@code spec} describes, standing on {@code ground}, made as {@code options} say. */
    public Building generate(BuildingSpec spec, GroundHeight ground, BuildingOptions options) {
        Plan plan = new Plan(spec, ground, options);
        List<ProceduralMesh> lods = new ArrayList<>(Builder.LEVELS);
        parts.clear();
        for (int level = 0; level < Builder.LEVELS; level++) {
            Kit kit = new Kit(options);
            new Builder(plan, kit, level).build();
            lods.add(kit.build(writer, Builder.ERRORS[level]));
            parts.add(kit.triangleParts());
        }
        return new Building(spec, plan.floor, lods, plan.toFittings());
    }

    /** The building's layout only: its floor's height and fittings, no meshes (cheap: no geometry is built). */
    public static Building layout(BuildingSpec spec, GroundHeight ground, BuildingOptions options) {
        Plan plan = new Plan(spec, ground, options);
        return new Building(spec, plan.floor, List.of(), plan.toFittings());
    }

    /** The closed part each triangle of the last building's level {@code level} belongs to (for tests). */
    int[] parts(int level) {
        return parts.get(level);
    }
}
