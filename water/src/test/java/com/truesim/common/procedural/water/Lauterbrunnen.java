package com.truesim.common.procedural.water;

import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.effects.terrain.data.SurfaceParameters;
import com.truesim.effects.terrain.data.TerrainSite;
import com.truesim.effects.terrain.data.TerrainSurface;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lauterbrunnen from effects-terrain's cache, where it holds the site: for the pictures and the film. */
final class Lauterbrunnen {

    /** The valley from Zweiluetschinen's side to the head of the valley, the Schilthorn to Wengen. */
    static final WaterRegion REGION = new WaterRegion(-8000, -3000, 6000, 13000);

    private Lauterbrunnen() {
    }

    /** effects-terrain's cache: {@code -Dtruesim.terrain.cache}, else the sibling checkout's. */
    static Path cache() {
        String given = System.getProperty("truesim.terrain.cache");
        return given != null ? Path.of(given) : Path.of("..", "..", "effects-terrain", "terrain-cache");
    }

    /** Lauterbrunnen's surface as the terrain draws it, or null where the cache does not hold it. */
    static TerrainSurface surface() throws IOException {
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

    /** The surface as the water's terrain: heights as a 2 m grid draws them. */
    static EcologyTerrain terrain(TerrainSurface surface) {
        return new EcologyTerrain() {
            @Override
            public double altitude(double x, double z) {
                return surface.heightAt(x, z, 2) + surface.fall(x, z);
            }

            @Override
            public double height(double x, double z) {
                return surface.heightAt(x, z, 2);
            }
        };
    }
}
