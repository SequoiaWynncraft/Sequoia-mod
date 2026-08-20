package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.managers.GuildRankNametagDecorator.Member;
import com.seqwawa.seq.model.DiscordRank;
import com.seqwawa.seq.model.RankPresentation;
import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.ComponentTextEditor;
import com.seqwawa.seq.utils.RankGradientAnimation;
import com.seqwawa.seq.utils.WynnPillGlyphs;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GuildRankNametagDecoratorTest {

    private static final RankPresentation DRYAD =
            new RankPresentation(new DiscordRank("rank.dryad", "Dryad", 95), ColorRamp.of(0x2ECC71));
    private static final Member ARC = new Member("ArcLeRetour", DRYAD);
    private static final Function<String, Member> MEMBERS =
            name -> "arcleretour".equals(name) ? ARC : null;

    /** Wynncraft's account rank badge, as it sits on a nametag above a head. */
    private static final String CHAMPION_BADGE = WynnPillGlyphs.encodePlainPill("champion");

    /** A private-use glyph standing for another mod's nametag decoration. */
    private static final String OTHER_MOD_GLYPH = "\uE0A0";

    /** Gold, as Wynncraft draws an account rank badge. */
    private static final int WYNNCRAFT_BADGE_COLOR = 0xFFAA00;

    /** Colours pinned by the decorations a test built, handed back when it ends. */
    private final List<TextColor> pinned = new ArrayList<>();

    @AfterEach
    void releasePinnedColors() {
        RankGradientAnimation.release(pinned);
        pinned.clear();
    }

    /** Decorates as the render hook does, keeping the pinned colours for cleanup. */
    private Component decorate(Component nameTag) {
        GuildRankNametagDecorator.Decoration decoration =
                GuildRankNametagDecorator.decorate(nameTag, MEMBERS);
        pinned.addAll(decoration.colors());
        return decoration.component();
    }

    @Test
    void replacesTheWynncraftBadgeWithTheSequoiaRank() {
        Component nameTag = Component.literal(CHAMPION_BADGE + " ArcLeRetour");

        Component decorated = decorate(nameTag);

        assertNotSame(nameTag, decorated);
        assertEquals(List.of("dryad"), pillLabels(decorated));
        assertTrue(decorated.getString().endsWith(" ArcLeRetour"), decorated.getString());
    }

    @Test
    void leavesAPlayerOutsideTheRosterAlone() {
        Component nameTag = Component.literal(CHAMPION_BADGE + " SomeoneElse");

        assertSame(nameTag, decorate(nameTag));
    }

    @Test
    void addsTheRankWhenTheAccountHasNoBadge() {
        Component nameTag = Component.literal("ArcLeRetour");

        Component decorated = decorate(nameTag);

        assertEquals(List.of("dryad"), pillLabels(decorated));
        assertTrue(decorated.getString().endsWith(" ArcLeRetour"), decorated.getString());
    }

    /**
     * Nametags are submitted every frame, and a mod may hand back one this already
     * rewrote; recognising its own pill keeps it from stacking a second one.
     */
    @Test
    void leavesANametagItAlreadyDecoratedAlone() {
        Component decorated =
                decorate(Component.literal(CHAMPION_BADGE + " ArcLeRetour"));

        assertSame(decorated, decorate(decorated));
    }

    /**
     * Only the badge standing next to the name is the rank badge. Another mod's
     * marker in front of it, such as a starred profile, has to survive.
     */
    @Test
    void replacesOnlyTheBadgeNextToTheName() {
        Component nameTag =
                Component.literal(OTHER_MOD_GLYPH + " " + CHAMPION_BADGE + " ArcLeRetour");

        Component decorated = decorate(nameTag);

        assertTrue(decorated.getString().startsWith(OTHER_MOD_GLYPH + " "), decorated.getString());
        assertEquals(List.of("dryad"), pillLabels(decorated));
    }

    /** A badge another mod appends after the name is not a rank badge either. */
    @Test
    void keepsDecorationsThatFollowTheName() {
        Component nameTag =
                Component.literal(CHAMPION_BADGE + " ArcLeRetour " + OTHER_MOD_GLYPH);

        Component decorated = decorate(nameTag);

        assertTrue(
                decorated.getString().endsWith(" ArcLeRetour " + OTHER_MOD_GLYPH),
                decorated.getString());
        assertEquals(List.of("dryad"), pillLabels(decorated));
    }

    /**
     * A mod that rebuilds the nametag from its own data leaves an unranked account
     * with nothing but its name, split across its own pieces; the rank still has to
     * reach the tag it produced.
     */
    @Test
    void decoratesARebuiltNametagThatKeptOnlyTheName() {
        Component nameTag = Component.literal("Arc").append(Component.literal("LeRetour"));

        Component decorated = decorate(nameTag);

        assertEquals(List.of("dryad"), pillLabels(decorated));
        assertTrue(decorated.getString().endsWith(" ArcLeRetour"), decorated.getString());
    }

    /**
     * Wynncraft's layered badge is a foreground pill, a back-advance and a shadow
     * repeating the label, joined by supplementary-plane characters. Replacing only
     * the pill used to leave the shadow standing next to the new rank, spelling the
     * old one.
     */
    @Test
    void removesEveryLayerOfALayeredBadge() {
        Component nameTag = Component.literal(layeredBadge("champion") + " ArcLeRetour");

        Component decorated = decorate(nameTag);

        assertEquals(List.of("dryad"), pillLabels(decorated));
        assertEquals(
                1,
                WynnPillGlyphs.findGlyphRuns(decorated.getString()).size(),
                "the old badge left glyphs behind: " + decorated.getString());
    }

    /** The name carries the rank colours too, so the tag reads as one label. */
    @Test
    void paintsTheNameInTheRankColour() {
        Component decorated =
                decorate(Component.literal(CHAMPION_BADGE + " ArcLeRetour"));

        assertEquals(List.of(0x2ECC71), nameColors(decorated));
    }

    /**
     * A nametag stands on screen for as long as its owner is in sight, far longer than
     * the chat line the animation registry is sized for. Its colours have to stay
     * registered through any amount of chat: an evicted stop stops following the
     * settings, and since eviction takes one glyph at a time, the pill used to come
     * apart into differently coloured pieces as one moved.
     */
    @Test
    void keepsItsColoursWhenChatFillsTheAnimationRegistry() {
        Component nameTag = Component.literal(CHAMPION_BADGE)
                .withStyle(style -> style.withColor(WYNNCRAFT_BADGE_COLOR))
                .append(Component.literal(" ArcLeRetour"));
        List<TextColor> backgrounds = pillBackgroundColors(decorate(nameTag));
        assertFalse(backgrounds.isEmpty());

        fillAnimationRegistry();

        Setting.BooleanSetting previous = SeqClient.colorRankPillsSetting;
        try {
            SeqClient.colorRankPillsSetting = new Setting.BooleanSetting("color_rank_pills", "chat", false);
            for (TextColor background : backgrounds) {
                assertEquals(
                        WYNNCRAFT_BADGE_COLOR,
                        RankGradientAnimation.animate(background).getValue(),
                        "the pill stopped answering to the settings, so its stop was evicted");
            }
        } finally {
            SeqClient.colorRankPillsSetting = previous;
        }
    }

    /**
     * Minecraft draws every nametag twice, and styled text takes the alpha of the pass
     * drawing it. Sequoia's glyphs are drawn at full alpha in both, which needs them to
     * be recognisable as its own; see {@code SeeThroughTextPass}.
     */
    @Test
    void marksItsColoursAsSequoiaDecorations() {
        Component decorated = decorate(Component.literal(CHAMPION_BADGE + " ArcLeRetour"));

        List<TextColor> backgrounds = pillBackgroundColors(decorated);
        assertFalse(backgrounds.isEmpty());
        for (TextColor background : backgrounds) {
            assertTrue(RankGradientAnimation.isDecorationColor(background), "an unrecognised pill colour");
        }
        assertFalse(
                RankGradientAnimation.isDecorationColor(TextColor.fromRgb(0x123456)),
                "a colour Sequoia never minted");
    }

    /** Chat traffic well past the registry's bound, to evict anything evictable. */
    private static void fillAnimationRegistry() {
        RankGradientAnimation.batchRegistrations(() -> {
            for (int index = 0; index < 5000; index++) {
                RankGradientAnimation.colorAt(ColorRamp.of(0xFF0000), 0d);
            }
            return null;
        });
    }

    @Test
    void findsTheNameBehindTheBadgeGlyphs() {
        String text = CHAMPION_BADGE + " ArcLeRetour";

        GuildRankNametagDecorator.DisplayedName name =
                GuildRankNametagDecorator.displayedName(text, MEMBERS);

        assertNotNull(name);
        assertEquals(text.indexOf("ArcLeRetour"), name.start());
        assertEquals(ARC, name.member());
    }

    @Test
    void readsEveryNameANametagCouldBeKnownBy() {
        assertEquals(
                List.of("Nick", "ArcLeRetour"),
                GuildRankNametagDecorator.nameCandidates(CHAMPION_BADGE + " Nick(ArcLeRetour)"));
    }

    /** One letter is a decoration, not a name, and twenty characters is not one either. */
    @Test
    void ignoresTextThatCannotBeAnAccountName() {
        assertEquals(List.of(), GuildRankNametagDecorator.nameCandidates("A b [] 1"));
        assertEquals(List.of(), GuildRankNametagDecorator.nameCandidates("ThisNameIsFarTooLong"));
    }

    @Test
    void findsNoNameOnATagThatBelongsToNobodyKnown() {
        assertNull(GuildRankNametagDecorator.displayedName("Wandering Merchant", MEMBERS));
    }

    private static List<String> pillLabels(Component component) {
        return WynnPillGlyphs.findPills(component.getString()).stream()
                .map(pill -> pill.label().toLowerCase(Locale.ROOT))
                .toList();
    }

    /** Colours of the fragments holding the displayed name. */
    private static List<Integer> nameColors(Component component) {
        return ComponentTextEditor.flatten(component).stream()
                .filter(fragment -> fragment.text().chars().allMatch(Character::isLetterOrDigit))
                .filter(fragment -> !fragment.text().isEmpty())
                .map(fragment -> fragment.style().getColor().getValue())
                .toList();
    }

    /** Colours of the pill's background blocks, i.e. the glyphs carrying no label. */
    private static List<TextColor> pillBackgroundColors(Component component) {
        return ComponentTextEditor.flatten(component).stream()
                .filter(fragment -> fragment.text().indexOf(WynnPillGlyphs.BACKGROUND) >= 0)
                .map(fragment -> fragment.style().getColor())
                .toList();
    }

    /**
     * Wynncraft's layered badge: a corner-framed foreground layer, a back-advance,
     * then a shadow layer repeating the label, per {@link WynnPillGlyphs}.
     */
    private static String layeredBadge(String label) {
        StringBuilder badge = new StringBuilder().appendCodePoint(0xE060);
        for (int index = 0; index < label.length(); index++) {
            badge.appendCodePoint(0xCFFFF).appendCodePoint(0xE030 + (label.charAt(index) - 'a'));
        }
        badge.appendCodePoint(0xCFFFF).appendCodePoint(0xE062).appendCodePoint(0xCFF80);
        for (int index = 0; index < label.length(); index++) {
            badge.appendCodePoint(0xE000 + (label.charAt(index) - 'a'));
        }
        return badge.appendCodePoint(0xD0002).toString();
    }
}
