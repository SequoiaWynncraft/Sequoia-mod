package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.model.DiscordRank;
import com.seqwawa.seq.model.SeqBadgeTier;
import com.seqwawa.seq.model.RankPresentation;
import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.ComponentTextEditor;
import com.seqwawa.seq.utils.WynnPillGlyphs;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordRankChatDecoratorTest {

    /** Captured markers are global state; clear them so test order cannot matter. */
    @BeforeEach
    void forgetCapturedMarkers() {
        GuildChatMarkers.reset();
    }

    private static final RankPresentation SAPLING = presentation("rank.sapling", "Sapling", 88, 0x4CB4FA);
    private static final Map<String, RankPresentation> RANKS = Map.of("arcleretour", SAPLING);
    /** Wynncraft's guild chat aqua. */
    private static final int GUILD_AQUA = 0x55FFFF;
    private static final int DARK_AQUA = 0x00AAAA;

    // Shapes taken from a real guild line (latest.log): Wynncraft draws the guild
    // icon in one font, the rank badge in another, and the text in the default one.
    // The icon mixes supplementary-plane and BMP glyphs, so only the font tells the
    // two decorations apart.
    private static final FontDescription ICON_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("wynncraft", "cp"));
    private static final FontDescription BADGE_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("wynncraft", "bp"));
    private static final String ICON_GLYPHS = "\uDAFF\uDFFC\uE01E\uDBFF\uDFFF\uE002";
    private static final String BADGE_GEOMETRY = "\uE001 \uE002\uE003";
    private static final String LEGACY_RED = "\u00A7c";
    private static final String LEGACY_RESET = "\u00A7f";

    @Test
    void replacesTheWynncraftGuildRankWithTheLinkedDiscordRank() {
        Component message = guildLine("RECRUITER", "EightySix(ArcLeRetour)", "ArcLeRetour", "=')");

        Component decorated = DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup);

        assertEquals(List.of("sapling"), pillLabels(decorated));
        assertTrue(decorated.getString().contains("EightySix(ArcLeRetour): =')"));
    }

    @Test
    void keepsShiftClickInsertionOfTheSpeakerName() {
        Component decorated = DiscordRankChatDecorator.decorateGuildChat(
                guildLine("RECRUITER", "EightySix(ArcLeRetour)", "ArcLeRetour", "hi"), DiscordRankChatDecoratorTest::lookup);

        assertTrue(ComponentTextEditor.flatten(decorated).stream()
                .anyMatch(fragment -> "ArcLeRetour".equals(fragment.style().getInsertion())));
    }

    @Test
    void resolvesSpeakersThatChatUnderTheirPlainUsername() {
        Component message = guildLine("RECRUIT", "ArcLeRetour", null, "no nickname here");

        assertEquals(List.of("sapling"), pillLabels(DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup)));
    }

    @Test
    void leavesTheLineAloneWhenTheSpeakerHasNoLinkedRank() {
        Component message = guildLine("RECRUITER", "SomeoneElse", "SomeoneElse", "hello");

        assertSame(message, DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup));
    }

    @Test
    void leavesNonGuildPillsSuchAsAccountRanksAlone() {
        Component message = guildLine("CHAMPION", "ArcLeRetour", "ArcLeRetour", "trading");

        assertSame(message, DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup));
    }

    @Test
    void ignoresPlayersMentionedInsideTheMessageBody() {
        Component message = Component.empty()
                .append(Component.literal(WynnPillGlyphs.encodePlainPill("RECRUITER") + " "))
                .append(Component.literal("SomeoneElse").withStyle(Style.EMPTY.withInsertion("SomeoneElse")))
                .append(Component.literal(": ping "))
                .append(Component.literal("ArcLeRetour").withStyle(Style.EMPTY.withInsertion("ArcLeRetour")));

        assertSame(message, DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup));
    }

    @Test
    void readsGuildRanksEvenWhenTheBadgeCarriesAStrayGlyph() {
        assertEquals("recruiter", DiscordRankChatDecorator.guildRankOf("xrecruiter"));
        assertEquals("recruiter", DiscordRankChatDecorator.guildRankOf("RECRUITER"));
        assertEquals("recruit", DiscordRankChatDecorator.guildRankOf("recruit"));
        assertNull(DiscordRankChatDecorator.guildRankOf("champion"));
        assertNull(DiscordRankChatDecorator.guildRankOf(null));
    }

    @Test
    void fallsBackToBadgePositionWhenTheLabelCannotBeDecoded() {
        // Glyphs outside the known letter range: unreadable, but still a badge.
        Component message = Component.empty()
                .append(Component.literal("[13:43:41] "))
                .append(Component.literal("\uE07A\uE0FF\uE07B "))
                .append(Component.literal("ArcLeRetour")
                        .withStyle(Style.EMPTY.withColor(GUILD_AQUA).withInsertion("ArcLeRetour")))
                .append(Component.literal(": t").withStyle(Style.EMPTY.withColor(GUILD_AQUA)));

        Component decorated =
                DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup);

        assertEquals(List.of("sapling"), pillLabels(decorated));
        assertTrue(decorated.getString().contains("ArcLeRetour: t"));
    }

    @Test
    void leavesOurOwnBridgeLineAloneWhenItComesBackForDecoration() {
        // Only the most recent bridged line is remembered by identity, so an earlier one
        // can reach the decorator. Our marker sits in the same private-use block as
        // Wynncraft's badges and the line carries guild aqua, which is exactly what the
        // position fallback looks for: it would replace our own marker with a pill.
        Component bridged = Component.empty()
                .append(Component.literal(DiscordRankChatDecorator.BRIDGE_ICON_GLYPH + " ")
                        .withStyle(Style.EMPTY.withColor(GUILD_AQUA)))
                .append(Component.literal("ArcLeRetour")
                        .withStyle(Style.EMPTY.withColor(GUILD_AQUA).withInsertion("ArcLeRetour")))
                .append(Component.literal(": hi").withStyle(Style.EMPTY.withColor(GUILD_AQUA)));

        assertSame(
                bridged, DiscordRankChatDecorator.decorateGuildChat(bridged, DiscordRankChatDecoratorTest::lookup));
    }

    @Test
    void keepsAnInsigniaItAlreadyInsertedInsteadOfEatingIt() {
        // A decorated line carries our insignia just before the ':'. Re-decorating must
        // not treat that glyph as part of a Wynncraft badge.
        Component decorated = Component.empty()
                .append(Component.literal(WynnPillGlyphs.encodePlainPill("RECRUITER") + " "))
                .append(Component.literal("ArcLeRetour")
                        .withStyle(Style.EMPTY.withColor(GUILD_AQUA).withInsertion("ArcLeRetour")))
                .append(Component.literal(""))
                .append(Component.literal(": hi").withStyle(Style.EMPTY.withColor(GUILD_AQUA)));

        String result = DiscordRankChatDecorator.decorateGuildChat(
                        decorated, DiscordRankChatDecoratorTest::lookup)
                .getString();

        assertTrue(result.contains(""), "the insignia must survive: " + describe(result));
    }

    private static String describe(String text) {
        return DiscordRankChatDecorator.describeCodepoints(text);
    }

    @Test

    void positionFallbackStaysOffChatThatIsNotGuildChat() {
        // Same shape without the guild aqua: a global-chat account badge must survive.
        Component message = Component.empty()
                .append(Component.literal("\uE07A\uE0FF\uE07B "))
                .append(Component.literal("ArcLeRetour").withStyle(Style.EMPTY.withInsertion("ArcLeRetour")))
                .append(Component.literal(": selling stuff"));

        assertSame(message, DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup));
    }

    @Test
    void leavesTheSeparatingSpaceInTheDefaultFont() {
        // In the marker's font there is no space glyph, so inheriting it would draw
        // Minecraft's missing-character box right next to the logo.
        List<ComponentTextEditor.Fragment> fragments =
                ComponentTextEditor.flatten(DiscordRankChatDecorator.bridgePrefix());

        ComponentTextEditor.Fragment space = fragments.getLast();
        assertEquals(" ", space.text());
        assertEquals(FontDescription.DEFAULT, space.style().getFont());
    }

    @Test
    void capturesOnlyTheMarkerFontAndNotAModsTimestamp() {
        DiscordRankChatDecorator.decorateGuildChat(
                wynncraftGuildLine("RECRUITER", "EightySix", "ArcLeRetour", "t"),
                DiscordRankChatDecoratorTest::lookup);

        assertNotNull(GuildChatMarkers.arrow());
        assertEquals(ICON_GLYPHS, GuildChatMarkers.arrow().glyphs());
        assertEquals(ICON_FONT, GuildChatMarkers.arrow().style().getFont());
    }

    @Test
    void keepsTheGuildIconAndItsFontUntouched() {
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(
                DiscordRankChatDecorator.decorateGuildChat(
                        wynncraftGuildLine("RECRUITER", "EightySix", "ArcLeRetour", "t"),
                        DiscordRankChatDecoratorTest::lookup));

        ComponentTextEditor.Fragment icon = fragments.stream()
                .filter(fragment -> fragment.text().contains(ICON_GLYPHS))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the guild icon must survive"));
        assertEquals(ICON_FONT, icon.style().getFont(), "and keep the font that renders it");
    }

    @Test
    void clearsEveryFragmentDrawnInTheBadgeFont() {
        // Badge geometry is negative space: a surviving piece drags the rest of the
        // line left rather than merely showing.
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(
                DiscordRankChatDecorator.decorateGuildChat(
                        wynncraftGuildLine("RECRUITER", "EightySix", "ArcLeRetour", "t"),
                        DiscordRankChatDecoratorTest::lookup));

        assertTrue(
                fragments.stream().noneMatch(fragment -> BADGE_FONT.equals(fragment.style().getFont())),
                "nothing in the badge font may remain");
        assertFalse(ComponentTextEditor.textOf(fragments).contains(BADGE_GEOMETRY));
    }

    @Test
    void drawsTheNewPillInTheDefaultFont() {
        // The badge font has no glyphs for these codepoints; inheriting it would
        // collapse the pill to no width.
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(
                DiscordRankChatDecorator.decorateGuildChat(
                        wynncraftGuildLine("RECRUITER", "EightySix", "ArcLeRetour", "t"),
                        DiscordRankChatDecoratorTest::lookup));

        List<ComponentTextEditor.Fragment> pillFragments = fragments.stream()
                .filter(fragment -> fragment.text().indexOf(WynnPillGlyphs.CORNER_LEFT) >= 0
                        || fragment.text().indexOf(WynnPillGlyphs.BACKGROUND) >= 0)
                .toList();

        assertFalse(pillFragments.isEmpty(), "the new pill must be there");
        pillFragments.forEach(fragment ->
                assertEquals(FontDescription.DEFAULT, fragment.style().getFont(), "pill glyph font"));
    }

    @Test
    void keepsAPrecedingTimestampWhenTheBadgeSharesTheDefaultFont() {
        // A bridged line: pill and timestamp are both in the default font, so the
        // font boundary alone would run back over the timestamp.
        Component message = Component.empty()
                .append(Component.literal("[19:40:06] "))
                .append(Component.literal(WynnPillGlyphs.encodePlainPill("RECRUITER") + " "))
                .append(Component.literal("ArcLeRetour")
                        .withStyle(Style.EMPTY.withColor(GUILD_AQUA).withInsertion("ArcLeRetour")))
                .append(Component.literal(": aadz").withStyle(Style.EMPTY.withColor(GUILD_AQUA)));

        String decorated = DiscordRankChatDecorator.decorateGuildChat(
                        message, DiscordRankChatDecoratorTest::lookup)
                .getString();

        assertTrue(decorated.startsWith("[19:40:06] "), "the timestamp must survive: " + decorated);
        assertEquals(List.of("sapling"), pillLabels(Component.literal(decorated)));
    }

    @Test
    void glyphRunStartNeverCrossesVisibleText() {
        String text = "[19:40:06] " + WynnPillGlyphs.encodePlainPill("RECRUITER") + " Name: hi";
        int badgeStart = text.indexOf(WynnPillGlyphs.CORNER_LEFT);

        assertEquals(badgeStart, DiscordRankChatDecorator.glyphRunStart(text, badgeStart));
    }

    @Test
    void keepsTheTimestampAndTheMessageIntact() {
        String decorated = DiscordRankChatDecorator.decorateGuildChat(
                        wynncraftGuildLine("RECRUITER", "EightySix", "ArcLeRetour", "t"),
                        DiscordRankChatDecoratorTest::lookup)
                .getString();

        assertTrue(decorated.startsWith("[19:25:32] " + ICON_GLYPHS + " "), decorated);
        assertTrue(decorated.endsWith(" EightySix" + LEGACY_RED + "(ArcLeRetour)" + LEGACY_RESET + ": t"), decorated);
    }

    @Test
    void stopsRecolouringAtTheLegacyCodeThatOpensTheNickReveal() {
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(
                DiscordRankChatDecorator.decorateGuildChat(
                        wynncraftGuildLine("RECRUITER", "EightySix", "ArcLeRetour", "t"),
                        DiscordRankChatDecoratorTest::lookup));

        assertEquals(0x4CB4FA, colorOfFragmentContaining(fragments, "EightySix"));
        assertEquals(
                DARK_AQUA,
                colorOfFragmentContaining(fragments, LEGACY_RED + "(ArcLeRetour)"),
                "the reveal keeps the styling it arrived with, its own §c doing the rest");
    }

    @Test
    void speakerNameEndStopsAtTheFirstLegacyCode() {
        String text = "  EightySix" + LEGACY_RED + "(ArcLeRetour)" + LEGACY_RESET + ": t";
        int colon = text.indexOf(':');

        assertEquals(
                text.indexOf(LEGACY_RED),
                DiscordRankChatDecorator.speakerNameEnd(
                        ComponentTextEditor.flatten(Component.literal(text)), text, 2, colon, "ArcLeRetour"));
    }

    @Test
    void leavesExactlyOneSpaceBetweenThePillAndTheName() {
        Component decorated = DiscordRankChatDecorator.decorateGuildChat(
                guildLine("RECRUITER", "ArcLeRetour", "ArcLeRetour", "hi"), DiscordRankChatDecoratorTest::lookup);

        assertTrue(decorated.getString().endsWith(" ArcLeRetour: hi"));
        assertFalse(decorated.getString().contains("  ArcLeRetour"), "no doubled space");
    }

    @Test
    void paintsTheSpeakerNameWithTheirRankColour() {
        Component decorated = DiscordRankChatDecorator.decorateGuildChat(
                guildLine("RECRUITER", "ArcLeRetour", "ArcLeRetour", "hi"), DiscordRankChatDecoratorTest::lookup);

        assertEquals(
                0x4CB4FA,
                ComponentTextEditor.flatten(decorated).stream()
                        .filter(fragment -> fragment.text().contains("ArcLeRetour"))
                        .findFirst()
                        .orElseThrow()
                        .style()
                        .getColor()
                        .getValue());
    }

    @Test
    void resolvesSpeakersShownAsUsernameSlashNickname() {
        Component message = guildLine("RECRUITER", "ArcLeRetour/EightySix", null, "hi");

        Component decorated =
                DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup);

        assertEquals(List.of("sapling"), pillLabels(decorated));
        assertTrue(decorated.getString().contains("ArcLeRetour/EightySix: hi"));
    }

    @Test
    void resolvesSpeakersWhenTheNicknameComesFirst() {
        // Which half is the account name depends on the add-on, so both are offered and
        // the roster picks.
        Component message = guildLine("RECRUITER", "EightySix/ArcLeRetour", null, "hi");

        assertEquals(
                List.of("sapling"),
                pillLabels(DiscordRankChatDecorator.decorateGuildChat(
                        message, DiscordRankChatDecoratorTest::lookup)));
    }

    @Test
    void recolorsOnlyTheFirstHalfOfASlashSeparatedName() {
        String text = "  ArcLeRetour/EightySix: hi";
        int colon = text.indexOf(':');

        assertEquals(
                text.indexOf('/'),
                DiscordRankChatDecorator.speakerNameEnd(
                        ComponentTextEditor.flatten(Component.literal(text)), text, 2, colon, "ArcLeRetour"));
    }

    @Test

    void leavesTheNickRevealInItsOwnColour() {
        Component message = Component.empty()
                .append(Component.literal(WynnPillGlyphs.encodePlainPill("RECRUITER") + " "))
                .append(Component.literal("EightySix")
                        .withStyle(Style.EMPTY.withColor(GUILD_AQUA).withInsertion("ArcLeRetour")))
                .append(Component.literal("(ArcLeRetour)").withStyle(Style.EMPTY.withColor(0xFF5555)))
                .append(Component.literal(": hi").withStyle(Style.EMPTY.withColor(GUILD_AQUA)));

        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(
                DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup));

        assertEquals(0x4CB4FA, colorOfFragmentContaining(fragments, "EightySix"));
        assertEquals(0xFF5555, colorOfFragmentContaining(fragments, "(ArcLeRetour)"));
    }

    @Test
    void leavesTheNickRevealAloneEvenWhenItIsNotParenthesised() {
        // The reveal is protected by its colour, not by the shape of the text, so it
        // survives whatever delimiters Wynntils happens to render it with.
        Component message = Component.empty()
                .append(Component.literal(WynnPillGlyphs.encodePlainPill("RECRUITER") + " "))
                .append(Component.literal("EightySix")
                        .withStyle(Style.EMPTY.withColor(GUILD_AQUA).withInsertion("ArcLeRetour")))
                .append(Component.literal("[ArcLeRetour]").withStyle(Style.EMPTY.withColor(0xFF5555)))
                .append(Component.literal(": hi").withStyle(Style.EMPTY.withColor(GUILD_AQUA)));

        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(
                DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup));

        assertEquals(0x4CB4FA, colorOfFragmentContaining(fragments, "EightySix"));
        assertEquals(0xFF5555, colorOfFragmentContaining(fragments, "[ArcLeRetour]"));
    }

    @Test
    void leavesAnyDistinctlyColouredPartOfTheNameRegionAlone() {
        Component message = Component.empty()
                .append(Component.literal(WynnPillGlyphs.encodePlainPill("RECRUITER") + " "))
                .append(Component.literal("ArcLeRetour")
                        .withStyle(Style.EMPTY.withColor(GUILD_AQUA).withInsertion("ArcLeRetour")))
                .append(Component.literal(" ★").withStyle(Style.EMPTY.withColor(0xFFAA00)))
                .append(Component.literal(": hi").withStyle(Style.EMPTY.withColor(GUILD_AQUA)));

        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(
                DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup));

        assertEquals(0x4CB4FA, colorOfFragmentContaining(fragments, "ArcLeRetour"));
        assertEquals(0xFFAA00, colorOfFragmentContaining(fragments, "★"));
    }

    @Test
    void recolorsNothingWhenTheNameAndTheRevealShareOneFragment() {
        // No safe cut exists here, so the name stays plain rather than risking the
        // reveal being repainted.
        Component message = Component.empty()
                .append(Component.literal(WynnPillGlyphs.encodePlainPill("RECRUITER") + " "))
                .append(Component.literal("EightySix(ArcLeRetour)")
                        .withStyle(Style.EMPTY.withColor(GUILD_AQUA).withInsertion("ArcLeRetour")))
                .append(Component.literal(": hi").withStyle(Style.EMPTY.withColor(GUILD_AQUA)));

        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(
                DiscordRankChatDecorator.decorateGuildChat(message, DiscordRankChatDecoratorTest::lookup));

        assertEquals(GUILD_AQUA, colorOfFragmentContaining(fragments, "EightySix(ArcLeRetour)"));
    }

    @Test
    void speakerNameEndCutsAtTheFragmentHoldingTheRealUsername() {
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(Component.empty()
                .append(Component.literal("  "))
                .append(Component.literal("EightySix"))
                .append(Component.literal("(ArcLeRetour)"))
                .append(Component.literal(": hi")));
        String text = ComponentTextEditor.textOf(fragments);

        assertEquals(
                text.indexOf('('),
                DiscordRankChatDecorator.speakerNameEnd(fragments, text, 2, text.indexOf(':'), "ArcLeRetour"));
    }

    @Test
    void speakerNameEndCoversTheWholeNameWhenThereIsNoNick() {
        List<ComponentTextEditor.Fragment> fragments =
                ComponentTextEditor.flatten(Component.literal("  ArcLeRetour: hi"));
        String text = ComponentTextEditor.textOf(fragments);

        assertEquals(
                text.indexOf(':'),
                DiscordRankChatDecorator.speakerNameEnd(fragments, text, 2, text.indexOf(':'), "ArcLeRetour"));
    }

    @Test
    void paintsThePillWithTheDiscordRoleColour() {
        assertEquals(0x4CB4FA, DiscordRankChatDecorator.colorFor(SAPLING).getValue());
        assertEquals(
                0x1B9056,
                DiscordRankChatDecorator.colorFor(presentation("rank.treant", "Treant", 104, 0x1B9056))
                        .getValue());
    }

    @Test
    void fallsBackToANeutralColourForUncolouredRanks() {
        RankPresentation uncoloured =
                new RankPresentation(new DiscordRank("rank.upper_strategist", "Upper Strategist", 96), ColorRamp.empty());

        assertNotNull(DiscordRankChatDecorator.colorFor(uncoloured));
        assertFalse(DiscordRankChatDecorator.rampFor(uncoloured).isEmpty(), "the pill still needs a colour");
    }

    @Test
    void keepsPillTextReadableOnBothPaleAndDarkRoleColours() {
        // Dryad is near-white pastel, Yggdrasil is deep purple.
        TextColor pale = TextColor.fromRgb(0xCDECE4);
        TextColor dark = TextColor.fromRgb(0x7506D6);

        double paleLabel = DiscordRankChatDecorator.luminanceOf(
                DiscordRankChatDecorator.labelColorOn(pale).getValue());
        double darkLabel = DiscordRankChatDecorator.luminanceOf(
                DiscordRankChatDecorator.labelColorOn(dark).getValue());

        assertTrue(paleLabel < 0.35d, "a pale pill needs a dark label, was " + paleLabel);
        assertTrue(darkLabel > 0.55d, "a dark pill needs a light label, was " + darkLabel);
    }

    @Test
    void usesMutedGreysRatherThanPureBlackOrWhite() {
        int onDark = DiscordRankChatDecorator.labelColorOn(TextColor.fromRgb(0x7506D6)).getValue();
        int onPale = DiscordRankChatDecorator.labelColorOn(TextColor.fromRgb(0xCDECE4)).getValue();

        assertNotEquals(0xFFFFFF, onDark);
        assertNotEquals(0x000000, onPale);
        assertTrue(DiscordRankChatDecorator.luminanceOf(onDark) < 0.85d, "must stay softer than white");
        assertTrue(DiscordRankChatDecorator.luminanceOf(onPale) > 0.05d, "must stay softer than black");
    }

    @Test
    void tintsTheLabelWithTheBackgroundHue() {
        // Blue pill: the label leans blue too, instead of being a flat neutral grey.
        int label = DiscordRankChatDecorator.labelColorOn(TextColor.fromRgb(0x4CB4FA)).getValue();

        assertTrue((label & 0xFF) > ((label >> 16) & 0xFF), "blue channel should lead on a blue pill");
    }

    @Test
    void blendMixesChannelsProportionally() {
        assertEquals(0x808080, DiscordRankChatDecorator.blend(0x000000, 0xFFFFFF, 0.5039d));
        assertEquals(0xFFFFFF, DiscordRankChatDecorator.blend(0x000000, 0xFFFFFF, 1d));
        assertEquals(0x123456, DiscordRankChatDecorator.blend(0x123456, 0xFFFFFF, 0d));
    }

    @Test
    void paintsAGradientRoleAcrossThePillInsteadOfFlatteningItToTheFirstStop() {
        RankPresentation gradient = presentation("rank.yggdrasil", "Ygg", 120, 0x000000, 0xFFFFFF);

        List<Integer> backgrounds = pillBackgroundColors(DiscordRankChatDecorator.rankPill(gradient, null));

        assertEquals(3, backgrounds.size(), "one background block per glyph");
        assertEquals(0x000000, backgrounds.getFirst());
        assertEquals(0xFFFFFF, backgrounds.getLast());
        assertTrue(
                backgrounds.get(1) > backgrounds.getFirst() && backgrounds.get(1) < backgrounds.getLast(),
                "the middle glyph must sit between the two stops, was " + backgrounds.get(1));
    }

    @Test
    void keepsASolidRoleFlatSoNothingChangesForNonGradientRanks() {
        List<Integer> backgrounds = pillBackgroundColors(DiscordRankChatDecorator.rankPill(SAPLING, null));

        assertEquals(List.of(0x4CB4FA, 0x4CB4FA, 0x4CB4FA), backgrounds.subList(0, 3));
    }

    @Test
    void keepsOneLabelColourAcrossAGradient() {
        // Only the background is graded. Letters that shifted hue from one to the next
        // read as a rendering fault rather than as a gradient.
        RankPresentation gradient = presentation("rank.treant", "Treant", 104, 0x1B9056, 0x50C9A6);

        List<Integer> labels = pillLabelColors(DiscordRankChatDecorator.rankPill(gradient, null));

        assertEquals(1, Set.copyOf(labels).size(), "every letter shares one colour, was " + labels);
    }

    @Test
    void picksTheLabelColourFromTheMiddleOfTheGradient() {
        // Sampling an end instead would leave the other end of a wide ramp unreadable.
        RankPresentation darkToLight = presentation("rank.yggdrasil", "Ygg", 120, 0x101010, 0x303030);

        int label = pillLabelColors(DiscordRankChatDecorator.rankPill(darkToLight, null)).getFirst();

        assertTrue(
                DiscordRankChatDecorator.luminanceOf(label) > 0.55d,
                "a dark pill needs a light label throughout, was " + Integer.toHexString(label));

    }

    @Test
    void speakerNameTakesThePrimaryStopOfAGradientRole() {
        // A name is one component, so it cannot carry the gradient itself.
        RankPresentation gradient = presentation("rank.yggdrasil", "Ygg", 120, 0x123456, 0xFFFFFF);

        assertEquals(0x123456, DiscordRankChatDecorator.colorFor(gradient).getValue());
    }

    @Test
    void bridgeInsigniaIsSuppressedWhileTheSettingIsOff() {
        // The bridge builds its own line, so unlike guild chat it never passes through
        // decorateGuildChat's early return and has to check the setting itself.
        withDiscordRanks(false, () -> {
            AtomicBoolean consulted = new AtomicBoolean();

            MutableComponent insignia = DiscordRankChatDecorator.bridgeInsignia("dix", name -> {
                consulted.set(true);
                return SeqBadgeTier.DIAMOND;
            });

            assertNull(insignia, "no insignia may be drawn with ranks turned off");
            assertFalse(consulted.get(), "and the roster must not even be consulted");
        });
    }

    @Test
    void bridgeInsigniaIsDrawnWhileTheSettingIsOn() {
        withDiscordRanks(
                true,
                () -> assertNotNull(DiscordRankChatDecorator.bridgeInsignia("dix", name -> SeqBadgeTier.DIAMOND)));
    }

    @Test
    void insigniaObeyTheBadgeSettingEvenWhileRanksStayOn() {
        // Someone who turned insignia off on nametags does not expect them back next to
        // their name in chat, so the two answer to the same setting.
        withSettings(true, false, () -> {
            AtomicBoolean consulted = new AtomicBoolean();

            MutableComponent insignia = DiscordRankChatDecorator.bridgeInsignia("dix", name -> {
                consulted.set(true);
                return SeqBadgeTier.DIAMOND;
            });

            assertNull(insignia, "no insignia with the badge setting off");
            assertFalse(consulted.get(), "and the roster must not even be consulted");
        });
    }

    @Test
    void guildChatDropsTheInsigniaWhenTheBadgeSettingIsOff() {
        List<ComponentTextEditor.Fragment> fragments =
                ComponentTextEditor.flatten(Component.literal("Name: hi"));
        int colon = "Name".length();

        withSettings(true, false, () -> assertSame(
                fragments,
                DiscordRankChatDecorator.insertInsignia(fragments, colon, "ArcLeRetour"),
                "the line must come back untouched"));
    }

    private static void withDiscordRanks(boolean enabled, Runnable body) {
        withSettings(enabled, true, body);
    }

    private static void withSettings(boolean ranks, boolean insignia, Runnable body) {
        Setting.BooleanSetting previousRanks = SeqClient.showDiscordRanksSetting;
        Setting.BooleanSetting previousInsignia = SeqClient.showInsigniaBadgesSetting;
        try {
            SeqClient.showDiscordRanksSetting = new Setting.BooleanSetting("show_discord_ranks", "chat", ranks);
            SeqClient.showInsigniaBadgesSetting =
                    new Setting.BooleanSetting("show_insignia_badges", "leaderboard_badges", insignia);
            body.run();
        } finally {
            SeqClient.showDiscordRanksSetting = previousRanks;
            SeqClient.showInsigniaBadgesSetting = previousInsignia;
        }
    }

    @Test
    void pillLabelIsUppercasedForTheWynncraftFont() {
        assertEquals("SAPLING", SAPLING.pillLabel());
    }

    @Test
    void usesAnIconThenAnAlignedBarForConsecutiveBridgeMessages() {
        DiscordRankChatDecorator.decorateGuildChat(Component.literal("ordinary chat"));

        Component firstPrefix = DiscordRankChatDecorator.bridgePrefix();
        Component bridgeLine = Component.literal("bridged line");
        DiscordRankChatDecorator.displayUndecorated(bridgeLine, () -> {});
        // Another mod may hand the line back late; it must not close its own block.
        DiscordRankChatDecorator.decorateGuildChat(bridgeLine);
        Component continuationPrefix = DiscordRankChatDecorator.bridgePrefix();

        assertEquals(DiscordRankChatDecorator.BRIDGE_ICON_GLYPH + " ", firstPrefix.getString());
        assertEquals(
                DiscordRankChatDecorator.BRIDGE_CONTINUATION_GLYPH + " ",
                continuationPrefix.getString());
        assertEquals(
                new FontDescription.Resource(Identifier.fromNamespaceAndPath("seq", "discord_bridge")),
                ComponentTextEditor.flatten(firstPrefix).getFirst().style().getFont());

        DiscordRankChatDecorator.decorateGuildChat(Component.literal("interrupting chat"));
        assertEquals(
                DiscordRankChatDecorator.BRIDGE_ICON_GLYPH + " ",
                DiscordRankChatDecorator.bridgePrefix().getString());
    }

    /** A rank with the colours already resolved for the member, as the service returns it. */
    private static RankPresentation presentation(String key, String label, int position, int... colors) {
        return new RankPresentation(
                new DiscordRank(key, label, position),
                ColorRamp.of(Arrays.stream(colors).boxed().toList()));
    }

    /** Mirrors {@code DiscordRankService}, which indexes members under lowercase names. */
    private static RankPresentation lookup(String username) {
        return RANKS.get(username.toLowerCase(Locale.ROOT));
    }

    /**
     * A guild line shaped like the real thing, per {@code latest.log}: timestamp, the
     * guild icon in its own font, badge geometry and letters in the badge font, then
     * the speaker with the nick reveal appended as a legacy {@code §c} run.
     */
    private static Component wynncraftGuildLine(String rank, String nick, String username, String body) {
        return Component.empty()
                .append(Component.literal("[19:25:32] "))
                .append(Component.literal(ICON_GLYPHS).withStyle(Style.EMPTY.withFont(ICON_FONT)))
                .append(Component.literal(" "))
                .append(Component.literal(BADGE_GEOMETRY).withStyle(Style.EMPTY.withFont(BADGE_FONT)))
                .append(Component.literal(WynnPillGlyphs.encodePlainPill(rank))
                        .withStyle(Style.EMPTY.withFont(BADGE_FONT).withColor(0x000000)))
                .append(Component.literal(" "))
                .append(Component.literal(nick + LEGACY_RED + "(" + username + ")" + LEGACY_RESET)
                        .withStyle(Style.EMPTY.withColor(DARK_AQUA).withInsertion(username)))
                .append(Component.literal(": " + body).withStyle(Style.EMPTY.withColor(GUILD_AQUA)));
    }

    /** Mirrors a Wynntils-rendered guild line: timestamp, rank pill, speaker, message. */
    private static Component guildLine(String rank, String displayedName, String insertion, String body) {
        Style nameStyle = insertion == null ? Style.EMPTY : Style.EMPTY.withInsertion(insertion);
        return Component.empty()
                .append(Component.literal("[09:33:28] "))
                .append(Component.literal(WynnPillGlyphs.encodePlainPill(rank) + " "))
                .append(Component.literal(displayedName).withStyle(nameStyle))
                .append(Component.literal(": " + body));
    }

    private static int colorOfFragmentContaining(List<ComponentTextEditor.Fragment> fragments, String needle) {
        return fragments.stream()
                .filter(fragment -> fragment.text().contains(needle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no fragment holds " + needle))
                .style()
                .getColor()
                .getValue();
    }

    /**
     * Colours of the pill's background blocks, one per glyph, in order. The corners
     * are excluded: they carry the end stops and would mask a flat middle.
     */
    private static List<Integer> pillBackgroundColors(Component pill) {
        return ComponentTextEditor.flatten(pill).stream()
                .filter(fragment -> fragment.text().indexOf(WynnPillGlyphs.BACKGROUND) >= 0)
                .map(fragment -> fragment.style().getColor().getValue())
                .toList();
    }

    /** Colours of the pill's letter glyphs, one per glyph, in order. */
    private static List<Integer> pillLabelColors(Component pill) {
        return ComponentTextEditor.flatten(pill).stream()
                .filter(fragment -> fragment.text().indexOf(WynnPillGlyphs.TEXT_OFFSET) >= 0)
                .map(fragment -> fragment.style().getColor().getValue())
                .toList();
    }

    private static List<String> pillLabels(Component component) {
        return WynnPillGlyphs.findPills(component.getString()).stream()
                .map(pill -> pill.label().toLowerCase(Locale.ROOT))
                .toList();
    }
}
