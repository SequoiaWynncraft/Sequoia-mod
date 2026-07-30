package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import com.seqwawa.seq.integrations.WynntilsGuildRankAccess;

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
    void parseGuildMessageHandlesWynnPrestigePrefixBeforeSpeaker() {
        Component message = Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("󏿿󏿿󏿿󏿿󏿿󏿿󏿿󏿿󏿿󏿿󏿿󏿄"))
                .append(Component.literal("󐀂 "))
                .append(Component.literal("<1>Commander Lilacs").withStyle(Style.EMPTY.withInsertion("RealLilacs")))
                .append(Component.literal(": tna/wtp 1/4"));

        ChatManager.ParsedMessage parsed = ChatManager.parseGuildMessage(message);

        assertNotNull(parsed);
        assertEquals("RealLilacs", parsed.username());
        assertEquals("Commander Lilacs", parsed.nickname());
        assertEquals("tna/wtp 1/4", parsed.message());
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
    void parseGuildMessagePreservesWrappedWynnBuilderUrl() {
        Component message = Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("Purprated").withStyle(Style.EMPTY.withInsertion("Purprated")))
                .append(Component.literal(
                        ": https://wynnbuilder.github.io/builder/#CW0R3\n"
                                + "󏿼󐀆 FvSpicp9HS4xaJbHgCI15MFoupRwBzpB+rKNFq0"));

        ChatManager.ParsedMessage parsed = ChatManager.parseGuildMessage(message);

        assertNotNull(parsed);
        assertEquals(
                "https://wynnbuilder.github.io/builder/#CW0R3"
                        + "FvSpicp9HS4xaJbHgCI15MFoupRwBzpB+rKNFq0",
                parsed.message());
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
    void parseAllianceUpdateHandlesFormedSystemMessage() {
        ChatManager.ParsedAllianceUpdate parsed = ChatManager.parseAllianceUpdate(Component.literal(
                "󏿼󏿿󏿾 Sequoia formed an alliance with Silk Road"));

        assertNotNull(parsed);
        assertEquals("formed", parsed.action());
        assertEquals("Silk Road", parsed.guildName());
    }

    @Test
    void parseAllianceUpdateHandlesOtherGuildRevokedHomeGuild() {
        ChatManager.ParsedAllianceUpdate parsed = ChatManager.parseAllianceUpdate(Component.literal(
                "󏿼󐀆 Anime Lovers revoked the alliance with Sequoia"));

        assertNotNull(parsed);
        assertEquals("revoked", parsed.action());
        assertEquals("Anime Lovers", parsed.guildName());
    }

    @Test
    void parseAllianceUpdateHandlesHomeGuildPlayerRevokedOtherGuild() {
        ChatManager.ParsedAllianceUpdate parsed = ChatManager.parseAllianceUpdate(Component.literal(
                "󏿼󏿿󏿾 GaztheCat revoked the alliance with Chiefs Of Corkus"));

        assertNotNull(parsed);
        assertEquals("revoked", parsed.action());
        assertEquals("Chiefs Of Corkus", parsed.guildName());
    }

    @Test
    void parseAllianceUpdateIgnoresUnrelatedFormedAllianceSystemMessage() {
        assertNull(ChatManager.parseAllianceUpdate(Component.literal(
                "󏿼󏿿󏿾 Tannslee formed an alliance with Radiant Roses")));
    }

    @Test
    void parseAllianceUpdateRejectsPlayerAuthoredChat() {
        assertNull(ChatManager.parseAllianceUpdate(Component.literal(
                "Frieren: Sequoia formed an alliance with Silk Road")));
        assertNull(ChatManager.parseAllianceUpdate(Component.literal(
                "󏿼󐀆 Frieren: Sequoia formed an alliance with Silk Road")));
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
    void dropsGuildChatWhenWynntilsMembershipIsUnavailable() {
        WynntilsGuildRankAccess.GuildMembership membership =
                new WynntilsGuildRankAccess.GuildMembership(false, false, null);

        assertFalse(ChatManager.shouldRelayForGuild(membership));
    }

    @Test
    void dropsGuildChatWhenWynntilsMembershipIsMissing() {
        assertFalse(ChatManager.shouldRelayForGuild(null));
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

    @Test
    void detectsGuildChatWhenOnlyLeadingFragmentIsGuildColored() {
        Component message = Component.empty()
                .append(Component.literal("󏿼󐀆 ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("ilyhug: what").withStyle(ChatFormatting.DARK_AQUA));

        assertTrue(ChatManager.hasLeadingGuildChatColor(message));
    }

    @Test
    void rejectsInfoChatWithLaterGuildColoredFragment() {
        Component message = Component.empty()
                .append(Component.literal("󏿼󏿿󏿾 Party Finder: ").withStyle(ChatFormatting.DARK_PURPLE))
                .append(Component.literal("The Nameless Anomaly").withStyle(ChatFormatting.AQUA));

        assertFalse(ChatManager.hasLeadingGuildChatColor(message));
    }
}
