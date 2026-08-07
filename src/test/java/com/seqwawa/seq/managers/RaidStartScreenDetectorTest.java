package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RaidStartScreenDetectorTest {
    @Test
    void recognizesWynncraftRaidStartTitle() {
        assertTrue(RaidStartScreenDetector.isRaidStartTitle("\uDAFF\uDFE1\uE00C"));
    }

    @Test
    void rejectsOtherAndMissingTitles() {
        assertFalse(RaidStartScreenDetector.isRaidStartTitle("Start Raid"));
        assertFalse(RaidStartScreenDetector.isRaidStartTitle("\uDAFF\uDFE1"));
        assertFalse(RaidStartScreenDetector.isRaidStartTitle(null));
    }

    @Test
    void identifiesKnownLocalGambitSlots() {
        assertTrue(RaidStartScreenDetector.isGambitSlot(1));
        assertTrue(RaidStartScreenDetector.isGambitSlot(3));
        assertTrue(RaidStartScreenDetector.isGambitSlot(5));
        assertTrue(RaidStartScreenDetector.isGambitSlot(7));
        assertFalse(RaidStartScreenDetector.isGambitSlot(0));
        assertFalse(RaidStartScreenDetector.isGambitSlot(8));
    }
}
