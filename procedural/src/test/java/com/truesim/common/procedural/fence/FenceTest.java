package com.truesim.common.procedural.fence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truesim.common.math.Vec2;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.layout.Vec2Attribute;
import com.truesim.common.math.layout.Vec3Attribute;
import com.truesim.common.math.layout.Vec4Attribute;
import com.truesim.common.procedural.fence.FencePiece.Role;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import java.lang.foreign.ValueLayout;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Fences on sloped, uneven ground: posts in it, rails between them, walls on it, levels, vertex data, recipes. */
class FenceTest {

    /** A hillside: 25 % up along x, 10 % along z, with bumps of 15 cm. */
    static final FenceGround HILL = (x, z) -> 0.25 * x + 0.1 * z + 0.15 * Math.sin(0.9 * x) * Math.cos(0.7 * z);
    /** A curving line up and across it. */
    static final double[] LINE = {0, 0, 6, 1.5, 11, 4.5, 15, 9.5, 17, 15};

    static List<FenceType> types() {
        return FenceType.PRESETS;
    }

    private static Fence fence(FenceType type, FenceOptions options) {
        return Fences.generate(type, options, LINE, HILL, 11);
    }

    private static FenceOptions intact() {
        return FenceOptions.DEFAULTS.withDamage(0);
    }

    @ParameterizedTest
    @MethodSource("types")
    void theSameInputsGiveBitIdenticalFences(FenceType type) {
        FenceGenerator generator = new FenceGenerator();
        Fence a = generator.generate(type, FenceOptions.DEFAULTS, FenceTuning.DEFAULTS, LINE, HILL, 5);
        generator.generate(type, FenceOptions.DEFAULTS, FenceTuning.DEFAULTS, LINE, HILL, 6);
        Fence b = Fences.generate(type, FenceOptions.DEFAULTS, LINE, HILL, 5);
        for (int l = 0; l < a.lods().size(); l++) {
            assertThat(bytes(a.lods().get(l))).isEqualTo(bytes(b.lods().get(l)));
            assertThat(a.lods().get(l).indices()).isEqualTo(b.lods().get(l).indices());
        }
        assertThat(bytes(Fences.generate(type, FenceOptions.DEFAULTS, LINE, HILL, 6).lods().getFirst()))
            .isNotEqualTo(bytes(a.lods().getFirst()));
    }

    @ParameterizedTest
    @MethodSource("types")
    void postsStandInTheGroundOnASlope(FenceType type) {
        Fence fence = fence(type, intact());
        List<FencePiece> posts = fence.pieces().stream()
            .filter(p -> p.role() == Role.POST || p.role() == Role.STAKE).toList();
        if (type.kind() == FenceKind.DRY_STONE) {
            assertThat(posts).isEmpty();
            return;
        }
        assertThat(posts).hasSizeGreaterThan(3);
        ProceduralMesh mesh = fence.lods().getFirst();
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        Vec3 o = fence.origin();
        for (FencePiece post : posts) {
            // its centre line goes into the ground and out of it: the foot below, the top above
            double footGround = HILL.height(post.at(0, 0), post.at(0, 2));
            assertThat(post.at(0, 1)).isLessThan(footGround - 0.2);
            assertThat(post.end(1) - HILL.height(post.end(0), post.end(2))).isGreaterThan(0.5);
            // every vertex of its lowest ring is under the ground where it is, downhill side included: no gap
            double lowest = Double.POSITIVE_INFINITY;
            for (int v = post.firstVertex(); v < post.endVertex(); v++) {
                lowest = Math.min(lowest, position.y(v));
            }
            int foot = 0;
            for (int v = post.firstVertex(); v < post.endVertex(); v++) {
                if (position.y(v) < lowest + 0.01) {
                    foot++;
                    double x = position.x(v) + o.x();
                    double z = position.z(v) + o.z();
                    assertThat(position.y(v) + o.y()).isLessThan(HILL.height(x, z) - 0.1);
                }
            }
            assertThat(foot).isGreaterThanOrEqualTo(3);
        }
    }

