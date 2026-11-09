package com.truesim.common.procedural.ecology.rocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.ecology.EcologyTile;
import com.truesim.common.procedural.ecology.Instances;
import com.truesim.common.procedural.ecology.Obstacles;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.rock.Rock;
import com.truesim.common.procedural.rock.Rocks;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RockFieldTest {

    /** Small analysis blocks: catchments a kilometre long suffice here. */
    static final EcologyParameters.Analysis QUICK = new EcologyParameters.Analysis(8, 512, 512, 150, 40, 32);
    static final EcologyParameters PARAMETERS = EcologyParameters.ALPS.withSeed(5).withAnalysis(QUICK)
        .withLandUse(EcologyParameters.LandUse.MEADOWS);

    /** The cliff's top, foot and the talus' toe along z (the wall faces south, +z). */
    static final double TOP = -400;
    static final double FOOT = -320;
    static final double TOE = -170;

    /**
     * A limestone wall facing south: a plateau at 1300 m, a cliff of about 75 degrees down to 1000 m at z = -320,
     * talus at 34 degrees to z = -170, a valley floor falling 3 degrees south beyond; gentle swells across.
     */
    static EcologyTerrain wall() {
        double talus = Maths.tan(Maths.toRadians(34));
        double floor = Maths.tan(Maths.toRadians(3));
        return (x, z) -> {
            double swell = 4 * Maths.sin(x / 90) + 2 * Maths.sin(z / 70 + x / 130);
            double h;
            if (z < TOP) {
                h = 1300 + 0.05 * (TOP - z);
            } else if (z < FOOT) {
                double s = (z - TOP) / (FOOT - TOP);
                h = 1300 - 300 * s;
            } else if (z < TOE) {
                h = 1000 - talus * (z - FOOT);
            } else {
                h = 1000 - talus * (TOE - FOOT) - floor * (z - TOE);
            }
            return h + swell;
        };
    }

    @Test
    void talusLiesBelowTheCliffItsBlocksGrowingDownslope() {
        RockField field = new RockField(new Ecology(wall(), PARAMETERS, AlpineSpecies.ALL),
            RockFieldParameters.DEFAULTS);
        RockTile tile = field.region(-128, -340, 256, 640);
        RockInstances rocks = tile.instances();
        // the talus: below the foot, above the toe; rockfall below the toe; none on the cliff or the plateau
        List<double[]> talus = new ArrayList<>();
        List<Double> fallen = new ArrayList<>();
        for (int i = 0; i < rocks.count(); i++) {
            double z = rocks.z(i);
            assertThat(z).as("nothing on the cliff").isGreaterThan(FOOT - 8);
            if (rocks.kind(i) == RockKind.TALUS) {
                assertThat(z).isLessThan(TOE + 12);
                talus.add(new double[] {z - FOOT, rocks.size(i)});
            } else if (rocks.kind(i) == RockKind.ROCKFALL) {
                assertThat(z).isGreaterThan(TOE - 12);
                fallen.add(rocks.size(i));
            }
        }
        assertThat(talus.size()).isGreaterThan(300);
        assertThat(fallen.size()).isGreaterThan(10);
        // size sorting: the mean size grows band by band downslope, and the run-out's blocks are bigger still
        double length = TOE - FOOT;
        double[] sum = new double[3];
        int[] count = new int[3];
        double sx = 0;
        double sy = 0;
        double sxx = 0;
        double sxy = 0;
        for (double[] rock : talus) {
            int band = (int) Math.clamp(rock[0] / length * 3, 0, 2);
            sum[band] += rock[1];
            count[band]++;
            sx += rock[0];
            sy += rock[1];
            sxx += rock[0] * rock[0];
            sxy += rock[0] * rock[1];
        }
        double upper = sum[0] / count[0];
        double middle = sum[1] / count[1];
        double lower = sum[2] / count[2];
        assertThat(middle).isGreaterThan(1.2 * upper);
        assertThat(lower).isGreaterThan(1.2 * middle);
        int n = talus.size();
        double regression = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        assertThat(regression).as("metres of size per metre downslope").isPositive();
        double fallenMean = fallen.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        assertThat(fallenMean).isGreaterThan(lower);
        // the apex is fresh, the toe older: less lichen up top
        assertThat(meanLichen(rocks, RockKind.TALUS, FOOT, FOOT + 50))
            .isLessThan(meanLichen(rocks, RockKind.TALUS, TOE - 50, TOE) + 1e-9);
    }

    @Test
    void rocksRestOnTheGround() {
        EcologyTerrain terrain = wall();
        RockField field = new RockField(new Ecology(terrain, PARAMETERS, AlpineSpecies.ALL),
            RockFieldParameters.DEFAULTS);
        RockInstances rocks = field.region(-64, -330, 128, 400).instances();
        assertThat(rocks.count()).isGreaterThan(100);
        double[] q = new double[4];
        double[] p = new double[3];
        int checked = 0;
        for (int i = 0; i < rocks.count(); i += 7) {
            RockVariant variant = rocks.variant(i);
            Rock rock = Rocks.generate(variant.params().withSubdivisions(List.of(3)), variant.cover(),
                variant.seed());
            ProceduralMesh mesh = rock.lods().getFirst();
            float[] positions = mesh.positions();
            q[0] = rocks.qx(i);
            q[1] = rocks.qy(i);
            q[2] = rocks.qz(i);
            q[3] = rocks.qw(i);
            double s = rocks.scale(i);
            double highest = Double.NEGATIVE_INFINITY; // the contact's highest point above the ground
            double deepest = Double.NEGATIVE_INFINITY;
            double top = Double.NEGATIVE_INFINITY; // the rock's top above the ground under it
            for (int v = 0; v < positions.length; v += 3) {
                double y = positions[v + 1];
                RockField.rotate(q, positions[v] * s, Math.max(0, y) * s, positions[v + 2] * s, p);
                double wx = rocks.x(i) + p[0];
                double wy = rocks.y(i) + p[1];
                double wz = rocks.z(i) + p[2];
                double ground = terrain.height(wx, wz);
                if (y <= 0) {
                    highest = Math.max(highest, wy - ground);
                    deepest = Math.max(deepest, ground - wy);
                }
                top = Math.max(top, wy - ground);
            }
            double size = rocks.size(i);
            // touching: its contact at the ground, nowhere above it; not swallowed: its top well out
            assertThat(highest).as("rock %d (%s, %.2f m) floats", i, rocks.kind(i), size)
                .isBetween(-0.04 * size - 1e-3, 0.01 * size); // its outline sampled: within a percent
            assertThat(deepest).as("rock %d sunk", i).isLessThan(0.6 * size + 0.05);
            assertThat(top).as("rock %d out of the ground", i).isGreaterThan(0.3 * rock.footprint().height() * s);
            checked++;
        }
        assertThat(checked).isGreaterThan(15);
    }

    @Test
    void tilesStreamInAnyOrderAndMatchAtTheirSeams() {
        Ecology ecology = new Ecology(wall(), PARAMETERS, AlpineSpecies.ALL);
        RockField a = new RockField(ecology, RockFieldParameters.DEFAULTS);
        RockField b = new RockField(ecology, RockFieldParameters.DEFAULTS);
        RockInstances whole = a.region(-128, -320, 256, 256).instances();
        RockInstances west = b.region(0, -320, 128, 256).instances(); // the east half first
        RockInstances east = b.region(-128, -320, 128, 256).instances();
        assertThat(west.count() + east.count()).isEqualTo(whole.count());
        List<String> parts = new ArrayList<>();
        describe(west, parts);
        describe(east, parts);
        List<String> all = new ArrayList<>();
        describe(whole, all);
        assertThat(parts).containsExactlyInAnyOrderElementsOf(all);
        assertThat(a.tile(0, -1).instances().count()).isEqualTo(a.region(0, -256, 256, 256).instances().count());
        assertThat(whole.variants()).isSameAs(a.variants());
    }

    @Test
    void plantsKeepOffTheRocksAndTheGrassMapClearsUnderThem() {
        Ecology plain = new Ecology(wall(), PARAMETERS, AlpineSpecies.ALL);
        RockField field = new RockField(plain, RockFieldParameters.DEFAULTS);
        Ecology ecology = plain.avoiding(field);
        assertThat(ecology.obstacles()).isSameAs(field);
        assertThat(plain.obstacles()).isSameAs(Obstacles.NONE);
        double x0 = -64;
        double z0 = -192;
        RockTile rocks = field.region(x0, z0, 128, 128);
        EcologyTile before = plain.region(x0, z0, 128, 128);
        EcologyTile after = ecology.region(x0, z0, 128, 128);
        Instances was = before.instances();
        Instances is = after.instances();
        int blocked = 0;
        for (int i = 0; i < was.count(); i++) {
            blocked += rocks.blocks(was.x(i), was.z(i), 0) ? 1 : 0;
        }
        assertThat(blocked).as("plants on rocks without the rocks").isPositive();
        for (int i = 0; i < is.count(); i++) {
            assertThat(rocks.blocks(is.x(i), is.z(i), 0)).as("plant %d on a rock", i).isFalse();
        }
        assertThat(is.count()).isPositive();
        // the same conditions and fields
        assertThat(after.cover(com.truesim.common.procedural.ecology.GroundCover.MEADOW))
            .isEqualTo(before.cover(com.truesim.common.procedural.ecology.GroundCover.MEADOW));
        // the grass' ground map: 1 under every rock's centre, 0 far from all
        double texel = 0.5;
        int size = 256;
        float[] map = field.footprint(x0, z0, texel, size, size);
        assertThat(map).hasSize(size * size);
        RockInstances inside = rocks.instances();
        assertThat(inside.count()).isPositive();
        for (int i = 0; i < inside.count(); i++) {
            int col = (int) ((inside.x(i) - x0) / texel);
            int row = (int) ((inside.z(i) - z0) / texel);
            // the texel holding its centre: covered (small stones within a texel partly)
            assertThat(map[row * size + col]).isGreaterThan(Math.min(inside.halfX(i), inside.halfZ(i)) > texel
                ? 0.99f : 0f);
            assertThat(rocks.cover(inside.x(i), inside.z(i), 0.5)).isEqualTo(1);
        }
        int clear = 0;
        for (float v : map) {
            clear += v == 0 ? 1 : 0;
        }
        assertThat(clear).as("most of the meadow keeps its grass").isGreaterThan(size * size / 2);
        // active talus below the cliff: no trees or shrubs, though they grow on the cone's lower part
        RockTile cone = field.region(-64, -320, 128, 160);
        Instances below = ecology.region(-64, -320, 128, 160).instances();
        Instances unblocked = plain.region(-64, -320, 128, 160).instances();
        int woodyOnActive = 0;
        int woodyLower = 0;
        for (int i = 0; i < below.count(); i++) {
            com.truesim.common.procedural.ecology.Layer layer = ecology.species().get(below.species(i)).layer();
            boolean woody = layer == com.truesim.common.procedural.ecology.Layer.CANOPY
                || layer == com.truesim.common.procedural.ecology.Layer.SHRUB;
            if (woody && cone.activeTalus(below.x(i), below.z(i))) {
                woodyOnActive++;
            } else if (woody) {
                woodyLower++;
            }
        }
        int woodyBefore = 0;
        for (int i = 0; i < unblocked.count(); i++) {
            com.truesim.common.procedural.ecology.Layer layer = ecology.species().get(unblocked.species(i)).layer();
            if ((layer == com.truesim.common.procedural.ecology.Layer.CANOPY
                || layer == com.truesim.common.procedural.ecology.Layer.SHRUB)
                && cone.activeTalus(unblocked.x(i), unblocked.z(i))) {
                woodyBefore++;
            }
        }
        assertThat(woodyBefore).as("trees on the active talus without the rocks").isPositive();
        assertThat(woodyOnActive).isZero();
        assertThat(woodyLower).isPositive();
        assertThat(cone.activeTalus(0, FOOT + 5)).isTrue();
        assertThat(cone.activeTalus(0, TOE + 20)).isFalse();
    }

    @Test
    void boulderLineStreamsPilesEdgeMeadowsAndErraticsLieAlone() {
        // a V-shaped valley draining south along x = 0, its sides gentle enough for meadows
        EcologyTerrain valley = (x, z) -> 900 + 0.12 * Math.min(Math.abs(x), 1500) - 0.03 * z;
        RockField field = new RockField(new Ecology(valley, PARAMETERS, AlpineSpecies.ALL),
            RockFieldParameters.DEFAULTS.withKind(RockKind.ERRATIC, RockFieldParameters.KINDS
                .get(RockKind.ERRATIC.ordinal()).withDensity(0.001)));
        RockInstances rocks = field.region(-256, 0, 512, 512).instances();
        int stream = 0;
        int erratic = 0;
        for (int i = 0; i < rocks.count(); i++) {
            switch (rocks.kind(i)) {
                case STREAM -> {
                    assertThat(Math.abs(rocks.x(i))).as("a stream boulder by the stream").isLessThan(12);
                    stream++;
                }
                case ERRATIC -> erratic++;
                case TALUS, ROCKFALL -> throw new AssertionError("no cliff here");
                default -> {
                }
            }
        }
        assertThat(stream).isGreaterThan(10);
        assertThat(erratic).isGreaterThan(5);
        assertThat(field.halo()).isGreaterThan(2 * field.maxReach());
    }

    @Test
    void parametersAreChecked() {
        assertThatThrownBy(() -> new RockFieldParameters(4, RockFieldParameters.Cliffs.DEFAULTS, List.of(), 1, 0.3,
            2, 0.8, 0.8, 0.3, RockFieldParameters.Variants.DEFAULTS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RockFieldParameters.DEFAULTS.withCell(0)).isInstanceOf(
            IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockFieldParameters.KindPlacement(1, 0.1, 2, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RockFieldParameters.Variants(1, 1, 1, 1))
            .isInstanceOf(IllegalArgumentException.class);
        RockFieldParameters p = RockFieldParameters.DEFAULTS.withSorting(2).withSpread(0.2).withFractal(2.5).withTilt(0.5)
            .withPacking(1).withPlantClearance(0.1).withVariants(new RockFieldParameters.Variants(2, 2, 2, 1))
            .withCliffs(RockFieldParameters.Cliffs.DEFAULTS).withCell(8);
        assertThat(p.sorting()).isEqualTo(2);
        assertThat(p.cell()).isEqualTo(8);
        RockField field = new RockField(new Ecology(wall(), PARAMETERS, AlpineSpecies.ALL), p);
        assertThat(field.variants()).hasSize(6 * 2 * 2 * 2 * 2);
        assertThat(field.parameters()).isSameAs(p);
        assertThat(field.footprint(0).reach()).isPositive();
    }

    @Test
    void talusSizesFollowAPowerLawAndItsCoverCarriesTheScree() {
        RockField field = new RockField(new Ecology(wall(), PARAMETERS, AlpineSpecies.ALL),
            RockFieldParameters.DEFAULTS);
        RockInstances rocks = field.region(-128, -330, 256, 170).instances();
        // a power law: near the cliff's foot (the smallest size still near the kind's), each doubling of size has
        // fewer rocks than the one below
        int[] octaves = new int[3];
        double lo = RockFieldParameters.KINDS.getFirst().minSize();
        for (int i = 0; i < rocks.count(); i++) {
            if (rocks.kind(i) == RockKind.TALUS && rocks.z(i) < FOOT + 30) {
                int o = (int) Math.floor(Math.log(rocks.size(i) / lo) / Math.log(2));
                if (o >= 0 && o < 3) {
                    octaves[o]++;
                }
            }
        }
        assertThat(octaves[0]).isGreaterThan(octaves[1]);
        assertThat(octaves[1]).isGreaterThan(octaves[2]);
        assertThat(RockField.powerLaw(0.1, 1, 2, 0)).isCloseTo(0.1, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(RockField.powerLaw(0.1, 1, 2, 1)).isCloseTo(1, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(RockField.powerLaw(1, 1, 2, 0.5)).isEqualTo(1);
        // the talus' cover: full on the cone, none on the cliff, the plateau or far out on the floor
        assertThat(field.talus(0, (FOOT + TOE) / 2)).isGreaterThan(0.8);
        assertThat(field.talus(0, (TOP + FOOT) / 2)).isZero();
        assertThat(field.talus(0, TOP - 100)).isZero();
        assertThat(field.talus(0, TOE + 300)).isZero();
        // the same from a field that worked out other blocks first
        RockField other = new RockField(new Ecology(wall(), PARAMETERS, AlpineSpecies.ALL),
            RockFieldParameters.DEFAULTS);
        other.talus(500, 500);
        assertThat(other.talus(13.7, -250.2)).isEqualTo(field.talus(13.7, -250.2));
    }

    @Test
    void theScreeIsScatteredDenselyOverTheTalusByAPowerLaw() {
        RockField field = new RockField(new Ecology(wall(), PARAMETERS, AlpineSpecies.ALL),
            RockFieldParameters.DEFAULTS);
        RockScatter scatter = new RockScatter(field, RockScatter.Parameters.DEFAULTS);
        RockScatter.Tile tile = scatter.region(-20, -300, 40, 100);
        assertThat(tile.cells()).isPositive();
        double expected = 0;
        for (int c = 0; c < tile.cells(); c++) {
            double cell = scatter.parameters().cell();
            expected += scatter.parameters().density() * cell * cell * tile.cover(c);
            assertThat(tile.end(c)).isGreaterThanOrEqualTo(tile.first(c));
            assertThat(tile.patches().x(c)).isEqualTo(tile.cellX(c));
        }
        RockScatter.Placed stones = tile.stones();
        assertThat((double) stones.count()).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.1 * expected));
        assertThat(stones.count() / (40.0 * 100)).as("stones per square metre").isGreaterThan(8);
        int small = 0;
        int large = 0;
        double mid = Math.sqrt(scatter.parameters().minSize() * scatter.parameters().maxSize());
        for (int i = 0; i < stones.count(); i++) {
            double size = stones.scale(i) * com.truesim.common.procedural.rock.RockParams.SCREE.size();
            assertThat(size).isBetween(scatter.parameters().minSize() - 1e-6, scatter.parameters().maxSize() + 1e-6);
            if (size < mid) {
                small++;
            } else {
                large++;
            }
            assertThat(stones.y(i)).isCloseTo(wall().height(stones.x(i), stones.z(i)),
                org.assertj.core.data.Offset.offset(1e-9));
            double q = stones.q(i, 0) * stones.q(i, 0) + stones.q(i, 1) * stones.q(i, 1) + stones.q(i, 2)
                * stones.q(i, 2) + stones.q(i, 3) * stones.q(i, 3);
            assertThat(q).isCloseTo(1, org.assertj.core.data.Offset.offset(1e-5));
        }
        assertThat(small).isGreaterThan(3 * large);
        // nothing off the talus; the same stones whatever the region
        assertThat(scatter.region(-20, TOE + 200, 40, 40).cells()).isZero();
        RockScatter.Tile half = scatter.region(-20, -300, 20, 100);
        assertThat(half.stones().x(0)).isEqualTo(stones.x(0));
        assertThat(scatter.stoneVariants()).hasSize(8);
        assertThat(scatter.patchVariants()).hasSize(8);
        assertThat(RockScatter.poisson(0, 0.5)).isZero();
        assertThatThrownBy(() -> new RockScatter.Parameters(0, 1, 0.1, 0.2, 2, 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noTreesOnTheCliffFace() {
        // the wall as a terrain that calls all its ground soil (as a terrain's grass layer might on a steep face)
        EcologyTerrain wall = wall();
        EcologyTerrain soil = new EcologyTerrain() {
            @Override
            public double altitude(double x, double z) {
                return wall.altitude(x, z);
            }

            @Override
            public boolean ground(double x, double z, double[] fractions) {
                java.util.Arrays.fill(fractions, 0);
                fractions[com.truesim.common.procedural.ecology.Ground.SOIL] = 1;
                return true;
            }
        };
        Ecology limited = new Ecology(soil, PARAMETERS, AlpineSpecies.ALL);
        Ecology anywhere = new Ecology(soil, PARAMETERS.withWoody(new EcologyParameters.Woody(90, 0)),
            AlpineSpecies.ALL);
        int[] on = woodyOnFace(limited);
        int[] free = woodyOnFace(anywhere);
        assertThat(free[0]).as("woody plants on the face without the limit").isPositive();
        assertThat(on[0]).as("woody plants on the face").isZero();
        assertThat(on[1]).as("woody plants below it").isPositive();
        assertThat(limited.conditions(0, (TOP + FOOT) / 2).soilDepth).isLessThan(0.05);
        assertThat(limited.conditions(0, TOE + 100).soilDepth).isGreaterThan(0.5);
    }

    /**
     * Woody plants on the wall's face (more than 8 m from its top and foot: the fields are sampled every 4 m, so the
     * limit blurs at the face's edges, where ledges would hold trees), and elsewhere.
     */
    private static int[] woodyOnFace(Ecology ecology) {
        Instances plants = ecology.region(-64, -400, 128, 128).instances();
        int[] out = new int[2];
        for (int i = 0; i < plants.count(); i++) {
            com.truesim.common.procedural.ecology.Layer layer = ecology.species().get(plants.species(i)).layer();
            if (layer != com.truesim.common.procedural.ecology.Layer.CANOPY
                && layer != com.truesim.common.procedural.ecology.Layer.SHRUB) {
                continue;
            }
            boolean face = plants.z(i) > TOP + 8 && plants.z(i) < FOOT - 8;
            out[face ? 0 : 1]++;
        }
        return out;
    }

    private static double meanLichen(RockInstances rocks, RockKind kind, double z0, double z1) {
        double sum = 0;
        int n = 0;
        for (int i = 0; i < rocks.count(); i++) {
            if (rocks.kind(i) == kind && rocks.z(i) >= z0 && rocks.z(i) < z1) {
                sum += rocks.variant(i).cover().lichen();
                n++;
            }
        }
        return sum / Math.max(1, n);
    }

    private static void describe(RockInstances rocks, List<String> out) {
        for (int i = 0; i < rocks.count(); i++) {
            out.add(rocks.x(i) + " " + rocks.y(i) + " " + rocks.z(i) + " " + rocks.scale(i) + " "
                + rocks.variantIndex(i) + " " + rocks.qw(i));
        }
    }
}
