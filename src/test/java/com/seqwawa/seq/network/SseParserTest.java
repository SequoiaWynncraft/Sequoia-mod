package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.unlock.UnlockPopup;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SseParserTest {

    @Test
    void theOpeningFrameOnlyMovesWhereToResumeFrom() {
        SseParser parser = new SseParser();

        assertNull(parser.accept("id: 41"));
        assertNull(parser.accept("retry: 3000"));
        assertNull(parser.accept(""));

        assertEquals("41", parser.lastEventId());
    }

    @Test
    void readsAnEventWithItsIdAndType() {
        SseParser parser = new SseParser();
        parser.accept("id: 42");
        parser.accept("event: quest_completed");
        parser.accept("data: {\"id\":42}");

        assertEquals(new SseParser.Event("42", "quest_completed", "{\"id\":42}"), parser.accept(""));
        assertEquals("42", parser.lastEventId());
    }

    @Test
    void joinsDataLinesAndIgnoresKeepAliveComments() {
        SseParser parser = new SseParser();

        assertNull(parser.accept(": keep-alive"));
        assertNull(parser.accept(""));
        parser.accept("data:first");
        parser.accept("data: second");
        SseParser.Event event = parser.accept("");

        assertEquals("first\nsecond", event.data());
        assertEquals("message", event.type());
    }

    @Test
    void turnsLeafEventsIntoPopupsAndSkipsWhatItCannotShow() {
        List<UnlockPopup> shown = new ArrayList<>();

        LeafEventStream.dispatch(new SseParser.Event("7", "leaf_earned",
                "{\"id\":7,\"type\":\"leaf_earned\",\"created_at\":\"2026-11-17T15:00:00.000Z\","
                        + "\"data\":{\"source\":\"raid\",\"leaf\":330,\"xp\":330,\"detail\":\"The Nameless Anomaly\","
                        + "\"occurred_at\":\"2026-11-17T14:59:40.000Z\"}}"), shown::add);
        LeafEventStream.dispatch(new SseParser.Event("8", "something_new", "{\"data\":{}}"), shown::add);
        LeafEventStream.dispatch(new SseParser.Event("9", "level_up", "not json"), shown::add);

        assertEquals(1, shown.size());
        assertEquals(UnlockPopup.Kind.TOAST, shown.getFirst().kind());
        assertEquals("The Nameless Anomaly", shown.getFirst().detail());
        assertTrue(shown.getFirst().leaf() == 330);
    }
}
