package com.seqwawa.seq.managers;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryService;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import java.time.Duration;
import java.time.Instant;
import net.minecraft.client.Minecraft;

/** Sends local reminders while the player is inside a queued war territory. */
public final class WarTerritoryBoundsReminder {
    private static final long MINIMUM_REMAINING_SECONDS_FOR_MESSAGE = 2L;

    private String trackedTerritory;
    private long lastReminderAtMillis;

    public void tick(Minecraft client) {
        WarTerritoryQueueManager queues = SeqClient.warTerritoryQueueManager;
        if (SeqClient.warQueueBoundsReminderSetting == null
                || !SeqClient.warQueueBoundsReminderSetting.getValue()
                || client == null
                || client.player == null
                || client.level == null
                || queues == null) {
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
            if (trackedTerritory != null) {
                client.player.displayClientMessage(NotificationAccessor.prefixed(
                        leaveMessage(trackedTerritory)), false);
                clear();
            }
            return;
        }

        long remainingSeconds = Math.max(0L, Duration.between(now, currentQueue.expiresAt()).getSeconds());
        if (remainingSeconds < MINIMUM_REMAINING_SECONDS_FOR_MESSAGE) {
            clear();
            return;
        }

        long nowMillis = System.currentTimeMillis();
        if (!currentQueue.territory().equalsIgnoreCase(trackedTerritory)) {
            if (trackedTerritory != null) {
                client.player.displayClientMessage(NotificationAccessor.prefixed(
                        leaveMessage(trackedTerritory)), false);
            }
            trackedTerritory = currentQueue.territory();
            sendReminder(client, currentQueue, now);
            lastReminderAtMillis = nowMillis;
        } else if (nowMillis - lastReminderAtMillis >= reminderInterval().toMillis()) {
            sendReminder(client, currentQueue, now);
            lastReminderAtMillis = nowMillis;
        }
    }

    public void clear() {
        trackedTerritory = null;
        lastReminderAtMillis = 0L;
    }

    private static Duration reminderInterval() {
        int seconds = SeqClient.warQueueBoundsReminderIntervalSetting == null
                ? 60
                : SeqClient.warQueueBoundsReminderIntervalSetting.getValue();
        return Duration.ofSeconds(seconds);
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

    private static String leaveMessage(String territory) {
        return "You've left bounds of the war for " + territory;
    }
}
