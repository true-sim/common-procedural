package com.truesim.common.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeedsTest {

    @Test
    void derivedSeedsAreStableAndDistinct() {
        assertEquals(Seeds.derive(42, 7), Seeds.derive(42, 7));
        Set<Long> seen = new HashSet<>();
        for (int salt = 0; salt < 10_000; salt++) {
            seen.add(Seeds.derive(42, salt));
        }
        assertEquals(10_000, seen.size());
        assertNotEquals(Seeds.derive(42, 0), Seeds.derive(43, 0));
    }

    @Test
    void mixIsSplitMix64sFinalizer() {
        // SplitMix64 seeded with 0: its first output is mix(0x9E3779B97F4A7C15)
        assertEquals(0xE220A8397B1DCDAFL, Seeds.mix(0x9E3779B97F4A7C15L));
        assertEquals(0, Seeds.mix(0));
    }
}
