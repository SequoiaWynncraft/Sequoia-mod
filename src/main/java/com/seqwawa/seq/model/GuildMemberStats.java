package com.seqwawa.seq.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The half of a member's profile that comes from Wynncraft rather than from what
 * they told us: how long they have played, and how many of each raid they have
 * cleared while in the guild.
 * <p>
 * Clear counts are keyed by raid key rather than by a raid object, because the
 * raid list is the backend's and can change between one refresh and the next.
 * <p>
 * Both numbers ride along in the roster payload the panel already fetches, so
 * they cost nothing extra and nobody can inflate them.
 */
public record GuildMemberStats(double playtimeHours, Map<String, Integer> raidCompletions) {

    private static final GuildMemberStats UNKNOWN = new GuildMemberStats(0d, Map.of());

    public GuildMemberStats {
        raidCompletions = normalize(raidCompletions);
    }

    public static GuildMemberStats unknown() {
        return UNKNOWN;
    }

    public boolean isKnown() {
        return playtimeHours > 0d || !raidCompletions.isEmpty();
    }

    /** How many times this member cleared {@code raid} while in the guild. */
    public int completions(RaidType raid) {
        return raid == null ? 0 : completions(raid.key());
    }

    public int completions(String raidKey) {
        if (raidKey == null) {
            return 0;
        }
        return raidCompletions.getOrDefault(raidKey.trim().toUpperCase(Locale.ROOT), 0);
    }

    public int totalRaidCompletions() {
        return raidCompletions.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Playtime as Wynncraft presents it, rounded to whole hours. */
    public String playtimeLabel() {
        if (playtimeHours <= 0d) {
            return "?";
        }
        return Math.round(playtimeHours) + "h";
    }

    /** Worded for a row: {@code 3566 guild raids} or {@code 1 guild raid}. */
    public String raidCountLabel(RaidType raid) {
        int clears = completions(raid);
        return clears + (clears == 1 ? " guild raid" : " guild raids");
    }

    private static Map<String, Integer> normalize(Map<String, Integer> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        value.forEach((key, count) -> {
            if (key != null && count != null && !key.isBlank()) {
                normalized.put(key.trim().toUpperCase(Locale.ROOT), count);
            }
        });
        return Map.copyOf(normalized);
    }
}
