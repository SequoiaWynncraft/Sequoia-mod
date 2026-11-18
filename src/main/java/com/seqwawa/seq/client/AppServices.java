package com.seqwawa.seq.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.BombShareManager;
import com.seqwawa.seq.managers.ChatManager;
import com.seqwawa.seq.managers.ChatRegexFilterManager;
import com.seqwawa.seq.managers.DiscordRankService;
import com.seqwawa.seq.managers.FontManager;
import com.seqwawa.seq.managers.GameManager;
import com.seqwawa.seq.managers.GuildRewardAutomationManager;
import com.seqwawa.seq.managers.GuildStorageTracker;
import com.seqwawa.seq.managers.GuildWarTrackerHandle;
import com.seqwawa.seq.managers.GuildWarTrackers;
import com.seqwawa.seq.managers.IngredientGuideManager;
import com.seqwawa.seq.managers.LeaderboardBadgeService;
import com.seqwawa.seq.managers.PartyFinderManager;
import com.seqwawa.seq.managers.SeqBadgeNametagRendererHandle;
import com.seqwawa.seq.managers.SeqBadgeNametagRenderers;
import com.seqwawa.seq.managers.ThemeManager;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.managers.WarTerritoryBoundsReminder;
import com.seqwawa.seq.managers.WorldEventManager;
import com.seqwawa.seq.managers.WynnPartySyncManager;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.raids.tna.TnaSahurSoundDetector;
import com.seqwawa.seq.session.SessionManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * What the mod keeps for as long as the game runs, whoever is signed in: the config, fonts
 * and assets, chat, the trackers and caches, and the auth service and session manager. What
 * belongs to one sign-in lives in its {@link com.seqwawa.seq.session.Session} instead.
 * <p>
 * The game builds one at start-up, filling it in the order the mod has always started its
 * parts, since some of them read others as they start; the assets come later, once Minecraft
 * has finished loading. {@link SeqClient#app()} hands it to mixins and commands. A test
 * builds its own with {@link #builder()} and installs it with {@link SeqClient#installApp}.
 */
public final class AppServices {

    /** The mod's key bindings, registered once. */
    public record Keys(
            KeyMapping openScreen,
            KeyMapping openPartyFinder,
            KeyMapping openWarPlanner,
            KeyMapping openWorldMap,
            KeyMapping openIngredientGuide,
            KeyMapping shareBombs) {}

    private FontManager fontManager;
    private GameManager gameManager;
    private AssetManager assetManager;
    private ConfigManager configManager;
    private PartyFinderManager partyFinderManager;
    private WarTerritoryBoundsReminder warTerritoryBoundsReminder;
    private MinecraftAuthService authService;
    private ChatManager chatManager;
    private ChatRegexFilterManager chatRegexFilterManager;
    private BombShareManager bombShareManager;
    private TreasuryOutManager treasuryOutManager;
    private WynnPartySyncManager wynnPartySyncManager;
    private GuildWarTrackerHandle guildWarTracker;
    private GuildStorageTracker guildStorageTracker;
    private GuildRewardAutomationManager guildRewardAutomationManager;
    private LeaderboardBadgeService leaderboardBadgeService;
    private DiscordRankService discordRankService;
    private SeqBadgeNametagRendererHandle seqBadgeNametagRenderer;
    private WorldEventManager worldEventManager;
    private IngredientGuideManager ingredientGuideManager;
    private Keys keys;
    private final PartyClassBroadcast partyClass = new PartyClassBroadcast();
    /** When the tick last tried to reconnect to production, by {@link System#currentTimeMillis()}. */
    private long lastProductionRecoveryAttemptAtMs;

    AppServices() {}

    /**
     * Starts the long-lived parts in the order the mod always has. Each is visible through
     * {@link SeqClient#app()} as soon as it exists, as the parts started after it expect.
     */
    void start() {
        TnaSahurSoundDetector.initialize();
        fontManager = new FontManager();
        gameManager = new GameManager();
        partyFinderManager = new PartyFinderManager();
        warTerritoryBoundsReminder = new WarTerritoryBoundsReminder();
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
        // Before anything can sign in: it hears of each sign-in for the session it belongs to.
        SessionManager.getInstance();
    }

    /** Registers the key bindings, at the point of start-up the controls list has always had them. */
    void registerKeys() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sequoia-mod", "controls"));
        keys = new Keys(
                KeyBindingHelper.registerKeyBinding(new KeyMapping(
                        "key.sequoia-mod.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category)),
                KeyBindingHelper.registerKeyBinding(new KeyMapping(
                        "key.sequoia-mod.open_party_finder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category)),
                KeyBindingHelper.registerKeyBinding(new KeyMapping(
                        "key.sequoia-mod.open_war_planner", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category)),
                KeyBindingHelper.registerKeyBinding(new KeyMapping(
                        "key.sequoia-mod.open_world_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category)),
                KeyBindingHelper.registerKeyBinding(new KeyMapping(
                        "key.sequoia-mod.open_ingredient_guide",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        category)),
                KeyBindingHelper.registerKeyBinding(new KeyMapping(
                        "key.sequoia-mod.share_bombs", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category)));
    }

    /** Loads the textures, once Minecraft has finished loading. */
    void loadAssets() {
        assetManager = new AssetManager();
    }

    public FontManager fontManager() { return fontManager; }
    public GameManager gameManager() { return gameManager; }
    /** Null until Minecraft has finished loading. */
    public AssetManager assetManager() { return assetManager; }
    public ConfigManager configManager() { return configManager; }
    public PartyFinderManager partyFinderManager() { return partyFinderManager; }
    public WarTerritoryBoundsReminder warTerritoryBoundsReminder() { return warTerritoryBoundsReminder; }
    public MinecraftAuthService authService() { return authService; }
    public ChatManager chatManager() { return chatManager; }
    public ChatRegexFilterManager chatRegexFilterManager() { return chatRegexFilterManager; }
    public BombShareManager bombShareManager() { return bombShareManager; }
    public TreasuryOutManager treasuryOutManager() { return treasuryOutManager; }
    public WynnPartySyncManager wynnPartySyncManager() { return wynnPartySyncManager; }
    public GuildWarTrackerHandle guildWarTracker() { return guildWarTracker; }
    public GuildStorageTracker guildStorageTracker() { return guildStorageTracker; }
    public GuildRewardAutomationManager guildRewardAutomationManager() { return guildRewardAutomationManager; }
    public LeaderboardBadgeService leaderboardBadgeService() { return leaderboardBadgeService; }
    public DiscordRankService discordRankService() { return discordRankService; }
    public SeqBadgeNametagRendererHandle seqBadgeNametagRenderer() { return seqBadgeNametagRenderer; }
    public WorldEventManager worldEventManager() { return worldEventManager; }
    public IngredientGuideManager ingredientGuideManager() { return ingredientGuideManager; }
    /** Null until start-up registers them. */
    public Keys keys() { return keys; }
    public PartyClassBroadcast partyClass() { return partyClass; }
    public SessionManager sessions() { return SessionManager.getInstance(); }
    public ServerScopeTracker scopes() { return ServerScopeTracker.get(); }

    long lastProductionRecoveryAttemptAtMs() {
        return lastProductionRecoveryAttemptAtMs;
    }

    void productionRecoveryAttempted(long atMs) {
        lastProductionRecoveryAttemptAtMs = atMs;
    }

    /** An app with only the parts a test gives it; everything else is absent, as before start-up. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds an {@link AppServices} for a test. */
    public static final class Builder {
        private final AppServices services = new AppServices();

        private Builder() {}

        public Builder configManager(ConfigManager configManager) {
            services.configManager = configManager;
            return this;
        }

        public Builder fontManager(FontManager fontManager) {
            services.fontManager = fontManager;
            return this;
        }

        public Builder authService(MinecraftAuthService authService) {
            services.authService = authService;
            return this;
        }

        public AppServices build() {
            return services;
        }
    }
}
