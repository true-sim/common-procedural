package com.truesim.common.procedural.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Vec3;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.math.random.Hash;
import com.truesim.common.procedural.flora.FloraSummary;
import com.truesim.common.procedural.flora.Fruiting;
import com.truesim.common.procedural.flora.Inflorescence;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Flowers, fruits and cones on trees and shrubs: deterministic, placed by their species' rules, in the state their
 * day says, swaying with their branch, within the levels' budgets.
 */
class AttachmentTest {

    static List<TreeSpecies> bearing() {
        return TreeSpecies.PRESETS.stream().filter(s -> s.fruiting() != null).toList();
    }

    private static byte[] bytes(ProceduralMesh mesh) {
        return mesh.vertices().segment().toArray(ValueLayout.JAVA_BYTE);
    }

    private static int attachmentTriangles(ProceduralMesh mesh) {
        return (mesh.submeshEnd(Tree.ATTACHMENTS) - mesh.submeshStart(Tree.ATTACHMENTS)) / 3;
    }

    private static TreeOptions bloom(TreeSpecies species) {
        return TreeOptions.DEFAULTS.withDay(species.fruiting().phenology().peakBloom());
    }

    private static TreeOptions ripe(TreeSpecies species) {
        return TreeOptions.DEFAULTS.withDay(species.fruiting().phenology().ripe());
    }

