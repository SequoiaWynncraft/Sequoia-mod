package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.seqwawa.seq.managers.DiscordRankChatDecorator;
import com.seqwawa.seq.managers.GuildChatMarkers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Component;
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
    void automaticSidebarKeepsItsFontColorAndShadowStyle() {
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
        assertEquals(0x5865F2, prefixStyle.getColor().getValue());
        assertEquals(
                new FontDescription.Resource(Identifier.fromNamespaceAndPath("seq", "discord_bridge")),
                prefixStyle.getFont());
        assertEquals(Style.NO_SHADOW, prefixStyle.getShadowColor());
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
