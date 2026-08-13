package com.seqwawa.seq.wynnbuilder.codec;

import java.util.Locale;

/**
 * Parsing and formatting of WynnBuilder URLs.
 *
 * <p>Accepts what people actually paste: a full builder or crafter URL, a URL from one of the
 * mirrors or forks, or a bare hash copied out of a chat message.
 */
public final class WynnBuilderLinks {
    public static final String BUILDER_URL = "https://wynnbuilder.github.io/builder/#";
    public static final String CRAFTER_URL = "https://wynnbuilder.github.io/crafter/#";

    private WynnBuilderLinks() {}

    /** What a pasted link refers to. */
    public enum Kind {
        BUILD,
        CRAFT,
        UNKNOWN
    }

    public record ParsedLink(Kind kind, String hash) {
        public boolean isValid() {
            return hash != null && !hash.isEmpty();
        }
    }

    public static String buildUrl(String hash) {
        return BUILDER_URL + hash;
    }

    public static String craftUrl(String hash) {
        return CRAFTER_URL + hash;
    }

    /**
     * Extracts the hash and guesses what it encodes.
     *
     * <p>The path decides the kind when present. A bare hash is reported as {@link Kind#UNKNOWN} so
     * the caller can decide: the two formats are not reliably distinguishable from the hash alone.
     */
    public static ParsedLink parse(String text) {
        if (text == null) {
            return new ParsedLink(Kind.UNKNOWN, null);
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return new ParsedLink(Kind.UNKNOWN, null);
        }

        Kind kind = Kind.UNKNOWN;
        String hash = trimmed;

        int hashIndex = trimmed.indexOf('#');
        if (hashIndex >= 0) {
            String beforeHash = trimmed.substring(0, hashIndex).toLowerCase(Locale.ROOT);
            hash = trimmed.substring(hashIndex + 1);
            if (beforeHash.contains("crafter")) {
                kind = Kind.CRAFT;
            } else if (beforeHash.contains("builder") || beforeHash.contains("index.html") || beforeHash.endsWith("/")) {
                kind = Kind.BUILD;
            }
        } else if (looksLikeUrl(trimmed)) {
            // A URL with no fragment carries no build.
            return new ParsedLink(Kind.UNKNOWN, null);
        }

        // Trailing query strings and stray whitespace are common in pasted links.
        int queryIndex = hash.indexOf('?');
        if (queryIndex >= 0) {
            hash = hash.substring(0, queryIndex);
        }
        hash = hash.trim();

        if (!isValidHash(hash)) {
            return new ParsedLink(kind, null);
        }
        return new ParsedLink(kind, hash);
    }

    /**
     * Whether every character belongs to the WynnBuilder alphabet.
     *
     * <p>Legacy build hashes additionally contain {@code _} separators and item names, so those are
     * tolerated here and rejected later by the legacy decoder if malformed.
     */
    public static boolean isValidHash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        for (int i = 0; i < hash.length(); i++) {
            char character = hash.charAt(i);
            if (!WynnBase64.isDigit(character) && character != '_' && character != '%') {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeUrl(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.contains("wynnbuilder");
    }
}
