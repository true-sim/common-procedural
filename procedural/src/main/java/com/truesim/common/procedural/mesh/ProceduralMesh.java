package com.truesim.common.procedural.mesh;

import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Aabb;
import com.truesim.common.math.layout.ElementBuffer;
import java.util.Arrays;

/**
 * A generated mesh: {@link ProceduralVertex} vertices in an {@link ElementBuffer} on the heap, triangles as three
 * indices each (counter-clockwise from the front), and submeshes as ranges of the indices, one per material slot
 * (a tree's bark is submesh 0, its leaves submesh 1). Vertices are in the order the triangles first use them and
 * triangles are emitted part by part (ring by ring along a branch, card by card), so neighbouring triangles share
 * vertices: the order meshlet builders and vertex caches want.
 *
 * @param vertices the vertices
 * @param indices the triangles' indices, {@code 3 * triangleCount()} of them
 * @param submeshes where each submesh's indices start, then the end: {@code submeshCount() + 1} offsets
 * @param bounds the vertices' box
 * @param error how far this mesh may differ from the most detailed one, in metres (0 for full detail): what an LOD
 *     selection projects to pixels
 */
public record ProceduralMesh(ElementBuffer vertices, int[] indices, int[] submeshes, Aabb bounds, double error) {

    public ProceduralMesh {
        if (submeshes.length < 2 || submeshes[0] != 0 || submeshes[submeshes.length - 1] != indices.length) {
            throw new IllegalArgumentException("Submeshes must cover the indices: " + Arrays.toString(submeshes));
        }
    }

    public int vertexCount() {
        return vertices.size();
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    public int submeshCount() {
        return submeshes.length - 1;
    }

    /** The first index of submesh {@code s}. */
    public int submeshStart(int s) {
        return submeshes[s];
    }

    /** The end (exclusive) of submesh {@code s}'s indices. */
    public int submeshEnd(int s) {
        return submeshes[s + 1];
    }

    /** Submesh {@code s}'s indices, a copy: e.g. for a mesh builder that takes one array per material slot. */
    public int[] submeshIndices(int s) {
        return Arrays.copyOfRange(indices, submeshes[s], submeshes[s + 1]);
    }

    /** The vertices' positions as float triples, a copy. */
    public float[] positions() {
        float[] out = new float[3 * vertexCount()];
        var position = vertices.attribute(ProceduralVertex.POSITION);
        for (int i = 0; i < vertexCount(); i++) {
            out[3 * i] = (float) position.x(i);
            out[3 * i + 1] = (float) position.y(i);
            out[3 * i + 2] = (float) position.z(i);
        }
        return out;
    }

    /** The bounds' center. */
    public Vec3 center() {
        return bounds.min().add(bounds.max()).scale(0.5);
    }
}
