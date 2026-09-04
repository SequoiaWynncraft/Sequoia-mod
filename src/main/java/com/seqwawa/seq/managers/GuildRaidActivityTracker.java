package com.seqwawa.seq.managers;

import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers when each guild member last finished a raid, so the members panel can
 * show who is mid-session rather than free to pull into a group.
 * <p>
 * The signal is the raid completion report already built for the Discord relay:
 * every announcement names its whole party, so one completion marks four to ten
 * members at once. Two paths feed it, and both write the same entries:
 * <ul>
 * <li>{@link RaidTracker} for a completion the local client witnessed, which
 * covers the local player's own party without any server round trip;</li>
 * <li>an incoming {@code guild_raid_announcement} relayed by the backend, which
 * covers everyone else running the mod.</li>
 * </ul>
 * A member counts as busy for {@link #BUSY_WINDOW} after their last completion.
 * That is not a guess at whether they are inside a raid right now — it is the
 * useful question answered instead: someone who cleared minutes ago is looting,
 * re-buffing or queueing again, and inviting them cuts that short.
 */
public final class GuildRaidActivityTracker {

    /**
     * How long a completion keeps someone marked busy. A raid clear is followed by
     * the reward chest, aspect handout and a regroup; eight minutes covers that
     * without holding the mark so long that it stops meaning anything.
     */
    public static final Duration BUSY_WINDOW = Duration.ofMinutes(8);

    /** Entries older than this are dropped, so the map cannot grow without bound. */
    private static final Duration RETENTION = BUSY_WINDOW.multipliedBy(2);

    private static final Map<String, Long> LAST_COMPLETION_MS = new ConcurrentHashMap<>();

    private GuildRaidActivityTracker() {}

    /** Records a completed raid for every member named in the announcement. */
    public static void recordCompletion(Collection<String> usernames) {
        recordCompletion(usernames, System.currentTimeMillis());
    }

    static void recordCompletion(Collection<String> usernames, long completedAtMs) {
        if (usernames == null || usernames.isEmpty()) {
            return;
        }
        for (String username : usernames) {
            String key = normalize(username);
            if (key == null) {
                continue;
            }
            // A later report always wins: two clients relaying the same raid must not
            // let the slower one shorten the window the faster one already opened.
            LAST_COMPLETION_MS.merge(key, completedAtMs, Math::max);
        }
        prune(completedAtMs);
    }

    /** Milliseconds left on this member's busy window, or {@code 0} when they are free. */
    public static long busyRemainingMillis(String username) {
        return busyRemainingMillis(username, System.currentTimeMillis());
    }

    static long busyRemainingMillis(String username, long nowMs) {
        String key = normalize(username);
        if (key == null) {
            return 0L;
        }
        Long completedAt = LAST_COMPLETION_MS.get(key);
        if (completedAt == null) {
            return 0L;
        }
        long remaining = completedAt + BUSY_WINDOW.toMillis() - nowMs;
        return Math.max(0L, remaining);
    }

    public static boolean isBusy(String username) {
        return busyRemainingMillis(username) > 0L;
    }

    /** Drops completions old enough that they can no longer make anyone busy. */
    private static void prune(long nowMs) {
        long cutoff = nowMs - RETENTION.toMillis();
        Iterator<Map.Entry<String, Long>> iterator = LAST_COMPLETION_MS.entrySet().iterator();
        while (iterator.hasNext()) {
            Long completedAt = iterator.next().getValue();
            if (completedAt == null || completedAt < cutoff) {
                iterator.remove();
            }
        }
    }

    private static String normalize(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    static void reset() {
        LAST_COMPLETION_MS.clear();
    }
}
