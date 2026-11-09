package com.truesim.common.procedural.fence;

import com.truesim.annotations.AllowAllocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A fence's recipe: its type, options, line, ground and seed, and the materials of its submeshes. Recipes are small
 * text files ({@code .fence}) of {@code key = value} lines; {@code #} starts a comment; all but {@code type} and
 * {@code line} are optional:
 *
 * <pre>
 * # game://village/meadow.fence
 * type = schaerhag              # a FenceType preset: post-and-rail post-and-three-rails schaerhag picket wattle
 *                               # board stangenzaun dry-stone wire
 * seed = 7
 * line = 0 0, 12 3, 20 9        # x z points (metres, the ground's frame); x y z points carry their own heights
 * ground = valley               # flat (y 0), line (the points' heights), or a ground registered by that name
 * wood = weathered-larch        # a FenceMaterial preset: weathered-spruce weathered-larch fresh-wood painted-red
 * stone = gneiss                # painted-white painted-green; gneiss limestone; galvanised
 * weathering = 0.8              # 0 to 1
 * lichen = 0.4
 * moss = 0.5
 * damage = 0.1
 * jitter = 1                    # 0: as drawn, 1: as built by hand
 * gates = 6.5 18                # gates' centres, metres along the line
 * gate-width = 1.2
 * rails = 3                     # the type's rails (strands, boards, rows, courses), height and spacing
 * height = 1.3
 * spacing = 2.5
 * material.wood = game://materials/fence-wood.material
 * material.stone = game://materials/fence-stone.material
 * material.metal = game://materials/wire.material
 * </pre>
 *
 * A recipe is a value: equal recipes give identical fences (for a given ground, tuning and generator version).
 *
 * @param type the fence's type, its preset changed by the recipe's rails, height and spacing
 * @param options its materials and wear
 * @param seed the seed
 * @param line the line's x, z pairs
 * @param heights the line's points' heights, if the recipe gave them (else empty)
 * @param ground {@code flat}, {@code line}, or the name of a ground the game registers
 * @param materials the material paths of the submeshes (wood, stone, metal); empty strings for none
 */
public record FenceRecipe(FenceType type, FenceOptions options, long seed, List<Double> line, List<Double> heights,
    String ground, List<String> materials) {

    /** The material slots, in submesh order. */
    public static final List<String> SLOTS = List.of("wood", "stone", "metal");

    public FenceRecipe {
        line = List.copyOf(line);
        heights = List.copyOf(heights);
        materials = List.copyOf(materials);
        if (line.size() < 4 || line.size() % 2 != 0) {
            throw new IllegalArgumentException("A fence's line needs two x z points or more");
        }
        if (!heights.isEmpty() && heights.size() != line.size() / 2) {
            throw new IllegalArgumentException("A height for every point of the line, or none");
        }
        if (ground.equals("line") && heights.isEmpty()) {
            throw new IllegalArgumentException("ground = line needs x y z points");
        }
    }

    /** The line as x, z pairs. */
    @AllowAllocation("at load time")
    public double[] points() {
        double[] out = new double[line.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = line.get(i);
        }
        return out;
    }

    /** The ground the recipe names itself ({@code flat}, {@code line}), or {@code null} for a registered one. */
    @AllowAllocation("at load time")
    public FenceGround ownGround() {
        return switch (ground) {
            case "flat" -> FenceGround.FLAT;
            case "line" -> {
                double[] xyz = new double[3 * heights.size()];
                for (int i = 0; i < heights.size(); i++) {
                    xyz[3 * i] = line.get(2 * i);
                    xyz[3 * i + 1] = heights.get(i);
                    xyz[3 * i + 2] = line.get(2 * i + 1);
                }
                yield FenceGround.alongPath(xyz);
            }
            default -> null;
        };
    }

    /** The fence over {@code over}, tuned by {@code tuning}. */
    @AllowAllocation("at load time")
    public Fence generate(FenceGround over, FenceTuning tuning) {
        return Fences.generate(type, options, tuning, points(), over, seed);
    }

    /** Reads a recipe's text. */
    @AllowAllocation("parsing, at load time")
    public static FenceRecipe parse(String text) {
        FenceType type = null;
        FenceOptions o = FenceOptions.DEFAULTS;
        long seed = 0;
        List<Double> line = new ArrayList<>();
        List<Double> heights = new ArrayList<>();
        String ground = null;
        List<Double> gates = new ArrayList<>();
        double gateWidth = o.gateWidth();
        Integer rails = null;
        Double height = null;
        Double spacing = null;
        String[] materials = {"", "", ""};
        int number = 0;
        for (String raw : text.split("\n")) {
            number++;
            int hash = raw.indexOf('#');
            String row = (hash >= 0 ? raw.substring(0, hash) : raw).strip();
            if (row.isEmpty()) {
                continue;
            }
            int equals = row.indexOf('=');
            if (equals < 0) {
                throw new IllegalArgumentException("Line " + number + " is not 'key = value': " + raw);
            }
            String key = row.substring(0, equals).strip();
            String value = row.substring(equals + 1).strip();
            switch (key) {
                case "type" -> type = FenceType.preset(value);
                case "seed" -> seed = Long.parseLong(value);
                case "line" -> {
                    for (String point : value.split(",")) {
                        String[] xyz = point.strip().split("\\s+");
                        if (xyz.length == 2) {
                            line.add(Double.parseDouble(xyz[0]));
                            line.add(Double.parseDouble(xyz[1]));
                        } else if (xyz.length == 3) {
                            line.add(Double.parseDouble(xyz[0]));
                            line.add(Double.parseDouble(xyz[2]));
                            heights.add(Double.parseDouble(xyz[1]));
                        } else {
                            throw new IllegalArgumentException("Line " + number + ": a point is 'x z' or 'x y z': "
                                + point);
                        }
                    }
                }
                case "ground" -> ground = value;
                case "wood" -> o = o.withWood(FenceMaterial.preset(value));
                case "stone" -> o = o.withStone(FenceMaterial.preset(value));
                case "metal" -> o = o.withMetal(FenceMaterial.preset(value));
                case "weathering" -> o = o.withWeathering(Double.parseDouble(value));
                case "lichen" -> o = o.withLichen(Double.parseDouble(value));
                case "moss" -> o = o.withMoss(Double.parseDouble(value));
                case "damage" -> o = o.withDamage(Double.parseDouble(value));
                case "jitter" -> o = o.withJitter(Double.parseDouble(value));
                case "gates" -> {
                    for (String at : value.split("[\\s,]+")) {
                        gates.add(Double.parseDouble(at));
                    }
                }
                case "gate-width" -> gateWidth = Double.parseDouble(value);
                case "rails" -> rails = Integer.parseInt(value);
                case "height" -> height = Double.parseDouble(value);
                case "spacing" -> spacing = Double.parseDouble(value);
                default -> {
                    if (!key.startsWith("material.")) {
                        throw new IllegalArgumentException("Line " + number + ": unknown key '" + key + "'");
                    }
                    int slot = SLOTS.indexOf(key.substring("material.".length()));
                    if (slot < 0) {
                        throw new IllegalArgumentException("A fence has no material slot '" + key + "'");
                    }
                    materials[slot] = value;
                }
            }
        }
        if (type == null || line.isEmpty()) {
            throw new IllegalArgumentException("A fence recipe names its type and line");
        }
        if (!heights.isEmpty() && heights.size() * 2 != line.size()) {
            throw new IllegalArgumentException("Every point of the line with a height, or none");
        }
        if (rails != null) {
            type = type.withRails(rails);
        }
        if (height != null) {
            type = type.withHeight(height);
        }
        if (spacing != null) {
            type = type.withSpacing(spacing);
        }
        o = o.withGates(gates, gateWidth);
        String g = ground != null ? ground : heights.isEmpty() ? "flat" : "line";
        return new FenceRecipe(type, o, seed, line, heights, g, List.of(materials));
    }

    /**
     * What the generated meshes depend on, as canonical text: everything but the materials (the ground by name: a
     * registered ground's heights are the game's). Equal keys give identical meshes over the same ground.
     */
    @AllowAllocation("at load time")
    public String generationKey() {
        StringBuilder key = new StringBuilder();
        key.append("type=").append(type).append('\n');
        key.append("options=").append(options.wood().name()).append(',').append(options.stone().name()).append(',')
            .append(options.metal().name()).append(',').append(bits(options.weathering())).append(',')
            .append(bits(options.lichen())).append(',').append(bits(options.moss())).append(',')
            .append(bits(options.damage())).append(',').append(bits(options.jitter())).append(',')
            .append(bits(options.gateWidth()));
        for (double gate : options.gates()) {
            key.append(',').append(bits(gate));
        }
        key.append("\nseed=").append(seed).append("\nline=");
        for (double v : line) {
            key.append(bits(v)).append(' ');
        }
        key.append("\nheights=");
        for (double v : heights) {
            key.append(bits(v)).append(' ');
        }
        key.append("\nground=").append(ground).append('\n');
        return key.toString();
    }

    private static String bits(double value) {
        return Long.toHexString(Double.doubleToLongBits(value));
    }

    /** The recipe's text, as {@link #parse} reads it. */
    @AllowAllocation("at save time")
    public String text() {
        StringBuilder out = new StringBuilder();
        out.append("type = ").append(type.name()).append('\n');
        out.append("rails = ").append(type.rails()).append('\n');
        out.append("height = ").append(type.height()).append('\n');
        out.append("spacing = ").append(type.spacing()).append('\n');
        out.append("seed = ").append(seed).append('\n');
        out.append("line = ");
        for (int i = 0; i < line.size() / 2; i++) {
            out.append(i > 0 ? ", " : "").append(line.get(2 * i));
            if (!heights.isEmpty()) {
                out.append(' ').append(heights.get(i));
            }
            out.append(' ').append(line.get(2 * i + 1));
        }
        out.append('\n').append("ground = ").append(ground).append('\n');
        out.append(String.format(Locale.ROOT, "wood = %s%nstone = %s%nmetal = %s%n", options.wood().name(),
            options.stone().name(), options.metal().name()));
        out.append("weathering = ").append(options.weathering()).append('\n');
        out.append("lichen = ").append(options.lichen()).append('\n');
        out.append("moss = ").append(options.moss()).append('\n');
        out.append("damage = ").append(options.damage()).append('\n');
        out.append("jitter = ").append(options.jitter()).append('\n');
        if (!options.gates().isEmpty()) {
            out.append("gates =");
            for (double gate : options.gates()) {
                out.append(' ').append(gate);
            }
            out.append('\n');
        }
        out.append("gate-width = ").append(options.gateWidth()).append('\n');
        for (int s = 0; s < SLOTS.size(); s++) {
            if (s < materials.size() && !materials.get(s).isEmpty()) {
                out.append("material.").append(SLOTS.get(s)).append(" = ").append(materials.get(s)).append('\n');
            }
        }
        return out.toString();
    }
}