    @ParameterizedTest
    @MethodSource("bearing")
    void theSameSeedAndDayGiveBitIdenticalAttachments(TreeSpecies species) throws Exception {
        TreeGenerator generator = new TreeGenerator();
        Tree first = generator.generate(species, bloom(species), 42);
        generator.generate(species, ripe(species), 7); // another tree and day in between
        Tree again = generator.generate(species, bloom(species), 42);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Tree> other = executor.submit(() -> Trees.generate(species, bloom(species), 42));
            for (Tree t : List.of(again, other.get())) {
                for (int l = 0; l < first.lods().size(); l++) {
                    assertThat(bytes(t.lods().get(l))).isEqualTo(bytes(first.lods().get(l)));
                    assertThat(t.lods().get(l).indices()).isEqualTo(first.lods().get(l).indices());
                    assertThat(t.lods().get(l).submeshes()).isEqualTo(first.lods().get(l).submeshes());
                }
                assertThat(t.flora()).isEqualTo(first.flora());
                assertThat(t.impostor()).isEqualTo(first.impostor());
            }
        }
        assertThat(attachmentTriangles(first.lods().getFirst())).isPositive();
        Tree different = generator.generate(species, bloom(species), 43);
        assertThat(bytes(different.lods().getFirst())).isNotEqualTo(bytes(first.lods().getFirst()));
    }

    @ParameterizedTest
    @MethodSource("bearing")
    void sitesFollowTheirSpeciesRules(TreeSpecies species) {
        TreeGenerator generator = new TreeGenerator();
        Tree tree = generator.generate(species, bloom(species), 5);
        Skeleton skeleton = generator.skeleton();
        Attachments sites = generator.attachments();
        Fruiting fruiting = species.fruiting();
        assertThat(sites.count()).isPositive();
        int[] lastNode = new int[skeleton.nodeCount()];
        Arrays.fill(lastNode, -1);
        int tips = 0;
        for (int b = 0; b < skeleton.branchCount(); b++) {
            int last = skeleton.firstNode(b) + skeleton.nodeCount(b) - 1;
            lastNode[last] = b;
            tips += skeleton.level(b) >= fruiting.fromLevel() && skeleton.nodeCount(b) > 1 ? 1 : 0;
        }
        for (int i = 0; i < sites.count(); i++) {
            int node = sites.node(i);
            int branch = skeleton.branchOf(node);
            assertThat(skeleton.level(branch)).isGreaterThanOrEqualTo(fruiting.fromLevel());
            assertThat(sites.position(i).y()).isGreaterThanOrEqualTo(fruiting.minHeight() * tree.height() * 0.85);
            assertThat(sites.direction(i).length()).isCloseTo(1, within(1e-9));
            if (fruiting.placement() == Fruiting.Placement.TIPS) {
                // at the twig's very tip
                assertThat(lastNode[node]).isEqualTo(branch);
                assertThat(sites.position(i)).isEqualTo(Axes.position(skeleton, node));
            } else {
                // along the outer part of the shoot
                double length = Axes.length(skeleton, branch);
                double along = 0;
                for (int n = skeleton.firstNode(branch) + 1; n <= node; n++) {
                    along += Axes.distance(skeleton, n, n - 1);
                }
                double segment = length / Math.max(skeleton.nodeCount(branch) - 1, 1);
                assertThat(along).isGreaterThanOrEqualTo(length * (1 - fruiting.tipFraction()) - segment - 1e-9);
            }
        }
        if (fruiting.placement() == Fruiting.Placement.TIPS && fruiting.minHeight() == 0) {
            // about density of the twig tips bear
            double expected = fruiting.density() * tips;
            assertThat((double) sites.count()).isBetween(expected - 4 * Math.sqrt(expected) - 1,
                expected + 4 * Math.sqrt(expected) + 1);
        }
    }

    @ParameterizedTest
    @MethodSource("bearing")
    void headsHaveTheirFlowerCountsAndStayAroundTheirSite(TreeSpecies species) {
        TreeGenerator generator = new TreeGenerator();
        Tree tree = generator.generate(species, bloom(species), 9);
        Attachments sites = generator.attachments();
        Inflorescence inflorescence = species.fruiting().inflorescence();
        int total = 0;
        for (int i = 0; i < 200; i++) {
            int n = Attachments.flowers(inflorescence, Hash.pcg(i));
            assertThat(n % inflorescence.florets()).isZero();
            assertThat(n / inflorescence.florets()).isBetween(inflorescence.minFlowers(), inflorescence.maxFlowers());
            total += n;
        }
        assertThat(total).isPositive();
        // every flower, fruit and stalk lies within its head's reach of a site
        Fruiting fruiting = species.fruiting();
        double reach = inflorescence.peduncle() + 1.2 * inflorescence.pedicel()
            + (inflorescence.arrangement() == Inflorescence.Arrangement.RACEME
                || inflorescence.arrangement() == Inflorescence.Arrangement.CORYMB
                || inflorescence.arrangement() == Inflorescence.Arrangement.UMBEL ? inflorescence.spread() : 0)
            + 2 * fruiting.fruit().halfLength() * (1 + fruiting.fruit().sizeVariation())
            + (fruiting.flower() == null ? 0 : 1.2 * fruiting.flower().reach())
            + 2 * 1.7 * Math.max(fruiting.flower() == null ? 0 : fruiting.flower().reach(),
                1.1 * fruiting.fruit().radius()) + 0.002;
        ProceduralMesh mesh = tree.lods().getFirst();
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        for (int t = mesh.submeshStart(Tree.ATTACHMENTS); t < mesh.submeshEnd(Tree.ATTACHMENTS); t += 7) {
            Vec3 p = position.get(mesh.indices()[t]);
            double nearest = Double.MAX_VALUE;
            for (int i = 0; i < sites.count(); i++) {
                nearest = Math.min(nearest, p.distance(sites.position(i)));
            }
            assertThat(nearest).isLessThan(reach);
        }
        FloraSummary flora = tree.flora();
        assertThat(flora.shown()).isPositive();
    }

    @Test
    void rowanFlowersInJuneAndBearsRedBerriesInAutumn() {
        TreeGenerator generator = new TreeGenerator();
        FloraSummary april = generator.generate(TreeSpecies.ROWAN, TreeOptions.DEFAULTS.withDay(100), 3).flora();
        assertThat(april.shown()).isZero();
        Tree june = generator.generate(TreeSpecies.ROWAN, TreeOptions.DEFAULTS.withDay(162), 3);
        assertThat(june.flora().flowers()).isGreaterThan(june.flora().fruits());
        assertThat(june.flora().albedo().mean()).isGreaterThan(0.4); // white blossom
        assertPartsWithin(june.lods().getFirst(), Set.of(Part.PETAL, Part.CENTRE, Part.SEPAL, Part.STALK, Part.FRUIT));
        Tree september = generator.generate(TreeSpecies.ROWAN, TreeOptions.DEFAULTS.withDay(255), 3);
        FloraSummary fruit = september.flora();
        assertThat(fruit.flowers() + fruit.buds()).isZero();
        assertThat(fruit.fruits()).isPositive();
        assertThat(fruit.albedo().r()).isGreaterThan(5 * fruit.albedo().g()); // scarlet
        assertPartsWithin(september.lods().getFirst(), Set.of(Part.FRUIT, Part.STALK, Part.SEPAL));
        // ripe: the fruits' u at the ramp's end
        ProceduralMesh mesh = september.lods().getFirst();
        Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
        double ripest = 0;
        for (int t = mesh.submeshStart(Tree.ATTACHMENTS); t < mesh.submeshEnd(Tree.ATTACHMENTS); t++) {
            int v = mesh.indices()[t];
            if (Palette.part(uv.y(v)) == Part.FRUIT) {
                ripest = Math.max(ripest, Palette.t(uv.x(v)));
            }
        }
        assertThat(ripest).isGreaterThan(0.95);
        // the impostor hint follows: red, and some of the crown
        assertThat(september.impostor().bloom()).isEqualTo(fruit.albedo());
        assertThat(september.impostor().bloomCoverage()).isBetween(1e-4, 1.0);
        // birds leave some into the winter, the summer's are gone by the next flowering
        assertThat(generator.generate(TreeSpecies.ROWAN, TreeOptions.DEFAULTS.withDay(20), 3).flora().fruits())
            .isPositive().isLessThan(fruit.fruits());
        assertThat(generator.generate(TreeSpecies.ROWAN, TreeOptions.DEFAULTS.withDay(60), 3).flora().shown())
            .isZero();
    }

    @Test
    void applesBlossomPinkInBudThenFruitAndDropByNovember() {
        TreeGenerator generator = new TreeGenerator();
        FloraSummary early = generator.generate(TreeSpecies.APPLE, TreeOptions.DEFAULTS.withDay(112), 1).flora();
        assertThat(early.buds()).isPositive();
        assertThat(early.albedo().r()).isGreaterThan(2 * early.albedo().g()); // pink buds
        FloraSummary bloom = generator.generate(TreeSpecies.APPLE, TreeOptions.DEFAULTS.withDay(130), 1).flora();
        assertThat(bloom.flowers()).isGreaterThan(bloom.buds());
        Tree autumn = generator.generate(TreeSpecies.APPLE, TreeOptions.DEFAULTS.withDay(256), 1);
        int flowersPerTree = 0;
        Attachments sites = generator.attachments();
        assertThat(sites.count()).isPositive();
        // about the fruit set: a fifth of the flowers, give or take
        int fruits = autumn.flora().fruits();
        for (int i = 0; i < sites.count(); i++) {
            flowersPerTree += Attachments.flowers(TreeSpecies.APPLE.fruiting().inflorescence(), sites.hash(i));
        }
        double expected = TreeSpecies.APPLE.fruiting().fruitSet() * flowersPerTree;
        assertThat((double) fruits).isBetween(expected - 4 * Math.sqrt(expected), expected + 4 * Math.sqrt(expected));
        assertThat(generator.generate(TreeSpecies.APPLE, TreeOptions.DEFAULTS.withDay(310), 1).flora().shown())
            .isZero();
        // the crop bows the boughs: the same tree's leaves hang lower in fruit than before it
        double before = leafHeight(generator.generate(TreeSpecies.APPLE, TreeOptions.DEFAULTS.withDay(100), 1));
        double laden = leafHeight(autumn);
        assertThat(laden).isLessThan(before - 0.01);
        // the two cultivars
        Set<Part> parts = new HashSet<>();
        for (long seed = 0; seed < 6; seed++) {
            parts.addAll(parts(Trees.generate(TreeSpecies.APPLE, TreeOptions.DEFAULTS.withDay(256), seed)
                .lods().getFirst()));
        }
        assertThat(parts).contains(Part.FRUIT, Part.FRUIT_ALT);
    }

    @Test
    void lingonberriesAndHipsHangThroughTheWinterUnderSnow() {
        Tree lingonberry = Trees.generate(TreeSpecies.LINGONBERRY, TreeOptions.DEFAULTS.withDay(30).withSnowLoad(1), 2);
        assertThat(lingonberry.flora().fruits()).isPositive();
        assertThat(lingonberry.flora().flowers()).isZero();
        Tree rose = Trees.generate(TreeSpecies.DOG_ROSE, TreeOptions.DEFAULTS.withSeason(Season.WINTER), 2);
        assertThat(rose.flora().fruits()).isPositive();
        // deciduous: bare, the hips still there
        ProceduralMesh mesh = rose.lods().getFirst();
        assertThat(mesh.submeshEnd(Tree.LEAVES) - mesh.submeshStart(Tree.LEAVES)).isZero();
        assertThat(attachmentTriangles(mesh)).isPositive();
        // elderberries do not
        assertThat(Trees.generate(TreeSpecies.ELDER, TreeOptions.DEFAULTS.withSeason(Season.WINTER), 2).flora()
            .shown()).isZero();
        // spruce: young red cones in spring, brown ones in autumn
        FloraSummary spring = Trees.generate(TreeSpecies.SPRUCE, TreeOptions.DEFAULTS.withDay(140), 2).flora();
        FloraSummary autumn = Trees.generate(TreeSpecies.SPRUCE, TreeOptions.DEFAULTS.withDay(300), 2).flora();
        assertThat(spring.shown()).isPositive();
        assertThat(spring.albedo().r()).isGreaterThan(2 * spring.albedo().g());
        assertThat(autumn.fruits()).isPositive();
        assertThat(autumn.albedo().r()).isGreaterThan(autumn.albedo().b());
    }

    @ParameterizedTest
    @MethodSource("bearing")
    void attachmentsSwayWithTheirBranch(TreeSpecies species) {
        TreeGenerator generator = new TreeGenerator();
        Tree tree = generator.generate(species, ripe(species), 21);
        Attachments sites = generator.attachments();
        TreeWind wind = generator.wind();
        // the wind of every site's node, as the format writes it
        MeshWriter writer = new MeshWriter();
        for (int i = 0; i < sites.count(); i++) {
            wind.applyNode(writer, sites.node(i), 0);
            writer.vertex(0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0);
        }
        ProceduralMesh nodes = writer.build(0);
        Set<List<Double>> allowed = new HashSet<>();
        Vec4Attribute nodeWind = nodes.vertices().attribute(ProceduralVertex.WIND);
        Vec2Attribute nodePivot = nodes.vertices().attribute(ProceduralVertex.WIND_PIVOT);
        for (int i = 0; i < nodes.vertexCount(); i++) {
            allowed.add(key(nodeWind.x(i), nodeWind.y(i), ProceduralVertex.phase(nodeWind.z(i)), nodePivot.x(i),
                nodePivot.y(i)));
        }
        ProceduralMesh mesh = tree.lods().getFirst();
        Vec4Attribute w = mesh.vertices().attribute(ProceduralVertex.WIND);
        Vec2Attribute pivot = mesh.vertices().attribute(ProceduralVertex.WIND_PIVOT);
        Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
        int checked = 0;
        for (int t = mesh.submeshStart(Tree.ATTACHMENTS); t < mesh.submeshEnd(Tree.ATTACHMENTS); t++) {
            int v = mesh.indices()[t];
            // trunk weight, branch weight, phase and pivot: its node's, so it moves with its branch rigidly
            assertThat(allowed).contains(key(w.x(v), w.y(v), ProceduralVertex.phase(w.z(v)), pivot.x(v),
                pivot.y(v)));
            assertThat(w.w(v)).isEqualTo(1);
            double leaf = ProceduralVertex.leaf(w.z(v));
            Part part = Palette.part(uv.y(v));
            if (part == Part.FRUIT || part == Part.FRUIT_ALT || part == Part.CONE || part == Part.STALK) {
                assertThat(leaf).isZero(); // solid parts do not flutter along their normals
            } else {
                assertThat(leaf).isLessThanOrEqualTo(0.6 + 1 / 30.0);
            }
            checked++;
        }
        assertThat(checked).isPositive();
    }

    private static List<Double> key(double trunk, double branch, double phase, double pivot, double reach) {
        return List.of(trunk, branch, phase, pivot, reach);
    }

    @ParameterizedTest
    @MethodSource("bearing")
    void levelsKeepTheirBudgetsWithFlowersAndFruits(TreeSpecies species) {
        for (TreeOptions options : List.of(bloom(species), ripe(species))) {
            Tree tree = Trees.generate(species, options, 4);
            List<ProceduralMesh> lods = tree.lods();
            for (int l = 0; l < lods.size(); l++) {
                ProceduralMesh mesh = lods.get(l);
                assertThat(mesh.triangleCount()).isLessThanOrEqualTo(options.lods().get(l).triangleBudget());
                assertThat(mesh.submeshCount()).isEqualTo(3);
                if (l > 0) {
                    assertThat(attachmentTriangles(mesh)).isLessThanOrEqualTo(attachmentTriangles(lods.get(l - 1)));
                    assertThat(mesh.error()).isGreaterThanOrEqualTo(lods.get(l - 1).error());
                }
            }
        }
        // a tight budget simplifies flowers and fruits before anything else, then drops them
        TreeOptions options = bloom(species);
        int full = attachmentTriangles(Trees.generate(species, options.withLods(List.of(new TreeLod(10, 6, 3, 0.008,
            1, 1, 2_000_000, 0))), 4).lods().getFirst());
        int wood = Trees.generate(species, options.withLods(List.of(new TreeLod(10, 6, 3, 0.008, 1, 1, 2_000_000,
            TreeLod.NO_ATTACHMENTS))), 4).lods().getFirst().triangleCount();
        Tree tight = Trees.generate(species, options.withLods(List.of(new TreeLod(10, 6, 3, 0.008, 1, 1,
            wood + full / 3 + 1, 0))), 4);
        assertThat(tight.lods().getFirst().triangleCount()).isLessThanOrEqualTo(wood + full / 3 + 1);
        assertThat(attachmentTriangles(tight.lods().getFirst())).isLessThan(full);
        assertThat(tight.lods().getFirst().error()).isPositive();
    }

    private static double leafHeight(Tree tree) {
        ProceduralMesh mesh = tree.lods().getFirst();
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        double sum = 0;
        int count = 0;
        for (int i = mesh.submeshStart(Tree.LEAVES); i < mesh.submeshEnd(Tree.LEAVES); i++) {
            sum += position.y(mesh.indices()[i]);
            count++;
        }
        return sum / count;
    }

    private static Set<Part> parts(ProceduralMesh mesh) {
        Set<Part> parts = new HashSet<>();
        Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
        for (int t = mesh.submeshStart(Tree.ATTACHMENTS); t < mesh.submeshEnd(Tree.ATTACHMENTS); t++) {
            parts.add(Palette.part(uv.y(mesh.indices()[t])));
        }
        return parts;
    }

    private static void assertPartsWithin(ProceduralMesh mesh, Set<Part> allowed) {
        assertThat(allowed).containsAll(parts(mesh));
    }

    @Test
    void seasonsMapToDaysAndBack() {
        for (Season season : Season.values()) {
            assertThat(Season.of(season.day())).isEqualTo(season);
        }
        assertThat(Season.of(-10)).isEqualTo(Season.WINTER);
        assertThat(Season.of(500)).isEqualTo(Season.SPRING);
        assertThat(TreeOptions.DEFAULTS.withDay(250).season()).isEqualTo(Season.SUMMER);
        assertThat(TreeOptions.DEFAULTS.withDay(280).season()).isEqualTo(Season.AUTUMN);
        assertThat(TreeOptions.DEFAULTS.withSeason(Season.SPRING).day()).isEqualTo(135);
        assertThat(new TreeLod(3, 3, 3, 0, 1, 1, 1).attachmentDetail()).isZero();
        assertThat(TreeSpecies.BEECH.withFruiting(TreeSpecies.ROWAN.fruiting()).fruiting())
            .isSameAs(TreeSpecies.ROWAN.fruiting());
        assertThat(Trees.generate(TreeSpecies.BEECH, 1).flora()).isEqualTo(FloraSummary.NONE);
        assertThat(Trees.generate(TreeSpecies.BEECH, 1).impostor().bloomCoverage()).isZero();
    }

    /**
     * Seen from 40 m, as a real tree is (a rowan in fruit covered in red bunches, an apple tree showing its apples, a
     * dog rose its pink flowers): the share of the crown's pixels that are its flowers or fruit, and for fruit, red.
     * Drawn by the CPU rasterizer at 1280 x 720, 34 degrees high, leaf cards cut by their masks.
     */
    @Test
    void flowersAndFruitShowFromFortyMetres() {
        long red = 0;
        while ((com.truesim.common.procedural.Seeds.derive(red, 4) & 1) != 0) {
            red++; // an apple of the red cultivar
        }
        assertShows(TreeSpecies.ROWAN, 240, 7, 0.08, true);
        assertShows(TreeSpecies.ROWAN, 160, 7, 0.08, false);
        assertShows(TreeSpecies.APPLE, 250, red, 0.035, true);
        assertShows(TreeSpecies.APPLE, 130, red, 0.05, false);
        assertShows(TreeSpecies.DOG_ROSE, 172, 7, 0.04, false);
        assertShows(TreeSpecies.DOG_ROSE, 280, 7, 0.02, true);
    }

    private static void assertShows(TreeSpecies species, double day, long seed, double share, boolean red) {
        Tree tree = Trees.generate(species, TreeOptions.DEFAULTS.withDay(day), seed);
        double h = tree.height();
        com.truesim.common.procedural.SoftwareRender r = new com.truesim.common.procedural.SoftwareRender(1280, 720);
        r.camera(0, 0.5 * h, 40, 0, 0.5 * h, 0, 0.6);
        r.draw(tree.lods().getFirst(), 0, 0, 0, null, tree.palette(), Tree.ATTACHMENTS);
        int crown = r.covered();
        int shown = 0;
        for (int y = 0; y < 720; y++) {
            for (int x = 0; x < 1280; x++) {
                if (r.tag(x, y) != Tree.ATTACHMENTS + 1) {
                    continue;
                }
                int c = r.rgb(x, y);
                int rr = c >> 16 & 255;
                int g = c >> 8 & 255;
                int b = c & 255;
                shown += !red || (rr > 1.6 * g && rr > 1.6 * b) ? 1 : 0;
            }
        }
        assertThat((double) shown / crown).as("%s on day %s", species.name(), day).isGreaterThan(share);
    }
}
