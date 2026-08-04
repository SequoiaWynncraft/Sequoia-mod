package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.DiscordRank;
import com.seqwawa.seq.model.RankProfilesResponse;
import com.seqwawa.seq.utils.ColorRamp;
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
                        summary("rank.sapling"),
                        new RankProfilesResponse.RoleColors("#112233", "#445566", "#778899"))));

        DiscordRankService.Index index = DiscordRankService.parseProfiles(response);

        assertEquals(1, index.rankedProfiles());
        assertEquals("Sapling", index.byMinecraftUuid().get(PLAYER_UUID).label());
        assertEquals("Sapling", index.byMinecraftUsername().get("dix").label());
        assertEquals("Sapling", index.byDiscordIdentity().get("breadmusic").label());
        assertEquals("Sapling", index.byDiscordIdentity().get("719729926802112553").label());
        assertEquals("Sapling", index.byDiscordIdentity().get("sapling dix").label());
        // The bridge forwards the nickname without its rank prefix.
        assertEquals("Sapling", index.byDiscordIdentity().get("dix").label());
        for (String identity : List.of(
                PLAYER_UUID, "dix", "719729926802112553", "breadmusic", "sapling dix")) {
            assertEquals(
                    List.of(0x112233, 0x445566, 0x778899),
                    index.colorsByIdentity().get(identity).stops(),
                    identity);
        }
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

        var colors = DiscordRankService.roleColors(catalog);

        assertEquals(0x4CB4FA, colors.get("rank.sapling").first());
        assertEquals(0xCDECE4, colors.get("rank.dryad").first(), "a missing # must still parse");
        assertNull(colors.get("rank.upper_strategist"), "an uncoloured role gets no ramp");
        assertNull(colors.get("rank.treant"), "malformed colours must not break the catalog");
    }

    @Test
    void readsEveryGradientStopInDiscordsOrder() {
        RankProfilesResponse.RoleColors colors =
                new RankProfilesResponse.RoleColors("#FF0000", "#00FF00", "#0000FF");

        assertEquals(List.of(0xFF0000, 0x00FF00, 0x0000FF), DiscordRankService.colorRamp(colors));
    }

    @Test
    void treatsASolidRoleAsASingleStopAndAnUncolouredOneAsNone() {
        assertEquals(
                List.of(0x4CB4FA),
                DiscordRankService.colorRamp(new RankProfilesResponse.RoleColors("#4CB4FA", null, null)));
        assertEquals(
                List.of(), DiscordRankService.colorRamp(new RankProfilesResponse.RoleColors(null, null, null)));
        assertEquals(List.of(), DiscordRankService.colorRamp(null));
    }

    @Test
    void dropsMalformedStopsWithoutLosingTheValidOnes() {
        RankProfilesResponse.RoleColors colors =
                new RankProfilesResponse.RoleColors("#FF0000", "not-a-colour", "0000FF");

        assertEquals(List.of(0xFF0000, 0x0000FF), DiscordRankService.colorRamp(colors));
    }

    @Test
    void exposesGradientRolesThroughTheCatalog() {
        RankProfilesResponse.Catalog catalog = new RankProfilesResponse.Catalog(
                List.of(new RankProfilesResponse.RoleDefinition(
                        "rank.yggdrasil",
                        "Yggdrasil",
                        "progression_rank",
                        null,
                        null,
                        120,
                        new RankProfilesResponse.RoleColors("#7506D6", "#CDECE4", null))),
                List.of(),
                List.of());

        DiscordRank yggdrasil = DiscordRankService.progressionRanks(catalog).get("rank.yggdrasil");
        ColorRamp colors = DiscordRankService.roleColors(catalog).get("rank.yggdrasil");

        assertEquals("Yggdrasil", yggdrasil.label(), "the rank itself carries no colour");
        assertTrue(colors.isGradient());
        assertEquals(List.of(0x7506D6, 0xCDECE4), colors.stops());
        assertEquals(0x7506D6, colors.first(), "the primary stop stays the single-colour answer");
    }

    @Test
    void anIndividualColourOverridesTheRoleColour() {
        // Two Saplings, one of whom the backend gives their own palette: they must
        // render differently, which is why colour is resolved apart from the rank.
        RankProfilesResponse response = new RankProfilesResponse(
                1,
                colouredCatalog(),
                List.of(
                        new RankProfilesResponse.Profile(
                                null,
                                new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "Plain"),
                                List.of("rank.sapling"),
                                List.of(),
                                summary("rank.sapling"),
                                null),
                        new RankProfilesResponse.Profile(
                                null,
                                new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "Special"),
                                List.of("rank.sapling"),
                                List.of(),
                                summary("rank.sapling"),
                                new RankProfilesResponse.RoleColors("#FF00FF", "#00FFFF", "#FFFF00"))));

        DiscordRankService.Index index = DiscordRankService.parseProfiles(response);
        DiscordRankService service = DiscordRankService.withIndex(index);

        assertEquals(List.of(0xFF00FF, 0x00FFFF, 0xFFFF00), index.colorsByIdentity().get("special").stops());
        assertNull(index.colorsByIdentity().get("plain"), "an ordinary member falls back to their role");
        assertEquals(List.of(0x4CB4FA), index.colorsByRoleKey().get("rank.sapling").stops());
        assertEquals(List.of(0x4CB4FA), service.presentationForMinecraftUsername("Plain").colors().stops());
        assertEquals(
                List.of(0xFF00FF, 0x00FFFF, 0xFFFF00),
                service.presentationForMinecraftUsername("Special").colors().stops());
    }

    @Test
    void emptyOrMalformedProfileDisplayColorsDoNotEraseTheCatalogFallback() {
        RankProfilesResponse response = new RankProfilesResponse(
                1,
                colouredCatalog(),
                List.of(
                        new RankProfilesResponse.Profile(
                                null,
                                new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "Empty"),
                                List.of("rank.sapling"),
                                List.of(),
                                summary("rank.sapling"),
                                new RankProfilesResponse.RoleColors(null, null, null)),
                        new RankProfilesResponse.Profile(
                                null,
                                new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "Malformed"),
                                List.of("rank.sapling"),
                                List.of(),
                                summary("rank.sapling"),
                                new RankProfilesResponse.RoleColors("#FF00FF", "not-a-colour", null))));

        DiscordRankService service = serviceWith(response);

        for (String identity : List.of("Empty", "Malformed")) {
            assertNull(DiscordRankService.parseProfiles(response)
                    .colorsByIdentity()
                    .get(identity.toLowerCase()));
            assertEquals(
                    List.of(0x4CB4FA),
                    service.presentationForMinecraftUsername(identity).colors().stops());
        }
    }

    @Test
    void resolvesTheRoleColourWhenTheMemberHasNoneOfTheirOwn() {
        RankProfilesResponse response = new RankProfilesResponse(
                1,
                colouredCatalog(),
                List.of(new RankProfilesResponse.Profile(
                        null,
                        new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "Plain"),
                        List.of("rank.sapling"),
                        List.of(),
                        summary("rank.sapling"),
                        null)));

        DiscordRankService.Index index = DiscordRankService.parseProfiles(response);
        DiscordRank sapling = index.byMinecraftUsername().get("plain");

        assertEquals(List.of(0x4CB4FA), index.colorsByRoleKey().get(sapling.key()).stops());
    }

    private static RankProfilesResponse.Catalog colouredCatalog() {
        return new RankProfilesResponse.Catalog(
                List.of(role("rank.sapling", "Sapling", "progression_rank", 88, "#4CB4FA")), List.of(), List.of());
    }

    @Test
    void linksEveryDiscordIdentityBackToTheGameAccount() {
        // Badges are held against the Minecraft account, so a bridge sender has to be
        // mapped back to their game name before an insignia can be looked up.
        RankProfilesResponse response = new RankProfilesResponse(
                1,
                CATALOG,
                List.of(new RankProfilesResponse.Profile(
                        new RankProfilesResponse.DiscordIdentity(
                                "719729926802112553", "breadmusic", "Sapling dix", null),
                        new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "dix"),
                        List.of("rank.sapling"),
                        List.of(),
                        summary("rank.sapling"))));

        var links = DiscordRankService.parseProfiles(response).minecraftUsernameByDiscordIdentity();

        assertEquals("dix", links.get("719729926802112553"));
        assertEquals("dix", links.get("breadmusic"));
        assertEquals("dix", links.get("sapling dix"));
    }

    @Test
    void prefersTheDiscordIdOverADisplayNameThatBelongsToSomeoneElse() {
        // Two members, where one has taken the other's game name as their Discord
        // display name: matching on the name alone would hand over the wrong rank.
        RankProfilesResponse response = new RankProfilesResponse(
                1,
                CATALOG,
                List.of(
                        new RankProfilesResponse.Profile(
                                new RankProfilesResponse.DiscordIdentity("111", "real", "Impostor", null),
                                new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "Impostor"),
                                List.of("rank.treant"),
                                List.of(),
                                summary("rank.treant")),
                        new RankProfilesResponse.Profile(
                                new RankProfilesResponse.DiscordIdentity("222", "other", "Someone", null),
                                new RankProfilesResponse.MinecraftIdentity(PLAYER_UUID, "Someone"),
                                List.of("rank.sapling"),
                                List.of(),
                                summary("rank.sapling"))));

        DiscordRankService service = serviceWith(response);

        assertEquals("Sapling", service.rankForBridgeSender("Impostor", "222").label(), "the id must win");
        assertEquals("Treant", service.rankForBridgeSender("Impostor", null).label(), "no id falls back to the name");
        assertEquals("Treant", service.rankForBridgeSender("Impostor", "unknown-id").label());
    }

    @Test
    void toleratesSendersItCannotIdentify() {
        // The bridge sends no id on older backends, and the index maps are immutable,
        // which throw on a null key instead of returning null.
        DiscordRankService service = serviceWith(new RankProfilesResponse(1, CATALOG, List.of()));

        assertNull(service.rankForBridgeSender(null, null));
        assertNull(service.rankForMinecraftUsername(null));
        assertNull(service.presentationForBridgeSender("nobody", null));
    }

    @Test
    void indexesMembersWhoNeverLinkedAGameAccount() {
        // The roster is scope=recognized, which also carries Discord-only members. They
        // can never match a guild chat line, which is resolved by game name, but they
        // do speak over the bridge and their rank is just as real.
        RankProfilesResponse response = new RankProfilesResponse(
                1,
                CATALOG,
                List.of(new RankProfilesResponse.Profile(
                        new RankProfilesResponse.DiscordIdentity("900", "discordonly", "Sapling ghost", null),
                        null,
                        List.of("rank.sapling"),
                        List.of(),
                        summary("rank.sapling"))));

        DiscordRankService.Index index = DiscordRankService.parseProfiles(response);

        assertEquals("Sapling", index.byDiscordIdentity().get("discordonly").label());
        assertEquals(1, index.rankedProfiles());
        assertNull(index.byMinecraftUsername().get("ghost"), "and stays out of the guild chat index");
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

    private static DiscordRankService serviceWith(RankProfilesResponse response) {
        return DiscordRankService.withIndex(DiscordRankService.parseProfiles(response));
    }

    private static RankProfilesResponse.Summary summary(String progressionRank) {
        return new RankProfilesResponse.Summary(progressionRank, null, null, null, null);
    }
}
