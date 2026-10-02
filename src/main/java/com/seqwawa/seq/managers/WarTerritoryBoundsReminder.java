package com.seqwawa.seq.managers;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryService;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import java.time.Duration;
import java.time.Instant;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Sends local reminders while the player is inside a queued war territory. */
public final class WarTerritoryBoundsReminder {
    private static final long MINIMUM_REMAINING_SECONDS_FOR_MESSAGE = 2L;

    private String trackedTerritory;

    public void tick(Minecraft client) {
        WarTerritoryQueueManager queues = SeqClient.warTerritoryQueueManager;
        if (client == null
                || client.player == null
                || client.level == null
                || queues == null) {
            clear();
            return;
        }
        boolean chatRemindersEnabled = SeqClient.warQueueBoundsReminderSetting != null
                && SeqClient.warQueueBoundsReminderSetting.getValue();
        if (!chatRemindersEnabled && !titleEnabled()) {
            clear();
            return;
        }

        GuildTerritoryService territoryService = GuildTerritoryService.getInstance();
        territoryService.loadBundledTerritories();
        var territoryIndex = territoryService.index();
        Instant now = queues.serverNow();
        double x = client.player.getX();
        double z = client.player.getZ();
        TerritoryQueue currentQueue = null;
        for (TerritoryQueue queue : queues.activeQueues()) {
            GuildTerritory territory = territoryIndex.territory(queue.territory());
            if (territory == null || queue.expiresAt() == null || !queue.expiresAt().isAfter(now)) {
                continue;
            }
            if (territory.contains(x, z)) {
                currentQueue = queue;
                break;
            }
        }

        if (currentQueue == null) {
            if (shouldShowLeaveMessage(
                    chatRemindersEnabled, trackedTerritory, null)) {
                client.player.displayClientMessage(NotificationAccessor.prefixed(
                        leaveMessage(trackedTerritory)), false);
            }
            clear();
            return;
        }

        long remainingSeconds = Math.max(0L, Duration.between(now, currentQueue.expiresAt()).getSeconds());
        if (shouldShowCountdown(titleEnabled(), remainingSeconds, titleCountdownSeconds())) {
            showWarTitle(client, countdownTitle(remainingSeconds));
        }
        if (remainingSeconds < MINIMUM_REMAINING_SECONDS_FOR_MESSAGE) {
            // Don't send left bounds message if being teleported into the war.
            clear();
            return;
        }

        if (shouldShowEntryMessage(
                true, trackedTerritory, currentQueue.territory())) {
            if (shouldShowLeaveMessage(
                    chatRemindersEnabled, trackedTerritory, currentQueue.territory())) {
                client.player.displayClientMessage(NotificationAccessor.prefixed(
                        leaveMessage(trackedTerritory)), false);
            }
            trackedTerritory = currentQueue.territory();
            if (chatRemindersEnabled) {
                sendReminder(client, currentQueue, now);
            }
        }
    }

    public void clear() {
        trackedTerritory = null;
    }

    private static int titleCountdownSeconds() {
        return SeqClient.warQueueBoundsReminderTitleCountdownSetting.getValue();
    }

    private static void sendReminder(Minecraft client, TerritoryQueue queue, Instant now) {
        long remainingSeconds = Math.max(0L, Duration.between(now, queue.expiresAt()).getSeconds());
        long remainingMinutes = remainingSeconds / 60L;
        long remainingSecondsPart = remainingSeconds % 60L;
        client.player.displayClientMessage(NotificationAccessor.prefixed(
                "You're in bounds of the war for " + queue.territory() + " in "
                        + remainingMinutes + "m" + remainingSecondsPart + "s"),
                false);
    }

    private static boolean titleEnabled() {
        return SeqClient.warQueueBoundsReminderTitleSetting != null
                && SeqClient.warQueueBoundsReminderTitleSetting.getValue();
    }

    private static void showWarTitle(Minecraft client, String title) {
        client.gui.setTimes(0, 2, 0);
        client.gui.setTitle(Component.literal(title).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private static String leaveMessage(String territory) {
        return "You've left bounds of the war for " + territory;
    }

    static boolean shouldShowCountdown(boolean enabled, long remainingSeconds, int countdownSeconds) {
        return enabled && remainingSeconds > 0L && remainingSeconds < countdownSeconds;
    }

    static boolean shouldShowEntryMessage(boolean enabled, String trackedTerritory, String currentTerritory) {
        return enabled && currentTerritory != null && !sameTerritory(trackedTerritory, currentTerritory);
    }

    static boolean shouldShowLeaveMessage(boolean enabled, String trackedTerritory, String currentTerritory) {
        return enabled && trackedTerritory != null && !sameTerritory(trackedTerritory, currentTerritory);
    }

    static String countdownTitle(long remainingSeconds) {
        return "War starts in " + remainingSeconds + "s";
    }

    private static boolean sameTerritory(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }
}
