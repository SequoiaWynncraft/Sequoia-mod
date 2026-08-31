package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.SeqPointsShop;
import org.junit.jupiter.api.Test;

class SeqPointsShopScreenTest {

    @Test
    void enteredDraftCanBeBoughtAgain() {
        SeqPointsShop.Item draft = item("DRAFT_ENTRY", true, 2L);

        assertFalse(SeqPointsShopScreen.purchaseBlocked(draft));
        assertEquals("Buy again", SeqPointsShopScreen.purchaseLabel(draft, false));
        assertEquals("Buying…", SeqPointsShopScreen.purchaseLabel(draft, true));
        assertEquals("100 SP · bonus only · 2 tickets", SeqPointsShopScreen.purchaseDetails(draft));
    }

    @Test
    void ticketDetailsHandleSingularAndLegacyBackend() {
        assertEquals(
                "100 SP · bonus only · 1 ticket",
                SeqPointsShopScreen.purchaseDetails(item("DRAFT_ENTRY", true, 1L)));
        assertEquals(
                "100 SP · bonus only", SeqPointsShopScreen.purchaseDetails(item("DRAFT_ENTRY", true, null)));
    }

    @Test
    void purchasedNonDraftStaysBlocked() {
        SeqPointsShop.Item item = item("LIMITED_ITEM", true, null);

        assertTrue(SeqPointsShopScreen.purchaseBlocked(item));
        assertEquals("Entered", SeqPointsShopScreen.purchaseLabel(item, false));
    }

    @Test
    void payoutUsesTheCatalogPriceAsItsRateAndRemainsRepeatable() {
        SeqPointsShop.Item payout = item("PAYOUT", true, null);

        assertFalse(SeqPointsShopScreen.purchaseBlocked(payout));
        assertEquals("Payout", SeqPointsShopScreen.purchaseLabel(payout, false));
        assertEquals("100 SP = 1 LE · all points", SeqPointsShopScreen.purchaseDetails(payout));
    }

    @Test
    void fourthCatalogRowCanBeScrolledIntoACompactViewport() {
        assertEquals(134f, SeqPointsShopScreen.catalogMaxScroll(4, 148), 0.001f);
        assertEquals(0f, SeqPointsShopScreen.catalogMaxScroll(2, 148), 0.001f);
    }

    private static SeqPointsShop.Item item(String fulfillmentType, boolean purchased, Long tickets) {
        return new SeqPointsShop.Item(
                "item", "Item", "Description", 100, "DRAW", fulfillmentType, false, "period", purchased, tickets);
    }
}
