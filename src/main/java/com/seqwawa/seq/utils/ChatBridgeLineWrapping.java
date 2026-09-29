package com.seqwawa.seq.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Adds aligned Discord rails to visual lines created by Minecraft's chat wrapping, and
 * keeps a bridged reply's quote to one line.
 */
public final class ChatBridgeLineWrapping {

    /** Three full stops rather than one ellipsis character, which Minecraft's own font lacks. */
    private static final String ELLIPSIS = "...";

    private ChatBridgeLineWrapping() {}

    public static List<FormattedCharSequence> wrapColoredBridgeMessage(
            List<FormattedCharSequence> initialLines,
            GuiMessage message,
            Font font,
            int maxWidth,
            Component continuationPrefix) {
        return addAutomaticContinuationPrefixes(
                initialLines,
                maxWidth,
                font.width(continuationPrefix),
                width -> message.splitLines(font, width),
                continuationPrefix.getVisualOrderText());
    }

    static List<FormattedCharSequence> addAutomaticContinuationPrefixes(
            List<FormattedCharSequence> initialLines,
            int maxWidth,
            int prefixWidth,
            IntFunction<List<FormattedCharSequence>> wrapAtWidth,
            FormattedCharSequence prefix) {
        if (initialLines.size() <= 1) {
            return initialLines;
        }

        List<FormattedCharSequence> wrappedLines = wrapAtWidth.apply(Math.max(1, maxWidth - prefixWidth));
        List<FormattedCharSequence> prefixedLines = new ArrayList<>(wrappedLines.size());
        for (int index = 0; index < wrappedLines.size(); index++) {
            FormattedCharSequence line = wrappedLines.get(index);
            prefixedLines.add(index == 0
                    ? line
                    : FormattedCharSequence.composite(prefix, withoutVanillaContinuationIndent(line)));
        }
        return List.copyOf(prefixedLines);
    }

    /**
     * {@code message} kept to one visual line, cut to fit and ended with an ellipsis,
     * for a line that only previews something and would read worse wrapped: the quote
     * above a bridged reply. {@code initialLines} is how Minecraft wrapped it.
     */
    public static List<FormattedCharSequence> keepToOneLine(
            List<FormattedCharSequence> initialLines, GuiMessage message, Font font, int maxWidth) {
        if (initialLines.size() <= 1) {
            return initialLines;
        }
        return List.of(Language.getInstance().getVisualOrder(cutToWidth(message.content(), maxWidth, font.getSplitter())));
    }

    /** {@code text} cut to fit {@code maxWidth} with its ellipsis, styled as the text it follows. */
    static FormattedText cutToWidth(FormattedText text, int maxWidth, StringSplitter splitter) {
        if (splitter.stringWidth(text) <= maxWidth) {
            return text;
        }
        Component ellipsis = Component.literal(ELLIPSIS);
        FormattedText head = splitter.headByWidth(text, Math.max(0, maxWidth - (int) Math.ceil(splitter.stringWidth(ellipsis))), Style.EMPTY);
        Style[] last = {Style.EMPTY};
        head.visit(
                (style, piece) -> {
                    if (!piece.isBlank()) {
                        last[0] = style;
                    }
                    return Optional.empty();
                },
                Style.EMPTY);
        return FormattedText.composite(head, FormattedText.of(ELLIPSIS, last[0]));
    }

    /** Minecraft prepends one plain space to every automatically wrapped continuation. */
    private static FormattedCharSequence withoutVanillaContinuationIndent(FormattedCharSequence line) {
        return sink -> {
            boolean[] first = {true};
            return line.accept((index, style, codePoint) -> {
                if (first[0]) {
                    first[0] = false;
                    if (codePoint == ' ') {
                        return true;
                    }
                }
                return sink.accept(index, style, codePoint);
            });
        };
    }
}
