package org.sequoia.seq.accessors;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.net.URISyntaxException;

public interface NotificationAccessor {

    String PREFIX = "§3[Seq] §7";

    default void notify(String message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal(PREFIX + message), false);
            }
        });
    }

    default void notifyClickable(String text, String url) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                try {
                    URI uri = new URI(url);
                    MutableComponent link = Component.literal(PREFIX + text)
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent.OpenUrl(uri))
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true));
                    Minecraft.getInstance().player.displayClientMessage(link, false);
                } catch (URISyntaxException e) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal(PREFIX + text + ": " + url), false);
                }
            }
        });
    }

    static Component prefixed(String message) {
        return Component.literal(PREFIX + message);
    }
}