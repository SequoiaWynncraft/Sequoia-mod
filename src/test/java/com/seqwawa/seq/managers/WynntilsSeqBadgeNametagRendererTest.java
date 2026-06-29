package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void rendersVanillaFallbackWhenWynntilsFeatureIsDisabled() {
        assertTrue(WynntilsSeqBadgeNametagRenderer.shouldRenderVanillaFallback(false, false, false, false));
        assertTrue(WynntilsSeqBadgeNametagRenderer.shouldRenderVanillaFallback(true, false, false, true));
    }

    @Test
    void rendersVanillaFallbackForLocalPlayerWhenWynntilsOwnNametagIsDisabled() {
        assertTrue(WynntilsSeqBadgeNametagRenderer.shouldRenderVanillaFallback(false, true, false, true));
        assertFalse(WynntilsSeqBadgeNametagRenderer.shouldRenderVanillaFallback(true, true, false, true));
        assertFalse(WynntilsSeqBadgeNametagRenderer.shouldRenderVanillaFallback(false, true, false, false));
    }

    @Test
    void forcesOwnAvatarRenderingWhenWynntilsWouldNotRenderOwnNametag() {
        assertTrue(WynntilsSeqBadgeNametagRenderer.shouldForceOwnAvatarRendering(false, true));
        assertTrue(WynntilsSeqBadgeNametagRenderer.shouldForceOwnAvatarRendering(true, false));
        assertFalse(WynntilsSeqBadgeNametagRenderer.shouldForceOwnAvatarRendering(true, true));
    }

    @Test
    void skipsVanillaFallbackForWynntilsEventRenders() {
        assertFalse(WynntilsSeqBadgeNametagRenderer.shouldRenderVanillaFallback(false, false, true, true));
    }

    @Test
    void usesLowerBadgeOffsetWhenSeqRendersAlone() {
        assertEquals(
                SeqBadgeNametagRenderSupport.WYNNTILS_DEFAULT_BADGE_Y_OFFSET,
                WynntilsSeqBadgeNametagRenderer.badgeYOffset(0f, false));
        assertEquals(
                SeqBadgeNametagRenderSupport.LOWER_BADGE_Y_OFFSET,
                WynntilsSeqBadgeNametagRenderer.badgeYOffset(0f, true));
        assertEquals(
                SeqBadgeNametagRenderSupport.LOWER_BADGE_Y_OFFSET,
                WynntilsSeqBadgeNametagRenderer.badgeYOffset(0.25f, false));
    }
}
