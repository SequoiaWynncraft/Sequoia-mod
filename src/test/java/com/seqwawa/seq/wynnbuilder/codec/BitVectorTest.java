package com.seqwawa.seq.wynnbuilder.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BitVectorTest {

    @Test
    void writesAndReadsUnsignedFieldsInOrder() {
        BitVector vector = new BitVector();
        vector.append(12, 6);
        vector.append(33, 10);
        vector.append(5428, 13);

        vector.seek(0);
        assertEquals(12, vector.readInt(6));
        assertEquals(33, vector.readInt(10));
        assertEquals(5428, vector.readInt(13));
    }

    @Test
    void readsTwosComplementSignedValues() {
        BitVector vector = new BitVector();
        vector.append(-150 & 0xFFF, 12);
        vector.append(2047, 12);
        vector.append(-2048 & 0xFFF, 12);

        vector.seek(0);
        assertEquals(-150, vector.readSigned(12));
        assertEquals(2047, vector.readSigned(12));
        assertEquals(-2048, vector.readSigned(12));
    }

    @Test
    void base64RoundTripPreservesEverySixBitGroup() {
        String text = "0Kv2SJ+-Az";
        BitVector vector = BitVector.fromBase64(text);
        assertEquals(text.length() * 6, vector.length());
        assertEquals(text, vector.toBase64());
    }

    @Test
    void base64IsLeastSignificantBitFirst() {
        // Upstream stores bit i at (1 << i) and reads fields by right-shifting, so the earliest bit
        // of a character is its lowest one. 'K' is 20 = 0b010100, giving 0,0,1,0,1,0 in stream order.
        // Getting this backwards produces hashes WynnBuilder cannot read, so pin it explicitly.
        BitVector vector = BitVector.fromBase64("K");
        assertFalse(vector.bitAt(0));
        assertFalse(vector.bitAt(1));
        assertTrue(vector.bitAt(2));
        assertFalse(vector.bitAt(3));
        assertTrue(vector.bitAt(4));
        assertFalse(vector.bitAt(5));
    }

    @Test
    void appendedFieldsAreLeastSignificantBitFirst() {
        BitVector vector = new BitVector();
        vector.append(0b101, 3);
        assertEquals("101", vector.toString());

        BitVector wider = new BitVector();
        wider.append(0b1100, 4);
        assertEquals("0011", wider.toString());
    }

    @Test
    void appendVectorSplicesBitsWithoutRealigning() {
        BitVector inner = new BitVector();
        inner.append(0b101, 3);

        BitVector outer = new BitVector();
        outer.append(0b11, 2);
        outer.appendVector(inner);

        assertEquals("11101", outer.toString());
    }

    @Test
    void singleCharacterValuesMatchTheBase64Table() {
        // A six-bit field written on its own must render as exactly that Base64 digit.
        for (int value = 0; value < 64; value++) {
            BitVector vector = new BitVector();
            vector.append(value, 6);
            assertEquals(String.valueOf(WynnBase64.digit(value)), vector.toBase64());
        }
    }

    @Test
    void readingPastTheEndFailsLoudly() {
        BitVector vector = new BitVector();
        vector.append(1, 4);
        vector.seek(0);
        vector.read(4);
        assertThrows(IllegalStateException.class, () -> vector.read(1));
    }

    @Test
    void padToBase64BoundaryFillsToAMultipleOfSix() {
        BitVector vector = new BitVector();
        vector.append(0b1011, 4);
        vector.padToBase64Boundary();
        assertEquals(6, vector.length());
    }
}
