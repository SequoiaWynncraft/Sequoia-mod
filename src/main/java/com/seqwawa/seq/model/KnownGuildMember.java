package com.seqwawa.seq.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * A guild member the roster knows, online or not.
 * <p>
 * {@code lastJoin} is when Wynncraft saw them connect, not when they left, which is
 * why the labels say "logged in" rather than "seen".
 */
public record KnownGuildMember(String username, String uuid, Instant lastJoin) {

    public KnownGuildMember {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        username = username.trim();
        uuid = uuid == null || uuid.isBlank() ? null : uuid.trim();
    }

    public String key() {
        return username.toLowerCase(Locale.ROOT);
    }

    /** "logged in 2h ago", or null when Wynncraft did not say. */
    public String lastLoginLabel(Instant now) {
        String ago = formatAgo(lastJoin, now);
        return ago == null ? null : "logged in " + ago;
    }

    /** Elapsed time cut down to fit a column: {@code 35m}, {@code 2h}, {@code 3d}. */
    public static String formatElapsedShort(Instant then, Instant now) {
        if (then == null || now == null) {
            return null;
        }
        Duration elapsed = Duration.between(then, now);
        if (elapsed.isNegative() || elapsed.toMinutes() < 1) {
            return "now";
        }
        if (elapsed.toMinutes() < 60) {
            return elapsed.toMinutes() + "m";
        }
        if (elapsed.toHours() < 48) {
            return elapsed.toHours() + "h";
        }
        return elapsed.toDays() + "d";
    }

    /** How long ago {@code then} was, in a single unit, or null when unknown. */
    public static String formatAgo(Instant then, Instant now) {
        if (then == null || now == null) {
            return null;
        }
        Duration elapsed = Duration.between(then, now);
        if (elapsed.isNegative() || elapsed.toMinutes() < 1) {
            return "just now";
        }
        long minutes = elapsed.toMinutes();
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = elapsed.toHours();
        if (hours < 48) {
            return hours + "h ago";
        }
        long days = elapsed.toDays();
        if (days < 60) {
            return days + "d ago";
        }
        return (days / 30) + " months ago";
    }
}
