package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.seqwawa.seq.managers.DiscordRankChatDecorator;
import com.seqwawa.seq.managers.GuildChatMarkers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

class ChatBridgeLineWrappingTest {

    @Test
    void automaticWrapsReserveWidthAndReceiveContinuationSidebars() {
        List<FormattedCharSequence> initialLines = List.of(sequence("first"), sequence(" second"));
        AtomicInteger requestedWidth = new AtomicInteger();

        List<FormattedCharSequence> result = ChatBridgeLineWrapping.addAutomaticContinuationPrefixes(
                initialLines,
                100,
                12,
                width -> {
                    requestedWidth.set(width);
                    return List.of(sequence("first"), sequence(" second"), sequence(" third"));
                },
                sequence("| "));

        assertEquals(88, requestedWidth.get());
        assertEquals(List.of("first", "| second", "| third"), result.stream()
                .map(ChatBridgeLineWrappingTest::textOf)
                .toList());
    }

    @Test
    void automaticSidebarAvoidsDoubleTintAndMatchesIconShadow() {
        GuildChatMarkers.reset();
        Component bridgeLine = Component.literal("styled bridge line");
        DiscordRankChatDecorator.displayUndecorated(bridgeLine, () -> {});
        Component prefix = DiscordRankChatDecorator.bridgeContinuationPrefixFor(bridgeLine);
        assertNotNull(prefix);

        List<FormattedCharSequence> result = ChatBridgeLineWrapping.addAutomaticContinuationPrefixes(
                List.of(sequence("first"), sequence(" second")),
                100,
                12,
                ignored -> List.of(sequence("first"), sequence(" second")),
                prefix.getVisualOrderText());

        int prefixCodePoint = prefix.getString().codePointAt(0);
        Style prefixStyle = styledCodePoints(result.get(1)).stream()
                .filter(codePoint -> codePoint.value() == prefixCodePoint)
                .findFirst()
                .orElseThrow()
                .style();
        // The bitmap itself is already #5865F2, unlike the white Discord mark.
        // White styling preserves that exact color instead of multiplying it twice.
        assertEquals(0xFFFFFF, prefixStyle.getColor().getValue());
        assertEquals(
                new FontDescription.Resource(Identifier.fromNamespaceAndPath("seq", "discord_bridge")),
                prefixStyle.getFont());
        assertNull(prefixStyle.getShadowColor());
    }

    @Test
    void singleVisualLineIsReturnedWithoutASecondWrap() {
        List<FormattedCharSequence> initialLines = List.of(sequence("short"));

        List<FormattedCharSequence> result = ChatBridgeLineWrapping.addAutomaticContinuationPrefixes(
                initialLines,
                100,
                12,
                ignored -> {
                    throw new AssertionError("a single line must not be wrapped again");
                },
                sequence("| "));

        assertSame(initialLines, result);
    }

    @Test
    void onlyStyledSequoiaBridgeMarkersTriggerAutomaticSidebars() {
        Component continuationPrefix = DiscordRankChatDecorator.bridgePrefix();
        Component bridgeLine = Component.empty()
                .append(continuationPrefix)
                .append(Component.literal("message"));

        assertNotNull(DiscordRankChatDecorator.bridgeContinuationPrefixFor(bridgeLine));
        assertNull(DiscordRankChatDecorator.bridgeContinuationPrefixFor(
                Component.literal(continuationPrefix.getString() + "ordinary text")));
        assertNull(DiscordRankChatDecorator.bridgeContinuationPrefixFor(Component.literal("ordinary text")));
    }

    @Test
    void registeredBridgeLinesRetainTheSameStyledPrefixForLaterRewraps() {
        Component bridgeLine = Component.literal("bridge line whose prefix was replaced");

        DiscordRankChatDecorator.displayUndecorated(bridgeLine, () -> {});

        Component retainedPrefix = DiscordRankChatDecorator.bridgeContinuationPrefixFor(bridgeLine);
        assertNotNull(retainedPrefix);
        assertSame(retainedPrefix, DiscordRankChatDecorator.bridgeContinuationPrefixFor(bridgeLine));
    }

    @Test
    void cutsAReplysQuoteToOneLineEndingInAnEllipsis() {
        StringSplitter sixWide = new StringSplitter((codePoint, style) -> 6f);
        Style grey = Style.EMPTY.withColor(0xB5BAC1);
        Component quote = Component.empty()
                .append(Component.literal("Target"))
                .append(Component.literal(" anyone up for a raid?").withStyle(grey));

        FormattedText cut = ChatBridgeLineWrapping.cutToWidth(quote, 66, sixWide);

        // Eleven glyphs of room: eight of the quote, then the three of the ellipsis.
        assertEquals("Target a...", cut.getString());
        assertEquals(grey, lastStyle(cut), "in the colour of the words it cuts short");
        assertSame(quote, ChatBridgeLineWrapping.cutToWidth(quote, 1000, sixWide), "a quote that fits is left whole");
    }

    private static Style lastStyle(FormattedText text) {
        Style[] last = {null};
        text.visit(
                (style, piece) -> {
                    last[0] = style;
                    return Optional.empty();
                },
                Style.EMPTY);
        return last[0];
    }

    private static FormattedCharSequence sequence(String text) {
        return FormattedCharSequence.forward(text, Style.EMPTY);
    }

    private static String textOf(FormattedCharSequence sequence) {
        StringBuilder text = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }

    private static List<StyledCodePoint> styledCodePoints(FormattedCharSequence sequence) {
        List<StyledCodePoint> codePoints = new ArrayList<>();
        sequence.accept((index, style, codePoint) -> {
            codePoints.add(new StyledCodePoint(codePoint, style));
            return true;
        });
        return List.copyOf(codePoints);
    }

    private record StyledCodePoint(int value, Style style) {}
}