    @ParameterizedTest
    @MethodSource("types")
    void railsConnectThePostsAndFollowTheSlope(FenceType type) {
        Fence fence = fence(type, intact());
        List<FencePiece> posts = fence.pieces(type.kind() == FenceKind.SPLIT_RAIL || type.kind() == FenceKind.WATTLE
            ? Role.STAKE : Role.POST);
        switch (type.kind()) {
            case POST_AND_RAIL, PICKET, BOARD, WIRE -> {
                List<FencePiece> rails = fence.pieces().stream()
                    .filter(p -> p.role() == Role.RAIL || p.role() == Role.BOARD || p.role() == Role.WIRE).toList();
                int expected = type.kind() == FenceKind.PICKET ? 2 : type.kind() == FenceKind.BOARD ? 3 : type.rails();
                assertThat(rails).hasSizeGreaterThanOrEqualTo(expected * (posts.size() - 1));
                for (FencePiece rail : rails) {
                    FencePiece a = nearest(posts, rail.at(0, 0), rail.at(0, 2));
                    FencePiece b = nearest(posts, rail.end(0), rail.end(2));
                    assertThat(a).isNotSameAs(b);
                    double reach = (rail.role() == Role.BOARD ? rail.depth() + 0.002 : 0) + 0.01;
                    assertThat(onAxis(a, rail.at(0, 0), rail.at(0, 1), rail.at(0, 2), reach)).isTrue();
                    assertThat(onAxis(b, rail.end(0), rail.end(1), rail.end(2), reach)).isTrue();
                    // as high over the ground at both posts: it follows the slope
                    double over0 = rail.at(0, 1) - HILL.height(a.at(0, 0), a.at(0, 2));
                    double over1 = rail.end(1) - HILL.height(b.at(0, 0), b.at(0, 2));
                    assertThat(Math.abs(over0 - over1)).as("%s at %s", rail.role(), type.name()).isLessThan(0.12);
                    // and above the ground all along
                    for (int i = 0; i < rail.points(); i++) {
                        double under = HILL.height(rail.at(i, 0), rail.at(i, 2));
                        assertThat(rail.at(i, 1) - 0.5 * rail.depth()).isGreaterThan(under);
                    }
                }
            }
            case SPLIT_RAIL -> {
                List<FencePiece> rails = fence.pieces(Role.RAIL);
                assertThat(rails).hasSizeGreaterThanOrEqualTo(type.rails() * (posts.size() / 2 - 1));
                for (FencePiece rail : rails) {
                    // each end rests between a crossing's stakes
                    assertThat(horizontal(nearest(posts, rail.at(0, 0), rail.at(0, 2)), rail.at(0, 0), rail.at(0, 2)))
                        .isLessThan(0.45);
                    assertThat(horizontal(nearest(posts, rail.end(0), rail.end(2)), rail.end(0), rail.end(2)))
                        .isLessThan(0.45);
                    assertThat(rail.at(0, 1)).isGreaterThan(HILL.height(rail.at(0, 0), rail.at(0, 2)));
                }
                assertThat(fence.pieces(Role.LASHING)).hasSize(posts.size());
            }
            case STANGENZAUN -> {
                List<FencePiece> poles = fence.pieces(Role.POLE);
                assertThat(poles).hasSizeGreaterThan(20);
                for (FencePiece pole : poles) {
                    // the pole starts on the ground (or at the end post pair) and rises along the fence
                    double rise = pole.end(1) - HILL.height(pole.end(0), pole.end(2))
                        - (pole.at(0, 1) - HILL.height(pole.at(0, 0), pole.at(0, 2)));
                    assertThat(rise).isGreaterThan(0);
                    // and passes between a pair of posts
                    double closest = Double.POSITIVE_INFINITY;
                    for (int i = 0; i + 1 < posts.size(); i += 2) {
                        double mx = 0.5 * (posts.get(i).at(0, 0) + posts.get(i + 1).at(0, 0));
                        double mz = 0.5 * (posts.get(i).at(0, 2) + posts.get(i + 1).at(0, 2));
                        closest = Math.min(closest, segmentDistance(pole, mx, mz));
                    }
                    assertThat(closest).isLessThan(0.08);
                }
            }
            case WATTLE -> {
                List<FencePiece> rods = fence.pieces(Role.ROD);
                assertThat(rods).hasSizeGreaterThan(40);
                for (FencePiece rod : rods) {
                    // a rod starts and ends at a stake, weaving within reach of the stakes all along
                    assertThat(horizontal(nearest(posts, rod.at(0, 0), rod.at(0, 2)), rod.at(0, 0), rod.at(0, 2)))
                        .isLessThan(0.12);
                    assertThat(horizontal(nearest(posts, rod.end(0), rod.end(2)), rod.end(0), rod.end(2)))
                        .isLessThan(0.12);
                    for (int i = 0; i < rod.points(); i++) {
                        assertThat(rod.at(i, 1)).isGreaterThan(HILL.height(rod.at(i, 0), rod.at(i, 2)));
                    }
                }
            }
            case DRY_STONE -> {
                // the wall stands on the ground and follows it: its top as high over the ground everywhere
                List<FencePiece> stones = fence.pieces(Role.STONE);
                assertThat(stones).hasSizeGreaterThan(100);
                double[] top = new double[(int) Math.ceil(fence.length()) + 1];
                for (FencePiece stone : stones) {
                    double under = HILL.height(stone.at(0, 0), stone.at(0, 2));
                    assertThat(stone.at(0, 1) - under).isBetween(-0.12, type.height());
                }
                for (int k = 0; k < top.length; k++) {
                    top[k] = Double.NEGATIVE_INFINITY;
                }
                for (FencePiece stone : stones) {
                    int k = (int) Math.round(along(stone.at(0, 0), stone.at(0, 2)));
                    top[k] = Math.max(top[k], stone.end(1) - HILL.height(stone.at(0, 0), stone.at(0, 2)));
                }
                for (int k = 1; k < top.length - 1; k++) {
                    assertThat(top[k]).as("the wall's top at %d m", k).isBetween(type.height() - 0.25,
                        type.height() + 0.25);
                }
            }
            default -> throw new IllegalStateException();
        }
    }

