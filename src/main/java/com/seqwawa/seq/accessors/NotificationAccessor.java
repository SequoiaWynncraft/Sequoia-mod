package com.seqwawa.seq.accessors;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.NotNull;

import com.seqwawa.seq.utils.WynnPillGlyphs;

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
        return wynnPill(
                label,
                TextColor.fromLegacyFormat(backgroundColor),
                TextColor.fromLegacyFormat(foregroundColor),
                clickEvent);
    }

    /** Pill variant accepting arbitrary colours, for palettes outside the 16 legacy ones. */
    static @NotNull MutableComponent wynnPill(
            String label,
            TextColor backgroundColor,
            TextColor foregroundColor,
            ClickEvent clickEvent) {
        MutableComponent pill = Component.empty();
        pill.append(styledPillPart(PILL_CORNER_LEFT, backgroundColor, clickEvent));

        for (int i = 0; i < label.length(); i++) {
            String glyph = toWynncraftGlyph(label.charAt(i));
            pill.append(styledPillPart(PILL_BG_BACK, backgroundColor, clickEvent));
            pill.append(labelPillPart(PILL_BG_FRONT + glyph, foregroundColor, clickEvent));
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

    /**
     * The label sits on top of the background block, so it must not cast a shadow:
     * the offset copy would smear across the block underneath. That is most visible
     * with dark labels on pale backgrounds, where the shadow reads as a blur rather
     * than an outline.
     */
    private static MutableComponent labelPillPart(
            String text,
            TextColor color,
            ClickEvent clickEvent) {
        return styledPillPart(text, color, clickEvent).withStyle(Style::withoutShadow);
    }

    private static MutableComponent styledPillPart(
            String text,
            TextColor color,
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
        return WynnPillGlyphs.encodeGlyph(rawChar);
    }
}
