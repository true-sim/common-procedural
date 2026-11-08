package com.truesim.common.procedural.ecology.terrain;

import com.truesim.effects.terrain.data.DemRaster;
import com.truesim.effects.terrain.data.LocalFrame;
import com.truesim.effects.terrain.data.SurfaceParameters;
import com.truesim.effects.terrain.data.TerrainSite;
import com.truesim.effects.terrain.data.TerrainSurface;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** effects-terrain sites for the tests: a synthetic one built in code, and Lauterbrunnen where its cache holds it. */
final class Sites {

    static final LocalFrame FRAME = new LocalFrame(46.5937, 7.9087);

    private Sites() {
    }

    /** A valley 1 arc second a sample, {@code size} samples a side round the origin, draining north. */
    static TerrainSite valley(int size) {
        double step = 1 / 3600.0;
        float[] heights = new float[size * size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                double x = (i - size / 2) * 21.4;
                double z = (j - size / 2) * 30.9;
                heights[j * size + i] = (float) (900 + 0.45 * Math.abs(x) + 0.01 * z);
            }
        }
        DemRaster raster = new DemRaster(DemRaster.GEOGRAPHIC, FRAME.longitude() - step * (size / 2),
            FRAME.latitude() + step * (size / 2), step, step, size, size, heights);
        return new TerrainSite("valley", "a synthetic valley", FRAME,
            List.of(new TerrainSite.Source("valley", raster, 0, "synthetic")));
    }

    /** effects-terrain's cache: {@code -Dtruesim.terrain.cache}, else the sibling checkout's. */
    static Path cache() {
        String given = System.getProperty("truesim.terrain.cache");
        return given != null ? Path.of(given) : Path.of("..", "..", "effects-terrain", "terrain-cache");
    }

    /** The imported Lauterbrunnen site, or null where the cache does not hold it. */
    static TerrainSite lauterbrunnen() throws IOException {
        Path folder = cache().resolve("sites").resolve("lauterbrunnen");
        Path file = folder.resolve("site.terrain");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            return TerrainSite.read(in, name -> Files.newInputStream(folder.resolve(name)));
        }
    }

    /** Lauterbrunnen's surface as the terrain draws it (data and detail, curvature), or null. */
    static TerrainSurface lauterbrunnenSurface() throws IOException {
        TerrainSite site = lauterbrunnen();
        return site == null ? null : new TerrainSurface(site, new SurfaceParameters(2.5, 64, 6, 1.5, true));
    }
}
