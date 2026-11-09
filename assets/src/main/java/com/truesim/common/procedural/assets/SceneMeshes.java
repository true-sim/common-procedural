package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.layout.ElementBuffer;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import com.truesim.scene.assets.mesh.MeshData;
import com.truesim.scene.assets.mesh.VertexAttribute;
import com.truesim.scene.assets.mesh.VertexFormat;
import com.truesim.scene.meshbuild.MeshBuilder;

/**
 * Generated meshes as scene meshes: every {@link ProceduralVertex} attribute onto the scene attribute of the same
 * encoding (normal, tangent with handedness +1, UV0, UV1 = the wind's pivot, COLOR = the wind's weights and phase),
 * built into meshlets by scene-meshbuild. Empty submeshes (a bare tree's leaves) are left out.
 */
public final class SceneMeshes {

    /** The scene vertex format of generated meshes with wind (trees): 20 bytes of attributes per vertex. */
    public static final VertexFormat FORMAT = VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.TANGENT,
        VertexAttribute.UV0, VertexAttribute.UV1, VertexAttribute.COLOR);
    /**
     * Without wind (rocks): 12 bytes. Materials that tint by the vertex color (metallic-roughness does) would take the
     * wind data for a color, so meshes that do not sway leave it out.
     */
    public static final VertexFormat STILL = VertexFormat.of(VertexAttribute.NORMAL, VertexAttribute.TANGENT,
        VertexAttribute.UV0);

    private SceneMeshes() {
    }

    /** {@code mesh} as a flat (single level) scene mesh with its wind data ({@link #FORMAT}). */
    public static MeshData build(ProceduralMesh mesh) {
        return build(mesh, true);
    }

    /** {@code mesh} as a flat scene mesh, with its wind data ({@link #FORMAT}) or without ({@link #STILL}). */
    public static MeshData build(ProceduralMesh mesh, boolean wind) {
        return build(mesh, wind, false);
    }

    /**
     * {@code mesh} as a scene mesh with its wind data or without: a cluster hierarchy ({@code hierarchy}: the engine
     * draws each meshlet at the detail its distance needs) or one flat level.
     */
    @AllowAllocation("building meshes, at load time")
    public static MeshData build(ProceduralMesh mesh, boolean wind, boolean hierarchy) {
        int n = mesh.vertexCount();
        ElementBuffer vertices = mesh.vertices();
        Vec3Attribute normal = vertices.attribute(ProceduralVertex.NORMAL);
        Vec3Attribute tangent = vertices.attribute(ProceduralVertex.TANGENT);
        Vec2Attribute uv = vertices.attribute(ProceduralVertex.UV);
        Vec2Attribute pivot = vertices.attribute(ProceduralVertex.WIND_PIVOT);
        Vec4Attribute weights = vertices.attribute(ProceduralVertex.WIND);
        float[] normals = new float[3 * n];
        float[] tangents = new float[4 * n];
        float[] uv0 = new float[2 * n];
        float[] uv1 = new float[2 * n];
        float[] colors = new float[4 * n];
        for (int i = 0; i < n; i++) {
            normals[3 * i] = (float) normal.x(i);
            normals[3 * i + 1] = (float) normal.y(i);
            normals[3 * i + 2] = (float) normal.z(i);
            tangents[4 * i] = (float) tangent.x(i);
            tangents[4 * i + 1] = (float) tangent.y(i);
            tangents[4 * i + 2] = (float) tangent.z(i);
            tangents[4 * i + 3] = 1;
            uv0[2 * i] = (float) uv.x(i);
            uv0[2 * i + 1] = (float) uv.y(i);
            uv1[2 * i] = (float) pivot.x(i);
            uv1[2 * i + 1] = (float) pivot.y(i);
            colors[4 * i] = (float) weights.x(i);
            colors[4 * i + 1] = (float) weights.y(i);
            colors[4 * i + 2] = (float) weights.z(i);
            colors[4 * i + 3] = (float) weights.w(i);
        }
        MeshBuilder builder = new MeshBuilder(wind ? FORMAT : STILL, n).positions(mesh.positions())
            .attribute(VertexAttribute.NORMAL, normals).attribute(VertexAttribute.TANGENT, tangents)
            .attribute(VertexAttribute.UV0, uv0);
        if (wind) {
            builder.attribute(VertexAttribute.UV1, uv1).attribute(VertexAttribute.COLOR, colors);
        }
        for (int s = 0; s < mesh.submeshCount(); s++) {
            if (mesh.submeshEnd(s) > mesh.submeshStart(s)) {
                builder.submesh(mesh.submeshIndices(s));
            }
        }
        return hierarchy ? builder.buildHierarchy() : builder.buildFlat();
    }

    /** Whether submesh {@code s} of {@code mesh} has triangles (kept by {@link #build}). */
    public static boolean kept(ProceduralMesh mesh, int s) {
        return mesh.submeshEnd(s) > mesh.submeshStart(s);
    }
}
