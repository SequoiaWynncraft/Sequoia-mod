package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seqwawa.seq.model.RankProfilesResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiscordRankServiceTest {
    private static final String PLAYER_UUID = "95f1b342-d7f5-466e-b1c8-32f0b42214db";

    private static final RankProfilesResponse.Catalog CATALOG = new RankProfilesResponse.Catalog(
            List.of(
                    role("rank.sapling", "Sapling", "progression_rank", 88),
                    role("rank.dryad", "Dryad", "progression_rank", 94),
                    role("rank.treant", "Treant", "progression_rank", 104),
                    role("in_game.recruiter", "Recruiter", "in_game_rank", 40)),
            List.of(),
            List.of());

    @Test
    void indexesEveryIdentityAMemberCanAppearUnder() {
        RankProfilesResponse response = new RankProfilesResponse(
                1,
                CATALOG,
                List.of(new RankProfilesResponse.Profile(
                        new RankProfilesResponse.DiscordIdentity(
                                "719729926802112553", "breadmusic", "Sapling dix", null),
                        new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "dix"),
                        List.of("rank.sapling", "in_game.recruiter"),
                        List.of(),
                        summary("rank.sapling"))));

        DiscordRankService.Index index = DiscordRankService.parseProfiles(response);

        assertEquals(1, index.rankedProfiles());
        assertEquals("Sapling", index.byMinecraftUuid().get(PLAYER_UUID).label());
        assertEquals("Sapling", index.byMinecraftUsername().get("dix").label());
        assertEquals("Sapling", index.byDiscordIdentity().get("breadmusic").label());
        assertEquals("Sapling", index.byDiscordIdentity().get("719729926802112553").label());
        assertEquals("Sapling", index.byDiscordIdentity().get("sapling dix").label());
        // The bridge forwards the nickname without its rank prefix.
        assertEquals("Sapling", index.byDiscordIdentity().get("dix").label());
    }

    @Test
    void fallsBackToHighestProgressionRoleWhenTheSummaryIsMissing() {
        RankProfilesResponse response = new RankProfilesResponse(
                1,
                CATALOG,
                List.of(new RankProfilesResponse.Profile(
                        null,
                        new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "Player"),
                        List.of("rank.sapling", "rank.treant", "rank.dryad"),
                        List.of(),
                        null)));

        DiscordRankService.Index index = DiscordRankService.parseProfiles(response);

        assertEquals("Treant", index.byMinecraftUsername().get("player").label());
    }

    @Test
    void skipsMembersWithoutAProgressionRank() {
        RankProfilesResponse response = new RankProfilesResponse(
                1,
                CATALOG,
                List.of(new RankProfilesResponse.Profile(
                        new RankProfilesResponse.DiscordIdentity("1", "guest", "Guest", null),
                        new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "Guest"),
                        List.of("in_game.recruiter"),
                        List.of(),
                        summary(null))));

        DiscordRankService.Index index = DiscordRankService.parseProfiles(response);

        assertEquals(0, index.rankedProfiles());
        assertNull(index.byMinecraftUsername().get("guest"));
        assertNull(index.byDiscordIdentity().get("guest"));
    }

    @Test
    void readsTheDiscordRoleColour() {
        RankProfilesResponse.Catalog catalog = new RankProfilesResponse.Catalog(
                List.of(
                        role("rank.sapling", "Sapling", "progression_rank", 88, "#4CB4FA"),
                        role("rank.dryad", "Dryad", "progression_rank", 94, "CDECE4"),
                        role("rank.upper_strategist", "Upper Strategist", "progression_rank", 96, null),
                        role("rank.treant", "Treant", "progression_rank", 104, "not-a-colour")),
                List.of(),
                List.of());

        var ranks = DiscordRankService.progressionRanks(catalog);

        assertEquals(0x4CB4FA, ranks.get("rank.sapling").color());
        assertEquals(0xCDECE4, ranks.get("rank.dryad").color(), "a missing # must still parse");
        assertNull(ranks.get("rank.upper_strategist").color());
        assertNull(ranks.get("rank.treant").color(), "malformed colours must not break the catalog");
    }

    @Test
    void onlyProgressionRolesEnterTheRankCatalog() {
        assertEquals(
                List.of("rank.dryad", "rank.sapling", "rank.treant"),
                DiscordRankService.progressionRanks(CATALOG).keySet().stream()
                        .sorted()
                        .toList());
    }

    private static RankProfilesResponse.RoleDefinition role(
            String key, String label, String category, int position) {
        return role(key, label, category, position, null);
    }

    private static RankProfilesResponse.RoleDefinition role(
            String key, String label, String category, int position, String primaryColor) {
        return new RankProfilesResponse.RoleDefinition(
                key,
                label,
                category,
                null,
                null,
                position,
                new RankProfilesResponse.RoleColors(primaryColor, null, null));
    }

    private static RankProfilesResponse.Summary summary(String progressionRank) {
        return new RankProfilesResponse.Summary(progressionRank, null, null, null, null);
    }
}
