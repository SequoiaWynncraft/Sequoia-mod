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
import org.sequoia.seq.events.MinecraftFinishedLoading;
import org.sequoia.seq.events.Render2DEvent;
import org.sequoia.seq.command.SeqCommand;
import org.sequoia.seq.managers.AssetManager;
import org.sequoia.seq.managers.FontManager;
import org.sequoia.seq.managers.GameManager;
import org.sequoia.seq.ui.SequoiaScreen;
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
        configManager = new ConfigManager();
        configManager.load();
        SeqCommand.register();

        KeyMapping.Category category =
                KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath("sequoia-mod", "controls")
                );

        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sequoia-mod.open_settings",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new SequoiaScreen());
                }
            }
        });
    }

    @Subscribe(Preference.CALLER) // to stay in thread
    public void onMinecraftFinishedLoading(MinecraftFinishedLoading ignored) {
        //after minecraft done loading
        NVGContext.init();
        SeqClient.gameManager.loadFont();
        SeqClient.assetManager = new AssetManager();

        getConfigManager().register(new Setting.IntSetting("int", "test", 1, 0, 50, 1));
        getConfigManager().register(new Setting.DoubleSetting("double", "test", 1.1, 0.1, 50.1, 0.1));
        getConfigManager().register(new Setting.FloatSetting("float", "test", 1.1f, 0.01f, 50.1f, 0.01f));
        getConfigManager().register(new Setting.BooleanSetting("boolean", "test", false));
        getConfigManager().register(new Setting.StringSetting("String", "test", "hi"));
        getConfigManager().register(new Setting.EnumSetting<>("Enum", "test", Enums.HELLO, Enums.class));
    }

    private enum Enums {
        HI,
        HELLO
    }

    public static Identifier getFileLocation(String path) {
        return Identifier.fromNamespaceAndPath("seq", path);
    }
}
