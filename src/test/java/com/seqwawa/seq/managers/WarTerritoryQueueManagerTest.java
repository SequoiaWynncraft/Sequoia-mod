package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.war.WarTerritoryQueueFeed;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.Participant;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import com.seqwawa.seq.network.ApiClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class WarTerritoryQueueManagerTest {
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void pollsEveryFiveSecondsOnlyWhileAvailableAndUsesServerClock() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);

        manager.tick();
        assertEquals(0, gateway.fetchCalls);
        assertFalse(manager.isActive());

        availability.available = true;
        manager.tick();
        assertEquals(1, gateway.fetchCalls);
        assertEquals(WarTerritoryQueueManager.State.LOADING, manager.state());
        gateway.fetchRequests.getFirst().complete(feed(
                1,
                NOW.plusSeconds(30),
                List.of(queue(7, NOW.plusSeconds(30), NOW.plusSeconds(150), List.of()))));

        assertEquals(WarTerritoryQueueManager.State.READY, manager.state());
        assertEquals(NOW.plusSeconds(30), manager.serverNow());
        assertEquals(1, manager.activeQueues().size());

        clock.advance(Duration.ofSeconds(4));
        manager.tick();
        assertEquals(1, gateway.fetchCalls);
        clock.advance(Duration.ofSeconds(1));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);

        availability.available = false;
        manager.tick();
        assertFalse(manager.isActive());
        assertTrue(manager.activeQueues().isEmpty());
        assertTrue(manager.feed().queues().isEmpty());
        assertEquals(WarTerritoryQueueManager.State.INACTIVE, manager.state());
    }

    @Test
    void missedWarRequiresExactNormalizedTextUsesServerClockAtLowerBoundAndOnlyFiresOnce() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        List<String> messages = new ArrayList<>();
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, clock, availability, messages::add);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                7,
                NOW.plusSeconds(30),
                List.of(queue(
                        41,
                        "Sulphuric Hollow",
                        "alpha-uuid",
                        "Alpha",
                        NOW.plusSeconds(5),
                        NOW.plusSeconds(35),
                        List.of(
                                new Participant("charlie-uuid", "Charlie", 2),
                                new Participant("alpha-uuid", "Alpha", 0),
                                new Participant("bravo-uuid", "Bravo", 1))))));

        clock.advance(Duration.ofSeconds(5));
        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertTrue(messages.isEmpty());

        clock.advance(Duration.ofSeconds(1));
        manager.onSystemChat(Component.literal("Nobody logged in for the war!"));
        manager.onSystemChat(Component.literal("nobody logged in for the war."));
        manager.onSystemChat(Component.literal("Notice: Nobody logged in for the war."));
        assertTrue(messages.isEmpty());

        manager.onSystemChat(Component.literal("\u2064Nobody logged in for the war.\u2064"));
        assertEquals(
                List.of(
                        "Nobody entered Sulphuric Hollow. Alpha, Bravo, and Charlie were last seen fighting the login button."),
                messages);

        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertEquals(1, messages.size());
    }

    @Test
    void disabledQueueMissMessagesConsumeTheMissWithoutDisplayingOrReplayingBlame() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        List<String> messages = new ArrayList<>();
        boolean[] messagesEnabled = {false};
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(
                gateway, clock, availability, messages::add, () -> messagesEnabled[0]);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                7,
                NOW,
                List.of(queue(
                        41,
                        "Sulphuric Hollow",
                        "alpha-uuid",
                        "Alpha",
                        NOW.minusSeconds(25),
                        NOW.plusSeconds(5),
                        List.of(new Participant("alpha-uuid", "Alpha", 0))))));

        clock.advance(Duration.ofSeconds(6));
        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertTrue(messages.isEmpty());

        messagesEnabled[0] = true;
        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertTrue(messages.isEmpty(), "an ignored miss must not surface after the setting is enabled");

        manager.tick();
        gateway.fetchRequests.get(1).complete(feed(
                8,
                NOW.plusSeconds(6),
                List.of(queue(
                        42,
                        "Detlas",
                        "bravo-uuid",
                        "Bravo",
                        NOW.plusSeconds(6),
                        NOW.plusSeconds(11),
                        List.of(new Participant("bravo-uuid", "Bravo", 0))))));
        clock.advance(Duration.ofSeconds(6));
        manager.onSystemChat(Component.literal("Nobody logged in for the war."));

        assertEquals(1, messages.size(), "new misses should use the setting's current value");
        assertTrue(messages.getFirst().contains("Detlas"));
    }

    @Test
    void missedWarWindowIncludesTenSecondsAndExcludesElevenSeconds() {
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;

        FakeGateway boundaryGateway = new FakeGateway();
        MutableClock boundaryClock = new MutableClock(NOW);
        List<String> boundaryMessages = new ArrayList<>();
        WarTerritoryQueueManager boundaryManager =
                new WarTerritoryQueueManager(boundaryGateway, boundaryClock, availability, boundaryMessages::add);
        boundaryManager.tick();
        boundaryGateway.fetchRequests.getFirst().complete(feed(
                3,
                NOW,
                List.of(queue(
                        11,
                        "Detlas",
                        "alpha-uuid",
                        "Alpha",
                        NOW.minusSeconds(25),
                        NOW.plusSeconds(5),
                        List.of(new Participant("alpha-uuid", "Alpha", 0))))));
        boundaryClock.advance(Duration.ofSeconds(15));

        boundaryManager.onSystemChat(Component.literal("Nobody logged in for the war."));
        boundaryManager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertEquals(
                List.of(
                        "Nobody entered Detlas. Alpha was last seen fighting the login button."),
                boundaryMessages);

        FakeGateway lateGateway = new FakeGateway();
        MutableClock lateClock = new MutableClock(NOW);
        List<String> lateMessages = new ArrayList<>();
        WarTerritoryQueueManager lateManager =
                new WarTerritoryQueueManager(lateGateway, lateClock, availability, lateMessages::add);
        lateManager.tick();
        lateGateway.fetchRequests.getFirst().complete(feed(
                4,
                NOW,
                List.of(queue(
                        12,
                        "Ragni",
                        "bravo-uuid",
                        "Bravo",
                        NOW.minusSeconds(25),
                        NOW.plusSeconds(5),
                        List.of(new Participant("bravo-uuid", "Bravo", 0))))));
        lateClock.advance(Duration.ofSeconds(16));

        lateManager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertTrue(lateMessages.isEmpty());
    }

    @Test
    void missedWarRetainsExpiredSnapshotAcrossEqualRevisionEmptyFeed() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        List<String> messages = new ArrayList<>();
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, clock, availability, messages::add);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                9,
                NOW,
                List.of(queue(
                        21,
                        "Nesaak",
                        "alpha-uuid",
                        "Alpha",
                        NOW.minusSeconds(25),
                        NOW.plusSeconds(5),
                        List.of(new Participant("alpha-uuid", "Alpha", 0))))));

        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        gateway.fetchRequests.get(1).complete(feed(9, NOW.plusSeconds(5), List.of()));
        assertTrue(manager.activeQueues().isEmpty());

        clock.advance(Duration.ofSeconds(1));
        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertEquals(
                List.of("Alpha chose peace. Unfortunately, they queued a war at Nesaak."),
                messages);
    }

    @Test
    void missedWarIgnoresOlderEqualRevisionFeedAndKeepsLatestClockAndSnapshot() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        List<String> messages = new ArrayList<>();
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, clock, availability, messages::add);
        manager.tick();
        manager.onSystemChat(Component.literal("The war for Latest Territory will start in 1 minute."));

        Instant latestServerTime = NOW.plusSeconds(30);
        gateway.observationRequests.getFirst().complete(feed(
                12,
                latestServerTime,
                List.of(queue(
                        61,
                        "Latest Territory",
                        "latest-uuid",
                        "LatestPlayer",
                        NOW.plusSeconds(5),
                        NOW.plusSeconds(35),
                        List.of(new Participant("latest-uuid", "LatestPlayer", 0))))));
        gateway.fetchRequests.getFirst().complete(feed(
                12,
                NOW,
                List.of(queue(
                        60,
                        "Stale Territory",
                        "stale-uuid",
                        "StalePlayer",
                        NOW.minusSeconds(25),
                        NOW.plusSeconds(5),
                        List.of(new Participant("stale-uuid", "StalePlayer", 0))))));

        assertEquals(latestServerTime, manager.feed().serverTime());
        assertEquals(61, manager.feed().queues().getFirst().id());
        clock.advance(Duration.ofSeconds(6));

        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertEquals(
                List.of("Latest Territory called. LatestPlayer sent it straight to voicemail."),
                messages);
    }

    @Test
    void missedWarDiscardsQueueThatDisappearsBeforeExpiry() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        List<String> messages = new ArrayList<>();
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, clock, availability, messages::add);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                5,
                NOW,
                List.of(queue(
                        22,
                        "Llevigar",
                        "alpha-uuid",
                        "Alpha",
                        NOW.minusSeconds(20),
                        NOW.plusSeconds(10),
                        List.of(new Participant("alpha-uuid", "Alpha", 0))))));

        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        gateway.fetchRequests.get(1).complete(feed(5, NOW.plusSeconds(5), List.of()));
        clock.advance(Duration.ofSeconds(6));

        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertTrue(messages.isEmpty());
    }

    @Test
    void missedWarDiscardsPreExpiryReplacementEvenWhenFeedArrivesAfterOldExpiry() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        List<String> messages = new ArrayList<>();
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, clock, availability, messages::add);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                20,
                NOW,
                List.of(queue(
                        70,
                        "Detlas",
                        "alpha-uuid",
                        "Alpha",
                        NOW.minusSeconds(25),
                        NOW.plusSeconds(5),
                        List.of(new Participant("alpha-uuid", "Alpha", 0))))));

        clock.advance(Duration.ofSeconds(6));
        manager.tick();
        gateway.fetchRequests.get(1).complete(feed(
                21,
                NOW.plusSeconds(6),
                List.of(queue(
                        71,
                        "Detlas",
                        "bravo-uuid",
                        "Bravo",
                        NOW.plusSeconds(4),
                        NOW.plusSeconds(35),
                        List.of(new Participant("bravo-uuid", "Bravo", 0))))));
        assertEquals(71, manager.queueForTerritory("Detlas").orElseThrow().id());

        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertTrue(messages.isEmpty());

        clock.advance(Duration.ofSeconds(30));
        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertEquals(
                List.of("Nobody entered Detlas. Bravo was last seen fighting the login button."),
                messages);
    }

    @Test
    void missedWarNarrowsMultipleCandidatesToExactlyOneContainingLocalPlayer() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        List<String> messages = new ArrayList<>();
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, clock, availability, messages::add);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                8,
                NOW,
                List.of(
                        queue(
                                31,
                                "Cinfras",
                                "alpha-uuid",
                                "Alpha",
                                NOW.minusSeconds(25),
                                NOW.plusSeconds(5),
                                List.of(
                                        new Participant("alpha-uuid", "Alpha", 0),
                                        new Participant("self-uuid", "Self", 1))),
                        queue(
                                32,
                                "Troms",
                                "bravo-uuid",
                                "Bravo",
                                NOW.minusSeconds(25),
                                NOW.plusSeconds(5),
                                List.of(new Participant("bravo-uuid", "Bravo", 0))))));
        clock.advance(Duration.ofSeconds(6));

        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertEquals(
                List.of("Cinfras called. Alpha and Self sent it straight to voicemail."),
                messages);

        FakeGateway ambiguousGateway = new FakeGateway();
        MutableClock ambiguousClock = new MutableClock(NOW);
        List<String> ambiguousMessages = new ArrayList<>();
        WarTerritoryQueueManager ambiguousManager =
                new WarTerritoryQueueManager(ambiguousGateway, ambiguousClock, availability, ambiguousMessages::add);
        ambiguousManager.tick();
        ambiguousGateway.fetchRequests.getFirst().complete(feed(
                9,
                NOW,
                List.of(
                        queue(
                                33,
                                "Aldorei",
                                "alpha-uuid",
                                "Alpha",
                                NOW.minusSeconds(25),
                                NOW.plusSeconds(5),
                                List.of(
                                        new Participant("alpha-uuid", "Alpha", 0),
                                        new Participant("self-uuid", "Self", 1))),
                        queue(
                                34,
                                "Thesead",
                                "bravo-uuid",
                                "Bravo",
                                NOW.minusSeconds(25),
                                NOW.plusSeconds(5),
                                List.of(
                                        new Participant("bravo-uuid", "Bravo", 0),
                                        new Participant("self-uuid", "Self", 1))))));
        ambiguousClock.advance(Duration.ofSeconds(6));

        ambiguousManager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertTrue(ambiguousMessages.isEmpty());
    }

    @Test
    void missedWarUsesUnknownForProvisionalOwnerAndOrdersExplicitParticipantsByPosition() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        List<String> messages = new ArrayList<>();
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, clock, availability, messages::add);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                6,
                NOW,
                List.of(new TerritoryQueue(
                        51,
                        "Kandon-Beda",
                        null,
                        null,
                        null,
                        null,
                        null,
                        NOW.minusSeconds(25),
                        NOW.plusSeconds(5),
                        List.of(
                                new Participant("charlie-uuid", "Charlie", 2),
                                new Participant("bravo-uuid", "Bravo", 1))))));
        clock.advance(Duration.ofSeconds(6));

        manager.onSystemChat(Component.literal("Nobody logged in for the war."));
        assertEquals(
                List.of(
                        "Unknown, Bravo, and Charlie chose peace. Unfortunately, they queued a war at Kandon-Beda."),
                messages);
    }

    @Test
    void missedWarBlameRotationUsesAllSixQueueIdVariantsAndSingularPluralLoginVerbs() {
        List<String> expected = List.of(
                "Territory-0 started with nobody inside. Blame Alpha, apparently they queued for moral support.",
                "Territory-1 called. Alpha sent it straight to voicemail.",
                "The war at Territory-2 began exactly as Alpha planned: without them.",
                "Alpha chose peace. Unfortunately, they queued a war at Territory-3.",
                "Breaking: Alpha successfully avoided the war they queued for at Territory-4.",
                "Nobody entered Territory-5. Alpha was last seen fighting the login button.");

        for (int variant = 0; variant < expected.size(); variant++) {
            TerritoryQueue queue = queue(
                    60 + variant,
                    "Territory-" + variant,
                    "alpha-uuid",
                    "Alpha",
                    NOW.minusSeconds(30),
                    NOW.plusSeconds(30),
                    List.of(new Participant("alpha-uuid", "Alpha", 0)));
            assertEquals(expected.get(variant), WarTerritoryQueueManager.formatMissedWarBlame(queue));
        }

        TerritoryQueue plural = queue(
                71,
                "Plural Territory",
                "alpha-uuid",
                "Alpha",
                NOW.minusSeconds(30),
                NOW.plusSeconds(30),
                List.of(
                        new Participant("alpha-uuid", "Alpha", 0),
                        new Participant("bravo-uuid", "Bravo", 1)));
        assertEquals(
                "Nobody entered Plural Territory. Alpha and Bravo were last seen fighting the login button.",
                WarTerritoryQueueManager.formatMissedWarBlame(plural));
    }

    @Test
    void strictParserUsesGuildColorInvisibleNormalizationAndNicknameMetadata() {
        Component valid = guildMessage("Soup Person", "xiaolongbao", "Alekin defense is Very High");

        WarTerritoryQueueManager.Observation parsed =
                WarTerritoryQueueManager.parseGuildObservation(valid).orElseThrow();

        assertEquals("xiaolongbao", parsed.minecraftUsername());
        assertEquals("Soup Person", parsed.nickname());
        assertEquals("Alekin", parsed.territory());
        assertEquals("Very High", parsed.defenseRating());

        assertTrue(WarTerritoryQueueManager.parseGuildObservation(
                        guildMessage("xiaolongbao", "xiaolongbao", "Alekin defense is Low"))
                .isPresent());
        assertTrue(WarTerritoryQueueManager.parseGuildObservation(
                        Component.literal("xiaolongbao: Alekin defense is Low"))
                .isEmpty());
        assertTrue(WarTerritoryQueueManager.parseGuildObservation(
                        guildMessage("xiaolongbao", "xiaolongbao", "Alekin defense is very high"))
                .isEmpty());
        assertTrue(WarTerritoryQueueManager.parseGuildObservation(
                        guildMessage("xiaolongbao", "xiaolongbao", "Alekin defense is High."))
                .isEmpty());
    }

    @Test
    void queueConfirmationParserMirrorsServerMinuteAndSecondWording() {
        assertEquals(
                new WarTerritoryQueueManager.QueueConfirmation("Sulphuric Hollow", 420),
                WarTerritoryQueueManager.parseQueueConfirmation(
                                Component.literal("The war for Sulphuric Hollow will start in 7 minutes.\u2064\u2064"))
                        .orElseThrow());
        assertEquals(
                60,
                WarTerritoryQueueManager.parseQueueConfirmation("The war for Detlas will start in 1 minute.")
                        .orElseThrow()
                        .durationSeconds());
        assertEquals(
                45,
                WarTerritoryQueueManager.parseQueueConfirmation("The war for Detlas will start in 45 seconds.")
                        .orElseThrow()
                        .durationSeconds());
        assertEquals(
                65,
                WarTerritoryQueueManager.parseQueueConfirmation(
                                "The war for Detlas will start in 1 minute and 5 seconds.")
                        .orElseThrow()
                        .durationSeconds());
        assertTrue(WarTerritoryQueueManager.parseQueueConfirmation(
                        "The war for Detlas will start in 0 seconds.")
                .isEmpty());
        assertTrue(WarTerritoryQueueManager.parseQueueConfirmation(
                        "The war for Detlas will start in 61 minutes.")
                .isEmpty());
        assertTrue(WarTerritoryQueueManager.parseQueueConfirmation(
                        "The war for Detlas will start soon.")
                .isEmpty());
    }

    @Test
    void queueConfirmationImmediatelySubmitsProvisionalTimerThenDefenseEnrichesUntimed() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        MutableClock clock = new MutableClock(NOW);
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);
        manager.tick();

        assertFalse(manager.onSystemChat(
                Component.literal("The war for Sulphuric Hollow will start in 7 minutes.\u2064\u2064")));

        WarTerritoryQueueManager.Observation confirmation = gateway.observations.getFirst();
        assertNull(confirmation.minecraftUsername());
        assertNull(confirmation.nickname());
        assertEquals("Sulphuric Hollow", confirmation.territory());
        assertNull(confirmation.defenseRating());
        assertEquals(420, confirmation.queueDurationSeconds());

        clock.advance(Duration.ofSeconds(2));
        assertTrue(manager.onSystemChat(guildMessage(
                "oMysteryCat", "xiaolongbao", "sulphuric hollow defense is Very Low")));
        assertEquals(1, gateway.observations.size());
        gateway.observationRequests.getFirst().complete(WarTerritoryQueueFeed.empty());
        manager.tick();

        WarTerritoryQueueManager.Observation observation = gateway.observations.get(1);
        assertEquals("xiaolongbao", observation.minecraftUsername());
        assertEquals("oMysteryCat", observation.nickname());
        assertEquals("sulphuric hollow", observation.territory());
        assertEquals("Very Low", observation.defenseRating());
        assertNull(observation.queueDurationSeconds());
    }

    @Test
    void defenseBeforeConfirmationRemainsUntimedAndBothMessageTypesDedupeIndependently() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC), availability);
        manager.tick();
        Component defense = guildMessage("Someone Else", "OtherPlayer", "Detlas defense is High");
        Component confirmation = Component.literal("The war for Detlas will start in 45 seconds.");

        assertTrue(manager.onSystemChat(defense));
        assertNull(gateway.observations.getFirst().queueDurationSeconds());
        assertFalse(manager.onSystemChat(defense));
        assertFalse(manager.onSystemChat(confirmation));
        assertFalse(manager.onSystemChat(confirmation));
        assertEquals(2, manager.pendingObservationCount());

        gateway.observationRequests.getFirst().complete(WarTerritoryQueueFeed.empty());
        manager.tick();

        assertEquals(2, gateway.observations.size());
        assertNull(gateway.observations.get(1).minecraftUsername());
        assertNull(gateway.observations.get(1).defenseRating());
        assertEquals(45, gateway.observations.get(1).queueDurationSeconds());
    }

    @Test
    void nonRetriableTimerFailureDoesNotBlockQueuedDefenseFallback() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC), availability);
        manager.tick();

        manager.onSystemChat(Component.literal("The war for Detlas will start in 1 minute."));
        assertTrue(manager.onSystemChat(
                guildMessage("oMysteryCat", "xiaolongbao", "Detlas defense is Low")));
        gateway.observationRequests.getFirst().completeExceptionally(
                new ApiClient.ApiException(400, "{\"code\":\"invalid_observation\"}"));
        manager.tick();

        assertEquals(2, gateway.observations.size());
        assertEquals("xiaolongbao", gateway.observations.get(1).minecraftUsername());
        assertEquals("Low", gateway.observations.get(1).defenseRating());
        assertNull(gateway.observations.get(1).queueDurationSeconds());
    }

    @Test
    void timerOnlyDurationIsRecomputedAfterWaitingBehindAnotherObservation() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        MutableClock clock = new MutableClock(NOW);
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);
        manager.tick();

        assertTrue(manager.onSystemChat(
                guildMessage("oMysteryCat", "xiaolongbao", "Ragni defense is Low")));
        manager.onSystemChat(Component.literal("The war for Detlas will start in 1 minute."));

        clock.advance(Duration.ofSeconds(10));
        gateway.observationRequests.getFirst().complete(WarTerritoryQueueFeed.empty());
        manager.tick();

        assertEquals(2, gateway.observations.size());
        assertEquals("Detlas", gateway.observations.get(1).territory());
        assertNull(gateway.observations.get(1).minecraftUsername());
        assertNull(gateway.observations.get(1).defenseRating());
        assertEquals(50, gateway.observations.get(1).queueDurationSeconds());
    }

    @Test
    void failedTimerOnlyObservationRetriesWithFreshRemainingDuration() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        MutableClock clock = new MutableClock(NOW);
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);
        manager.tick();

        manager.onSystemChat(Component.literal("The war for Detlas will start in 1 minute."));
        assertEquals(60, gateway.observations.getFirst().queueDurationSeconds());
        assertNull(gateway.observations.getFirst().minecraftUsername());
        assertNull(gateway.observations.getFirst().defenseRating());
        gateway.observationRequests.getFirst().completeExceptionally(new IllegalStateException("offline"));

        clock.advance(Duration.ofSeconds(4));
        manager.tick();
        assertEquals(1, gateway.observations.size());
        clock.advance(Duration.ofSeconds(1));
        manager.tick();

        assertEquals(2, gateway.observations.size());
        assertEquals(55, gateway.observations.get(1).queueDurationSeconds());
    }

    @Test
    void confirmedTimerDisplacesUntimedBacklogAndDispatchesFirst() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        MutableClock clock = new MutableClock(NOW);
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);
        manager.tick();

        for (int index = 0; index <= WarTerritoryQueueManager.MAX_PENDING_OBSERVATIONS; index++) {
            assertTrue(manager.onSystemChat(guildMessage(
                    "oMysteryCat", "xiaolongbao", "Backlog " + index + " defense is Medium")));
        }
        manager.onSystemChat(Component.literal("The war for Priority will start in 1 minute."));

        assertEquals(WarTerritoryQueueManager.MAX_PENDING_OBSERVATIONS + 1, manager.pendingObservationCount());
        gateway.observationRequests.getFirst().complete(WarTerritoryQueueFeed.empty());
        manager.tick();

        assertEquals("Priority", gateway.observations.get(1).territory());
        assertNull(gateway.observations.get(1).minecraftUsername());
        assertNull(gateway.observations.get(1).defenseRating());
        assertEquals(60, gateway.observations.get(1).queueDurationSeconds());
    }

    @Test
    void observationsAreAvailabilityGatedDeduplicatedAndBounded() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        MutableClock clock = new MutableClock(NOW);
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);
        Component duplicate = guildMessage("xiaolongbao", "xiaolongbao", "Alekin defense is Low");

        assertFalse(manager.onSystemChat(duplicate));
        availability.available = true;
        manager.tick();
        assertTrue(manager.onSystemChat(duplicate));
        assertFalse(manager.onSystemChat(duplicate));

        int accepted = 1;
        for (int index = 0; index < 40; index++) {
            if (manager.onSystemChat(guildMessage(
                    "xiaolongbao", "xiaolongbao", "Territory " + index + " defense is Medium"))) {
                accepted++;
            }
        }

        assertEquals(1, gateway.observationCalls);
        assertEquals(WarTerritoryQueueManager.MAX_PENDING_OBSERVATIONS + 1, manager.pendingObservationCount());
        assertEquals(WarTerritoryQueueManager.MAX_PENDING_OBSERVATIONS + 1, accepted);
    }

    @Test
    void joinIsIdempotentAndAppliesTheReturnedFeed() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC), availability);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                1, NOW, List.of(queue(7, NOW.minusSeconds(30), NOW.plusSeconds(120), List.of()))));

        CompletableFuture<WarTerritoryQueueManager.ActionResult> first = manager.joinQueue(7);
        CompletableFuture<WarTerritoryQueueManager.ActionResult> duplicate = manager.joinQueue(7);

        assertSame(first, duplicate);
        assertEquals(1, gateway.joinCalls);
        gateway.joinRequests.getFirst().complete(feed(
                2,
                NOW,
                List.of(queue(
                        7,
                        NOW.minusSeconds(30),
                        NOW.plusSeconds(120),
                        List.of(new Participant("self-uuid", "Self", 0))))));

        assertTrue(first.join().success());
        assertTrue(manager.queueForTerritory("alEKin").orElseThrow().hasParticipant("self-uuid"));
        assertTrue(manager.joinQueue(7).join().success());
        assertEquals(1, gateway.joinCalls);
    }

    @Test
    void toggleJoinsAnUnjoinedQueueAndCoalescesRepeatedRequests() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC), availability);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                1,
                NOW,
                List.of(queue(
                        7,
                        NOW.minusSeconds(30),
                        NOW.plusSeconds(120),
                        List.of(new Participant("queuer-uuid", "Queuer", 0))))));

        CompletableFuture<WarTerritoryQueueManager.ActionResult> first = manager.toggleQueueMembership(7);
        CompletableFuture<WarTerritoryQueueManager.ActionResult> duplicate = manager.toggleQueueMembership(7);

        assertSame(first, duplicate);
        assertEquals(1, gateway.joinCalls);
        assertEquals(0, gateway.leaveCalls);
        gateway.joinRequests.getFirst().complete(feed(
                2,
                NOW,
                List.of(queue(
                        7,
                        NOW.minusSeconds(30),
                        NOW.plusSeconds(120),
                        List.of(
                                new Participant("queuer-uuid", "Queuer", 0),
                                new Participant("self-uuid", "Self", 1))))));

        assertTrue(first.join().success());
        assertEquals("Joined territory queue.", first.join().message());
        assertTrue(manager.queueForTerritory("Alekin").orElseThrow().hasParticipant("self-uuid"));
    }

    @Test
    void toggleLetsBackendReconcileAStaleLocallyFullQueue() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC), availability);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(queue(
                7,
                NOW.minusSeconds(30),
                NOW.plusSeconds(120),
                List.of(
                        new Participant("queuer-uuid", "Queuer", 0),
                        new Participant("one-uuid", "One", 1),
                        new Participant("two-uuid", "Two", 2),
                        new Participant("three-uuid", "Three", 3),
                        new Participant("stale-uuid", "Stale", 4))))));

        CompletableFuture<WarTerritoryQueueManager.ActionResult> result = manager.toggleQueueMembership(7);

        assertEquals(1, gateway.joinCalls);
        assertFalse(manager.queueForTerritory("Alekin").orElseThrow().hasParticipant("self-uuid"));
        gateway.joinRequests.getFirst().complete(feed(2, NOW, List.of(queue(
                7,
                NOW.minusSeconds(30),
                NOW.plusSeconds(120),
                List.of(
                        new Participant("queuer-uuid", "Queuer", 0),
                        new Participant("one-uuid", "One", 1),
                        new Participant("two-uuid", "Two", 2),
                        new Participant("three-uuid", "Three", 3),
                        new Participant("self-uuid", "Self", 4))))));

        assertTrue(result.join().success());
        assertTrue(manager.queueForTerritory("Alekin").orElseThrow().hasParticipant("self-uuid"));
    }

    @Test
    void toggleLeavesAJoinedNonOwnerAndAppliesOnlyTheReturnedFeed() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC), availability);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                4,
                NOW,
                List.of(queue(
                        7,
                        NOW.minusSeconds(30),
                        NOW.plusSeconds(120),
                        List.of(
                                new Participant("queuer-uuid", "Queuer", 0),
                                new Participant("self-uuid", "Self", 1))))));

        CompletableFuture<WarTerritoryQueueManager.ActionResult> first = manager.toggleQueueMembership(7);
        CompletableFuture<WarTerritoryQueueManager.ActionResult> duplicate = manager.toggleQueueMembership(7);

        assertSame(first, duplicate);
        assertEquals(0, gateway.joinCalls);
        assertEquals(1, gateway.leaveCalls);
        assertTrue(manager.queueForTerritory("Alekin").orElseThrow().hasParticipant("self-uuid"));
        gateway.leaveRequests.getFirst().complete(feed(
                5,
                NOW,
                List.of(queue(
                        7,
                        NOW.minusSeconds(30),
                        NOW.plusSeconds(120),
                        List.of(new Participant("queuer-uuid", "Queuer", 0))))));

        assertTrue(first.join().success());
        assertEquals("Left territory queue.", first.join().message());
        assertFalse(manager.queueForTerritory("Alekin").orElseThrow().hasParticipant("self-uuid"));
    }

    @Test
    void toggleKeepsQueueOwnerInReservedSlotWithoutCallingBackend() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC), availability);
        manager.tick();
        TerritoryQueue owned = new TerritoryQueue(
                7,
                "Alekin",
                "self-uuid",
                "Self",
                null,
                "Low",
                "Very High",
                NOW.minusSeconds(30),
                NOW.plusSeconds(120),
                List.of(new Participant("self-uuid", "Self", 0)));
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(owned)));

        WarTerritoryQueueManager.ActionResult result = manager.toggleQueueMembership(7).join();

        assertTrue(result.success());
        assertEquals("You queued this territory and remain its owner.", result.message());
        assertEquals(0, gateway.joinCalls);
        assertEquals(0, gateway.leaveCalls);
        assertTrue(manager.queueForTerritory("Alekin").orElseThrow().hasParticipant("self-uuid"));
    }

    @Test
    void joinPreflightUsesBackendStableErrorCodes() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC), availability);

        assertEquals("war_availability_required", manager.joinQueue(7).join().code());

        availability.available = true;
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(queue(
                7,
                NOW.minusSeconds(30),
                NOW.plusSeconds(120),
                List.of(
                        new Participant("one", "One", 0),
                        new Participant("two", "Two", 1),
                        new Participant("three", "Three", 2),
                        new Participant("four", "Four", 3),
                        new Participant("five", "Five", 4))))));

        assertEquals("territory_queue_not_found", manager.joinQueue(99).join().code());
        assertEquals("territory_queue_full", manager.joinQueue(7).join().code());
        assertEquals(0, gateway.joinCalls);
    }

    @Test
    void backendJoinErrorCodeAndMessageAreSurfacedWithoutReplacingFeed() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC), availability);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                4, NOW, List.of(queue(7, NOW.minusSeconds(30), NOW.plusSeconds(120), List.of()))));

        CompletableFuture<WarTerritoryQueueManager.ActionResult> result = manager.joinQueue(7);
        gateway.joinRequests.getFirst().completeExceptionally(new ApiClient.ApiException(
                409,
                "{\"code\":\"territory_queue_full\",\"message\":\"Five people already joined\"}"));

        assertFalse(result.join().success());
        assertEquals("territory_queue_full", result.join().code());
        assertEquals("Five people already joined", result.join().message());
        assertEquals(4, manager.feed().revision());
    }

    private static Component guildMessage(String displayedName, String username, String content) {
        return Component.empty()
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF)))
                .append(Component.literal("\u2064\u2064 "))
                .append(Component.literal(displayedName).withStyle(Style.EMPTY.withInsertion(username)))
                .append(Component.literal(": " + content));
    }

    private static WarTerritoryQueueFeed feed(long revision, Instant serverTime, List<TerritoryQueue> queues) {
        return new WarTerritoryQueueFeed(1, revision, serverTime, queues);
    }

    private static TerritoryQueue queue(
            long id, Instant queuedAt, Instant expiresAt, List<Participant> participants) {
        return new TerritoryQueue(
                id,
                "Alekin",
                "queuer-uuid",
                "xiaolongbao",
                "Soup Person",
                "Low",
                "Very High",
                queuedAt,
                expiresAt,
                participants);
    }

    private static TerritoryQueue queue(
            long id,
            String territory,
            String queuedBy,
            String minecraftUsername,
            Instant queuedAt,
            Instant expiresAt,
            List<Participant> participants) {
        return new TerritoryQueue(
                id,
                territory,
                queuedBy,
                minecraftUsername,
                null,
                "Low",
                "Very High",
                queuedAt,
                expiresAt,
                participants);
    }

    private static final class FakeAvailability implements WarTerritoryQueueManager.AvailabilityContext {
        private boolean available;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public String playerUuid() {
            return "self-uuid";
        }
    }

    private static final class FakeGateway implements WarTerritoryQueueManager.Gateway {
        private final List<CompletableFuture<WarTerritoryQueueFeed>> fetchRequests = new ArrayList<>();
        private final List<CompletableFuture<WarTerritoryQueueFeed>> observationRequests = new ArrayList<>();
        private final List<WarTerritoryQueueManager.Observation> observations = new ArrayList<>();
        private final List<CompletableFuture<WarTerritoryQueueFeed>> joinRequests = new ArrayList<>();
        private final List<CompletableFuture<WarTerritoryQueueFeed>> leaveRequests = new ArrayList<>();
        private int fetchCalls;
        private int observationCalls;
        private int joinCalls;
        private int leaveCalls;

        @Override
        public CompletableFuture<WarTerritoryQueueFeed> fetch() {
            fetchCalls++;
            CompletableFuture<WarTerritoryQueueFeed> request = new CompletableFuture<>();
            fetchRequests.add(request);
            return request;
        }

        @Override
        public CompletableFuture<WarTerritoryQueueFeed> observe(WarTerritoryQueueManager.Observation observation) {
            observationCalls++;
            observations.add(observation);
            CompletableFuture<WarTerritoryQueueFeed> request = new CompletableFuture<>();
            observationRequests.add(request);
            return request;
        }

        @Override
        public CompletableFuture<WarTerritoryQueueFeed> join(long queueId) {
            joinCalls++;
            CompletableFuture<WarTerritoryQueueFeed> request = new CompletableFuture<>();
            joinRequests.add(request);
            return request;
        }

        @Override
        public CompletableFuture<WarTerritoryQueueFeed> leave(long queueId) {
            leaveCalls++;
            CompletableFuture<WarTerritoryQueueFeed> request = new CompletableFuture<>();
            leaveRequests.add(request);
            return request;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
