package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;

/**
 * The signed-in member's Leaf, as Leaf's {@code GET /v1/me} sends it.
 *
 * @param leaf the spendable balance
 * @param xp all XP ever earned; it never drops when Leaf is spent
 */
public record LeafAccount(long leaf, long xp, Level level) {

    public static final LeafAccount EMPTY = new LeafAccount(0, 0, new Level(1, 0, 0));

    public record Level(
            int number,
            @SerializedName("xp_into_level") long xpIntoLevel,
            @SerializedName("xp_for_next_level") long xpForNextLevel) {}

    public Level levelOrFirst() {
        return level == null ? EMPTY.level() : level;
    }
}
