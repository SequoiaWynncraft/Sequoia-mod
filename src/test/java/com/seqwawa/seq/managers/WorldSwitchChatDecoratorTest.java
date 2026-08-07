package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.utils.ComponentTextEditor;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class WorldSwitchChatDecoratorTest {

    private static final Set<String> PREFIXES = Set.of("NA", "EU", "AS");
    /** Wynncraft's guild chat aqua. */
    private static final int GUILD_AQUA = 0x55FFFF;

    @Test
    void linksAWorldNamedInGuildChat() {
        Component message = guildLine("Player: prof world up na6 go go");

        Component decorated = WorldSwitchChatDecorator.decorate(message, PREFIXES, true);

        assertEquals(message.getString(), decorated.getString());
        assertEquals(new ClickEvent.RunCommand("/switch NA6"), clickEventOn(decorated, "na6"));
    }

    @Test
    void linksEveryWorldOnALineListingThem() {
        Component message = Component.literal("Shared bomb worlds: NA6, AS2, EU11");

        Component decorated = WorldSwitchChatDecorator.decorate(message, PREFIXES, true);

        assertEquals(new ClickEvent.RunCommand("/switch NA6"), clickEventOn(decorated, "NA6"));
        assertEquals(new ClickEvent.RunCommand("/switch AS2"), clickEventOn(decorated, "AS2"));
        assertEquals(new ClickEvent.RunCommand("/switch EU11"), clickEventOn(decorated, "EU11"));
    }

    @Test
    void underlinesTheMentionAndKeepsTheTextAndItsColour() {
        Component message = guildLine("go to as2");

        Component decorated = WorldSwitchChatDecorator.decorate(message, PREFIXES, true);

        Style linked = styleOn(decorated, "as2");
        assertTrue(linked.isUnderlined());
        assertEquals(GUILD_AQUA, linked.getColor().getValue());
        assertEquals("go to as2", decorated.getString());
    }

    @Test
    void tellsTheReaderWhatTheClickWillDo() {
        Component decorated = WorldSwitchChatDecorator.decorate(Component.literal("na6"), PREFIXES, true);

        HoverEvent hover = styleOn(decorated, "na6").getHoverEvent();
        assertEquals(
                "Click to switch to NA6\n/switch NA6",
                assertInstanceOf(HoverEvent.ShowText.class, hover).value().getString());
    }

    @Test
    void typesTheCommandInsteadWhenSwitchingOnClickIsOff() {
        Component decorated = WorldSwitchChatDecorator.decorate(Component.literal("na6"), PREFIXES, false);

        Style linked = styleOn(decorated, "na6");
        assertEquals(new ClickEvent.SuggestCommand("/switch NA6"), linked.getClickEvent());
        assertEquals(
                "Click to type /switch NA6",
                assertInstanceOf(HoverEvent.ShowText.class, linked.getHoverEvent())
                        .value()
                        .getString());
    }

    /**
     * Wynncraft leaves legacy colour codes in the text of the components it builds,
     * and one directly in front of a mention must not hide the word boundary.
     */
    @Test
    void linksAMentionSittingBehindALegacyColourCode() {
        Component message = Component.literal("§bna6§7 is fresh");

        Component decorated = WorldSwitchChatDecorator.decorate(message, PREFIXES, true);

        assertEquals(new ClickEvent.RunCommand("/switch NA6"), clickEventOn(decorated, "na6"));
        assertEquals("§bna6§7 is fresh", decorated.getString());
    }

    @Test
    void leavesALineNamingNoWorldExactlyAsItWas() {
        Component message = guildLine("Player: anyone up for tcc");

        assertSame(message, WorldSwitchChatDecorator.decorate(message, PREFIXES, true));
    }

    @Test
    void leavesAPlayerNameAloneEvenWhenItLooksLikeAWorld() {
        Component message = Component.empty()
                .append(Component.literal("NA6").withStyle(Style.EMPTY.withInsertion("NA6")))
                .append(Component.literal(": hi"));

        Component decorated = WorldSwitchChatDecorator.decorate(message, PREFIXES, true);

        assertSame(message, decorated);
    }

    @Test
    void leavesAMentionThatAlreadyCarriesAClickActionAlone() {
        Component message = Component.literal("join ")
                .append(Component.literal("NA6")
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/seq p join 4"))));

        Component decorated = WorldSwitchChatDecorator.decorate(message, PREFIXES, true);

        assertSame(message, decorated);
    }

    @Test
    void ignoresWordsThatOnlyLookLikeWorlds() {
        assertEquals(List.of(), mentionsIn("banana6 na0 na007 na1234 wc3 xy4 pizza"));
    }

    @Test
    void readsAMentionOutOfSurroundingPunctuation() {
        assertEquals(List.of("NA6", "AS2", "EU3"), worldsIn("(na6), as2! eu3."));
    }

    @Test
    void recognisesAWorldPrefixThisBuildDoesNotKnowOnceItIsPlayedOn() {
        assertEquals(List.of(), worldsIn("sa4"));
        assertEquals(List.of("SA4"), mentionsIn("sa4", Set.of("SA")).stream()
                .map(WorldSwitchChatDecorator.WorldMention::world)
                .toList());
        assertEquals("SA", WorldSwitchChatDecorator.prefixOf("sa4"));
        assertNull(WorldSwitchChatDecorator.prefixOf("WC??"));
    }

    private static List<WorldSwitchChatDecorator.WorldMention> mentionsIn(String text) {
        return mentionsIn(text, PREFIXES);
    }

    private static List<WorldSwitchChatDecorator.WorldMention> mentionsIn(String text, Set<String> prefixes) {
        return WorldSwitchChatDecorator.findWorldMentions(text, prefixes);
    }

    private static List<String> worldsIn(String text) {
        return mentionsIn(text).stream()
                .map(WorldSwitchChatDecorator.WorldMention::world)
                .toList();
    }

    private static Component guildLine(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA));
    }

    private static ClickEvent clickEventOn(Component message, String text) {
        return styleOn(message, text).getClickEvent();
    }

    /** Style of the fragment holding {@code text}, which restyling splits out on its own. */
    private static Style styleOn(Component message, String text) {
        return ComponentTextEditor.flatten(message).stream()
                .filter(fragment -> fragment.text().equals(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No fragment holding '" + text + "' in: " + message.getString()))
                .style();
    }
}
