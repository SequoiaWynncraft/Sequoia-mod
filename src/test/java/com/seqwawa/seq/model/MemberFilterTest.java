package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemberFilterTest {

    private static final RaidCatalog CATALOG = RaidCatalogTest.sequoiaCatalog();

    private static RaidType raid(String key) {
        return CATALOG.raid(key);
    }

    private static GuildMemberPresence member(String username, Map<String, Integer> clears) {
        return new GuildMemberPresence(
                username,
                "uuid-" + username,
                GuildMemberPresence.GuildRank.RECRUIT,
                "NA1",
                false,
                new GuildMemberStats(120d, clears));
    }

    private static RaidTeamProfile profileWith(String... buildKeys) {
        return RaidTeamProfile.empty().withBuildKeys(Set.of(buildKeys)).savedAt(1L);
    }

    @Test
    void anInactiveFilterMatchesEveryone() {
        MemberFilter filter = MemberFilter.none();

        assertFalse(filter.isActive());
        assertTrue(filter.matches(member("alpha", Map.of()), RaidTeamProfile.empty(), false, CATALOG));
        assertTrue(filter.matches(member("alpha", Map.of()), RaidTeamProfile.empty(), true, CATALOG));
    }

    @Test
    void aRaidFilterMatchesADeclaredBuildForThatRaid() {
        MemberFilter filter = MemberFilter.none().withRaid(raid("TNA"));

        assertTrue(filter.matches(member("alpha", Map.of()), profileWith("RESONANCE"), false, CATALOG));
        assertFalse(
                filter.matches(member("alpha", Map.of()), profileWith("HADAL"), false, CATALOG),
                "hadal is NOTG-only, so it does not answer a TNA search");
    }

    @Test
    void aDeclaredProfileWithoutTheBuildIsExcludedEvenWithHugeClearCounts() {
        MemberFilter filter = MemberFilter.none().withRaid(raid("TNA"));
        GuildMemberPresence veteran = member("alpha", Map.of("TNA", 3566));

        assertFalse(
                filter.matches(veteran, profileWith("HADAL"), false, CATALOG),
                "once someone declares what they own, that is the answer, not their history");
    }

    @Test
    void withoutAProfileTheRaidFilterFallsBackOnMeasuredClears() {
        MemberFilter filter = MemberFilter.none().withRaid(raid("TNA"));

        assertTrue(
                filter.matches(member("alpha", Map.of("TNA", 3566)), RaidTeamProfile.empty(), false, CATALOG));
        assertFalse(
                filter.matches(member("bravo", Map.of("NOTG", 40)), RaidTeamProfile.empty(), false, CATALOG));
        assertFalse(filter.matches(member("charlie", Map.of()), null, false, CATALOG));
    }

    @Test
    void theAuraFilterNeverFallsBackBecauseWynncraftDoesNotKnowIt() {
        GuildMemberPresence veteran = member("alpha", Map.of("TNA", 3566));

        assertFalse(
                MemberFilter.none().withAurasOnly(true).matches(veteran, RaidTeamProfile.empty(), false, CATALOG));
        assertTrue(MemberFilter.none()
                .withAurasOnly(true)
                .matches(veteran, profileWith("HERO").withCanBringAuras(true), false, CATALOG));
    }

    @Test
    void availableOnlyDropsBusyMembers() {
        MemberFilter filter = MemberFilter.none().withAvailableOnly(true);

        assertTrue(filter.matches(member("alpha", Map.of()), RaidTeamProfile.empty(), false, CATALOG));
        assertFalse(filter.matches(member("alpha", Map.of()), RaidTeamProfile.empty(), true, CATALOG));
    }

    @Test
    void searchIsACaseInsensitiveSubstringOfTheName() {
        MemberFilter filter = MemberFilter.none().withSearch("  BLOU  ");

        assertTrue(filter.matches(member("blousy", Map.of()), RaidTeamProfile.empty(), false, CATALOG));
        assertFalse(filter.matches(member("visroul", Map.of()), RaidTeamProfile.empty(), false, CATALOG));
        assertFalse(MemberFilter.none().withSearch("   ").isActive(), "a blank search is not a filter");
    }

    @Test
    void clausesCombine() {
        MemberFilter filter =
                MemberFilter.none().withRaid(raid("NOTG")).withAurasOnly(true).withAvailableOnly(true);
        RaidTeamProfile ready = profileWith("HADAL").withCanBringAuras(true);

        assertTrue(filter.matches(member("alpha", Map.of()), ready, false, CATALOG));
        assertFalse(filter.matches(member("alpha", Map.of()), ready, true, CATALOG), "busy fails the available clause");
        assertFalse(
                filter.matches(member("alpha", Map.of()), profileWith("HADAL"), false, CATALOG),
                "no auras fails the aura clause");
    }

    @Test
    void aRaidTheCatalogNoLongerKnowsStopsHidingTheGuild() {
        MemberFilter filter = MemberFilter.none().withRaid(raid("TNA"));

        assertTrue(
                filter.matches(member("alpha", Map.of()), RaidTeamProfile.empty(), false, RaidCatalog.empty()),
                "an unresolvable clause is skipped rather than excluding everyone");
    }

    @Test
    void theFilterHoldsKeysSoItSurvivesACatalogRefresh() {
        MemberFilter filter = MemberFilter.none().withRaid(raid("TNA"));

        assertEquals("TNA", filter.raidKey());
        assertTrue(filter.hasRaid());
        assertEquals("TNA", filter.raid(CATALOG).key());
        assertEquals(null, filter.raid(RaidCatalog.empty()));
    }
}
