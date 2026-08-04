package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SequoiaProfileLinksTest {

    @Test
    void buildsThePlayerCardUrl() {
        assertEquals(
                "https://seqwawa.com/statistics/player/playercard?player=ArcLeRetour",
                SequoiaProfileLinks.playerCard("ArcLeRetour").toString());
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals(
                "https://seqwawa.com/statistics/player/playercard?player=dix",
                SequoiaProfileLinks.playerCard("  dix  ").toString());
    }

    @Test
    void rejectsInsertionsThatAreNotPlayerNames() {
        // Shift-click insertions are not always usernames, and a link must not be
        // opened for arbitrary text.
        assertNull(SequoiaProfileLinks.playerCard(null));
        assertNull(SequoiaProfileLinks.playerCard(""));
        assertNull(SequoiaProfileLinks.playerCard("no"));
        assertNull(SequoiaProfileLinks.playerCard("way too long a name here"));
        assertNull(SequoiaProfileLinks.playerCard("has spaces"));
        assertNull(SequoiaProfileLinks.playerCard("bad/char"));
    }
}
