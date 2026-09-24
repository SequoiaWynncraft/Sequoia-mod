package com.seqwawa.seq.model;

/** Lifetime raid totals from the guild payload: damage, healing, deaths, gambits. */
public record RaidPerformance(long damageDealt, long healthHealed, long deaths, long gambitsUsed) {

    private static final RaidPerformance UNKNOWN = new RaidPerformance(0L, 0L, 0L, 0L);

    public static RaidPerformance unknown() {
        return UNKNOWN;
    }

    public boolean isKnown() {
        return damageDealt > 0L || healthHealed > 0L || deaths > 0L || gambitsUsed > 0L;
    }
}
