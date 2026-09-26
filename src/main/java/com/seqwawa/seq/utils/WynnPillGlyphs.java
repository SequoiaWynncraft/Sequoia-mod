package com.seqwawa.seq.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decoder for the private-use glyph "pills" Wynncraft draws around rank badges
 * in chat (the rounded box reading e.g. {@code RECRUITER} before a guild
 * message).
 * <p>
 * A pill is a run of characters from Wynncraft's negative-space font: corner
 * glyphs, one background block plus one back-offset per character, and the
 * letter glyphs themselves. Letters live at {@code U+E040..U+E059} and digits at
 * {@code U+E060..U+E069}, which is what makes the visible label recoverable.
 * Newer guild-rank badges use a layered form framed by {@code U+E060}/{@code U+E062}:
 * their foreground letters live at {@code U+E030..U+E049}, separated by
 * {@code U+CFFFF}, and a shadow layer repeats them at {@code U+E000..U+E019}.
 * <p>
 * Detection deliberately keys on the letter glyphs and on the Basic Multilingual
 * Plane private-use block as a whole rather than on specific corner or
 * background codepoints, because Wynncraft varies those between badge styles.
 * The supplementary-plane private-use characters Wynncraft uses for guild icons
 * and prestige bars fall outside that block, so they break a run instead of
 * being swallowed into it.
 *
 * @see com.seqwawa.seq.accessors.NotificationAccessor NotificationAccessor, which encodes pills
 */
public final class WynnPillGlyphs {

    public static final char BACKGROUND = '\uE00F';
    public static final char CORNER_LEFT = '\uE010';
    public static final char CORNER_RIGHT = '\uE011';
    public static final char TEXT_OFFSET = '\uE012';
    /** INVISIBLE PLUS, used by Wynncraft as a zero-width padding character. */
    public static final char SEPARATOR = '\u2064';

    private static final char LETTER_FIRST = '\uE040';
    private static final char LETTER_LAST = '\uE059';
    private static final char DIGIT_FIRST = '\uE060';
    private static final char DIGIT_LAST = '\uE069';

    private static final int LAYERED_CORNER_LEFT = 0xE060;
    private static final int LAYERED_CORNER_RIGHT = 0xE062;
    private static final int LAYERED_SEPARATOR = 0xCFFFF;
    private static final int LAYERED_LETTER_FIRST = 0xE030;
    private static final int LAYERED_LETTER_LAST = 0xE049;
    private static final int LAYERED_SHADOW_FIRST = 0xE000;
    private static final int LAYERED_ADVANCE_FIRST = 0xCFF00;
    private static final int LAYERED_ADVANCE_LAST = 0xCFFFE;
    private static final int LAYERED_END = 0xD0002;

    /** Basic Multilingual Plane private use area, where the pill font lives. */
    private static final char PRIVATE_USE_FIRST = '\uE000';
    private static final char PRIVATE_USE_LAST = '\uF8FF';

    /**
     * Tail of that block reserved for glyphs this mod draws itself: the Discord bridge
     * marker and its continuation bar, the insignia, and the advance spacers (see
     * {@code assets/seq/font}).
     * <p>
     * They are private-use characters exactly like Wynncraft's, so without carving them
     * out the decorator cannot tell its own output from a server badge, and replaces
     * one with the other. Wynncraft's own glyphs all sit far below this range.
     */
    public static final char MOD_GLYPH_FIRST = '\uF8E0';
    public static final char MOD_GLYPH_LAST = '\uF8FE';

    /** Shortest label worth treating as a badge, to skip decorative glyph runs. */
    private static final int MIN_LABEL_LENGTH = 2;

    private WynnPillGlyphs() {}

