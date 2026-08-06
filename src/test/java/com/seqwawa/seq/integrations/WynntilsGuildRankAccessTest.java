package com.seqwawa.seq.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WynntilsGuildRankAccessTest {
    @Test
    void guildNamesEqualIgnoresCaseAndOuterWhitespace() {
        assertTrue(WynntilsGuildRankAccess.guildNamesEqual(" seQuoIa ", "Sequoia"));
    }

    @Test
    void guildNamesEqualRejectsBlankOrMissingNames() {
        assertFalse(WynntilsGuildRankAccess.guildNamesEqual("", "Sequoia"));
        assertFalse(WynntilsGuildRankAccess.guildNamesEqual("Sequoia", null));
        assertFalse(WynntilsGuildRankAccess.guildNamesEqual(null, "Sequoia"));
    }

    @Test
    void guildNamesEqualRejectsOtherGuilds() {
        assertFalse(WynntilsGuildRankAccess.guildNamesEqual("Other Guild", "Sequoia"));
    }

    @Test
    void recognizesEveryWynncraftGuildRankName() {
        assertEquals("Recruit", WynntilsGuildRankAccess.rankLabel("RECRUIT"));
        assertEquals("Recruiter", WynntilsGuildRankAccess.rankLabel("recruiter"));
        assertEquals("Captain", WynntilsGuildRankAccess.rankLabel("CAPTAIN"));
        assertEquals("Strategist", WynntilsGuildRankAccess.rankLabel("STRATEGIST"));
        assertEquals("Chief", WynntilsGuildRankAccess.rankLabel("CHIEF"));
        assertEquals("Owner", WynntilsGuildRankAccess.rankLabel("OWNER"));
        assertNull(WynntilsGuildRankAccess.rankLabel("UNKNOWN_RANK"));
    }
}
