package com.truesim.common.procedural.mesh;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Aabb;
import com.truesim.common.math.layout.ElementBuffer;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import java.util.Arrays;

/**
 * Collects vertices and triangles in growable arrays, then writes them into a {@link ProceduralMesh}. Reused from mesh
 * to mesh ({@link #reset}), so generating many meshes allocates only the meshes themselves once warm. The wind values
 * set by {@link #wind} apply to the vertices added after it. Not thread-safe: one per thread.
 */
public final class MeshWriter {

    private float[] positions = new float[3 * 1024];
    private float[] normals = new float[3 * 1024];
    private float[] tangents = new float[3 * 1024];
    private float[] uvs = new float[2 * 1024];
    private float[] pivots = new float[2 * 1024];
    private float[] winds = new float[4 * 1024];
    private int[] indices = new int[3 * 2048];
    private int[] submeshes = new int[8];
    private int vertexCount;
    private int indexCount;
    private int submeshCount;
    private float trunk;
    private float branch;
    private float leaf;
    private float phase;
    private float pivotHeight;
    private float reach;

    /** Empties the writer, keeping its memory; starts submesh 0. */
    public MeshWriter reset() {
        vertexCount = 0;
        indexCount = 0;
        submeshCount = 0;
        wind(0, 0, 0, 0, 0, 0);
        return this;
    }

    /** Starts the next submesh: the triangles added from now on belong to it. */
    @AllowAllocation("growing, until warm")
    public void nextSubmesh() {
        if (submeshCount + 1 >= submeshes.length) {
            submeshes = Arrays.copyOf(submeshes, submeshes.length * 2);
        }
        submeshes[++submeshCount] = indexCount;
    }

    /** The wind values of the vertices added next (see {@link ProceduralVertex}). */
    public void wind(double trunk, double branch, double leaf, double phase, double pivotHeight, double reach) {
        this.trunk = (float) trunk;
        this.branch = (float) branch;
        this.leaf = (float) leaf;
        this.phase = (float) phase;
        this.pivotHeight = (float) pivotHeight;
        this.reach = (float) reach;
    }

    /** Sets the branch and leaf weights only. */
    public void windWeights(double branch, double leaf) {
        this.branch = (float) branch;
        this.leaf = (float) leaf;
    }

    /** Adds a vertex and returns its index. Normals and tangents are expected unit length. */
    @AllowAllocation("growing, until warm")
    public int vertex(double px, double py, double pz, double nx, double ny, double nz, double tx, double ty,
        double tz, double u, double v) {
        if (vertexCount == positions.length / 3) {
            int capacity = 2 * vertexCount;
            positions = Arrays.copyOf(positions, 3 * capacity);
            normals = Arrays.copyOf(normals, 3 * capacity);
            tangents = Arrays.copyOf(tangents, 3 * capacity);
            uvs = Arrays.copyOf(uvs, 2 * capacity);
            pivots = Arrays.copyOf(pivots, 2 * capacity);
            winds = Arrays.copyOf(winds, 4 * capacity);
        }
        int i = vertexCount;
        positions[3 * i] = (float) px;
        positions[3 * i + 1] = (float) py;
        positions[3 * i + 2] = (float) pz;
        normals[3 * i] = (float) nx;
        normals[3 * i + 1] = (float) ny;
        normals[3 * i + 2] = (float) nz;
        tangents[3 * i] = (float) tx;
        tangents[3 * i + 1] = (float) ty;
        tangents[3 * i + 2] = (float) tz;
        uvs[2 * i] = (float) u;
        uvs[2 * i + 1] = (float) v;
        pivots[2 * i] = pivotHeight;
        pivots[2 * i + 1] = reach;
        winds[4 * i] = trunk;
        winds[4 * i + 1] = branch;
        winds[4 * i + 2] = (float) ProceduralVertex.packLeafAndPhase(leaf, phase);
        winds[4 * i + 3] = 1;
        return vertexCount++;
    }

    /** Adds a vertex at the same position as vertex {@code at} (bit for bit), e.g. a texture seam's copy. */
    public int copyPosition(int at, double nx, double ny, double nz, double tx, double ty, double tz, double u,
        double v) {
        int i = vertex(0, 0, 0, nx, ny, nz, tx, ty, tz, u, v);
        positions[3 * i] = positions[3 * at];
        positions[3 * i + 1] = positions[3 * at + 1];
        positions[3 * i + 2] = positions[3 * at + 2];
        return i;
    }

    /** Adds a triangle, counter-clockwise from the front. */
    @AllowAllocation("growing, until warm")
    public void triangle(int a, int b, int c) {
        if (indexCount + 3 > indices.length) {
            indices = Arrays.copyOf(indices, indices.length * 2);
        }
        indices[indexCount++] = a;
        indices[indexCount++] = b;
        indices[indexCount++] = c;
    }

    /** Adds the quad a, b, c, d (counter-clockwise) as two triangles. */
    public void quad(int a, int b, int c, int d) {
        triangle(a, b, c);
        triangle(a, c, d);
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int triangleCount() {
        return indexCount / 3;
    }

    /** The y of vertex {@code i}. */
    public float y(int i) {
        return positions[3 * i + 1];
    }

    /** The mesh written so far, with {@code error} as its LOD error. */
    @AllowAllocation("the mesh made")
    public ProceduralMesh build(double error) {
        ElementBuffer buffer = ElementBuffer.onHeap(ProceduralVertex.LAYOUT, vertexCount);
        Vec3Attribute position = buffer.attribute(ProceduralVertex.POSITION);
        Vec3Attribute normal = buffer.attribute(ProceduralVertex.NORMAL);
        Vec3Attribute tangent = buffer.attribute(ProceduralVertex.TANGENT);
        Vec2Attribute uv = buffer.attribute(ProceduralVertex.UV);
        Vec2Attribute pivot = buffer.attribute(ProceduralVertex.WIND_PIVOT);
        Vec4Attribute wind = buffer.attribute(ProceduralVertex.WIND);
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < vertexCount; i++) {
            float x = positions[3 * i];
            float y = positions[3 * i + 1];
            float z = positions[3 * i + 2];
            position.set(i, x, y, z);
            normal.set(i, normals[3 * i], normals[3 * i + 1], normals[3 * i + 2]);
            tangent.set(i, tangents[3 * i], tangents[3 * i + 1], tangents[3 * i + 2]);
            uv.set(i, uvs[2 * i], uvs[2 * i + 1]);
            pivot.set(i, pivots[2 * i], pivots[2 * i + 1]);
            wind.set(i, winds[4 * i], winds[4 * i + 1], winds[4 * i + 2], winds[4 * i + 3]);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        int[] starts = new int[submeshCount + 2];
        System.arraycopy(submeshes, 0, starts, 0, submeshCount + 1);
        starts[submeshCount + 1] = indexCount;
        Aabb bounds = vertexCount == 0 ? new Aabb(Vec3.ZERO, Vec3.ZERO)
            : new Aabb(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
        return new ProceduralMesh(buffer, Arrays.copyOf(indices, indexCount), starts, bounds, error);
    }
}
