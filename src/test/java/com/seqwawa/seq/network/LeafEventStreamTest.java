package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class LeafEventStreamTest {

    private static LeafEventStream resumingAfter(String eventId) {
        return new LeafEventStream(force -> new CompletableFuture<>(), eventId, popup -> {}, () -> {});
    }

    @Test
    void aStreamForTheSamePlayerResumesAfterTheEventTheLastOneSaw() {
        assertEquals("event-41", resumingAfter("event-41").cursor());
    }

    @Test
    void aStreamForANewAccountStartsFromNow() {
        assertNull(resumingAfter(null).cursor());
    }

    @Test
    void remembersTheLastEventSeenAndIgnoresEventsWithoutAnId() {
        LeafEventStream stream = resumingAfter(null);

        stream.advanceCursor("event-42");
        stream.advanceCursor(null);

        assertEquals("event-42", stream.cursor());
    }
}
