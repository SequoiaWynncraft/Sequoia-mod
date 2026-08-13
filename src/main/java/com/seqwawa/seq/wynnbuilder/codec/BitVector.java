package com.seqwawa.seq.wynnbuilder.codec;

import java.util.Arrays;

/**
 * Least-significant-bit-first bit buffer backing the V12 WynnBuilder encodings.
 *
 * <p>The bit order is not a free choice: it must match the reference implementation exactly or the
 * produced links are unreadable. Upstream stores bit <em>i</em> at {@code 1 << (i % 32)} and reads a
 * field by right-shifting, so within both a field and a Base64 character the earliest bit is the
 * <em>least</em> significant one. Appending the value {@code 0b101} in three bits therefore lays
 * down 1, 0, 1 in stream order.
 *
 * <p>Note this differs from {@link WynnBase64#toLong}, which reads legacy multi-character numbers
 * big-endian across characters. The two schemes genuinely coexist: legacy hashes are fixed-width
 * Base64 numbers, V12 hashes are this bit vector rendered six bits at a time.
 */
public final class BitVector {
    private long[] words;
    private int length;
    private int cursor;

    public BitVector() {
        this.words = new long[8];
    }

    /** Builds a vector from a Base64 string, 6 bits per character, ready for reading. */
    public static BitVector fromBase64(String text) {
        BitVector vector = new BitVector();
        for (int i = 0; i < text.length(); i++) {
            int value = WynnBase64.value(text.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base64 character '" + text.charAt(i) + "'");
            }
            vector.append(value, 6);
        }
        vector.cursor = 0;
        return vector;
    }

    /** Number of bits written. */
    public int length() {
        return length;
    }

    /** Current read position. */
    public int position() {
        return cursor;
    }

    public void seek(int position) {
        if (position < 0 || position > length) {
            throw new IndexOutOfBoundsException("position " + position + " outside [0, " + length + "]");
        }
        this.cursor = position;
    }

    public int remaining() {
        return length - cursor;
    }

    public boolean hasRemaining(int bits) {
        return remaining() >= bits;
    }

    /** Appends the low {@code bits} of {@code value}, least significant bit first. */
    public void append(long value, int bits) {
        if (bits < 0 || bits > 64) {
            throw new IllegalArgumentException("bits must be in [0, 64]");
        }
        for (int i = 0; i < bits; i++) {
            appendBit(((value >>> i) & 1L) != 0);
        }
    }

    public void appendBit(boolean bit) {
        ensureCapacity(length + 1);
        if (bit) {
            words[length >>> 6] |= 1L << (length & 63);
        }
        length++;
    }

    /** Appends every bit of another vector in stream order, used to splice embedded blobs in. */
    public void appendVector(BitVector other) {
        for (int i = 0; i < other.length; i++) {
            appendBit(other.bitAt(i));
        }
    }

    public boolean bitAt(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("bit " + index + " outside [0, " + length + ")");
        }
        return (words[index >>> 6] & (1L << (index & 63))) != 0;
    }

    /** Reads the next {@code bits} as an unsigned value, least significant bit first. */
    public long read(int bits) {
        if (bits < 0 || bits > 64) {
            throw new IllegalArgumentException("bits must be in [0, 64]");
        }
        if (!hasRemaining(bits)) {
            throw new IllegalStateException("Truncated data: needed " + bits + " bits, " + remaining() + " left");
        }
        long value = 0;
        for (int i = 0; i < bits; i++) {
            if (bitAt(cursor++)) {
                value |= 1L << i;
            }
        }
        return value;
    }

    public int readInt(int bits) {
        return Math.toIntExact(read(bits));
    }

    public boolean readBit() {
        return read(1) != 0;
    }

    /** Reads the next {@code bits} as a two's-complement signed value. */
    public int readSigned(int bits) {
        long raw = read(bits);
        if (bits >= 64) {
            return Math.toIntExact(raw);
        }
        long signBit = 1L << (bits - 1);
        if ((raw & signBit) != 0) {
            raw -= (1L << bits);
        }
        return Math.toIntExact(raw);
    }

    /** Appends zero bits until the length is a multiple of 6, as required before Base64 output. */
    public void padToBase64Boundary() {
        while (length % 6 != 0) {
            appendBit(false);
        }
    }

    /** Renders the vector to Base64, six bits per character, least significant bit first. */
    public String toBase64() {
        StringBuilder out = new StringBuilder((length + 5) / 6);
        for (int i = 0; i < length; i += 6) {
            int value = 0;
            for (int bit = 0; bit < 6; bit++) {
                int index = i + bit;
                if (index < length && bitAt(index)) {
                    value |= 1 << bit;
                }
            }
            out.append(WynnBase64.digit(value));
        }
        return out.toString();
    }

    private void ensureCapacity(int bits) {
        int neededWords = (bits + 63) >>> 6;
        if (neededWords > words.length) {
            words = Arrays.copyOf(words, Math.max(neededWords, words.length * 2));
        }
    }

    /** Bits in stream order, earliest first. Useful in tests and debugging. */
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(bitAt(i) ? '1' : '0');
        }
        return out.toString();
    }
}
