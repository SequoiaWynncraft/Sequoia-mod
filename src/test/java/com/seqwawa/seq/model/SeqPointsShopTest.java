package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeqPointsShopTest {

    @Test
    void normalizesNullableCollectionsAndRecognizesRenameItems() {
        SeqPointsShop shop = new SeqPointsShop(1, null, null, null, null, null);
        SeqPointsShop.Item rename = new SeqPointsShop.Item(
                "temporary-rename",
                "Temporary Rename",
                "Alias",
                500,
                "ENTERTAINMENT",
                "TEMPORARY_RENAME",
                true,
                null,
                false,
                null);
        SeqPointsShop.Item draft = new SeqPointsShop.Item(
                "weekly-draft-entry",
                "Weekly Draft Entry",
                "Ticket",
                100,
                "DRAW",
                "DRAFT_ENTRY",
                false,
                "2026-W35",
                true,
                2L);
        SeqPointsShop.Item payout = new SeqPointsShop.Item(
                "payout", "Payout", "Withdraw", 100, "PAYOUT", "PAYOUT", true, null, false, null);

        assertTrue(shop.isSupported());
        assertEquals(List.of(), shop.items());
        assertEquals(0, shop.balance().total());
        assertTrue(rename.isRename());
        assertTrue(draft.isDraft());
        assertTrue(payout.isPayout());
        assertEquals(2, draft.ticketCountThisPeriod());
    }

    @Test
    void ticketCountIsAdditiveForLegacySchemaOnePayloads() {
        Gson gson = new Gson();
        SeqPointsShop.Item current = gson.fromJson(
                "{\"fulfillment_type\":\"DRAFT_ENTRY\",\"ticket_count_this_period\":2}",
                SeqPointsShop.Item.class);
        SeqPointsShop.Item legacy =
                gson.fromJson("{\"fulfillment_type\":\"DRAFT_ENTRY\"}", SeqPointsShop.Item.class);

        assertEquals(2, current.ticketCountThisPeriod());
        assertNull(legacy.ticketCountThisPeriod());
    }
}
