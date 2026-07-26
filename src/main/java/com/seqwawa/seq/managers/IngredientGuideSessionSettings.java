package com.seqwawa.seq.managers;

import com.seqwawa.seq.managers.IngredientGuideManager.SortDirection;
import com.seqwawa.seq.managers.IngredientGuideManager.SortKey;

/**
 * In-memory ingredient guide preferences. This singleton intentionally has no
 * serialization so its values reset when the client exits.
 */
public final class IngredientGuideSessionSettings {
    private static final IngredientGuideSessionSettings INSTANCE = new IngredientGuideSessionSettings();

    private SortOrder sortOrder = SortOrder.defaults();

    private IngredientGuideSessionSettings() {}

    public static IngredientGuideSessionSettings getInstance() {
        return INSTANCE;
    }

    public synchronized SortOrder sortOrder() {
        return sortOrder;
    }

    public synchronized void setSortOrder(
            SortKey primaryKey,
            SortDirection primaryDirection,
            SortKey secondaryKey,
            SortDirection secondaryDirection) {
        sortOrder = new SortOrder(primaryKey, primaryDirection, secondaryKey, secondaryDirection);
    }

    public record SortOrder(
            SortKey primaryKey,
            SortDirection primaryDirection,
            SortKey secondaryKey,
            SortDirection secondaryDirection) {
        public SortOrder {
            primaryKey = primaryKey == null ? SortKey.LEVEL : primaryKey;
            primaryDirection = primaryDirection == null ? SortDirection.ASCENDING : primaryDirection;
            secondaryKey = secondaryKey == null ? SortKey.RARITY : secondaryKey;
            secondaryDirection = secondaryDirection == null ? SortDirection.ASCENDING : secondaryDirection;
            if (secondaryKey == primaryKey) {
                secondaryKey = primaryKey == SortKey.LEVEL ? SortKey.RARITY : SortKey.LEVEL;
            }
        }

        public static SortOrder defaults() {
            return new SortOrder(
                    SortKey.LEVEL,
                    SortDirection.ASCENDING,
                    SortKey.RARITY,
                    SortDirection.ASCENDING);
        }
    }
}
