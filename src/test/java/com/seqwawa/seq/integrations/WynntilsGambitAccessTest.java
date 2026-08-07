package com.seqwawa.seq.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WynntilsGambitAccessTest {

    @Test
    void acceptsEverySupportedGambitCount() {
        for (int count = 0; count <= 4; count++) {
            assertEquals(count, WynntilsGambitAccess.validatedCount(count).orElseThrow());
        }
    }

    @Test
    void rejectsCountsOutsideTheRaidStartSlots() {
        assertTrue(WynntilsGambitAccess.validatedCount(-1).isEmpty());
        assertTrue(WynntilsGambitAccess.validatedCount(5).isEmpty());
    }
}
