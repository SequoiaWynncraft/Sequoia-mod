package com.seqwawa.seq.model.war;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Immutable schema-v1 feed of active territory war queues. */
public record WarTerritoryQueueFeed(
        @SerializedName("schema_version") int schemaVersion,
        long revision,
        @SerializedName("server_time") Instant serverTime,
        List<TerritoryQueue> queues) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public WarTerritoryQueueFeed {
        queues = queues == null
                ? List.of()
                : queues.stream().filter(java.util.Objects::nonNull).toList();
    }

    public static WarTerritoryQueueFeed empty() {
        return new WarTerritoryQueueFeed(SUPPORTED_SCHEMA_VERSION, 0L, null, List.of());
    }

    public boolean isSupported() {
        return schemaVersion == SUPPORTED_SCHEMA_VERSION;
    }

    public record TerritoryQueue(
            long id,
            String territory,
            @SerializedName("queued_by") String queuedBy,
            @SerializedName("minecraft_username") String minecraftUsername,
            String nickname,
            @SerializedName("queued_defense_rating") String queuedDefenseRating,
            @SerializedName("reported_defense_rating") String reportedDefenseRating,
            @SerializedName("queued_at") Instant queuedAt,
            @SerializedName("expires_at") Instant expiresAt,
            List<Participant> participants) {

        public TerritoryQueue {
            territory = normalizeRequiredText(territory);
            queuedBy = normalizeOptionalText(queuedBy);
            minecraftUsername = normalizeOptionalText(minecraftUsername);
            nickname = normalizeOptionalText(nickname);
            queuedDefenseRating = normalizeOptionalText(queuedDefenseRating);
            reportedDefenseRating = normalizeOptionalText(reportedDefenseRating);
            participants = participants == null
                    ? List.of()
                    : participants.stream().filter(java.util.Objects::nonNull).toList();
        }

        public String displayName() {
            if (minecraftUsername != null) {
                if (nickname != null && !minecraftUsername.equalsIgnoreCase(nickname)) {
                    return minecraftUsername + "/" + nickname;
                }
                return minecraftUsername;
            }
            if (nickname != null) {
                return nickname;
            }
            return queuedBy == null ? "Unknown" : queuedBy;
        }

        public boolean provisional() {
            return queuedBy == null;
        }

        public int participantCount() {
            return Math.min(5, participants.size() + (provisional() ? 1 : 0));
        }

        public boolean full() {
            return participantCount() >= 5;
        }

        public boolean hasParticipant(String playerUuid) {
            if (playerUuid == null || playerUuid.isBlank()) {
                return false;
            }
            return participants.stream().anyMatch(participant ->
                    playerUuid.equalsIgnoreCase(participant.playerUuid()));
        }

        public boolean isExpired(Instant now) {
            return expiresAt != null && now != null && !expiresAt.isAfter(now);
        }

        public String territoryKey() {
            return territory.toLowerCase(Locale.ROOT);
        }
    }

    public record Participant(
            @SerializedName("player_uuid") String playerUuid,
            @SerializedName("minecraft_username") String minecraftUsername,
            int position) {

        public Participant {
            playerUuid = normalizeRequiredText(playerUuid);
            minecraftUsername = normalizeOptionalText(minecraftUsername);
            position = Math.max(0, position);
        }
    }

    private static String normalizeRequiredText(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? "" : normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