    @ParameterizedTest
    @MethodSource("types")
    void levelsGetCoarserAndTheVertexDataFitsItsFormats(FenceType type) {
        Fence fence = fence(type, FenceOptions.DEFAULTS.withGates(List.of(9.0), 1.2));
        assertThat(fence.lods()).hasSize(3);
        double previousError = -1;
        int previous = Integer.MAX_VALUE;
        for (ProceduralMesh mesh : fence.lods()) {
            assertThat(mesh.submeshCount()).isEqualTo(3);
            assertThat(mesh.triangleCount()).isLessThan(previous);
            assertThat(mesh.error()).isGreaterThanOrEqualTo(previousError);
            previous = mesh.triangleCount();
            previousError = mesh.error();
            Vec3Attribute normal = mesh.vertices().attribute(ProceduralVertex.NORMAL);
            Vec3Attribute tangent = mesh.vertices().attribute(ProceduralVertex.TANGENT);
            Vec2Attribute uv = mesh.vertices().attribute(ProceduralVertex.UV);
            Vec2Attribute extra = mesh.vertices().attribute(ProceduralVertex.WIND_PIVOT);
            Vec4Attribute color = mesh.vertices().attribute(ProceduralVertex.WIND);
            for (int v = 0; v < mesh.vertexCount(); v++) {
                Vec3 n = normal.get(v);
                assertThat(n.length()).isCloseTo(1, org.assertj.core.data.Offset.offset(0.01));
                assertThat(Math.abs(n.dot(tangent.get(v)))).isLessThan(0.05);
                Vec2 t = uv.get(v);
                // half floats stay precise to a few millimetres below 8
                assertThat(Math.abs(t.x())).isLessThan(8);
                assertThat(Math.abs(t.y())).isLessThan(8);
                Vec2 e = extra.get(v);
                assertThat(e.x()).isBetween(0.0, 1.0);
                assertThat(e.y()).isBetween(0.0, 1.0);
                var c = color.get(v);
                assertThat(c.x()).isBetween(0.0, 0.9);
                assertThat(c.y()).isBetween(0.0, 0.9);
                assertThat(c.z()).isBetween(0.0, 0.9);
                assertThat(c.w()).isEqualTo(1);
            }
        }
        assertThat(fence.lods().getFirst().error()).isZero();
        assertThat(fence.lods().getLast().error()).isLessThan(0.3);
        // pieces are small: each a few meshlets' worth
        for (FencePiece piece : fence.pieces()) {
            assertThat(piece.endVertex() - piece.firstVertex()).isBetween(1, 256);
        }
        int part = type.kind() == FenceKind.DRY_STONE ? Fence.STONE : Fence.WOOD;
        ProceduralMesh near = fence.lods().getFirst();
        assertThat(near.submeshEnd(part)).isGreaterThan(near.submeshStart(part));
        if (type.kind() == FenceKind.WIRE) {
            assertThat(near.submeshEnd(Fence.METAL)).isGreaterThan(near.submeshStart(Fence.METAL));
        }
        assertThat(fence.pieces(Role.GATE)).hasSize(6); // two stiles, three rails, a brace
    }

