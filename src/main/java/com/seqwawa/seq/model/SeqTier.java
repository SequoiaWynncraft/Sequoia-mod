package com.seqwawa.seq.model;

import java.util.List;
import java.util.Locale;

public enum SeqTier {
    BRONZE("Bronze", 25),
    SILVER("Silver", 50),
    GOLD("Gold", 100),
    PLATINUM("Platinum", 500),
    DIAMOND("Diamond", 1_000),
    OBSIDIAN("Obsidian", 2_500),
    MYTHRIL("Mythril", 5_000);

    public static final int SINGLE_RAID = 1;
    public static final int ALL_RAIDS = 2;

    private static final List<SeqTier> ORDERED = List.of(values());

    private final String label;
    private final int threshold;

    SeqTier(String label, int threshold) {
        this.label = label;
        this.threshold = threshold;
    }

    public String label() {
        return label;
    }

    public int threshold(int scale) {
        return threshold * scale;
    }

    public static List<SeqTier> ordered() {
        return ORDERED;
    }

    public static SeqTier next(int count, int scale) {
        return next(count, null, scale);
    }

    public static SeqTier next(int count, SeqTier currentTier, int scale) {
        int start = currentTier == null ? 0 : currentTier.ordinal() + 1;
        for (int index = start; index < ORDERED.size(); index++) {
            SeqTier tier = ORDERED.get(index);
            if (count < tier.threshold(scale)) {
                return tier;
            }
        }
        return null;
    }

    public static SeqTier fromKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
