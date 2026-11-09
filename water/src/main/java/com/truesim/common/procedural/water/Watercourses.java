package com.truesim.common.procedural.water;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mapped watercourses: the hook real data comes in through, and a small text format ({@code .courses}) for them.
 *
 * <p><b>Where survey data exists</b>, a {@link Source} gives the watercourses of a region and the network follows
 * them ({@link WaterNetwork#build(com.truesim.common.procedural.ecology.EcologyTerrain, WaterRegion, WaterParameters,
 * List)}); without, the network comes from the terrain's flow alone. swisstopo's swissTLM3D watercourses
 * ({@code TLM_FLIESSGEWAESSER}) are published for the whole country at once (not per tile, as swissALTI3D is), so they
 * come in as a file: exported once with {@code ogr2ogr -f CSV -lco GEOMETRY=AS_WKT -spat <bbox> courses.csv
 * swissTLM3D.gpkg TLM_FLIESSGEWAESSER} and cut to lines of WKT, or written by hand for a few streams.
 *
 * <p><b>The format</b>: {@code #} starts a comment. A watercourse is either a WKT line string with an optional name
 * after a semicolon, or a block of {@code course <name>}, optional {@code width = <metres>} and
 * {@code discharge = <cubic metres a second>} lines, and coordinate pairs, one a line, downstream:
 *
 * <pre>
 * LINESTRING Z (2635250.1 1159530.4 795.2, 2635240.8 1159610.0 794.9) ; Weisse Luetschine
 * course Staubbach
 * width = 3
 * discharge = 0.3
 * 2634820 1159020
 * 2634870 1159010
 * </pre>
 *
 * Coordinates are projected into the terrain's frame by a {@link Projection} (LV95 to the site's local frame for
 * swisstopo data); a {@code MULTILINESTRING} gives a watercourse for each of its parts.
 */
public final class Watercourses {

    /** Where mapped watercourses come from: survey data, a file, a game's hand-drawn streams. */
    @FunctionalInterface
    public interface Source {

        /** The watercourses crossing {@code region}. */
        List<Watercourse> courses(WaterRegion region) throws IOException;
    }

    /** Projects the data's coordinates (easting and northing, say) into the terrain's frame: x east, z south. */
    @FunctionalInterface
    public interface Projection {

        /** Coordinates as the terrain's frame and metres ({@code x}, {@code z} as given). */
        Projection IDENTITY = (a, b, xz) -> {
            xz[0] = a;
            xz[1] = b;
        };

        /** The frame's x and z of the data's ({@code a}, {@code b}) into {@code xz}. */
        void project(double a, double b, double[] xz);
    }

    /** No watercourses: the network from the flow alone. */
    public static final Source NONE = region -> List.of();

    private Watercourses() {
    }

    /** A source of {@code courses}, giving those with a point in the region asked for. */
    public static Source of(List<Watercourse> courses) {
        List<Watercourse> all = List.copyOf(courses);
        return region -> {
            List<Watercourse> inside = new ArrayList<>();
            for (Watercourse course : all) {
                for (int i = 0; i < course.pointCount(); i++) {
                    if (region.contains(course.x(i), course.z(i))) {
                        inside.add(course);
                        break;
                    }
                }
            }
            return inside;
        };
    }

    /** Reads watercourses in the frame's coordinates. */
    public static List<Watercourse> parse(String text) {
        return parse(text, Projection.IDENTITY);
    }

    /** Reads watercourses, projecting their coordinates. */
    public static List<Watercourse> parse(String text, Projection projection) {
        List<Watercourse> courses = new ArrayList<>();
        Block block = null;
        int number = 0;
        double[] xz = new double[2];
        for (String raw : text.split("\n")) {
            number++;
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).strip();
            if (line.isEmpty()) {
                continue;
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("LINESTRING") || upper.startsWith("MULTILINESTRING")) {
                block = finish(block, courses);
                wkt(line, projection, courses, number);
            } else if (upper.startsWith("COURSE")) {
                block = finish(block, courses);
                block = new Block(line.substring("course".length()).strip());
            } else if (block == null) {
                throw new IllegalArgumentException("Line " + number + " is outside a course: " + raw);
            } else if (line.indexOf('=') >= 0) {
                String key = line.substring(0, line.indexOf('=')).strip();
                double value = Double.parseDouble(line.substring(line.indexOf('=') + 1).strip());
                switch (key) {
                    case "width" -> block.width = value;
                    case "discharge" -> block.discharge = value;
                    default -> throw new IllegalArgumentException("Line " + number + ": unknown key '" + key + "'");
                }
            } else {
                String[] parts = line.split("[\\s,]+");
                if (parts.length < 2) {
                    throw new IllegalArgumentException("Line " + number + " is not a coordinate pair: " + raw);
                }
                projection.project(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), xz);
                block.points.add(xz[0]);
                block.points.add(xz[1]);
            }
        }
        finish(block, courses);
        return courses;
    }

    /** {@code courses} in the format (the frame's coordinates, block form), to be parsed again. */
    public static String write(List<Watercourse> courses) {
        StringBuilder out = new StringBuilder();
        for (Watercourse course : courses) {
            out.append("course ").append(course.name()).append('\n');
            if (!Double.isNaN(course.width())) {
                out.append("width = ").append(course.width()).append('\n');
            }
            if (!Double.isNaN(course.discharge())) {
                out.append("discharge = ").append(course.discharge()).append('\n');
            }
            for (int i = 0; i < course.pointCount(); i++) {
                out.append(course.x(i)).append(' ').append(course.z(i)).append('\n');
            }
        }
        return out.toString();
    }

    private static Block finish(Block block, List<Watercourse> courses) {
        if (block != null) {
            double[] points = new double[block.points.size()];
            for (int i = 0; i < points.length; i++) {
                points[i] = block.points.get(i);
            }
            courses.add(new Watercourse(block.name, points, block.width, block.discharge));
        }
        return null;
    }

    private static void wkt(String line, Projection projection, List<Watercourse> courses, int number) {
        int semicolon = line.indexOf(';');
        String name = semicolon >= 0 ? line.substring(semicolon + 1).strip() : "";
        String geometry = semicolon >= 0 ? line.substring(0, semicolon) : line;
        int open = geometry.indexOf('(');
        int close = geometry.lastIndexOf(')');
        if (open < 0 || close < open) {
            throw new IllegalArgumentException("Line " + number + " is not a WKT line string: " + line);
        }
        double[] xz = new double[2];
        // each innermost parenthesised list is a part: one for a LINESTRING, several for a MULTILINESTRING
        String body = geometry.substring(open + 1, close);
        for (String part : body.replace("(", "").split("\\)")) {
            String coordinates = part.strip();
            if (coordinates.startsWith(",")) {
                coordinates = coordinates.substring(1).strip();
            }
            if (coordinates.isEmpty()) {
                continue;
            }
            List<Double> points = new ArrayList<>();
            for (String pair : coordinates.split(",")) {
                String[] values = pair.strip().split("\\s+");
                projection.project(Double.parseDouble(values[0]), Double.parseDouble(values[1]), xz);
                points.add(xz[0]);
                points.add(xz[1]);
            }
            double[] array = new double[points.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = points.get(i);
            }
            courses.add(Watercourse.of(name, array));
        }
    }

    private static final class Block {
        private final String name;
        private final List<Double> points = new ArrayList<>();
        private double width = Double.NaN;
        private double discharge = Double.NaN;

        Block(String name) {
            this.name = name;
        }
    }
}
