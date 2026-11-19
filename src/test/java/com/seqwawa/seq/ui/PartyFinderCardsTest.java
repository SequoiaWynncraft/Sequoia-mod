package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PartyFinderCardsTest {

    private static final UiBounds VIEW = new UiBounds(0, 100, 300, 200);

    @Test
    void aCardCollapsingAtTheBottomOfTheListMovesTheCardsBeforeTheyAreDrawn() {
        ScrollPane scroll = new ScrollPane();
        // Two closed cards and a tall open one, scrolled to the bottom.
        PartyFinderScreen.stackCards(List.of(56f, 56f, 300f), scroll, VIEW);
        scroll.scrollTo(scroll.max());

        // The open card closes, or a socket update shortens it: everything now fits.
        List<UiBounds> drawn = PartyFinderScreen.stackCards(List.of(56f, 56f, 56f), scroll, VIEW);

        assertEquals(0, scroll.offset());
        assertEquals(VIEW.y() + 8, drawn.getFirst().y(), "drawn at the offset the frame's clicks use");
        assertEquals(drawn, PartyFinderScreen.stackCards(List.of(56f, 56f, 56f), scroll, VIEW));
    }

    @Test
    void aSwingingFilterChipIsHitWhereItIsDrawn() {
        UiBounds bounds = new UiBounds(0, 0, 40, 10);
        PartyFinderScreen.TagChip upright = new PartyFinderScreen.TagChip("NOG", bounds, 0);
        PartyFinderScreen.TagChip turned = new PartyFinderScreen.TagChip("NOG", bounds, 90);

        assertTrue(upright.contains(new UiPointer(35, 5)));
        assertFalse(turned.contains(new UiPointer(35, 5)), "the chip's end has swung away");
        assertTrue(turned.contains(new UiPointer(20, 20)), "and hangs below its centre");
    }
}
