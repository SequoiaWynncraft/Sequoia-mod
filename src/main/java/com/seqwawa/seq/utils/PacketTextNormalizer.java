package com.seqwawa.seq.utils;

import java.util.regex.Pattern;

/**
 * Normalizes raw packet-level Wynncraft chat text into a parsing-friendly form.
 * This strips private-use glyph spam, collapses multiline packets, and repairs
 * punctuation spacing introduced by line splits.
 */
public final class PacketTextNormalizer {
    private static final Pattern LEGACY_FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern AMPERSAND_FORMATTING_PATTERN = Pattern.compile("(?i)&[0-9A-FK-OR]");
    private static final Pattern AMPERSAND_FONT_TAG_PATTERN = Pattern.compile("&\\{[^}]+\\}");
    private static final Pattern SPACE_BEFORE_PUNCTUATION_PATTERN = Pattern.compile("\\s+([,.:;!?])");
    private static final Pattern SPACE_AFTER_OPENING_DELIMITER_PATTERN = Pattern.compile("([\\[(])\\s+");
    private static final Pattern SPACE_BEFORE_CLOSING_DELIMITER_PATTERN = Pattern.compile("\\s+([\\])])");
    private static final Pattern COMMA_SPACING_PATTERN = Pattern.compile("\\s*,\\s*");
    private static final Pattern SLASH_SPACING_PATTERN = Pattern.compile("(?<=\\d)\\s*/\\s*(?=\\d)");
    private static final Pattern MULTISPACE_PATTERN = Pattern.compile(" {2,}");

    private PacketTextNormalizer() {
    }

    public static String normalizeForParsing(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String strippedFormatting = LEGACY_FORMATTING_PATTERN.matcher(rawText).replaceAll("").replace("§", "");
        strippedFormatting = AMPERSAND_FONT_TAG_PATTERN.matcher(strippedFormatting).replaceAll("");
        strippedFormatting = AMPERSAND_FORMATTING_PATTERN.matcher(strippedFormatting).replaceAll("");
        strippedFormatting = joinWrappedUrlContinuations(strippedFormatting);
        StringBuilder normalized = new StringBuilder(strippedFormatting.length());
        boolean previousWasSpace = false;

        for (int index = 0; index < strippedFormatting.length();) {
            int codePoint = strippedFormatting.codePointAt(index);
            index += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint) || isIgnorableForParsing(codePoint)) {
                if (!previousWasSpace) {
                    normalized.append(' ');
                    previousWasSpace = true;
                }
                continue;
            }

            normalized.appendCodePoint(codePoint);
            previousWasSpace = false;
        }

        String cleaned = normalized.toString().trim();
        cleaned = SPACE_BEFORE_PUNCTUATION_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = SPACE_AFTER_OPENING_DELIMITER_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = SPACE_BEFORE_CLOSING_DELIMITER_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = COMMA_SPACING_PATTERN.matcher(cleaned).replaceAll(", ");
        cleaned = SLASH_SPACING_PATTERN.matcher(cleaned).replaceAll("/");
        cleaned = MULTISPACE_PATTERN.matcher(cleaned).replaceAll(" ");
        return cleaned.trim();
    }

    private static String joinWrappedUrlContinuations(String text) {
        StringBuilder repaired = new StringBuilder(text.length());
        boolean insideUrl = false;

        for (int index = 0; index < text.length();) {
            if (!insideUrl && startsUrlAt(text, index)) {
                insideUrl = true;
            }

            int codePoint = text.codePointAt(index);
            int nextIndex = index + Character.charCount(codePoint);
            if (insideUrl && (codePoint == '\n' || codePoint == '\r')) {
                int continuationIndex = nextIndex;
                boolean foundPacketDecoration = false;
                while (continuationIndex < text.length()) {
                    int continuationCodePoint = text.codePointAt(continuationIndex);
                    if (!Character.isWhitespace(continuationCodePoint)
                            && !isIgnorableForParsing(continuationCodePoint)) {
                        break;
                    }
                    if (continuationCodePoint != '\n'
                            && continuationCodePoint != '\r'
                            && !Character.isWhitespace(continuationCodePoint)) {
                        foundPacketDecoration = true;
                    }
                    continuationIndex += Character.charCount(continuationCodePoint);
                }
                if (foundPacketDecoration) {
                    index = continuationIndex;
                    continue;
                }
            }

            repaired.appendCodePoint(codePoint);
            if (Character.isWhitespace(codePoint)) {
                insideUrl = false;
            }
            index = nextIndex;
        }
        return repaired.toString();
    }

    private static boolean startsUrlAt(String text, int index) {
        return text.regionMatches(true, index, "https://", 0, "https://".length())
                || text.regionMatches(true, index, "http://", 0, "http://".length());
    }

    private static boolean isIgnorableForParsing(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.PRIVATE_USE,
                    Character.SURROGATE,
                    Character.UNASSIGNED -> true;
            default -> false;
        };
    }
}
