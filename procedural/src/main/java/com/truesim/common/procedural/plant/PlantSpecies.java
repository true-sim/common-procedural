package com.truesim.common.procedural.plant;

import com.truesim.common.procedural.flora.Albedo;
import com.truesim.common.procedural.flora.FlowerShape;
import com.truesim.common.procedural.flora.FlowerShape.Form;
import com.truesim.common.procedural.flora.FlowerShape.Tip;
import com.truesim.common.procedural.flora.FruitShape;
import com.truesim.common.procedural.flora.Outline;
import com.truesim.common.procedural.flora.Palette;
import com.truesim.common.procedural.flora.Palette.Part;
import com.truesim.common.procedural.flora.Phenology;
import com.truesim.common.procedural.flora.Ramp;
import java.util.List;

/**
 * Everything that makes a small plant or clump: a rosette of basal leaves, stems with their leaves and side branches,
 * a flower (or a grass's spike) at the end of each, when it flowers, and its colors. Metres, radians and days of the
 * year. The presets are meadow plants of the Alps, for effects-vegetation to scatter.
 *
 * @param name a name, e.g. for logs and recipes
 * @param height the mean height of the flowering stems (a grass's culms), metres
 * @param heightVariation its random variation, a fraction
 * @param spread how far apart stems and leaves start around the foot, metres (a clump's radius)
 * @param minStems the fewest flowering stems
 * @param maxStems the most flowering stems
 * @param stemLean radians stems lean out from upright, at most
 * @param stemNod radians a stem bends over toward its top (nodding heads, arching culms)
 * @param stemRadius a stem's radius at its foot, metres
 * @param branches side branches with a flower each, per stem, at most
 * @param basal the leaves from the foot: a rosette, or a grass's blades
 * @param stemLeaves the leaves along each stem (in opposite pairs when even)
 * @param flower the flowers, or {@code null} (grasses: their spikes show from the start)
 * @param seedHead what follows a flower that sets fruit, or {@code null}; a grass's spike
 * @param nodding radians a head turns toward the ground
 * @param phenology when it flowers and seeds
 * @param fruitSet the share of flowers setting a seed head
 * @param greenUp the day its leaves have grown in spring
 * @param senescence the day its leaves begin to wither (they are brown 40 days later and through the winter)
 * @param flutter the leaf weight at the tips of leaves and petals
 * @param grass whether its leaves are grass blades (the palette's blade ramp) rather than leaves (its leaf ramp)
 * @param palette its colors
 */
