package com.seqwawa.seq.model;

public record GuildWarQueueSubmission(
        String territory,
        String submittedBy,
        String submittedAt,
        String defenseRating,
        int queueMinutes
) {}
