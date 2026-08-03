package com.seqwawa.seq.client;

import com.collarmc.pounce.EventBus;
import com.collarmc.pounce.Preference;
import com.collarmc.pounce.Subscribe;
import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.events.GameStartEvent;
import com.seqwawa.seq.events.MinecraftFinishedLoading;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.BombShareManager;
import com.seqwawa.seq.managers.ChatManager;
import com.seqwawa.seq.managers.ChatRegexFilterManager;
import com.seqwawa.seq.managers.FontManager;
import com.seqwawa.seq.managers.GameManager;
import com.seqwawa.seq.managers.GuildRewardAutomationManager;
import com.seqwawa.seq.managers.GuildStorageTracker;
import com.seqwawa.seq.managers.GuildWarTrackerHandle;
import com.seqwawa.seq.managers.IngredientGuideManager;
import com.seqwawa.seq.managers.LeaderboardBadgeService;
import com.seqwawa.seq.managers.PartyHealthCache;
import com.seqwawa.seq.managers.PartyFinderManager;
import com.seqwawa.seq.managers.RaidPartySnapshotTracker;
import com.seqwawa.seq.managers.SeqBadgeNametagRendererHandle;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.managers.WynnPartySyncManager;
import com.seqwawa.seq.managers.WorldEventManager;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.radiance.RadianceCheckerClient;
import com.seqwawa.seq.ui.SequoiaScreen;
import com.seqwawa.seq.update.UpdateManager;
import com.seqwawa.seq.utils.WynnClassCache;
import org.slf4j.Logger;

public class SeqClient implements ClientModInitializer {
    private static final long MAIN_SCOPE_RECOVERY_INTERVAL_MS = 60_000L;

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final Minecraft mc = Minecraft.getInstance();

    @Getter
    public static EventBus eventBus;

    @Getter
    public static FontManager fontManager;

    public static GameManager gameManager;
    public static AssetManager assetManager;

    @Getter
    public static ConfigManager configManager;

    @Getter
    public static PartyFinderManager partyFinderManager;

    @Getter
    public static MinecraftAuthService authService;

    public static ChatManager chatManager;

    @Getter
    public static ChatRegexFilterManager chatRegexFilterManager;

    @Getter
    public static BombShareManager bombShareManager;

    @Getter
    public static TreasuryOutManager treasuryOutManager;

    // ── Network config settings ──
    @Getter
    public static Setting.BooleanSetting autoConnectSetting;

    @Getter
    public static Setting.BooleanSetting showDiscordChatSetting;

    @Getter
    public static Setting.BooleanSetting raidAutoAnnounceSetting;

    @Getter
    public static Setting.BooleanSetting trackGuildWarsSetting;

    @Getter
    public static Setting.BooleanSetting checkUpdatesSetting;

    @Getter
    public static Setting.BooleanSetting trackGuildStorageSetting;

    @Getter
    public static Setting.IntSetting guildStorageEmeraldNotifyValueSetting;

    @Getter
    public static Setting.IntSetting guildStorageAspectNotifyValueSetting;

    @Getter
    public static Setting.BooleanSetting easterEggsSetting;

    @Getter
    public static Setting.BooleanSetting startupVideoSetting;

    @Getter
    public static Setting.IntSetting uiSizePercentSetting;

    @Getter
    public static Setting.ChoiceSetting themeSetting;

    @Getter
    public static Setting.BooleanSetting announceOpenPartiesSetting;

    @Getter
    public static Setting.IntSetting announceOpenPartiesIntervalMinutesSetting;

    @Getter
    public static Setting.BooleanSetting syncWynnPartySetting;

    @Getter
    public static Setting.BooleanSetting receiveBombShareRequestsSetting;

    @Getter
    public static Setting.BooleanSetting radianceCheckerSetting;

    @Getter
    public static Setting.ColorSetting radianceMarkerColorSetting;

