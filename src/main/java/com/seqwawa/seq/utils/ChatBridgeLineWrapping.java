package com.seqwawa.seq.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Adds aligned Discord rails to visual lines created by Minecraft's chat wrapping. */
public final class ChatBridgeLineWrapping {

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
