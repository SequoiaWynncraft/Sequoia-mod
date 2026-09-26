package com.seqwawa.seq.accessors;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.RankGradientAnimation;
import com.seqwawa.seq.utils.WynnPillGlyphs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.DoubleFunction;
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

    /**
     * One pixel-wide column of a {@link #PILL_BG_BACK} block, and the step back that
     * butts the next column against it; see {@code assets/seq/font/rank_pill.json}.
     */
    String PILL_COLUMN = String.valueOf(WynnPillGlyphs.COLUMN);
    String PILL_COLUMN_STEP_BACK = String.valueOf(WynnPillGlyphs.COLUMN_STEP_BACK);
    FontDescription PILL_COLUMN_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("seq", "rank_pill"));

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
     * Only the background is graded. The label keeps {@code labelColor} for the whole
     * pill: letters that shifted hue from one to the next read as a rendering fault
     * rather than as a gradient.
     * <p>
     * The background colours are minted through {@link RankGradientAnimation} so a
     * gradient role can be scrolled at render time; they are the same colours either
     * way while the animation setting is off.
     */
    static @NotNull MutableComponent wynnPill(
            String label,
            ColorRamp ramp,
            TextColor labelColor,
            ClickEvent clickEvent) {
        return wynnPill(label, ramp, ramp, labelColor, clickEvent, null);
    }

    /** Gradient pill that returns to {@code baseBackgroundColor} when role colouring is off. */
    static @NotNull MutableComponent wynnPill(
            String label,
            ColorRamp ramp,
            TextColor labelColor,
            ClickEvent clickEvent,
            TextColor baseBackgroundColor) {
        return wynnPill(label, ramp, ramp, labelColor, clickEvent, baseBackgroundColor);
    }

    /** Pill that can switch live between a member palette and their shared role palette. */
    static @NotNull MutableComponent wynnPill(
            String label,
            ColorRamp displayRamp,
            ColorRamp roleRamp,
            TextColor labelColor,
            ClickEvent clickEvent,
            TextColor baseBackgroundColor) {
        IntFunction<TextColor> backgroundAt = index -> RankGradientAnimation.colorAt(
                displayRamp,
                roleRamp,
                gradientPosition(index, label.length()),
                RankGradientAnimation.Target.RANK_BADGE,
                baseBackgroundColor);
        return RankGradientAnimation.batchRegistrations(
                () -> wynnPill(label, backgroundAt, index -> labelColor, clickEvent));
    }

    /**
     * Gradient pill whose background is filled one pixel column at a time, so a role
     * gradient runs smoothly across it rather than stepping once per letter.
     * <p>
     * Every {@link #PILL_BG_BACK} block becomes {@link #PILL_BG_WIDTH} columns that
     * cover the same pixels and advance by the same amount, so the letters, corners
     * and everything after the pill land exactly where the block pill puts them. Each
     * column is sampled at its own pixel along the pill, which keeps the ramp even
     * under narrow letters, where Wynncraft lets the next block overlap.
     * <p>
     * A role with no gradient on either palette keeps the block pill: its columns
     * would all be one colour, at several times the glyphs and registered stops.
     */
    static @NotNull MutableComponent smoothWynnPill(
            String label,
            ColorRamp displayRamp,
            ColorRamp roleRamp,
            TextColor labelColor,
            ClickEvent clickEvent,
            TextColor baseBackgroundColor) {
        if (!displayRamp.isGradient() && !roleRamp.isGradient()) {
            return wynnPill(label, displayRamp, roleRamp, labelColor, clickEvent, baseBackgroundColor);
        }
        return RankGradientAnimation.batchRegistrations(() -> columnPill(
                label, displayRamp, roleRamp, labelColor, clickEvent, baseBackgroundColor));
    }

    private static @NotNull MutableComponent columnPill(
            String label,
            ColorRamp displayRamp,
            ColorRamp roleRamp,
            TextColor labelColor,
            ClickEvent clickEvent,
            TextColor baseBackgroundColor) {
        int[] blockStarts = pillBlockStarts(label);
        int span = label.isEmpty() ? 0 : blockStarts[label.length() - 1] + PILL_BG_WIDTH;
        // Half the step between neighbouring columns, so a column's edges meet theirs.
        double spread = span <= 1 ? 0d : 0.5d / (span - 1);
        DoubleFunction<TextColor> backgroundAt = position -> RankGradientAnimation.colorAt(
                displayRamp, roleRamp, position, RankGradientAnimation.Target.RANK_BADGE, baseBackgroundColor);
        DoubleFunction<TextColor> columnAt = position -> RankGradientAnimation.colorAt(
                displayRamp,
                roleRamp,
                position,
                spread,
                RankGradientAnimation.Target.RANK_BADGE,
                baseBackgroundColor);

        MutableComponent pill = Component.empty();
        pill.append(styledPillPart(PILL_CORNER_LEFT, backgroundAt.apply(0d), clickEvent));

        for (int i = 0; i < label.length(); i++) {
            char rawChar = label.charAt(i);
            for (int column = 0; column < PILL_BG_WIDTH; column++) {
                // The last column keeps the pixel of advance a bitmap glyph leaves after
                // itself, so the run advances exactly as far as the block it replaces.
                String glyph = column < PILL_BG_WIDTH - 1 ? PILL_COLUMN + PILL_COLUMN_STEP_BACK : PILL_COLUMN;
                double position = gradientPosition(blockStarts[i] + column, span);
                pill.append(columnPillPart(glyph, columnAt.apply(position), clickEvent));
            }
            if (WynnPillGlyphs.hasGlyph(rawChar)) {
                pill.append(labelPillPart(PILL_BG_FRONT + toWynncraftGlyph(rawChar), labelColor, clickEvent));
            }
        }

        pill.append(styledPillPart(PILL_CORNER_RIGHT, backgroundAt.apply(1d), clickEvent));
        return pill;
    }

    /**
     * Where each character's background block starts, in pixels from the first. A
     * letter moves the pill on by its own advance, since {@link #PILL_BG_FRONT} pulls
     * it back over its block; a character with no glyph moves it by the whole block.
     * <p>
     * Letters are measured with the loaded font because Wynncraft's are not all one
     * width: {@code I} is narrower, and the next block overlaps its own. Outside a
     * running client every letter is taken to fill its block.
     */
    private static int[] pillBlockStarts(String label) {
        Font font = clientFont();
        int[] starts = new int[label.length()];
        int x = 0;
        for (int i = 0; i < label.length(); i++) {
            starts[i] = x;
            char rawChar = label.charAt(i);
            if (!WynnPillGlyphs.hasGlyph(rawChar)) {
                x += PILL_BG_ADVANCE;
                continue;
            }
            int advance = font == null ? 0 : font.width(toWynncraftGlyph(rawChar));
            x += advance > 0 ? advance : PILL_BG_WIDTH;
        }
        return starts;
    }

    /** The client's font, or {@code null} when there is no client, as in unit tests. */
    private static Font clientFont() {
        try {
            return Minecraft.getInstance().font;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * Where glyph or pixel column {@code index} of {@code length} sits in {@code [0, 1]}
     * along the pill. A single step has no span to run a gradient over, so it takes the
     * first stop.
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

    /** A background column, in the mod's own font since Wynncraft has no such glyph. */
    private static MutableComponent columnPillPart(
            String text,
            TextColor color,
            ClickEvent clickEvent) {
        return styledPillPart(text, color, clickEvent).withStyle(style -> style.withFont(PILL_COLUMN_FONT));
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