    @Getter
    public static Setting.BooleanSetting halcyonRangeVisualiserSetting;

    @Getter
    public static Setting.ColorSetting halcyonRingColorSetting;

    @Getter
    public static Setting.BooleanSetting lightRoomVisualiserSetting;

    @Getter
    public static Setting.ColorSetting lightRoomRingColorSetting;

    @Getter
    public static Setting.BooleanSetting showRaidBadgesSetting;

    @Getter
    public static Setting.BooleanSetting showInsigniaBadgesSetting;

    @Getter
    public static Setting.BooleanSetting showOwnLeaderboardBadgeSetting;

    @Getter
    public static Setting.BooleanSetting showPartyHealthBarsSetting;

    @Getter
    public static Setting.BooleanSetting notifyTrackedWorldEventsSetting;

    @Getter
    public static WynnPartySyncManager wynnPartySyncManager;

    @Getter
    public static GuildWarTrackerHandle guildWarTracker;

    @Getter
    public static GuildStorageTracker guildStorageTracker;

    @Getter
    public static GuildRewardAutomationManager guildRewardAutomationManager;

    @Getter
    public static LeaderboardBadgeService leaderboardBadgeService;

    @Getter
    public static SeqBadgeNametagRendererHandle seqBadgeNametagRenderer;

    @Getter
    public static WorldEventManager worldEventManager;

    @Getter
    public static IngredientGuideManager ingredientGuideManager;

    static KeyMapping openScreenKey;
    static KeyMapping shareBombsKey;
    private static WynnClassType lastBroadcastPartyClass;
    private static boolean wasInPartyFinder;
    private static WynncraftServerPolicy.Scope lastServerScope = WynncraftServerPolicy.Scope.BLOCKED;
    private static String lastServerHost;
    private static long lastProductionRecoveryAttemptAtMs;
    private static UUID lastSeenMinecraftProfileId;

    @Override
    public void onInitializeClient() {
        SeqClientBootstrap.initialize(this);
    }

    static void onEndClientTick(Minecraft client) {
        while (openScreenKey.consumeClick()) {
            if (client.screen == null) {
                openMainScreen();
            }
        }
        while (shareBombsKey.consumeClick()) {
            if (bombShareManager != null) {
                bombShareManager.tryHotkeyShareLatestPendingPrompt();
            }
        }

        WynncraftServerPolicy.Scope serverScope = WynncraftServerPolicy.currentScope();
        String currentHost = WynncraftServerPolicy.currentNormalizedHost();
        WynncraftServerPolicy.Scope previousServerScope = lastServerScope;
        logServerScopeChange(serverScope, currentHost);
        if (worldEventManager != null) {
            worldEventManager.tick(
                    client,
                    serverScope,
                    notifyTrackedWorldEventsSetting != null && notifyTrackedWorldEventsSetting.getValue());
        }
        if (serverScope == WynncraftServerPolicy.Scope.BLOCKED) {
            RadianceCheckerClient.reset();
            ConnectionManager.disconnectForBlockedServer();
            wasInPartyFinder = false;
            lastBroadcastPartyClass = null;
            if (wynnPartySyncManager != null) {
                wynnPartySyncManager.reset();
            }
            RaidPartySnapshotTracker.onServerUnavailable();
            if (guildWarTracker != null) {
                guildWarTracker.reset();
            }
            if (guildStorageTracker != null) {
                guildStorageTracker.reset();
            }
            return;
        }
        if (serverScope == WynncraftServerPolicy.Scope.UNKNOWN) {
            RadianceCheckerClient.reset();
            RaidPartySnapshotTracker.onServerUnavailable();
            ConnectionManager.flushPendingOutbound();
            return;
        }

        if (handleMinecraftAccountChange()) {
            return;
        }

        maybeRecoverProductionConnection(serverScope, previousServerScope, currentHost);

        if (partyFinderManager != null) {
            partyFinderManager.tickOpenPartyAnnouncements();
        }
        if (wynnPartySyncManager != null) {
            wynnPartySyncManager.tick();
        }
        if (showPartyHealthBarsSetting == null || showPartyHealthBarsSetting.getValue()) {
            PartyHealthCache.tick();
        }
        RaidPartySnapshotTracker.tick();
        if (guildWarTracker != null) {
            guildWarTracker.tick();
        }
        if (guildStorageTracker != null) {
            guildStorageTracker.tick();
        }
        if (guildRewardAutomationManager != null) {
            guildRewardAutomationManager.tick();
        }
        if (leaderboardBadgeService != null) {
            leaderboardBadgeService.tick();
        }
        if (seqBadgeNametagRenderer != null) {
            seqBadgeNametagRenderer.tick();
        }
        RadianceCheckerClient.tick(client);
        ConnectionManager.flushPendingOutbound();

        boolean inPartyFinder = partyFinderManager != null && partyFinderManager.isInParty();
        if (!inPartyFinder) {
            wasInPartyFinder = false;
            lastBroadcastPartyClass = null;
            return;
        }

        if (!ConnectionManager.isConnected()) {
            wasInPartyFinder = true;
            return;
        }

        WynnClassType currentClass = WynnClassCache.resolveLocalClassType();
        if (currentClass == null) {
            wasInPartyFinder = true;
            return;
        }

        if (!wasInPartyFinder || currentClass != lastBroadcastPartyClass) {
            ConnectionManager.getInstance().sendPartyClassUpdate(currentClass);
            lastBroadcastPartyClass = currentClass;
        }
        wasInPartyFinder = true;
    }

