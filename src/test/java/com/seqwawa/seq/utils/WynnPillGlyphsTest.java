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
    void roundTripsDigitsAndLetters() {
        assertEquals("sapling2", WynnPillGlyphs.findPills(WynnPillGlyphs.encodePlainPill("Sapling2"))
                .get(0)
                .label());
    }
}
