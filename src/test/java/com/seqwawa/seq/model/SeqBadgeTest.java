package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SeqBadgeTest {
    @Test
    void parsesLegacyCombinedBadgeName() {
        assertEquals(
                new SeqBadge(SeqBadgeEvent.WTP, SeqBadgeTier.GOLD),
                SeqBadge.parseLegacy("WTP_GOLD"));
    }

    @Test
    void rejectsUnknownLegacyBadge() {
        assertNull(SeqBadge.parseLegacy("UNKNOWN_GOLD"));
    }

    @Test
    void buildsEventSpecificTexturePath() {
        assertEquals(
                "seq:badges/wtp_gold.png",
                new SeqBadge(SeqBadgeEvent.WTP, SeqBadgeTier.GOLD).textureId().toString());
        assertEquals(
                "seq:badges/nol_gold.png",
                new SeqBadge(SeqBadgeEvent.NOL, SeqBadgeTier.GOLD).textureId().toString());
    }
}
