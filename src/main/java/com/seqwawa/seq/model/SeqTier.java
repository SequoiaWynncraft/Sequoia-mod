package com.seqwawa.seq.model;

import java.util.List;

public enum SeqTier {
    BRONZE("Bronze", 25),
    SILVER("Silver", 50),
    GOLD("Gold", 100),
    PLATINUM("Platinum", 250),
    DIAMOND("Diamond", 500),
    OBSIDIAN("Obsidian", 1000),
    MYTHRIL("Mythril", 2500);

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

    public static SeqTier reached(int count, int scale) {
        SeqTier reached = null;
        for (SeqTier tier : ORDERED) {
            if (count >= tier.threshold(scale)) {
                reached = tier;
            }
        }
        return reached;
    }

    public static SeqTier next(int count, int scale) {
        for (SeqTier tier : ORDERED) {
            if (count < tier.threshold(scale)) {
                return tier;
            }
        }
        return null;
    }
}
