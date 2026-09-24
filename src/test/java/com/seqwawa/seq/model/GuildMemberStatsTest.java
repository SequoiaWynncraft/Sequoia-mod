package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuildMemberStatsTest {

    private static GuildMemberStats full() {
        return new GuildMemberStats(
                12040.17d,
                Map.of("TNA", 9631),
                12056,
                5344,
                37_275_376_256_322L,
                3,
                Instant.parse("2023-03-14T19:21:11Z"),
                new RaidPerformance(369_072_114_311L, 39_906_690_965L, 2491L, 52_611L));
    }

    @Test
    void countsAreGroupedSoTheyCanBeReadAtAGlance() {
        assertEquals("12,056", GuildMemberStats.formatCount(12056));
        assertEquals("999,999", GuildMemberStats.formatCount(999_999));
        assertEquals("0", GuildMemberStats.formatCount(0));
    }

    @Test
    void numbersNobodyReadsDigitByDigitAreShortened() {
        assertEquals("999,999", GuildMemberStats.formatCompact(999_999L), "still readable in full");
        assertEquals("1.0M", GuildMemberStats.formatCompact(1_000_000L));
        assertEquals("369B", GuildMemberStats.formatCompact(369_072_114_311L));
        assertEquals("39.9B", GuildMemberStats.formatCompact(39_906_690_965L));
        assertEquals("37.3T", GuildMemberStats.formatCompact(37_275_376_256_322L));
    }

    @Test
    void labelsReadTheWayTheCardShowsThem() {
        GuildMemberStats stats = full();

        assertEquals("12040h", stats.playtimeLabel());
        assertEquals("12,056", stats.warsLabel());
        assertEquals("5,344", stats.totalLevelLabel());
        assertEquals("37.3T", stats.contributedXpLabel());
        assertEquals("#3", stats.contributionRankLabel());
        assertTrue(stats.isKnown());
        assertTrue(stats.raidPerformance().isKnown());
    }

    @Test
    void theJoinDateIsTheMonthTheyJoinedTheGuild() {
        // Read in the player's own zone, which is where the month has to make sense.
        Instant joined = ZonedDateTime.of(2023, 3, 14, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant();
        GuildMemberStats stats = new GuildMemberStats(
                0d, Map.of(), 0, 0, 0L, 0, joined, RaidPerformance.unknown());

        assertEquals("Mar 2023", stats.joinedGuildLabel());
    }

    @Test
    void aMemberWhoHidesTheirProfileShowsQuestionMarksRatherThanZeroes() {
        GuildMemberStats unknown = GuildMemberStats.unknown();

        assertFalse(unknown.isKnown());
        assertEquals("?", unknown.playtimeLabel());
        assertEquals("?", unknown.warsLabel());
        assertEquals("?", unknown.totalLevelLabel());
        assertEquals("?", unknown.contributedXpLabel());
        assertEquals("?", unknown.contributionRankLabel());
        assertEquals("?", unknown.joinedGuildLabel());
        assertFalse(unknown.raidPerformance().isKnown());
    }

    @Test
    void theShortConstructorStillWorksForCallersThatOnlyKnowRaids() {
        GuildMemberStats stats = new GuildMemberStats(10d, Map.of("TNA", 4));

        assertEquals(4, stats.completions("tna"));
        assertEquals(0, stats.wars());
        assertEquals(RaidPerformance.unknown(), stats.raidPerformance());
    }
}
