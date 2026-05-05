package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import org.sequoia.seq.integrations.WynntilsGuildRankAccess;

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

    @Test
    void detectsUpdatedWynncraftWelcomeBanner() {
        Component message = Component.literal(
                "\n󐁙Welcome to Wynncraft!\n󐀻play.wynncraft.com -/- wynncraft.com\n\n󐁄WYNNCRAFT: FRUMA EXPANSION\n󐂁OUT NOW!\n󐂚\n󐀲Discover Fruma: wynncraft.com/fruma");

        assertTrue(ChatManager.isWynncraftWelcomeMessage(message));
    }

    @Test
    void doesNotTreatOrdinaryChatMentionAsWelcomeBanner() {
        Component message = Component.literal("Frieren: Welcome to Wynncraft! meet me on EU7");

        assertFalse(ChatManager.isWynncraftWelcomeMessage(message));
    }

    @Test
    void relaysGuildChatWhenWynntilsMembershipIsUnavailable() {
        WynntilsGuildRankAccess.GuildMembership membership =
                new WynntilsGuildRankAccess.GuildMembership(false, false, null);

        assertTrue(ChatManager.shouldRelayForGuild(membership));
    }

    @Test
    void relaysGuildChatForExpectedWynntilsGuild() {
        WynntilsGuildRankAccess.GuildMembership membership =
                new WynntilsGuildRankAccess.GuildMembership(true, true, "Sequoia");

        assertTrue(ChatManager.shouldRelayForGuild(membership));
    }

    @Test
    void dropsGuildChatForOtherKnownWynntilsGuild() {
        WynntilsGuildRankAccess.GuildMembership membership =
                new WynntilsGuildRankAccess.GuildMembership(true, false, "Other Guild");

        assertFalse(ChatManager.shouldRelayForGuild(membership));
    }
}
