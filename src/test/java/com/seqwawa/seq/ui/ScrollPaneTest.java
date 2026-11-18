package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScrollPaneTest {
    private static final UiBounds TRACK = new UiBounds(600, 50, 4, 200);
    /** The bar's hit area: its track, two pixels wider each side. */
    private static final UiBounds GRAB = TRACK.inset(-2, 0);

    @Test
    void theThumbIsAsLongAsTheShareOfTheContentInView() {
        // 200 in view of 400 in all: half the track.
        ScrollPane.Bar bar = ScrollPane.bar(TRACK, 0, 200);

        assertEquals(100, bar.thumbHeight());
        assertEquals(50, bar.thumbY());
        assertEquals(new UiBounds(600, 50, 4, 100), bar.thumb());
    }

    @Test
    void theThumbReachesTheFootOfTheTrackWhenScrolledToTheEnd() {
        ScrollPane.Bar bar = ScrollPane.bar(TRACK, 200, 200);

        assertEquals(TRACK.bottom(), bar.thumbY() + bar.thumbHeight());
    }

    @Test
    void theThumbIsNeverShorterThanItCanBeGrabbedNorLongerThanItsTrack() {
        assertEquals(ScrollPane.MIN_THUMB, ScrollPane.bar(TRACK, 0, 100_000).thumbHeight());
        assertEquals(12, ScrollPane.bar(new UiBounds(600, 50, 4, 12), 0, 100).thumbHeight());
    }

    @Test
    void thereIsNoBarWhileEverythingFits() {
        assertNull(ScrollPane.bar(TRACK, 0, 0));
        assertNull(ScrollPane.bar(new UiBounds(600, 50, 4, 0), 0, 100));
    }

    @Test
    void theOffsetStaysWithinWhatCanScroll() {
        ScrollPane pane = new ScrollPane();
        pane.setMax(100);

        pane.scrollBy(500);
        assertEquals(100, pane.offset());
        pane.scrollBy(-1_000);
        assertEquals(0, pane.offset());
        pane.scrollTo(80);
        pane.setMax(40);
        assertEquals(40, pane.offset(), "content that shrank pulls the offset back");
        pane.setMax(-10);
        assertEquals(0, pane.max());
        assertFalse(pane.canScroll());
    }

    @Test
    void draggingTheThumbScrollsAsFarAsTheThumbMoves() {
        ScrollPane pane = new ScrollPane();
        pane.setMax(200);

        assertTrue(pane.startDrag(new UiPointer(602, 60), GRAB));
        // The thumb is 100 tall in a 200 track: 100 of room for 200 of content.
        assertTrue(pane.drag(new UiPointer(602, 110), TRACK));
        assertEquals(100, pane.offset());
        pane.drag(new UiPointer(602, 900), TRACK);
        assertEquals(200, pane.offset(), "no further than the end");
        assertTrue(pane.release());
        assertFalse(pane.release());
        assertFalse(pane.drag(new UiPointer(602, 60), TRACK), "a released thumb doesn't follow the pointer");
    }

    @Test
    void aPressOffTheBarOrWithNothingToScrollStartsNoDrag() {
        ScrollPane pane = new ScrollPane();
        assertFalse(pane.startDrag(new UiPointer(602, 60), GRAB), "nothing to scroll");

        pane.setMax(200);
        assertFalse(pane.startDrag(new UiPointer(590, 60), GRAB));
        assertTrue(pane.startDrag(new UiPointer(598, 60), GRAB), "the hit area is wider than the bar");
    }

    @Test
    void resetGoesBackToTheTop() {
        ScrollPane pane = new ScrollPane();
        pane.setMax(200);
        pane.scrollTo(150);

        pane.reset();

        assertEquals(0, pane.offset());
    }
}
