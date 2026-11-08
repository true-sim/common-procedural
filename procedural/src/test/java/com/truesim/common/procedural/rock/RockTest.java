package com.truesim.common.procedural.rock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.math.geometry.Meshes;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.lang.foreign.ValueLayout;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RockTest {

    static List<RockParams> presets() {
        return RockParams.PRESETS;
    }

    @ParameterizedTest
    @MethodSource("presets")
    void theSameSeedGivesBitIdenticalRocks(RockParams params) {
        RockGenerator generator = new RockGenerator();
        Rock a = generator.generate(params, 5);
        generator.generate(params, 6);
        Rock b = Rocks.generate(params, 5);
        for (int l = 0; l < a.lods().size(); l++) {
            assertThat(a.lods().get(l).vertices().segment().toArray(ValueLayout.JAVA_BYTE))
                .isEqualTo(b.lods().get(l).vertices().segment().toArray(ValueLayout.JAVA_BYTE));
            assertThat(a.lods().get(l).indices()).isEqualTo(b.lods().get(l).indices());
        }
        assertThat(generator.generate(params, 6).lods().getFirst().vertices().segment().toArray(ValueLayout.JAVA_BYTE))
            .isNotEqualTo(a.lods().getFirst().vertices().segment().toArray(ValueLayout.JAVA_BYTE));
        assertThat(a.params()).isSameAs(params);
        assertThat(a.seed()).isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("presets")
    void everyLevelIsClosedOutwardAndCoarser(RockParams params) {
        Rock rock = Rocks.generate(params, 3);
        int previous = Integer.MAX_VALUE;
        double previousError = -1;
        for (int l = 0; l < rock.lods().size(); l++) {
            ProceduralMesh mesh = rock.lods().get(l);
            int level = params.subdivisions().get(l);
            assertThat(mesh.triangleCount()).isEqualTo(20 << (2 * level)).isLessThan(previous);
            assertThat(Meshes.openEdges(mesh.indices(), mesh.indices().length, null)).isZero();
            assertThat(signedVolume(mesh)).isPositive(); // wound counter-clockwise from outside
            assertThat(mesh.error()).isGreaterThan(previousError);
            previous = mesh.triangleCount();
            previousError = mesh.error();
            // on the ground: the flat cut a little below y 0, the rock above it
            assertThat(mesh.bounds().min().y()).isBetween(-0.051 * params.size(), 0.0);
            assertThat(mesh.bounds().max().y()).isGreaterThan(0.1 * params.size());
            Vec4Attribute wind = mesh.vertices().attribute(ProceduralVertex.WIND);
            for (int i = 0; i < mesh.vertexCount(); i++) {
                assertThat(wind.get(i).lengthSquared()).isZero();
            }
        }
        assertThat(rock.lods().getFirst().error()).isZero();
        // a coarse level stays near the detailed surface
        assertThat(rock.lods().get(1).error()).isLessThan(0.25 * params.size());
    }

    private static double signedVolume(ProceduralMesh mesh) {
        Vec3Attribute p = mesh.vertices().attribute(ProceduralVertex.POSITION);
        int[] idx = mesh.indices();
        double volume = 0;
        for (int t = 0; t < idx.length; t += 3) {
            var a = p.get(idx[t]);
            var b = p.get(idx[t + 1]);
            var c = p.get(idx[t + 2]);
            volume += a.dot(b.cross(c)) / 6;
        }
        return volume;
    }

    @Test
    void parametersAreChecked() {
        assertThatThrownBy(() -> new RockParams("x", 0, 1, 1, 0, 0, 0, 0, 0, 0, List.of(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockParams("x", 1, 1, 1, 0, 0, 0, 0, 0, 0, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockParams("x", 1, 1, 1, 0, 0, 0, 0, 0, 0, List.of(8)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockParams("x", 1, 1, 1, 0, 0, 0, 0, 0, 0, List.of(-1)))
            .isInstanceOf(IllegalArgumentException.class);
        // a plain sphere: no noise, no planes
        Rock ball = Rocks.generate(new RockParams("ball", 1, 1, 1, 0, 0, 0, 0, 0, 0, List.of(3, 0)), 1);
        assertThat(ball.lods().get(1).triangleCount()).isEqualTo(20);
    }
}
