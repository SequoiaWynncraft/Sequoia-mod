package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.seqwawa.seq.model.DiscordRank;
import com.seqwawa.seq.model.RankPresentation;
import com.seqwawa.seq.model.SeqPointsShopEffect;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.ComponentTextEditor;
import com.seqwawa.seq.utils.WynnPillGlyphs;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class SeqPointsChatAliasDecoratorTest {

    private static final SeqPointsShopEffect EFFECT = new SeqPointsShopEffect(
            1,
            "00000000-0000-4000-8000-000000000001",
            "RealPlayer",
            "New Alias",
            Instant.parse("2026-08-29T10:00:00Z"),
            Instant.parse("2026-08-29T10:15:00Z"));

    @Test
    void aliasesAPlayerAcrossTimestampedChannelChat() {
        Style identity = Style.EMPTY
                .withColor(0x4CB4FA)
                .withInsertion("RealPlayer")
                .withClickEvent(new ClickEvent.SuggestCommand("/msg RealPlayer "))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Player profile")));
        Component message = Component.empty()
                .append(Component.literal("[12:34:56] "))
                .append(Component.literal("RealPlayer").withStyle(identity))
                .append(Component.literal(": hello"));

        Component decorated = SeqPointsChatAliasDecorator.decorate(message, SeqPointsChatAliasDecoratorTest::lookup);

        assertEquals("[12:34:56] New Alias (RealPlayer): hello", decorated.getString());
        Style aliasStyle = styleOn(decorated, "New Alias");
        assertEquals(0x4CB4FA, aliasStyle.getColor().getValue());
        assertEquals("RealPlayer", aliasStyle.getInsertion());
        assertEquals(new ClickEvent.SuggestCommand("/msg RealPlayer "), aliasStyle.getClickEvent());
        assertEquals("Player profile", ((HoverEvent.ShowText) aliasStyle.getHoverEvent()).value().getString());
    }

    @Test
    void rankDecorationPaintsTheAliasAcrossTheOriginalUsernameGradient() {
        RankPresentation gradient = new RankPresentation(
                new DiscordRank("rank.druid", "Druid", 92),
                ColorRamp.of(List.of(0x123456, 0xFFFFFF)));
        Component message = Component.empty()
                .append(Component.literal(WynnPillGlyphs.encodePlainPill("recruit") + " "))
                .append(Component.literal("RealPlayer")
                        .withStyle(Style.EMPTY.withInsertion("RealPlayer")))
                .append(Component.literal(": hello"));

        Component aliased = SeqPointsChatAliasDecorator.decorate(
                message, SeqPointsChatAliasDecoratorTest::lookup);
        Component decorated = DiscordRankChatDecorator.decorateGuildChat(aliased, ignored -> gradient);
        List<ComponentTextEditor.Fragment> identity = ComponentTextEditor.flatten(decorated).stream()
                .filter(fragment -> "RealPlayer".equals(fragment.style().getInsertion()))
                .toList();

        assertEquals(
                "New Alias (RealPlayer)",
                identity.stream().map(ComponentTextEditor.Fragment::text).reduce("", String::concat));
        assertEquals(0x123456, identity.getFirst().style().getColor().getValue());
        assertEquals(0xFFFFFF, identity.get("New Alias".length() - 1).style().getColor().getValue());
        assertEquals(
                ChatFormatting.GRAY.getColor(),
                identity.get("New Alias".length()).style().getColor().getValue());
    }

    @Test
    void resolvesANicknameFromItsRealUsernameHover() {
        Style nickname = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(
                Component.literal("Knight's real name is RealPlayer")));
        Component message = Component.empty()
                .append(Component.literal("Knight").withStyle(nickname))
                .append(Component.literal(": ready"));

        Component decorated = SeqPointsChatAliasDecorator.decorate(message, SeqPointsChatAliasDecoratorTest::lookup);

        assertEquals("New Alias (RealPlayer): ready", decorated.getString());
    }

    @Test
    void ignoresInsertionThatDoesNotMatchTheVisibleSpeaker() {
        Component message = Component.empty()
                .append(Component.literal("OtherPlayer")
                        .withStyle(Style.EMPTY.withInsertion("RealPlayer")))
                .append(Component.literal(": hello"));
        Component pollutedPrefix = Component.empty()
                .append(Component.literal("[rank]")
                        .withStyle(Style.EMPTY.withInsertion("RealPlayer")))
                .append(Component.literal(" OtherPlayer: hello"));

        assertSame(
                message,
                SeqPointsChatAliasDecorator.decorate(
                        message, SeqPointsChatAliasDecoratorTest::lookup));
        assertSame(
                pollutedPrefix,
                SeqPointsChatAliasDecorator.decorate(
                        pollutedPrefix, SeqPointsChatAliasDecoratorTest::lookup));
    }

    @Test
    void replacesTheVisibleNameRatherThanAnEarlierRankPillIdentity() {
        Style identity = Style.EMPTY.withInsertion("RealPlayer");
        Component message = Component.empty()
                .append(Component.literal("[rank]").withStyle(identity))
                .append(Component.literal(" "))
                .append(Component.literal("Real").withStyle(identity.withColor(0x112233)))
                .append(Component.literal("Player").withStyle(identity.withColor(0x445566)))
                .append(Component.literal(": hi"));

        Component decorated = SeqPointsChatAliasDecorator.decorate(message, SeqPointsChatAliasDecoratorTest::lookup);

        assertEquals("[rank] New Alias (RealPlayer): hi", decorated.getString());
    }

    @Test
    void aliasesBothPlayerIdentitiesInPrivateChatWithoutTouchingTheBody() {
        SeqPointsShopEffect second = new SeqPointsShopEffect(
                2,
                "00000000-0000-4000-8000-000000000002",
                "OtherPlayer",
                "Other Alias",
                EFFECT.startsAt(),
                EFFECT.endsAt());
        Component message = Component.empty()
                .append(Component.literal("RealPlayer").withStyle(Style.EMPTY.withInsertion("RealPlayer")))
                .append(Component.literal(" -> "))
                .append(Component.literal("OtherPlayer").withStyle(Style.EMPTY.withInsertion("OtherPlayer")))
                .append(Component.literal(": RealPlayer stays body text"));

        Component decorated = SeqPointsChatAliasDecorator.decorate(message, username -> switch (username.toLowerCase(Locale.ROOT)) {
            case "realplayer" -> EFFECT;
            case "otherplayer" -> second;
            default -> null;
        });

        assertEquals(
                "New Alias (RealPlayer) -> Other Alias (OtherPlayer): RealPlayer stays body text",
                decorated.getString());
    }

    @Test
    void supportsVanillaAngleBracketChat() {
        Component message = Component.empty()
                .append(Component.literal("<"))
                .append(Component.literal("RealPlayer"))
                .append(Component.literal("> hello"));

        Component decorated = SeqPointsChatAliasDecorator.decorate(message, SeqPointsChatAliasDecoratorTest::lookup);

        assertEquals("<New Alias (RealPlayer)> hello", decorated.getString());
    }

    @Test
    void resolvesPlainCanonicalSpeakersWithoutMatchingTheBody() {
        Component plain = Component.literal("RealPlayer: hello");
        Component prefixed = Component.literal("[12:34:56] "
                + WynnPillGlyphs.encodePlainPill("recruit") + " RealPlayer: hello");
        Component bodyOnly = Component.literal("OtherPlayer: quoting RealPlayer: hello");

        assertEquals(
                "New Alias (RealPlayer): hello",
                SeqPointsChatAliasDecorator.decorate(plain, SeqPointsChatAliasDecoratorTest::lookup).getString());
        assertEquals(
                "[12:34:56] " + WynnPillGlyphs.encodePlainPill("recruit")
                        + " New Alias (RealPlayer): hello",
                SeqPointsChatAliasDecorator.decorate(prefixed, SeqPointsChatAliasDecoratorTest::lookup).getString());
        assertSame(
                bodyOnly,
                SeqPointsChatAliasDecorator.decorate(bodyOnly, SeqPointsChatAliasDecoratorTest::lookup));
    }

    @Test
    void aStyledBodyMentionDoesNotHideAPlainSpeaker() {
        Component message = Component.empty()
                .append(Component.literal("RealPlayer: hello "))
                .append(Component.literal("OtherPlayer")
                        .withStyle(Style.EMPTY.withInsertion("OtherPlayer")))
                .append(Component.literal(": quoted text"));

        Component decorated = SeqPointsChatAliasDecorator.decorate(
                message, SeqPointsChatAliasDecoratorTest::lookup);

        assertEquals(
                "New Alias (RealPlayer): hello OtherPlayer: quoted text",
                decorated.getString());
    }

    @Test
    void doesNotTreatADiscordBridgeDisplayNameAsAMinecraftIdentity() {
        Component bridge = ChatManager.bridgeSenderLine(
                new ConnectionManager.DiscordChatMessage("RealPlayer", "hello"), "hello", null);
        AtomicReference<Component> decorated = new AtomicReference<>();

        DiscordRankChatDecorator.displayUndecorated(
                bridge,
                () -> decorated.set(SeqPointsChatAliasDecorator.decorate(
                        bridge, SeqPointsChatAliasDecoratorTest::lookup)),
                false);

        assertSame(bridge, decorated.get());
    }

    @Test
    void aWorldShapedAliasRemainsAPlayerIdentityInsteadOfAWorldLink() {
        SeqPointsShopEffect worldAlias = new SeqPointsShopEffect(
                EFFECT.id(),
                EFFECT.targetPlayerUuid(),
                EFFECT.targetUsername(),
                "EU7",
                EFFECT.startsAt(),
                EFFECT.endsAt());
        Style hoverOnlyIdentity = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(
                Component.literal("Knight's real name is RealPlayer")));
        Component message = Component.empty()
                .append(Component.literal("Knight").withStyle(hoverOnlyIdentity))
                .append(Component.literal(": ready"));

        Component aliased = SeqPointsChatAliasDecorator.decorate(message, ignored -> worldAlias);
        Component linked = WorldSwitchChatDecorator.decorate(aliased, Set.of("EU"), true);

        Style aliasStyle = styleOn(linked, "EU7");
        assertEquals("RealPlayer", aliasStyle.getInsertion());
        assertEquals(null, aliasStyle.getClickEvent());
    }

    @Test
    void leavesSystemLinesAndUnaffectedPlayersUntouched() {
        Component system = Component.literal("Server restart in 5 minutes: please finish up");
        Component other = Component.empty()
                .append(Component.literal("OtherPlayer").withStyle(Style.EMPTY.withInsertion("OtherPlayer")))
                .append(Component.literal(": hello"));

        assertSame(system, SeqPointsChatAliasDecorator.decorate(system, SeqPointsChatAliasDecoratorTest::lookup));
        assertSame(other, SeqPointsChatAliasDecorator.decorate(other, SeqPointsChatAliasDecoratorTest::lookup));
    }

    private static SeqPointsShopEffect lookup(String username) {
        return "realplayer".equalsIgnoreCase(username) ? EFFECT : null;
    }

    private static Style styleOn(Component message, String text) {
        return ComponentTextEditor.flatten(message).stream()
                .filter(fragment -> fragment.text().contains(text))
                .findFirst()
                .orElseThrow()
                .style();
    }
}
