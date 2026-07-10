package com.seqwawa.seq.model;

import com.google.gson.Gson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
                "seq:badges/insignia_gold.png",
                new SeqBadge(SeqBadgeType.INSIGNIA, SeqBadgeTier.GOLD).textureId().toString());
    }

    @Test
    void rendersInsigniaAfterOtherBadgeTypes() {
        assertEquals(
                List.of(
                        new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.GOLD),
                        new SeqBadge(SeqBadgeType.NOL, SeqBadgeTier.SILVER),
                        new SeqBadge(SeqBadgeType.INSIGNIA, SeqBadgeTier.DIAMOND)),
                SeqBadge.sortForRender(List.of(
                        new SeqBadge(SeqBadgeType.INSIGNIA, SeqBadgeTier.DIAMOND),
                        new SeqBadge(SeqBadgeType.NOL, SeqBadgeTier.SILVER),
                        new SeqBadge(SeqBadgeType.WTP, SeqBadgeTier.GOLD))));
    }

    @Test
    void rankProfileFieldsUseSnakeCaseWireKeys() {
        String json = """
                {
                  "schema_version": 1,
                  "catalog": {"roles": [], "awards": [], "assets": []},
                  "profiles": [{
                    "minecraft": {"uuid": "00000000-0000-0000-0000-000000000000", "username": "Test"},
                    "role_keys": ["role-key"],
                    "award_keys": ["award-key"]
                  }]
                }
                """;

        RankProfilesResponse response = GSON.fromJson(json, RankProfilesResponse.class);

        assertEquals(1, response.schemaVersion());
        assertEquals(List.of("role-key"), response.profiles().getFirst().roleKeys());
        assertEquals(List.of("award-key"), response.profiles().getFirst().awardKeys());
    }

}
