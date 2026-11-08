package com.truesim.common.procedural.mesh;

import com.truesim.common.math.layout.ElementLayout;
import com.truesim.common.math.layout.Field;
import com.truesim.common.math.layout.Format;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;

/**
 * The vertex of every generated mesh, 32 bytes interleaved in common-math's formats. Its attributes after the position
 * are those of a scene mesh's attribute stream (true-sim/scene {@code VertexAttribute}), in the same encodings, so a
 * mesh maps onto a scene {@code MeshBuilder} attribute by attribute:
 *
 * <table>
 *   <caption>Attributes</caption>
 *   <tr><th>Field</th><th>Format</th><th>Scene attribute</th><th>Contents</th></tr>
 *   <tr><td>{@link #POSITION}</td><td>FLOAT3</td><td>positions</td><td>mesh space, metres, +y up, the base at the
 *       origin</td></tr>
 *   <tr><td>{@link #NORMAL}</td><td>OCT16</td><td>NORMAL</td><td>unit normal</td></tr>
 *   <tr><td>{@link #TANGENT}</td><td>OCT16</td><td>TANGENT</td><td>unit tangent along +u; the bitangent
 *       {@code cross(normal, tangent)} points along +v (handedness always +1)</td></tr>
 *   <tr><td>{@link #UV}</td><td>HALF2</td><td>UV0</td><td>texture coordinates: bark tiles, or a leaf atlas
 *       cell</td></tr>
 *   <tr><td>{@link #WIND_PIVOT}</td><td>HALF2</td><td>UV1</td><td>wind: the pivot's height and the swaying part's
 *       reach (see below)</td></tr>
 *   <tr><td>{@link #WIND}</td><td>UNORM8_4</td><td>COLOR</td><td>wind: trunk and branch weights, the leaf weight and
 *       phase packed, and 1</td></tr>
 * </table>
 *
 * <p><b>Wind</b> follows the usual hierarchy of vegetation animation (trunk bending, branch swaying, leaf flutter;
 * Sousa, GPU Gems 3, ch. 16), all in mesh space:
 * <ul>
 *   <li>{@code WIND.x} (color red) trunk weight in [0, 1]: how much the vertex follows the bending of its trunk (or of
 *       its stem, for shrubs): 0 at the ground, 1 at the top, rising as {@code (h / height)^1.5}; every vertex of a
 *       branch takes the value at the branch's pivot, so branches ride the trunk rigidly.</li>
 *   <li>{@code WIND.y} (green) branch weight in [0, 1]: how far the vertex swings with its main branch (the trunk's
 *       child it belongs to, sub-branches and leaves included): 0 at the pivot, rising as {@code (s / reach)^1.5}
 *       along the path {@code s} from the pivot, 1 at the reach and beyond; 0 on the trunk.</li>
 *   <li>{@code WIND.z} (blue) the leaf weight and the phase, a nibble each of the byte
 *       {@code b = 16 * leaf15 + phase16} ({@link #packLeafAndPhase}): {@code leaf = (b >> 4) / 15} in [0, 1], the
 *       flutter, 0 on wood, on a leaf card 0 where it is attached and the species' flutter at its far edge;
 *       {@code phase = (b & 15) / 16} in [0, 1), the main branch's, so a branch sways as one (0 on the trunk).</li>
 *   <li>{@code WIND.w} (alpha) always 1: alpha-masked materials multiply their mask by the vertex color's alpha, so
 *       it must not carry data.</li>
 *   <li>{@code WIND_PIVOT.x} (UV1 u) the pivot's height: the main branch sways about {@code (0, WIND_PIVOT.x, 0)},
 *       where it leaves the trunk (trunks are near the mesh's y axis: their lean is kept within a few percent of the
 *       height, and broadleaf trunks end at the crown's base, so the pivot is that close); on the trunk 0, the trunk
 *       bending about its base.</li>
 *   <li>{@code WIND_PIVOT.y} (UV1 v) the reach: the main branch's length (the trunk's height on the trunk), in metres;
 *       a shader can make long branches sway slower, as their natural frequency drops with length.</li>
 * </ul>
 * The branch's direction at a vertex is {@code normalize(position - pivot)}: rotating the vertex about the pivot
 * toward the wind by an angle proportional to the branch weight keeps branch lengths.
 */
public final class ProceduralVertex {

    private static final ElementLayout.Builder BUILDER = ElementLayout.builder();

    /** Position in mesh space: FLOAT3. */
    public static final Field<Vec3Attribute> POSITION = BUILDER.vec3("position", Format.FLOAT3);
    /** Unit normal: OCT16. */
    public static final Field<Vec3Attribute> NORMAL = BUILDER.vec3("normal", Format.OCT16);
    /** Unit tangent along +u: OCT16. */
    public static final Field<Vec3Attribute> TANGENT = BUILDER.vec3("tangent", Format.OCT16);
    /** Texture coordinates: HALF2. */
    public static final Field<Vec2Attribute> UV = BUILDER.vec2("uv", Format.HALF2);
    /** The wind's pivot height and reach: HALF2. */
    public static final Field<Vec2Attribute> WIND_PIVOT = BUILDER.vec2("windPivot", Format.HALF2);
    /** The wind's trunk and branch weights, leaf weight and phase packed, and 1: UNORM8_4. */
    public static final Field<Vec4Attribute> WIND = BUILDER.vec4("wind", Format.UNORM8_4);

    /** The layout: 32 bytes per vertex. */
    public static final ElementLayout LAYOUT = BUILDER.build();

    private ProceduralVertex() {
    }

    /**
     * The leaf weight (0 to 1, in 16 steps) and phase (0 to 1, in 16 steps) as {@code WIND.z}: the byte
     * {@code 16 * round(15 * leaf) + floor(16 * phase)}, divided by 255 as UNORM8 stores it exactly.
     */
    public static double packLeafAndPhase(double leaf, double phase) {
        int l = (int) Math.round(Math.clamp(leaf, 0, 1) * 15);
        int p = Math.min((int) (Math.clamp(phase, 0, 1) * 16), 15);
        return (16 * l + p) / 255.0;
    }

    /** The leaf weight packed in {@code WIND.z}. */
    public static double leaf(double packed) {
        return (Math.round(packed * 255) >> 4) / 15.0;
    }

    /** The phase packed in {@code WIND.z}. */
    public static double phase(double packed) {
        return (Math.round(packed * 255) & 15) / 16.0;
    }
}
