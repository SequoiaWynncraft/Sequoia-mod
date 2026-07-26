package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.managers.IngredientGuideManager.SortDirection;
import com.seqwawa.seq.managers.IngredientGuideManager.SortKey;
import org.junit.jupiter.api.Test;

class IngredientGuideSessionSettingsTest {
    @Test
    void retainsSortingForTheClientSession() {
        IngredientGuideSessionSettings settings = IngredientGuideSessionSettings.getInstance();
        IngredientGuideSessionSettings.SortOrder original = settings.sortOrder();
        try {
            settings.setSortOrder(
                    SortKey.RARITY,
                    SortDirection.DESCENDING,
                    SortKey.NAME,
                    SortDirection.ASCENDING);

            IngredientGuideSessionSettings.SortOrder retained =
                    IngredientGuideSessionSettings.getInstance().sortOrder();
            assertEquals(SortKey.RARITY, retained.primaryKey());
            assertEquals(SortDirection.DESCENDING, retained.primaryDirection());
            assertEquals(SortKey.NAME, retained.secondaryKey());
            assertEquals(SortDirection.ASCENDING, retained.secondaryDirection());
        } finally {
            settings.setSortOrder(
                    original.primaryKey(),
                    original.primaryDirection(),
                    original.secondaryKey(),
                    original.secondaryDirection());
        }
    }

    @Test
    void usesLevelAndRarityAscendingDefaults() {
        IngredientGuideSessionSettings.SortOrder defaults =
                IngredientGuideSessionSettings.SortOrder.defaults();

        assertEquals(SortKey.LEVEL, defaults.primaryKey());
        assertEquals(SortDirection.ASCENDING, defaults.primaryDirection());
        assertEquals(SortKey.RARITY, defaults.secondaryKey());
        assertEquals(SortDirection.ASCENDING, defaults.secondaryDirection());
    }
}