public record PlantSpecies(String name, double height, double heightVariation, double spread, int minStems,
    int maxStems, double stemLean, double stemNod, double stemRadius, int branches, LeafSpec basal,
    LeafSpec stemLeaves, FlowerShape flower, FruitShape seedHead, double nodding, Phenology phenology,
    double fruitSet, double greenUp, double senescence, double flutter, boolean grass, Palette palette) {

    public PlantSpecies {
        if (height <= 0 || minStems < 0 || maxStems < minStems || stemRadius <= 0 || branches < 0
            || (flower == null && seedHead == null) || fruitSet < 0 || fruitSet > 1 || greenUp >= senescence) {
            throw new IllegalArgumentException("Not a plant: " + name);
        }
    }

    // Petal and fruit albedos as for the trees (TreeSpecies): linear sRGB after typical published reflectance spectra
    // (FReD, Arnold et al. 2010; buttercup petals' gloss: Vignolini et al., "Directional scattering from the glossy
    // flower of Ranunculus", J. R. Soc. Interface 2012), approximations for rendering.

    /** Trumpet gentian (Gentiana acaulis): a rosette of leathery leaves and one deep blue trumpet, May and June. */
    public static final PlantSpecies GENTIAN = new PlantSpecies("gentian", 0.03, 0.2, 0.025, 1, 2, 0.2, 0, 0.002, 0,
        new LeafSpec(5, 8, 0.035, 0.013, Outline.ELLIPTIC, 0.25, -0.2, 0.2, 1, 0),
        new LeafSpec(0, 2, 0.015, 0.006, Outline.ELLIPTIC, 0.8, 0, 0.2, 1, 0),
        new FlowerShape(Form.BELL, 5, 0.045, 0.017, Tip.POINTED, 0.55, 0, 0, 0.01, 0, 0, 0), null, 0,
        new Phenology(14, 130, 170, 16, 200, 210, 10, 220, 240), 0, 100, 270, 0.3, false,
        Palette.DEFAULT
            .with(Part.PETAL, new Ramp(a(0.04, 0.08, 0.14), a(0.025, 0.05, 0.45), a(0.08, 0.07, 0.24))));

    /** Arnica (Arnica montana): a flat rosette, a stem with one pair of leaves, orange-yellow heads in July. */
    public static final PlantSpecies ARNICA = new PlantSpecies("arnica", 0.4, 0.2, 0.04, 1, 2, 0.15, 0.1, 0.0022, 2,
        new LeafSpec(4, 6, 0.1, 0.04, Outline.OVATE, 0.12, -0.15, 0.15, 1, 0),
        new LeafSpec(2, 2, 0.05, 0.015, Outline.LANCEOLATE, 0.7, -0.2, 0.2, 1, 0),
        new FlowerShape(Form.COMPOSITE, 13, 0.025, 0.007, Tip.TOOTHED, -0.35, 0, 10, 0.01, 0.009, 0.005, 0), null, 0,
        new Phenology(14, 170, 205, 15, 220, 230, 10, 240, 260), 0, 120, 255, 0.4, false,
        Palette.DEFAULT
            .with(Part.PETAL, new Ramp(a(0.25, 0.3, 0.06), a(0.78, 0.42, 0.04), a(0.45, 0.25, 0.06)))
            .with(Part.CENTRE, Ramp.of(a(0.75, 0.38, 0.03))));

    /** Alpine aster (Aster alpinus): a low tuft, violet rays around a yellow disc, July and August. */
    public static final PlantSpecies ALPINE_ASTER = new PlantSpecies("alpine-aster", 0.15, 0.2, 0.03, 1, 3, 0.25, 0.05, 0.0016, 0,
        new LeafSpec(5, 8, 0.05, 0.012, Outline.SPATULATE, 0.35, -0.2, 0.2, 1, 0),
        new LeafSpec(2, 3, 0.022, 0.005, Outline.LANCEOLATE, 0.6, 0, 0.2, 1, 0),
        new FlowerShape(Form.COMPOSITE, 28, 0.018, 0.0028, Tip.ROUND, 0.05, 0, 12, 0.006, 0.007, 0.003, 0), null, 0,
        new Phenology(12, 185, 225, 17, 240, 250, 10, 255, 270), 0, 120, 260, 0.4, false,
        Palette.DEFAULT
            .with(Part.PETAL, new Ramp(a(0.2, 0.15, 0.3), a(0.32, 0.2, 0.62), a(0.3, 0.25, 0.4)))
            .with(Part.CENTRE, Ramp.of(a(0.75, 0.55, 0.06))));

    /**
     * Edelweiss (Leontopodium nivale subsp. alpinum): grey woolly leaves, a star of white felted bracts around a few
     * small yellowish heads, July to September.
     */
    public static final PlantSpecies EDELWEISS = new PlantSpecies("edelweiss", 0.12, 0.2, 0.025, 1, 3, 0.2, 0,
        0.0015, 0,
        new LeafSpec(5, 8, 0.03, 0.006, Outline.LANCEOLATE, 0.4, -0.1, 0.25, 1, 0),
        new LeafSpec(3, 3, 0.016, 0.004, Outline.LANCEOLATE, 0.6, 0, 0.2, 1, 0),
        new FlowerShape(Form.STAR, 8, 0.022, 0.007, Tip.POINTED, 0.15, 0, 0, 0.005, 0.0035, 0.003, 6), null, 0,
        new Phenology(14, 190, 245, 28, 250, 260, 10, 270, 300), 0, 130, 265, 0.3, false,
        Palette.DEFAULT
            .with(Part.PETAL, new Ramp(a(0.4, 0.42, 0.36), a(0.62, 0.63, 0.57), a(0.45, 0.43, 0.38)))
            .with(Part.CENTRE, Ramp.of(a(0.52, 0.48, 0.3)))
            .with(Part.STALK, Ramp.of(a(0.2, 0.23, 0.17)))
            .with(Part.LEAF, new Ramp(a(0.2, 0.24, 0.17), a(0.22, 0.24, 0.16), a(0.25, 0.22, 0.15))));

    /**
     * Meadow buttercup (Ranunculus acris): palmately lobed leaves on long stalks, branched stems of glossy yellow cups,
     * May to July.
     */
    public static final PlantSpecies BUTTERCUP = new PlantSpecies("buttercup", 0.45, 0.2, 0.04, 1, 2, 0.2, 0.1,
        0.0018, 3,
        new LeafSpec(3, 6, 0.035, 0.016, Outline.SPATULATE, 0.6, -0.4, 0.15, 5, 0.09),
        new LeafSpec(1, 1, 0.03, 0.006, Outline.LANCEOLATE, 0.6, -0.2, 0.1, 3, 0),
        new FlowerShape(Form.SIMPLE, 5, 0.011, 0.011, Tip.ROUND, 0.45, 0, 5, 0.005, 0.003, 0.002, 0), null, 0,
        new Phenology(12, 130, 195, 28, 210, 220, 10, 230, 250), 0, 105, 265, 0.5, false,
        Palette.DEFAULT
            .with(Part.PETAL, new Ramp(a(0.45, 0.42, 0.06), a(0.8, 0.62, 0.03), a(0.6, 0.5, 0.1)))
            .with(Part.CENTRE, Ramp.of(a(0.7, 0.55, 0.03))));

    /** Red clover (Trifolium pratense): trifoliate leaves, round pink-purple heads, June to September. */
    public static final PlantSpecies CLOVER = new PlantSpecies("clover", 0.22, 0.2, 0.05, 1, 3, 0.35, 0.15, 0.0016,
        0,
        new LeafSpec(4, 8, 0.025, 0.013, Outline.ELLIPTIC, 0.7, -0.2, 0.1, 3, 0.07),
        new LeafSpec(1, 1, 0.022, 0.011, Outline.ELLIPTIC, 0.4, -0.1, 0.1, 3, 0.01),
        new FlowerShape(Form.GLOBE, 9, 0.012, 0.012, Tip.ROUND, 0, 0, 2, 0.012, 0, 0, 0), null, 0,
        new Phenology(12, 160, 250, 38, 260, 265, 10, 270, 290), 0, 105, 275, 0.4, false,
        Palette.DEFAULT
            .with(Part.PETAL, new Ramp(a(0.3, 0.2, 0.2), a(0.45, 0.1, 0.3), a(0.18, 0.1, 0.06))));

    /**
     * A meadow grass clump (after Festuca and Poa of Alpine meadows): arching blades, culms with spike-like panicles
     * flowering in June, ripening to straw in July and August; the blades straw-brown from October.
     */
    public static final PlantSpecies GRASS = new PlantSpecies("grass", 0.45, 0.25, 0.04, 3, 8, 0.3, 0.25, 0.0012, 0,
        new LeafSpec(45, 70, 0.26, 0.004, Outline.BLADE, 0.95, -1.5, 0.35, 1, 0),
        new LeafSpec(1, 1, 0.1, 0.003, Outline.BLADE, 0.5, -0.5, 0.3, 1, 0),
        null, new FruitShape(FruitShape.Form.SPIKE, 0.003, 7, 0.15, 0, 9, 0, 0), 0.2,
        new Phenology(20, 160, 185, 12, 195, 215, 15, 260, 330), 1, 95, 280, 0.6, true,
        Palette.DEFAULT
            .with(Part.FRUIT, new Ramp(a(0.1, 0.16, 0.05), a(0.17, 0.15, 0.09), a(0.4, 0.35, 0.2)))
            .with(Part.FRUIT_ALT, new Ramp(a(0.1, 0.16, 0.05), a(0.17, 0.15, 0.09), a(0.4, 0.35, 0.2))));


    /** Common sedge (Carex nigra): a tuft of narrow keeled leaves, three-edged culms with dark spikes, May to July. */
    public static final PlantSpecies SEDGE = new PlantSpecies("sedge", 0.35, 0.25, 0.035, 2, 6, 0.25, 0.15, 0.0012, 0,
        new LeafSpec(30, 50, 0.3, 0.003, Outline.BLADE, 1.05, -1.3, 0.5, 1, 0),
        new LeafSpec(1, 1, 0.08, 0.0025, Outline.BLADE, 0.6, -0.4, 0.4, 1, 0),
        null, new FruitShape(FruitShape.Form.SPIKE, 0.0035, 4, 0.15, 0, 7, 0, 0), 0.1,
        new Phenology(15, 150, 175, 12, 190, 210, 15, 250, 320), 1, 100, 270, 0.5, true,
        Palette.DEFAULT
            .with(Part.BLADE, new Ramp(a(0.06, 0.1, 0.04), a(0.14, 0.15, 0.07), a(0.3, 0.24, 0.13)))
            .with(Part.FRUIT, new Ramp(a(0.1, 0.13, 0.05), a(0.06, 0.05, 0.04), a(0.15, 0.1, 0.06)))
            .with(Part.FRUIT_ALT, new Ramp(a(0.1, 0.13, 0.05), a(0.06, 0.05, 0.04), a(0.15, 0.1, 0.06))));

    /**
     * Cotton grass (Eriophorum angustifolium): grassy leaves in wet hollows, nodding heads that flower inconspicuously in
     * May and turn to tufts of white cotton in June and July.
     */
    public static final PlantSpecies COTTON_GRASS = new PlantSpecies("cotton-grass", 0.4, 0.2, 0.03, 2, 5, 0.2, 0.2,
        0.0013, 0,
        new LeafSpec(12, 22, 0.25, 0.004, Outline.BLADE, 1.0, -1.2, 0.5, 1, 0),
        new LeafSpec(1, 1, 0.08, 0.003, Outline.BLADE, 0.6, -0.4, 0.4, 1, 0),
        null, new FruitShape(FruitShape.Form.BERRY, 0.014, 1.3, 0.15, 0, 9, 0, 0), 0.6,
        new Phenology(15, 130, 160, 10, 165, 185, 12, 220, 250), 1, 100, 265, 0.6, true,
        Palette.DEFAULT
            .with(Part.FRUIT, new Ramp(a(0.12, 0.16, 0.06), a(0.5, 0.5, 0.45), a(0.78, 0.78, 0.75)))
            .with(Part.FRUIT_ALT, new Ramp(a(0.12, 0.16, 0.06), a(0.5, 0.5, 0.45), a(0.78, 0.78, 0.75))));

    /**
     * Soft rush (Juncus effusus): a dense clump of upright, smooth green stems in wet ground, brown flower clusters
     * from their side in summer.
     */
    public static final PlantSpecies RUSH = new PlantSpecies("rush", 0.55, 0.2, 0.05, 3, 6, 0.15, 0.05, 0.0015, 0,
        new LeafSpec(40, 70, 0.55, 0.003, Outline.BLADE, 1.4, -0.35, 0.9, 1, 0),
        LeafSpec.NONE,
        null, new FruitShape(FruitShape.Form.BERRY, 0.009, 1.4, 0.15, 0, 9, 0, 0), 1.0,
        new Phenology(15, 170, 200, 15, 215, 235, 15, 280, 400), 1, 95, 290, 0.3, true,
        Palette.DEFAULT
            .with(Part.BLADE, new Ramp(a(0.07, 0.13, 0.04), a(0.1, 0.14, 0.05), a(0.25, 0.22, 0.12)))
            .with(Part.FRUIT, new Ramp(a(0.12, 0.13, 0.05), a(0.18, 0.12, 0.06), a(0.2, 0.12, 0.06)))
            .with(Part.FRUIT_ALT, new Ramp(a(0.12, 0.13, 0.05), a(0.18, 0.12, 0.06), a(0.2, 0.12, 0.06))));

    /**
     * Marsh marigold (Caltha palustris): round, glossy leaves on long stalks and branched stems of large golden cups,
     * April to June, by springs and streams.
     */
    public static final PlantSpecies MARSH_MARIGOLD = new PlantSpecies("marsh-marigold", 0.25, 0.2, 0.06, 2, 4, 0.3,
        0.1, 0.0028, 2,
        new LeafSpec(5, 8, 0.07, 0.07, Outline.ELLIPTIC, 0.5, -0.25, 0.25, 1, 0.12),
        new LeafSpec(1, 2, 0.045, 0.045, Outline.ELLIPTIC, 0.4, -0.2, 0.2, 1, 0.02),
        new FlowerShape(Form.SIMPLE, 5, 0.016, 0.015, Tip.ROUND, 0.35, 0, 0, 0.005, 0.005, 0.002, 0), null, 0,
        new Phenology(12, 110, 160, 22, 175, 185, 10, 190, 210), 0, 95, 250, 0.4, false,
        Palette.DEFAULT
            .with(Part.PETAL, new Ramp(a(0.4, 0.38, 0.05), a(0.8, 0.6, 0.03), a(0.6, 0.48, 0.1)))
            .with(Part.CENTRE, Ramp.of(a(0.72, 0.56, 0.04))));

    /** Every preset: meadow flowers, grass, then plants of wet ground. */
    public static final List<PlantSpecies> PRESETS = List.of(GENTIAN, ARNICA, ALPINE_ASTER, EDELWEISS, BUTTERCUP,
        CLOVER, GRASS, SEDGE, COTTON_GRASS, RUSH, MARSH_MARIGOLD);

    private static Albedo a(double r, double g, double b) {
        return new Albedo(r, g, b);
    }
}
