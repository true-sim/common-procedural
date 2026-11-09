package com.truesim.common.procedural.water;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.truesim.engine.settings.EngineSettings;
import com.truesim.engine.settings.Setting;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecipesTest {

    @Test
    void settingsDefaultToTheDefaults() {
        assertEquals(WaterParameters.DEFAULTS, WaterSettings.parameters(new EngineSettings()));
        Set<String> names = new HashSet<>();
        for (Setting<?> s : WaterSettings.ALL) {
            assertTrue(names.add(s.name()), "unique: " + s.name());
            assertTrue(s.name().startsWith("water."));
        }
        assertEquals(41, WaterSettings.ALL.size());
    }

    @Test
    void parametersAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> new WaterParameters.Flow(0, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WaterParameters.Channels(1, 1, 1, 1, 1, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WaterParameters.Banks(1, 1, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new WaterParameters.Falls(1, 0.5, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WaterParameters.Stones(1, 1, 2, 1, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new WaterParameters.Surface(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new WaterRegion(0, 0, 0, 1));
        WaterParameters p = WaterParameters.DEFAULTS.withBanks(new WaterParameters.Banks(1, 2, 0.5, 0.2, 0.1))
            .withFalls(WaterParameters.Falls.DEFAULTS).withSurface(new WaterParameters.Surface(256, 2, 6));
        assertEquals(256, p.surface().chunk());
        assertEquals(2, p.banks().minBank());
        WaterRegion square = WaterRegion.around(10, 20, 100);
        assertEquals(100, square.width());
        assertEquals(100, square.depth());
        assertTrue(square.contains(10, 20));
        assertFalse(square.contains(100, 20));
    }

    @Test
    void recipesReadEveryKey() {
        WaterRecipe recipe = WaterRecipe.parse("""
            # the valley
            region = -8000 -3000 6000 13000
            seed = 7
            cell = 12
            subsamples = 2
            channel-area = 300000
            runoff = 0.05
            burn-depth = 10
            min-length = 80
            width-coefficient = 3.5
            width-exponent = 0.45
            manning = 0.05
            fall-height = 10
            stone-density = 0.1
            chunk = 256
            spacing = 3
            across = 6
            courses = game://water/valley.courses
            material.surface = game://water/river.material   # flowing water
            material.falls = game://water/falls.material
            """);
        WaterParameters p = recipe.parameters();
        assertEquals(new WaterRegion(-8000, -3000, 6000, 13000), recipe.region());
        assertEquals(7, p.seed());
        assertEquals(new WaterParameters.Flow(12, 2, 300000, 0.05, 10, 80), p.flow());
        assertEquals(3.5, p.channels().widthCoefficient());
        assertEquals(0.45, p.channels().widthExponent());
        assertEquals(0.05, p.channels().manning());
        assertEquals(10, p.falls().minHeight());
        assertEquals(0.1, p.stones().density());
        assertEquals(new WaterParameters.Surface(256, 3, 6), p.surface());
        assertEquals("game://water/valley.courses", recipe.courses());
        assertEquals("game://water/river.material", recipe.material("surface"));
        assertEquals("", recipe.material("jets"));
        assertEquals(p, WaterRecipe.parse("region = -8000 -3000 6000 13000\n", p).parameters());
        assertThrows(IllegalArgumentException.class, () -> WaterRecipe.parse("seed = 1"));
        assertThrows(IllegalArgumentException.class, () -> WaterRecipe.parse("region = 1 2 3"));
        assertThrows(IllegalArgumentException.class, () -> WaterRecipe.parse("region 1 2 3 4"));
        assertThrows(IllegalArgumentException.class, () -> WaterRecipe.parse("region = 0 0 1 1\ncolour = blue"));
        assertThrows(IllegalArgumentException.class,
            () -> WaterRecipe.parse("region = 0 0 1 1\nmaterial.sky = x.material"));
    }

    @Test
    void recipesBuildTheirNetwork() {
        WaterRecipe recipe = WaterRecipe.parse("region = -1500 0 1500 4000\nstone-density = 0.01\n");
        WaterNetwork a = recipe.network(Landscapes.valley());
        WaterNetwork b = recipe.network(Landscapes.valley(), List.of());
        assertEquals(a.channels().size(), b.channels().size());
        assertEquals(a.stones(), b.stones());
        assertEquals(recipe.region(), a.region());
        assertEquals(recipe.parameters(), a.parameters());
    }

    @Test
    void watercoursesReadInEveryForm() throws Exception {
        List<Watercourse> courses = Watercourses.parse("""
            # swissTLM3D, exported as WKT
            LINESTRING Z (10 20 800, 30 40 799) ; Weisse Luetschine
            MULTILINESTRING ((0 0, 1 1), (2 2, 3 3, 4 4))
            course Staubbach
            width = 3
            discharge = 0.3
            100 200
            110, 210
            course Trickle
            5 5
            6 6
            """, (a, b, xz) -> {
                xz[0] = a * 2;
                xz[1] = b;
            });
        assertEquals(5, courses.size());
        assertEquals("Weisse Luetschine", courses.get(0).name());
        assertArrayEquals(new double[] {20, 20, 60, 40}, courses.get(0).points());
        assertEquals(2, courses.get(1).pointCount());
        assertEquals(3, courses.get(2).pointCount());
        assertEquals(8, courses.get(2).x(2));
        Watercourse staubbach = courses.get(3);
        assertEquals("Staubbach", staubbach.name());
        assertEquals(3, staubbach.width());
        assertEquals(0.3, staubbach.discharge());
        assertEquals(220, staubbach.x(1));
        assertEquals(210, staubbach.z(1));
        assertTrue(Double.isNaN(courses.get(4).width()));
        // written and read again
        List<Watercourse> again = Watercourses.parse(Watercourses.write(courses));
        assertEquals(courses, again);
        assertEquals(courses.hashCode(), again.hashCode());
        assertTrue(staubbach.toString().contains("Staubbach"));
        assertNotEquals(staubbach, courses.get(4));
        assertNotEquals(staubbach, "Staubbach");
        // a source gives those crossing a region
        Watercourses.Source source = Watercourses.of(courses);
        assertEquals(1, source.courses(new WaterRegion(150, 150, 300, 300)).size());
        assertTrue(Watercourses.NONE.courses(new WaterRegion(0, 0, 1, 1)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Watercourses.parse("1 2"));
        assertThrows(IllegalArgumentException.class, () -> Watercourses.parse("course a\nspeed = 3"));
        assertThrows(IllegalArgumentException.class, () -> Watercourses.parse("course a\n5"));
        assertThrows(IllegalArgumentException.class, () -> Watercourses.parse("LINESTRING 1 2, 3 4"));
        assertThrows(IllegalArgumentException.class, () -> Watercourse.of("one", 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Watercourse("w", new double[] {0, 0, 1, 1}, -1,
            Double.NaN));
    }

    @Test
    void linesAreSmoothedEvenlyAndLevelledMonotonically() {
        double[][] square = {{0, 10, 10}, {0, 0, 10}};
        double[][] cut = NetworkBuilder.chaikin(square);
        assertEquals(4, cut[0].length);
        assertEquals(0, cut[0][0]);
        assertEquals(10, cut[1][3]);
        double[][] two = {{0, 1}, {0, 1}};
        assertSame(two, NetworkBuilder.chaikin(two));
        double[][] even = NetworkBuilder.resample(new double[] {0, 10}, new double[] {0, 0}, 2.4);
        assertEquals(5, even[0].length);
        assertEquals(2.5, even[0][1], 1e-12);
        double[] level = NetworkBuilder.monotone(new double[] {10, 8, 9, 7, 7.5, 3});
        for (int i = 1; i < level.length; i++) {
            assertTrue(level[i] <= level[i - 1]);
        }
        assertEquals(8.5, level[1], 1e-12);
        assertEquals(8.5, level[2], 1e-12);
        assertEquals(10, level[0]);
        assertEquals(3, level[5]);
    }
}
