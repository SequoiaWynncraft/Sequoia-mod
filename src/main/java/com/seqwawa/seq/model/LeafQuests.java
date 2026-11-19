package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The signed-in member's quests, as Leaf's {@code GET /v1/me/quests} sends them.
 *
 * <p>There is at most one quest per slot: a daily (dailies bank up, so missing a
 * day costs nothing), a weekly that ends on Monday, and a timed quest, the same for
 * the whole guild, open for two hours every four. Leaf can also hand out repeatable
 * quests, which players see as Endless; it doesn't for now.
 */
public record LeafQuests(
        Daily daily,
        Timed timed,
        List<Quest> quests,
        @SerializedName("recently_completed") List<Quest> recentlyCompleted) {

    public static final LeafQuests EMPTY = new LeafQuests(new Daily(0, 0, null), null, List.of(), List.of());

    public static final String DAILY = "daily";
    public static final String WEEKLY = "weekly";
    public static final String TIMED = "timed";

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
     * @param endsAt when the open timed quest closes, or null between windows
     * @param nextAt when the next timed quest opens
     */
    public record Timed(@SerializedName("ends_at") Instant endsAt, @SerializedName("next_at") Instant nextAt) {}

    /**
     * @param raid the raid a raid quest asks for, such as {@code nol}, or null for any raid
     */
    public record Objective(String type, String raid) {}

    public record Reward(long leaf, long xp) {}

    /**
     * One way to finish a quest, with its own progress. Most quests have two, raids
     * or wars, and whichever is met first finishes the quest.
     *
     * @param type {@code raids}, {@code wars} or {@code season_rating}
     * @param raid the raid a raid goal asks for, such as {@code nol}, or null for any raid
     * @param title the goal on its own, such as "Take part in 9 wars"
     */
    public record Goal(String type, String raid, String title, long progress, long target) {

        public boolean met() {
            return target > 0 && progress >= target;
        }
    }

    /**
     * @param title the whole quest, such as "Complete 3 NOL raids or 9 wars"
     * @param objective what the goal nearest to being met asks for; with {@code progress}
     *     and {@code target}, what clients with room for one bar show
     * @param goals every way to finish it; absent from Leaf before either-or quests
     */
    public record Quest(
            String id,
            String slot,
            String title,
            Objective objective,
            long progress,
            long target,
            List<Goal> goals,
            Reward reward,
            @SerializedName("started_at") Instant startedAt,
            @SerializedName("expires_at") Instant expiresAt,
            @SerializedName("completed_at") Instant completedAt) {

        public boolean completed() {
            return completedAt != null;
        }

        /** Every way to finish it, in Leaf's order: its goals, or the one objective an older Leaf sends. */
        public List<Goal> goalsOrObjective() {
            List<Goal> listed = goals == null ? List.of() : goals.stream().filter(Objects::nonNull).toList();
            if (!listed.isEmpty()) return listed;
            return List.of(new Goal(objective == null ? null : objective.type(),
                    objective == null ? null : objective.raid(), title, progress, target));
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
