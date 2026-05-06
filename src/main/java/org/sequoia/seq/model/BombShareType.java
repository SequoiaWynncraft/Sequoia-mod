package org.sequoia.seq.model;

import java.util.Locale;
import java.util.Optional;

public enum BombShareType {
    COMBAT_XP("combat xp", "dxp"),
    PROFESSION_XP("profession xp", "profxp"),
    PROFESSION_SPEED("profession speed", "profspeed"),
    LOOT("loot", "loot"),
    LOOT_CHEST("loot chest", "lc");

    private final String displayName;
    private final String primaryToken;

    BombShareType(String displayName, String primaryToken) {
        this.displayName = displayName;
        this.primaryToken = primaryToken;
    }

    public String displayName() {
        return displayName;
    }

    public String primaryToken() {
        return primaryToken;
    }

    public static Optional<BombShareType> fromWireValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(BombShareType.valueOf(rawValue.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
