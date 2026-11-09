package com.truesim.common.procedural.fence;

import com.truesim.common.math.Vec3;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.List;

/**
 * A generated fence along a line over the ground: its meshes from full detail down, and its pieces.
 *
 * <p><b>Mesh space</b> is the ground's frame moved by {@link #origin()} (the line's first point on the ground), so a
 * fence far from the frame's origin keeps float precision: place the mesh at the origin, unturned, unscaled. Three
 * submeshes, some of them empty: {@link #WOOD}, {@link #STONE}, {@link #METAL}.
 *
 * <p><b>Vertex data</b> in {@code ProceduralVertex}'s layout, with fences' meanings (they do not sway):
 * <ul>
 *   <li>{@code UV} (scene {@code UV0}): metres, u around a piece (across a board), v along it from a random offset
 *       below 1; restarting on every piece, so half floats keep them to a few millimetres. Stones: x and y of the
 *       stone's own frame. A material maps a wood or stone texture one tile per metre.</li>
 *   <li>{@code WIND} (scene {@code COLOR}): the albedo, linear r, g, b, alpha 1: the material's weathering, its
 *       variation per piece, lichen and moss baked per vertex. A metallic-roughness material with a grey-scale texture
 *       (mean about 1) draws it as it is.</li>
 *   <li>{@code WIND_PIVOT} (scene {@code UV1}): x the moss, y the lichen at the vertex, 0 to 1, for materials that
 *       add their own detail (roughness, a moss texture).</li>
 * </ul>
 *
 * @param type what it is
 * @param options its materials and wear
 * @param seed what made it
 * @param origin the ground's frame position of mesh space's origin
 * @param length the line's length, metres
 * @param pieces the pieces of level 0, in the ground's frame
 * @param lods the meshes, most detailed first; each mesh's error says how far it may differ from level 0
 */
public record Fence(FenceType type, FenceOptions options, long seed, Vec3 origin, double length,
    List<FencePiece> pieces, List<ProceduralMesh> lods) {

    /** The submesh of wood: posts, rails, pickets, boards, stakes, poles, rods, lashings, gates. */
    public static final int WOOD = 0;
    /** The submesh of stone: walls. */
    public static final int STONE = 1;
    /** The submesh of metal: wire. */
    public static final int METAL = 2;

    public Fence {
        pieces = List.copyOf(pieces);
        lods = List.copyOf(lods);
    }

    /** The pieces playing {@code role}. */
    @com.truesim.annotations.AllowAllocation("a list, for tools and tests")
    public List<FencePiece> pieces(FencePiece.Role role) {
        return pieces.stream().filter(p -> p.role() == role).toList();
    }
}
