package com.seqwawa.seq.client;

import com.collarmc.pounce.EventBus;
import com.collarmc.pounce.Preference;
import com.collarmc.pounce.Subscribe;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.seqwawa.seq.LightRoomTnaRange.LightRoom;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.command.SeqCommand;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.events.GameStartEvent;
import com.seqwawa.seq.events.MinecraftFinishedLoading;
import com.seqwawa.seq.halcyon.HalcyonRangeVisualiserClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.BombShareManager;
import com.seqwawa.seq.managers.ChatManager;
import com.seqwawa.seq.managers.ChatRegexFilterManager;
import com.seqwawa.seq.managers.FontManager;
import com.seqwawa.seq.managers.GameManager;
import com.seqwawa.seq.managers.GuildRaidProgressService;
import com.seqwawa.seq.managers.GuildRewardAutomationManager;
import com.seqwawa.seq.managers.GuildStorageTracker;
import com.seqwawa.seq.managers.GuildWarTrackerHandle;
import com.seqwawa.seq.managers.GuildWarTrackers;
import com.seqwawa.seq.managers.DiscordRankService;
import com.seqwawa.seq.managers.IngredientGuideManager;
import com.seqwawa.seq.managers.LeaderboardBadgeService;
import com.seqwawa.seq.managers.MinecraftWarTowerTracker;
import com.seqwawa.seq.managers.RankProfileRoster;
import com.seqwawa.seq.managers.PartyHealthCache;
import com.seqwawa.seq.managers.PartyFinderManager;
import com.seqwawa.seq.managers.PrincessMode;
import com.seqwawa.seq.managers.PrincessRaidStatsManager;
import com.seqwawa.seq.managers.RaidPartySnapshotTracker;
import com.seqwawa.seq.managers.SeqBadgeNametagRendererHandle;
import com.seqwawa.seq.managers.SeqBadgeNametagRenderers;
import com.seqwawa.seq.managers.ThemeManager;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.managers.WynnPartySyncManager;
import com.seqwawa.seq.managers.WorldEventManager;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.managers.WarTerritoryQueueManager;
import com.seqwawa.seq.map.IngredientWaypointRenderer;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.radiance.RadianceCheckerClient;
import com.seqwawa.seq.raids.tna.TnaLineupHelper;
import com.seqwawa.seq.scroll.CraftedScrollRangeVisualiserClient;
import com.seqwawa.seq.ui.IngredientGuideScreen;
import com.seqwawa.seq.ui.PartyFinderScreen;
import com.seqwawa.seq.ui.PrincessRaidCelebration;
import com.seqwawa.seq.ui.SequoiaScreen;
import com.seqwawa.seq.ui.SettingsScreen;
import com.seqwawa.seq.ui.WorldMapScreen;
import com.seqwawa.seq.ui.WarPlannerScreen;
import com.seqwawa.seq.update.UpdateManager;
import com.seqwawa.seq.utils.WynnClassCache;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
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
    public static WarPlannerManager warPlannerManager;

    @Getter
    public static WarTerritoryQueueManager warTerritoryQueueManager;

    @Getter
    public static PrincessRaidStatsManager princessRaidStatsManager;

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
    public static Setting.BooleanSetting showDiscordRanksSetting;

    @Getter
    public static Setting.BooleanSetting showChatInsigniasSetting;

    @Getter
    public static Setting.BooleanSetting usePerUserColorsSetting;

    @Getter
    public static Setting.BooleanSetting colorDiscordBridgeSetting;

    @Getter
    public static Setting.ColorSetting discordChatTextColorSetting;

    @Getter
    public static Setting.ColorSetting inGameGuildChatTextColorSetting;

    @Getter
    public static Setting.BooleanSetting showRankPillGradientsSetting;

    @Getter
    public static Setting.BooleanSetting showUsernameGradientsSetting;

    @Getter
    public static Setting.BooleanSetting colorRankPillsSetting;

    @Getter
    public static Setting.BooleanSetting colorUsernamesSetting;

    @Getter
    public static Setting.BooleanSetting colorPartyChatSetting;

    @Getter
    public static Setting.BooleanSetting animateRankGradientsSetting;

    @Getter
    public static Setting.BooleanSetting animateUsernameGradientsSetting;

    @Getter
    public static Setting.BooleanSetting profileOnShiftClickSetting;

    @Getter
    public static Setting.BooleanSetting linkWorldNamesSetting;

    @Getter
    public static Setting.BooleanSetting worldLinkRunsSwitchSetting;

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
    public static Setting.BooleanSetting tnaRoomThreeHelperSetting;

    @Getter
    public static Setting.BooleanSetting tnaBerryLineupSetting;

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
    public static Setting.BooleanSetting warPlannerResourceColorsSetting;

    @Getter
    public static Setting.IntSetting warPlannerBackgroundOpacitySetting;

    @Getter
    public static Setting.IntSetting warQueueHudTextSizeSetting;

    @Getter
    public static Setting.BooleanSetting warQueueHudOnlyOwnedOrJoinedSetting;

    @Getter
    public static Setting.BooleanSetting warQueueMissMessagesSetting;

    @Getter
    public static Setting.IntSetting warQueueHudMaxRowsSetting;

    @Getter
    public static Setting.BooleanSetting warPlannerLockTerritoriesSetting;

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
    public static DiscordRankService discordRankService;

    @Getter
    public static SeqBadgeNametagRendererHandle seqBadgeNametagRenderer;

    @Getter
    public static WorldEventManager worldEventManager;

    @Getter
    public static IngredientGuideManager ingredientGuideManager;

    private static KeyMapping openScreenKey;
    private static KeyMapping openPartyFinderKey;
    private static KeyMapping openWorldMapKey;
    private static KeyMapping openIngredientGuideKey;
    private static KeyMapping shareBombsKey;
    private static WynnClassType lastBroadcastPartyClass;
    private static boolean wasInPartyFinder;
    private static WynncraftServerPolicy.Scope lastServerScope = WynncraftServerPolicy.Scope.BLOCKED;
    private static String lastServerHost;
    private static long lastProductionRecoveryAttemptAtMs;
    private static UUID lastSeenMinecraftProfileId;

    @Override
    public void onInitializeClient() {
        try {
            eventBus = new EventBus(mc::execute);
            eventBus.subscribe(this);
        } catch (Exception e) {
            LOGGER.warn("Event bus failed to initialize.");
        }
        fontManager = new FontManager();
        gameManager = new GameManager();
        partyFinderManager = new PartyFinderManager();
        warPlannerManager = new WarPlannerManager();
        warTerritoryQueueManager = new WarTerritoryQueueManager();
        princessRaidStatsManager = new PrincessRaidStatsManager();
        wynnPartySyncManager = new WynnPartySyncManager();
        guildWarTracker = GuildWarTrackers.create();
        guildStorageTracker = GuildStorageTracker.getInstance();
        guildRewardAutomationManager = new GuildRewardAutomationManager();
        chatManager = new ChatManager();
        bombShareManager = new BombShareManager();
        treasuryOutManager = new TreasuryOutManager();
        ConnectionManager.onTreasuryOutRecorded(treasuryOutManager::handleRecorded);
        ConnectionManager.onTreasuryOutError(treasuryOutManager::handleError);
        configManager = new ConfigManager();
        chatRegexFilterManager = new ChatRegexFilterManager();
        chatRegexFilterManager.settings().forEach(configManager::register);
        chatRegexFilterManager.registerIncomingHooks();
        configManager.load();
        configManager.migrateToken();
        ThemeManager.initialize();
        leaderboardBadgeService = LeaderboardBadgeService.getInstance();
        discordRankService = DiscordRankService.getInstance();
        seqBadgeNametagRenderer = SeqBadgeNametagRenderers.createIfAvailable();
        worldEventManager = WorldEventManager.getInstance();
        ingredientGuideManager = IngredientGuideManager.getInstance();
        authService = MinecraftAuthService.getInstance();
        SeqCommand.register();
        PrincessRaidCelebration.initialize();
        RadianceCheckerClient.initialize();
        HalcyonRangeVisualiserClient.initialize();
        CraftedScrollRangeVisualiserClient.initialize();
        IngredientWaypointRenderer.initialize();
        TnaLineupHelper.initialize();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MinecraftUiRenderer.shutdown());
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> resetWarTrackingState());
        LightRoom.init();

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sequoia-mod", "controls"));

        openScreenKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.sequoia-mod.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));
        openPartyFinderKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sequoia-mod.open_party_finder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        openWorldMapKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sequoia-mod.open_world_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        openIngredientGuideKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sequoia-mod.open_ingredient_guide",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));
        shareBombsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sequoia-mod.share_bombs", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.consumeClick()) {
                if (client.screen == null) {
                    openMainScreen();
                }
            }
            while (openPartyFinderKey.consumeClick()) {
                if (client.screen == null) {
                    openPartyFinderScreen();
                }
            }
            while (openWorldMapKey.consumeClick()) {
                if (client.screen == null) {
                    openWorldMapScreen();
                }
            }
            while (openIngredientGuideKey.consumeClick()) {
                if (client.screen == null) {
                    openIngredientGuideScreen();
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
            boolean minecraftAccountChanged = handleMinecraftAccountChange();
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
                resetWarTrackingState();
                if (guildStorageTracker != null) {
                    guildStorageTracker.reset();
                }
                resetWarPlanningState();
                GuildRaidProgressService.getInstance().tick(false);
                return;
            }
            if (serverScope == WynncraftServerPolicy.Scope.UNKNOWN) {
                RadianceCheckerClient.reset();
                RaidPartySnapshotTracker.onServerUnavailable();
                ConnectionManager.flushPendingOutbound();
                resetWarTrackingState();
                resetWarPlanningState();
                GuildRaidProgressService.getInstance().tick(false);
                return;
            }

            GuildRaidProgressService.getInstance().tick();
            if (minecraftAccountChanged) {
                return;
            }

            maybeRecoverProductionConnection(serverScope, previousServerScope, currentHost);

            if (warPlannerManager != null) {
                warPlannerManager.tick();
            }
            if (warTerritoryQueueManager != null) {
                warTerritoryQueueManager.tick();
            }

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
            // One poll feeds both the badge and the rank indexes.
            RankProfileRoster.getInstance().tick();
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
        });
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
                authService.clearSession();
            }
        }
        wasInPartyFinder = false;
        lastBroadcastPartyClass = null;
        if (wynnPartySyncManager != null) {
            wynnPartySyncManager.reset();
        }
        RaidPartySnapshotTracker.reset();
        GuildRaidProgressService.getInstance().reset();
        resetWarTrackingState();
        if (guildStorageTracker != null) {
            guildStorageTracker.reset();
        }
        resetWarPlanningState();
        if (princessRaidStatsManager != null) {
            princessRaidStatsManager.reset();
        }
        return true;
    }

    private static void resetWarTrackingState() {
        if (guildWarTracker != null) {
            guildWarTracker.reset();
        } else {
            MinecraftWarTowerTracker.getInstance().reset();
        }
    }

    private static void resetWarPlanningState() {
        if (warPlannerManager != null) {
            warPlannerManager.reset();
        }
        if (warTerritoryQueueManager != null) {
            warTerritoryQueueManager.reset();
        }
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

    public static void openPartyFinderScreen() {
        mc.execute(() -> mc.setScreen(new PartyFinderScreen(mc.screen)));
    }

    public static void openSettingsScreen() {
        mc.execute(() -> mc.setScreen(new SettingsScreen(mc.screen)));
    }

    public static void openWarPlannerScreen() {
        WarPlannerManager manager = getWarPlannerManager();
        if (manager == null || !manager.isAuthorized()) {
            return;
        }
        mc.execute(() -> {
            WarPlannerScreen screen = new WarPlannerScreen(mc.screen);
            mc.setScreen(screen);
            screen.refreshPlanner();
        });
    }

    public static void openWorldMapScreen() {
        mc.execute(() -> mc.setScreen(new WorldMapScreen(mc.screen)));
    }

    public static void openIngredientGuideScreen() {
        mc.execute(() -> mc.setScreen(new IngredientGuideScreen(mc.screen)));
    }

    public static boolean isBombShareHotkeyDown() {
        return shareBombsKey != null && shareBombsKey.isDown();
    }

    @Subscribe(Preference.CALLER) // to stay in thread
    public void onMinecraftFinishedLoading(MinecraftFinishedLoading ignored) {
        // after minecraft done loading
        MinecraftUiRenderer.initialize();
        SeqClient.gameManager.loadFont();
        SeqClient.assetManager = new AssetManager();

        // Network settings
        autoConnectSetting = new Setting.BooleanSetting("auto_connect", "network", true);
        showDiscordChatSetting = new Setting.BooleanSetting("show_discord_bridge", "chat", true);
        showDiscordRanksSetting = new Setting.BooleanSetting("show_discord_ranks", "chat", true);
        showChatInsigniasSetting = new Setting.BooleanSetting("show_chat_insignias", "chat", false);
        usePerUserColorsSetting = new Setting.BooleanSetting("use_per_user_colors", "chat", true);
        colorDiscordBridgeSetting = new Setting.BooleanSetting("color_discord_bridge", "chat", true);
        discordChatTextColorSetting = new Setting.ColorSetting("discord_chat_text_color", "chat", 0x55FFFF)
                .withValueOverride(PrincessMode::paletteColorOverride);
        inGameGuildChatTextColorSetting =
                new Setting.ColorSetting("in_game_guild_chat_text_color", "chat", 0x55FFFF)
                        .withValueOverride(PrincessMode::paletteColorOverride);
        colorRankPillsSetting = new Setting.BooleanSetting("color_rank_pills", "chat", true);
        colorUsernamesSetting = new Setting.BooleanSetting("color_usernames", "chat", true);
        colorPartyChatSetting = new Setting.BooleanSetting("color_party_chat", "chat", true);
        showRankPillGradientsSetting = new Setting.BooleanSetting("show_rank_pill_gradients", "chat", true);
        showUsernameGradientsSetting = new Setting.BooleanSetting("show_username_gradients", "chat", true);
        // Off by default: moving colour draws the eye away from what is being said, and
        // a still gradient is what a Discord role looks like everywhere else.
        animateRankGradientsSetting = new Setting.BooleanSetting("animate_rank_gradients", "chat", false);
        animateUsernameGradientsSetting =
                new Setting.BooleanSetting("animate_username_gradients", "chat", false);
        // Off by default: shift-click is vanilla's "insert this name into the chat box"
        // gesture, so taking it over is opt-in rather than a surprise.
        profileOnShiftClickSetting = new Setting.BooleanSetting("profile_on_shift_click", "chat", false);
        linkWorldNamesSetting = new Setting.BooleanSetting("link_world_names", "chat", true);
        // Switching on the click itself is the point: a fresh profession world fills
        // while you retype the command. The link has to be clicked deliberately, and
        // anyone who would rather read the command first can turn this off.
        worldLinkRunsSwitchSetting = new Setting.BooleanSetting("world_link_runs_switch", "chat", true);

        showDiscordChatSetting.setPresentation(
                "Show Discord chat", "Display messages forwarded from the Sequoia Discord.", "Discord chat");
        colorDiscordBridgeSetting.setPresentation(
                "Style Discord messages like guild chat",
                "Use rank badges, Discord colors and the guild-chat message style.",
                "Discord chat");
        colorDiscordBridgeSetting.setParentSetting(showDiscordChatSetting);
        colorDiscordBridgeSetting.setEnabledCondition(showDiscordRanksSetting::getValue);
        discordChatTextColorSetting.setPresentation(
                "Discord message text color",
                "Choose the text color used for messages forwarded from Discord.",
                "Chat colors");
        discordChatTextColorSetting.setParentSetting(showDiscordChatSetting);
        inGameGuildChatTextColorSetting.setPresentation(
                "In-game guild message text color",
                "Choose the text color used for native Wynncraft guild messages.",
                "Chat colors");

        showDiscordRanksSetting.setPresentation(
                "Show Discord ranks and colors",
                "Show Sequoia Discord ranks in guild chat and member colors in supported chat channels.",
                "Discord ranks");
        showChatInsigniasSetting.setPresentation(
                "Show insignias", "Display a member's Sequoia insignia beside their chat name.", "Discord ranks");
        showChatInsigniasSetting.setParentSetting(showDiscordRanksSetting);
        usePerUserColorsSetting.setPresentation(
                "Use per-user colors",
                "Prefer each member's individual Discord palette over their progression rank's colors.",
                "Discord ranks");
        usePerUserColorsSetting.setParentSetting(showDiscordRanksSetting);
        colorRankPillsSetting.setPresentation(
                "Color rank pills",
                "Use each member's Discord role color on their rank pill.",
                "Rank pills");
        colorRankPillsSetting.setParentSetting(showDiscordRanksSetting);
        showRankPillGradientsSetting.setPresentation(
                "Use gradients",
                "Show the complete gradient or holographic role palette on rank pills.",
                "Rank pills");
        showRankPillGradientsSetting.setParentSetting(colorRankPillsSetting);
        animateRankGradientsSetting.setPresentation(
                "Animate gradients",
                "Move gradient colors across rank pills while chat is rendered.",
                "Rank pills");
        animateRankGradientsSetting.setParentSetting(showRankPillGradientsSetting);
        colorUsernamesSetting.setPresentation(
                "Color chat usernames",
                "Use each member's Discord role color on guild, party and Discord bridge names.",
                "Usernames");
        colorUsernamesSetting.setParentSetting(showDiscordRanksSetting);
        colorPartyChatSetting.setPresentation(
                "Color party chat",
                "Apply Sequoia member colors to player names in Wynncraft party chat.",
                "Usernames");
        colorPartyChatSetting.setParentSetting(colorUsernamesSetting);
        showUsernameGradientsSetting.setPresentation(
                "Use gradients",
                "Show the complete gradient or holographic role palette on usernames.",
                "Usernames");
        showUsernameGradientsSetting.setParentSetting(colorUsernamesSetting);
        animateUsernameGradientsSetting.setPresentation(
                "Animate gradients",
                "Move gradient colors across guild, party and Discord bridge names while chat is rendered.",
                "Usernames");
        animateUsernameGradientsSetting.setParentSetting(showUsernameGradientsSetting);

        profileOnShiftClickSetting.setPresentation(
                "Open Sequoia profile on shift-click",
                "Opens the Sequoia website player profile.",
                "Chat behavior");
        linkWorldNamesSetting.setPresentation(
                "Link world names in chat",
                "Turn a world named in chat, such as NA6, into a link that switches you to it.",
                "Chat behavior");
        worldLinkRunsSwitchSetting.setPresentation(
                "Switch on click",
                "Click a world name to switch straight away. When off, the /switch command is typed"
                        + " into your chat box so you can send it yourself.",
                "Chat behavior");
        worldLinkRunsSwitchSetting.setParentSetting(linkWorldNamesSetting);
        raidAutoAnnounceSetting = new Setting.BooleanSetting("auto_announce", "raids", true);
        radianceCheckerSetting = new Setting.BooleanSetting("enable_radiance_visualiser", "raids", true);
        radianceMarkerColorSetting = new Setting.ColorSetting("radiance_marker_color", "raids", 0xFF0000)
                .withValueOverride(PrincessMode::paletteColorOverride);
        radianceMarkerColorSetting.setParentSetting(radianceCheckerSetting);
        halcyonRangeVisualiserSetting = new Setting.BooleanSetting("enable_halcyon_range_visualiser", "raids", true);
        halcyonRingColorSetting = new Setting.ColorSetting("halcyon_ring_color", "raids", 0x00FFFF)
                .withValueOverride(PrincessMode::paletteColorOverride);
        halcyonRingColorSetting.setParentSetting(halcyonRangeVisualiserSetting);
        lightRoomVisualiserSetting = new Setting.BooleanSetting("enable_light_room_visualiser", "raids", true);
        lightRoomRingColorSetting = new Setting.ColorSetting("light_room_ring_color", "raids", 0x00FFFF)
                .withValueOverride(PrincessMode::paletteColorOverride);
        lightRoomRingColorSetting.setParentSetting(lightRoomVisualiserSetting);
        tnaRoomThreeHelperSetting = new Setting.BooleanSetting("enable_tna_room_3_helper", "raids", true);
        tnaRoomThreeHelperSetting.setPresentation(
                "VM lineup", null, "Raid helpers");
        tnaBerryLineupSetting = new Setting.BooleanSetting("enable_tna_berry_lineup", "raids", true);
        tnaBerryLineupSetting.setPresentation(
                "Berry lineup", null, "Raid helpers");
        trackGuildWarsSetting = new Setting.BooleanSetting("track_guild_wars", "guild_wars", true);
        checkUpdatesSetting = new Setting.BooleanSetting("check_updates", "updates", true);
        trackGuildStorageSetting = new Setting.BooleanSetting("track_guild_storage", "guild_storage", true);
        guildStorageEmeraldNotifyValueSetting =
                new Setting.IntSetting("guild_storage_emerald_threshold_percent", "guild_storage", 100, 0, 100);
        guildStorageAspectNotifyValueSetting =
                new Setting.IntSetting("guild_storage_aspect_threshold_percent", "guild_storage", 100, 0, 100);
        guildStorageEmeraldNotifyValueSetting.setParentSetting(trackGuildStorageSetting);
        guildStorageAspectNotifyValueSetting.setParentSetting(trackGuildStorageSetting);
        easterEggsSetting = new Setting.BooleanSetting("enable_easter_eggs", "ui", true);
        startupVideoSetting = new Setting.BooleanSetting("startup_video", "ui", false);
        uiSizePercentSetting = new Setting.IntSetting("ui_size_percent", "ui", 100, 75, 150, 5)
                .allowOutOfRangeManualInput();
        themeSetting = new Setting.ChoiceSetting(
                "theme",
                "ui",
                ThemeManager.currentTheme().name(),
                ThemeManager.loadedThemeNames(),
                ThemeManager::setCurrentTheme);
        announceOpenPartiesSetting = new Setting.BooleanSetting("announce_open_parties", "party_finder", true);
        announceOpenPartiesIntervalMinutesSetting =
                new Setting.IntSetting("announce_open_parties_interval_minutes", "party_finder", 5, 1, 60);
        announceOpenPartiesIntervalMinutesSetting.setParentSetting(announceOpenPartiesSetting);
        syncWynnPartySetting = new Setting.BooleanSetting("sync_with_wynn_party", "party_finder", true);
        receiveBombShareRequestsSetting = new Setting.BooleanSetting("receive_bomb_share_requests", "network", true);
        showRaidBadgesSetting =
                new Setting.BooleanSetting("show_raid_badges", "leaderboard_badges", true);
        showInsigniaBadgesSetting =
                new Setting.BooleanSetting("show_insignia_badges", "leaderboard_badges", true);
        showOwnLeaderboardBadgeSetting =
                new Setting.BooleanSetting("show_own_leaderboard_badge", "leaderboard_badges", true);
        showPartyHealthBarsSetting = new Setting.BooleanSetting("show_party_healthbars", "raids", true);
        notifyTrackedWorldEventsSetting =
                new Setting.BooleanSetting("notify_tracked_world_events", "world_events", false);
        warPlannerResourceColorsSetting =
                new Setting.BooleanSetting("resource_colors", "war_planner", false);
        warPlannerBackgroundOpacitySetting =
                new Setting.IntSetting("background_opacity_percent", "war_planner", 100, 0, 100, 5);
        warQueueHudTextSizeSetting =
                new Setting.IntSetting("queue_hud_text_size", "war_planner", 9, 6, 18);
        warQueueHudOnlyOwnedOrJoinedSetting =
                new Setting.BooleanSetting("queue_hud_only_owned_or_joined", "war_planner", false);
        warQueueMissMessagesSetting =
                new Setting.BooleanSetting("queue_miss_messages", "war_planner", false);
        warQueueHudMaxRowsSetting =
                new Setting.IntSetting("queue_hud_max_rows", "war_planner", 6, 1, 20);
        warPlannerLockTerritoriesSetting =
                new Setting.BooleanSetting("lock_territories", "war_planner", false);
        List.of(
                        warPlannerResourceColorsSetting,
                        warPlannerBackgroundOpacitySetting,
                        warQueueHudTextSizeSetting,
                        warQueueHudOnlyOwnedOrJoinedSetting,
                        warQueueMissMessagesSetting,
                        warQueueHudMaxRowsSetting,
                        warPlannerLockTerritoriesSetting)
                .forEach(setting -> setting.setPresentationCategory("guild_wars"));
        warPlannerResourceColorsSetting.setPresentation(
                "Color by resource type",
                "Fill map territories using their resource production colors.",
                "War planner display");
        warPlannerBackgroundOpacitySetting.setPresentation(
                "Background opacity",
                "Adjust war-planner panels so the in-game chat remains visible behind them.",
                "War planner display");
        warQueueHudTextSizeSetting.setPresentation(
                "Queue HUD text size",
                "Adjust the territory queue text shown at the top right of the game HUD.",
                "War queue HUD");
        warQueueHudOnlyOwnedOrJoinedSetting.setPresentation(
                "Only show my queues",
                "Only show territories you queued or joined on the war map and top-right queue HUD.",
                "War queue HUD");
        warQueueMissMessagesSetting.setPresentation(
                "Queue miss messages",
                "Show a blame message when nobody enters a queued territory war.",
                "War queue messages");
        warQueueHudMaxRowsSetting.setPresentation(
                "Maximum queue rows",
                "Set how many territory queues can appear in the top-right HUD.",
                "War queue HUD");
        warPlannerLockTerritoriesSetting.setPresentation(
                "Lock territories",
                "Manager-only view that hides territories not assigned to a zone.",
                "War planner display");
        warPlannerLockTerritoriesSetting.setVisibilityCondition(
                () -> warPlannerManager != null && warPlannerManager.canManage());
        getConfigManager().register(autoConnectSetting);
        getConfigManager().register(showDiscordChatSetting);
        getConfigManager().register(colorDiscordBridgeSetting);
        getConfigManager().register(discordChatTextColorSetting);
        getConfigManager().register(inGameGuildChatTextColorSetting);
        getConfigManager().register(showDiscordRanksSetting);
        getConfigManager().register(showChatInsigniasSetting);
        getConfigManager().register(usePerUserColorsSetting);
        getConfigManager().register(colorRankPillsSetting);
        getConfigManager().registerWithLegacyKeys(showRankPillGradientsSetting, "chat.show_rank_gradients");
        getConfigManager().register(animateRankGradientsSetting);
        getConfigManager().register(colorUsernamesSetting);
        getConfigManager().register(colorPartyChatSetting);
        getConfigManager().registerWithLegacyKeys(showUsernameGradientsSetting, "chat.show_rank_gradients");
        getConfigManager().register(animateUsernameGradientsSetting);
        getConfigManager().register(profileOnShiftClickSetting);
        getConfigManager().register(linkWorldNamesSetting);
        getConfigManager().register(worldLinkRunsSwitchSetting);
        getConfigManager().register(raidAutoAnnounceSetting);
        getConfigManager().register(trackGuildWarsSetting);
        getConfigManager().register(checkUpdatesSetting);
        getConfigManager().register(trackGuildStorageSetting);
        getConfigManager().register(guildStorageEmeraldNotifyValueSetting);
        getConfigManager().register(guildStorageAspectNotifyValueSetting);
        getConfigManager().register(easterEggsSetting);
        getConfigManager().register(startupVideoSetting);
        getConfigManager().register(uiSizePercentSetting);
        getConfigManager().register(themeSetting);
        getConfigManager().register(announceOpenPartiesSetting);
        getConfigManager().register(announceOpenPartiesIntervalMinutesSetting);
        getConfigManager().register(syncWynnPartySetting);
        getConfigManager().register(receiveBombShareRequestsSetting);
        getConfigManager().register(radianceCheckerSetting);
        getConfigManager().register(radianceMarkerColorSetting);
        getConfigManager().register(halcyonRangeVisualiserSetting);
        getConfigManager().register(halcyonRingColorSetting);
        getConfigManager().register(lightRoomVisualiserSetting);
        getConfigManager().register(lightRoomRingColorSetting);
        getConfigManager().register(tnaRoomThreeHelperSetting);
        getConfigManager().register(tnaBerryLineupSetting);
        getConfigManager().register(showRaidBadgesSetting);
        getConfigManager().register(showInsigniaBadgesSetting);
        getConfigManager().register(showOwnLeaderboardBadgeSetting);
        getConfigManager().register(showPartyHealthBarsSetting);
        getConfigManager().register(notifyTrackedWorldEventsSetting);
        getConfigManager().register(warPlannerResourceColorsSetting);
        getConfigManager().register(warPlannerBackgroundOpacitySetting);
        getConfigManager().register(warPlannerLockTerritoriesSetting);
        getConfigManager().register(warQueueHudOnlyOwnedOrJoinedSetting);
        getConfigManager().register(warQueueHudMaxRowsSetting);
        getConfigManager().register(warQueueHudTextSizeSetting);
        getConfigManager().register(warQueueMissMessagesSetting);
        getConfigManager().load(); // reload to pick up saved values for new settings

        // Auto-connect if enabled. The auth service will refresh or mint a backend token as needed.
        if (autoConnectSetting.getValue() && WynncraftServerPolicy.currentScope() == WynncraftServerPolicy.Scope.MAIN) {
            ConnectionManager.getInstance().connect();
        }
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
