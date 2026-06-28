package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WynntilsSeqBadgeNametagRendererTest {
    @Test
    void doesNotMatchLocalUsernameBySubstring() {
        assertFalse(WynntilsSeqBadgeNametagRenderer.matchesLocalUsername("Alex", "Alexandra"));
        assertFalse(WynntilsSeqBadgeNametagRenderer.matchesLocalUsername("Alex", "[VIP] Alexandra"));
    }

    @Test
    void matchesExactLocalUsernameIgnoringCaseAndFormatting() {
        assertTrue(WynntilsSeqBadgeNametagRenderer.matchesLocalUsername("Alex", "alex"));
        assertTrue(WynntilsSeqBadgeNametagRenderer.matchesLocalUsername("Alex", "§aAlex"));
    }

    @Test
    void matchesLocalUsernameAsNametagToken() {
        assertTrue(WynntilsSeqBadgeNametagRenderer.matchesLocalUsername("Alex", "[VIP] Alex"));
    }

    @Test
    void doesNotRenderStandaloneCanceledEvent() {
        assertFalse(WynntilsSeqBadgeNametagRenderer.shouldRenderStandaloneEvent(true));
        assertTrue(WynntilsSeqBadgeNametagRenderer.shouldRenderStandaloneEvent(false));
    }
}
