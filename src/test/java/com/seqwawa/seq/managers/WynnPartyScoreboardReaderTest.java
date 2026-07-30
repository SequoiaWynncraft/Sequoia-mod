package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class WynnPartyScoreboardReaderTest {
    @Test
    void rejectsOnlineRowsOutsidePartySection() {
        assertNull(WynnPartyScoreboardReader.parsePartyLine("120 RaidMember [106]", false));
    }

    @Test
    void parsesOnlineRowsInsidePartySection() {
        WynnPartyScoreboardReader.ParsedPartyLine parsed =
                WynnPartyScoreboardReader.parsePartyLine("120 PartyMember [106]", true);

        assertEquals("PartyMember", parsed.nickname());
        assertEquals(120, parsed.hp());
        assertEquals(106, parsed.level());
    }

    @Test
    void rejectsOfflineRowsOutsidePartySection() {
        assertNull(WynnPartyScoreboardReader.parsePartyLine("- PartyMember", false));
    }

    @Test
    void parsesOfflineRowsInsidePartySection() {
        WynnPartyScoreboardReader.ParsedPartyLine parsed =
                WynnPartyScoreboardReader.parsePartyLine("- PartyMember", true);

        assertEquals("PartyMember", parsed.nickname());
        assertFalse(parsed.online());
    }

    @Test
    void canonicalRosterDoesNotDependOnScoreboardRowOrder() {
        List<WynnPartyScoreboardReader.PartyHealth> scoreboardRows = List.of(
                partyHealth("SecondNick", "SecondPlayer"),
                partyHealth("FirstNick", "FirstPlayer"));

        assertEquals(
                List.of("FirstPlayer", "SecondPlayer"),
                WynnPartyScoreboardReader.matchingWynntilsPartyRoster(
                        scoreboardRows, List.of("FirstPlayer", "SecondPlayer")));
    }

    private WynnPartyScoreboardReader.PartyHealth partyHealth(String nickname, String username) {
        return new WynnPartyScoreboardReader.PartyHealth(
                nickname, username, 100, 106, true, true, true);
    }
}
