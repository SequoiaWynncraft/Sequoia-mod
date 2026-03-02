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
import org.sequoia.seq.events.Render2DEvent;
import org.sequoia.seq.command.SeqCommand;
import org.sequoia.seq.managers.AssetManager;
import org.sequoia.seq.managers.ChatManager;
import org.sequoia.seq.managers.FontManager;
import org.sequoia.seq.managers.GameManager;
import org.sequoia.seq.managers.PartyFinderManager;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.ui.SequoiaScreen;
import org.sequoia.seq.update.UpdateManager;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;
import org.slf4j.Logger;

import java.awt.*;

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

    private static KeyMapping openScreenKey;

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
        chatManager = new ChatManager();
        configManager = new ConfigManager();
        configManager.load();
        configManager.migrateToken();
        SeqCommand.register();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("sequoia-mod", "controls"));

        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sequoia-mod.open_settings",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.consumeClick()) {
                if (client.screen == null) {
                    openMainScreen();
                }
            }
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
        getConfigManager().register(autoConnectSetting);
        getConfigManager().register(showDiscordChatSetting);
        getConfigManager().register(raidAutoAnnounceSetting);
        getConfigManager().register(checkUpdatesSetting);
        getConfigManager().load(); // reload to pick up saved values for new settings

        // Auto-connect if enabled and token is present
        if (autoConnectSetting.getValue()) {
            String token = configManager.getToken();
            if (token != null && !token.isBlank()) {
                ConnectionManager.getInstance().connect();
            }
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
