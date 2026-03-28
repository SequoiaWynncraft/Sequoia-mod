package org.sequoia.seq.model;

import java.util.List;

public record GuildWarSubmission(
        String territory,
        String submittedBy,
        String submittedAt,
        String startTime,
        List<String> warrers,
        TowerStats stats,
        int seasonRating,
        boolean completed) {

    public record TowerStats(long damageLow, long damageHigh, double attackSpeed, long health, double defence) {}
}
