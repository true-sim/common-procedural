package com.truesim.common.procedural.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.Site;
import com.truesim.common.procedural.settlement.Settlement.Building;
import com.truesim.common.procedural.settlement.Settlement.Plot;
import com.truesim.common.procedural.settlement.Settlement.Road;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A village's plan on a synthetic Alpine valley: its roads, plots and buildings keep the plan's rules. */
class SettlementTest {

    static Ecology ecology;
    static Settlement village;

    @BeforeAll
    static void plan() {
        ecology = Valleys.ecology(Valleys.valley());
        village = Settlements.plan(SettlementParameters.DEFAULTS, ecology, -60, 0, 7);
    }

    @Test
    void theVillageHasEveryPart() {
        assertThat(village.roads()).extracting(Road::kind).contains(RoadKind.values());
        assertThat(village.roads()).extracting(Road::surface).contains(RoadSurface.values());
        assertThat(village.buildings()).extracting(b -> b.spec().kind()).contains(BuildingKind.values());
        assertThat(village.plots()).extracting(Plot::zone).contains(Settlement.Zone.values());
        assertThat(village.lamps()).isNotEmpty();
        assertThat(village.fences()).extracting(Settlement.Fence::kind).contains(Settlement.FenceKind.values());
        assertThat(village.props()).extracting(Settlement.Prop::kind).contains(PropKind.FOUNTAIN, PropKind.BENCH,
            PropKind.TABLE_SET, PropKind.WOODPILE, PropKind.SIGNPOST);
        assertThat(village.buildings()).filteredOn(b -> b.spec().kind() == BuildingKind.CHURCH).hasSize(1);
        assertThat(village.buildings()).filteredOn(b -> b.spec().kind() == BuildingKind.INN).hasSize(1);
        assertThat(village.buildings().size()).isGreaterThan(30);
    }

    @Test
    void theSameInputsGiveTheSamePlan() {
        Settlement again = Settlements.plan(SettlementParameters.DEFAULTS, Valleys.ecology(Valleys.valley()), -60, 0,
            7);
        assertSame(village, again);
        Settlement other = Settlements.plan(SettlementParameters.DEFAULTS, ecology, -60, 0, 8);
        assertThat(other.buildings().size() != village.buildings().size()
            || other.buildings().getLast().x() != village.buildings().getLast().x()).isTrue();
    }

    static void assertSame(Settlement a, Settlement b) {
        assertThat(b.roads()).hasSameSizeAs(a.roads());
        for (int r = 0; r < a.roads().size(); r++) {
            assertThat(b.roads().get(r).x()).isEqualTo(a.roads().get(r).x());
            assertThat(b.roads().get(r).y()).isEqualTo(a.roads().get(r).y());
            assertThat(b.roads().get(r).z()).isEqualTo(a.roads().get(r).z());
        }
        assertThat(b.plots()).hasSameSizeAs(a.plots());
        assertThat(b.buildings()).isEqualTo(a.buildings());
        assertThat(b.props()).isEqualTo(a.props());
        assertThat(b.lamps()).isEqualTo(a.lamps());
        assertThat(b.fences()).hasSameSizeAs(a.fences());
    }

    @Test
    void roadsStayUnderTheirClassesGrade() {
        assertGrades(village);
    }

    static void assertGrades(Settlement s) {
        for (Road r : s.roads()) {
            double max = s.parameters().road(r.kind()).maxGrade();
            for (int i = 1; i < r.count(); i++) {
                double run = Geometry.length(r.x()[i] - r.x()[i - 1], r.z()[i] - r.z()[i - 1]);
                assertThat(Math.abs(r.y()[i] - r.y()[i - 1])).as("road %d (%s) at %d", r.id(), r.kind(), i)
                    .isLessThanOrEqualTo(max * run + 1e-9);
            }
        }
    }

    @Test
    void plotsKeepOffRoadsAndEachOther() {
        assertPlots(village);
    }

    static void assertPlots(Settlement s) {
        for (Plot plot : s.plots()) {
            for (Road r : s.roads()) {
                for (int i = 1; i < r.count(); i++) {
                    double d = Geometry.segmentPolygon(r.x()[i - 1], r.z()[i - 1], r.x()[i], r.z()[i], plot.x(),
                        plot.z());
                    assertThat(d).as("plot %d and road %d", plot.id(), r.id()).isGreaterThanOrEqualTo(r.width() / 2);
                }
            }
            for (Plot other : s.plots()) {
                if (other != plot) {
                    assertThat(Geometry.overlap(plot.x(), plot.z(), other.x(), other.z(), 0.01))
                        .as("plots %d and %d", plot.id(), other.id()).isFalse();
                }
            }
            double[] c = s.square().corners();
            assertThat(Geometry.overlap(plot.x(), plot.z(), new double[] {c[0], c[2], c[4], c[6]},
                new double[] {c[1], c[3], c[5], c[7]}, 0.01)).isFalse();
            assertThat(plot.area()).isGreaterThan(50);
        }
    }

    @Test
    void everyBuildingIsReachable() {
        assertReachable(village);
    }

