package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.managers.LeafQuestService.State;
import com.seqwawa.seq.model.SeqBadgeTier;
import org.junit.jupiter.api.Test;

class SidebarProfileTest {

    @Test
    void theBalanceIsShortenedOnlyWhenItWouldNotFit() {
        // Six pixels a character, as the pixel font is at 10, and eleven more for the Leaf.
        java.util.function.Function<String, Float> width = text -> text.length() * 6f + 11;

        assertEquals("12,480", SidebarProfile.balanceText(12_480, 60, width));
        assertEquals("1.2M", SidebarProfile.balanceText(1_234_567, 60, width));
    }

    @Test
    void sitsOnTheArtPixelGridAtTheSidebarsFoot() {
        assertEquals(360 - 8 - 76, SidebarProfile.top(360));
        assertEquals(0, SidebarProfile.top(361) % SidebarProfile.P);
    }

    @Test
    void theHeadAndTheLeafRowsFitInsideTheFrame() {
        // The head's frame and the gap under it, the Leaf row, and the bar, inside the padding.
        int headBottom = SidebarProfile.PAD + SidebarProfile.HEAD;
        int barBottom = 30 + 4;
        assertEquals(true, headBottom + 2 <= 26 - 3, "the Leaf row clears the head");
        assertEquals(true, barBottom <= SidebarProfile.HEIGHT - SidebarProfile.PAD, "the bar clears the frame's bottom");
    }

    @Test
    void saysWhyThereIsNoBalanceYet() {
        assertEquals("Connect for Leaf", SidebarProfile.leafStatus(State.LOADING, false));
        assertEquals("Loading Leaf...", SidebarProfile.leafStatus(State.LOADING, true));
        assertEquals("Leaf is for members", SidebarProfile.leafStatus(State.NOT_MEMBER, true));
        assertEquals("Leaf is unavailable", SidebarProfile.leafStatus(State.UNAVAILABLE, true));
    }

    @Test
    void findsTheInsigniaArtTheBadgesAreLoadedUnder() {
        assertEquals("badge:insignia_gold", SidebarProfile.insigniaKey(SeqBadgeTier.GOLD));
    }
}
