package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.List;

/**
 * The signed-in member's quests, as Leaf's {@code GET /v1/me/quests} sends them.
 *
 * <p>There is at most one quest per slot: a daily (dailies bank up, so missing a
 * day costs nothing), a weekly that ends on Monday, and a repeatable that is
 * replaced as soon as it is finished.
 */
public record LeafQuests(
        Daily daily,
        List<Quest> quests,
        @SerializedName("recently_completed") List<Quest> recentlyCompleted) {

    public static final LeafQuests EMPTY = new LeafQuests(new Daily(0, 0, null), List.of(), List.of());

    public static final String DAILY = "daily";
    public static final String WEEKLY = "weekly";
    public static final String REPEATABLE = "repeatable";

    /**
     * @param charges dailies the member can do, counting the one in progress
     * @param chargeCap how many dailies can be banked
     * @param nextChargeAt when the next daily arrives, or null while the bank is full
     */
    public record Daily(
            int charges,
            @SerializedName("charge_cap") int chargeCap,
            @SerializedName("next_charge_at") Instant nextChargeAt) {}

    /**
     * @param raid the raid a raid quest asks for, such as {@code nol}, or null for any raid
     */
    public record Objective(String type, String raid) {}

    public record Reward(long leaf, long xp) {}

    public record Quest(
            String id,
            String slot,
            String title,
            Objective objective,
            long progress,
            long target,
            Reward reward,
            @SerializedName("started_at") Instant startedAt,
            @SerializedName("expires_at") Instant expiresAt,
            @SerializedName("completed_at") Instant completedAt) {

        public boolean completed() {
            return completedAt != null;
        }
    }

    /** The quest in a slot, or null when the slot is empty right now. */
    public Quest quest(String slot) {
        if (quests == null) return null;
        for (Quest quest : quests) {
            if (quest != null && slot.equals(quest.slot())) return quest;
        }
        return null;
    }

    public Daily dailyOrEmpty() {
        return daily == null ? EMPTY.daily() : daily;
    }
}
