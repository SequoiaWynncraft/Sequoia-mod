package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KnownGuildMemberTest {

    private static final Instant NOW = Instant.parse("2026-09-15T20:00:00Z");

    private static String ago(Duration elapsed) {
        return KnownGuildMember.formatAgo(NOW.minus(elapsed), NOW);
    }

    @Test
    void picksTheOneUnitThatReadsNaturally() {
        assertEquals("just now", ago(Duration.ofSeconds(20)));
        assertEquals("1 min ago", ago(Duration.ofSeconds(61)));
        assertEquals("59 min ago", ago(Duration.ofMinutes(59)));
        assertEquals("1h ago", ago(Duration.ofMinutes(60)));
        assertEquals("47h ago", ago(Duration.ofHours(47)));
        assertEquals("2d ago", ago(Duration.ofHours(48)));
        assertEquals("59d ago", ago(Duration.ofDays(59)));
        assertEquals("2 months ago", ago(Duration.ofDays(60)));
    }

    @Test
    void aClockAheadOfUsReadsAsJustNow() {
        assertEquals("just now", KnownGuildMember.formatAgo(NOW.plusSeconds(90), NOW));
    }

    @Test
    void anUnknownLastJoinHasNoLabel() {
        assertNull(KnownGuildMember.formatAgo(null, NOW));
        assertNull(new KnownGuildMember("Ann", null, null).lastLoginLabel(NOW));
    }

    @Test
    void theLabelSaysLoggedInBecauseThatIsAllWynncraftTellsUs() {
        KnownGuildMember member = new KnownGuildMember("Ann", "uuid", NOW.minus(Duration.ofHours(2)));

        assertEquals("logged in 2h ago", member.lastLoginLabel(NOW));
        assertEquals("ann", member.key());
    }

    @Test
    void rejectsABlankName() {
        assertThrows(IllegalArgumentException.class, () -> new KnownGuildMember(" ", null, null));
    }
}
