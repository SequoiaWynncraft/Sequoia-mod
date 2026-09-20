package com.seqwawa.seq.model;

import java.util.UUID;

/** Only the backend formats and authorizes an achievement announcement. */
public record AchievementAnnouncementClaim(Announcement announcement) {
    public record Announcement(UUID id, String message) {}
}
