package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.unlock.UnlockPopup;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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
    void remembersTheLastEventSeenAndIgnoresEventsWithoutAnId() throws Exception {
        LeafEventStream stream = resumingAfter(null);

        readOnItsOwnThread(stream, Stream.of("id: event-42", "", ": keep-alive", ""));

        assertEquals("event-42", stream.cursor());
    }

    @Test
    void anEventReadAsTheStreamStopsLeavesItsPlaceWhereStopFoundIt() throws Exception {
        List<UnlockPopup> shown = new ArrayList<>();
        LeafEventStream stream = new LeafEventStream(force -> new CompletableFuture<>(), "event-41", shown::add, () -> {});
        // The session ends while the worker reads the line that finishes an event, after it last checked.
        Iterator<String> lines = List.of("id: event-42", "event: leaf_earned",
                "data: {\"data\":{\"leaf\":330,\"source\":\"raid\",\"detail\":\"The Nameless Anomaly\"}}", "")
                .iterator();
        Iterator<String> stopping = new Iterator<>() {
            @Override public boolean hasNext() { return lines.hasNext(); }
            @Override public String next() {
                String line = lines.next();
                if (line.isEmpty()) stream.stop();
                return line;
            }
        };

        readOnItsOwnThread(stream, StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(stopping, Spliterator.ORDERED), false));

        assertEquals("event-41", stream.cursor(), "the next session resumes where stop() left it, and shows the event");
        assertTrue(shown.isEmpty(), "the stopped stream does not show it");
    }

    private static void readOnItsOwnThread(LeafEventStream stream, Stream<String> lines) throws InterruptedException {
        Thread reader = new Thread(() -> stream.readAsWorker(lines));
        reader.start();
        reader.join(5_000);
    }
}
