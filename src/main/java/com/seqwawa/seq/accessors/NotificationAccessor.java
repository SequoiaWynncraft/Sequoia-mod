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

import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.WynnPillGlyphs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

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
        return wynnPill(label, index -> backgroundColor, index -> foregroundColor, clickEvent);
    }

    /**
     * Pill whose background runs through {@code ramp} across its glyphs, so a Discord
     * gradient role reads as a gradient rather than as its primary colour alone.
     * <p>
     * Only the background is graded. The label keeps one colour for the whole pill,
     * derived from the middle of the ramp by {@code labelColor}: letters that shifted
     * hue from one to the next read as a rendering fault rather than as a gradient.
     */
    static @NotNull MutableComponent wynnPill(
            String label,
            ColorRamp ramp,
            UnaryOperator<TextColor> labelColor,
            ClickEvent clickEvent) {
        IntFunction<TextColor> backgroundAt =
                index -> TextColor.fromRgb(ramp.sample(gradientPosition(index, label.length())));
        TextColor labelAcrossPill = labelColor.apply(TextColor.fromRgb(ramp.sample(0.5d)));
        return wynnPill(label, backgroundAt, index -> labelAcrossPill, clickEvent);
    }

    /**
     * Where glyph {@code index} sits in {@code [0, 1]} along the pill. A single-glyph
     * label has no span to run a gradient over, so it takes the first stop.
     */
    private static double gradientPosition(int index, int length) {
        return length <= 1 ? 0d : (double) index / (length - 1);
    }

    private static @NotNull MutableComponent wynnPill(
            String label,
            IntFunction<TextColor> backgroundAt,
            IntFunction<TextColor> labelAt,
            ClickEvent clickEvent) {
        int lastIndex = Math.max(0, label.length() - 1);
        MutableComponent pill = Component.empty();
        pill.append(styledPillPart(PILL_CORNER_LEFT, backgroundAt.apply(0), clickEvent));

        for (int i = 0; i < label.length(); i++) {
            char rawChar = label.charAt(i);
            pill.append(styledPillPart(PILL_BG_BACK, backgroundAt.apply(i), clickEvent));
            // A character with no glyph, such as the space in "Upper Strategist", gets
            // the background block on its own. Drawing a plain character on top instead
            // would advance by a different amount than the block it sits on and push the
            // rest of the label off its background; a bare block reads as a gap, which
            // is what a space in a pill should look like anyway.
            if (WynnPillGlyphs.hasGlyph(rawChar)) {
                pill.append(labelPillPart(PILL_BG_FRONT + toWynncraftGlyph(rawChar), labelAt.apply(i), clickEvent));
            }
        }

        pill.append(styledPillPart(PILL_CORNER_RIGHT, backgroundAt.apply(lastIndex), clickEvent));
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
