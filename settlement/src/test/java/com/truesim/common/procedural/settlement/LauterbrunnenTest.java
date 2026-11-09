package com.truesim.common.procedural.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.truesim.common.procedural.ecology.Ecology;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The showcase's village on Lauterbrunnen's valley floor (effects-terrain's cache; skipped where it does not hold the
 * site): its square where the showcase puts it, west of the Weisse Lütschine below the Staubbach falls.
 */
class LauterbrunnenTest {

    @Test
    void theShowcasesVillageKeepsThePlansRules() throws Exception {
        Ecology ecology = Valleys.lauterbrunnenEcology();
        assumeTrue(ecology != null, "effects-terrain's cache holds no Lauterbrunnen");
        Settlement village = Settlements.plan(SettlementParameters.DEFAULTS, ecology, -130, 300, 7);
        SettlementTest.assertGrades(village);
        SettlementTest.assertPlots(village);
        SettlementTest.assertReachable(village);
        SettlementTest.assertSited(village, ecology);
        assertThat(village.buildings().size()).isGreaterThan(25);
        // the main road runs along the valley (north and south of the square), not up its walls
        Settlement.Square square = village.square();
        assertThat(Math.abs(Math.sin(square.angle()))).isGreaterThan(0.8);
        List<RoadMeshes.Tile> tiles = RoadMeshes.build(village);
        assertThat(tiles).isNotEmpty();
    }
}
