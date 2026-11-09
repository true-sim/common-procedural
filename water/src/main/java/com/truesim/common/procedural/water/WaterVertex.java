package com.truesim.common.procedural.water;

import com.truesim.common.procedural.mesh.ProceduralVertex;

/**
 * What the vertices of water meshes carry, in {@link ProceduralVertex}'s layout (so they become scene meshes with
 * {@code SceneMeshes}' wind format: UV0, UV1 and COLOR) with water's meanings:
 *
 * <table>
 *   <caption>Attributes</caption>
 *   <tr><th>Field</th><th>Scene attribute</th><th>Water</th></tr>
 *   <tr><td>POSITION</td><td>positions</td><td>metres from the mesh's origin (a chunk's corner, a fall's lip)</td></tr>
 *   <tr><td>NORMAL</td><td>NORMAL</td><td>the surface's normal (a sheet's faces away from the cliff)</td></tr>
 *   <tr><td>TANGENT</td><td>TANGENT</td><td>the direction of flow, a unit vector (down a falling sheet)</td></tr>
 *   <tr><td>UV</td><td>UV0</td><td>x: the speed, metres a second ({@link #SPEED}); y: across the flow, -1 at the left
 *       bank to 1 at the right facing downstream ({@link #ACROSS})</td></tr>
 *   <tr><td>WIND_PIVOT</td><td>UV1</td><td>x: the depth, metres (a sheet's or jet's thickness) ({@link #DEPTH}); y: on
 *       falls the metres fallen over the break-up length (0 at the lip, 1 where the sheet breaks up), 0 on channels
 *       ({@link #BREAKUP})</td></tr>
 *   <tr><td>WIND</td><td>COLOR</td><td>r: foam, 0 to 1, from the Froude number, emergent stones, plunge pools and
 *       breaking sheets ({@link #FOAM}); g: the shore, 0 mid-channel to 1 at the water's edge ({@link #SHORE});
 *       b: atomisation, the fraction of a fall's water already spray ({@link #SPRAY}); a: 1</td></tr>
 * </table>
 *
 * A flow-mapped material reads the velocity as {@code TANGENT * UV0.x}; depth absorption reads {@code UV1.x}.
 */
public final class WaterVertex {

    /** UV0.x. */
    public static final String SPEED = "uv0.x";
    /** UV0.y. */
    public static final String ACROSS = "uv0.y";
    /** UV1.x. */
    public static final String DEPTH = "uv1.x";
    /** UV1.y. */
    public static final String BREAKUP = "uv1.y";
    /** COLOR.r. */
    public static final String FOAM = "color.r";
    /** COLOR.g. */
    public static final String SHORE = "color.g";
    /** COLOR.b. */
    public static final String SPRAY = "color.b";

    private WaterVertex() {
    }
}
