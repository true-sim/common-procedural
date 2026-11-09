package com.truesim.common.procedural.ecology.terrain;

import static org.assertj.core.api.Assertions.assertThat;

import com.truesim.common.procedural.ecology.AlpineSpecies;
import com.truesim.common.procedural.ecology.Ecology;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.common.procedural.ecology.EcologyTerrain;
import com.truesim.common.procedural.ecology.rocks.RockField;
import com.truesim.common.procedural.ecology.rocks.RockFieldParameters;
import com.truesim.effects.terrain.data.TerrainModifier;
import org.junit.jupiter.api.Test;

class TalusModifierTest {

    /** A wall facing south: a cliff from 1300 m down to 1000 m at z = -320, talus at 34 degrees to z = -170. */
    private static final EcologyTerrain WALL = (x, z) -> {
        double talus = Math.tan(Math.toRadians(34));
        if (z < -400) {
            return 1300 + 0.05 * (-400 - z);
        } else if (z < -320) {
            return 1300 - 300 * (z + 400) / 80;
        } else if (z < -170) {
            return 1000 - talus * (z + 320);
        }
        return 1000 - talus * 150 - 0.05 * (z + 170);
    };

    @Test
    void gravelAndRoughnessOverTheTalusOnly() {
        Ecology ecology = new Ecology(WALL, EcologyParameters.ALPS.withAnalysis(
            new EcologyParameters.Analysis(8, 512, 512, 150, 40, 32)), AlpineSpecies.ALL);
        RockField field = new RockField(ecology, RockFieldParameters.DEFAULTS);
        TerrainModifier.Bounds bounds = new TerrainModifier.Bounds(-500, -800, 500, 400);
        TalusModifier talus = new TalusModifier(field, TalusModifier.Parameters.DEFAULTS, bounds);
        assertThat(talus.bounds()).isSameAs(bounds);
        double[] cover = new double[TerrainModifier.MATERIALS];
        // on the cone: gravel; the height within half the roughness, varying
        double total = talus.materials(0, -250, 1, cover);
        assertThat(total).isGreaterThan(0.8).isEqualTo(cover[TerrainModifier.GRAVEL]);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int k = 0; k < 200; k++) {
            double x = k * 0.13;
            double h = talus.height(x, -250, 0.1, 1000);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        assertThat(min).isGreaterThanOrEqualTo(1000 - 0.04 - 1e-9);
        assertThat(max).isLessThanOrEqualTo(1000 + 0.04 + 1e-9);
        assertThat(max - min).isGreaterThan(0.02);
        // coarse samples see no roughness, the far ones no gravel
        assertThat(talus.height(3, -250, 4, 1000)).isEqualTo(1000);
        java.util.Arrays.fill(cover, 0);
        assertThat(talus.materials(0, -250, 64, cover)).isZero();
        // off the talus: nothing
        java.util.Arrays.fill(cover, 0);
        assertThat(talus.materials(0, 200, 1, cover)).isZero();
        assertThat(talus.height(0, 200, 0.1, 900)).isEqualTo(900);
        assertThat(talus.height(0, -360, 0.1, 1100)).isEqualTo(1100); // the cliff
        TalusModifier smooth = new TalusModifier(field, new TalusModifier.Parameters(1, 0, 0.4, 32), null);
        assertThat(smooth.height(0, -250, 0.1, 1000)).isEqualTo(1000);
    }
}