    /**
     * Every road joins the network (its start lies on the square or another road, all linked to the square), and
     * every building stands in a plot fronting a road or at a footpath's end.
     */
    static void assertReachable(Settlement s) {
        List<Road> roads = s.roads();
        boolean[] reached = new boolean[roads.size()];
        Deque<Integer> queue = new ArrayDeque<>();
        for (Road r : roads) {
            if (touches(s, r, Settlement.SQUARE)) {
                reached[r.id()] = true;
                queue.add(r.id());
            }
        }
        while (!queue.isEmpty()) {
            Road a = roads.get(queue.poll());
            for (Road b : roads) {
                if (!reached[b.id()] && (joins(b, a) || joins(a, b))) {
                    reached[b.id()] = true;
                    queue.add(b.id());
                }
            }
        }
        for (Road r : roads) {
            assertThat(reached[r.id()]).as("road %d (%s) joins the network", r.id(), r.kind()).isTrue();
        }
        double[] door = new double[2];
        for (Building b : s.buildings()) {
            Road road = b.road() >= 0 ? roads.get(b.road()) : null;
            if (b.plot() >= 0) {
                Plot plot = s.plots().get(b.plot());
                double[] k = b.corners();
                for (int c = 0; c < 4; c++) {
                    assertThat(Geometry.depth(k[2 * c], k[2 * c + 1], plot.x(), plot.z())).as("building %d", b.id())
                        .isGreaterThan(-1e-6);
                }
                // its plot's front on its road (or the square): a front corner across the verge from it
                double reach = Double.MAX_VALUE;
                for (int c = 0; c < 2; c++) {
                    reach = Math.min(reach, plot.road() == Settlement.SQUARE
                        ? s.square().outside(plot.x()[c], plot.z()[c])
                        : roads.get(plot.road()).distance(plot.x()[c], plot.z()[c])
                        - roads.get(plot.road()).width() / 2);
                }
                assertThat(reach).as("plot %d's front", plot.id()).isLessThan(s.parameters().plots().verge() + 0.01);
            } else {
                b.door(door);
                assertThat(road).isNotNull();
                assertThat(road.distance(door[0], door[1])).as("barn %d's door", b.id()).isLessThan(2.5);
            }
        }
    }

    private static boolean touches(Settlement s, Road r, int what) {
        return r.from() == what && s.square().outside(r.x()[0], r.z()[0]) < 0.05
            || r.to() == what && s.square().outside(r.x()[r.count() - 1], r.z()[r.count() - 1]) < 0.05;
    }

    /** Whether road {@code a} starts or ends on road {@code b}. */
    private static boolean joins(Road a, Road b) {
        double tolerance = 0.05;
        return b.distance(a.x()[0], a.z()[0]) < tolerance
            || b.distance(a.x()[a.count() - 1], a.z()[a.count() - 1]) < tolerance;
    }

    @Test
    void buildingsKeepOffFloodProneAndSteepGround() {
        assertSited(village, ecology);
    }

    static void assertSited(Settlement s, Ecology ecology) {
        SettlementParameters.Siting siting = s.parameters().siting();
        Site site = new Site();
        for (Building b : s.buildings()) {
            if (b.spec().kind() == BuildingKind.CHURCH || b.spec().kind() == BuildingKind.INN) {
                continue; // on the square, wherever it is
            }
            ecology.conditions(b.x(), b.z(), site);
            double floods = siting.floodHeight() + siting.floodPerRoot() * Math.sqrt(site.streamArea / 1e6);
            assertThat(site.water).as("building %d", b.id()).isLessThanOrEqualTo(siting.water() + 0.05);
            assertThat(site.hand).as("building %d's height above its drainage", b.id())
                .isGreaterThan(floods - 0.5);
            // its footprint no steeper than allowed: the fall across it
            double max = b.spec().kind() == BuildingKind.HAY_BARN ? s.parameters().barns().maxSlope()
                : siting.maxSlope();
            double diagonal = Math.sqrt(b.spec().width() * b.spec().width() + b.spec().depth() * b.spec().depth());
            assertThat(b.y() - b.foundation()).as("building %d's fall", b.id())
                .isLessThan(Math.tan(Math.toRadians(max + 6)) * diagonal);
        }
    }

    @Test
    void housesFaceTheValleyOrTheirRoad() {
        double[] facing = new double[2];
        Field field = new Field(ecology, -60, 0, SettlementParameters.DEFAULTS);
        int valley = 0;
        for (Building b : village.buildings()) {
            if (b.spec().kind() != BuildingKind.HOUSE && b.spec().kind() != BuildingKind.FARMHOUSE) {
                continue;
            }
            field.facing(b.x(), b.z(), 24, SettlementParameters.DEFAULTS.buildings().sunWeight(), facing);
            double fx = Math.sin(b.yaw());
            double fz = Math.cos(b.yaw());
            if (fx * facing[0] + fz * facing[1] > 0.99) {
                valley++;
            }
            assertThat(b.spec().ridge()).isEqualTo(BuildingSpec.Ridge.ACROSS);
        }
        assertThat(valley).isGreaterThan(5);
    }

    @Test
    void parametersAndSpecsCheckTheirValues() {
        assertThatThrownBy(() -> new SettlementParameters.Grid(10, 4, 1)).isInstanceOf(
            IllegalArgumentException.class);
        assertThatThrownBy(() -> new SettlementParameters.RoadClass(0, 0.1, 20, 1, 100)).isInstanceOf(
            IllegalArgumentException.class);
        assertThatThrownBy(() -> new BuildingSpec(BuildingKind.HOUSE, 0, 1, 1, BuildingSpec.Ridge.ALONG, 1))
            .isInstanceOf(IllegalArgumentException.class);
        SettlementParameters p = SettlementParameters.DEFAULTS;
        for (RoadKind kind : RoadKind.values()) {
            assertThat(p.withRoad(kind, SettlementParameters.RoadClass.FOOTPATH).road(kind))
                .isEqualTo(SettlementParameters.RoadClass.FOOTPATH);
        }
        assertThat(p.withGrid(p.grid()).withNetwork(p.network()).withCore(p.core()).withPlots(p.plots())
            .withBuildings(p.buildings()).withSiting(p.siting()).withBarns(p.barns()).withFurniture(p.furniture())
            .withSurface(p.surface())).isEqualTo(p);
    }
}
