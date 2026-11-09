package com.truesim.common.procedural.fence;

import java.util.List;

/**
 * One fence's materials, wear and variation. The amounts are 0 to 1; each piece varies around them by the seed.
 *
 * @param wood the posts', rails' and boards' material
 * @param stone a wall's stone
 * @param metal the wire's
 * @param weathering how weathered: fresh wood (0) to silver grey (1); paint faded and peeling; zinc dulled, rusting
 * @param lichen how much lichen grows on it: grey-green and orange patches
 * @param moss how much moss grows at its foot (and on the tops of stones)
 * @param damage how much is missing or broken: missing rails, pickets, boards and stones (fallen beside the wall),
 *     broken rails hanging to the ground, slack wire, leaning posts
 * @param jitter how much posts lean, rails sag, spacing varies, from 0 (as drawn) to 1 (as built by hand) and beyond
 * @param gates where gates are: their centres' distances along the line, metres
 * @param gateWidth the gates' width
 */
public record FenceOptions(FenceMaterial wood, FenceMaterial stone, FenceMaterial metal, double weathering,
    double lichen, double moss, double damage, double jitter, List<Double> gates, double gateWidth) {

    /** Weathered spruce, gneiss, galvanised wire; well weathered, some lichen and moss, a little damage. */
    public static final FenceOptions DEFAULTS = new FenceOptions(FenceMaterial.WEATHERED_SPRUCE, FenceMaterial.GNEISS,
        FenceMaterial.GALVANISED, 0.75, 0.3, 0.35, 0.05, 1, List.of(), 1.2);

    public FenceOptions {
        gates = List.copyOf(gates);
        if (wood.part() != Fence.WOOD || stone.part() != Fence.STONE || metal.part() != Fence.METAL) {
            throw new IllegalArgumentException("Wood, stone and metal materials in their slots: " + wood + ", "
                + stone + ", " + metal);
        }
        for (double amount : new double[] {weathering, lichen, moss, damage}) {
            if (!(amount >= 0 && amount <= 1)) {
                throw new IllegalArgumentException("Amounts are 0 to 1: " + amount);
            }
        }
        if (!(jitter >= 0) || !(gateWidth > 0.3)) {
            throw new IllegalArgumentException("A jitter from 0 and a gate wider than 0.3 m");
        }
    }

    /** These options with {@code material} for the posts and rails. */
    public FenceOptions withWood(FenceMaterial material) {
        return new FenceOptions(material, stone, metal, weathering, lichen, moss, damage, jitter, gates, gateWidth);
    }

    /** These options with {@code material} for walls. */
    public FenceOptions withStone(FenceMaterial material) {
        return new FenceOptions(wood, material, metal, weathering, lichen, moss, damage, jitter, gates, gateWidth);
    }

    /** These options with {@code material} for wire. */
    public FenceOptions withMetal(FenceMaterial material) {
        return new FenceOptions(wood, stone, material, weathering, lichen, moss, damage, jitter, gates, gateWidth);
    }

    /** These options weathered {@code amount}. */
    public FenceOptions withWeathering(double amount) {
        return new FenceOptions(wood, stone, metal, amount, lichen, moss, damage, jitter, gates, gateWidth);
    }

    /** These options with {@code amount} of lichen. */
    public FenceOptions withLichen(double amount) {
        return new FenceOptions(wood, stone, metal, weathering, amount, moss, damage, jitter, gates, gateWidth);
    }

    /** These options with {@code amount} of moss. */
    public FenceOptions withMoss(double amount) {
        return new FenceOptions(wood, stone, metal, weathering, lichen, amount, damage, jitter, gates, gateWidth);
    }

    /** These options damaged {@code amount}. */
    public FenceOptions withDamage(double amount) {
        return new FenceOptions(wood, stone, metal, weathering, lichen, moss, amount, jitter, gates, gateWidth);
    }

    /** These options with {@code amount} of jitter. */
    public FenceOptions withJitter(double amount) {
        return new FenceOptions(wood, stone, metal, weathering, lichen, moss, damage, amount, gates, gateWidth);
    }

    /** These options with gates centred at {@code at} (metres along the line), {@code width} wide. */
    public FenceOptions withGates(List<Double> at, double width) {
        return new FenceOptions(wood, stone, metal, weathering, lichen, moss, damage, jitter, at, width);
    }
}
