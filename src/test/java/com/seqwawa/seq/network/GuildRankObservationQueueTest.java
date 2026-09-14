package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.*;
import com.seqwawa.seq.managers.GuildRankEventParser;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuildRankObservationQueueTest {
    private final UUID observer = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-11T12:00:00Z");

    @Test
    void retriesWithSameIdUntilAcknowledged() {
        var queue = new GuildRankObservationQueue();
        queue.add(event("Recruit", "Captain"), observer, now);
        var payload = queue.due(observer, now).getFirst();
        String id = payload.get("observation_id").getAsString();
        queue.sent(id, now);
        assertTrue(queue.due(observer, now.plusSeconds(4)).isEmpty());
        assertEquals(id, queue.due(observer, now.plusSeconds(5)).getFirst().get("observation_id").getAsString());
        queue.acknowledge(id);
        assertTrue(queue.due(observer, now.plusSeconds(6)).isEmpty());
    }

    @Test
    void suppressesRepeatedPacketButPreservesRapidReversal() {
        var queue = new GuildRankObservationQueue();
        queue.add(event("Recruit", "Captain"), observer, now);
        queue.add(event("Recruit", "Captain"), observer, now.plusMillis(100));
        queue.add(event("Captain", "Recruit"), observer, now.plusMillis(200));
        queue.add(event("Recruit", "Captain"), observer, now.plusMillis(300));
        for (int i = 0; i < 3; i++) {
            var payload = queue.due(observer, now.plusSeconds(1)).getFirst();
            queue.acknowledge(payload.get("observation_id").getAsString());
        }
        assertTrue(queue.due(observer, now.plusSeconds(1)).isEmpty());
    }

    @Test
    void neverReplaysAcrossAccountsOrAfterExpiry() {
        var queue = new GuildRankObservationQueue();
        queue.add(event("Recruit", "Captain"), observer, now);
        assertTrue(queue.due(UUID.randomUUID(), now).isEmpty());
        queue.add(event("Recruit", "Captain"), observer, now.plusSeconds(1));
        assertTrue(queue.due(observer, now.plusSeconds(602)).isEmpty());
    }

    @Test
    void keepsOnlyTheLatestHundredReportsAndPreservesTheirPayload() {
        var queue = new GuildRankObservationQueue();
        for (int i = 0; i < 101; i++) {
            queue.add(event("Recruit", "Captain"), observer, now.plusSeconds(i));
        }
        for (int i = 1; i <= 100; i++) {
            var payload = queue.due(observer, now.plusSeconds(101)).getFirst();
            assertEquals(now.plusSeconds(i).toString(), payload.get("observed_at").getAsString());
            assertEquals("Recruit", payload.get("old_rank").getAsString());
            assertEquals("Captain", payload.get("new_rank").getAsString());
            assertEquals("unresolved", payload.getAsJsonObject("actor").get("source").getAsString());
            assertTrue(payload.getAsJsonObject("actor").get("username").isJsonNull());
            assertEquals("NotReyz", payload.getAsJsonObject("target").get("username").getAsString());
            queue.acknowledge(payload.get("observation_id").getAsString());
        }
        assertTrue(queue.due(observer, now.plusSeconds(101)).isEmpty());
    }

    private GuildRankEventParser.Event event(String from, String to) {
        return new GuildRankEventParser.Event(
                new GuildRankEventParser.Identity("hardcoremaxxer", null, "unresolved"),
                new GuildRankEventParser.Identity("NotReyz", "NotReyz", "component"), from, to, "observed message");
    }
}
