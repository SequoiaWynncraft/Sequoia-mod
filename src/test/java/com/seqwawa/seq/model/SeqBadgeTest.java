package com.seqwawa.seq.model;

import com.google.gson.Gson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SeqBadgeTest {
    private static final Gson GSON = new Gson();

    @Test
    void parsesLegacyCombinedBadgeName() {
        assertEquals(
                new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.GOLD),
                SeqBadge.parseLegacy("WTP_GOLD"));
    }

    @Test
    void rejectsUnknownLegacyBadge() {
        assertNull(SeqBadge.parseLegacy("UNKNOWN_GOLD"));
    }

    @Test
    void buildsTypeSpecificTexturePath() {
        assertEquals(
                "seq:badges/wtp_gold.png",
                new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.GOLD).textureId().toString());
        assertEquals(
                "seq:badges/nol_gold.png",
                new SeqBadge(SeqBadgeType.NOL, SeqBadgeTier.GOLD).textureId().toString());
        assertEquals(
                "seq:badges/insigna_gold.png",
                new SeqBadge(SeqBadgeType.INSIGNA, SeqBadgeTier.GOLD).textureId().toString());
    }

    @Test
    void rendersInsignaAfterOtherBadgeTypes() {
        assertEquals(
                List.of(
                        new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.GOLD),
                        new SeqBadge(SeqBadgeType.NOL, SeqBadgeTier.SILVER),
                        new SeqBadge(SeqBadgeType.INSIGNA, SeqBadgeTier.DIAMOND)),
                SeqBadge.sortForRender(List.of(
                        new SeqBadge(SeqBadgeType.INSIGNA, SeqBadgeTier.DIAMOND),
                        new SeqBadge(SeqBadgeType.NOL, SeqBadgeTier.SILVER),
                        new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.GOLD))));
    }

    @Test
    void badgeAssignmentsUseTypeWireKey() {
        LeaderboardBadgeAssignment assignment =
                new LeaderboardBadgeAssignment("00000000-0000-0000-0000-000000000000", new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.GOLD));

        String json = GSON.toJson(assignment);

        assertEquals(
                "WTP",
                GSON.fromJson(json, LeaderboardBadgeAssignment.class).type());
        assertFalse(json.contains("\"event\""));
        assertTrue(json.contains("\"type\""));
    }

}