    /** Cheap pre-check so callers can skip work on ordinary chat lines. */
    public static boolean containsPill(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            if (isPrivateUse(text.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds every readable pill in {@code text}.
     * <p>
     * Runs are split on a closing corner glyph when Wynncraft uses the known one, so
     * two adjacent pills stay separate; otherwise the whole glyph run is reported as
     * one pill, which still lets callers replace it wholesale.
     */
    public static List<Pill> findPills(String text) {
        List<Pill> pills = new ArrayList<>(findLayeredPills(text));
        findGlyphRuns(text).stream()
                .filter(pill -> hasMinimumLabelLength(pill.label()))
                .filter(candidate -> pills.stream().noneMatch(layered -> overlaps(layered, candidate)))
                .forEach(pills::add);
        pills.sort(Comparator.comparingInt(Pill::start));
        return List.copyOf(pills);
    }

    /** Reads Wynncraft's current two-layer guild-rank badge representation. */
    private static List<Pill> findLayeredPills(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<Pill> pills = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            if (text.codePointAt(index) != LAYERED_CORNER_LEFT) {
                index += Character.charCount(text.codePointAt(index));
                continue;
            }

            Pill pill = readLayeredPill(text, index);
            if (pill == null) {
                index += Character.charCount(LAYERED_CORNER_LEFT);
                continue;
            }
            pills.add(pill);
            index = pill.endExclusive();
        }
        return List.copyOf(pills);
    }

    private static Pill readLayeredPill(String text, int start) {
        int cursor = start + Character.charCount(LAYERED_CORNER_LEFT);
        StringBuilder label = new StringBuilder();
        while (cursor < text.length() && text.codePointAt(cursor) == LAYERED_SEPARATOR) {
            cursor += Character.charCount(LAYERED_SEPARATOR);
            if (cursor >= text.length()) {
                return null;
            }

            int glyph = text.codePointAt(cursor);
            cursor += Character.charCount(glyph);
            if (glyph == LAYERED_CORNER_RIGHT) {
                if (label.length() < MIN_LABEL_LENGTH) {
                    return null;
                }
                return new Pill(start, layeredPillEnd(text, cursor, label), label.toString());
            }

            char decoded = decodeLayeredLetter(glyph);
            if (decoded == 0) {
                return null;
            }
            label.append(decoded);
        }
        return null;
    }

    /** Includes the advance and shadow layer when it exactly repeats the foreground. */
    private static int layeredPillEnd(String text, int primaryEnd, StringBuilder label) {
        int cursor = primaryEnd;
        if (cursor >= text.length()) {
            return primaryEnd;
        }
        int advance = text.codePointAt(cursor);
        if (advance < LAYERED_ADVANCE_FIRST || advance > LAYERED_ADVANCE_LAST) {
            return primaryEnd;
        }
        cursor += Character.charCount(advance);

        for (int index = 0; index < label.length(); index++) {
            if (cursor >= text.length()) {
                return primaryEnd;
            }
            int expected = LAYERED_SHADOW_FIRST + (label.charAt(index) - 'a');
            int actual = text.codePointAt(cursor);
            if (actual != expected) {
                return primaryEnd;
            }
            cursor += Character.charCount(actual);
        }

        if (cursor >= text.length() || text.codePointAt(cursor) != LAYERED_END) {
            return primaryEnd;
        }
        return cursor + Character.charCount(LAYERED_END);
    }

    private static char decodeLayeredLetter(int glyph) {
        return glyph >= LAYERED_LETTER_FIRST && glyph <= LAYERED_LETTER_LAST
                ? (char) ('a' + glyph - LAYERED_LETTER_FIRST)
                : 0;
    }

    private static boolean overlaps(Pill left, Pill right) {
        return left.start() < right.endExclusive() && right.start() < left.endExclusive();
    }

    /**
     * Every glyph run, including those whose label could not be decoded. Callers that
     * already know a run is a rank badge, because of where it sits on a guild chat
     * line, can use this to replace it without reading its text.
     */
    public static List<Pill> findGlyphRuns(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<Pill> runs = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            if (!isGlyphChar(text.charAt(index))) {
                index++;
                continue;
            }

            int runStart = index;
            StringBuilder label = new StringBuilder();
            while (index < text.length() && isGlyphChar(text.charAt(index))) {
                char current = text.charAt(index);
                index++;

                char decoded = decodeGlyph(current);
                if (decoded != 0) {
                    label.append(decoded);
                    continue;
                }
                // NotificationAccessor represents a space with a background block but
                // no text-offset/glyph pair. That missing pair is the only information
                // needed to recover the gap in a multi-word label.
                if (current == BACKGROUND && (index >= text.length() || text.charAt(index) != TEXT_OFFSET)) {
                    label.append(' ');
                    continue;
                }
                if (current == CORNER_RIGHT && hasMinimumLabelLength(label)) {
                    while (index < text.length() && isPadding(text.charAt(index))) {
                        index++;
                    }
                    runs.add(new Pill(runStart, index, label.toString()));
                    label.setLength(0);
                    runStart = index;
                }
            }

            if (runStart < index) {
                runs.add(new Pill(runStart, index, label.toString()));
            }
        }
        return List.copyOf(runs);
    }

    private static boolean hasMinimumLabelLength(CharSequence label) {
        int readable = 0;
        for (int index = 0; index < label.length(); index++) {
            if (!Character.isWhitespace(label.charAt(index)) && ++readable >= MIN_LABEL_LENGTH) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when Wynncraft's pill font has a glyph for {@code rawChar}, i.e. when it is
     * a letter or a digit. Anything else has no glyph and must not be drawn on top of a
     * background block: the block and the character advance by different amounts, which
     * shifts the whole rest of the pill off its background.
     */
    public static boolean hasGlyph(char rawChar) {
        char lowered = Character.toLowerCase(rawChar);
        return (lowered >= 'a' && lowered <= 'z') || (lowered >= '0' && lowered <= '9');
    }

    /**
     * Maps a plain character to its pill glyph, or leaves it as-is when Wynncraft's
     * font has no glyph for it.
     */
    public static String encodeGlyph(char rawChar) {
        char lowered = Character.toLowerCase(rawChar);
        if (lowered >= 'a' && lowered <= 'z') {
            return String.valueOf((char) (LETTER_FIRST + (lowered - 'a')));
        }
        if (lowered >= '0' && lowered <= '9') {
            return String.valueOf((char) (DIGIT_FIRST + (lowered - '0')));
        }
        return String.valueOf(rawChar);
    }

    /**
     * Builds the raw glyph text of a pill. Rendering a two-tone pill needs one
     * component per glyph, so this is only for plain-text uses such as tests and
     * log output; see {@code NotificationAccessor#wynnPill} for the rendered form.
     */
    public static String encodePlainPill(String label) {
        StringBuilder pill = new StringBuilder().append(CORNER_LEFT).append(SEPARATOR);
        for (int index = 0; index < label.length(); index++) {
            char rawChar = label.charAt(index);
            pill.append(BACKGROUND);
            if (hasGlyph(rawChar)) {
                pill.append(TEXT_OFFSET).append(encodeGlyph(rawChar));
            }
        }
        return pill.append(CORNER_RIGHT).append(SEPARATOR).toString();
    }

    /** Maps a pill glyph back to its plain character, or {@code 0} if it is not one. */
    public static char decodeGlyph(char glyph) {
        if (glyph >= LETTER_FIRST && glyph <= LETTER_LAST) {
            return (char) ('a' + (glyph - LETTER_FIRST));
        }
        if (glyph >= DIGIT_FIRST && glyph <= DIGIT_LAST) {
            return (char) ('0' + (glyph - DIGIT_FIRST));
        }
        return 0;
    }

    private static boolean isGlyphChar(char glyph) {
        return isPrivateUse(glyph) || isPadding(glyph);
    }

    private static boolean isPrivateUse(char glyph) {
        return glyph >= PRIVATE_USE_FIRST && glyph <= PRIVATE_USE_LAST && !isModGlyph(glyph);
    }

    /** True for a glyph this mod draws itself, which is never part of a server badge. */
    public static boolean isModGlyph(char glyph) {
        return glyph >= MOD_GLYPH_FIRST && glyph <= MOD_GLYPH_LAST;
    }

    private static boolean isPadding(char glyph) {
        return Character.getType(glyph) == Character.FORMAT;
    }

    /**
     * A decoded pill.
     *
     * @param start        index of the first glyph
     * @param endExclusive index just past the last glyph and its padding
     * @param label        lowercase decoded text, e.g. {@code recruiter}
     */
    public record Pill(int start, int endExclusive, String label) {}
}
