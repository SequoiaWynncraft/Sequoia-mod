package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
    void parseRealPacketExamplesHandlesSplitGuildBankPhrases() {
        GuildBankTracker tracker = new GuildBankTracker();

        GuildBankTracker.GuildBankEvent withdrawal = tracker.parseEvent(
                "󏿼󏿿󏿾 Purprated withdrew 1x Gelibord Teleportation Scroll [3/3]\n"
                        + "󏿼󐀆 from the Guild Bank (Everyone)");
        GuildBankTracker.GuildBankEvent deposit = tracker.parseEvent(
                "󏿼󏿿󏿾 Purprated deposited 1x Gelibord Teleportation Scroll\n"
                        + "󏿼󐀆 [2/3] to the Guild Bank (Everyone)");

        assertNotNull(withdrawal);
        assertEquals("Gelibord Teleportation Scroll", withdrawal.itemName());
        assertEquals("3/3", withdrawal.charges());
        assertEquals("Everyone", withdrawal.accessTier());

        assertNotNull(deposit);
        assertEquals("Gelibord Teleportation Scroll", deposit.itemName());
        assertEquals("2/3", deposit.charges());
    }

    @Test
    void parseComponentMessageResolvesRealUsernameInsteadOfNickname() {
        GuildBankTracker tracker = new GuildBankTracker();

        Component message = Component.empty()
                .append(Component.literal("󏿼󏿿󏿾 "))
                .append(Component.literal("death by choking").withStyle(Style.EMPTY.withInsertion("a3pki")))
                .append(Component.literal(" withdrew 1x MR dagger [100%]\n󏿼󐀆 from the Guild Bank (Everyone)"));

        GuildBankTracker.GuildBankEvent event = tracker.parseEvent(message);

        assertNotNull(event);
        assertEquals("a3pki", event.player());
    }

    @Test
    void parseComponentMessageDropsNicknameWhenRealUsernameIsMissing() {
        GuildBankTracker tracker = new GuildBankTracker();

        Component message = Component.empty()
                .append(Component.literal("󏿼󏿿󏿾 death by choking withdrew 1x MR dagger [100%]\n󏿼󐀆 from the Guild Bank (Everyone)"));

        assertNull(tracker.parseEvent(message));
    }

    @Test
    void parseComponentMessageResolvesRealUsernameForDepositsWithSpacedNickname() {
        GuildBankTracker tracker = new GuildBankTracker();

        Component message = Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("divvy lunne").withStyle(Style.EMPTY.withInsertion("blousy")))
                .append(Component.literal(" deposited 1x WS [100%] to the Guild Bank (\n󏿼󐀆 Everyone)"));

        GuildBankTracker.GuildBankEvent event = tracker.parseEvent(message);

        assertNotNull(event);
        assertEquals("blousy", event.player());
        assertEquals("WS", event.itemName());
        assertEquals("100%", event.charges());
    }

    @Test
    void parseMessageHandlesSplitAccessTierAndQuantityItemNamesFromLogs() {
        GuildBankTracker tracker = new GuildBankTracker();

        GuildBankTracker.GuildBankEvent deposit = tracker.parseEvent(
                "󏿼󏿿󏿾 5up3rZ deposited 2x Light Paper [✫✫✫] to the Guild Bank (\n󏿼󐀆 High Ranked)");
        GuildBankTracker.GuildBankEvent liquidEmerald = tracker.parseEvent(
                "󏿼󏿿󏿾 enzololxd deposited 10x Liquid Emerald to the Guild Bank (\n󏿼󐀆 Everyone)");

        assertNotNull(deposit);
        assertEquals(2, deposit.quantity());
        assertEquals("Light Paper", deposit.itemName());
        assertEquals("✫✫✫", deposit.charges());
        assertEquals("High Ranked", deposit.accessTier());

        assertNotNull(liquidEmerald);
        assertEquals(10, liquidEmerald.quantity());
        assertEquals("Liquid Emerald", liquidEmerald.itemName());
        assertEquals("Everyone", liquidEmerald.accessTier());
    }

    @Test
    void parseMessageHandlesAbbreviatedGuildSuffixWithoutBankTier() {
        GuildBankTracker tracker = new GuildBankTracker();

        GuildBankTracker.GuildBankEvent withdrawal =
                tracker.parseEvent("cinfrascitizen withdrew 1x Mural Shard [✫✫✫] from the Guild");
        GuildBankTracker.GuildBankEvent deposit =
                tracker.parseEvent("cinfrascitizen deposited 1x Mural Shard [✫✫✫] to the Guild");

        assertNotNull(withdrawal);
        assertEquals(GuildBankTracker.GuildBankAction.WITHDRAWAL, withdrawal.action());
        assertEquals("cinfrascitizen", withdrawal.player());
        assertEquals(1, withdrawal.quantity());
        assertEquals("Mural Shard", withdrawal.itemName());
        assertEquals("✫✫✫", withdrawal.charges());
        assertEquals("Unknown", withdrawal.accessTier());

        assertNotNull(deposit);
        assertEquals(GuildBankTracker.GuildBankAction.DEPOSIT, deposit.action());
        assertEquals("Unknown", deposit.accessTier());
    }

    @Test
    void parseMessageHandlesGuildBankSuffixWithoutAccessTier() {
        GuildBankTracker tracker = new GuildBankTracker();

        GuildBankTracker.GuildBankEvent withdrawal =
                tracker.parseEvent("pat_crafter07 withdrew 3x Doom Stone from the Guild Bank");
        GuildBankTracker.GuildBankEvent deposit =
                tracker.parseEvent("melodozina deposited 3x Photophobic Kelp to the Guild Bank");

        assertNotNull(withdrawal);
        assertEquals(GuildBankTracker.GuildBankAction.WITHDRAWAL, withdrawal.action());
        assertEquals("pat_crafter07", withdrawal.player());
        assertEquals(3, withdrawal.quantity());
        assertEquals("Doom Stone", withdrawal.itemName());
        assertEquals("Unknown", withdrawal.accessTier());

        assertNotNull(deposit);
        assertEquals(GuildBankTracker.GuildBankAction.DEPOSIT, deposit.action());
        assertEquals("melodozina", deposit.player());
        assertEquals("Photophobic Kelp", deposit.itemName());
        assertEquals("Unknown", deposit.accessTier());
    }

    @Test
    void parseMessageHandlesFormattedGuildChatLogLines() {
        GuildBankTracker tracker = new GuildBankTracker();

        GuildBankTracker.GuildBankEvent withdrawal = tracker.parseEvent(
                "[10:45:41] [Render thread/INFO]: [CHAT/GUILD] &b&{fr:cp}󏿼󏿿󏿾&{fr:d} &3cinfrascitizen&b withdrew &e1x Mural Shard [✫✫✫]&b from the Guild&{fr:d}");
        GuildBankTracker.GuildBankEvent deposit = tracker.parseEvent(
                "[10:45:50] [Render thread/INFO]: [CHAT/GUILD] &b&{fr:cp}󏿼󏿿󏿾&{fr:d} &3cinfrascitizen&b deposited &e1x Mural Shard [✫✫✫]&b to the Guild&{fr:d}");

        assertNotNull(withdrawal);
        assertEquals("cinfrascitizen", withdrawal.player());
        assertEquals("Mural Shard", withdrawal.itemName());
        assertEquals("✫✫✫", withdrawal.charges());
        assertEquals("Unknown", withdrawal.accessTier());

        assertNotNull(deposit);
        assertEquals(GuildBankTracker.GuildBankAction.DEPOSIT, deposit.action());
        assertEquals("cinfrascitizen", deposit.player());
    }

    @Test
    void parseEventIgnoresGuildMessagesThatAreNotBankTransactions() {
        GuildBankTracker tracker = new GuildBankTracker();

        assertNull(tracker.parseEvent("󏿼󐀆 Purprated rewarded 1024 Emeralds to cinfrascitizen"));
        assertNull(tracker.parseEvent("󏿼󐀆 Territory Gelibord is producing more resources than it can store!"));
    }

    @Test
    void ignoresNonBankMessages() {
        GuildBankTracker tracker = new GuildBankTracker();

        assertNull(tracker.parseEvent("SequoiaUser: hello guild"));
        assertNull(tracker.parseEvent("Wynncraft: Fruma Expansion"));
    }
}
