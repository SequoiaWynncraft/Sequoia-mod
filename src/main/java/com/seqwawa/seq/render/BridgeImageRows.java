package com.seqwawa.seq.render;

import com.seqwawa.seq.managers.BridgeImageCache;
import com.seqwawa.seq.managers.DiscordRankChatDecorator;
import com.seqwawa.seq.utils.BridgeMedia;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Util;

/**
 * Shows a picture sent over the Discord bridge as part of chat itself.
 * <p>
 * A picture is its own chat message, carrying an invisible marker that names it.
 * When chat lays that message out, it becomes as many lines as the picture is tall,
 * and each line draws its own slice of the picture. Drawn a line at a time, the picture
 * scrolls, fades and scales with the lines around it, and is cut off at the edge of
 * chat exactly where they are.
 * <p>
 * Each line also carries the bridge rail, and blank space as wide as the picture that
 * opens it when clicked, so it answers to the mouse like any other chat link. A picture
 * that could not be shown is laid out as its message's text instead: a link to it.
 */
public final class BridgeImageRows {

    /** Zero-width marker, in a font of its own; see {@code assets/seq/font/bridge_image.json}. */
    static final String MARKER = "\uF8F5";
    static final FontDescription MARKER_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("seq", "bridge_image"));

    /** Space left around a picture, so it does not touch the text around it. */
    static final int PADDING = 1;

    /** Advance of the space the click area is made of. */
    private static final int SPACE_WIDTH = 4;

    private BridgeImageRows() {}

    /**
     * The chat message for {@code picture}, after {@code prefix}. It is laid out as the
     * picture, and reads as a clickable label wherever it is not: while the picture
     * cannot be shown, or when a mod copies the message into something else.
     */
    public static MutableComponent message(BridgeMedia.Picture picture, Component prefix) {
        Style link = linkStyle(picture);
        return Component.empty()
                .append(prefix)
                .append(Component.literal(MARKER).withStyle(link.withFont(MARKER_FONT)))
                .append(Component.literal(picture.label() + " " + picture.name())
                        .withStyle(link.withColor(ChatFormatting.GRAY).withUnderlined(true)));
    }

    /**
     * Opens the picture itself when clicked, and says so when hovered. Only ever an
     * image file on a trusted host, never the page a message linked to it through.
     */
    public static Style linkStyle(BridgeMedia.Picture picture) {
        return BridgeMedia.isSafeMediaLink(picture.fetch())
                ? Style.EMPTY
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Open image in browser").withStyle(ChatFormatting.GRAY)))
                        .withClickEvent(new ClickEvent.OpenUrl(picture.fetch()))
                : Style.EMPTY;
    }

    /**
     * The visual lines for chat message {@code content}, or {@code null} to lay it out
     * as text. A picture still loading takes one line saying so, and has
     * {@code refreshChat} lay chat out again once it has loaded or failed.
     */
    public static List<FormattedCharSequence> rows(
            Component content, Font font, int maxWidth, Runnable refreshChat) {
        String key = pictureKey(content);
        if (key == null || !BridgeImageCache.enabled()) {
            return null;
        }
        BridgeImageCache.Image image = BridgeImageCache.find(key);
        if (image == null) {
            return null;
        }

        Component rail = DiscordRankChatDecorator.bridgeContinuationPrefixFor(content);
        FormattedCharSequence prefix = rail == null ? FormattedCharSequence.EMPTY : rail.getVisualOrderText();
        int x = rail == null ? 0 : font.width(rail);
        return switch (image.state()) {
            case LOADING -> {
                image.notifyWhenSettled(refreshChat);
                yield List.of(FormattedCharSequence.composite(prefix, Component.literal("Loading image...")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                        .getVisualOrderText()));
            }
            case READY -> readyRows(image, content, prefix, x, maxWidth);
            case FAILED, RELEASED -> null;
        };
    }

    private static List<FormattedCharSequence> readyRows(
            BridgeImageCache.Image image, Component content, FormattedCharSequence prefix, int x, int maxWidth) {
        int lineHeight = lineHeight();
        Layout layout = layout(
                image.sourceWidth(),
                image.sourceHeight(),
                BridgeImageCache.pixelDensity(),
                maxWidth - x - 2 * PADDING,
                BridgeImageCache.maxLines() * lineHeight - 2 * PADDING,
                lineHeight);

        Style link = markerStyle(content);
        FormattedCharSequence clickArea = Component.literal(" ".repeat(
                        Math.max(1, (layout.width() + 2 * PADDING + SPACE_WIDTH - 1) / SPACE_WIDTH)))
                .withStyle(link == null ? Style.EMPTY : link)
                .getVisualOrderText();
        FormattedCharSequence text = FormattedCharSequence.composite(prefix, clickArea);

        List<FormattedCharSequence> rows = new ArrayList<>(layout.rows());
        for (int index = 0; index < layout.rows(); index++) {
            rows.add(new Row(image, index, x + PADDING, layout.width(), layout.height(), text));
        }
        return List.copyOf(rows);
    }

    /**
     * How large a picture is drawn, in chat pixels, and over how many lines: shrunk to
     * fit inside {@code maxWidth x maxHeight} with its proportions kept, and never
     * drawn past its own size in screen pixels, {@code density} of them to a chat pixel,
     * so it is never stretched and blurred. {@link #PADDING} is kept above and below.
     */
    static Layout layout(
            int sourceWidth, int sourceHeight, double density, int maxWidth, int maxHeight, int lineHeight) {
        double scale = Math.min(1d / Math.max(1d, density), Math.min(
                (double) Math.max(1, maxWidth) / Math.max(1, sourceWidth),
                (double) Math.max(1, maxHeight) / Math.max(1, sourceHeight)));
        int width = Math.max(1, (int) Math.round(sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));
        int rows = Math.max(1, (height + 2 * PADDING + lineHeight - 1) / lineHeight);
        return new Layout(width, height, rows);
    }

    /** A picture's size in chat, in chat pixels, and the lines it takes. */
    record Layout(int width, int height, int rows) {}

    /**
     * Draws {@code row}'s slice of its picture, for the chat line whose text is drawn
     * at {@code textY}. Chat does not say where a line starts, only where its text
     * does, so the line's top is worked back from chat's own spacing.
     */
    public static void draw(GuiGraphics graphics, int textY, float opacity, Row row) {
        if (opacity <= 0.004f) {
            return;
        }
        Identifier frame = row.image().frameAt(Util.getMillis());
        if (frame == null) {
            return;
        }

        double spacing = Minecraft.getInstance().options.chatLineSpacing().get();
        int lineHeight = (int) (9.0 * (spacing + 1.0));
        int textOffset = (int) Math.round(8.0 * (spacing + 1.0) - 4.0 * spacing);
        int rowTop = textY + textOffset - lineHeight;
        int imageTop = rowTop - row.index() * lineHeight + PADDING;

        int top = Math.max(rowTop, imageTop);
        int bottom = Math.min(rowTop + lineHeight, imageTop + row.height());
        if (bottom <= top) {
            return;
        }
        // The texture is addressed in chat pixels of the drawn picture, so each line's
        // slice meets the next exactly, whatever the texture's own resolution.
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                frame,
                row.x(),
                top,
                0f,
                top - imageTop,
                row.width(),
                bottom - top,
                row.width(),
                bottom - top,
                row.width(),
                row.height(),
                ARGB.white(opacity));
    }

    /**
     * One chat line of a picture: which slice of which picture it draws, and the text
     * it lays out, which is the rail and the click area.
     */
    public record Row(BridgeImageCache.Image image, int index, int x, int width, int height, FormattedCharSequence text)
            implements FormattedCharSequence {
        @Override
        public boolean accept(FormattedCharSink sink) {
            return text.accept(sink);
        }
    }

    /** The key of the picture {@code content} stands for, or {@code null}. */
    public static String pictureKey(Component content) {
        Style marker = markerStyle(content);
        return marker != null
                        && marker.getClickEvent() instanceof ClickEvent.OpenUrl open
                        && BridgeMedia.isSafeMediaLink(open.uri())
                ? open.uri().toString()
                : null;
    }

    /** The style of {@code content}'s marker, which carries the picture's link. */
    private static Style markerStyle(Component content) {
        if (content == null) {
            return null;
        }
        return content.visit(
                        (style, text) -> text.contains(MARKER) && MARKER_FONT.equals(style.getFont())
                                ? Optional.of(style.withFont(FontDescription.DEFAULT))
                                : Optional.<Style>empty(),
                        Style.EMPTY)
                .orElse(null);
    }

    private static int lineHeight() {
        return (int) (9.0 * (Minecraft.getInstance().options.chatLineSpacing().get() + 1.0));
    }
}
