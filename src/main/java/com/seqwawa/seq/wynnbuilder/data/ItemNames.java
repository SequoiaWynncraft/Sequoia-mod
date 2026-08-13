package com.seqwawa.seq.wynnbuilder.data;

import java.util.Locale;

/**
 * Normalisation for matching in-game item names against the data files.
 *
 * <p>The two sources disagree in small ways that stop an exact match: the game renders typographic
 * apostrophes and dashes where the data uses ASCII ones, item names carry colour codes and custom
 * font glyphs, and powdered items have a bracketed suffix appended. Both sides are pushed through
 * this before comparison, so the index and the lookup can never drift apart.
 */
public final class ItemNames {

    private ItemNames() {}

    /** Reduces a name to its comparable form. */
    public static String normalise(String name) {
        if (name == null) {
            return "";
        }
        String text = stripFormatting(name);
        text = stripBracketedSuffix(text);

        StringBuilder out = new StringBuilder(text.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            char canonical = canonicalise(character);
            if (canonical == ' ') {
                // Collapse runs of whitespace so double spaces cannot break a match.
                if (!lastWasSpace && out.length() > 0) {
                    out.append(' ');
                }
                lastWasSpace = true;
                continue;
            }
            if (canonical == '\0') {
                continue;
            }
            out.append(canonical);
            lastWasSpace = false;
        }
        return out.toString().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Maps a character to its comparable form.
     *
     * @return the character to keep, {@code ' '} for whitespace, or {@code '\0'} to drop it
     */
    private static char canonicalise(char character) {
        return switch (character) {
            // Typographic punctuation the game uses where the data files use ASCII.
            case '‘', '’', 'ʼ', '´', '`' -> '\'';
            case '–', '—', '−' -> '-';
            case '“', '”' -> '"';
            case ' ', ' ', ' ' -> ' ';
            default -> {
                if (Character.isWhitespace(character)) {
                    yield ' ';
                }
                // Wynncraft renders icons through private-use glyphs; they carry no meaning here.
                if (Character.getType(character) == Character.PRIVATE_USE
                        || Character.isISOControl(character)) {
                    yield '\0';
                }
                yield character;
            }
        };
    }

    /** Removes section-sign colour codes. */
    public static String stripFormatting(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            out.append(character);
        }
        return out.toString();
    }

    /**
     * Removes a trailing bracketed suffix, which is how the game shows applied powders.
     *
     * <p>Only a trailing group is removed, so an item whose real name contains brackets is safe.
     */
    public static String stripBracketedSuffix(String text) {
        String trimmed = text.trim();
        while (trimmed.endsWith("]")) {
            int open = trimmed.lastIndexOf('[');
            if (open < 0) {
                break;
            }
            trimmed = trimmed.substring(0, open).trim();
        }
        return trimmed;
    }
}
