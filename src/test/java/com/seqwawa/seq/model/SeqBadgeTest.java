package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SeqBadgeTest {
    @Test
    void parsesLegacyCombinedBadgeName() {
        assertEquals(
                new SeqBadge(SeqBadgeEvent.TWP, SeqBadgeTier.GOLD),
                SeqBadge.parseLegacy("TWP_GOLD"));
    }

    @Test
    void rejectsUnknownLegacyBadge() {
        assertNull(SeqBadge.parseLegacy("UNKNOWN_GOLD"));
    }

    @Test
    void buildsEventSpecificTexturePath() {
        SeqBadge badge = new SeqBadge(SeqBadgeEvent.TWP, SeqBadgeTier.DIAMOND);

        assertEquals("seq:badges/fruma_diamond.png", badge.textureId().toString());
    }
}
