package com.truesim.common.procedural.tree;

import com.truesim.common.math.geometry.Meshes;
import com.truesim.common.procedural.mesh.ProceduralMesh;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Pictures of every species and rock at every level from the CPU rasterizer, with timings, for a quick look without
 * a GPU: {@code TRUESIM_PICTURES=true ./gradlew :common-procedural:test --tests '*ProbeTest'}, into build/probe.
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "TRUESIM_PICTURES", matches = "true")
class ProbeTest {

    @Test
    void rocks() throws Exception {
        int i = 0;
        for (com.truesim.common.procedural.rock.RockParams params : com.truesim.common.procedural.rock.RockParams.PRESETS) {
            for (int l = 0; l < 4; l++) {
                com.truesim.common.procedural.rock.Rock rock = com.truesim.common.procedural.rock.Rocks.generate(params, 7);
                com.truesim.common.procedural.SoftwareRender r = new com.truesim.common.procedural.SoftwareRender(320, 240);
                double s = params.size();
                r.camera(s * 2.2, s * 1.4, s * 2.6, 0, s * 0.4, 0, 0.9);
                r.ground(50);
                r.draw(rock.lods().get(l), 0, 0, 0, null);
                r.save(java.nio.file.Path.of("build/probe", "rock-" + params.name() + "-lod" + l + ".png"));
            }
        }
    }

    @Test
    void probe() {
        TreeGenerator generator = new TreeGenerator();
        for (TreeSpecies species : TreeSpecies.PRESETS) {
            generator.generate(species, 1);
            long start = System.nanoTime();
            int n = 5;
            Tree tree = null;
            for (int i = 0; i < n; i++) {
                tree = generator.generate(species, i + 2);
            }
            double ms = (System.nanoTime() - start) / 1e6 / n;
            Skeleton s = generator.skeleton();
            StringBuilder line = new StringBuilder(String.format("%-7s %6.1f ms h=%.1f crown=%.1f nodes=%d branches=%d leaves=%d base r=%.3f |",
                species.name(), ms, tree.height(), tree.crownRadius(), s.nodeCount(), s.branchCount(), s.leafCount(), s.radius(0)));
            for (ProceduralMesh m : tree.lods()) {
                line.append(String.format(" %d tris (%d bark) err %.3f;", m.triangleCount(), m.submeshEnd(0) / 3, m.error()));
            }
            ProceduralMesh m = tree.lods().getFirst();
            float[] p = m.positions();
            int[] canonical = new int[m.vertexCount()];
            Meshes.weld(p, m.vertexCount(), canonical);
            int open = Meshes.openEdges(Arrays.copyOf(m.indices(), m.submeshEnd(0)), m.submeshEnd(0), canonical);
            line.append(" open=").append(open);
            System.out.println(line);
            try {
                for (int l = 0; l < tree.lods().size(); l++) {
                    com.truesim.common.procedural.SoftwareRender r = new com.truesim.common.procedural.SoftwareRender(480, 640);
                    double h = tree.height();
                    r.camera(h * 1.3, h * 0.45, h * 0.6, 0, h * 0.5, 0, 0.9);
                    r.ground(50);
                    r.draw(tree.lods().get(l), 0, 0, 0, null);
                    r.save(java.nio.file.Path.of(System.getProperty("probe.dir", "build/probe"), species.name() + "-lod" + l + ".png"));
                }
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
