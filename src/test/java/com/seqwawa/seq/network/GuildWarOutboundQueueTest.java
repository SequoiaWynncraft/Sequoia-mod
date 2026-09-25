package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GuildWarOutboundQueueTest {
    private static final String QUEUED_AT = "2026-09-25T09:00:21Z";

    @AfterEach
    void resetConnection() {
        ConnectionManager.resetForTest();
    }

    @Test
    void throttledQueueRetriesWithOriginalTimestampAndTimer() {
        var queue = new GuildWarOutboundQueue();
        JsonObject original = queuePayload();
        assertTrue(queue.offer("guild_war_queue", original));

        queue.flushOne((type, payload) -> false); // Guild chat consumed this tick's send slot.
        original.addProperty("submitted_at", "2026-09-25T09:00:53Z");

        var delivered = new ArrayList<JsonObject>();
        queue.flushOne((type, payload) -> {
            assertEquals("guild_war_queue", type);
            delivered.add(payload);
            return true;
        });
        queue.flushOne((type, payload) -> {
            delivered.add(payload);
            return true;
        });
        assertEquals(1, delivered.size());
        assertEquals(QUEUED_AT, delivered.getFirst().get("submitted_at").getAsString());
        assertEquals(11, delivered.getFirst().get("queue_minutes").getAsInt());
    }

    @Test
    void disconnectedBacklogReplaysQueueCancellationAndCompletionInOrder() {
        var queue = new GuildWarOutboundQueue();
        queue.offer("guild_war_queue", queuePayload());
        queue.offer("guild_war_queue_cancel", queuePayload());
        queue.offer("guild_war_submission", queuePayload());
        queue.flushOne((type, payload) -> false);
        queue.flushOne((type, payload) -> false);

        var delivered = new ArrayList<String>();
        for (int i = 0; i < 4; i++) {
            queue.flushOne((type, payload) -> {
                delivered.add(type);
                return true;
            });
        }
        assertEquals(List.of("guild_war_queue", "guild_war_queue_cancel", "guild_war_submission"), delivered);
    }

    @Test
    void socketClosingDuringSendRetainsEventForNextConnection() {
        var queue = new GuildWarOutboundQueue();
        queue.offer("guild_war_queue", queuePayload());
        queue.flushOne((type, payload) -> ConnectionManager.tryGuildWarEventSend(() -> {
            payload.addProperty("submitted_at", "changed by unsuccessful sender");
            throw new WebsocketNotConnectedException();
        }));

        var delivered = new ArrayList<JsonObject>();
        queue.flushOne((type, payload) -> {
            delivered.add(payload);
            return true;
        });
        assertEquals(1, delivered.size());
        assertEquals(QUEUED_AT, delivered.getFirst().get("submitted_at").getAsString());
    }

    @Test
    void replacingConnectionDoesNotDiscardBufferedEvents() throws Exception {
        GuildWarOutboundQueue queue = connectionQueue();
        queue.offer("guild_war_queue", queuePayload());
        ConnectionManager first = ConnectionManager.getInstance();
        first.disconnect();
        assertNotSame(first, ConnectionManager.getInstance());

        var delivered = new ArrayList<String>();
        connectionQueue().flushOne((type, payload) -> {
            delivered.add(type);
            return true;
        });
        assertEquals(List.of("guild_war_queue"), delivered);
    }

    @Test
    void accountChangeClearsBacklogEvenBetweenConnections() throws Exception {
        connectionQueue().offer("guild_war_queue", queuePayload());
        ConnectionManager.getInstance().disconnect();
        ConnectionManager.resetForAccountChange();
        ConnectionManager.getInstance();

        var delivered = new ArrayList<String>();
        connectionQueue().flushOne((type, payload) -> {
            delivered.add(type);
            return true;
        });
        assertTrue(delivered.isEmpty());
    }

    @Test
    void fullBufferRejectsNewEventsWithoutEvictingAcceptedReports() {
        var queue = new GuildWarOutboundQueue();
        for (int i = 0; i < 1024; i++) {
            JsonObject payload = queuePayload();
            payload.addProperty("sequence", i);
            assertTrue(queue.offer("guild_war_queue", payload));
        }
        assertFalse(queue.offer("guild_war_queue", queuePayload()));
        var delivered = new ArrayList<Integer>();
        for (int i = 0; i < 1024; i++) {
            queue.flushOne((type, payload) -> {
                delivered.add(payload.get("sequence").getAsInt());
                return true;
            });
        }
        assertEquals(1024, delivered.size());
        assertEquals(0, delivered.getFirst());
        assertEquals(1023, delivered.getLast());
        assertTrue(queue.offer("guild_war_queue", queuePayload()));
    }

    private static GuildWarOutboundQueue connectionQueue() throws Exception {
        var field = ConnectionManager.class.getDeclaredField("pendingGuildWarEvents");
        field.setAccessible(true);
        return (GuildWarOutboundQueue) field.get(null);
    }

    private static JsonObject queuePayload() {
        var payload = new JsonObject();
        payload.addProperty("territory", "Balloon Airbase");
        payload.addProperty("submitted_by", "a9e1132c-c13f-4fd4-abea-812ebf35d5c3");
        payload.addProperty("submitted_at", QUEUED_AT);
        payload.addProperty("defense_rating", "Very Low");
        payload.addProperty("queue_minutes", 11);
        return payload;
    }
}
