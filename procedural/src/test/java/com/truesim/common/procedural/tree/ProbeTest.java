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
            for (int l = 0; l < params.subdivisions().size(); l++) {
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

    @Test
    void flora() throws Exception {
        TreeGenerator generator = new TreeGenerator();
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("probe.dir", "build/probe"));
        for (TreeSpecies species : TreeSpecies.PRESETS) {
            if (species.fruiting() == null) {
                continue;
            }
            com.truesim.common.procedural.flora.Phenology p = species.fruiting().phenology();
            double[] days = {p.peakBloom(), p.ripe()};
            String[] names = {"flower", "fruit"};
            for (int d = 0; d < 2; d++) {
                TreeOptions options = TreeOptions.DEFAULTS.withDay(days[d]);
                generator.generate(species, options, 1);
                long start = System.nanoTime();
                Tree tree = generator.generate(species, options, 2);
                double ms = (System.nanoTime() - start) / 1e6;
                StringBuilder line = new StringBuilder(String.format(java.util.Locale.ROOT,
                    "%-11s %-6s day %5.1f %7.1f ms sites %4d flora %s |", species.name(), names[d], days[d], ms,
                    generator.attachments().count(), tree.flora()));
                for (ProceduralMesh m : tree.lods()) {
                    line.append(String.format(java.util.Locale.ROOT, " %d (%d att) err %.3f;", m.triangleCount(),
                        (m.submeshEnd(2) - m.submeshStart(2)) / 3, m.error()));
                }
                System.out.println(line);
                double h = tree.height();
                double size = Math.max(h, 2 * tree.crownRadius());
                com.truesim.common.procedural.SoftwareRender r = new com.truesim.common.procedural.SoftwareRender(480, 480);
                r.camera(size * 1.2, h * 0.5, size * 0.9, 0, h * 0.5, 0, 0.9);
                r.ground(50);
                r.draw(tree.lods().getFirst(), 0, 0, 0, null, tree.palette(), 2);
                r.save(dir.resolve("flora-" + species.name() + "-" + names[d] + ".png"));
                if (generator.attachments().count() > 0) {
                    com.truesim.common.math.Vec3 at = generator.attachments().position(0);
                    com.truesim.common.procedural.flora.Inflorescence inf = species.fruiting().inflorescence();
                    double head = (inf.arrangement() == com.truesim.common.procedural.flora.Inflorescence.Arrangement.CLUSTER ? 0.5 : 1) * inf.spread()
                        + inf.pedicel() + inf.peduncle() + (species.fruiting().flower() == null
                        ? species.fruiting().fruit().halfLength() : species.fruiting().flower().reach());
                    double reach = 1.6 * head + 0.01;
                    for (int l = 0; l < tree.lods().size(); l++) {
                        ProceduralMesh m = tree.lods().get(l);
                        ProceduralMesh only = new ProceduralMesh(m.vertices(), m.submeshIndices(2),
                            new int[] {0, m.submeshEnd(2) - m.submeshStart(2)}, m.bounds(), 0);
                        com.truesim.common.procedural.SoftwareRender c = new com.truesim.common.procedural.SoftwareRender(360, 360);
                        c.camera(at.x() + reach * 1.5, at.y() + reach * 0.8, at.z() + reach * 1.4, at.x(), at.y() + 0.3 * head,
                            at.z(), 0.9);
                        c.draw(only, 0, 0, 0, null, tree.palette(), 0);
                        c.save(dir.resolve("flora-" + species.name() + "-" + names[d] + "-close" + l + ".png"));
                    }
                }
            }
        }
    }

    @Test
    void plants() throws Exception {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("probe.dir", "build/probe"));
        com.truesim.common.procedural.plant.PlantGenerator generator = new com.truesim.common.procedural.plant.PlantGenerator();
        for (com.truesim.common.procedural.plant.PlantSpecies species : com.truesim.common.procedural.plant.PlantSpecies.PRESETS) {
            double[] days = {species.phenology().peakBloom(), species.phenology().ripenEnd() + 5, 290, 20};
            String[] names = {"flower", "late", "autumn", "winter"};
            for (int d = 0; d < days.length; d++) {
                com.truesim.common.procedural.plant.PlantOptions options = com.truesim.common.procedural.plant.PlantOptions.DEFAULTS.withDay(days[d]);
                generator.generate(species, options, 1);
                long start = System.nanoTime();
                com.truesim.common.procedural.plant.Plant plant = generator.generate(species, options, 2);
                double ms = (System.nanoTime() - start) / 1e6;
                StringBuilder line = new StringBuilder(String.format(java.util.Locale.ROOT,
                    "%-10s %-7s day %5.1f %6.2f ms h %.2f r %.2f %s |", species.name(), names[d], days[d], ms,
                    plant.height(), plant.radius(), plant.flora()));
                for (ProceduralMesh m : plant.lods()) {
                    line.append(String.format(java.util.Locale.ROOT, " %d (%d fl) err %.3f;", m.triangleCount(),
                        (m.submeshEnd(1) - m.submeshStart(1)) / 3, m.error()));
                }
                System.out.println(line);
                double h = Math.max(plant.height(), 2 * plant.radius());
                for (int l = 0; l < 3; l++) {
                    com.truesim.common.procedural.SoftwareRender r = new com.truesim.common.procedural.SoftwareRender(360, 360);
                    r.camera(h * 1.1, h * 0.7, h * 0.9, 0, h * 0.4, 0, 0.9);
                    r.ground(5);
                    r.draw(plant.lods().get(l), 0, 0, 0, null, plant.palette(), 0);
                    r.save(dir.resolve("plant-" + species.name() + "-" + names[d] + "-lod" + l + ".png"));
                }
            }
        }
    }
}
