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
}
