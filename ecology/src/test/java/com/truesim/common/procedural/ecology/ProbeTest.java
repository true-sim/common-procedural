package com.truesim.common.procedural.ecology;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Prints the conditions and dominant species up synthetic slopes, for tuning by eye. */
class ProbeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "TRUESIM_PROBE", matches = "true")
    void slopes() {
        for (boolean south : new boolean[] {true, false}) {
            EcologyTerrain terrain = Terrains.slope(25, south, 0);
            Ecology ecology = new Ecology(terrain, EcologyParameters.ALPS.withAnalysis(Terrains.QUICK),
                AlpineSpecies.ALL);
            double tan = Math.tan(Math.toRadians(25));
            int n = ecology.species().size();
            double[] abundance = new double[n];
            double[] size = new double[n];
            System.out.println(south ? "SOUTH" : "NORTH");
            for (double alt = 400; alt <= 3400; alt += 100) {
                double z = (south ? -1 : 1) * alt / tan;
                Site site = ecology.conditions(0, z);
                double canopy = ecology.community(0, z, abundance, size);
                StringBuilder top = new StringBuilder();
                for (int k = 0; k < 6; k++) {
                    int best = -1;
                    for (int s = 0; s < n; s++) {
                        if (abundance[s] > 0.02 && (best < 0 || abundance[s] > abundance[best])) {
                            best = s;
                        }
                    }
                    if (best < 0) {
                        break;
                    }
                    top.append(String.format(Locale.ROOT, " %s %.2f/%.2f", ecology.species().get(best).id(),
                        abundance[best], size[best]));
                    abundance[best] = -1;
                }
                System.out.printf(Locale.ROOT,
                    "%5.0f m %s T %.1f ins %.2f F %.1f snow %.0f twi %.1f hand %.1f canopy %.2f |%s%n", alt,
                    site.belt, site.temperature, site.insolation, site.moisture, site.snowDays, site.wetness,
                    site.hand, canopy, top);
            }
        }
    }
}
