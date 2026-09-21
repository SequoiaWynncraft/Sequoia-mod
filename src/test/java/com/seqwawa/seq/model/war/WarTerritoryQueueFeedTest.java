package com.seqwawa.seq.model.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.Participant;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarTerritoryQueueFeedTest {
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void gsonNormalizesTimerOnlyQueueAsUnknownWithAReservedOwnerSlot() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>)
                        (json, type, context) -> Instant.parse(json.getAsString()))
                .create();

        WarTerritoryQueueFeed feed = gson.fromJson(
                """
                {
                  "schema_version": 1,
                  "revision": 7,
                  "queues": [{"id": 4, "territory": " Alekin ", "nickname": " "}]
                }
                """,
                WarTerritoryQueueFeed.class);

        assertTrue(feed.isSupported());
        TerritoryQueue queue = feed.queues().getFirst();
        assertEquals("Alekin", queue.territory());
        assertEquals(null, queue.queuedBy());
        assertEquals(null, queue.minecraftUsername());
        assertEquals(null, queue.nickname());
        assertEquals(null, queue.queuedDefenseRating());
        assertEquals(null, queue.reportedDefenseRating());
        assertEquals("Unknown", queue.displayName());
        assertTrue(queue.participants().isEmpty());
        assertEquals(1, queue.participantCount());
        assertFalse(queue.full());
    }

    @Test
    void nestedCollectionsAreImmutableDefensiveCopies() {
        ArrayList<Participant> participants = new ArrayList<>();
        participants.add(new Participant("player-1", "Player", 0));
        TerritoryQueue queue = queue(participants);
        participants.clear();

        assertEquals(1, queue.participantCount());
        assertThrows(
                UnsupportedOperationException.class,
                () -> queue.participants().add(new Participant("player-2", "Other", 1)));
    }

    @Test
    void queueHelpersFormatIdentityCapacityMembershipAndExpiry() {
        TerritoryQueue queue = new TerritoryQueue(
                4L,
                "Alekin",
                "fallback-uuid",
                "xiaolongbao",
                "Soup Person",
                "Low",
                "Very High",
                NOW.minusSeconds(30),
                NOW,
                List.of(
                        new Participant("SELF", "Player", 0),
                        new Participant("two", "Two", 1),
                        new Participant("three", "Three", 2),
                        new Participant("four", "Four", 3),
                        new Participant("five", "Five", 4)));

        assertEquals("xiaolongbao/Soup Person", queue.displayName());
        assertEquals(5, queue.participantCount());
        assertTrue(queue.full());
        assertTrue(queue.hasParticipant("self"));
        assertTrue(queue.isExpired(NOW));
        assertFalse(queue.isExpired(NOW.minusMillis(1)));
    }

    @Test
    void provisionalQueueBecomesFullAfterFourJoinersBecauseTheOwnerSlotIsReserved() {
        TerritoryQueue queue = new TerritoryQueue(
                5L,
                "Detlas",
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW.plusSeconds(420),
                List.of(
                        new Participant("one", "One", 1),
                        new Participant("two", "Two", 2),
                        new Participant("three", "Three", 3),
                        new Participant("four", "Four", 4)));

        assertEquals("Unknown", queue.displayName());
        assertEquals(5, queue.participantCount());
        assertTrue(queue.full());
        assertTrue(queue.hasParticipant("FOUR"));
    }

    private static TerritoryQueue queue(List<Participant> participants) {
        return new TerritoryQueue(
                4L,
                "Alekin",
                "fallback-uuid",
                "xiaolongbao",
                null,
                "Low",
                null,
                NOW.minusSeconds(30),
                NOW.plusSeconds(120),
                participants);
    }
}
