package com.seqwawa.seq.client;

import com.collarmc.pounce.EventBus;
import com.collarmc.pounce.Preference;
import com.collarmc.pounce.Subscribe;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.UUID;

import com.seqwawa.seq.LightRoomTnaRange.LightRoom;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import com.seqwawa.seq.managers.GuildRewardAutomationManager;
import com.seqwawa.seq.managers.GuildStorageTracker;
import com.seqwawa.seq.managers.GuildWarTrackerHandle;
import com.seqwawa.seq.managers.GuildWarTrackers;
import com.seqwawa.seq.managers.DiscordRankService;
import com.seqwawa.seq.managers.IngredientGuideManager;
import com.seqwawa.seq.managers.LeaderboardBadgeService;
import com.seqwawa.seq.managers.RankProfileRoster;
import com.seqwawa.seq.managers.PartyHealthCache;
import com.seqwawa.seq.managers.PartyFinderManager;
import com.seqwawa.seq.managers.RaidPartySnapshotTracker;
import com.seqwawa.seq.managers.SeqBadgeNametagRendererHandle;
import com.seqwawa.seq.managers.SeqBadgeNametagRenderers;
import com.seqwawa.seq.managers.ThemeManager;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.managers.WynnPartySyncManager;
import com.seqwawa.seq.managers.WorldEventManager;
import com.seqwawa.seq.map.IngredientWaypointRenderer;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.radiance.RadianceCheckerClient;
import com.seqwawa.seq.ui.SequoiaScreen;
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
    public static Setting.BooleanSetting colorDiscordBridgeSetting;

    @Getter
    public static Setting.BooleanSetting showRankGradientsSetting;

    @Getter
    public static Setting.BooleanSetting animateRankGradientsSetting;

    @Getter
    public static Setting.BooleanSetting animateUsernameGradientsSetting;

    @Getter
    public static Setting.IntSetting visibleChatLinesSetting;

    @Getter
    public static Setting.BooleanSetting profileOnShiftClickSetting;

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
    public static DiscordRankService discordRankService;

    @Getter
    public static SeqBadgeNametagRendererHandle seqBadgeNametagRenderer;

    @Getter
    public static WorldEventManager worldEventManager;

    @Getter
    public static IngredientGuideManager ingredientGuideManager;

    private static KeyMapping openScreenKey;
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
        wynnPartySyncManager = new WynnPartySyncManager();
        guildWarTracker = GuildWarTrackers.createIfAvailable();
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
        RadianceCheckerClient.initialize();
        HalcyonRangeVisualiserClient.initialize();
        IngredientWaypointRenderer.initialize();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MinecraftUiRenderer.shutdown());
        LightRoom.init();

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sequoia-mod", "controls"));

        openScreenKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.sequoia-mod.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));
        shareBombsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sequoia-mod.share_bombs", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
        // after minecraft done loading
        MinecraftUiRenderer.initialize();
        SeqClient.gameManager.loadFont();
        SeqClient.assetManager = new AssetManager();

        // Network settings
        autoConnectSetting = new Setting.BooleanSetting("auto_connect", "network", true);
        showDiscordChatSetting = new Setting.BooleanSetting("show_discord_bridge", "chat", true);
        showDiscordRanksSetting = new Setting.BooleanSetting("show_discord_ranks", "chat", true);
        showChatInsigniasSetting = new Setting.BooleanSetting("show_chat_insignias", "chat", false);
        colorDiscordBridgeSetting = new Setting.BooleanSetting("color_discord_bridge", "chat", true);
        showRankGradientsSetting = new Setting.BooleanSetting("show_rank_gradients", "chat", true);
        // Off by default: moving colour draws the eye away from what is being said, and
        // a still gradient is what a Discord role looks like everywhere else.
        animateRankGradientsSetting = new Setting.BooleanSetting("animate_rank_gradients", "chat", false);
        animateUsernameGradientsSetting =
                new Setting.BooleanSetting("animate_username_gradients", "chat", false);
        visibleChatLinesSetting = new Setting.IntSetting("visible_chat_lines", "chat", 12, 5, 20);
        // Off by default: shift-click is vanilla's "insert this name into the chat box"
        // gesture, so taking it over is opt-in rather than a surprise.
        profileOnShiftClickSetting = new Setting.BooleanSetting("profile_on_shift_click", "chat", false);

        showDiscordChatSetting.setPresentation(
                "Show Discord chat", "Display messages forwarded from the Sequoia Discord.", "Discord chat");
        colorDiscordBridgeSetting.setPresentation(
                "Style Discord messages like guild chat",
                "Use rank badges, Discord colors and the guild-chat message style.",
                "Discord chat");
        colorDiscordBridgeSetting.setParentSetting(showDiscordChatSetting);
        colorDiscordBridgeSetting.setEnabledCondition(showDiscordRanksSetting::getValue);

        showDiscordRanksSetting.setPresentation(
                "Show Discord ranks and colors",
                "Replace Wynncraft guild ranks with each member's Sequoia Discord rank.",
                "Discord ranks and colors");
        showRankGradientsSetting.setPresentation(
                "Use Discord gradients",
                "Show complete gradient and holographic role palettes instead of only their primary color.",
                "Discord ranks and colors");
        showRankGradientsSetting.setParentSetting(showDiscordRanksSetting);
        animateRankGradientsSetting.setPresentation(
                "Animate rank badges",
                "Move gradient colors across rank badges while chat is rendered.",
                "Discord ranks and colors");
        animateRankGradientsSetting.setParentSetting(showRankGradientsSetting);
        animateUsernameGradientsSetting.setPresentation(
                "Animate usernames",
                "Move gradient colors across player and Discord bridge names independently of badges.",
                "Discord ranks and colors");
        animateUsernameGradientsSetting.setParentSetting(showRankGradientsSetting);
        showChatInsigniasSetting.setPresentation(
                "Show insignias", "Display a member's Sequoia insignia beside their chat name.", "Discord ranks and colors");
        showChatInsigniasSetting.setParentSetting(showDiscordRanksSetting);

        visibleChatLinesSetting.setPresentation(
                "Visible chat lines", null, "Chat behavior");
        profileOnShiftClickSetting.setPresentation(
                "Open Sequoia profile on shift-click",
                "Opens the Sequoia website player profile.",
                "Chat behavior");
        raidAutoAnnounceSetting = new Setting.BooleanSetting("auto_announce", "raids", true);
        radianceCheckerSetting = new Setting.BooleanSetting("enable_radiance_visualiser", "raids", true);
        radianceMarkerColorSetting = new Setting.ColorSetting("radiance_marker_color", "raids", 0xFF0000);
        radianceMarkerColorSetting.setVisibilityCondition(() -> radianceCheckerSetting.getValue());
        halcyonRangeVisualiserSetting = new Setting.BooleanSetting("enable_halcyon_range_visualiser", "raids", true);
        halcyonRingColorSetting = new Setting.ColorSetting("halcyon_ring_color", "raids", 0x00FFFF);
        halcyonRingColorSetting.setVisibilityCondition(() -> halcyonRangeVisualiserSetting.getValue());
        lightRoomVisualiserSetting = new Setting.BooleanSetting("enable_light_room_visualiser", "raids", true);
        lightRoomRingColorSetting = new Setting.ColorSetting("light_room_ring_color", "raids", 0x00FFFF);
        lightRoomRingColorSetting.setVisibilityCondition(() -> lightRoomVisualiserSetting.getValue());
        trackGuildWarsSetting = new Setting.BooleanSetting("track_guild_wars", "guild_wars", true);
        checkUpdatesSetting = new Setting.BooleanSetting("check_updates", "updates", true);
        trackGuildStorageSetting = new Setting.BooleanSetting("track_guild_storage", "guild_storage", true);
        guildStorageEmeraldNotifyValueSetting =
                new Setting.IntSetting("guild_storage_emerald_threshold_percent", "guild_storage", 100, 0, 100);
        guildStorageAspectNotifyValueSetting =
                new Setting.IntSetting("guild_storage_aspect_threshold_percent", "guild_storage", 100, 0, 100);
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
        getConfigManager().register(autoConnectSetting);
        getConfigManager().register(showDiscordChatSetting);
        getConfigManager().register(colorDiscordBridgeSetting);
        getConfigManager().register(showDiscordRanksSetting);
        getConfigManager().register(showRankGradientsSetting);
        getConfigManager().register(animateRankGradientsSetting);
        getConfigManager().register(animateUsernameGradientsSetting);
        getConfigManager().register(showChatInsigniasSetting);
        getConfigManager().register(visibleChatLinesSetting);
        getConfigManager().register(profileOnShiftClickSetting);
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
        getConfigManager().register(showRaidBadgesSetting);
        getConfigManager().register(showInsigniaBadgesSetting);
        getConfigManager().register(showOwnLeaderboardBadgeSetting);
        getConfigManager().register(showPartyHealthBarsSetting);
        getConfigManager().register(notifyTrackedWorldEventsSetting);
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