    @Test
    void aGateLeavesAGapInTheFence() {
        Fence fence = fence(FenceType.POST_AND_RAIL, intact().withGates(List.of(8.0), 1.4));
        for (FencePiece rail : fence.pieces(Role.RAIL)) {
            double a = along(rail.at(0, 0), rail.at(0, 2));
            double b = along(rail.end(0), rail.end(2));
            assertThat(Math.max(a, b) <= 8.0 - 0.7 || Math.min(a, b) >= 8.0 + 0.7).isTrue();
        }
        List<FencePiece> leaf = fence.pieces(Role.GATE);
        for (FencePiece piece : leaf) {
            assertThat(along(piece.at(0, 0), piece.at(0, 2))).isBetween(8.0 - 0.75, 8.0 + 0.75);
            assertThat(piece.at(0, 1)).isGreaterThan(HILL.height(piece.at(0, 0), piece.at(0, 2)));
        }
    }

    @Test
    void damageLeavesPiecesOutAndWearChangesTheColors() {
        Fence whole = fence(FenceType.PICKET, intact());
        Fence worn = fence(FenceType.PICKET, intact().withDamage(0.6));
        assertThat(worn.pieces(Role.PICKET).size()).isLessThan(whole.pieces(Role.PICKET).size());
        Fence wall = fence(FenceType.DRY_STONE, intact().withDamage(0.8));
        // fallen stones lie beside the wall
        long beside = wall.pieces(Role.STONE).stream().filter(s -> distanceToLine(s.at(0, 0), s.at(0, 2)) > 0.45)
            .count();
        assertThat(beside).isGreaterThan(3);
        Fence fresh = fence(FenceType.POST_AND_RAIL, intact().withWood(FenceMaterial.FRESH_WOOD).withWeathering(0)
            .withMoss(0).withLichen(0));
        Fence grey = fence(FenceType.POST_AND_RAIL, intact().withWeathering(1).withMoss(0).withLichen(0));
        assertThat(meanSaturation(fresh.lods().getFirst())).isGreaterThan(meanSaturation(grey.lods().getFirst()) + 0.2);
        Fence mossy = fence(FenceType.POST_AND_RAIL, intact().withMoss(1));
        Vec2Attribute extra = mossy.lods().getFirst().vertices().attribute(ProceduralVertex.WIND_PIVOT);
        int mossed = 0;
        for (int v = 0; v < mossy.lods().getFirst().vertexCount(); v++) {
            mossed += extra.get(v).x() > 0.3 ? 1 : 0;
        }
        assertThat(mossed).isGreaterThan(100);
    }

    @Test
    void aRecipeReadsWritesAndGeneratesTheSameFence() {
        FenceRecipe recipe = FenceRecipe.parse("""
            # a Schärhag up the meadow
            type = schaerhag
            seed = 7
            line = 0 0 0, 12 3 3, 20 6 9
            wood = weathered-larch
            weathering = 0.9
            moss = 0.6
            gates = 10
            rails = 5
            material.wood = game://materials/larch.material
            """);
        assertThat(recipe.type().rails()).isEqualTo(5);
        assertThat(recipe.ground()).isEqualTo("line");
        assertThat(recipe.options().wood()).isEqualTo(FenceMaterial.WEATHERED_LARCH);
        assertThat(recipe.materials()).containsExactly("game://materials/larch.material", "", "");
        assertThat(FenceRecipe.parse(recipe.text())).isEqualTo(recipe);
        assertThat(FenceRecipe.parse(recipe.text()).generationKey()).isEqualTo(recipe.generationKey());
        FenceGround ground = recipe.ownGround();
        assertThat(ground.height(12, 3)).isEqualTo(3);
        assertThat(ground.height(16, 6)).isCloseTo(4.5, org.assertj.core.data.Offset.offset(1e-9));
        Fence fence = recipe.generate(ground, FenceTuning.DEFAULTS);
        Fence direct = Fences.generate(recipe.type(), recipe.options(), recipe.points(), ground, 7);
        assertThat(bytes(fence.lods().getFirst())).isEqualTo(bytes(direct.lods().getFirst()));
        assertThat(FenceRecipe.parse("type = wire\nline = 0 0, 5 5\n").ownGround()).isSameAs(FenceGround.FLAT);
        assertThat(FenceRecipe.parse("type = wire\nline = 0 0, 5 5\nground = valley\n").ownGround()).isNull();
        assertThatThrownBy(() -> FenceRecipe.parse("type = moat\nline = 0 0, 1 1\n"))
            .hasMessageContaining("moat");
        assertThatThrownBy(() -> FenceRecipe.parse("type = wire\n")).hasMessageContaining("line");
        assertThatThrownBy(() -> FenceRecipe.parse("type = wire\nline = 0 0, 1 1\ncolour = red\n"))
            .hasMessageContaining("colour");
    }

