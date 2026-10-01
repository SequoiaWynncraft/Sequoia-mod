package com.seqwawa.seq.map;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.MinecraftWarWorldDetector;
import com.seqwawa.seq.map.IngredientWaypointManager.Kind;
import com.seqwawa.seq.map.IngredientWaypointManager.Waypoint;
import com.seqwawa.seq.map.IngredientWaypointManager.WaypointIcon;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Derives a session-only marker without modifying manually selected ingredient waypoints. */
public final class WarQueueWaypointProvider {
    private WarQueueWaypointProvider() {}

    public static Optional<Waypoint> current(double playerY) {
        var queues = SeqClient.warTerritoryQueueManager;
        if (queues == null
                || SeqClient.warQueueAutoWaypointSetting == null
                || !SeqClient.warQueueAutoWaypointSetting.getValue()
                || MinecraftWarWorldDetector.isWarInstance()) {
            return Optional.empty();
        }
        GuildTerritoryService territories = GuildTerritoryService.getInstance();
        territories.loadBundledTerritories();
        return resolve(true, queues.activeQueues(), queues.localPlayerUuid(), territories.index(), playerY, queues.serverNow());
    }

    /** Input queues must retain the queue manager's active, server-time-adjusted ordering. */
    static Optional<Waypoint> resolve(
            boolean enabled,
            List<TerritoryQueue> activeQueues,
            String playerUuid,
            GuildTerritoryIndex territories,
            double playerY,
            Instant now) {
        if (!enabled || playerUuid == null || playerUuid.isBlank()) {
            return Optional.empty();
        }
        // Resolve coordinates only after selection: missing data must not redirect to a later war.
        return activeQueues.stream()
                .filter(queue -> playerUuid.equalsIgnoreCase(queue.queuedBy()))
                .findFirst()
                .flatMap(queue -> Optional.ofNullable(territories.territory(queue.territory()))
                        .map(territory -> new Waypoint(
                                "war-queue:" + queue.id(),
                                Kind.WAR_QUEUE,
                                countdown(queue, now),
                                "War for " + queue.territory(),
                                territory.centerX(),
                                playerY,
                                territory.centerZ(),
                                WaypointIcon.of(new ItemStack(Items.IRON_SWORD), null))));
    }

    private static String countdown(TerritoryQueue queue, Instant now) {
        long seconds = Math.max(0L, Duration.between(now, queue.expiresAt()).getSeconds());
        return seconds / 60 + "m" + seconds % 60 + "s";
    }
}
