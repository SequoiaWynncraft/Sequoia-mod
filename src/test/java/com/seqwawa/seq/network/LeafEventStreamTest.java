package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LeafEventStreamTest {

    @Test
    void aPopupPostedBeforeAnAccountChangeIsDroppedWhenItRuns() {
        LeafEventStream stream = LeafEventStream.getInstance();
        AtomicInteger shown = new AtomicInteger();
        Runnable posted = stream.unlessResetSince(stream.generation(), shown::incrementAndGet);

        // The account change resets the stream and clears the popups in the same tick, before the post runs.
        stream.reset();
        posted.run();

        assertEquals(0, shown.get());
    }

    @Test
    void aPopupFromTheCurrentStreamStillShows() {
        LeafEventStream stream = LeafEventStream.getInstance();
        AtomicInteger shown = new AtomicInteger();

        stream.unlessResetSince(stream.generation(), shown::incrementAndGet).run();

        assertEquals(1, shown.get());
    }

    @Test
    void aStreamStoppedForADisconnectKeepsItsPopups() {
        LeafEventStream stream = LeafEventStream.getInstance();
        AtomicInteger shown = new AtomicInteger();
        Runnable posted = stream.unlessResetSince(stream.generation(), shown::incrementAndGet);

        // The same account's unlock still belongs to the player.
        stream.stop();
        posted.run();

        assertEquals(1, shown.get());
    }

    @Test
    void aSessionEndedForTheSameAccountDropsItsPopupsButResumesFromTheLastEvent() {
        LeafEventStream stream = LeafEventStream.getInstance();
        stream.reset();
        stream.advanceCursor("event-41");
        AtomicInteger shown = new AtomicInteger();
        Runnable posted = stream.unlessResetSince(stream.generation(), shown::incrementAndGet);

        // A rejected token ends the session; the account is the same, so the stream picks up where it was.
        stream.endSessionKeepingCursor();
        posted.run();

        assertEquals(0, shown.get());
        assertEquals("event-41", stream.cursor());
    }

    @Test
    void anAccountChangeStartsTheNextStreamFromNow() {
        LeafEventStream stream = LeafEventStream.getInstance();
        stream.advanceCursor("event-41");

        stream.reset();

        assertEquals(null, stream.cursor());
    }
}
