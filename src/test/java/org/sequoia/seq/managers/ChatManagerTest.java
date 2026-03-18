package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.network.chat.Component;
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
}
