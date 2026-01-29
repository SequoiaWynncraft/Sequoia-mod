package op.legends.seq.client;

import com.collarmc.pounce.EventBus;
import com.collarmc.pounce.Subscribe;
import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import op.legends.seq.events.MinecraftFinishedLoading;
import op.legends.seq.events.Render2DEvent;
import op.legends.seq.managers.AssetManager;
import op.legends.seq.managers.FontManager;
import op.legends.seq.managers.GameManager;
import op.legends.seq.utils.rendering.nvg.NVGContext;
import op.legends.seq.utils.rendering.nvg.NVGWrapper;
import org.slf4j.Logger;

import java.awt.*;

public class SeqClient implements ClientModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final Minecraft mc = Minecraft.getInstance();

    public static boolean isGameLaunched;
    public static OS os;
    String osName;

    @Getter
    public static EventBus eventBus;
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
    }

    @Subscribe
    public void onMinecraftFinishedLoading(MinecraftFinishedLoading ignored) {
        //after minecraft done loading
        //NO RENDER SHIT THE EVENT TAKES IT OUT OF THE THREAD
    }

    @Subscribe
    public void onRender2Dtest(Render2DEvent event) {
        NVGContext.render(nvg -> {
            NVGWrapper.drawImage(nvg, SeqClient.assetManager.getAsset("icon")
                    , 50, 50, 50, 50, 255);
            SeqClient.fontManager.drawText(event.context(), "SEQ ON TOP", 50, 50, Color.CYAN, true);
        });
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
