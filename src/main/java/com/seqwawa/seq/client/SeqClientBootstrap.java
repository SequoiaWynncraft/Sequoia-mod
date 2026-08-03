package com.seqwawa.seq.client;

import com.collarmc.pounce.EventBus;
import com.mojang.blaze3d.platform.InputConstants;
import com.seqwawa.seq.LightRoomTnaRange.LightRoom;
import com.seqwawa.seq.command.SeqCommand;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.halcyon.HalcyonRangeVisualiserClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.BombShareManager;
import com.seqwawa.seq.managers.ChatManager;
import com.seqwawa.seq.managers.ChatRegexFilterManager;
import com.seqwawa.seq.managers.FontManager;
import com.seqwawa.seq.managers.GameManager;
import com.seqwawa.seq.managers.GuildRewardAutomationManager;
import com.seqwawa.seq.managers.GuildStorageTracker;
import com.seqwawa.seq.managers.GuildWarTrackers;
import com.seqwawa.seq.managers.IngredientGuideManager;
import com.seqwawa.seq.managers.LeaderboardBadgeService;
import com.seqwawa.seq.managers.PartyFinderManager;
import com.seqwawa.seq.managers.SeqBadgeNametagRenderers;
import com.seqwawa.seq.managers.ThemeManager;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.managers.WynnPartySyncManager;
import com.seqwawa.seq.managers.WorldEventManager;
import com.seqwawa.seq.map.IngredientWaypointRenderer;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.radiance.RadianceCheckerClient;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

final class SeqClientBootstrap {
    private SeqClientBootstrap() {}

    static void initialize(SeqClient client) {
        initializeEventBus(client);
        initializeManagers();
        initializeBaseConfiguration();
        initializeIntegrations();
        registerRendererShutdown();
        LightRoom.init();
        registerKeyBindings();
        ClientTickEvents.END_CLIENT_TICK.register(SeqClient::onEndClientTick);
    }

    private static void initializeEventBus(SeqClient client) {
        try {
            SeqClient.eventBus = new EventBus(SeqClient.mc::execute);
            SeqClient.eventBus.subscribe(client);
        } catch (Exception exception) {
            SeqClient.LOGGER.warn("Event bus failed to initialize.");
        }
    }

    private static void initializeManagers() {
        SeqClient.fontManager = new FontManager();
        SeqClient.gameManager = new GameManager();
        SeqClient.partyFinderManager = new PartyFinderManager();
        SeqClient.wynnPartySyncManager = new WynnPartySyncManager();
        SeqClient.guildWarTracker = GuildWarTrackers.createIfAvailable();
        SeqClient.guildStorageTracker = GuildStorageTracker.getInstance();
        SeqClient.guildRewardAutomationManager = new GuildRewardAutomationManager();
        SeqClient.chatManager = new ChatManager();
        SeqClient.bombShareManager = new BombShareManager();
        SeqClient.treasuryOutManager = new TreasuryOutManager();
        ConnectionManager.onTreasuryOutRecorded(SeqClient.treasuryOutManager::handleRecorded);
        ConnectionManager.onTreasuryOutError(SeqClient.treasuryOutManager::handleError);
    }

    private static void initializeBaseConfiguration() {
        SeqClient.configManager = new ConfigManager();
        SeqClient.chatRegexFilterManager = new ChatRegexFilterManager();
        SeqClient.chatRegexFilterManager.settings().forEach(SeqClient.configManager::register);
        SeqClient.chatRegexFilterManager.registerIncomingHooks();
        SeqClient.configManager.load();
        SeqClient.configManager.migrateToken();
    }

    private static void initializeIntegrations() {
        ThemeManager.initialize();
        SeqClient.leaderboardBadgeService = LeaderboardBadgeService.getInstance();
        SeqClient.seqBadgeNametagRenderer = SeqBadgeNametagRenderers.createIfAvailable();
        SeqClient.worldEventManager = WorldEventManager.getInstance();
        SeqClient.ingredientGuideManager = IngredientGuideManager.getInstance();
        SeqClient.authService = MinecraftAuthService.getInstance();
        SeqCommand.register();
        RadianceCheckerClient.initialize();
        HalcyonRangeVisualiserClient.initialize();
        IngredientWaypointRenderer.initialize();
    }

    private static void registerRendererShutdown() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MinecraftUiRenderer.shutdown());
    }

    private static void registerKeyBindings() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sequoia-mod", "controls"));
        SeqClient.openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sequoia-mod.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));
        SeqClient.shareBombsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sequoia-mod.share_bombs", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
    }

    static void finishLoading() {
        MinecraftUiRenderer.initialize();
        SeqClient.gameManager.loadFont();
        SeqClient.assetManager = new AssetManager();

        SeqClientSettingsCatalog settings = SeqClientSettingsCatalog.create(
                ThemeManager.currentTheme().name(),
                ThemeManager.loadedThemeNames(),
                ThemeManager::setCurrentTheme);
        settings.install(SeqClient.configManager);
        SeqClient.configManager.load();

        if (SeqClient.autoConnectSetting.getValue()
                && WynncraftServerPolicy.currentScope() == WynncraftServerPolicy.Scope.MAIN) {
            ConnectionManager.getInstance().connect();
        }
    }
}
