package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class AspectAwardWaveControllerTest {

    @Test
    void schedulesAspectClicksAtExactTwoTickIntervals() {
        AspectAwardWaveController controller = controller(3);

        assertEquals(RewardClickController.NextAction.CLICK, controller.tick());
        controller.recordClick(1);
        assertEquals(RewardClickController.NextAction.WAIT, controller.tick());
        assertEquals(RewardClickController.NextAction.CLICK, controller.tick());
        controller.recordClick(1);
        assertEquals(RewardClickController.NextAction.WAIT, controller.tick());
        assertEquals(RewardClickController.NextAction.CLICK, controller.tick());
    }

    @Test
    void sendsTheConfirmedDeficitAsANewWaveAfterFiveHundredMilliseconds() {
        AspectAwardWaveController controller = controller(30);

        sendClicks(controller, 30, 1);
        confirm(controller, 24);

        for (int tick = 1; tick < AspectAwardWaveController.WAVE_SETTLE_TICKS; tick++) {
            assertEquals(RewardClickController.NextAction.WAIT, controller.tick());
        }
        assertEquals(RewardClickController.NextAction.CLICK, controller.tick());
        controller.recordClick(1);

        sendAdditionalClicks(controller, 5, 1);
        confirm(controller, 6);

        assertEquals(RewardClickController.NextAction.COMPLETE, controller.tick());
        assertEquals(30, controller.confirmedAmount());
        assertEquals(0, controller.remainingToConfirm());
    }

    @Test
    void repeatsDeficitWavesAndStopsAsSoonAsTargetIsConfirmed() {
        AspectAwardWaveController controller = controller(30);

        sendClicks(controller, 30, 1);
        confirm(controller, 24);
        advanceToNextWave(controller);
        controller.recordClick(1);
        sendAdditionalClicks(controller, 5, 1);

        confirm(controller, 5);
        advanceToNextWave(controller);
        controller.recordClick(1);
        confirm(controller, 2);

        assertEquals(RewardClickController.NextAction.COMPLETE, controller.tick());
        assertEquals(31, controller.confirmedAmount());
    }

    @Test
    void stallsInsteadOfRepeatingAWaveThatProducedNoConfirmations() {
        AspectAwardWaveController controller = controller(2);

        sendClicks(controller, 2, 1);
        for (int tick = 1; tick < AspectAwardWaveController.NO_PROGRESS_TIMEOUT_TICKS; tick++) {
            assertEquals(RewardClickController.NextAction.WAIT, controller.tick());
        }

        assertEquals(RewardClickController.NextAction.STALLED, controller.tick());
    }

    @Test
    void acceptsOnlyMatchingAspectConfirmations() {
        AspectAwardWaveController controller = controller(3);

        assertNull(controller.recordRewardGrant(new GuildStorageTracker.RewardGrant(
                "AnotherPlayer", "cinfrascitizen", GuildStorageTracker.ResourceType.ASPECTS, 1)));
        assertNull(controller.recordRewardGrant(new GuildStorageTracker.RewardGrant(
                "Dwoc", "AnotherPlayer", GuildStorageTracker.ResourceType.ASPECTS, 1)));
        assertNull(controller.recordRewardGrant(new GuildStorageTracker.RewardGrant(
                "Dwoc", "cinfrascitizen", GuildStorageTracker.ResourceType.EMERALDS, 1_024)));

        RewardClickController.ConfirmationProgress progress = controller.recordRewardGrant(
                GuildStorageTracker.parseRewardGrant(
                        Component.literal("Dwoc rewarded an Aspect to cinfrascitizen")));

        assertEquals(new RewardClickController.ConfirmationProgress(1, 3), progress);
    }

    private static AspectAwardWaveController controller(long targetAmount) {
        return new AspectAwardWaveController(targetAmount, "dwoc", "CinfrasCitizen");
    }

    private static void confirm(AspectAwardWaveController controller, long amount) {
        controller.recordRewardGrant(new GuildStorageTracker.RewardGrant(
                "Dwoc", "cinfrascitizen", GuildStorageTracker.ResourceType.ASPECTS, amount));
    }

    private static void sendClicks(AspectAwardWaveController controller, int count, long amountPerClick) {
        if (count <= 0) {
            return;
        }
        assertEquals(RewardClickController.NextAction.CLICK, controller.tick());
        controller.recordClick(amountPerClick);
        sendAdditionalClicks(controller, count - 1, amountPerClick);
    }

    private static void sendAdditionalClicks(
            AspectAwardWaveController controller, int count, long amountPerClick) {
        for (int click = 0; click < count; click++) {
            assertEquals(RewardClickController.NextAction.WAIT, controller.tick());
            assertEquals(RewardClickController.NextAction.CLICK, controller.tick());
            controller.recordClick(amountPerClick);
        }
    }

    private static void advanceToNextWave(AspectAwardWaveController controller) {
        for (int tick = 1; tick < AspectAwardWaveController.WAVE_SETTLE_TICKS; tick++) {
            assertEquals(RewardClickController.NextAction.WAIT, controller.tick());
        }
        assertEquals(RewardClickController.NextAction.CLICK, controller.tick());
    }
}
