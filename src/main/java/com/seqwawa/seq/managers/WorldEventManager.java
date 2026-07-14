package com.seqwawa.seq.managers;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.WorldEventDefinition;
import com.seqwawa.seq.map.WorldEventNotificationTracker;
import com.seqwawa.seq.map.WorldEventService;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.ui.WorldMapScreen;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class WorldEventManager {
    private static final WorldEventManager INSTANCE = new WorldEventManager(WorldEventService.getInstance());

    private final WorldEventService service;
    private final WorldEventNotificationTracker notificationTracker = new WorldEventNotificationTracker();
    private boolean notificationsActive;
    private long processedSnapshotVersion = -1;

    public static WorldEventManager getInstance() {
        return INSTANCE;
    }

    private WorldEventManager(WorldEventService service) {
        this.service = service;
    }

    public WorldEventService service() {
        return service;
    }

    public void requestMapRefresh() {
        service.requestRefresh();
    }

    public void tick(
            Minecraft client,
            WynncraftServerPolicy.Scope serverScope,
            boolean notificationsEnabled) {
        boolean mapOpen = client.screen instanceof WorldMapScreen;
        boolean shouldNotify = notificationsEnabled && serverScope == WynncraftServerPolicy.Scope.MAIN;
        if (mapOpen || shouldNotify) {
            service.requestRefresh();
        }

        if (shouldNotify != notificationsActive) {
            notificationsActive = shouldNotify;
            notificationTracker.resetToBaseline();
            processedSnapshotVersion = -1;
        }
        if (!shouldNotify) {
            return;
        }

        WorldEventService.Snapshot snapshot = service.snapshot();
        if (snapshot.version() == 0 || snapshot.version() == processedSnapshotVersion) {
            return;
        }
        processedSnapshotVersion = snapshot.version();
        List<WorldEventDefinition> detected = notificationTracker.update(
                snapshot.events(),
                SeqClient.getConfigManager().trackedWorldEventIds());
        for (WorldEventDefinition event : detected) {
            NotificationAccessor.notifyPlayer(detectionMessage(event, Instant.now()));
        }
    }

    static String detectionMessage(WorldEventDefinition event, Instant now) {
        return "World event detected: " + event.name() + " " + scheduleText(event.schedule(), now);
    }

    private static String scheduleText(Instant schedule, Instant now) {
        if (schedule == null || !schedule.isAfter(now)) {
            return "started";
        }
        long seconds = Duration.between(now, schedule).getSeconds();
        long minutes = Math.max(1, (seconds + 59) / 60);
        return "starts in " + minutes + "m";
    }

}
