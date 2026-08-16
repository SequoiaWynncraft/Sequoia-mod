package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WarPlannerScreenTest {
    @Test
    void narrowManagerTabsReserveSpaceForActionButton() {
        float screenWidth = 320;
        float tabsRight = 12 + WarPlannerScreen.tabWidth(screenWidth, true) * 3;
        float managerActionLeft = screenWidth - 92;

        assertTrue(tabsRight <= managerActionLeft);
    }
}
