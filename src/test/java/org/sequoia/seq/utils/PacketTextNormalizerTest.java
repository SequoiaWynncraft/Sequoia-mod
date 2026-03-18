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

    @Test
    void normalizesSplitRaidNameAndFinishedBoundary() {
        String normalized = PacketTextNormalizer.normalizeForParsing(
                "󏿼󏿿󏿾 Tannslee, JeongSooMin, wisedrag, and D4MIT finished Nest\n"
                        + "󏿼󐀆 of the Grootslangs and claimed 2x Aspects, 2048x Emeralds\n"
                        + "󏿼󐀆 , and +10367m Guild Experience");

        assertEquals(
                "Tannslee, JeongSooMin, wisedrag, and D4MIT finished Nest of the Grootslangs and claimed 2x Aspects, 2048x Emeralds, and +10367m Guild Experience",
                normalized);
    }

    @Test
    void normalizesSplitGuildBankActionBoundary() {
        String normalized = PacketTextNormalizer.normalizeForParsing(
                "󏿼󏿿󏿾 Purprated withdrew 1x Gelibord Teleportation Scroll [3/3]\n"
                        + "󏿼󐀆 from the Guild Bank (Everyone)");

        assertEquals(
                "Purprated withdrew 1x Gelibord Teleportation Scroll [3/3] from the Guild Bank (Everyone)",
                normalized);
    }

    @Test
    void stripsPacketGarbageButPreservesGuildChatContent() {
        String normalized = PacketTextNormalizer.normalizeForParsing(
                "󏿼󐀆 Emanant Force: r u trying to pind kaia\u0000\u200B");

        assertEquals("Emanant Force: r u trying to pind kaia", normalized);
    }
}
