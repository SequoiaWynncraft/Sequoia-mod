package com.seqwawa.seq.model;

/** Immutable live tower sample for the active guild war. */
public record WarTowerUpdate(String territory, float health, long ehp, long dps) {
    public WarTowerUpdate {
        territory = territory == null ? null : territory.trim();
        if (territory == null || territory.isEmpty()) {
            throw new IllegalArgumentException("A tower update requires a territory.");
        }
        if (!Float.isFinite(health) || health < 0.0f || health > 1.0f) {
            throw new IllegalArgumentException("Tower health must be finite and between 0 and 1.");
        }
        if (ehp < 0L || dps < 0L) {
            throw new IllegalArgumentException("Tower EHP and DPS must be nonnegative.");
        }
    }
}
