package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WarTerritoryBoundsReminderTest {
    @Test
    void countdownAppearsOnlyWhenEnabledAndInsideConfiguredWindow() {
        assertTrue(WarTerritoryBoundsReminder.shouldShowCountdown(true, 14, 15));
        assertFalse(WarTerritoryBoundsReminder.shouldShowCountdown(true, 15, 15));
        assertFalse(WarTerritoryBoundsReminder.shouldShowCountdown(true, 0, 15));
        assertFalse(WarTerritoryBoundsReminder.shouldShowCountdown(false, 10, 15));
    }

    @Test
    void chatMessagesOnlyTriggerWhenCrossingATerritoryBoundary() {
        assertTrue(WarTerritoryBoundsReminder.shouldShowEntryMessage(true, null, "Almuj"));
        assertFalse(WarTerritoryBoundsReminder.shouldShowEntryMessage(true, "Almuj", "ALMUJ"));
        assertFalse(WarTerritoryBoundsReminder.shouldShowEntryMessage(false, null, "Almuj"));

        assertTrue(WarTerritoryBoundsReminder.shouldShowLeaveMessage(true, "Almuj", null));
        assertTrue(WarTerritoryBoundsReminder.shouldShowLeaveMessage(true, "Almuj", "Detlas"));
        assertFalse(WarTerritoryBoundsReminder.shouldShowLeaveMessage(true, "Almuj", "almuj"));
        assertFalse(WarTerritoryBoundsReminder.shouldShowLeaveMessage(false, "Almuj", null));
    }

    @Test
    void countdownTitleFormatsSeconds() {
        assertEquals("War starts in 9s", WarTerritoryBoundsReminder.countdownTitle(9));
    }
}
