package com.seqwawa.seq.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What Wynncraft says about a member, as opposed to what they declared: playtime,
 * guild raid clears, wars, total level, contribution, join date and raid totals.
 * <p>
 * Clear counts are keyed by raid key, not by a raid object, because the raid list
 * comes from the backend and can change between refreshes.
 */
public record GuildMemberStats(
        double playtimeHours,
        Map<String, Integer> raidCompletions,
        int wars,
        int totalLevel,
        /** Guild XP contributed, in the trillions for veterans. */
        long contributedXp,
        /** Rank of that contribution in the guild, 1 being the highest. */
        int contributionRank,
        /** When they joined the guild. */
        Instant joinedGuildAt,
        RaidPerformance raidPerformance) {

    private static final GuildMemberStats UNKNOWN =
            new GuildMemberStats(0d, Map.of(), 0, 0, 0L, 0, null, RaidPerformance.unknown());

    private static final DateTimeFormatter JOINED_FORMAT =
            DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH);

    public GuildMemberStats {
        raidCompletions = normalize(raidCompletions);
        raidPerformance = raidPerformance == null ? RaidPerformance.unknown() : raidPerformance;
    }

    /** For callers that only know playtime and clear counts. */
    public GuildMemberStats(double playtimeHours, Map<String, Integer> raidCompletions) {
        this(playtimeHours, raidCompletions, 0, 0, 0L, 0, null, RaidPerformance.unknown());
    }

    public static GuildMemberStats unknown() {
        return UNKNOWN;
    }

    public boolean isKnown() {
        return playtimeHours > 0d || !raidCompletions.isEmpty() || wars > 0 || totalLevel > 0;
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

    public String warsLabel() {
        return wars > 0 ? formatCount(wars) : "?";
    }

    public String totalLevelLabel() {
        return totalLevel > 0 ? formatCount(totalLevel) : "?";
    }

    /** {@code #3} for the third biggest contributor, or {@code ?} when unknown. */
    public String contributionRankLabel() {
        return contributionRank > 0 ? "#" + contributionRank : "?";
    }

    public String contributedXpLabel() {
        return contributedXp > 0L ? formatCompact(contributedXp) : "?";
    }

    /** {@code Mar 2023}, the month they joined the guild. */
    public String joinedGuildLabel() {
        return joinedGuildAt == null ? "?" : JOINED_FORMAT.format(joinedGuildAt.atZone(ZoneId.systemDefault()));
    }

    /** Thousands separated, the way a count reads: {@code 12,056}. */
    public static String formatCount(long value) {
        return String.format(Locale.ENGLISH, "%,d", value);
    }

    /** {@code 369B}, {@code 37.3T}. Anything under a million stays a plain count. */
    public static String formatCompact(long value) {
        if (value < 1_000_000L) {
            return formatCount(value);
        }
        double scaled;
        String unit;
        if (value >= 1_000_000_000_000L) {
            scaled = value / 1_000_000_000_000d;
            unit = "T";
        } else if (value >= 1_000_000_000L) {
            scaled = value / 1_000_000_000d;
            unit = "B";
        } else {
            scaled = value / 1_000_000d;
            unit = "M";
        }
        String number = scaled >= 100d
                ? String.valueOf(Math.round(scaled))
                : String.format(Locale.ENGLISH, "%.1f", scaled);
        return number + unit;
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
