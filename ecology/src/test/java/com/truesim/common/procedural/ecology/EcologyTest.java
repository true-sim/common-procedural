package com.truesim.common.procedural.ecology;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.truesim.common.math.Maths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EcologyTest {

    private static final double TAN25 = Maths.tan(Maths.toRadians(25));

    private static Ecology slope(boolean south) {
        return new Ecology(Terrains.slope(25, south, 0), EcologyParameters.ALPS.withAnalysis(Terrains.QUICK),
            AlpineSpecies.ALL);
    }

    /** z of altitude {@code altitude} on the 25 degree slope. */
    private static double at(boolean south, double altitude) {
        return (south ? -1 : 1) * altitude / TAN25;
    }

    private static String dominant(Ecology ecology, double[] abundance, Layer layer) {
        int best = -1;
        for (int s = 0; s < abundance.length; s++) {
            if (ecology.species().get(s).layer() == layer && (best < 0 || abundance[s] > abundance[best])) {
                best = s;
            }
        }
        return abundance[best] > 0.02 ? ecology.species().get(best).id() : "none";
    }

    /** The altitude where the canopy's cover first falls below {@code cover} going up the slope. */
    private static double treeline(Ecology ecology, boolean south, double cover) {
        int n = ecology.species().size();
        for (double altitude = 1600; altitude < 2600; altitude += 5) {
            if (ecology.community(0, at(south, altitude), new double[n], new double[n]) < cover) {
                return altitude;
            }
        }
        return Double.NaN;
    }

    @Test
    void beltsAndTheirTreesFollowAltitudeAndShiftWithAspect() {
        Ecology south = slope(true);
        Ecology north = slope(false);
        int n = south.species().size();
        double[] abundance = new double[n];
        double[] size = new double[n];
        // belts at their altitudes (south slope: about 1 degree warmer)
        assertEquals(Belt.COLLINE, south.conditions(0, at(true, 600)).belt);
        assertEquals(Belt.MONTANE, south.conditions(0, at(true, 1200)).belt);
        assertEquals(Belt.SUBALPINE, south.conditions(0, at(true, 1900)).belt);
        assertEquals(Belt.ALPINE, south.conditions(0, at(true, 2600)).belt);
        assertEquals(Belt.NIVAL, south.conditions(0, at(true, 3300)).belt);
        // the canopy: beech and oak low, beech with fir and spruce montane, spruce, then larch at the treeline
        south.community(0, at(true, 600), abundance, size);
        assertTrue(List.of("beech", "oak").contains(dominant(south, abundance, Layer.CANOPY)));
        south.community(0, at(true, 1200), abundance, size);
        assertTrue(abundance[south.index("beech")] > 0.1 && abundance[south.index("fir")] > 0.1
            && abundance[south.index("spruce")] > 0.1, "a montane beech-fir-spruce forest");
        south.community(0, at(true, 1750), abundance, size);
        assertEquals("spruce", dominant(south, abundance, Layer.CANOPY));
        south.community(0, at(true, 2050), abundance, size);
        assertEquals("larch", dominant(south, abundance, Layer.CANOPY));
        // above the treeline: dwarf shrubs and krummholz, then alpine grassland with gentians, then lichens
        double canopy = south.community(0, at(true, 2350), abundance, size);
        assertTrue(canopy < 0.01);
        assertTrue(abundance[south.index("alpenrose")] + abundance[south.index("mugo")] > 0.3);
        south.community(0, at(true, 2550), abundance, size);
        assertTrue(abundance[south.index("grass")] > 0.8 && abundance[south.index("gentian")] > 0.3);
        // nival: no vascular plants on the plane (snow lies all year)
        south.community(0, at(true, 3300), abundance, size);
        assertEquals(0, abundance[south.index("grass")]);
        // the treeline is higher on the sunny slope
        double southLine = treeline(south, true, 0.1);
        double northLine = treeline(north, false, 0.1);
        assertTrue(southLine > 2050 && southLine < 2300, "south treeline " + southLine);
        assertTrue(southLine - northLine > 80 && southLine - northLine < 400,
            "south " + southLine + ", north " + northLine);
        double southBelt = Double.NaN;
        double northBelt = Double.NaN;
        for (double altitude = 1800; altitude < 2600; altitude += 5) {
            if (Double.isNaN(southBelt) && south.conditions(0, at(true, altitude)).belt == Belt.ALPINE) {
                southBelt = altitude;
            }
            if (Double.isNaN(northBelt) && north.conditions(0, at(false, altitude)).belt == Belt.ALPINE) {
                northBelt = altitude;
            }
        }
        assertTrue(southBelt > 2200 && northBelt < 2200 && southBelt - northBelt > 80,
            "alpine from " + southBelt + " (south), " + northBelt + " (north)");
    }

    @Test
    void theTreelineIsAnEcotoneTreesThinShrinkAndCrawl() {
        Ecology ecology = slope(true);
        int n = ecology.species().size();
        // the canopy's cover falls continuously: no step above 0.06 per 5 m
        double previous = ecology.community(0, at(true, 1700), new double[n], new double[n]);
        for (double altitude = 1705; altitude <= 2400; altitude += 5) {
            double cover = ecology.community(0, at(true, altitude), new double[n], new double[n]);
            assertTrue(Math.abs(cover - previous) < 0.06, "a step at " + altitude);
            previous = cover;
        }
        // the trees placed up a strip from 1850 to 2350 m: fewer and smaller band by band
        EcologyTile strip = ecology.region(0, -5040, 128, 1072);
        Instances plants = strip.instances();
        int bands = 5;
        int[] count = new int[bands];
        double[] height = new double[bands];
        int crawling = 0;
        for (int i = 0; i < plants.count(); i++) {
            if (ecology.species().get(plants.species(i)).layer() != Layer.CANOPY) {
                continue;
            }
            int band = (int) ((plants.y(i) - 1850) / 100);
            if (band >= 0 && band < bands) {
                count[band]++;
                height[band] += plants.height(i);
            }
            if (plants.width(i) > 1.3 * plants.height(i)) {
                crawling++;
            }
        }
        for (int b = 0; b < bands; b++) {
            height[b] /= Math.max(1, count[b]);
        }
        String trees = java.util.Arrays.toString(count) + " " + java.util.Arrays.toString(height);
        for (int b = 1; b < 4; b++) {
            // the closed forest below keeps its density (within a few percent); from the ecotone on, fewer trees
            assertTrue(count[b] < (b == 1 ? 1.05 : 1.0) * count[b - 1] && height[b] < height[b - 1], trees);
        }
        assertTrue(count[2] > 0 && height[2] > 0.1 && height[2] < 0.7, "trees in the ecotone, smaller: " + trees);
        assertEquals(0, count[4], trees);
        assertTrue(crawling > 0, "krummholz near the treeline");
    }

    @Test
    void plantsKeepTheirSpacing() {
        Ecology ecology = slope(true);
        EcologyTile tile = ecology.region(0, -2800, 256, 256); // about 1250 to 1370 m: montane forest
        Instances plants = tile.instances();
        EcologyParameters.Placement placement = ecology.parameters().placement();
        int n = plants.count();
        double[] radius = new double[n];
        int[] layer = new int[n];
        int trees = 0;
        for (int i = 0; i < n; i++) {
            Species species = ecology.species().get(plants.species(i));
            layer[i] = species.layer().ordinal();
            radius[i] = species.habit().crown() * plants.width(i) * placement.layers().get(layer[i]).spacing();
            trees += layer[i] == 0 ? 1 : 0;
        }
        assertTrue(trees > 100, "a forest: " + trees);
        int understory = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dx = plants.x(i) - plants.x(j);
                double dz = plants.z(i) - plants.z(j);
                double d = Math.sqrt(dx * dx + dz * dz);
                if (layer[i] == layer[j]) {
                    assertTrue(d >= radius[i] + radius[j] - 1e-9, "too close in a layer");
                } else {
                    int upper = layer[i] < layer[j] ? i : j;
                    int lower = upper == i ? j : i;
                    double shade = ecology.species().get(plants.species(lower)).shadeTolerance();
                    assertTrue(d >= radius[lower] + radius[upper] * Math.max(0.15, 1 - shade) - 1e-9,
                        "under a crown it cannot bear");
                    if (layer[upper] == 0 && d < radius[upper] / ecology.parameters().placement().layers().get(0)
                        .spacing()) {
                        understory++;
                    }
                }
            }
        }
        assertTrue(understory > 0, "shade-tolerant plants under the trees' crowns");
    }

    private static Map<Long, double[]> byVariant(Instances plants) {
        Map<Long, double[]> all = new HashMap<>();
        for (int i = 0; i < plants.count(); i++) {
            all.put(plants.variant(i), new double[] {plants.x(i), plants.y(i), plants.z(i), plants.yaw(i),
                plants.height(i), plants.width(i), plants.species(i), plants.age(i), plants.health(i)});
        }
        return all;
    }

    @Test
    void tilesStreamDeterministicallyWithMatchingSeams() {
        EcologyParameters parameters = EcologyParameters.ALPS.withAnalysis(Terrains.QUICK).withTiles(128, 4);
        Ecology first = new Ecology(Terrains.rugged(), parameters, AlpineSpecies.ALL);
        EcologyParameters.Analysis one = new EcologyParameters.Analysis(8, 512, 512, 150, 40, 9);
        Ecology second = new Ecology(Terrains.rugged(), parameters.withAnalysis(one), AlpineSpecies.ALL);
        long[][] order = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
        EcologyTile[] a = new EcologyTile[4];
        EcologyTile[] b = new EcologyTile[4];
        for (int k = 0; k < 4; k++) {
            a[k] = first.tile(order[k][0] + 20, order[k][1] - 3);
        }
        for (int k = 3; k >= 0; k--) {
            b[k] = second.tile(order[k][0] + 20, order[k][1] - 3);
        }
        EcologyTile whole = first.region(20 * 128, -3 * 128, 256, 256);
        Map<Long, double[]> union = new HashMap<>();
        int total = 0;
        for (int k = 0; k < 4; k++) {
            Map<Long, double[]> x = byVariant(a[k].instances());
            Map<Long, double[]> y = byVariant(b[k].instances());
            assertEquals(x.keySet(), y.keySet());
            for (Long key : x.keySet()) {
                assertArrayEquals(x.get(key), y.get(key));
            }
            for (int f = 0; f < EcologyTile.Field.ALL.length; f++) {
                assertArrayEquals(a[k].fields[f], b[k].fields[f]);
            }
            for (int s = 0; s < first.species().size(); s++) {
                assertArrayEquals(a[k].abundance(s), b[k].abundance(s));
            }
            union.putAll(x);
            total += x.size();
        }
        assertEquals(total, union.size(), "every plant in one tile only");
        Map<Long, double[]> region = byVariant(whole.instances());
        assertTrue(region.size() > 1000);
        assertEquals(region.keySet(), union.keySet(), "tiles and the region they cover hold the same plants");
        for (Long key : region.keySet()) {
            assertArrayEquals(region.get(key), union.get(key));
        }
        // seams: shared edge samples equal, and the region's samples equal the tiles'
        int samples = a[0].samples();
        for (int j = 0; j < samples; j++) {
            for (int s = 0; s < first.species().size(); s++) {
                assertEquals(a[0].abundance(s)[j * samples + samples - 1], a[1].abundance(s)[j * samples]);
                assertEquals(a[0].size(s)[(samples - 1) * samples + j], a[2].size(s)[j]);
            }
            for (int i = 0; i < samples; i++) {
                for (GroundCover c : GroundCover.ALL) {
                    assertEquals(whole.cover(c)[(j + samples - 1) * whole.samples() + i + samples - 1],
                        a[3].cover(c)[j * samples + i]);
                }
            }
        }
    }

    @Test
    void wetnessIndexOnAKnownValley() {
        // sides rising 0.4 to divides 1500 m out: parallel flow, so a = distance from the divide, TWI = ln(a / tan)
        EcologyParameters parameters = EcologyParameters.ALPS;
        Ecology ecology = new Ecology(Terrains.valley(0.4, 0.02), parameters, AlpineSpecies.ALL);
        double tan = Math.sqrt(0.4 * 0.4 + 0.02 * 0.02);
        for (double x = 200; x <= 1300; x += 100) {
            Site site = ecology.conditions(x, 0);
            double expected = Maths.log((1500 - x) / tan);
            assertEquals(expected, site.wetness, 0.15, "at " + x);
            // height above the thalweg it drains to: 0.4 per metre across, a little more downstream
            assertEquals(0.4 * x, site.hand, 0.05 * 0.4 * x + 2, "HAND at " + x);
        }
        Site thalweg = ecology.conditions(0, 0);
        assertTrue(thalweg.wetness > 12 && thalweg.hand < 1 && thalweg.streamArea > 1e6,
            thalweg.wetness + " " + thalweg.hand + " " + thalweg.streamArea);
        assertTrue(ecology.analysedBlocks() > 0 && ecology.analysisNanos() > 0);
    }

    @Test
    void wetlandsOnlyOnSaturatedFlatGroundAndTheRiparianBandFollowsTheChannel() {
        Ecology ecology = new Ecology(Terrains.meanders(), EcologyParameters.ALPS, AlpineSpecies.ALL);
        int wetlands = 0;
        for (double z = -1200; z <= 1200; z += 300) {
            double c = Terrains.channel(z);
            Site near = ecology.conditions(c + 14, z);
            assertTrue(near.riparian > 0.5, "riparian next to the channel at " + z + ": " + near.riparian);
            Site channel = ecology.conditions(c, z);
            assertTrue(channel.water > 0.3, "a river at " + z + ": " + channel.water);
            for (double off : new double[] {-150, 150}) {
                Site far = ecology.conditions(c + off, z);
                assertTrue(far.riparian < 0.1, "not riparian " + off + " m away at " + z + ": " + far.riparian);
                assertEquals(0, far.water);
            }
            for (double off = -300; off <= 300; off += 6) {
                Site site = ecology.conditions(c + off, z);
                if (site.wetland > 0) {
                    assertTrue(site.slope < ecology.parameters().hydrology().wetlandSlope(), "flat");
                    assertTrue(site.moisture >= 8, "saturated");
                }
                if (site.wetland > 0.3) {
                    wetlands++;
                }
            }
        }
        assertTrue(wetlands > 5, "wetlands along the river: " + wetlands);
        // alder and willow take the riparian ground; sedges and the like the wetland
        int n = ecology.species().size();
        double[] abundance = new double[n];
        ecology.community(Terrains.channel(0) + 14, 0, abundance, new double[n]);
        assertTrue(abundance[ecology.index("alder")] + abundance[ecology.index("willow")] > 0.2);
        EcologyTile tile = ecology.region(-256, -128, 512, 256);
        double wet = 0;
        double sedges = 0;
        for (int k = 0; k < tile.samples() * tile.samples(); k++) {
            wet += tile.cover(GroundCover.WETLAND)[k];
            sedges += tile.abundance(tile.index("sedge"))[k] + tile.abundance(tile.index("cotton-grass"))[k]
                + tile.abundance(tile.index("marsh-marigold"))[k];
        }
        assertTrue(wet > 10 && sedges > 5, "wetland cover " + wet + ", sedges " + sedges);
    }

    @Test
    void groundCoverSumsToOne() {
        Ecology ecology = new Ecology(Terrains.rugged(), EcologyParameters.ALPS.withAnalysis(Terrains.QUICK)
            .withTiles(128, 8), AlpineSpecies.ALL);
        for (long t = 0; t < 4; t++) {
            EcologyTile tile = ecology.tile(t * 7, -t * 5);
            for (int k = 0; k < tile.samples() * tile.samples(); k++) {
                double sum = 0;
                for (GroundCover c : GroundCover.ALL) {
                    float v = tile.cover(c)[k];
                    assertTrue(v >= -1e-6 && v <= 1 + 1e-6);
                    sum += v;
                }
                assertEquals(1, sum, 1e-5);
            }
            assertNotNull(tile.belt(0, 0));
            assertEquals(t * 7 * 128, tile.x0());
        }
    }

    @Test
    void meadowsReplaceTheForestOnGentleSlopes() {
        EcologyTerrain gentle = Terrains.slope(8, true, 900);
        EcologyParameters wild = EcologyParameters.ALPS.withAnalysis(Terrains.QUICK);
        Ecology natural = new Ecology(gentle, wild, AlpineSpecies.ALL);
        Ecology farmed = new Ecology(gentle, wild.withLandUse(EcologyParameters.LandUse.MEADOWS), AlpineSpecies.ALL);
        EcologyTile a = natural.region(0, -256, 256, 256);
        EcologyTile b = farmed.region(0, -256, 256, 256);
        double forestA = 0;
        double forestB = 0;
        double meadowB = 0;
        for (int k = 0; k < a.samples() * a.samples(); k++) {
            forestA += a.field(EcologyTile.Field.CANOPY)[k];
            forestB += b.field(EcologyTile.Field.CANOPY)[k];
            meadowB += b.cover(GroundCover.MEADOW)[k];
        }
        int samples = a.samples() * a.samples();
        assertTrue(forestB < 0.5 * forestA, forestA + " " + forestB);
        assertTrue(meadowB / samples > 0.4);
        assertTrue(b.instances().count() > 0 && a.instances().count() > 0);
    }

    @Test
    void insolationFavoursSouthSlopes() {
        Insolation sun = new Insolation(46.6);
        assertEquals(46.6, sun.latitude());
        assertEquals(1, sun.relative(0, 0), 1e-9);
        assertTrue(sun.relative(30, 180) > 1.05);
        assertTrue(sun.relative(30, 0) < 0.8);
        assertEquals(sun.relative(30, 90), sun.relative(30, 270), 0.02);
        assertEquals(sun.relative(30, -90), sun.relative(30, 270), 1e-9);
    }

    @Test
    void rangesAndClimate() {
        Range r = new Range(0, 1, 2, 4);
        assertEquals(0, r.response(-1));
        assertEquals(0.5, r.response(0.5), 1e-12);
        assertEquals(1, r.response(1.5));
        assertEquals(0.5, r.response(3), 1e-12);
        assertEquals(0, r.response(5));
        assertEquals(1, new Range(1, 1, 1, 1).response(1));
        assertEquals(1, Range.ANY.response(1e6));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new Range(2, 1, 3, 4));
        Climate climate = Climate.NORTHERN_ALPS;
        assertEquals(2100, climate.altitudeOf(6.4), 1);
        assertEquals(40, climate.snowDays(15.7), 1e-9);
        assertEquals(365, climate.snowDays(-5));
        assertEquals(0, climate.snowDays(40));
        assertEquals(165, climate.snowDays(9.64), 1e-9);
        assertEquals(climate, Climate.NORTHERN_ALPS.withLatitude(46.6));
        assertEquals(climate.hashCode(), climate.withLapseRate(0.006).hashCode());
        assertTrue(climate.withAspectWarming(1).toString().contains("aspectWarming=1.0"));
        assertEquals(4, climate.beltAltitudes().length);
        assertEquals(8, climate.snowKnots().length);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new Climate(46, 19, 0.006, 2, new double[] {1, 2}, new double[] {1, 2, 3, 4}));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new Climate(46, 19, 0.006, 2, new double[] {1, 2, 0, 3}, new double[] {1, 2}));
        assertEquals(Belt.NIVAL, climate.belt(-3));
    }

    @Test
    void speciesCatalogue() {
        assertEquals("spruce", AlpineSpecies.byId("spruce").id());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> AlpineSpecies.byId("palm"));
        for (Species species : AlpineSpecies.ALL) {
            assertTrue(species.shadeTolerance() >= 0 && species.shadeTolerance() <= 1);
            assertTrue(species.lightNeed() > 0 && species.lightNeed() < 1);
            assertTrue(species.source().length() > 10, species.id());
            assertEquals(1, AlpineSpecies.ALL.stream().filter(s -> s.id().equals(species.id())).count());
        }
        assertEquals(2.0, AlpineSpecies.BEECH.withWeight(2.0).habit().weight());
    }
}
