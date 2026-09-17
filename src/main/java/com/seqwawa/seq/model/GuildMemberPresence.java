package com.seqwawa.seq.model;

import java.util.Locale;

/**
 * One guild member as the panel shows them, from the Wynncraft guild API.
 * <p>
 * Busy state is absent on purpose: it expires on a timer and is read live from
 * {@link com.seqwawa.seq.managers.GuildRaidActivityTracker} while rendering, so a
 * countdown keeps ticking between roster refreshes.
 */
public record GuildMemberPresence(
        String username,
        String uuid,
        GuildRank rank,
        /** The world they are on, such as {@code NA6}, or null when not reported. */
        String world,
        /** Whether this member has the mod connected to the backend. */
        boolean sequoiaConnected,
        /** Wynncraft's own numbers, never self-declared. */
        GuildMemberStats stats) {

    /** For callers with no Wynncraft stats to attach. */
    public GuildMemberPresence(
            String username, String uuid, GuildRank rank, String world, boolean sequoiaConnected) {
        this(username, uuid, rank, world, sequoiaConnected, GuildMemberStats.unknown());
    }

    public GuildMemberPresence {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        rank = rank == null ? GuildRank.RECRUIT : rank;
        world = world == null || world.isBlank() ? null : world.trim().toUpperCase(Locale.ROOT);
        stats = stats == null ? GuildMemberStats.unknown() : stats;
    }

    /** Case-insensitive key, for matching against chat and raid names. */
    public String key() {
        return username.toLowerCase(Locale.ROOT);
    }

    public boolean hasWorld() {
        return world != null;
    }

    public GuildMemberPresence withSequoiaConnected(boolean connected) {
        return new GuildMemberPresence(username, uuid, rank, world, connected, stats);
    }

    /** Wynncraft guild ranks, ordered from most to least senior. */
    public enum GuildRank {
        OWNER("Owner"),
        CHIEF("Chief"),
        STRATEGIST("Strategist"),
        CAPTAIN("Captain"),
        RECRUITER("Recruiter"),
        RECRUIT("Recruit");

        private final String displayName;

        GuildRank(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        /** Parses the lowercase rank keys the guild API groups members under. */
        public static GuildRank fromApiKey(String key) {
            if (key == null) {
                return RECRUIT;
            }
            try {
                return valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return RECRUIT;
            }
        }
    }
}
