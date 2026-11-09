package com.truesim.common.procedural.rock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.math.geometry.Meshes;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RockTest {

    private static final RockCover MOSSY = new RockCover(0.7, 0.5, 1.1, 0.3);

    static List<RockParams> presets() {
        return RockParams.PRESETS;
    }

    @ParameterizedTest
    @MethodSource("presets")
    void theSameSeedGivesBitIdenticalRocks(RockParams params) throws Exception {
        RockGenerator generator = new RockGenerator();
        Rock a = generator.generate(params, MOSSY, 5);
        generator.generate(RockParams.CLUSTER, 6);
        Rock b = Rocks.generate(params, MOSSY, 5);
        Rock c;
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<Rock> other = pool.submit(() -> Rocks.generate(params, MOSSY, 5));
            c = other.get();
        }
        for (int l = 0; l < a.lods().size(); l++) {
            byte[] bytes = a.lods().get(l).vertices().segment().toArray(ValueLayout.JAVA_BYTE);
            assertThat(b.lods().get(l).vertices().segment().toArray(ValueLayout.JAVA_BYTE)).isEqualTo(bytes);
            assertThat(c.lods().get(l).vertices().segment().toArray(ValueLayout.JAVA_BYTE)).isEqualTo(bytes);
            assertThat(a.lods().get(l).indices()).isEqualTo(b.lods().get(l).indices());
            assertThat(a.lods().get(l).submeshes()).isEqualTo(b.lods().get(l).submeshes());
        }
        assertThat(generator.generate(params, MOSSY, 6).lods().getFirst().vertices().segment()
            .toArray(ValueLayout.JAVA_BYTE)).isNotEqualTo(a.lods().getFirst().vertices().segment()
                .toArray(ValueLayout.JAVA_BYTE));
        assertThat(a.params()).isSameAs(params);
        assertThat(a.cover()).isEqualTo(MOSSY);
        assertThat(a.seed()).isEqualTo(5);
        assertThat(a.footprint()).isEqualTo(b.footprint());
    }

    @ParameterizedTest
    @MethodSource("presets")
    void everyLevelIsClosedOutwardCoarserAndOnTheGround(RockParams params) {
        Rock rock = Rocks.generate(params, 3);
        int members = params.cluster().members();
        int previous = Integer.MAX_VALUE;
        double previousError = -1;
        for (int l = 0; l < rock.lods().size(); l++) {
            ProceduralMesh mesh = rock.lods().get(l);
            int level = params.subdivisions().get(l);
            assertThat(mesh.triangleCount()).isEqualTo(members * (20 << (2 * level))).isLessThan(previous);
            assertThat(mesh.submeshCount()).isEqualTo(2);
            assertThat(Meshes.openEdges(mesh.indices(), mesh.indices().length, null)).isZero();
            assertThat(signedVolume(mesh)).isPositive(); // wound counter-clockwise from outside
            assertThat(mesh.error()).isGreaterThanOrEqualTo(previousError);
            previous = mesh.triangleCount();
            previousError = mesh.error();
            // on the ground: the flat cut a little below y 0, the rock above it
            assertThat(mesh.bounds().min().y()).isBetween(-0.051 * params.size(), 0.0);
            assertThat(mesh.bounds().max().y()).isGreaterThan(0.1 * params.size());
            Vec2Attribute pivot = mesh.vertices().attribute(ProceduralVertex.WIND_PIVOT);
            Vec4Attribute tint = mesh.vertices().attribute(ProceduralVertex.WIND);
            for (int i = 0; i < mesh.vertexCount(); i++) {
                assertThat(pivot.x(i)).isZero(); // no wind
                assertThat(tint.w(i)).isEqualTo(1);
                assertThat(tint.x(i)).isBetween(0.3, 1.0); // bare stone: weathered or fresh, a soil stain
            }
        }
        assertThat(rock.lods().getFirst().error()).isZero();
        assertThat(rock.lods().get(1).error()).isPositive();
        // a coarse level stays near the detailed surface
        assertThat(rock.lods().get(1).error()).isLessThan(0.25 * params.size());
        RockFootprint footprint = rock.footprint();
        assertThat(footprint.covers(0, 0, 0)).isTrue();
        assertThat(footprint.reach()).isGreaterThanOrEqualTo(Math.max(footprint.halfX(), footprint.halfZ()));
        assertThat(footprint.height()).isCloseTo(rock.lods().getFirst().bounds().max().y(),
            org.assertj.core.data.Offset.offset(1e-6));
        // the estimate without generating holds the rock
        assertThat(params.reach()).isGreaterThan(0.8 * footprint.reach());
    }

    @Test
    void chipsShowFreshStoneAndBlocksFollowTheirBedding() {
        Rock block = Rocks.generate(RockParams.BLOCK, 11);
        ProceduralMesh mesh = block.lods().getFirst();
        int fresh = mesh.submeshEnd(Rock.FRESH) - mesh.submeshStart(Rock.FRESH);
        assertThat(fresh).isPositive();
        Vec4Attribute tint = mesh.vertices().attribute(ProceduralVertex.WIND);
        int[] indices = mesh.indices();
        for (int i = mesh.submeshStart(Rock.FRESH); i < mesh.submeshEnd(Rock.FRESH); i++) {
            int v = indices[i];
            // fresh stone is lighter than the weathered (but where the soil stains it)
            if (mesh.vertices().attribute(ProceduralVertex.POSITION).y(v) > 0.2) {
                assertThat(tint.x(v)).isGreaterThan(0.9);
            }
        }
        // a boulder without chips has none
        Rock boulder = Rocks.generate(RockParams.BOULDER, 11);
        ProceduralMesh round = boulder.lods().getFirst();
        assertThat(round.submeshEnd(Rock.FRESH)).isEqualTo(round.submeshStart(Rock.FRESH));
        // bedding: a slab lies on its beds, its top flat: much of it faces within 20 degrees of up, unlike a boulder
        assertThat(upShare(Rocks.generate(RockParams.SLAB, 11).lods().get(1))).isGreaterThan(0.3);
        assertThat(upShare(boulder.lods().get(1))).isLessThan(0.2);
    }

    @Test
    void mossGrowsOnTheTopAndTheSideFacingNorthAsTheRockIsTurned() {
        for (double yaw : new double[] {0, Math.PI / 2, Math.PI}) {
            Rock rock = Rocks.generate(RockParams.BOULDER, new RockCover(0.8, 0, yaw, Double.NaN), 4);
            ProceduralMesh mesh = rock.lods().getFirst();
            Vec3Attribute normal = mesh.vertices().attribute(ProceduralVertex.NORMAL);
            Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
            Vec4Attribute tint = mesh.vertices().attribute(ProceduralVertex.WIND);
            // the side that faces north once turned by yaw: mesh (sin yaw, 0, -cos yaw)
            double northX = Math.sin(yaw);
            double northZ = -Math.cos(yaw);
            double north = 0;
            double south = 0;
            double top = 0;
            int nn = 0;
            int ns = 0;
            int nt = 0;
            for (int i = 0; i < mesh.vertexCount(); i++) {
                if (position.y(i) < 0.3 * rock.footprint().height()) {
                    continue; // above the ground band
                }
                double green = tint.y(i) - tint.x(i) + (1 - tint.y(i)); // moss: dark and green
                double facing = normal.x(i) * northX + normal.z(i) * northZ;
                if (Math.abs(normal.y(i)) < 0.4 && facing > 0.7) {
                    north += green;
                    nn++;
                } else if (Math.abs(normal.y(i)) < 0.4 && facing < -0.7) {
                    south += green;
                    ns++;
                } else if (normal.y(i) > 0.9) {
                    top += green;
                    nt++;
                }
            }
            assertThat(nn).isPositive();
            assertThat(ns).isPositive();
            assertThat(nt).isPositive();
            assertThat(north / nn).as("yaw %s", yaw).isGreaterThan(south / ns + 0.2);
            assertThat(top / nt).as("yaw %s", yaw).isGreaterThan(south / ns + 0.1);
        }
        // bare stone has none; lichen alone lightens and yellows exposed faces
        Rock bare = Rocks.generate(RockParams.BOULDER, RockCover.BARE, 4);
        Rock lichened = Rocks.generate(RockParams.BOULDER, new RockCover(0, 1, 0, Double.NaN), 4);
        assertThat(meanTint(lichened.lods().getFirst())).isGreaterThan(meanTint(bare.lods().getFirst()));
    }

    @Test
    void burialSinksTheRockAndWidensItsContact() {
        Rock shallow = Rocks.generate(RockParams.BOULDER, new RockCover(0, 0, 0, 0.05), 9);
        Rock deep = Rocks.generate(RockParams.BOULDER, new RockCover(0, 0, 0, 0.45), 9);
        assertThat(deep.footprint().height()).isLessThan(0.75 * shallow.footprint().height());
        assertThat(deep.footprint().halfX()).isGreaterThan(shallow.footprint().halfX());
        assertThat(deep.lods().getFirst().bounds().min().y()).isBetween(-0.051 * RockParams.BOULDER.size(), 0.0);
    }

    @Test
    void aPileHasItsStonesRoundTheFirst() {
        Rock pile = Rocks.generate(RockParams.CLUSTER, 2);
        Rock one = Rocks.generate(RockParams.CLUSTER.withSize(0.45).withSubdivisions(List.of(4)), 2);
        assertThat(pile.lods().getFirst().triangleCount()).isEqualTo(7 * 20 * 256);
        assertThat(pile.footprint().reach()).isGreaterThan(1.5 * RockParams.CLUSTER.size());
        assertThat(one.lods()).hasSize(1);
    }

    @Test
    void parametersAreChecked() {
        RockParams.Fractures none = new RockParams.Fractures(0, 1, 1, 0, 0, 0, 0);
        RockParams.Weathering smooth = new RockParams.Weathering(0, 0, 0, 0);
        assertThatThrownBy(() -> new RockParams("x", 0, 1, 1, none, smooth, RockParams.Bedding.NONE,
            RockParams.Cluster.SINGLE, 0, List.of(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockParams("x", 1, 1, 1, none, smooth, RockParams.Bedding.NONE,
            RockParams.Cluster.SINGLE, 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockParams("x", 1, 1, 1, none, smooth, RockParams.Bedding.NONE,
            RockParams.Cluster.SINGLE, 0, List.of(8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockParams("x", 1, 1, 1, none, smooth, RockParams.Bedding.NONE,
            RockParams.Cluster.SINGLE, 1, List.of(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockParams.Fractures(40, 1, 1, 0, 0, 10, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockParams.Cluster(0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockCover(2, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockCover(0, 0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RockParams.preset("pebble")).isInstanceOf(IllegalArgumentException.class);
        assertThat(RockParams.preset("slab")).isSameAs(RockParams.SLAB);
        assertThat(RockCover.WEATHERED.withYaw(1).withBuried(0.2)).isEqualTo(new RockCover(0.5, 0.4, 1, 0.2));
        assertThat(RockCover.BARE.bare()).isTrue();
        // a plain sphere: no noise, no planes
        Rock ball = Rocks.generate(new RockParams("ball", 1, 1, 1, none, smooth, RockParams.Bedding.NONE,
            RockParams.Cluster.SINGLE, 0, List.of(3, 0)), 1);
        assertThat(ball.lods().get(1).triangleCount()).isEqualTo(20);
        assertThat(ball.lods().getFirst().bounds().max().y()).isCloseTo(2, org.assertj.core.data.Offset.offset(0.01));
        assertThat(new RockFootprint(0, 1, 1, 1).covers(0, 0, 0)).isFalse();
    }

    private static double meanTint(ProceduralMesh mesh) {
        Vec4Attribute tint = mesh.vertices().attribute(ProceduralVertex.WIND);
        double sum = 0;
        for (int i = 0; i < mesh.vertexCount(); i++) {
            sum += tint.x(i);
        }
        return sum / mesh.vertexCount();
    }

    /** The share of the area above the ground facing within 20 degrees of up. */
    private static double upShare(ProceduralMesh mesh) {
        Vec3Attribute p = mesh.vertices().attribute(ProceduralVertex.POSITION);
        int[] idx = mesh.indices();
        double up = 0;
        double total = 0;
        double cos = Math.cos(Math.toRadians(20));
        for (int t = 0; t < idx.length; t += 3) {
            var a = p.get(idx[t]);
            var b = p.get(idx[t + 1]);
            var c = p.get(idx[t + 2]);
            var cross = b.sub(a).cross(c.sub(a));
            double l = cross.length();
            if (l == 0 || cross.y() / l < -0.9) {
                continue; // the cut below
            }
            total += l;
            if (cross.y() / l > cos) {
                up += l;
            }
        }
        return up / total;
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
}
