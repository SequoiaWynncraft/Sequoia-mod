package org.sequoia.seq.accessors;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;

public interface NotificationAccessor {

    String PREFIX_LABEL = "sequoia";
    String PILL_CORNER_LEFT = "⁤";
    String PILL_CORNER_RIGHT = "⁤";
    String PILL_BG_BACK = "";
    String PILL_BG_FRONT = "";

    static @NotNull MutableComponent prefixComponent() {
        return wynnPill(PREFIX_LABEL, ChatFormatting.DARK_PURPLE, ChatFormatting.WHITE)
                .append(Component.literal(" "));
    }

    static @NotNull MutableComponent wynnPill(
            String label,
            ChatFormatting backgroundColor,
            ChatFormatting foregroundColor) {
        return wynnPill(label, backgroundColor, foregroundColor, null);
    }

    static @NotNull MutableComponent wynnPill(
            String label,
            ChatFormatting backgroundColor,
            ChatFormatting foregroundColor,
            ClickEvent clickEvent) {
        MutableComponent pill = Component.empty();
        pill.append(styledPillPart(PILL_CORNER_LEFT, backgroundColor, clickEvent));

        for (int i = 0; i < label.length(); i++) {
            String glyph = toWynncraftGlyph(label.charAt(i));
            pill.append(styledPillPart(PILL_BG_BACK, backgroundColor, clickEvent));
            pill.append(styledPillPart(PILL_BG_FRONT + glyph, foregroundColor, clickEvent));
        }

        pill.append(styledPillPart(PILL_CORNER_RIGHT, backgroundColor, clickEvent));
        return pill;
    }

    static void notifyPlayer(String message) {
        Minecraft.getInstance().execute(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(prefixed(message), false);
            }
        });
    }

    default void notify(String message) {
        notifyPlayer(message);
    }

    default void notifyClickable(String text, String url) {
        Minecraft.getInstance().execute(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                try {
                    URI uri = new URI(url);
                    MutableComponent link = prefixComponent()
                            .append(Component.literal(String.valueOf(text))
                                    .withStyle(style -> style
                                            .withClickEvent(new ClickEvent.OpenUrl(uri))
                                            .withColor(ChatFormatting.AQUA)
                                            .withUnderlined(true)));
                    player.displayClientMessage(link, false);
                } catch (URISyntaxException e) {
                    player.displayClientMessage(prefixed(text + ": " + url), false);
                }
            }
        });
    }

    static @NotNull Component prefixed(String message) {
        return prefixComponent().append(Component.literal(String.valueOf(message)).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent styledPillPart(
            String text,
            ChatFormatting color,
            ClickEvent clickEvent) {
        return Component.literal(text).withStyle(style -> {
            style = style.withColor(color);
            if (clickEvent != null) {
                style = style.withClickEvent(clickEvent);
            }
            return style;
        });
    }

    private static String toWynncraftGlyph(char rawChar) {
        char ch = Character.toLowerCase(rawChar);
        return switch (ch) {
            case 'a' -> "";
            case 'b' -> "";
            case 'c' -> "";
            case 'd' -> "";
            case 'e' -> "";
            case 'f' -> "";
            case 'g' -> "";
            case 'h' -> "";
            case 'i' -> "";
            case 'j' -> "";
            case 'k' -> "";
            case 'l' -> "";
            case 'm' -> "";
            case 'n' -> "";
            case 'o' -> "";
            case 'p' -> "";
            case 'q' -> "";
            case 'r' -> "";
            case 's' -> "";
            case 't' -> "";
            case 'u' -> "";
            case 'v' -> "";
            case 'w' -> "";
            case 'x' -> "";
            case 'y' -> "";
            case 'z' -> "";
            case '0' -> "";
            case '1' -> "";
            case '2' -> "";
            case '3' -> "";
            case '4' -> "";
            case '5' -> "";
            case '6' -> "";
            case '7' -> "";
            case '8' -> "";
            case '9' -> "";
            case ' ' -> " ";
            default -> String.valueOf(rawChar);
        };
    }
}
