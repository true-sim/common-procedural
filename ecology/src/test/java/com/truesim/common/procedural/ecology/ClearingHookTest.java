package com.truesim.common.procedural.ecology;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A clearing keeps plants off the ground it clears, and changes nothing elsewhere or when it clears nothing. */
class ClearingHookTest {

    @Test
    void plantsKeepOffClearedGround() {
        EcologyTerrain slope = Terrains.slope(12, true, 1100);
        Ecology ecology = new Ecology(slope, EcologyParameters.ALPS.withAnalysis(Terrains.QUICK).withTiles(128, 4)
            .withSeed(5), AlpineSpecies.ALL);
        assertThat(ecology.clearing()).isSameAs(EcologyClearing.NONE);
        // a road along z = 64: no trees within 6 m of it, no herbs on it
        EcologyClearing road = (x, z, layer) -> Math.abs(z - 64) < (layer == Layer.CANOPY ? 6 : 2) ? 0 : 1;
        Ecology cleared = ecology.withClearing(road);
        assertThat(cleared.clearing()).isSameAs(road);
        Instances all = ecology.tile(0, 0).instances();
        Instances kept = cleared.tile(0, 0).instances();
        assertThat(all.count()).isGreaterThan(kept.count());
        int nearRoad = 0;
        for (int i = 0; i < kept.count(); i++) {
            Layer layer = cleared.species().get(kept.species(i)).layer();
            assertThat(road.keep(kept.x(i), kept.z(i), layer)).isEqualTo(1);
            nearRoad += Math.abs(kept.z(i) - 64) < 12 ? 1 : 0;
        }
        assertThat(nearRoad).isGreaterThan(0);
        // a clearing of nothing: the same plants
        Instances same = ecology.withClearing(EcologyClearing.NONE).tile(0, 0).instances();
        assertThat(same.count()).isEqualTo(all.count());
        // the fields are the ecology's own
        assertThat(cleared.tile(0, 0).cover(GroundCover.MEADOW)).isEqualTo(ecology.tile(0, 0).cover(GroundCover.MEADOW));
        // with obstacles too, the clearing kept
        assertThat(cleared.avoiding(Obstacles.NONE).clearing()).isSameAs(road);
        assertThat(cleared.avoiding(Obstacles.NONE).tile(0, 0).instances().count()).isEqualTo(kept.count());
    }
}
