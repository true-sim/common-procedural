package com.truesim.common.procedural.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Aabb;
import com.truesim.common.math.layout.ElementBuffer;
import org.junit.jupiter.api.Test;

class MeshWriterTest {

    @Test
    void writesVerticesTrianglesAndSubmeshesAndGrows() {
        MeshWriter writer = new MeshWriter().reset();
        writer.wind(0.25, 0.5, 0.75, 1, 3, 4);
        int first = -1;
        for (int i = 0; i < 5000; i++) { // past the initial capacity
            int v = writer.vertex(i, -i, 2 * i, 0, 1, 0, 1, 0, 0, 0.5, 0.25);
            first = first < 0 ? v : first;
        }
        for (int i = 0; i + 2 < 5000; i += 3) {
            writer.triangle(i, i + 1, i + 2);
        }
        writer.nextSubmesh();
        writer.windWeights(0, 1);
        int a = writer.vertex(0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0);
        int b = writer.copyPosition(a, 0, 0, 1, 1, 0, 0, 1, 0);
        writer.quad(a, b, a, b);
        for (int s = 0; s < 8; s++) {
            writer.nextSubmesh(); // past the submesh capacity
        }
        assertThat(writer.vertexCount()).isEqualTo(5002);
        assertThat(writer.y(1)).isEqualTo(-1);
        ProceduralMesh mesh = writer.build(0.5);
        assertThat(mesh.vertexCount()).isEqualTo(5002);
        assertThat(mesh.triangleCount()).isEqualTo(1666 + 2);
        assertThat(mesh.submeshCount()).isEqualTo(10);
        assertThat(mesh.submeshStart(1)).isEqualTo(3 * 1666);
        assertThat(mesh.submeshEnd(1)).isEqualTo(3 * 1668);
        assertThat(mesh.submeshIndices(1)).containsExactly(a, b, a, a, a, b);
        assertThat(mesh.error()).isEqualTo(0.5);
        assertThat(mesh.bounds()).isEqualTo(new Aabb(new Vec3(0, -4999, 0), new Vec3(4999, 0, 9998)));
        assertThat(mesh.center()).isEqualTo(new Vec3(2499.5, -2499.5, 4999));
        var wind = mesh.vertices().attribute(ProceduralVertex.WIND);
        assertThat(wind.x(0)).isCloseTo(0.25, within(1 / 255.0));
        assertThat(wind.w(0)).isEqualTo(1);
        assertThat(ProceduralVertex.leaf(wind.z(0))).isCloseTo(0.75, within(1 / 30.0));
        assertThat(ProceduralVertex.phase(wind.z(0))).isEqualTo(15 / 16.0); // a phase of 1 is the last step
        assertThat(ProceduralVertex.leaf(wind.z(b))).isEqualTo(1);
        assertThat(ProceduralVertex.packLeafAndPhase(0, 0)).isZero();
        assertThat(ProceduralVertex.packLeafAndPhase(2, -1)).isEqualTo(240 / 255.0);
        assertThat(ProceduralVertex.phase(ProceduralVertex.packLeafAndPhase(0.4, 0.3))).isEqualTo(4 / 16.0);
        var pivot = mesh.vertices().attribute(ProceduralVertex.WIND_PIVOT);
        assertThat(pivot.x(0)).isEqualTo(3);
        assertThat(pivot.y(0)).isEqualTo(4);
        assertThat(mesh.positions()[3 * b + 2]).isEqualTo(0);
        assertThat(ProceduralVertex.LAYOUT.stride()).isEqualTo(32);
    }

    @Test
    void anEmptyMeshAndBadSubmeshes() {
        ProceduralMesh empty = new MeshWriter().reset().build(0);
        assertThat(empty.vertexCount()).isZero();
        assertThat(empty.bounds()).isEqualTo(new Aabb(Vec3.ZERO, Vec3.ZERO));
        ElementBuffer buffer = ElementBuffer.onHeap(ProceduralVertex.LAYOUT, 0);
        Aabb box = new Aabb(Vec3.ZERO, Vec3.ZERO);
        assertThatThrownBy(() -> new ProceduralMesh(buffer, new int[3], new int[] {0}, box, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProceduralMesh(buffer, new int[3], new int[] {1, 3}, box, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProceduralMesh(buffer, new int[3], new int[] {0, 2}, box, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
