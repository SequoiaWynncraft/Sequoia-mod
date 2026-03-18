package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GuildBankTrackerTest {

    @Test
    void parseDepositMessageExtractsStructuredFields() {
        GuildBankTracker tracker = new GuildBankTracker();

        GuildBankTracker.GuildBankEvent event = tracker.parseEvent(
                "B tier idol user deposited 1x Ahmsord Teleportation Scroll [3/3] to the Guild Bank (Everyone)");

        assertNotNull(event);
        assertEquals(GuildBankTracker.GuildBankAction.DEPOSIT, event.action());
        assertEquals("B tier idol user", event.player());
        assertEquals(1, event.quantity());
        assertEquals("Ahmsord Teleportation Scroll", event.itemName());
        assertEquals("3/3", event.charges());
        assertEquals("Everyone", event.accessTier());
    }

    @Test
    void parseWithdrawalMessageExtractsStructuredFields() {
        GuildBankTracker tracker = new GuildBankTracker();

        GuildBankTracker.GuildBankEvent event = tracker.parseEvent(
                "B tier idol user withdrew 1x Ahmsord Teleportation Scroll [3/3] from the Guild Bank (High Ranked)");

        assertNotNull(event);
        assertEquals(GuildBankTracker.GuildBankAction.WITHDRAWAL, event.action());
        assertEquals("High Ranked", event.accessTier());
    }

    @Test
    void parseMultilineMessageNormalizesIntoSingleEvent() {
        GuildBankTracker tracker = new GuildBankTracker();

        GuildBankTracker.GuildBankEvent event = tracker.parseEvent(
                "󏿼󏿿󏿾 B tier idol user deposited 1x Ahmsord Teleportation\n󏿼󐀆 Scroll [3/3] to the Guild Bank (Everyone)");

        assertNotNull(event);
        assertEquals("Ahmsord Teleportation Scroll", event.itemName());
        assertEquals(
                "B tier idol user deposited 1x Ahmsord Teleportation Scroll [3/3] to the Guild Bank (Everyone)",
                event.rawMessage());
    }

    @Test
    void ignoresNonBankMessages() {
        GuildBankTracker tracker = new GuildBankTracker();

        assertNull(tracker.parseEvent("SequoiaUser: hello guild"));
        assertNull(tracker.parseEvent("Wynncraft: Fruma Expansion"));
    }
}