    @Test
    void tuningSinksPostsAndSetsTheLevels() {
        FenceTuning deep = new FenceTuning(0.6, 0.5, 12, 2);
        Fence fence = Fences.generate(FenceType.POST_AND_RAIL, intact(), deep, LINE, HILL, 3);
        assertThat(fence.lods()).hasSize(2);
        for (FencePiece post : fence.pieces(Role.POST)) {
            assertThat(post.at(0, 1)).isLessThan(HILL.height(post.at(0, 0), post.at(0, 2)) - 0.6);
        }
        assertThatThrownBy(() -> new FenceTuning(0.3, 0.5, 2, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- helpers

    private static byte[] bytes(ProceduralMesh mesh) {
        return mesh.vertices().segment().toArray(ValueLayout.JAVA_BYTE);
    }

    private static FencePiece nearest(List<FencePiece> posts, double x, double z) {
        FencePiece best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (FencePiece post : posts) {
            double d = horizontal(post, x, z);
            if (d < distance) {
                distance = d;
                best = post;
            }
        }
        return best;
    }

    /** The horizontal distance from (x, z) to a post's axis where it meets the ground. */
    private static double horizontal(FencePiece post, double x, double z) {
        double gx = 0.5 * (post.at(0, 0) + post.end(0));
        double gz = 0.5 * (post.at(0, 2) + post.end(2));
        return Math.sqrt((gx - x) * (gx - x) + (gz - z) * (gz - z));
    }

    /** Whether (x, y, z) is inside {@code post} (within {@code reach} more than its radius of its axis). */
    private static boolean onAxis(FencePiece post, double x, double y, double z, double reach) {
        double ax = post.at(0, 0);
        double ay = post.at(0, 1);
        double az = post.at(0, 2);
        double dx = post.end(0) - ax;
        double dy = post.end(1) - ay;
        double dz = post.end(2) - az;
        double l2 = dx * dx + dy * dy + dz * dz;
        double t = ((x - ax) * dx + (y - ay) * dy + (z - az) * dz) / l2;
        if (t < 0 || t > 1) {
            return false;
        }
        double ex = ax + t * dx - x;
        double ey = ay + t * dy - y;
        double ez = az + t * dz - z;
        return Math.sqrt(ex * ex + ey * ey + ez * ez) <= 0.5 * post.width() * 1.42 + reach;
    }

    /** The horizontal distance from (x, z) to a piece's centre line. */
    private static double segmentDistance(FencePiece piece, double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < piece.points(); i++) {
            double ax = piece.at(i, 0);
            double az = piece.at(i, 2);
            double dx = piece.at(i + 1, 0) - ax;
            double dz = piece.at(i + 1, 2) - az;
            double t = Math.clamp(((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz), 0, 1);
            double ex = ax + t * dx - x;
            double ez = az + t * dz - z;
            best = Math.min(best, Math.sqrt(ex * ex + ez * ez));
        }
        return best;
    }

    /** The distance along {@link #LINE} of the point of it nearest to (x, z). */
    private static double along(double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        double at = 0;
        double s = 0;
        for (int i = 0; i + 3 < LINE.length; i += 2) {
            double ax = LINE[i];
            double az = LINE[i + 1];
            double dx = LINE[i + 2] - ax;
            double dz = LINE[i + 3] - az;
            double l = Math.sqrt(dx * dx + dz * dz);
            double t = Math.clamp(((x - ax) * dx + (z - az) * dz) / (l * l), 0, 1);
            double ex = ax + t * dx - x;
            double ez = az + t * dz - z;
            double d = ex * ex + ez * ez;
            if (d < best) {
                best = d;
                at = s + t * l;
            }
            s += l;
        }
        return at;
    }

    private static double distanceToLine(double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 3 < LINE.length; i += 2) {
            double ax = LINE[i];
            double az = LINE[i + 1];
            double dx = LINE[i + 2] - ax;
            double dz = LINE[i + 3] - az;
            double t = Math.clamp(((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz), 0, 1);
            double ex = ax + t * dx - x;
            double ez = az + t * dz - z;
            best = Math.min(best, Math.sqrt(ex * ex + ez * ez));
        }
        return best;
    }

    private static double meanSaturation(ProceduralMesh mesh) {
        Vec4Attribute color = mesh.vertices().attribute(ProceduralVertex.WIND);
        double sum = 0;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            var c = color.get(v);
            double max = Math.max(c.x(), Math.max(c.y(), c.z()));
            double min = Math.min(c.x(), Math.min(c.y(), c.z()));
            sum += max > 0 ? (max - min) / max : 0;
        }
        return sum / mesh.vertexCount();
    }
}
