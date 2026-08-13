package com.seqwawa.seq.wynnbuilder.codec;

/**
 * WynnBuilder's custom Base64 alphabet.
 *
 * <p>This is not RFC 4648: the digits are ordered {@code 0-9A-Za-z+-} so that a single character
 * maps directly to its numeric value, which is what makes the legacy fixed-width encodings
 * readable. Both the legacy and the V12 binary encodings use this table.
 */
public final class WynnBase64 {
    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+-";
    private static final int[] VALUES = new int[128];

    static {
        java.util.Arrays.fill(VALUES, -1);
        for (int i = 0; i < DIGITS.length(); i++) {
            VALUES[DIGITS.charAt(i)] = i;
        }
    }

    private WynnBase64() {}

    /** Returns the character for a 6-bit digit. */
    public static char digit(int value) {
        if (value < 0 || value >= 64) {
            throw new IllegalArgumentException("Base64 digit out of range: " + value);
        }
        return DIGITS.charAt(value);
    }

    /** Returns the 6-bit value of a character, or -1 when it is not part of the alphabet. */
    public static int value(char character) {
        return character < 128 ? VALUES[character] : -1;
    }

    public static boolean isDigit(char character) {
        return value(character) >= 0;
    }

    /**
     * Decodes an unsigned big-endian Base64 number.
     *
     * @throws IllegalArgumentException when the text contains a character outside the alphabet
     */
    public static long toLong(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Base64 number must not be empty");
        }
        long result = 0;
        for (int i = 0; i < text.length(); i++) {
            int value = value(text.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base64 character '" + text.charAt(i) + "'");
            }
            result = result * 64 + value;
        }
        return result;
    }

    public static int toInt(String text) {
        return Math.toIntExact(toLong(text));
    }

    /**
     * Decodes a Base64 number as a two's-complement signed value of {@code text.length() * 6} bits.
     * Used for skill points and custom item identifications.
     */
    public static int toSignedInt(String text) {
        long unsigned = toLong(text);
        int bits = text.length() * 6;
        if (bits >= 64) {
            return Math.toIntExact(unsigned);
        }
        long signBit = 1L << (bits - 1);
        if ((unsigned & signBit) != 0) {
            unsigned -= (1L << bits);
        }
        return Math.toIntExact(unsigned);
    }

    /** Encodes a non-negative value into exactly {@code length} Base64 characters. */
    public static String fromInt(long value, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        char[] out = new char[length];
        long remaining = value;
        for (int i = length - 1; i >= 0; i--) {
            out[i] = digit((int) (remaining & 63));
            remaining >>>= 6;
        }
        if (remaining != 0) {
            throw new IllegalArgumentException(value + " does not fit in " + length + " Base64 characters");
        }
        return new String(out);
    }
}
