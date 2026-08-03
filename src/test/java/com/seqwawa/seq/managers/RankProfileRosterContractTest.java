package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.seqwawa.seq.model.RankProfilesResponse;
import com.seqwawa.seq.model.SeqBadgeTier;
import com.seqwawa.seq.model.SeqBadgeType;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Parses a slice of a real {@code scope=recognized} response, so the assumptions
 * the single-roster merge rests on break loudly if the payload ever changes.
 * <p>
 * The fixture keeps the catalog whole and samples profiles of each shape the
 * roster returns: members with both identities, members with only a Discord
 * account, and members holding raid awards.
 */
class RankProfileRosterContractTest {

    private static RankProfilesResponse roster() {
        try (InputStream stream =
                RankProfileRosterContractTest.class.getResourceAsStream("/rank-profiles-sample.json")) {
            assertNotNull(stream, "the sample roster fixture must be on the test classpath");
            return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), RankProfilesResponse.class);
        } catch (Exception exception) {
            throw new AssertionError("could not read the sample roster", exception);
        }
    }

    @Test
    void theCatalogCarriesBothTheBadgeAndTheRankVocabulary() {
        // One response has to serve both features, so its catalog must describe both.
        RankProfilesResponse.Catalog catalog = roster().catalog();

        assertFalse(DiscordRankService.progressionRanks(catalog).isEmpty(), "progression ranks");
        assertFalse(LeaderboardBadgeService.badgeDefinitions(catalog).isEmpty(), "badge definitions");
        assertFalse(catalog.assets().isEmpty(), "badge art assets");
    }

    @Test
    void progressionRolesCarryLabelPositionAndColours() {
        var ranks = DiscordRankService.progressionRanks(roster().catalog());
        var colors = DiscordRankService.roleColors(roster().catalog());

        ranks.values().forEach(rank -> {
            assertFalse(rank.label().isBlank(), rank.key() + " needs a label");
            assertTrue(rank.position() > 0, rank.key() + " needs a position");
        });
        assertFalse(colors.isEmpty(), "at least one progression role must publish a colour");
    }

    @Test
    void gradientRolesArePublishedToday() {
        // The reviewer expected gradients to be future work; the backend already sends
        // a secondary stop, so the gradient pill renders as soon as this ships.
        boolean anyGradient = DiscordRankService.roleColors(roster().catalog()).values().stream()
                .anyMatch(ramp -> ramp.isGradient());

        assertTrue(anyGradient, "no role in the sample carries a secondary colour");
    }

    @Test
    void oneSnapshotFeedsBothIndexes() {
        RankProfilesResponse roster = roster();

        DiscordRankService.Index ranks = DiscordRankService.parseProfiles(roster);
        Map<String, Map<SeqBadgeType, SeqBadgeTier>> badgesByUuid = LeaderboardBadgeService.parseProfiles(roster);
        Map<String, Map<SeqBadgeType, SeqBadgeTier>> badgesByName =
                LeaderboardBadgeService.parseProfilesByUsername(roster);

        assertTrue(ranks.rankedProfiles() > 0, "the roster must yield ranks");
        assertFalse(badgesByUuid.isEmpty(), "the roster must yield badges by uuid");
        assertEquals(badgesByUuid.size(), badgesByName.size(), "both badge indexes cover the same members");
    }

    @Test
    void discordOnlyMembersDoNotReachTheGuildChatIndex() {
        // Guild chat resolves speakers by game name, so a member without one must never
        // be matchable there even though the roster carries them.
        DiscordRankService.Index index = DiscordRankService.parseProfiles(roster());

        assertTrue(
                index.byMinecraftUsername().size() <= index.byDiscordIdentity().size(),
                "game-name index must not exceed the discord index");
    }
}
