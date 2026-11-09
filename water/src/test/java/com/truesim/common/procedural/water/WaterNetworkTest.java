package com.truesim.common.procedural.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WaterNetworkTest {

    private static EcologyTerrain valleyTerrain;
    private static WaterNetwork valley;

    @BeforeAll
    static void build() {
        valleyTerrain = Landscapes.valley();
        valley = WaterNetwork.build(valleyTerrain, Landscapes.VALLEY, WaterParameters.DEFAULTS);
    }

    /** The channel carrying the most water out of the region. */
    private static Channel main(WaterNetwork network) {
        Channel best = null;
        for (Channel c : network.channels()) {
            if (best == null || c.discharge(c.count() - 1) > best.discharge(best.count() - 1)) {
                best = c;
            }
        }
        return best;
    }

    @Test
    void channelsFlowDownhill() {
        assertTrue(valley.channels().size() > 3, "tributaries from the gullies: " + valley.channels().size());
        for (Channel c : valley.channels()) {
            assertTrue(c.count() >= 2);
            for (int i = 0; i + 1 < c.count(); i++) {
                assertTrue(c.surface(i + 1) <= c.surface(i) + 1e-9, "channel " + c.id() + " rises at " + i);
            }
            if (c.downstream() >= 0) {
                Channel d = valley.channels().get(c.downstream());
                assertTrue(c.downstream() > c.id(), "downstream channels come after");
                assertEquals(d.surface(0), c.surface(c.count() - 1), 1e-9, "joined at the confluence");
                assertEquals(d.x(0), c.x(c.count() - 1), 1e-9);
                assertEquals(d.z(0), c.z(c.count() - 1), 1e-9);
                assertTrue(d.order() >= c.order(), "Strahler orders never fall downstream");
                assertTrue(d.discharge(0) >= c.discharge(c.count() - 1) - 1e-9, "discharge adds up");
            }
            // the surface follows the ground: never far above it, and the flow points from point to point
            for (int i = 0; i < c.count(); i++) {
                assertTrue(c.surface(i) <= c.ground(i) + 5, "the surface stays near the ground");
                assertEquals(1, c.directionX(i) * c.directionX(i) + c.directionZ(i) * c.directionZ(i), 1e-9);
                assertTrue(c.depth(i) > 0 && c.speed(i) > 0 && c.width(i) > 0 && c.slope(i) > 0);
                assertEquals(c.surface(i) - c.depth(i), c.bed(i), 1e-12);
            }
        }
    }

    @Test
    void theMainRiverRunsDownTheValleyFloor() {
        Channel main = main(valley);
        assertTrue(main.order() >= 2, "the gullies' streams join it");
        // the main river leaves the region at the south edge, near the floor's middle
        assertTrue(main.z(main.count() - 1) > 3900);
        for (Channel c : valley.channels()) {
            if (c.order() == main.order() && c.discharge(0) > 0.5) {
                for (int i = 0; i < c.count(); i++) {
                    assertTrue(Math.abs(c.x(i)) < 60, "on the floor: " + c.x(i));
                }
            }
        }
        // its flow points south
        for (int i = 0; i < main.count(); i++) {
            assertTrue(main.directionZ(i) > 0.5);
        }
    }

    @Test
    void widthGrowsWithDischarge() {
        WaterParameters.Channels g = WaterParameters.DEFAULTS.channels();
        WaterParameters.Flow f = WaterParameters.DEFAULTS.flow();
        double lastQ = -1;
        double lastW = -1;
        Channel main = main(valley);
        for (int i = 0; i < main.count(); i++) {
            assertTrue(main.discharge(i) >= lastQ - 1e-12, "discharge grows downstream");
            assertTrue(main.width(i) >= lastW - 1e-12, "and so does the width");
            lastQ = main.discharge(i);
            lastW = main.width(i);
        }
        for (Channel c : valley.channels()) {
            for (int i = 0; i < c.count(); i++) {
                double q = c.discharge(i);
                assertEquals(f.runoff() * c.area(i) * 1e-6, q, 1e-12, "discharge from catchment");
                assertEquals(Math.max(g.minWidth(), g.widthCoefficient() * Math.pow(q, g.widthExponent())),
                    c.width(i), 1e-9, "hydraulic geometry");
                // continuity: Q = w d v
                assertEquals(q, c.width(i) * c.depth(i) * c.speed(i), 1e-9 * Math.max(1, q));
                assertTrue(c.speed(i) <= g.maxSpeed() + 1e-9);
            }
        }
        // across the network: a larger river is wider and (on similar slopes) deeper
        Channel smallest = valley.channels().getFirst();
        for (Channel c : valley.channels()) {
            if (c.discharge(0) < smallest.discharge(0)) {
                smallest = c;
            }
        }
        assertTrue(main.width(main.count() - 1) > 2 * smallest.width(0));
        assertTrue(main.discharge(main.count() - 1) > 4 * smallest.discharge(0));
    }

    @Test
    void theFlowRoutingDrainsEveryCell() {
        FlowGrid grid = FlowGrid.build(valleyTerrain, Landscapes.VALLEY, WaterParameters.DEFAULTS.flow(), List.of());
        double total = 0;
        for (int a = 0; a < grid.count(); a++) {
            int r = grid.receiver[a];
            if (r >= 0) {
                assertTrue(grid.filled[r] < grid.filled[a], "receivers lie lower");
            } else {
                total += grid.area[a];
            }
            assertTrue(grid.filled[a] >= grid.height[a]);
        }
        assertEquals(grid.count() * grid.cell * grid.cell, total, 1e-3, "every cell drains out at the edge");
        assertEquals(-1, grid.cellAt(-2000, 10));
        assertEquals(-1, grid.neighbour(0, 3));
        assertTrue(valley.catchment(0, 3990) > 1e6);
        assertEquals(0, valley.catchment(9000, 0));
        assertEquals(10, valley.cell());
    }

    @Test
    void surfacesCarryTheFlow() {
        assertFalse(valley.chunks().isEmpty());
        int vertices = 0;
        for (WaterNetwork.WaterChunk chunk : valley.chunks()) {
            ProceduralMesh mesh = valley.surface(chunk);
            assertTrue(mesh.triangleCount() > 0);
            Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
            Vec3Attribute normal = mesh.vertices().attribute(ProceduralVertex.NORMAL);
            Vec3Attribute tangent = mesh.vertices().attribute(ProceduralVertex.TANGENT);
            Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
            Vec2Attribute uv1 = mesh.vertices().attribute(ProceduralVertex.WIND_PIVOT);
            Vec4Attribute color = mesh.vertices().attribute(ProceduralVertex.WIND);
            for (int v = 0; v < mesh.vertexCount(); v++) {
                assertTrue(normal.y(v) > 0.9, "facing up");
                assertTrue(tangent.y(v) <= 1e-3, "flowing level or down");
                assertTrue(uv.x(v) > 0, "a speed");
                assertTrue(uv.y(v) >= -1 && uv.y(v) <= 1, "across");
                assertTrue(uv1.x(v) > 0, "a depth");
                assertTrue(color.x(v) >= 0 && color.x(v) <= 1 && color.w(v) == 1);
                // within the chunk's reach, relative to its corner
                assertTrue(position.x(v) > -100 && position.x(v) < 612);
            }
            int[] idx = mesh.indices();
            for (int t = 0; t < idx.length; t += 3) {
                double ax = position.x(idx[t + 1]) - position.x(idx[t]);
                double az = position.z(idx[t + 1]) - position.z(idx[t]);
                double bx = position.x(idx[t + 2]) - position.x(idx[t]);
                double bz = position.z(idx[t + 2]) - position.z(idx[t]);
                assertTrue(az * bx - ax * bz >= -1e-6, "counter-clockwise from above");
            }
            vertices += mesh.vertexCount();
        }
        assertTrue(vertices > 1000);
        assertEquals(0, valley.surface(99, 99).triangleCount(), "no water, no surface");
    }

    @Test
    void bedsAndBanksAreCarvedAndBare() {
        Channel main = main(valley);
        int i = main.count() / 2;
        double x = main.x(i);
        double z = main.z(i);
        WaterField field = valley.field();
        WaterField.Sample s = field.sample(x, z);
        assertNotNull(s);
        assertTrue(s.wet());
        assertEquals(main.surface(i), field.surface(x, z), 0.05);
        assertTrue(field.depth(x, z) > main.depth(i), "deepest in the middle");
        double ground = valleyTerrain.height(x, z);
        assertTrue(field.carve(x, z, ground) <= main.surface(i) - main.depth(i), "the bed is cut");
        assertEquals(0, field.grass(x, z), 1e-12);
        // off the river: untouched
        double far = 700;
        assertEquals(valleyTerrain.height(far, z), field.carve(far, z, valleyTerrain.height(far, z)), 1e-12);
        assertEquals(1, field.grass(far, z), 1e-12);
        assertTrue(Double.isNaN(field.surface(far, z)));
        assertEquals(0, field.depth(far, z));
        assertNull(field.sample(5000, 5000));
        assertEquals(1, field.grass(5000, 5000));
        assertEquals(3, field.carve(5000, 5000, 3));
        // across the bank: grass comes back, the ground is never raised
        double rx = -main.directionZ(i);
        double rz = main.directionX(i);
        double previous = -1;
        for (double o = 0; o < 20; o += 0.5) {
            double px = x + rx * o;
            double pz = z + rz * o;
            double g = field.grass(px, pz);
            assertTrue(g >= previous - 1e-9, "grass grows back away from the water");
            previous = g;
            double h = valleyTerrain.height(px, pz);
            assertTrue(field.carve(px, pz, h) <= h + 1e-9);
        }
        assertEquals(1, previous, 1e-9);
        WaterField.Raster raster = field.raster(valleyTerrain, x - 32, z - 32, 64, 32);
        assertEquals(32 * 32, raster.ground().length);
        int wet = 0;
        for (int k = 0; k < raster.depth().length; k++) {
            if (raster.depth()[k] > 0) {
                wet++;
                assertFalse(Float.isNaN(raster.surface()[k]));
            } else {
                assertTrue(Float.isNaN(raster.surface()[k]));
            }
        }
        assertTrue(wet > 0 && wet < 32 * 32);
    }

    @Test
    void stonesLieInTheBedsSizedByTheFlow() {
        List<BedStone> stones = valley.stones();
        assertFalse(stones.isEmpty());
        WaterParameters.Stones p = WaterParameters.DEFAULTS.stones();
        int emergent = 0;
        int inChunks = 0;
        for (BedStone s : stones) {
            assertTrue(s.size() >= p.minSize() && s.size() <= p.maxSize());
            assertEquals(BedStone.preset(s.size()), s.preset());
            Channel c = valley.channels().get(s.channel());
            assertTrue(valley.field().sample(s.x(), s.z()) != null, "in a channel or on its bank");
            if (s.emergent()) {
                emergent++;
            }
            assertTrue(c.count() > 0);
        }
        for (WaterNetwork.WaterChunk chunk : valley.chunks()) {
            inChunks += valley.stones(chunk).size();
        }
        assertTrue(inChunks <= stones.size());
        assertTrue(emergent > 0, "some stand out of the water");
        assertEquals("boulder", BedStone.preset(1.2));
        assertEquals("block", BedStone.preset(0.5));
        assertEquals("scree", BedStone.preset(0.2));
        // deterministic, and the seed moves them
        WaterNetwork again = WaterNetwork.build(valleyTerrain, Landscapes.VALLEY, WaterParameters.DEFAULTS);
        assertEquals(stones, again.stones());
        WaterNetwork reseeded = WaterNetwork.build(valleyTerrain, Landscapes.VALLEY,
            WaterParameters.DEFAULTS.withSeed(99));
        assertFalse(stones.equals(reseeded.stones()));
        WaterNetwork none = WaterNetwork.build(valleyTerrain, Landscapes.VALLEY, WaterParameters.DEFAULTS
            .withStones(new WaterParameters.Stones(0, 0.5, 0.15, 2.5, 0.15, 3)));
        assertTrue(none.stones().isEmpty());
    }

    @Test
    void aCliffMakesAFreeFallThatTurnsToSprayAndMist() {
        WaterRegion region = new WaterRegion(-1000, 0, 1000, 2500);
        WaterNetwork network = WaterNetwork.build(Landscapes.cliff(150, 30), region, WaterParameters.DEFAULTS);
        Waterfall fall = network.tallest();
        assertNotNull(fall);
        assertTrue(fall.free());
        assertEquals(150, fall.height(), 15);
        Channel c = network.channels().get(fall.channel());
        assertEquals(1000, c.z(fall.lip()), 30, "the lip at the cliff's edge");
        assertTrue(fall.lip() < fall.foot());
        for (int i = fall.lip(); i < fall.foot(); i++) {
            assertEquals(fall.id(), c.fall(i));
        }
        WaterParameters.Falls f = WaterParameters.DEFAULTS.falls();
        double q = fall.discharge() / fall.width();
        assertEquals(f.breakupCoefficient() * Math.pow(q, f.breakupExponent()), fall.breakup(), 1e-9);
        assertEquals(1 - Math.exp(-fall.height() / f.atomisation()), fall.atomised(), 1e-9);
        assertEquals(q, fall.thickness() * fall.lipSpeed(), 1e-9);
        // the sheet falls, never inside the ground, faster and faster toward the terminal speed
        Waterfall.Path path = fall.path();
        assertTrue(path.count() > 20);
        EcologyTerrain cliff = Landscapes.cliff(150, 30);
        for (int k = 1; k < path.count(); k++) {
            assertTrue(path.y()[k] <= path.y()[k - 1]);
            assertTrue(path.time()[k] > path.time()[k - 1]);
            assertTrue(path.y()[k] >= cliff.height(path.x()[k], path.z()[k]) - 1, "off the face");
            assertTrue(path.width()[k] >= path.width()[k - 1]);
        }
        assertEquals(fall.height(), path.fallen(path.count() - 1), 0.5);
        assertTrue(path.speed(path.count() - 1) > 8 && path.speed(path.count() - 1) < 1.5 * f.terminalSpeed() + 5);
        // all its water goes somewhere: spray on the way, the rest plunging
        double total = 0;
        for (Waterfall.Spray s : fall.spray()) {
            total += s.rate();
            assertTrue(s.radius() > 0);
        }
        assertEquals(fall.discharge(), total, 1e-9);
        Waterfall.Mist mist = fall.mist();
        assertTrue(mist.density() > 0 && mist.radiusY() > 0);
        assertTrue(mist.y() > path.y()[path.count() - 1]);
        assertEquals(1 / mist.radiusY(), mist.falloff(), 1e-12);
        // its meshes: a sheet facing away from the cliff, and jets
        ProceduralMesh mesh = network.fallMesh(fall.id());
        assertEquals(2, mesh.submeshCount());
        assertTrue(mesh.submeshEnd(0) > 0, "a sheet");
        assertTrue(mesh.submeshEnd(1) > mesh.submeshStart(1), "jets");
        Vec3Attribute normal = mesh.vertices().attribute(ProceduralVertex.NORMAL);
        Vec2Attribute uv1 = mesh.vertices().attribute(ProceduralVertex.WIND_PIVOT);
        for (int v = 0; v < mesh.vertexCount(); v++) {
            assertTrue(normal.z(v) > 0.9, "facing south, away from the cliff");
            assertTrue(uv1.y(v) >= 0);
        }
        double[] origin = network.fallOrigin(fall.id());
        assertEquals(c.surface(fall.lip()), origin[1], 1e-12);
        // the river's surface leaves the free fall out
        for (WaterNetwork.WaterChunk chunk : network.chunks()) {
            ProceduralMesh surface = network.surface(chunk);
            Vec3Attribute position = surface.vertices().attribute(ProceduralVertex.POSITION);
            for (int v = 0; v < surface.vertexCount(); v++) {
                double z = position.z(v) + chunk.originZ();
                assertFalse(z > c.z(fall.lip()) + 1 && z < c.z(fall.foot()) - 1, "no surface down the cliff");
            }
        }
    }

    @Test
    void aSteepButSlopingDropIsACascade() {
        WaterRegion region = new WaterRegion(-1000, 0, 1000, 2500);
        WaterNetwork network = WaterNetwork.build(Landscapes.cliff(60, 45), region, WaterParameters.DEFAULTS);
        assertNull(network.tallest());
        Waterfall cascade = network.falls().getFirst();
        assertFalse(cascade.free());
        assertEquals(0, cascade.atomised());
        assertEquals(0, network.fallMesh(cascade.id()).triangleCount());
        Channel c = network.channels().get(cascade.channel());
        assertEquals(-1, c.fall(cascade.lip()), "a cascade keeps its surface");
        assertEquals(cascade.foot() - cascade.lip() + 1, cascade.path().count());
        assertEquals(1, cascade.spray().size(), "spray where it lands");
    }

    @Test
    void mappedWatercoursesAreFollowed() throws Exception {
        WaterRegion region = new WaterRegion(0, 0, 2000, 2000);
        Watercourse brook = new Watercourse("Brook", new double[] {200, 200, 1000, 900, 1800, 1100}, 2.5, 0.4);
        WaterNetwork network = WaterNetwork.build(Landscapes.plane(), region, WaterParameters.DEFAULTS,
            Watercourses.of(List.of(brook)));
        Channel named = null;
        for (Channel c : network.channels()) {
            if (c.name().equals("Brook")) {
                named = c;
            }
        }
        assertNotNull(named, "a channel follows it, whatever its catchment");
        assertEquals(2.5, named.width(named.count() / 2), 1e-12);
        assertEquals(0.4, named.discharge(named.count() / 2), 1e-12);
        // along the mapped line (diagonally down the plane, not straight down it)
        int i = named.count() / 3;
        double x = named.x(i);
        double z = named.z(i);
        double expected = x < 1000 ? 200 + (x - 200) * 700.0 / 800 : 900 + (x - 1000) * 200.0 / 800;
        assertTrue(Math.abs(z - expected) < 25, "on the brook: " + x + ", " + z);
        assertTrue(WaterNetwork.build(Landscapes.plane(), region, WaterParameters.DEFAULTS).channels().stream()
            .noneMatch(c -> c.name().equals("Brook")));
    }

    @Test
    void theSheetsLedgesAreSmoothedNeverIntoTheRock() {
        // a path falling north (direction 0, -1) held off the face: out 0 m, then a jump to 6 m at sample 20
        int n = 60;
        double[] x = new double[n];
        double[] y = new double[n];
        double[] z = new double[n];
        double[] zero = new double[n];
        double[] w = new double[n];
        for (int k = 0; k < n; k++) {
            y[k] = -2.0 * k;
            z[k] = k < 20 ? 0 : -6;
            x[k] = 0.1 * k;
            w[k] = 2;
        }
        Waterfall.Path path = new Waterfall.Path(x, y, z, zero, zero, zero, zero, w);
        Waterfall.Path smooth = WaterMeshes.smooth(path, 0, -1);
        double steepest = 0;
        for (int k = 0; k < n; k++) {
            assertTrue(-smooth.z()[k] >= -z[k] - 1e-9, "never closer to the rock");
            assertEquals(x[k], smooth.x()[k], 1e-9, "the sideways position kept");
            assertEquals(y[k], smooth.y()[k], 1e-12);
            if (k > 0) {
                steepest = Math.max(steepest, Math.abs(smooth.z()[k] - smooth.z()[k - 1]));
            }
        }
        assertTrue(steepest < 3, "the 6 m ledge spread over samples: " + steepest);
        assertEquals(0, smooth.z()[0], 1e-12);
        assertTrue(WaterMeshes.smooth(new Waterfall.Path(new double[2], new double[2], new double[2], new double[2],
            new double[2], new double[2], new double[2], new double[2]), 1, 0).count() == 2);
    }
}