    private static boolean handleMinecraftAccountChange() {
        UUID currentProfileId = currentMinecraftProfileId();
        if (currentProfileId == null) {
            return false;
        }
        if (lastSeenMinecraftProfileId == null) {
            lastSeenMinecraftProfileId = currentProfileId;
            return false;
        }
        if (lastSeenMinecraftProfileId.equals(currentProfileId)) {
            return false;
        }

        String currentUsername = currentMinecraftUsername();
        StoredAuthSession storedSession = configManager == null ? null : configManager.getStoredAuthSession();
        boolean preserveOperatorSession = shouldPreserveOperatorSession(
                currentProfileId, storedSession == null ? null : storedSession.minecraftUuid());
        LOGGER.info(
                "[Seq] Active Minecraft account changed {} -> {} username={} preserveOperatorSession={}",
                lastSeenMinecraftProfileId,
                currentProfileId,
                currentUsername,
                preserveOperatorSession);
        lastSeenMinecraftProfileId = currentProfileId;
        if (!preserveOperatorSession) {
            ConnectionManager.resetForAccountChange();
            if (authService != null) {
                authService.clearSessionIfNotActiveProfile(currentProfileId);
            }
        }
        wasInPartyFinder = false;
        lastBroadcastPartyClass = null;
        if (wynnPartySyncManager != null) {
            wynnPartySyncManager.reset();
        }
        RaidPartySnapshotTracker.reset();
        if (guildWarTracker != null) {
            guildWarTracker.reset();
        }
        if (guildStorageTracker != null) {
            guildStorageTracker.reset();
        }
        return true;
    }

    private static UUID currentMinecraftProfileId() {
        if (mc == null || mc.getUser() == null) {
            return null;
        }
        return mc.getUser().getProfileId();
    }

    private static String currentMinecraftUsername() {
        if (mc == null || mc.getUser() == null) {
            return null;
        }
        return mc.getUser().getName();
    }

