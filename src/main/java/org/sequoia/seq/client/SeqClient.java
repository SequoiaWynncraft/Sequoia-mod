package org.sequoia.seq.client;

import com.collarmc.pounce.EventBus;
import com.collarmc.pounce.Preference;
import com.collarmc.pounce.Subscribe;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.sequoia.seq.config.ConfigManager;
import org.sequoia.seq.config.Setting;
import org.sequoia.seq.events.GameStartEvent;
import org.sequoia.seq.events.MinecraftFinishedLoading;
import org.sequoia.seq.command.SeqCommand;
import org.sequoia.seq.managers.AssetManager;
import org.sequoia.seq.managers.ChatManager;
import org.sequoia.seq.managers.FontManager;
import org.sequoia.seq.managers.GameManager;
import org.sequoia.seq.managers.PartyFinderManager;
import org.sequoia.seq.managers.WynnPartySyncManager;
import org.sequoia.seq.model.WynnClassType;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.network.WynncraftServerPolicy;
import org.sequoia.seq.network.auth.MinecraftAuthService;
import org.sequoia.seq.ui.SequoiaScreen;
import org.sequoia.seq.utils.WynnClassCache;
import org.sequoia.seq.update.UpdateManager;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.slf4j.Logger;

public class SeqClient implements ClientModInitializer {
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

    // ── Network config settings ──
    @Getter
    public static Setting.BooleanSetting autoConnectSetting;

    @Getter
    public static Setting.BooleanSetting showDiscordChatSetting;

    @Getter
    public static Setting.BooleanSetting raidAutoAnnounceSetting;

    @Getter
    public static Setting.BooleanSetting checkUpdatesSetting;

    @Getter
    public static Setting.BooleanSetting easterEggsSetting;

    @Getter
    public static Setting.BooleanSetting announceOpenPartiesSetting;

    @Getter
    public static Setting.IntSetting announceOpenPartiesIntervalMinutesSetting;

    @Getter
    public static Setting.BooleanSetting syncWynnPartySetting;

    @Getter
    public static WynnPartySyncManager wynnPartySyncManager;

    private static KeyMapping openScreenKey;
    private static WynnClassType lastBroadcastPartyClass;
    private static boolean wasInPartyFinder;

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
        chatManager = new ChatManager();
        configManager = new ConfigManager();
        configManager.load();
        configManager.migrateToken();
        authService = MinecraftAuthService.getInstance();
        SeqCommand.register();

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sequoia-mod", "controls"));

        openScreenKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.sequoia-mod.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.consumeClick()) {
                if (client.screen == null) {
                    openMainScreen();
                }
            }

            if (!WynncraftServerPolicy.isCurrentServerAllowed()) {
                ConnectionManager.disconnectForBlockedServer();
                wasInPartyFinder = false;
                lastBroadcastPartyClass = null;
                if (wynnPartySyncManager != null) {
                    wynnPartySyncManager.reset();
                }
                return;
            }

            if (partyFinderManager != null) {
                partyFinderManager.tickOpenPartyAnnouncements();
            }
            if (wynnPartySyncManager != null) {
                wynnPartySyncManager.tick();
            }

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

    public static void openMainScreen() {
        mc.execute(() -> mc.setScreen(new SequoiaScreen()));
    }

    @Subscribe(Preference.CALLER) // to stay in thread
    public void onMinecraftFinishedLoading(MinecraftFinishedLoading ignored) {
        // after minecraft done loading
        NVGContext.init();
        SeqClient.gameManager.loadFont();
        SeqClient.assetManager = new AssetManager();

        // Network settings
        autoConnectSetting = new Setting.BooleanSetting("auto_connect", "network", true);
        showDiscordChatSetting = new Setting.BooleanSetting("show_discord_bridge", "chat", true);
        raidAutoAnnounceSetting = new Setting.BooleanSetting("auto_announce", "raids", true);
        checkUpdatesSetting = new Setting.BooleanSetting("check_updates", "updates", true);
        easterEggsSetting = new Setting.BooleanSetting("enable_easter_eggs", "ui", true);
        announceOpenPartiesSetting = new Setting.BooleanSetting("announce_open_parties", "party_finder", true);
        announceOpenPartiesIntervalMinutesSetting =
                new Setting.IntSetting("announce_open_parties_interval_minutes", "party_finder", 5, 1, 60);
        syncWynnPartySetting = new Setting.BooleanSetting("sync_with_wynn_party", "party_finder", true);
        getConfigManager().register(autoConnectSetting);
        getConfigManager().register(showDiscordChatSetting);
        getConfigManager().register(raidAutoAnnounceSetting);
        getConfigManager().register(checkUpdatesSetting);
        getConfigManager().register(easterEggsSetting);
        getConfigManager().register(announceOpenPartiesSetting);
        getConfigManager().register(announceOpenPartiesIntervalMinutesSetting);
        getConfigManager().register(syncWynnPartySetting);
        getConfigManager().load(); // reload to pick up saved values for new settings

        // Auto-connect if enabled. The auth service will refresh or mint a backend token as needed.
        if (autoConnectSetting.getValue() && WynncraftServerPolicy.isCurrentServerAllowed()) {
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
