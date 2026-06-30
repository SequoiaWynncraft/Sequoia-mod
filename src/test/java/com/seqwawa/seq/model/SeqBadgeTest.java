package com.seqwawa.seq.model;

import com.google.gson.Gson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeqBadgeTest {
    private static final Gson GSON = new Gson();

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

    @Test
    void badgeAssignmentsUseTypeWireKey() {
        LeaderboardBadgeAssignment assignment =
                new LeaderboardBadgeAssignment("00000000-0000-0000-0000-000000000000", new SeqBadge(SeqBadgeEvent.WTP, SeqBadgeTier.GOLD));

        String json = GSON.toJson(assignment);

        assertEquals(
                "WTP",
                GSON.fromJson(json, LeaderboardBadgeAssignment.class).type());
        assertFalse(json.contains("\"event\""));
        assertTrue(json.contains("\"type\""));
    }

}