    static boolean shouldPreserveOperatorSession(UUID activeMinecraftProfileId, String authenticatedMinecraftUuid) {
        if (authenticatedMinecraftUuid == null || authenticatedMinecraftUuid.isBlank()) {
            return false;
        }
        if (activeMinecraftProfileId == null) {
            return false;
        }
        try {
            return activeMinecraftProfileId.equals(UUID.fromString(authenticatedMinecraftUuid));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void logServerScopeChange(WynncraftServerPolicy.Scope serverScope, String currentHost) {
        if (serverScope == lastServerScope && Objects.equals(currentHost, lastServerHost)) {
            return;
        }
        LOGGER.info(
                "[Seq] Wynncraft scope changed {} -> {} host={} previousHost={}",
                lastServerScope,
                serverScope,
                currentHost,
                lastServerHost);
        lastServerScope = serverScope;
        lastServerHost = currentHost;
    }

    private static void maybeRecoverProductionConnection(
            WynncraftServerPolicy.Scope serverScope,
            WynncraftServerPolicy.Scope previousScope,
            String currentHost) {
        if (serverScope != WynncraftServerPolicy.Scope.MAIN || autoConnectSetting == null || !autoConnectSetting.getValue()) {
            return;
        }

        if (ConnectionManager.isConnected()) {
            lastProductionRecoveryAttemptAtMs = System.currentTimeMillis();
            return;
        }

        if (!ConnectionManager.canAutoConnectNow()) {
            return;
        }

        long now = System.currentTimeMillis();
        AutoConnectTrigger trigger = determineAutoConnectTrigger(
                true,
                serverScope,
                previousScope,
                true,
                now,
                lastProductionRecoveryAttemptAtMs,
                MAIN_SCOPE_RECOVERY_INTERVAL_MS);
        if (trigger == AutoConnectTrigger.NONE) {
            return;
        }

        lastProductionRecoveryAttemptAtMs = now;
        LOGGER.info(
                "[Seq] Triggering production reconnect reason={} host={} manualSuppressed={}",
                trigger.logName,
                currentHost,
                ConnectionManager.isAutoConnectSuppressedByManualDisconnect());
        ConnectionManager.getInstance().connect();
    }

    enum AutoConnectTrigger {
        NONE("none"),
        SCOPE_RECOVERY("scope_recovery"),
        PERIODIC_RECOVERY("periodic_recovery");

        private final String logName;

        AutoConnectTrigger(String logName) {
            this.logName = logName;
        }
    }

    static AutoConnectTrigger determineAutoConnectTrigger(
            boolean autoConnectEnabled,
            WynncraftServerPolicy.Scope currentScope,
            WynncraftServerPolicy.Scope previousScope,
            boolean canAutoConnectNow,
            long nowMs,
            long lastRecoveryAttemptAtMs,
            long recoveryIntervalMs) {
        if (!autoConnectEnabled || currentScope != WynncraftServerPolicy.Scope.MAIN || !canAutoConnectNow) {
            return AutoConnectTrigger.NONE;
        }
        if (previousScope != WynncraftServerPolicy.Scope.MAIN) {
            return AutoConnectTrigger.SCOPE_RECOVERY;
        }
        if (nowMs - lastRecoveryAttemptAtMs >= recoveryIntervalMs) {
            return AutoConnectTrigger.PERIODIC_RECOVERY;
        }
        return AutoConnectTrigger.NONE;
    }

    public static void openMainScreen() {
        mc.execute(() -> mc.setScreen(new SequoiaScreen()));
    }

    public static boolean isBombShareHotkeyDown() {
        return shareBombsKey != null && shareBombsKey.isDown();
    }

    @Subscribe(Preference.CALLER) // to stay in thread
    public void onMinecraftFinishedLoading(MinecraftFinishedLoading ignored) {
        SeqClientBootstrap.finishLoading();
    }

    @Subscribe(Preference.CALLER)
    public void onGameStart(GameStartEvent ignored) {
        if (checkUpdatesSetting == null || checkUpdatesSetting.getValue()) {
            UpdateManager.getInstance().checkForUpdatesOnStartup();
        }
    }

    public static Identifier getFileLocation(String path) {
        return Identifier.fromNamespaceAndPath("seq", path);
    }
}
