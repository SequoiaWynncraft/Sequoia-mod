package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.managers.CheapestQueueTracker.TerritoryEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CheapestQueueTrackerTest {
    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<String> effects = new ArrayList<>();
    private final CheapestQueueTracker tracker = new CheapestQueueTracker(clock::get, effects::add);

    @Test
    void triggersOnceForAnyAttackedTerritoryWhenOwnHqIsCheapest() {
        cheapestHq();
        attack("Ragni");
        success("Ragni");
        success("Ragni");
        assertEquals(List.of("Ragni"), effects);
    }

    @Test
    void onlyTheLocalAttackerGetsTheEffect() {
        cheapestHq();
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fastest", "unknown"})
    void doesNotGuessStyleFromDuration(String style) {
        hq("Detlas");
        if (!style.equals("unknown")) style("Detlas", style);
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    @Test
    void attackedTerritoryBeingCheapestDoesNotCount() {
        hq("Detlas");
        style("Detlas", "fastest");
        style("Ragni", "cheapest");
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    @Test
    void styleBeforeHeadquartersDiscoveryIsRemembered() {
        style("Detlas", "cheapest");
        hq("Detlas");
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of("Ragni"), effects);
    }

    @Test
    void movingHqDoesNotCarryOldStyleOver() {
        cheapestHq();
        hq("Nesaak");
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of(), effects);
        style("Nesaak", "cheapest");
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of("Ragni"), effects);
    }

    @Test
    void hqChangeWhileQueueingInvalidatesAttempt() {
        cheapestHq();
        attack("Ragni");
        style("Nesaak", "cheapest");
        hq("Nesaak");
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    @Test
    void fastestUpdateInvalidatesAttemptEvenIfChangedBack() {
        cheapestHq();
        attack("Ragni");
        style("Detlas", "fastest");
        style("Detlas", "cheapest");
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    @Test
    void unrelatedStyleChangeDoesNotInvalidateAttempt() {
        cheapestHq();
        attack("Ragni");
        style("Nesaak", "fastest");
        success("Ragni");
        assertEquals(List.of("Ragni"), effects);
    }

    @Test
    void differentTerritoryConfirmationDoesNotConsumeLocalAttempt() {
        cheapestHq();
        attack("Ragni");
        success("Nesaak");
        assertEquals(List.of(), effects);
        success("Ragni");
        assertEquals(List.of("Ragni"), effects);
    }

    @Test
    void nextAttackReplacesPreviousAttempt() {
        cheapestHq();
        attack("Ragni");
        attack("Nesaak");
        success("Ragni");
        assertEquals(List.of(), effects);
        success("Nesaak");
        assertEquals(List.of("Nesaak"), effects);
    }

    @Test
    void failedUnconfirmedAttackExpiresAtDeadline() {
        cheapestHq();
        attack("Ragni");
        tracker.onSystemChat("You cannot attack this territory.");
        clock.addAndGet(CheapestQueueTracker.ATTEMPT_TIMEOUT_MS);
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    @Test
    void confirmationJustBeforeDeadlineWorks() {
        cheapestHq();
        attack("Ragni");
        clock.addAndGet(CheapestQueueTracker.ATTEMPT_TIMEOUT_MS - 1);
        success("Ragni");
        assertEquals(List.of("Ragni"), effects);
    }

    @Test
    void reconnectOrDisablingClearsAllEvidence() {
        cheapestHq();
        attack("Ragni");
        tracker.reset();
        success("Ragni");
        attack("Nesaak");
        success("Nesaak");
        assertEquals(List.of(), effects);
    }

    @Test
    void loadoutInvalidatesKnownTradeStyles() {
        cheapestHq();
        attack("Ragni");
        tracker.onSystemChat("Torment applied the loadout :void: on Detlas, Nesaak");
        success("Ragni");
        attack("Nesaak");
        success("Nesaak");
        assertEquals(List.of(), effects);
    }

    @Test
    void ordinaryChatCannotSetHeadquartersOrStyleOrConfirmSuccess() {
        tracker.onSystemChat("Envy: I set the guild headquarters to Detlas");
        tracker.onSystemChat("Torment: I changed the style of Detlas to cheapest");
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of(), effects);
        cheapestHq();
        attack("Ragni");
        tracker.onSystemChat("Envy: The war for Ragni will start in 3 minutes.");
        assertEquals(List.of(), effects);
    }

    @Test
    void normalizesWrappedServerNotices() {
        tracker.onSystemChat("󏿼󐀆 Envy set the guild headquarters to Detlas");
        tracker.onSystemChat("󏿼󐀆 Torment changed the style of Detlas to \n󏿼󐀆 cheapest");
        tracker.onAttackClick("§cAttacking: Ragni", "§4Attack");
        success("Ragni");
        assertEquals(List.of("Ragni"), effects);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sequoia: Territories", "Select Territories"})
    void recognizesHqFromVerifiedGuildTerritoryEntry(String title) {
        style("Detlas", "cheapest");
        tracker.observeTerritories(title, List.of(new TerritoryEntry("§b[!] §fDetlas (HQ)", "§7Left-Click to view territory")));
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of("Ragni"), effects);
    }

    @Test
    void ignoresLookalikeItemsOutsideTheTerritoryMenuOrWithWrongLore() {
        style("Detlas", "cheapest");
        tracker.observeTerritories("Chest", List.of(new TerritoryEntry("Detlas (HQ)", "Click to Select")));
        tracker.observeTerritories("Sequoia: Territories", List.of(new TerritoryEntry("Detlas (HQ)", "Renamed item")));
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    @Test
    void ambiguousHqEntriesInvalidatePreviousEvidence() {
        cheapestHq();
        tracker.observeTerritories("Sequoia: Territories", List.of(
                new TerritoryEntry("Detlas (HQ)", "Click to Select"),
                new TerritoryEntry("Nesaak (HQ)", "Click to Unselect")));
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    @Test
    void pageWithoutHqDoesNotErasePreviousDiscovery() {
        cheapestHq();
        tracker.observeTerritories("Sequoia: Territories", List.of(new TerritoryEntry("Nesaak", "Click to Select")));
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of("Ragni"), effects);
    }

    @Test
    void otherMenuClicksDoNotArmAnAttempt() {
        cheapestHq();
        tracker.onAttackClick("Chest", "Attack");
        tracker.onAttackClick("Attacking: Ragni", "Cancel");
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    @Test
    void emptyMenuCanDiscoverHqFromLaterSlotUpdate() {
        style("Detlas", "cheapest");
        tracker.onMenuOpened(5, "Sequoia: Territories", 54);
        tracker.onMenuContents(5, Collections.nCopies(90, null));
        tracker.onMenuSlot(5, 10, new TerritoryEntry("Detlas (HQ)", "Click to Select"));
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of("Ragni"), effects);
    }

    @Test
    void contentsAndSlotUpdatesExcludeInventoryAndWrongMenuIds() {
        style("Detlas", "cheapest");
        TerritoryEntry fakeHq = new TerritoryEntry("Detlas (HQ)", "Click to Select");
        tracker.onMenuOpened(5, "Sequoia: Territories", 54);
        var contents = new ArrayList<TerritoryEntry>(Collections.nCopies(90, null));
        contents.set(54, fakeHq);
        tracker.onMenuContents(5, contents);
        tracker.onMenuContents(6, Collections.nCopies(90, fakeHq));
        tracker.onMenuSlot(6, 10, fakeHq);
        tracker.onMenuSlot(5, 54, fakeHq);
        tracker.onMenuSlot(5, -1, fakeHq);
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    @Test
    void hqReplacementInSameMenuDoesNotRetainOldCheapestHq() {
        style("Detlas", "cheapest");
        tracker.onMenuOpened(5, "Sequoia: Territories", 54);
        tracker.onMenuSlot(5, 10, new TerritoryEntry("Detlas (HQ)", "Click to Select"));
        attack("Ragni");
        tracker.onMenuSlot(5, 10, new TerritoryEntry("Nesaak (HQ)", "Click to Select"));
        success("Ragni");
        attack("Nesaak");
        success("Nesaak");
        assertEquals(List.of(), effects);
    }

    @Test
    void closedAndReplacedMenuPacketsCannotRestoreHeadquarters() {
        style("Detlas", "cheapest");
        TerritoryEntry oldHq = new TerritoryEntry("Detlas (HQ)", "Click to Select");
        tracker.onMenuOpened(5, "Sequoia: Territories", 54);
        tracker.onMenuClosed(5);
        tracker.onMenuSlot(5, 10, oldHq);
        tracker.onMenuOpened(6, "Sequoia: Territories", 54);
        tracker.onMenuOpened(7, "Chest", 54);
        tracker.onMenuContents(6, Collections.nCopies(90, oldHq));
        tracker.onMenuSlot(7, 10, oldHq);
        attack("Ragni");
        success("Ragni");
        assertEquals(List.of(), effects);
    }

    private void cheapestHq() {
        hq("Detlas");
        style("Detlas", "cheapest");
    }

    private void hq(String territory) {
        tracker.onSystemChat("Envy set the guild headquarters to " + territory);
    }

    private void style(String territory, String mode) {
        tracker.onSystemChat("Torment changed the style of " + territory + " to " + mode);
    }

    private void attack(String territory) {
        tracker.onAttackClick("Attacking: " + territory, "Attack");
    }

    private void success(String territory) {
        tracker.onSystemChat("The war for " + territory + " will start in 3 minutes.");
    }
}
