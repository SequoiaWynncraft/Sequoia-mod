package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WynnPillGlyphsTest {

    @Test
    void decodesLabelOfAnEncodedPill() {
        String pill = WynnPillGlyphs.encodePlainPill("RECRUITER");

        List<WynnPillGlyphs.Pill> pills = WynnPillGlyphs.findPills("[09:33:28] " + pill + " ArcLeRetour: hey");

        assertEquals(1, pills.size());
        assertEquals("recruiter", pills.get(0).label());
    }

    @Test
    void reportsSpanCoveringTheWholePillIncludingPadding() {
        String prefix = "x ";
        String pill = WynnPillGlyphs.encodePlainPill("chief");
        String text = prefix + pill + " Name: msg";

        WynnPillGlyphs.Pill found = WynnPillGlyphs.findPills(text).get(0);

        assertEquals(prefix.length(), found.start());
        assertEquals(prefix.length() + pill.length(), found.endExclusive());
    }

    @Test
    void keepsAdjacentPillsSeparateInsteadOfMergingLabels() {
        String text = WynnPillGlyphs.encodePlainPill("vip") + WynnPillGlyphs.encodePlainPill("recruit");

        List<WynnPillGlyphs.Pill> pills = WynnPillGlyphs.findPills(text);

        assertEquals(List.of("vip", "recruit"), pills.stream().map(WynnPillGlyphs.Pill::label).toList());
    }

    @Test
    void ignoresUnterminatedOrEmptyPills() {
        assertTrue(WynnPillGlyphs.findPills(WynnPillGlyphs.CORNER_LEFT + "Recruiter").isEmpty());
        assertTrue(WynnPillGlyphs.findPills("" + WynnPillGlyphs.CORNER_LEFT + WynnPillGlyphs.CORNER_RIGHT)
                .isEmpty());
        assertTrue(WynnPillGlyphs.findPills("ArcLeRetour: no pills here").isEmpty());
    }

    @Test
    void readsBadgesBuiltFromUnknownCornerAndFillerGlyphs() {
        // Wynncraft varies the decorative glyphs between badge styles; only the
        // letter glyphs are fixed, so detection must not depend on the rest.
        String badge = "\uE07A\uE0FF\uE051\uE0FF\uE044\uE0FF\uE042\uE07B";

        List<WynnPillGlyphs.Pill> pills = WynnPillGlyphs.findPills("[13:43:41] " + badge + " Player: hi");

        assertEquals(1, pills.size());
        assertEquals("rec", pills.get(0).label());
        assertEquals(11, pills.get(0).start());
        assertEquals(11 + badge.length(), pills.get(0).endExclusive());
    }

    @Test
    void decodesCapturedLayeredGuildRankBadges() {
        assertLayeredRank("chief", 0xCFFE2, 0xE032, 0xE037, 0xE038, 0xE034, 0xE035);
        assertLayeredRank("owner", 0xCFFE0, 0xE03E, 0xE046, 0xE03D, 0xE034, 0xE041);
        assertLayeredRank(
                "strategist",
                0xCFFC4,
                0xE042,
                0xE043,
                0xE041,
                0xE030,
                0xE043,
                0xE034,
                0xE036,
                0xE038,
                0xE042,
                0xE043);
        assertLayeredRank("captain", 0xCFFD6, 0xE032, 0xE030, 0xE03F, 0xE043, 0xE030, 0xE038, 0xE03D);
        assertLayeredRank(
                "recruiter",
                0xCFFCA,
                0xE041,
                0xE034,
                0xE032,
                0xE041,
                0xE044,
                0xE038,
                0xE043,
                0xE034,
                0xE041);
        assertLayeredRank("recruit", 0xCFFD6, 0xE041, 0xE034, 0xE032, 0xE041, 0xE044, 0xE038, 0xE043);
    }

    @Test
    void stopsAtSupplementaryPlaneIconsSoTheyAreNeverReplaced() {
        String guildIcon = "\uDAFF\uDFFC";
        String pill = WynnPillGlyphs.encodePlainPill("recruiter");

        WynnPillGlyphs.Pill found = WynnPillGlyphs.findPills(guildIcon + pill + " Player: hi").get(0);

        assertEquals(guildIcon.length(), found.start());
        assertEquals("recruiter", found.label());
    }

    @Test
    void detectsPillPresenceWithoutFullParsing() {
        assertTrue(WynnPillGlyphs.containsPill(WynnPillGlyphs.encodePlainPill("owner")));
        assertFalse(WynnPillGlyphs.containsPill("plain chat line"));
        assertFalse(WynnPillGlyphs.containsPill(null));
    }

    @Test
    void ignoresTheGlyphsThisModDrawsItself() {
        // The Discord marker, the continuation bar and the insignia all live in the
        // same private-use block as Wynncraft's pills. Treating them as a badge lets
        // the decorator overwrite its own output.
        String ours = "";

        assertFalse(WynnPillGlyphs.containsPill(ours), "the mod's own glyphs are not a pill");
        assertTrue(WynnPillGlyphs.findGlyphRuns(ours).isEmpty(), "and form no glyph run");
    }

    @Test
    void stillReadsAWynncraftPillSittingNextToTheModsGlyphs() {
        String text = " " + WynnPillGlyphs.encodePlainPill("recruiter") + " Player: hi";

        List<WynnPillGlyphs.Pill> pills = WynnPillGlyphs.findPills(text);

        assertEquals(1, pills.size());
        assertEquals("recruiter", pills.get(0).label());
        assertEquals(text.indexOf(WynnPillGlyphs.CORNER_LEFT), pills.get(0).start());
    }

    @Test
    void aMultiWordLabelKeepsOneBackgroundBlockPerCharacter() {
        // "Upper Strategist" is a real rank. Its space has no glyph, so it must get a
        // background block on its own: a plain space advances differently from the block
        // it would sit on, which slides the rest of the label off its background.
        String pill = WynnPillGlyphs.encodePlainPill("UPPER STRATEGIST");

        assertEquals(
                "UPPER STRATEGIST".length(),
                pill.chars().filter(c -> c == WynnPillGlyphs.BACKGROUND).count(),
                "one block per character, space included");
        assertEquals(
                "UPPERSTRATEGIST".length(),
                pill.chars().filter(c -> c == WynnPillGlyphs.TEXT_OFFSET).count(),
                "but no text offset for the space");
        assertFalse(pill.contains(" "), "and no raw space inside the pill");
    }

    @Test
    void aSpaceDoesNotSplitAMultiWordPillInTwo() {
        List<WynnPillGlyphs.Pill> pills =
                WynnPillGlyphs.findPills(WynnPillGlyphs.encodePlainPill("UPPER STRATEGIST") + " Player: hi");

        assertEquals(1, pills.size(), "the run must stay whole");
        assertEquals("upperstrategist", pills.get(0).label());
    }

    @Test
    void knowsWhichCharactersTheFontCanDraw() {
        assertTrue(WynnPillGlyphs.hasGlyph('a'));
        assertTrue(WynnPillGlyphs.hasGlyph('Z'));
        assertTrue(WynnPillGlyphs.hasGlyph('7'));
        assertFalse(WynnPillGlyphs.hasGlyph(' '));
        assertFalse(WynnPillGlyphs.hasGlyph('-'));
        assertFalse(WynnPillGlyphs.hasGlyph('é'));
    }

    @Test
    void roundTripsDigitsAndLetters() {
        assertEquals("sapling2", WynnPillGlyphs.findPills(WynnPillGlyphs.encodePlainPill("Sapling2"))
                .get(0)
                .label());
    }

    private static void assertLayeredRank(String expected, int advance, int... foregroundGlyphs) {
        StringBuilder badge = new StringBuilder().appendCodePoint(0xE060);
        for (int glyph : foregroundGlyphs) {
            badge.appendCodePoint(0xCFFFF).appendCodePoint(glyph);
        }
        badge.appendCodePoint(0xCFFFF).appendCodePoint(0xE062).appendCodePoint(advance);
        expected.codePoints().forEach(letter -> badge.appendCodePoint(0xE000 + letter - 'a'));
        badge.appendCodePoint(0xD0002);

        List<WynnPillGlyphs.Pill> pills = WynnPillGlyphs.findPills(badge + " Player: hi");

        assertEquals(1, pills.size());
        assertEquals(expected, pills.getFirst().label());
        assertEquals(0, pills.getFirst().start());
        assertEquals(badge.length(), pills.getFirst().endExclusive());
    }
}
