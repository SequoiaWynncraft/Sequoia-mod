package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                false);

        assertTrue(shop.isSupported());
        assertEquals(List.of(), shop.items());
        assertEquals(0, shop.balance().total());
        assertTrue(rename.isRename());
    }
}
