package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class ChatManagerTest {

    @Test
    void parseGuildMessageHandlesPacketGlyphsAndNicknameRealNameSplit() {
        Component message = Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("󏿿󏿿󏿿󏿿󏿿󏿿󏿢󐀂 "))
                .append(Component.literal("Emanant Force").withStyle(Style.EMPTY.withInsertion("Purprated")))
                .append(Component.literal(": any raids?"));

        ChatManager.ParsedMessage parsed = ChatManager.parseGuildMessage(message);

        assertNotNull(parsed);
        assertEquals("Purprated", parsed.username());
        assertEquals("Emanant Force", parsed.nickname());
        assertEquals("any raids?", parsed.message());
    }

    @Test
    void parseGuildMessageHandlesDirectUsernameWithoutNickname() {
        ChatManager.ParsedMessage parsed = ChatManager.parseGuildMessage(Component.literal(
                "󏿼󐀆 xmattypazox: 3/4 tna"));

        assertNotNull(parsed);
        assertEquals("xmattypazox", parsed.username());
        assertNull(parsed.nickname());
        assertEquals("3/4 tna", parsed.message());
    }

    @Test
    void parseGuildMessageHandlesMultilineContentAndWeirdSpacing() {
        Component message = Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("teslaco").withStyle(Style.EMPTY.withInsertion("a3pki")))
                .append(Component.literal(": tna tna tna 3/\n󏿼󐀆 4 3 out of 4"));

        ChatManager.ParsedMessage parsed = ChatManager.parseGuildMessage(message);

        assertNotNull(parsed);
        assertEquals("a3pki", parsed.username());
        assertEquals("teslaco", parsed.nickname());
        assertEquals("tna tna tna 3/4 3 out of 4", parsed.message());
    }

    @Test
    void parseGuildMessageHandlesHoverBasedRealNameFallback() {
        Style style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(
                Component.literal("teslaco's real name is a3pki")));
        Component message = Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("teslaco").withStyle(style))
                .append(Component.literal(": meow"));

        ChatManager.ParsedMessage parsed = ChatManager.parseGuildMessage(message);

        assertNotNull(parsed);
        assertEquals("a3pki", parsed.username());
        assertEquals("teslaco", parsed.nickname());
        assertEquals("meow", parsed.message());
    }

    @Test
    void parseGuildMessageIgnoresGuildSystemMessagesWithoutSpeakerColon() {
        assertNull(ChatManager.parseGuildMessage(Component.literal(
                "󏿼󏿿󏿾 Territory Gelibord is producing more resources than it\n󏿼󐀆 can store!")));
        assertNull(ChatManager.parseGuildMessage(Component.literal(
                "󏿼󐀆 Purprated rewarded 1024 Emeralds to cinfrascitizen")));
    }
}
