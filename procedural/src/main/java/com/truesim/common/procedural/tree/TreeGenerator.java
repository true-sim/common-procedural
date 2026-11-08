package com.truesim.common.procedural.tree;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Sphere;
import com.truesim.common.math.random.Rng;
import com.truesim.common.procedural.Seeds;
import com.truesim.common.procedural.flora.FloraSummary;
import com.truesim.common.procedural.flora.Fruiting;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.mesh.MeshWriter;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import com.truesim.common.procedural.mesh.ProceduralVertex;
import com.truesim.common.math.layout.Vec3Attribute;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates trees: grows a skeleton by the species' method, weighs it down with snow, hangs leaf cards, gives it
 * pipe-model radii and wind data, and meshes every level of detail, a billboard and impostor hints. The same species,
 * options and seed always give bit-identical meshes, on every platform.
 *
 * <p>A generator keeps its working memory from tree to tree, so generating a forest allocates little beyond the
 * meshes; it is not thread-safe, so use one per thread ({@link Trees} keeps one per thread).
 */
public final class TreeGenerator {

    /** Version of the generated output: changes whenever the same inputs would give different meshes. */
    public static final int VERSION = 2;

    private final Skeleton skeleton = new Skeleton();
    private final ParametricGrowth parametric = new ParametricGrowth();
    private final SpaceColonization colonization = new SpaceColonization();
    private final TreeWind wind = new TreeWind();
    private final TreeMesher mesher = new TreeMesher();
    private final Attachments attachments = new Attachments();
    private final MeshWriter billboard = new MeshWriter();
    private double[] scratchA = new double[1024];
    private double[] scratchB = new double[1024];

    /** A tree of {@code species} with the default options. */
    public Tree generate(TreeSpecies species, long seed) {
        return generate(species, TreeOptions.DEFAULTS, seed);
    }

    /** A tree of {@code species} as {@code options} say, made from {@code seed}. */
    @AllowAllocation("the tree made")
    public Tree generate(TreeSpecies species, TreeOptions options, long seed) {
        Rng rng = Rng.seeded(Seeds.derive(seed, 0));
        double height = species.height() * (1 + species.heightVariation() * (2 * rng.nextDouble() - 1));
        skeleton.clear();
        Rng growth = Rng.seeded(Seeds.derive(seed, 1));
        if (species.growth() == Growth.PARAMETRIC) {
            parametric.grow(skeleton, species, height, growth);
        } else {
            colonization.grow(skeleton, species, height, growth);
        }
        Fruiting fruiting = species.fruiting();
        // Snow, and a crop of fruit, weigh the branches down
        double crop = fruiting == null ? 0 : fruiting.weightDroop() * attachments.load(fruiting, options.day());
        snow(species.snowDroop() * options.snowLoad() + crop);
        Vec3 crown = new Vec3(0, height * (1 + species.crownBase()) / 2, 0);
        LeafPlacement.place(skeleton, species.foliage(), crown, Rng.seeded(Seeds.derive(seed, 2)));
        attachments.place(skeleton, fruiting, height, crown, Rng.seeded(Seeds.derive(seed, 3)));
        boolean alt = (Seeds.derive(seed, 4) & 1) != 0; // the second cultivar's fruit color
        skeleton.pipeRadii(species.pipeExponent(), species.baseRadius() * height / species.height());
        wind.compute(skeleton, seed);
        double[] barkV = barkV(species.barkTileLength());
        TreeMesher.Context context = new TreeMesher.Context(species, options.season(), wind, barkV, crown.x(),
            crown.y(), crown.z(), options.day(), alt, attachments);
        List<ProceduralMesh> lods = new ArrayList<>(options.lods().size());
        double error = 0;
        for (TreeLod lod : options.lods()) {
            ProceduralMesh mesh = level(context, lod, error);
            error = mesh.error();
            lods.add(mesh);
        }
        ProceduralMesh full = lods.getFirst();
        double top = full.bounds().max().y();
        double crownRadius = crownRadius(full);
        Vec3 center = full.center();
        double radius = full.bounds().max().sub(full.bounds().min()).length() * 0.5;
        FloraSummary flora = attachments.summary(fruiting, options.day(), alt);
        double coverage = Math.min(1, flora.area() / Math.max(2 * crownRadius * top, 1e-9));
        ImpostorHints impostor = new ImpostorHints(new Sphere(center, radius), options.impostorFrames(),
            options.impostorResolution(), flora.albedo(), coverage);
        Palette palette = fruiting == null ? Palette.DEFAULT : fruiting.palette();
        return new Tree(species, seed, lods, billboard(top, crownRadius), impostor, top, crownRadius, flora,
            palette);
    }

    /**
     * The skeleton of the tree generated last: nodes, radii and leaf cards. Valid until the next {@link #generate};
     * for inspection and tests.
     */
    public Skeleton skeleton() {
        return skeleton;
    }

    /** The wind data of the tree generated last; for tests. */
    TreeWind wind() {
        return wind;
    }

    /** The flowers', fruits' and cones' sites of the tree generated last; for tests. */
    Attachments attachments() {
        return attachments;
    }

