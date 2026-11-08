package com.truesim.common.procedural.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Maths;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Meshes;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/** Procedural trees: determinism, the pipe model, closed branches, budgets, wind data, seasons and snow. */
class TreeTest {

    static List<TreeSpecies> species() {
        return TreeSpecies.PRESETS;
    }

    private static byte[] bytes(ProceduralMesh mesh) {
        return mesh.vertices().segment().toArray(ValueLayout.JAVA_BYTE);
    }

    private static void assertIdentical(Tree a, Tree b) {
        assertThat(a.lods()).hasSameSizeAs(b.lods());
        for (int l = 0; l < a.lods().size(); l++) {
            assertThat(bytes(a.lods().get(l))).isEqualTo(bytes(b.lods().get(l)));
            assertThat(a.lods().get(l).indices()).isEqualTo(b.lods().get(l).indices());
            assertThat(a.lods().get(l).submeshes()).isEqualTo(b.lods().get(l).submeshes());
        }
        assertThat(bytes(a.billboard())).isEqualTo(bytes(b.billboard()));
        assertThat(a.impostor()).isEqualTo(b.impostor());
        assertThat(a.height()).isEqualTo(b.height());
    }

    @ParameterizedTest
    @MethodSource("species")
    void theSameSeedGivesBitIdenticalMeshes(TreeSpecies species) throws Exception {
        TreeGenerator generator = new TreeGenerator();
        Tree first = generator.generate(species, 42);
        generator.generate(species, 7); // a different tree in between: no state leaks from tree to tree
        assertIdentical(first, generator.generate(species, 42));
        // on another thread, with a fresh generator, through the facade
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Tree> other = executor.submit(() -> Trees.generate(species, 42));
            assertIdentical(first, other.get());
        }
        Tree different = generator.generate(species, 43);
        assertThat(bytes(different.lods().getFirst())).isNotEqualTo(bytes(first.lods().getFirst()));
    }

    @ParameterizedTest
    @MethodSource("species")
    void radiiFollowThePipeModel(TreeSpecies species) {
        TreeGenerator generator = new TreeGenerator();
        generator.generate(species, 3);
        Skeleton skeleton = generator.skeleton();
        double e = skeleton.pipeExponent();
        assertThat(e).isEqualTo(species.pipeExponent());
        double[] carried = new double[skeleton.nodeCount()];
        for (int n = 0; n < skeleton.nodeCount(); n++) {
            carried[n] = skeleton.leafPipes(n);
        }
        for (int n = 0; n < skeleton.nodeCount(); n++) {
            if (skeleton.parent(n) >= 0) {
                carried[skeleton.parent(n)] += Maths.pow(skeleton.radius(n), e);
            }
        }
        for (int n = 0; n < skeleton.nodeCount(); n++) {
            double own = Maths.pow(skeleton.radius(n), e);
            // r^e of a node = the sum of its children's r^e plus its leaves' pipes
            assertThat(own).isCloseTo(carried[n], within(1e-9 * Math.max(own, 1e-12)));
            if (skeleton.parent(n) >= 0) {
                assertThat(skeleton.radius(n)).isLessThanOrEqualTo(skeleton.radius(skeleton.parent(n)) * (1 + 1e-12));
            }
        }
        // the first trunk has the species' base radius, scaled by the tree's height
        double expected = species.baseRadius();
        assertThat(skeleton.radius(0)).isBetween(expected * (1 - species.heightVariation()) - 1e-9,
            expected * (1 + species.heightVariation()) + 1e-9);
        assertThat(skeleton.leafPipe()).isPositive();
    }

    @ParameterizedTest
    @MethodSource("species")
    void everyBranchIsAClosedSurfaceAtEveryLevel(TreeSpecies species) {
        Tree tree = Trees.generate(species, 11);
        for (ProceduralMesh mesh : tree.lods()) {
            int[] canonical = new int[mesh.vertexCount()];
            Meshes.weld(mesh.positions(), mesh.vertexCount(), canonical);
            int bark = mesh.submeshEnd(Tree.BARK);
            assertThat(bark).isPositive();
            assertThat(Meshes.openEdges(Arrays.copyOf(mesh.indices(), bark), bark, canonical)).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("species")
    void levelsKeepTheirBudgetsAndCoarsen(TreeSpecies species) {
        TreeOptions options = TreeOptions.DEFAULTS;
        Tree tree = Trees.generate(species, 5);
        List<ProceduralMesh> lods = tree.lods();
        assertThat(lods).hasSize(options.lods().size());
        for (int l = 0; l < lods.size(); l++) {
            ProceduralMesh mesh = lods.get(l);
            assertThat(mesh.triangleCount()).isLessThanOrEqualTo(options.lods().get(l).triangleBudget());
            assertThat(mesh.submeshCount()).isEqualTo(2);
            if (l > 0) {
                assertThat(mesh.triangleCount()).isLessThan(lods.get(l - 1).triangleCount());
                assertThat(mesh.error()).isGreaterThanOrEqualTo(lods.get(l - 1).error());
            }
        }
        assertThat(tree.billboard().triangleCount()).isEqualTo(6);
        assertThat(tree.billboard().error()).isGreaterThan(0);
        // the far level is a small fraction of the near one
        assertThat(lods.getLast().triangleCount()).isLessThan(lods.getFirst().triangleCount() / 4);
    }

    @Test
    void aTightBudgetDropsLeavesAndTwigsUntilItFits() {
        TreeLod tight = new TreeLod(5, 3, 3, 0.01, 1, 1, 3000);
        Tree tree = Trees.generate(TreeSpecies.SPRUCE, TreeOptions.DEFAULTS.withLods(List.of(tight)), 1);
        assertThat(tree.lods().getFirst().triangleCount()).isLessThanOrEqualTo(3000);
        assertThat(tree.lods().getFirst().error()).isGreaterThan(0.02);
    }

    @ParameterizedTest
    @MethodSource("species")
    void windDataIsInRangeAndFollowsTheHierarchy(TreeSpecies species) {
        TreeGenerator generator = new TreeGenerator();
        Tree tree = generator.generate(species, 21);
        ProceduralMesh mesh = tree.lods().getFirst();
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        Vec4Attribute wind = mesh.vertices().attribute(ProceduralVertex.WIND);
        Vec2Attribute pivot = mesh.vertices().attribute(ProceduralVertex.WIND_PIVOT);
        boolean[] leafVertex = new boolean[mesh.vertexCount()];
        for (int i = mesh.submeshStart(Tree.LEAVES); i < mesh.submeshEnd(Tree.LEAVES); i++) {
            leafVertex[mesh.indices()[i]] = true;
        }
        int trunkVertices = 0;
        int branchVertices = 0;
        double flutter = species.foliage().flutter();
        for (int i = 0; i < mesh.vertexCount(); i++) {
            double trunk = wind.x(i);
            double branch = wind.y(i);
            double leaf = ProceduralVertex.leaf(wind.z(i));
            double phase = ProceduralVertex.phase(wind.z(i));
            assertThat(wind.w(i)).isEqualTo(1); // alpha masking multiplies by it
            for (double w : new double[] {trunk, branch, leaf, phase}) {
                assertThat(w).isBetween(0.0, 1.0);
            }
            assertThat(pivot.x(i)).isBetween(-0.01, tree.height() + 0.01);
            assertThat(pivot.y(i)).isPositive();
            if (leafVertex[i]) {
                assertThat(leaf).isLessThanOrEqualTo(flutter + 1 / 30.0);
                continue;
            }
            assertThat(leaf).isZero(); // wood does not flutter
            if (pivot.x(i) == 0 && branch == 0) {
                // the trunk: (h / height)^1.5 of its stem, its reach the stem's height
                trunkVertices++;
                double h = Math.clamp(position.y(i) / pivot.y(i), 0, 1);
                assertThat(trunk).isCloseTo(h * Math.sqrt(h), within(0.03 + 0.05 * species.rootFlare()));
            } else {
                branchVertices++;
                assertThat(pivot.x(i)).isGreaterThan(0);
                assertThat(pivot.y(i)).isLessThan(tree.height() * 1.5);
            }
        }
        assertThat(trunkVertices).isPositive();
        assertThat(branchVertices).isPositive();
        // leaves: 0 where attached, the species' flutter at the far edge
        int i = mesh.indices()[mesh.submeshStart(Tree.LEAVES)];
        assertThat(ProceduralVertex.leaf(wind.z(i))).isZero();
        // main branches' pivots sit near the y axis, as the format's (0, pivot height, 0) assumes
        Skeleton skeleton = generator.skeleton();
        double worst = 0;
        for (int b = 0; b < skeleton.branchCount(); b++) {
            if (skeleton.level(b) == 1 && species.stems() == 1) {
                int p = skeleton.parent(skeleton.firstNode(b));
                worst = Math.max(worst, Math.hypot(skeleton.x(p), skeleton.z(p)));
            }
        }
        assertThat(worst).isLessThan(0.05 * tree.height());
    }

    @Test
    void branchWeightsRiseAlongBranchesAndPhasesDifferBetweenThem() {
        Tree tree = Trees.generate(TreeSpecies.FIR, 9);
        ProceduralMesh mesh = tree.lods().getFirst();
        Vec4Attribute wind = mesh.vertices().attribute(ProceduralVertex.WIND);
        double[] phases = new double[mesh.vertexCount()];
        int distinct = 0;
        double maxBranch = 0;
        for (int i = 0; i < mesh.vertexCount(); i++) {
            phases[i] = ProceduralVertex.phase(wind.z(i));
            maxBranch = Math.max(maxBranch, wind.y(i));
        }
        Arrays.sort(phases);
        for (int i = 1; i < phases.length; i++) {
            distinct += phases[i] != phases[i - 1] ? 1 : 0;
        }
        assertThat(distinct).isEqualTo(15); // all 16 phases occur
        assertThat(maxBranch).isGreaterThan(0.9);
    }

    @Test
    void deciduousTreesAreBareInWinterAndSeasonsPickTheirTile() {
        Tree summer = Trees.generate(TreeSpecies.LARCH, 4);
        Tree autumn = Trees.generate(TreeSpecies.LARCH, TreeOptions.DEFAULTS.withSeason(Season.AUTUMN), 4);
        Tree winter = Trees.generate(TreeSpecies.LARCH, TreeOptions.DEFAULTS.withSeason(Season.WINTER), 4);
        ProceduralMesh bare = winter.lods().getFirst();
        assertThat(bare.submeshEnd(Tree.LEAVES) - bare.submeshStart(Tree.LEAVES)).isZero();
        // the wood is the same in every season
        ProceduralMesh full = summer.lods().getFirst();
        assertThat(Arrays.copyOf(full.indices(), full.submeshEnd(Tree.BARK)))
            .isEqualTo(Arrays.copyOf(bare.indices(), bare.submeshEnd(Tree.BARK)));
        // summer is tile 1 (right, top) of the 2 x 2 atlas, autumn tile 2 (left, bottom)
        assertLeafUvsWithin(full, 0.5, 1, 0, 0.5);
        assertLeafUvsWithin(autumn.lods().getFirst(), 0, 0.5, 0.5, 1);
        // evergreens keep their needles in winter
        ProceduralMesh spruce = Trees.generate(TreeSpecies.SPRUCE, TreeOptions.DEFAULTS.withSeason(Season.WINTER), 4)
            .lods().getFirst();
        assertThat(spruce.submeshEnd(Tree.LEAVES)).isGreaterThan(spruce.submeshStart(Tree.LEAVES));
        assertLeafUvsWithin(spruce, 0.5, 1, 0.5, 1);
        assertThat(TreeSpecies.SPRUCE.foliage().tile(Season.SPRING)).isZero();
    }

    private static void assertLeafUvsWithin(ProceduralMesh mesh, double u0, double u1, double v0, double v1) {
        Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
        for (int i = mesh.submeshStart(Tree.LEAVES); i < mesh.submeshEnd(Tree.LEAVES); i++) {
            int v = mesh.indices()[i];
            assertThat(uv.x(v)).isBetween(u0, u1);
            assertThat(uv.y(v)).isBetween(v0, v1);
        }
    }

    @Test
    void snowWeighsTheBranchesDown() {
        TreeGenerator generator = new TreeGenerator();
        Tree light = generator.generate(TreeSpecies.SPRUCE, 8);
        double before = leafHeight(light.lods().getFirst());
        Tree heavy = generator.generate(TreeSpecies.SPRUCE, TreeOptions.DEFAULTS.withSnowLoad(1), 8);
        double after = leafHeight(heavy.lods().getFirst());
        assertThat(after).isLessThan(before - 0.05);
        assertThat(heavy.height()).isCloseTo(light.height(), within(0.5)); // the trunk stands
    }

    private static double leafHeight(ProceduralMesh mesh) {
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        double sum = 0;
        int count = 0;
        for (int i = mesh.submeshStart(Tree.LEAVES); i < mesh.submeshEnd(Tree.LEAVES); i++) {
            sum += position.y(mesh.indices()[i]);
            count++;
        }
        return sum / count;
    }

    @ParameterizedTest
    @MethodSource("species")
    void barkUvsStayPreciseAndRunAlongBranches(TreeSpecies species) {
        ProceduralMesh mesh = Trees.generate(species, 13).lods().getFirst();
        Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
        Vec3Attribute normal = mesh.vertices().attribute(ProceduralVertex.NORMAL);
        Vec3Attribute tangent = mesh.vertices().attribute(ProceduralVertex.TANGENT);
        for (int i = 0; i < mesh.submeshEnd(Tree.BARK); i++) {
            int v = mesh.indices()[i];
            assertThat(uv.y(v)).isBetween(0.0, 4.1); // float16 keeps about 1/500 of a tile below 4
            assertThat(uv.x(v)).isBetween(0.0, 16.0);
            Vec3 n = normal.get(v);
            Vec3 t = tangent.get(v);
            assertThat(n.length()).isCloseTo(1, within(1e-3));
            assertThat(Math.abs(n.dot(t))).isLessThan(0.35); // the radius' slope tilts the normal a little
        }
    }

    @ParameterizedTest
    @MethodSource("species")
    void impostorHintsCoverTheTree(TreeSpecies species) {
        Tree tree = Trees.generate(species, 2);
        ImpostorHints hints = tree.impostor();
        ProceduralMesh full = tree.lods().getFirst();
        assertThat(hints.frames()).isEqualTo(8);
        assertThat(hints.atlasSize()).isEqualTo(8 * 256);
        Vec3 c = hints.bounds().center();
        double r = hints.bounds().radius();
        assertThat(c.distance(full.bounds().min())).isLessThanOrEqualTo(r + 1e-9);
        assertThat(c.distance(full.bounds().max())).isLessThanOrEqualTo(r + 1e-9);
        for (int i = 0; i < hints.frames(); i++) {
            for (int j = 0; j < hints.frames(); j++) {
                Vec3 d = hints.direction(i, j);
                assertThat(d.length()).isCloseTo(1, within(1e-9));
                assertThat(d.y()).isGreaterThanOrEqualTo(0);
                assertThat(hints.cameraPosition(i, j).distance(c)).isCloseTo(2 * r, within(1e-9));
            }
        }
        assertThat(tree.crownRadius()).isPositive();
        assertThat(tree.species()).isSameAs(species);
        assertThat(tree.seed()).isEqualTo(2);
    }

    @Test
    void badOptionsAndSpeciesAreRefused() {
        assertThatThrownBy(() -> new TreeLod(2, 3, 3, 0, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TreeLod(3, 3, 3, 0, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TreeLod(3, 3, 3, 0, 1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TreeLod(3, 3, 3, 0, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TreeLod(3, 2, 3, 0, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TreeLod(3, 3, 2, 0, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TreeOptions.DEFAULTS.withLods(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TreeOptions.DEFAULTS.withSnowLoad(2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TreeOptions.DEFAULTS.withSnowLoad(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TreeOptions(Season.SUMMER, 0, List.of(TreeLod.FULL), 1, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TreeOptions(Season.SUMMER, 0, List.of(TreeLod.FULL), 2, 0))
            .isInstanceOf(IllegalArgumentException.class);
        TreeSpecies b = TreeSpecies.BEECH;
        assertThatThrownBy(() -> new TreeSpecies("x", Growth.SPACE_COLONIZATION, 1, 0, 1, 0, 0, 0, 0, 1, 0, 2, 0.5,
            0.3, List.of(), null, b.foliage(), 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(TreeLod.FULL.sides(0)).isEqualTo(10);
        assertThat(TreeLod.FULL.sides(1)).isEqualTo(6);
        assertThat(TreeLod.FULL.sides(5)).isEqualTo(3);
    }

    @Test
    void shapesAndEnvelopes() {
        for (Branching.Shape shape : Branching.Shape.values()) {
            for (double t = 0; t <= 1; t += 0.05) {
                assertThat(shape.at(t)).isBetween(0.19, 1.0 + 1e-12);
            }
            assertThat(shape.at(-1)).isEqualTo(shape.at(0));
        }
        assertThat(Branching.Shape.FLAME.at(0.7)).isCloseTo(1, within(1e-12));
        assertThat(Crown.Shape.ELLIPSOID.contains(0, 0.5, 0)).isTrue();
        assertThat(Crown.Shape.ELLIPSOID.contains(1, 0.0, 0)).isFalse();
        assertThat(Crown.Shape.OVOID.contains(0.9, 0.35, 0)).isTrue();
        assertThat(Crown.Shape.OVOID.contains(0.0, 0.95, 0)).isTrue();
        assertThat(Crown.Shape.OVOID.contains(0.9, 0.9, 0)).isFalse();
        assertThat(Crown.Shape.OVOID.contains(0.9, 0.05, 0)).isFalse();
        assertThat(Crown.Shape.HEMISPHERE.contains(0.9, 0.1, 0)).isTrue();
        assertThat(Crown.Shape.HEMISPHERE.contains(0.9, 0.9, 0)).isFalse();
    }
}
