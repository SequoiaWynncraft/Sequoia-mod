package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PacedRewardClickControllerTest {

    @Test
    void preservesTheThreeTickEmeraldClickInterval() {
        PacedRewardClickController controller = PacedRewardClickController.emeralds(Long.MAX_VALUE);

        assertEquals(RewardClickController.NextAction.CLICK, controller.tick());
        controller.recordClick(1_024);
        assertEquals(RewardClickController.NextAction.WAIT, controller.tick());
        assertEquals(RewardClickController.NextAction.WAIT, controller.tick());
        assertEquals(RewardClickController.NextAction.CLICK, controller.tick());
    }

    @Test
    void unlimitedEmeraldsCompleteOnlyAfterASentClickAndServerRejection() {
        PacedRewardClickController controller = PacedRewardClickController.emeralds(Long.MAX_VALUE);

        assertFalse(controller.recordInsufficientEmeralds());
        controller.recordClick(1_024);
        assertTrue(controller.recordInsufficientEmeralds());
        assertEquals(RewardClickController.NextAction.COMPLETE, controller.tick());
    }

    @Test
    void finiteEmeraldsAndTomesCompleteAfterTheirExistingPacingDelay() {
        PacedRewardClickController emeralds = PacedRewardClickController.emeralds(1_024);
        emeralds.recordClick(1_024);
        assertWaitsThenCompletes(
                emeralds, PacedRewardClickController.EMERALD_CLICK_INTERVAL_TICKS);

        PacedRewardClickController tome = PacedRewardClickController.tomes(1);
        tome.recordClick(1);
        assertWaitsThenCompletes(
                tome, PacedRewardClickController.TOME_CLICK_INTERVAL_TICKS);
    }

    private static void assertWaitsThenCompletes(
            PacedRewardClickController controller, int intervalTicks) {
        for (int tick = 1; tick < intervalTicks; tick++) {
            assertEquals(RewardClickController.NextAction.WAIT, controller.tick());
        }
        assertEquals(RewardClickController.NextAction.COMPLETE, controller.tick());
    }
}
