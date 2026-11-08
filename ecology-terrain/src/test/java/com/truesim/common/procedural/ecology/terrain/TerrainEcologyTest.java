package com.truesim.common.procedural.ecology.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.EcologyTile;
import com.truesim.common.procedural.ecology.Ground;
import com.truesim.common.procedural.ecology.GroundCover;
import com.truesim.common.procedural.ecology.Site;
import com.truesim.effects.terrain.data.SurfaceParameters;
import com.truesim.effects.terrain.data.TerrainSurface;
import org.junit.jupiter.api.Test;

class TerrainEcologyTest {

    private static final EcologyParameters.Analysis QUICK = new EcologyParameters.Analysis(8, 512, 512, 150, 40, 16);

    @Test
    void heightsAndGroundComeFromEffectsTerrain() {
        TerrainSurface surface = new TerrainSurface(Sites.valley(300), new SurfaceParameters(2.5, 64, 6, 1.5, true));
        TerrainEcology plain = TerrainEcology.of(surface, 2);
        assertEquals(surface, plain.surface());
        for (double x = -1500; x <= 1500; x += 377) {
            double z = 0.7 * x - 200;
            assertEquals(surface.heightAt(x, z, 2), plain.height(x, z));
            assertEquals(surface.heightAt(x, z, 2) + surface.fall(x, z), plain.altitude(x, z));
        }
        assertFalse(plain.ground(0, 0, new double[Ground.COUNT]));
        assertTrue(Double.isNaN(plain.snowDays(0, 0)));
        // layers as effects-terrain gives them: rock, grass, scree, snow
        TerrainEcology layered = TerrainEcology.of(surface, 2, (x, z, w) -> {
            w[TerrainEcology.Layers.ROCK] = 0.1;
            w[TerrainEcology.Layers.GRASS] = 0.6;
            w[TerrainEcology.Layers.SCREE] = 0.2;
            w[TerrainEcology.Layers.SNOW] = 0.1;
        });
        double[] ground = new double[Ground.COUNT];
        assertTrue(layered.ground(5, 5, ground));
        assertEquals(0.1, ground[Ground.ROCK]);
        assertEquals(0.6, ground[Ground.SOIL]);
        assertEquals(0.2, ground[Ground.SCREE]);
        assertEquals(0.1, ground[Ground.SNOW]);
        // the ecology over it: a valley at 900 m with its stream along x = 0
        Ecology ecology = new Ecology(layered, EcologyParameters.ALPS.withAnalysis(QUICK), AlpineSpecies.ALL);
        Site floor = ecology.conditions(0, 0);
        assertEquals(surface.heightAt(0, 0, 2) + surface.fall(0, 0), floor.altitude, 1e-9);
        assertEquals(0.1, floor.ground[Ground.ROCK]);
        assertTrue(floor.hand < 2 && floor.streamArea > 50_000, floor.hand + " " + floor.streamArea);
        EcologyTile tile = ecology.tile(-1, -1);
        assertTrue(tile.instances().count() > 0);
        float[] snow = tile.cover(GroundCover.SNOW);
        assertEquals(0.1, snow[snow.length / 2], 0.01);
    }

    @Test
    void lauterbrunnenValleyFloorAlongTheWeisseLuetschine() throws Exception {
        TerrainSurface surface = Sites.lauterbrunnenSurface();
        assumeTrue(surface != null, "effects-terrain's cache holds no Lauterbrunnen");
        Ecology ecology = new Ecology(TerrainEcology.of(surface, 2), EcologyParameters.ALPS, AlpineSpecies.ALL);
        // across the floor south of the village (z 1500 m south of the church): the river, riparian ground by it
        double river = Double.NaN;
        double riparian = 0;
        for (double x = -700; x <= 300; x += 4) {
            Site site = ecology.conditions(x, 1500);
            if (site.water > 0.5 && Double.isNaN(river)) {
                river = x;
            }
            riparian = Math.max(riparian, site.riparian);
        }
        assertFalse(Double.isNaN(river), "the Weisse Luetschine on the floor");
        assertTrue(riparian > 0.5);
        // the cliffs of the U-shaped valley within a kilometre west of the river: steep, rock, never riparian
        double steepest = 0;
        for (double x = river - 1000; x < river; x += 8) {
            Site site = ecology.conditions(x, 1500);
            if (site.slope > 50) {
                assertEquals(0, site.riparian);
                assertTrue(site.ground[com.truesim.common.procedural.ecology.Ground.ROCK] > 0.5);
            }
            steepest = Math.max(steepest, site.slope);
        }
        assertTrue(steepest > 50, "the western cliffs: " + steepest);
    }
}
