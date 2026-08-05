package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatDisplayLayoutTest {

    @Test
    void requestedLineCountDeterminesTheCompleteChatHeight() {
        assertEquals(108, ChatDisplayLayout.heightForVisibleLines(12, 9, 200));
        assertEquals(180, ChatDisplayLayout.heightForVisibleLines(20, 9, 200));
        assertEquals(45, ChatDisplayLayout.heightForVisibleLines(5, 9, 200));
    }

    @Test
    void lineCountIsCappedToCompleteLinesThatFitOnScreen() {
        assertEquals(198, ChatDisplayLayout.heightForVisibleLines(20, 18, 200));
        assertEquals(0, ChatDisplayLayout.heightForVisibleLines(5, 18, 10));
    }

    @Test
    void invalidGeometryCannotCreateANegativeChatHeight() {
        assertEquals(0, ChatDisplayLayout.heightForVisibleLines(12, 0, -10));
    }
}
