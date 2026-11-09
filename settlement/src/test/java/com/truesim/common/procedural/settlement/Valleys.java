package com.truesim.common.procedural.settlement;

import com.truesim.common.math.Maths;
import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.effects.terrain.data.SurfaceParameters;
import com.truesim.effects.terrain.data.TerrainSite;
import com.truesim.effects.terrain.data.TerrainSurface;
import com.truesim.common.procedural.ecology.terrain.TerrainEcology;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Terrains for the tests: a synthetic Alpine valley, and Lauterbrunnen where effects-terrain's cache holds it. */
final class Valleys {

    /** Small analysis blocks: catchments of a kilometre suffice. */
    static final EcologyParameters.Analysis QUICK = new EcologyParameters.Analysis(8, 512, 512, 150, 40, 32);

    private Valleys() {
    }

    /**
     * A U-shaped valley draining north along a river at x = 150 sin(z / 700): a floor 500 m wide rising 3 % away
     * from the river and 0.6 % south, sides rising at 32 degrees beyond it with a bench 200 m up, gullies down them.
     */
    static EcologyTerrain valley() {
        return (x, z) -> {
            double river = 150 * Maths.sin(z / 700);
            double d = Math.abs(x - river);
            double floor = 0.03 * Math.min(d, 250) + 0.006 * z;
            double side = d > 250 ? 0.62 * (d - 250) : 0;
            // a bench (a terrace) on the sides, as Lauterbrunnen's
            double bench = d > 600 ? -0.45 * Math.min(d - 600, 120) : 0;
            double gullies = d > 260 ? 6 * Maths.sin(z / 37 + x / 90) * Math.min(1, (d - 260) / 60) : 0;
            return 800 + floor + side + bench + gullies;
        };
    }

    /** The ecology over {@code terrain} with quick analysis, open meadows. */
    static Ecology ecology(EcologyTerrain terrain) {
        EcologyParameters parameters = EcologyParameters.ALPS.withAnalysis(QUICK)
            .withLandUse(EcologyParameters.LandUse.MEADOWS).withSeed(3);
        return new Ecology(terrain, parameters, AlpineSpecies.ALL);
    }

    /** effects-terrain's cache: {@code -Dtruesim.terrain.cache}, else the sibling checkout's. */
    static Path cache() {
        String given = System.getProperty("truesim.terrain.cache");
        return given != null ? Path.of(given) : Path.of("..", "..", "effects-terrain", "terrain-cache");
    }

    /** Lauterbrunnen's surface as the terrain draws it, or null where the cache does not hold it. */
    static TerrainSurface lauterbrunnen() throws IOException {
        Path folder = cache().resolve("sites").resolve("lauterbrunnen");
        Path file = folder.resolve("site.terrain");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            TerrainSite site = TerrainSite.read(in, name -> Files.newInputStream(folder.resolve(name)));
            return new TerrainSurface(site, new SurfaceParameters(2.5, 64, 6, 1.5, true));
        }
    }

    /** The ecology over Lauterbrunnen's surface (heights every 2 m), or null. */
    static Ecology lauterbrunnenEcology() throws IOException {
        TerrainSurface surface = lauterbrunnen();
        if (surface == null) {
            return null;
        }
        EcologyParameters parameters = EcologyParameters.ALPS.withLandUse(EcologyParameters.LandUse.MEADOWS)
            .withSeed(11);
        return new Ecology(TerrainEcology.of(surface, 2), parameters, AlpineSpecies.ALL);
    }
}
