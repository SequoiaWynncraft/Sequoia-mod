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
}
