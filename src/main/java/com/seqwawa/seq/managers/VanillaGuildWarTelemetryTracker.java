package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.WarStatusUpdate;
import com.seqwawa.seq.model.WarTowerUpdate;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.ConnectionManager;
import java.time.Duration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Live guild-war telemetry implementation that uses only vanilla Minecraft
 * state and packet observations.
 *
 * <p>Legacy completed-war summaries, death handling, and queue submissions are
 * intentionally unavailable here because those require Wynntils lifecycle
 * models.
 */
final class VanillaGuildWarTelemetryTracker implements GuildWarTrackerHandle {
    private final MinecraftCharacterClassDetector classDetector;
    private final MinecraftWarTowerTracker towerTracker;
    private final LiveWarTelemetryTracker liveTelemetryTracker;
    private boolean runtimeIdentityKnown;
    private int connectionIdentity;
    private int levelIdentity;
    private int playerIdentity;

    VanillaGuildWarTelemetryTracker() {
        this.classDetector = MinecraftCharacterClassDetector.getInstance();
        this.towerTracker = MinecraftWarTowerTracker.getInstance();
        this.liveTelemetryTracker = new LiveWarTelemetryTracker(
                new RuntimePlayerContext(classDetector),
                new RuntimePublisher(),
                VanillaGuildWarTelemetryTracker::trackingEnabled,
                System::currentTimeMillis);
    }

    VanillaGuildWarTelemetryTracker(
            MinecraftCharacterClassDetector classDetector,
            MinecraftWarTowerTracker towerTracker,
            LiveWarTelemetryTracker liveTelemetryTracker) {
        this.classDetector = classDetector;
        this.towerTracker = towerTracker;
        this.liveTelemetryTracker = liveTelemetryTracker;
    }

    @Override
    public void tick() {
        if (!observeRuntimeIdentity()) {
            return;
        }
        tickCurrentState();
    }

    void tickCurrentState() {
        if (trackingEnabled()) {
            classDetector.tick();
        }
        MinecraftWarTowerTracker.TowerSnapshot snapshot = towerTracker.latestSnapshot();
        LiveWarTelemetryTracker.WarObservation observation = snapshot == null
                ? null
                : new LiveWarTelemetryTracker.WarObservation(
                        snapshot.update().territory(), snapshot.bossBarId().toString(), snapshot.update());
        liveTelemetryTracker.tick(observation);
    }

    @Override
    public void onSystemChat(Component message) {
        // Vanilla live telemetry has no completed-war chat submission path.
    }

    @Override
    public void reset() {
        clearTrackedState();
        runtimeIdentityKnown = false;
        connectionIdentity = 0;
        levelIdentity = 0;
        playerIdentity = 0;
    }

    private void clearTrackedState() {
        classDetector.reset();
        towerTracker.reset();
        liveTelemetryTracker.reset();
    }

    @Override
    public void onSlotClick(String screenName, ItemStack item) {
        classDetector.observeCharacterSelection(screenName, item);
    }

    private boolean observeRuntimeIdentity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null
                || minecraft.getConnection() == null
                || minecraft.level == null
                || minecraft.player == null) {
            if (runtimeIdentityKnown) {
                clearTrackedState();
                runtimeIdentityKnown = false;
            }
            return false;
        }

        observeRuntimeIdentity(
                System.identityHashCode(minecraft.getConnection()),
                System.identityHashCode(minecraft.level),
                System.identityHashCode(minecraft.player));
        return true;
    }

    void observeRuntimeIdentity(
            int currentConnectionIdentity, int currentLevelIdentity, int currentPlayerIdentity) {
        if (runtimeIdentityKnown
                && (currentConnectionIdentity != connectionIdentity
                        || currentLevelIdentity != levelIdentity
                        || currentPlayerIdentity != playerIdentity)) {
            clearTrackedState();
        }
        connectionIdentity = currentConnectionIdentity;
        levelIdentity = currentLevelIdentity;
        playerIdentity = currentPlayerIdentity;
        runtimeIdentityKnown = true;
    }

    private static boolean trackingEnabled() {
        return SeqClient.getTrackGuildWarsSetting() == null
                || SeqClient.getTrackGuildWarsSetting().getValue();
    }

    private record RuntimePlayerContext(MinecraftCharacterClassDetector classDetector)
            implements LiveWarTelemetryTracker.PlayerContext {
        @Override
        public boolean warModeActive() {
            WarPlannerManager manager = SeqClient.getWarPlannerManager();
            if (manager == null) {
                return false;
            }
            Duration remaining = manager.ownAvailabilityRemaining();
            return remaining != null && !remaining.isZero() && !remaining.isNegative();
        }

        @Override
        public WynnClassType localClassType() {
            return classDetector.currentClass();
        }

        @Override
        public LiveWarTelemetryTracker.WorldPosition worldPosition() {
            Player player = Minecraft.getInstance().player;
            return player == null
                    ? null
                    : new LiveWarTelemetryTracker.WorldPosition(player.getBlockX(), player.getBlockZ());
        }
    }

    private static final class RuntimePublisher implements LiveWarTelemetryTracker.Publisher {
        @Override
        public boolean ready() {
            return ConnectionManager.isLiveWarTelemetryReady();
        }

        @Override
        public boolean publishWarStatus(WarStatusUpdate update) {
            return ConnectionManager.getInstance().sendWarStatus(update);
        }

        @Override
        public boolean publishWarTowerUpdate(WarTowerUpdate update) {
            return ConnectionManager.getInstance().sendWarTowerUpdate(update);
        }
    }
}
