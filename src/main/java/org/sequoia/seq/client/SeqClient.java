package org.sequoia.seq.client;

import com.collarmc.pounce.EventBus;
import com.collarmc.pounce.Preference;
import com.collarmc.pounce.Subscribe;
import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.sequoia.seq.events.MinecraftFinishedLoading;
import org.sequoia.seq.events.Render2DEvent;
import org.sequoia.seq.command.SeqCommand;
import org.sequoia.seq.managers.AssetManager;
import org.sequoia.seq.managers.FontManager;
import org.sequoia.seq.managers.GameManager;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;
import org.slf4j.Logger;

import java.awt.*;

public class SeqClient implements ClientModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final Minecraft mc = Minecraft.getInstance();

    public static OS os;
    String osName;

    @Getter
    public static EventBus eventBus;
    @Getter
    public static FontManager fontManager;
    public static GameManager gameManager;
    public static AssetManager assetManager;

    @Override
    public void onInitializeClient() {
        osCheck();
        try {
            eventBus = new EventBus(mc::execute);
            eventBus.subscribe(this);
        } catch (Exception e) {
            LOGGER.warn("Event bus failed to initialize.");
        }
        fontManager = new FontManager();
        gameManager = new GameManager();
        SeqCommand.register();
    }

    @Subscribe(Preference.CALLER) // to stay in thread
    public void onMinecraftFinishedLoading(MinecraftFinishedLoading ignored) {
        //after minecraft done loading
        NVGContext.init();
        SeqClient.gameManager.loadFont();
        SeqClient.assetManager = new AssetManager();
    }

    public static Identifier getFileLocation(String path) {
        return Identifier.fromNamespaceAndPath("seq", path);
    }

    public void osCheck() {
        osName = System.getProperty("os.name");
        if (osName.charAt(0) == 'w' || osName.charAt(0) == 'W') {
            // for some reason the main mc thread runs Headless as true
            //due to this some things in java are limited ie adding something to clipboard or doing our login stuff
            os = OS.WINDOWS;
            //System.setProperty("java.awt.headless", "false");

        } else if (osName.charAt(0) == 'm' || osName.charAt(0) == 'M') {
            os = OS.MAC;
        } else {
            os = OS.LINUX;
        }
    }

    public enum OS {
        WINDOWS,
        MAC,
        LINUX

    }
}
