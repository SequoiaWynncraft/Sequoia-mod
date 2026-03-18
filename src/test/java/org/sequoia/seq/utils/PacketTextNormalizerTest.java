package org.sequoia.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PacketTextNormalizerTest {

    @Test
    void normalizesMultilineGuildBankPacketText() {
        String normalized = PacketTextNormalizer.normalizeForParsing(
                "󏿼󐀆 a3pki deposited 1x MR dagger [100%] to the Guild Bank (\n󏿼󐀆 Everyone)");

        assertEquals("a3pki deposited 1x MR dagger [100%] to the Guild Bank (Everyone)", normalized);
    }

    @Test
    void normalizesSplitRaidRewardComma() {
        String normalized = PacketTextNormalizer.normalizeForParsing(
                "󏿼󏿿󏿾 bubblebouncy, xmattypazox, death by choking, and divvy\n"
                        + "󏿼󐀆 lunne finished The Nameless Anomaly and claimed 2x Aspects\n"
                        + "󏿼󐀆 , 2048x Emeralds, and +10367m Guild Experience");

        assertEquals(
                "bubblebouncy, xmattypazox, death by choking, and divvy lunne finished The Nameless Anomaly and claimed 2x Aspects, 2048x Emeralds, and +10367m Guild Experience",
                normalized);
    }
}
