package com.seqwawa.seq.model;

import java.util.Locale;

/**
 * One guild member as the members panel shows them.
 * <p>
 * The facts here come from the Wynncraft guild API and change only when the
 * roster is refreshed. Raid state is deliberately absent: it expires on a timer
 * and is read live from {@link com.seqwawa.seq.managers.GuildRaidActivityTracker}
 * at render time, so a countdown ticks down between refreshes.
 */
public record GuildMemberPresence(
        String username,
        String uuid,
        GuildRank rank,
        /** The world the member is on, such as {@code NA6}, or null when Wynncraft does not report one. */
        String world,
        /** Whether this member has the Sequoia mod connected to the backend right now. */
        boolean sequoiaConnected) {

    public GuildMemberPresence {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        rank = rank == null ? GuildRank.RECRUIT : rank;
        world = world == null || world.isBlank() ? null : world.trim().toUpperCase(Locale.ROOT);
    }

    /** Case-insensitive key used to match this member against chat and raid names. */
    public String key() {
        return username.toLowerCase(Locale.ROOT);
    }

    public boolean hasWorld() {
        return world != null;
    }

    public GuildMemberPresence withSequoiaConnected(boolean connected) {
        return new GuildMemberPresence(username, uuid, rank, world, connected);
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
