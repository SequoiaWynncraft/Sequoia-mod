package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.model.war.WarPlannerAccess;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.Participant;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WarTerritoryQueueManagerTest {
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final StoredAuthSession SESSION =
            new StoredAuthSession("session-token", NOW.plusSeconds(86_400), "self-uuid", "Self");

    @TempDir
    Path tempDir;

    @Test
    void activeQueuesOrderTiesAndAdvanceUsingServerTimeEvenWhenPollingFails() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(1, NOW.plusSeconds(30), List.of(
                queue(4, "Ragni", "self-uuid", "Self", NOW, NOW.plusSeconds(60), List.of()),
                queue(3, "Alekin", "self-uuid", "Self", NOW, NOW.plusSeconds(60), List.of()),
                queue(2, "Alekin", "self-uuid", "Self", NOW, NOW.plusSeconds(60), List.of()),
                queue(1, "Alekin", "self-uuid", "Self", NOW, NOW.plusSeconds(30), List.of()),
                queue(5, "Ragni", "self-uuid", "Self", NOW, NOW.plusSeconds(90), List.of()))));

        assertEquals(List.of(2L, 3L, 4L, 5L), manager.activeQueues().stream().map(TerritoryQueue::id).toList());
        clock.advance(Duration.ofSeconds(30));
        manager.tick();
        gateway.fetchRequests.getLast().completeExceptionally(new RuntimeException("Offline"));
        assertEquals(List.of(5L), manager.activeQueues().stream().map(TerritoryQueue::id).toList());
        clock.advance(Duration.ofSeconds(30));
        assertTrue(manager.activeQueues().isEmpty());
    }

    @Test
    void findsLocalTerritoryWhenItsTimerExpiresAtThePreparationRoom() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                1,
                NOW,
                List.of(queue(
                        7,
                        NOW.minusSeconds(30),
                        NOW,
                        List.of(new Participant("self-uuid", "Self", 1))))));

        assertEquals("Alekin", manager.enteredTerritoryForLocalPlayer().orElseThrow());
        availability.available = false;
        manager.tick();
        assertEquals("Alekin", manager.enteredTerritoryForLocalPlayer().orElseThrow());
        manager.resetForWorldTransition();
        availability.playerUuid = null;
        assertEquals("Alekin", manager.enteredTerritoryForLocalPlayer().orElseThrow());
        clock.advance(Duration.ofSeconds(12));
        assertEquals("Alekin", manager.enteredTerritoryForLocalPlayer().orElseThrow());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(manager.enteredTerritoryForLocalPlayer().isEmpty());
    }

    @Test
    void preservesServerClockOffsetAcrossTheInstanceTransfer() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);
        Instant serverNow = NOW.plusSeconds(46);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                1,
                serverNow,
                List.of(queue(7, serverNow.minusSeconds(30), serverNow, List.of()))));

        manager.resetForWorldTransition();
        availability.playerUuid = null;

        assertEquals("Alekin", manager.enteredTerritoryForLocalPlayer().orElseThrow());
    }

    @Test
    void recognizesOnlyTheWarSidebarCue() {
        assertTrue(MinecraftWarWorldDetector.hasWarSidebar(List.of("§4War:", "The battle starts soon")));
        assertFalse(MinecraftWarWorldDetector.hasWarSidebar(List.of("Guild Wars", "Challenges: 0/4")));
    }

    @Test
    void recognizesTranslatedWarInstanceCoordinates() {
        assertTrue(MinecraftWarWorldDetector.hasWarInstanceCoordinates(-65_534, -65_554));
        assertFalse(MinecraftWarWorldDetector.hasWarInstanceCoordinates(-1_517, -5_130));
    }

    @Test
    void prefersTheLocalPlayersQueueWhenSeveralTimersExpireTogether() {
        FakeGateway gateway = new FakeGateway();
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, new MutableClock(NOW), availability);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                1,
                NOW,
                List.of(
                        queue(7, "Other Queue", "other-uuid", "Other", NOW, NOW.plusSeconds(1), List.of()),
                        queue(8, "Local Queue", "self-uuid", "Self", NOW, NOW.plusSeconds(2), List.of()))));

        manager.resetForWorldTransition();
        availability.playerUuid = null;
        assertEquals("Local Queue", manager.enteredTerritoryForLocalPlayer().orElseThrow());
    }

    @Test
    void acceptsOneUnownedTimerButDoesNotGuessBetweenSeveral() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, clock, availability);
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(
                1,
                NOW,
                List.of(queue(7, "Only Queue", "other-uuid", "Other", NOW, NOW.plusSeconds(2), List.of()))));

        assertEquals("Only Queue", manager.enteredTerritoryForLocalPlayer().orElseThrow());

        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        gateway.fetchRequests.get(1).complete(feed(
                2,
                NOW.plusSeconds(5),
                List.of(
                        queue(7, "First Queue", "other-uuid", "Other", NOW, NOW.plusSeconds(1), List.of()),
                        queue(8, "Second Queue", "another-uuid", "Another", NOW, NOW.plusSeconds(2), List.of()))));

        assertTrue(manager.enteredTerritoryForLocalPlayer().isEmpty());
    }

    @Test
    void disabledHudBootstrapsOnceThenPollsEveryFiveSecondsOnlyWhileAvailable() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);

        manager.tick();
        assertEquals(1, gateway.fetchCalls);
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of()));
        manager.tick();

        availability.available = true;
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
        assertEquals(WarTerritoryQueueManager.State.LOADING, manager.state());
        gateway.fetchRequests.get(1).complete(feed(
                2,
                NOW.plusSeconds(30),
                List.of(queue(7, NOW.plusSeconds(30), NOW.plusSeconds(150), List.of()))));

        assertEquals(WarTerritoryQueueManager.State.READY, manager.state());
        assertEquals(NOW.plusSeconds(30), manager.serverNow());
        assertEquals(1, manager.activeQueues().size());

        clock.advance(Duration.ofSeconds(4));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
        clock.advance(Duration.ofSeconds(1));
        manager.tick();
        assertEquals(3, gateway.fetchCalls);
        assertFalse(manager.isHudVisible());

        availability.available = false;
        manager.tick();
        assertEquals(1, manager.activeQueues().size());
        assertEquals(1, manager.feed().queues().size());
        assertEquals(WarTerritoryQueueManager.State.READY, manager.state());

        clock.advance(Duration.ofSeconds(10));
        manager.tick();
        assertEquals(3, gateway.fetchCalls);
    }

    @Test
    void unavailableViewerFetchesOnceAndJoiningRefreshesAvailabilityWithoutStartingBackgroundPolling() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);

        CompletableFuture<WarTerritoryQueueManager.ActionResult> viewerRefresh = manager.refreshForViewer();
        assertEquals(1, gateway.fetchCalls);
        gateway.fetchRequests.getFirst().complete(feed(
                1,
                NOW,
                List.of(queue(
                        7,
                        NOW.minusSeconds(30),
                        NOW.plusSeconds(120),
                        List.of(new Participant("queuer-uuid", "Queuer", 0))))));

        assertTrue(viewerRefresh.join().success());
        assertFalse(manager.isHudVisible());
        assertEquals(1, manager.activeQueues().size());
        clock.advance(Duration.ofSeconds(10));
        manager.tick();
        assertEquals(1, gateway.fetchCalls);

        CompletableFuture<WarTerritoryQueueManager.ActionResult> joined = manager.toggleQueueMembership(7);
        assertEquals(1, gateway.joinCalls);
        gateway.joinRequests.getFirst().complete(feed(
                2,
                NOW.plusSeconds(10),
                List.of(queue(
                        7,
                        NOW.minusSeconds(30),
                        NOW.plusSeconds(120),
                        List.of(
                                new Participant("queuer-uuid", "Queuer", 0),
                                new Participant("self-uuid", "Self", 1))))));

        assertTrue(joined.join().success());
        assertEquals(1, availability.refreshCalls);
        assertEquals(1, gateway.fetchCalls);
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

        assertEquals("territory_queue_not_found", manager.joinQueue(7).join().code());

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
    void rejectedObservationDoesNotEraseAnOwnedTimerWhenAvailabilityExpires() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);
        manager.tick();
        TerritoryQueue owned = queue(
                7, "Alekin", "self-uuid", "Self", NOW, NOW.plusSeconds(120), List.of());
        gateway.fetchRequests.getFirst().complete(feed(4, NOW, List.of(owned)));

        assertTrue(manager.onSystemChat(guildMessage("Other", "Other", "Ragni defense is Low")));
        availability.available = false;
        gateway.observationRequests.getFirst().completeExceptionally(new ApiClient.ApiException(
                403,
                "{\"code\":\"war_availability_required\",\"message\":\"Turn on war availability\"}"));

        assertEquals(List.of(owned), manager.activeQueues());
        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
        clock.advance(Duration.ofSeconds(115));
        manager.tick();
        assertTrue(manager.activeQueues().isEmpty());
        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
    }

    @Test
    void rejectedLeavePreservesTheJoinedTimerAndReportsTheBackendError() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);
        manager.tick();
        TerritoryQueue joined = queue(
                7, NOW.minusSeconds(30), NOW.plusSeconds(120),
                List.of(new Participant("self-uuid", "Self", 1)));
        gateway.fetchRequests.getFirst().complete(feed(4, NOW, List.of(joined)));

        CompletableFuture<WarTerritoryQueueManager.ActionResult> result = manager.toggleQueueMembership(7);
        availability.available = false;
        gateway.leaveRequests.getFirst().completeExceptionally(new ApiClient.ApiException(
                403,
                "{\"code\":\"war_availability_required\",\"message\":\"Turn on war availability\"}"));

        assertFalse(result.join().success());
        assertEquals("war_availability_required", result.join().code());
        assertEquals("Turn on war availability", result.join().message());
        assertEquals(List.of(joined), manager.activeQueues());
        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
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

    @Test
    void requestedHudKeepsForeignTimersLiveAfterAvailabilityExpires() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability, () -> true);
        manager.tick();
        TerritoryQueue foreign = queue(7, "Alekin", "other-uuid", "Other", NOW, NOW.plusSeconds(300), List.of());
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(foreign)));

        availability.available = false;
        manager.tick();
        assertTrue(manager.isHudVisible());
        assertEquals(List.of(foreign), manager.activeQueues());
        clock.advance(Duration.ofSeconds(4));
        manager.tick();
        assertEquals(1, gateway.fetchCalls);
        clock.advance(Duration.ofSeconds(1));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
        TerritoryQueue later = queue(8, "Ragni", "other-uuid", "Other", NOW, NOW.plusSeconds(200), List.of());
        gateway.fetchRequests.get(1).complete(feed(2, NOW.plusSeconds(5), List.of(foreign, later)));

        assertEquals(List.of(later, foreign), manager.activeQueues());
        assertTrue(manager.isHudVisible());
        assertEquals(0, availability.refreshCalls);
    }

    @Test
    void requestedHudOutlivesTheLastPersonalQueueAndDiscoversQueuesFromAnEmptyFeed() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability, () -> true);
        manager.tick();
        TerritoryQueue owned = queue(7, "Alekin", "self-uuid", "Self", NOW, NOW.plusSeconds(10), List.of());
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(owned)));

        clock.advance(Duration.ofSeconds(10));
        manager.tick();
        gateway.fetchRequests.get(1).complete(feed(2, NOW.plusSeconds(10), List.of()));
        assertTrue(manager.activeQueues().isEmpty());
        assertTrue(manager.isHudVisible());

        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        assertEquals(3, gateway.fetchCalls);
        TerritoryQueue discovered =
                queue(8, "Ragni", "other-uuid", "Other", NOW.plusSeconds(14), NOW.plusSeconds(300), List.of());
        gateway.fetchRequests.get(2).complete(feed(3, NOW.plusSeconds(15), List.of(discovered)));

        assertEquals(List.of(discovered), manager.activeQueues());
        assertEquals(WarTerritoryQueueManager.State.READY, manager.state());
    }

    @Test
    void disabledHudHidesTheOverlayButKeepsPersonalTrackingAndResumesWhenEnabled() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        AtomicBoolean hudEnabled = new AtomicBoolean(false);
        WarTerritoryQueueManager manager =
                new WarTerritoryQueueManager(gateway, clock, availability, hudEnabled::get);
        manager.tick();
        TerritoryQueue owned = queue(7, "Alekin", "self-uuid", "Self", NOW, NOW.plusSeconds(8), List.of());
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(owned)));

        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
        assertFalse(manager.isHudVisible());
        assertEquals(List.of(owned), manager.activeQueues());
        assertEquals("Alekin", manager.queueForTerritory("Alekin").orElseThrow().territory());

        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        clock.advance(Duration.ofSeconds(10));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);

        hudEnabled.set(true);
        manager.tick();
        assertEquals(3, gateway.fetchCalls);
        assertTrue(manager.isHudVisible());
        assertEquals(WarTerritoryQueueManager.State.LOADING, manager.state());
    }

    @Test
    void viewerRefreshUsesTheLiveHudFeedAndViewingNeverTouchesAvailabilityOrObservations() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability, () -> true);
        manager.tick();

        assertTrue(manager.refreshForViewer().join().success());
        assertEquals(1, gateway.fetchCalls);
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of()));
        assertFalse(manager.onSystemChat(guildMessage("Other", "Other", "Ragni defense is Low")));
        assertFalse(manager.onSystemChat(Component.literal("The war for Ragni will start in 2 minutes.")));

        assertEquals(0, gateway.observationCalls);
        assertEquals(0, manager.pendingObservationCount());
        assertEquals(0, availability.refreshCalls);
        assertFalse(availability.available);
    }

    @Test
    void transientFailuresKeepAcceptedCountdownsOnTheirServerDeadlines() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability, () -> true);
        Instant serverNow = NOW.plusSeconds(40);
        TerritoryQueue foreign =
                queue(7, "Alekin", "other-uuid", "Other", serverNow, serverNow.plusSeconds(20), List.of());
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(1, serverNow, List.of(foreign)));

        List<RuntimeException> failures = List.of(
                new RuntimeException("Request timed out"),
                new ApiClient.ApiException(503, "{\"code\":\"guild_roster_unavailable\",\"message\":\"Roster\"}"),
                new ApiClient.ApiException(500, "{\"code\":\"internal_error\",\"message\":\"Boom\"}"));
        for (int index = 0; index < failures.size(); index++) {
            clock.advance(Duration.ofSeconds(5));
            manager.tick();
            assertEquals(index + 2, gateway.fetchCalls);
            gateway.fetchRequests.getLast().completeExceptionally(failures.get(index));
            assertEquals(List.of(foreign), manager.activeQueues());
            assertEquals(serverNow.plusSeconds(5L * (index + 1)), manager.serverNow());
            assertTrue(manager.isHudVisible());
            assertEquals(WarTerritoryQueueManager.State.READY, manager.state());
        }

        clock.advance(Duration.ofSeconds(5));
        assertTrue(manager.activeQueues().isEmpty());
    }

    @Test
    void readDenialInvalidatesEveryConsumerBacksOffAndRecoversOnlyAfterAnAuthorizedFeed() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability, () -> true);
        TerritoryQueue owned = queue(7, NOW.minusSeconds(20), NOW.minusSeconds(1), List.of(
                new Participant("self-uuid", "Self", 1)));
        TerritoryQueue foreign = queue(8, "Ragni", "other-uuid", "Other", NOW, NOW.plusSeconds(900), List.of());
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(owned, foreign)));
        assertEquals("Alekin", manager.enteredTerritoryForLocalPlayer().orElseThrow());

        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        gateway.fetchRequests.getLast().completeExceptionally(new ApiClient.ApiException(
                403, "{\"code\":\"not_in_guild\",\"message\":\"Members only\"}"));

        assertTrue(manager.activeQueues().isEmpty());
        assertTrue(manager.feed().queues().isEmpty());
        assertTrue(manager.enteredTerritoryForLocalPlayer().isEmpty());
        assertFalse(manager.isHudVisible());
        assertEquals(WarTerritoryQueueManager.State.OFFLINE, manager.state());
        assertEquals("Members only", manager.lastError());
        WarTerritoryQueueManager.ActionResult viewer = manager.refreshForViewer().join();
        assertFalse(viewer.success());
        assertEquals("Members only", viewer.message());

        clock.advance(Duration.ofMinutes(5).minusSeconds(1));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
        clock.advance(Duration.ofSeconds(1));
        manager.tick();
        assertEquals(3, gateway.fetchCalls);
        gateway.fetchRequests.getLast().completeExceptionally(new ApiClient.ApiException(
                401, "{\"code\":\"token_expired\",\"message\":\"Expired token\"}"));
        clock.advance(Duration.ofMinutes(4));
        manager.tick();
        assertEquals(3, gateway.fetchCalls);

        clock.advance(Duration.ofMinutes(1));
        manager.tick();
        gateway.fetchRequests.getLast().complete(feed(2, NOW.plusSeconds(605), List.of(foreign)));
        assertEquals(List.of(foreign), manager.activeQueues());
        assertTrue(manager.isHudVisible());
        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        assertEquals(5, gateway.fetchCalls);
    }

    @Test
    void viewerReadDenialAlsoSuspendsBootstrapUntilTheRetryWindow() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability);

        CompletableFuture<WarTerritoryQueueManager.ActionResult> viewer = manager.refreshForViewer();
        gateway.fetchRequests.getFirst().completeExceptionally(new ApiClient.ApiException(
                403, "{\"code\":\"not_in_guild\",\"message\":\"Members only\"}"));

        assertEquals("not_in_guild", viewer.join().code());
        manager.tick();
        assertFalse(manager.refreshForViewer().join().success());
        assertEquals(1, gateway.fetchCalls);
        clock.advance(Duration.ofMinutes(5));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
    }

    @Test
    void explicitPlannerDenialSuspendsReadsButRosterVerificationOutageDoesNot() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability, () -> true);
        TerritoryQueue foreign = queue(8, "Ragni", "other-uuid", "Other", NOW, NOW.plusSeconds(900), List.of());
        manager.tick();
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(foreign)));

        // A planner roster outage clears its access, so only the cached identity remains.
        availability.playerUuid = null;
        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
        assertEquals("self-uuid", manager.localPlayerUuid());
        assertEquals(List.of(foreign), manager.activeQueues());

        availability.accessDenied = true;
        manager.tick();
        assertTrue(manager.activeQueues().isEmpty());
        assertFalse(manager.isHudVisible());
        gateway.fetchRequests.getLast().complete(feed(2, NOW.plusSeconds(5), List.of(foreign)));
        assertTrue(manager.activeQueues().isEmpty());
        clock.advance(Duration.ofMinutes(1));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);

        availability.accessDenied = false;
        manager.tick();
        assertEquals(3, gateway.fetchCalls);
        gateway.fetchRequests.getLast().complete(feed(3, NOW.plusSeconds(65), List.of(foreign)));
        assertEquals(List.of(foreign), manager.activeQueues());
        assertTrue(manager.isHudVisible());
    }

    @Test
    void contextResetsClearReadDenialSoTheNextContextReadsImmediately() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability, () -> true);
        manager.tick();
        gateway.fetchRequests.getFirst().completeExceptionally(new ApiClient.ApiException(
                403, "{\"code\":\"not_in_guild\",\"message\":\"Members only\"}"));
        manager.tick();
        assertEquals(1, gateway.fetchCalls);

        manager.resetForWorldTransition();
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
        gateway.fetchRequests.get(1).completeExceptionally(new ApiClient.ApiException(
                403, "{\"code\":\"not_in_guild\",\"message\":\"Members only\"}"));
        manager.reset();
        assertEquals(WarTerritoryQueueManager.State.INACTIVE, manager.state());

        availability.playerUuid = "next-uuid";
        manager.tick();
        assertEquals(3, gateway.fetchCalls);
        TerritoryQueue foreign = queue(8, "Ragni", "other-uuid", "Other", NOW, NOW.plusSeconds(900), List.of());
        gateway.fetchRequests.get(2).complete(feed(1, NOW, List.of(foreign)));
        assertEquals(List.of(foreign), manager.activeQueues());
        assertTrue(manager.isHudVisible());
    }

    @Test
    void staleCallbacksFromAReplacedContextNeitherDenyNorRepopulateIt() {
        FakeGateway gateway = new FakeGateway();
        gateway.ignoreFetchCancellation = true;
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability, () -> true);
        TerritoryQueue current = queue(8, "Ragni", "other-uuid", "Other", NOW, NOW.plusSeconds(900), List.of());
        TerritoryQueue stale = queue(9, "Detlas", "other-uuid", "Other", NOW, NOW.plusSeconds(900), List.of());
        manager.tick();
        manager.reset();
        availability.playerUuid = "next-uuid";
        manager.tick();
        gateway.fetchRequests.getFirst().completeExceptionally(new ApiClient.ApiException(
                403, "{\"code\":\"not_in_guild\",\"message\":\"Members only\"}"));

        assertTrue(manager.isHudVisible());
        gateway.fetchRequests.get(1).complete(feed(1, NOW, List.of(current)));
        assertEquals(List.of(current), manager.activeQueues());

        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        manager.resetForWorldTransition();
        manager.tick();
        assertEquals(4, gateway.fetchCalls);
        gateway.fetchRequests.get(2).complete(feed(9, NOW.plusSeconds(5), List.of(stale)));
        assertTrue(manager.activeQueues().isEmpty());
        gateway.fetchRequests.get(3).complete(feed(2, NOW.plusSeconds(5), List.of(current)));
        assertEquals(List.of(current), manager.activeQueues());
    }

    @Test
    void rejectedObservationIsAWriteFailureThatLeavesThePassiveFeedLive() {
        FakeGateway gateway = new FakeGateway();
        MutableClock clock = new MutableClock(NOW);
        FakeAvailability availability = new FakeAvailability();
        availability.available = true;
        WarTerritoryQueueManager manager = new WarTerritoryQueueManager(gateway, clock, availability, () -> true);
        manager.tick();
        TerritoryQueue foreign = queue(8, "Ragni", "other-uuid", "Other", NOW, NOW.plusSeconds(900), List.of());
        gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(foreign)));

        assertTrue(manager.onSystemChat(guildMessage("Other", "Other", "Ragni defense is Low")));
        availability.available = false;
        gateway.observationRequests.getFirst().completeExceptionally(new ApiClient.ApiException(
                403,
                "{\"code\":\"war_availability_required\",\"message\":\"Turn on war availability\"}"));

        assertEquals(List.of(foreign), manager.activeQueues());
        assertTrue(manager.isHudVisible());
        clock.advance(Duration.ofSeconds(5));
        manager.tick();
        assertEquals(2, gateway.fetchCalls);
    }

    @Test
    void explicitLogoutClearsTheLiveFeedAndPersonalContextUntilReauthentication() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        ArrayDeque<CompletableFuture<WarPlannerAccess>> accessResponses = new ArrayDeque<>();
        WarPlannerManager planner = new WarPlannerManager(plannerGateway(accessResponses), clock);
        withRuntimeSession(planner, config -> {
            FakeGateway gateway = new FakeGateway();
            gateway.ignoreFetchCancellation = true;
            WarTerritoryQueueManager manager = new WarTerritoryQueueManager(
                    gateway, clock, new WarTerritoryQueueManager.RuntimeAvailabilityContext(), () -> true);
            TerritoryQueue foreign =
                    queue(8, "Ragni", "other-uuid", "Other", NOW, NOW.plusSeconds(900), List.of());
            TerritoryQueue owned = queue(7, "Alekin", "self-uuid", "Self", NOW, NOW.plusSeconds(900), List.of());
            accessResponses.add(CompletableFuture.completedFuture(plannerAccess()));
            planner.tick(true, true);
            manager.tick();
            gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(owned, foreign)));
            clock.advance(Duration.ofSeconds(5));
            manager.tick();
            assertTrue(manager.isHudVisible());
            assertEquals(2, gateway.fetchCalls);

            // Mirrors /seq logout: the session token is cleared and planner access reset.
            config.clearAuthSession();
            planner.reset();
            manager.tick();

            assertFalse(manager.isHudVisible());
            assertTrue(manager.activeQueues().isEmpty());
            assertNull(manager.localPlayerUuid());
            gateway.fetchRequests.get(1).complete(feed(2, NOW.plusSeconds(5), List.of(owned, foreign)));
            assertTrue(manager.activeQueues().isEmpty());
            assertFalse(manager.refreshForViewer().join().success());
            clock.advance(Duration.ofSeconds(30));
            planner.tick(true, false);
            manager.tick();
            assertEquals(2, gateway.fetchCalls);

            config.setAuthSession(SESSION);
            accessResponses.add(CompletableFuture.completedFuture(plannerAccess()));
            planner.tick(true, true);
            manager.tick();
            assertEquals(3, gateway.fetchCalls);
            gateway.fetchRequests.get(2).complete(feed(3, NOW.plusSeconds(35), List.of(foreign)));
            assertEquals(List.of(foreign), manager.activeQueues());
            assertTrue(manager.isHudVisible());
        });
    }

    @Test
    void plannerRosterOutageWithARetainedSessionKeepsTimersLive() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        ArrayDeque<CompletableFuture<WarPlannerAccess>> accessResponses = new ArrayDeque<>();
        WarPlannerManager planner = new WarPlannerManager(plannerGateway(accessResponses), clock);
        withRuntimeSession(planner, config -> {
            FakeGateway gateway = new FakeGateway();
            WarTerritoryQueueManager manager = new WarTerritoryQueueManager(
                    gateway, clock, new WarTerritoryQueueManager.RuntimeAvailabilityContext(), () -> true);
            TerritoryQueue foreign =
                    queue(8, "Ragni", "other-uuid", "Other", NOW, NOW.plusSeconds(900), List.of());
            accessResponses.add(CompletableFuture.completedFuture(plannerAccess()));
            planner.tick(true, true);
            manager.tick();
            gateway.fetchRequests.getFirst().complete(feed(1, NOW, List.of(foreign)));

            clock.advance(Duration.ofSeconds(45));
            accessResponses.add(CompletableFuture.failedFuture(new ApiClient.ApiException(
                    503, "{\"code\":\"guild_roster_unavailable\",\"message\":\"Roster unavailable\"}")));
            planner.tick(true, true);
            assertNull(planner.playerUuid());
            assertEquals(WarPlannerManager.State.OFFLINE, planner.state());
            manager.tick();

            assertEquals(2, gateway.fetchCalls);
            assertTrue(manager.isHudVisible());
            assertEquals("self-uuid", manager.localPlayerUuid());
            assertEquals(List.of(foreign), manager.activeQueues());
        });
    }

    private interface SessionScenario {
        void run(ConfigManager config) throws Exception;
    }

    /** Installs a real token store and planner as the runtime context sees them, then restores both. */
    private void withRuntimeSession(WarPlannerManager planner, SessionScenario scenario) throws Exception {
        ConfigManager previousConfig = SeqClient.configManager;
        WarPlannerManager previousPlanner = SeqClient.warPlannerManager;
        var constructor = ConfigManager.class.getDeclaredConstructor(Path.class, Path.class, boolean.class);
        constructor.setAccessible(true);
        ConfigManager config =
                constructor.newInstance(tempDir.resolve("config.json"), tempDir.resolve("token"), false);
        config.setAuthSession(SESSION);
        SeqClient.configManager = config;
        SeqClient.warPlannerManager = planner;
        try {
            scenario.run(config);
        } finally {
            SeqClient.configManager = previousConfig;
            SeqClient.warPlannerManager = previousPlanner;
        }
    }

    private static WarPlannerAccess plannerAccess() {
        return new WarPlannerAccess(WarPlannerAccess.SUPPORTED_SCHEMA_VERSION, NOW, "self-uuid", null);
    }

    /** Only access() is exercised; every other planner request is a test failure. */
    private static WarPlannerManager.Gateway plannerGateway(
            ArrayDeque<CompletableFuture<WarPlannerAccess>> accessResponses) {
        return (WarPlannerManager.Gateway) Proxy.newProxyInstance(
                WarPlannerManager.Gateway.class.getClassLoader(),
                new Class<?>[] {WarPlannerManager.Gateway.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("access")) {
                        return accessResponses.removeFirst();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
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
        private boolean accessDenied;
        private String playerUuid = "self-uuid";
        private int refreshCalls;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public String playerUuid() {
            return playerUuid;
        }

        @Override
        public void refreshAvailability() {
            refreshCalls++;
        }

        @Override
        public boolean accessDenied() {
            return accessDenied;
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
        /** Lets a test deliver a response after reset, as a request racing cancellation would. */
        private boolean ignoreFetchCancellation;

        @Override
        public CompletableFuture<WarTerritoryQueueFeed> fetch() {
            fetchCalls++;
            CompletableFuture<WarTerritoryQueueFeed> request = ignoreFetchCancellation
                    ? new CompletableFuture<>() {
                        @Override
                        public boolean cancel(boolean mayInterruptIfRunning) {
                            return false;
                        }
                    }
                    : new CompletableFuture<>();
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