    /**
     * One level, within its budget: flowers and fruits simplified and merged, then leaf cards and thin branches
     * dropped further, flowers and fruits dropped last, until it fits.
     */
    private ProceduralMesh level(TreeMesher.Context context, TreeLod lod, double finer) {
        int keep = lod.leafKeep();
        // Small trees and shrubs keep their stems at every level
        double minRadius = Math.min(lod.minRadius(), 0.6 * skeleton.radius(0));
        double cardLength = context.species().foliage().length();
        Fruiting fruiting = context.species().fruiting();
        int detail = attachments.count() == 0 ? TreeLod.NO_ATTACHMENTS : lod.attachmentDetail();
        for (int attempt = 0; ; attempt++) {
            // At least the finer level's: a coarser level never claims to be closer to the tree
            double error = Math.max(finer, Math.max(Math.max(2 * minRadius, cardLength * (Math.sqrt(keep) - 1)),
                Attachments.error(fruiting, attachments.count() == 0 ? 0 : detail)));
            ProceduralMesh mesh = mesher.mesh(skeleton, context, lod, keep, minRadius, detail, error);
            if (mesh.triangleCount() <= lod.triangleBudget() || attempt == 16) {
                return mesh;
            }
            // flowers and fruits simplified and merged first, then leaves and twigs thinned; dropped last. Where they
            // are too few to make the difference, both at once: every attempt meshes the whole tree again
            int over = mesh.triangleCount() - lod.triangleBudget();
            int attached = (mesh.submeshEnd(Tree.ATTACHMENTS) - mesh.submeshStart(Tree.ATTACHMENTS)) / 3;
            if (detail < 2) {
                // simplifying keeps about a third: when that cannot fit, merge at once (a pass saved)
                detail = detail == 0 && mesh.triangleCount() - 0.65 * attached > lod.triangleBudget() ? 2 : detail + 1;
                if (attached < over) {
                    keep += Math.max(1, keep / 2);
                    minRadius *= 1.4;
                }
            } else if (detail == 2 && keep > 6 * lod.leafKeep()) {
                // even thinned leaves leave no room: drop flowers and fruits, and start thinning over
                detail++;
                keep = lod.leafKeep();
                minRadius = Math.min(lod.minRadius(), 0.6 * skeleton.radius(0));
            } else {
                keep += Math.max(1, keep / 2);
                minRadius *= 1.4;
            }
        }
    }

    /** Sinks branches under snow: each node by {@code droop * s² / length} along its branch, plus its parent's. */
    @AllowAllocation("growing, until warm")
    private void snow(double droop) {
        if (droop <= 0) {
            return;
        }
        int nodes = skeleton.nodeCount();
        if (scratchA.length < nodes) {
            scratchA = new double[2 * nodes];
            scratchB = new double[2 * nodes];
        }
        double[] along = scratchA;
        double[] sink = scratchB;
        for (int n = 0; n < nodes; n++) {
            int b = skeleton.branchOf(n);
            int p = skeleton.parent(n);
            along[n] = n == skeleton.firstNode(b) ? 0 : along[p] + Axes.distance(skeleton, n, p);
        }
        for (int n = 0; n < nodes; n++) {
            int b = skeleton.branchOf(n);
            int p = skeleton.parent(n);
            if (skeleton.level(b) == 0) {
                sink[n] = 0;
            } else if (n == skeleton.firstNode(b)) {
                sink[n] = sink[p];
            } else {
                double length = Math.max(Axes.length(skeleton, b), 1e-6);
                sink[n] = sink[p] + droop * (along[n] * along[n] - along[p] * along[p]) / length;
            }
        }
        for (int n = 0; n < nodes; n++) {
            skeleton.move(n, skeleton.x(n), skeleton.y(n) - sink[n], skeleton.z(n));
        }
    }

    /** Bark v at every node: tiles along the branches from the ground, a branch continuing from where it starts. */
    @AllowAllocation("growing, until warm")
    private double[] barkV(double tileLength) {
        int nodes = skeleton.nodeCount();
        if (scratchA.length < nodes) {
            scratchA = new double[2 * nodes];
            scratchB = new double[2 * nodes];
        }
        double[] v = scratchA;
        for (int n = 0; n < nodes; n++) {
            int p = skeleton.parent(n);
            v[n] = p < 0 ? 0 : v[p] + Axes.distance(skeleton, n, p) / tileLength;
        }
        return v;
    }

    private static double crownRadius(ProceduralMesh mesh) {
        Vec3Attribute position = mesh.vertices().attribute(ProceduralVertex.POSITION);
        double max = 0;
        for (int i = 0; i < mesh.vertexCount(); i++) {
            double x = position.x(i);
            double z = position.z(i);
            max = Math.max(max, x * x + z * z);
        }
        return Math.sqrt(max);
    }

    /** Three quads crossed at 60 degrees through the y axis, as tall and wide as the tree. */
    private ProceduralMesh billboard(double top, double crownRadius) {
        billboard.reset();
        for (int q = 0; q < 3; q++) {
            double angle = Math.PI * q / 3;
            double cx = com.truesim.common.math.Maths.cos(angle);
            double cz = com.truesim.common.math.Maths.sin(angle);
            // across: (cx, 0, cz); normal: (-cz, 0, cx); bitangent normal x across = +y
            double nx = -cz;
            double nz = cx;
            int[] corners = new int[4];
            for (int k = 0; k < 4; k++) {
                double u = k == 1 || k == 2 ? 1 : 0;
                double v = k >= 2 ? 1 : 0;
                double h = v * top;
                double weight = v * Math.sqrt(v);
                billboard.wind(weight, 0, 0, 0, 0, top);
                corners[k] = billboard.vertex((2 * u - 1) * crownRadius * cx, h, (2 * u - 1) * crownRadius * cz, nx,
                    0, nz, cx, 0, cz, u, v);
            }
            billboard.quad(corners[0], corners[1], corners[2], corners[3]);
        }
        return billboard.build(crownRadius * 0.3);
    }
}
