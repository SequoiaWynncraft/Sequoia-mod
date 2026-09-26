package com.seqwawa.seq.accessors;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.NotNull;

import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.RankGradientAnimation;
import com.seqwawa.seq.utils.WynnPillGlyphs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.IntFunction;

public interface NotificationAccessor {

    String PREFIX_LABEL = "sequoia";
    String PILL_CORNER_LEFT = "⁤";
    String PILL_CORNER_RIGHT = "⁤";
    String PILL_BG_BACK = "";
    String PILL_BG_FRONT = "";

    /** Width Wynncraft draws a {@link #PILL_BG_BACK} block at; it advances one pixel further. */
    int PILL_BG_WIDTH = 6;
    int PILL_BG_ADVANCE = PILL_BG_WIDTH + 1;

    /** How far a corner moves the pill on: its glyph, less the padding that follows it. */
    int PILL_CORNER_ADVANCE = 2;

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
     * A rank pill, the gradient axis its background was painted on, and where that axis
     * lies: {@code axisStart} pixels from the pill's start, and the pill {@code advance}
     * pixels wide in all, so a name drawn after it can be joined onto the same gradient.
     */
    record GradientPill(MutableComponent component, RankGradientAnimation.Axis axis, int axisStart, int advance) {

        /** Pixels from the end of this pill's gradient to a name starting {@code after} pixels past the pill. */
        public float gapTo(int after) {
            return advance + after - axisStart - axis.length();
        }
    }

    /**
     * Pill whose background is painted along a gradient axis, so a Discord gradient role
     * reads as a gradient rather than as its primary colour alone, and switches live
     * between the member's own palette and their shared role palette.
     * <p>
     * Only the background is graded. The label keeps {@code labelColor} for the whole
     * pill: letters that shifted hue from one to the next read as a rendering fault
     * rather than as a gradient.
     * <p>
     * Each character's background is one block, placed on the axis at the pixel it is
     * drawn at. The gradient is applied to the blocks' corners as they are drawn, so it
     * runs smoothly across each block and on into the next; see
     * {@link RankGradientAnimation#shade}.
     */
    static GradientPill gradientPill(
            String label,
            ColorRamp displayRamp,
            ColorRamp roleRamp,
            TextColor labelColor,
            ClickEvent clickEvent,
            TextColor baseBackgroundColor) {
        return RankGradientAnimation.batchRegistrations(() -> buildGradientPill(
                label, displayRamp, roleRamp, labelColor, clickEvent, baseBackgroundColor));
    }

    private static GradientPill buildGradientPill(
            String label,
            ColorRamp displayRamp,
            ColorRamp roleRamp,
            TextColor labelColor,
            ClickEvent clickEvent,
            TextColor baseBackgroundColor) {
        Font font = clientFont();
        int corner = measured(font, PILL_CORNER_LEFT, PILL_CORNER_ADVANCE);
        int[] starts = pillBlockStarts(label, font);
        int end = starts[label.length()];
        int length = label.isEmpty() ? 1 : starts[label.length() - 1] + PILL_BG_WIDTH;
        RankGradientAnimation.Axis axis = RankGradientAnimation.axis(
                displayRamp, roleRamp, RankGradientAnimation.Target.RANK_BADGE, length);

        MutableComponent pill = Component.empty();
        pill.append(styledPillPart(
                PILL_CORNER_LEFT, axis.colorAt(-corner, corner, baseBackgroundColor), clickEvent));
        for (int i = 0; i < label.length(); i++) {
            char rawChar = label.charAt(i);
            pill.append(styledPillPart(
                    PILL_BG_BACK, axis.colorAt(starts[i], PILL_BG_WIDTH, baseBackgroundColor), clickEvent));
            // A character with no glyph, such as the space in "Upper Strategist", gets
            // the background block on its own. Drawing a plain character on top instead
            // would advance by a different amount than the block it sits on and push the
            // rest of the label off its background; a bare block reads as a gap, which
            // is what a space in a pill should look like anyway.
            if (WynnPillGlyphs.hasGlyph(rawChar)) {
                pill.append(labelPillPart(PILL_BG_FRONT + toWynncraftGlyph(rawChar), labelColor, clickEvent));
            }
        }
        pill.append(styledPillPart(PILL_CORNER_RIGHT, axis.colorAt(end, corner, baseBackgroundColor), clickEvent));
        return new GradientPill(pill, axis, corner, corner + end + corner);
    }

    /**
     * Where each character's background block starts, in pixels from the first, and
     * where the last one ends. A letter moves the pill on by its own advance, since
     * {@link #PILL_BG_FRONT} pulls it back over its block; a character with no glyph
     * moves it by the whole block.
     * <p>
     * Letters are measured with the loaded font because Wynncraft's are not all one
     * width: {@code I} is narrower, and the next block overlaps its own. Outside a
     * running client every letter is taken to fill its block.
     */
    private static int[] pillBlockStarts(String label, Font font) {
        int[] starts = new int[label.length() + 1];
        int x = 0;
        for (int i = 0; i < label.length(); i++) {
            starts[i] = x;
            char rawChar = label.charAt(i);
            x += WynnPillGlyphs.hasGlyph(rawChar)
                    ? measured(font, toWynncraftGlyph(rawChar), PILL_BG_WIDTH)
                    : PILL_BG_ADVANCE;
        }
        starts[label.length()] = x;
        return starts;
    }

    /** {@code text}'s width in the loaded font, or {@code fallback} without one. */
    private static int measured(Font font, String text, int fallback) {
        int width = font == null ? 0 : font.width(text);
        return width > 0 ? width : fallback;
    }

    /** The client's font, or {@code null} when there is no client, as in unit tests. */
    private static Font clientFont() {
        try {
            return Minecraft.getInstance().font;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
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
