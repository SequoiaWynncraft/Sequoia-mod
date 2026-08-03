package com.seqwawa.seq.utils;

import java.util.regex.Pattern;

/** Shared validation and normalization rules for Minecraft usernames. */
public final class MinecraftUsername {
    private static final Pattern VALID_USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private MinecraftUsername() {}

    /** Returns whether the raw value is a valid username without modifying it. */
    public static boolean isValid(String value) {
        return value != null && VALID_USERNAME.matcher(value).matches();
    }

    /** Trims and validates a username, preserving its case, or returns {@code null}. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return isValid(normalized) ? normalized : null;
    }
}
