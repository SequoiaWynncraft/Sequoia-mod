package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.GuildWarQueueSubmission;
import com.seqwawa.seq.model.WarStatusUpdate;
import com.seqwawa.seq.model.WarTowerUpdate;
import com.seqwawa.seq.model.WynnClassType;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import com.seqwawa.seq.model.GuildWarSubmission;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class GuildWarTrackerTest {

    @Test
    void normalCaptureWithSeasonRatingPublishesInitialTowerStats() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext(
                        "LocalUser",
                        "550e8400-e29b-41d4-a716-446655440000",
                        List.of("Alpha", "Bravo", "Charlie")),
                publisher);

        warInfoProvider.currentWar =
                war("Detlas Suburbs", towerState(1_000L, 450_000L), towerState(2_000L, 0L));

        tracker.tick();
        assertTrue(publisher.warSubmissions.isEmpty());

        tracker.onSystemChat(Component.literal(
                "Territory Captured! Captured \"Detlas Suburbs\" and +410 Seasonal Rating"));

        assertEquals(1, publisher.warSubmissions.size());
        GuildWarSubmission submission = publisher.warSubmissions.getFirst();
        assertEquals("Detlas Suburbs", submission.territory());
        assertEquals(List.of("LocalUser", "Alpha", "Bravo", "Charlie"), submission.warrers());
        assertEquals(450_000L, submission.stats().health());
        assertEquals(410, submission.seasonRating());
        assertEquals("2024-03-28T01:06:40Z", submission.completedAt());
    }

    @Test
    void towerDestroyedBeforeSeasonRatingStaysPendingUntilWarDisappears() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-1", List.of("Alpha")),
                publisher);

        warInfoProvider.currentWar = war("Nemract", towerState(1_000L, 250_000L), towerState(2_000L, 0L));

        tracker.tick();
        assertTrue(publisher.warSubmissions.isEmpty());

        warInfoProvider.currentWar = null;
        tracker.tick();

        assertEquals(1, publisher.warSubmissions.size());
        GuildWarSubmission submission = publisher.warSubmissions.getFirst();
        assertEquals(0, submission.seasonRating());
        assertEquals("1970-01-01T00:00:02Z", submission.completedAt());
    }

    @Test
    void localDeathForceSendsCurrentWar() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-2", List.of("Alpha", "Bravo")),
                publisher);

        warInfoProvider.currentWar = war("Olux", towerState(1_000L, 350_000L), towerState(2_000L, 300_000L));

        tracker.tick();
        tracker.onCharacterDeath();

        assertEquals(1, publisher.warSubmissions.size());
        GuildWarSubmission submission = publisher.warSubmissions.getFirst();
        assertEquals(0, submission.seasonRating());
        assertNull(submission.completedAt());
        assertEquals(350_000L, submission.stats().health());
    }

    @Test
    void mismatchedCapturedTerritoryIsIgnored() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-3", List.of("Alpha")),
                publisher);

        warInfoProvider.currentWar = war("Llevigar Plains", towerState(1_000L, 500_000L), towerState(2_000L, 0L));

        tracker.tick();
        tracker.onSystemChat(Component.literal(
                "Territory Captured! Captured \"Wrong Territory\" and +225 Seasonal Rating"));

        assertTrue(publisher.warSubmissions.isEmpty());

        warInfoProvider.currentWar = null;
        tracker.tick();

        assertEquals(1, publisher.warSubmissions.size());
        assertEquals(0, publisher.warSubmissions.getFirst().seasonRating());
    }

    @Test
    void duplicateStateChangesDoNotResubmit() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-4", List.of("Alpha")),
                publisher);

        warInfoProvider.currentWar = war("Ahmsord", towerState(1_000L, 500_000L), towerState(2_000L, 0L));

        tracker.tick();
        tracker.onSystemChat(Component.literal(
                "Territory Captured! Captured \"Ahmsord\" and +111 Seasonal Rating"));
        tracker.tick();
        tracker.onSystemChat(Component.literal(
                "Territory Captured! Captured \"Ahmsord\" and +111 Seasonal Rating"));

        assertEquals(1, publisher.warSubmissions.size());
    }

    @Test
    void invalidNearbyWarrersFallBackToLocalPlayer() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-5", List.of("bad name", "", "ab", "???")),
                publisher);

        warInfoProvider.currentWar = war("Corkus City", towerState(1_000L, 500_000L), towerState(2_000L, 0L));

        tracker.tick();
        warInfoProvider.currentWar = null;
        tracker.tick();

        assertEquals(1, publisher.warSubmissions.size());
        assertEquals(List.of("LocalUser"), publisher.warSubmissions.getFirst().warrers());
    }

    @Test
    void queueClickWaitsForMatchingServerConfirmationAndRoundsOnlyLegacyMinutes() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-queue", List.of()),
                publisher);

        tracker.rememberQueueAttempt("Sulphuric Hollow", "Very Low");
        tracker.onSystemChat(Component.literal("The war for Detlas will start in 4 minutes."));
        assertTrue(publisher.queueSubmissions.isEmpty());

        tracker.onSystemChat(Component.literal(
                "The war for Sulphuric Hollow will start in 6 minutes and 31 seconds.\u2064\u2064"));

        assertEquals(1, publisher.queueSubmissions.size());
        GuildWarQueueSubmission submission = publisher.queueSubmissions.getFirst();
        assertEquals("Sulphuric Hollow", submission.territory());
        assertEquals("Very Low", submission.defenseRating());
        assertEquals(7, submission.queueMinutes());
    }

    @Test
    void unconfirmedQueueClickExpiresWithoutPublishing() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        long[] now = {1_711_588_000_000L};
        GuildWarTracker tracker = new GuildWarTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-queue", List.of()),
                publisher,
                () -> true,
                () -> now[0],
                false);

        tracker.rememberQueueAttempt("Detlas", "High");
        now[0] += 15_001L;
        tracker.onSystemChat(Component.literal("The war for Detlas will start in 1 minute."));

        assertTrue(publisher.queueSubmissions.isEmpty());
    }

    @Test
    void worldStatusHeartbeatsWithoutSendingForEveryCoordinateChange() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        MutablePlayerContext player = new MutablePlayerContext();
        player.warModeActive = true;
        player.classType = WynnClassType.MAGE;
        player.x = 120;
        player.z = -430;
        CapturingPublisher publisher = new CapturingPublisher();
        MutableClock clock = new MutableClock(10_000L);
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, clock);

        tracker.tick();
        assertEquals(List.of(WarStatusUpdate.world(WynnClassType.MAGE, 120, -430)), publisher.statusUpdates);

        player.x = 121;
        player.z = -431;
        tracker.tick();
        clock.advance(GuildWarTracker.STATUS_HEARTBEAT_MS - 1L);
        tracker.tick();
        assertEquals(1, publisher.statusUpdates.size());

        clock.advance(1L);
        tracker.tick();
        assertEquals(2, publisher.statusUpdates.size());
        assertEquals(121, publisher.statusUpdates.getLast().x());
        assertEquals(-431, publisher.statusUpdates.getLast().z());
    }

    @Test
    void classWarAndWarModeTransitionsPublishImmediately() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        MutablePlayerContext player = new MutablePlayerContext();
        player.warModeActive = true;
        player.classType = WynnClassType.MAGE;
        player.x = 50;
        player.z = 75;
        CapturingPublisher publisher = new CapturingPublisher();
        MutableClock clock = new MutableClock(20_000L);
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, clock);

        tracker.tick();
        player.classType = WynnClassType.ARCHER;
        tracker.tick();

        warInfoProvider.currentWar = war(
                "Entrance to Olux",
                towerState(System.currentTimeMillis() - 5_000L, 450_000L),
                towerState(System.currentTimeMillis(), 300_000L));
        tracker.tick();

        warInfoProvider.currentWar = null;
        tracker.tick();
        player.warModeActive = false;
        tracker.tick();
        tracker.tick();

        assertEquals(5, publisher.statusUpdates.size());
        assertEquals(WarStatusUpdate.Status.WORLD, publisher.statusUpdates.get(0).status());
        assertEquals(WynnClassType.ARCHER, publisher.statusUpdates.get(1).classType());
        assertEquals(WarStatusUpdate.Status.WAR, publisher.statusUpdates.get(2).status());
        assertEquals("Entrance to Olux", publisher.statusUpdates.get(2).territory());
        assertEquals(WarStatusUpdate.Status.WORLD, publisher.statusUpdates.get(3).status());
        assertEquals(WarStatusUpdate.Status.REMOVE, publisher.statusUpdates.get(4).status());
    }

    @Test
    void towerUpdatesUseDirectBossMetricsAndIndependentHeartbeat() {
        long sampledAt = System.currentTimeMillis();
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        warInfoProvider.towerUpdate = new WarTowerUpdate("Mangled Lake", 0.8731f, 400_000L, 20_000L);
        warInfoProvider.currentWar = war(
                "Mangled Lake",
                towerState(sampledAt - 5_000L, 450_000L),
                towerState(sampledAt, 300_000L));
        MutablePlayerContext player = new MutablePlayerContext();
        player.classType = WynnClassType.WARRIOR;
        CapturingPublisher publisher = new CapturingPublisher();
        MutableClock clock = new MutableClock(30_000L);
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, clock);

        tracker.tick();
        assertEquals(1, publisher.towerUpdates.size());
        WarTowerUpdate first = publisher.towerUpdates.getFirst();
        assertEquals("Mangled Lake", first.territory());
        assertEquals(0.8731f, first.health());
        assertEquals(400_000L, first.ehp());
        assertEquals(20_000L, first.dps());

        clock.advance(GuildWarTracker.TOWER_HEARTBEAT_MS - 1L);
        tracker.tick();
        assertEquals(1, publisher.towerUpdates.size());
        clock.advance(1L);
        tracker.tick();
        assertEquals(2, publisher.towerUpdates.size());
        assertEquals(1, publisher.statusUpdates.size());
    }

    @Test
    void failedSendRetriesAndReconnectResynchronizesUnchangedState() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        MutablePlayerContext player = new MutablePlayerContext();
        player.warModeActive = true;
        player.classType = WynnClassType.SHAMAN;
        CapturingPublisher publisher = new CapturingPublisher();
        publisher.ready = false;
        MutableClock clock = new MutableClock(40_000L);
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, clock);

        tracker.tick();
        assertTrue(publisher.statusAttempts.isEmpty());

        publisher.ready = true;
        tracker.tick();
        assertEquals(1, publisher.statusUpdates.size());

        publisher.ready = false;
        tracker.tick();
        publisher.ready = true;
        tracker.tick();
        assertEquals(2, publisher.statusUpdates.size());

        publisher.acceptStatus = false;
        player.classType = WynnClassType.ASSASSIN;
        tracker.tick();
        int attemptsAfterFailure = publisher.statusAttempts.size();
        tracker.tick();
        assertEquals(attemptsAfterFailure, publisher.statusAttempts.size());

        publisher.acceptStatus = true;
        clock.advance(1_000L);
        tracker.tick();
        assertEquals(WynnClassType.ASSASSIN, publisher.statusUpdates.getLast().classType());
    }

    @Test
    void publisherExceptionDoesNotEscapeClientTickAndUsesBoundedRetry() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        MutablePlayerContext player = new MutablePlayerContext();
        player.warModeActive = true;
        player.classType = WynnClassType.MAGE;
        CapturingPublisher publisher = new CapturingPublisher();
        publisher.throwStatus = true;
        MutableClock clock = new MutableClock(45_000L);
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, clock);

        tracker.tick();
        assertEquals(1, publisher.statusAttempts.size());
        tracker.tick();
        assertEquals(1, publisher.statusAttempts.size());

        publisher.throwStatus = false;
        clock.advance(1_000L);
        tracker.tick();
        assertEquals(2, publisher.statusAttempts.size());
        assertEquals(1, publisher.statusUpdates.size());
    }

    @Test
    void missingDirectBossMetricsUsesBoundedRetryWithoutPublishingWynntilsFallback() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        warInfoProvider.currentWar = war("Olux", towerState(1_000L, 400L), towerState(2_000L, 200L));
        MutablePlayerContext player = new MutablePlayerContext();
        player.classType = WynnClassType.MAGE;
        CapturingPublisher publisher = new CapturingPublisher();
        MutableClock clock = new MutableClock(47_000L);
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, clock);

        tracker.tick();
        tracker.tick();
        assertTrue(publisher.towerUpdates.isEmpty());

        warInfoProvider.towerUpdate = new WarTowerUpdate("Olux", 0.5f, 266L, 13L);
        clock.advance(999L);
        tracker.tick();
        assertTrue(publisher.towerUpdates.isEmpty());
        clock.advance(1L);
        tracker.tick();

        assertEquals(warInfoProvider.towerUpdate, publisher.towerUpdates.getFirst());
    }

    @Test
    void resetIsSilentAndNextTickAdvertisesCurrentStateAgain() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        MutablePlayerContext player = new MutablePlayerContext();
        player.warModeActive = true;
        player.classType = WynnClassType.MAGE;
        CapturingPublisher publisher = new CapturingPublisher();
        MutableClock clock = new MutableClock(50_000L);
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, clock);

        tracker.tick();
        tracker.reset();
        assertEquals(1, publisher.statusUpdates.size());

        tracker.tick();
        assertEquals(2, publisher.statusUpdates.size());
        assertEquals(WarStatusUpdate.Status.WORLD, publisher.statusUpdates.getLast().status());
    }

    @Test
    void timedAvailabilityWithoutBattlePublishesWorldThenExpiryPublishesRemove() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        MutablePlayerContext availability = new MutablePlayerContext();
        availability.warModeActive = true;
        availability.classType = WynnClassType.ARCHER;
        availability.x = 315;
        availability.z = -902;
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider, availability, publisher, new MutableClock(60_000L));

        tracker.tick();
        assertEquals(WarStatusUpdate.world(WynnClassType.ARCHER, 315, -902), publisher.statusUpdates.getLast());

        availability.warModeActive = false;
        tracker.tick();
        assertEquals(WarStatusUpdate.Status.REMOVE, publisher.statusUpdates.getLast().status());
    }

    @Test
    void inactiveReconnectPublishesAbsenceWithoutClassAndResetItselfStaysSilent() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        MutablePlayerContext player = new MutablePlayerContext();
        player.classType = null;
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, new MutableClock(70_000L));

        tracker.reset();
        assertTrue(publisher.statusUpdates.isEmpty());
        tracker.tick();

        assertEquals(1, publisher.statusUpdates.size());
        assertEquals(WarStatusUpdate.Status.REMOVE, publisher.statusUpdates.getFirst().status());
        assertNull(publisher.statusUpdates.getFirst().classType());
    }

    @Test
    void activeAvailabilityWaitsForARealPlayerPosition() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        MutablePlayerContext player = new MutablePlayerContext();
        player.warModeActive = true;
        player.classType = WynnClassType.MAGE;
        player.positionAvailable = false;
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, new MutableClock(80_000L));

        tracker.tick();
        assertTrue(publisher.statusUpdates.isEmpty());

        player.positionAvailable = true;
        player.x = 42;
        player.z = -27;
        tracker.tick();
        assertEquals(WarStatusUpdate.world(WynnClassType.MAGE, 42, -27), publisher.statusUpdates.getFirst());
    }

    @Test
    void newBattleIdForSameTerritoryForcesStatusAndTowerUpdates() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        warInfoProvider.currentWar = war("Mangled Lake", towerState(1_000L, 500L), towerState(1_100L, 400L));
        warInfoProvider.towerUpdate = new WarTowerUpdate("Mangled Lake", 0.8f, 533L, 10L);
        MutablePlayerContext player = new MutablePlayerContext();
        player.classType = WynnClassType.WARRIOR;
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, new MutableClock(90_000L));

        tracker.tick();
        warInfoProvider.currentWar = war("Mangled Lake", towerState(2_000L, 500L), towerState(2_100L, 400L));
        tracker.tick();

        assertEquals(2, publisher.statusUpdates.size());
        assertEquals(2, publisher.towerUpdates.size());
    }

    @Test
    void missingInitialTowerStateKeepsOneStableBattleIdentity() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        warInfoProvider.currentWar = new WarBattleInfo("Mangled Lake", "Sequoia", null);
        warInfoProvider.towerUpdate = new WarTowerUpdate("Mangled Lake", 0.8f, 533L, 10L);
        MutablePlayerContext player = new MutablePlayerContext();
        player.classType = WynnClassType.WARRIOR;
        CapturingPublisher publisher = new CapturingPublisher();
        MutableClock clock = new MutableClock(95_000L);
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, clock);

        tracker.tick();
        clock.advance(1L);
        tracker.tick();

        assertEquals(1, publisher.statusUpdates.size());
        assertEquals(1, publisher.towerUpdates.size());
    }

    @Test
    void disabledTrackingRetriesFailedRemoval() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        MutablePlayerContext player = new MutablePlayerContext();
        player.warModeActive = true;
        player.classType = WynnClassType.SHAMAN;
        CapturingPublisher publisher = new CapturingPublisher();
        MutableClock clock = new MutableClock(100_000L);
        AtomicBoolean enabled = new AtomicBoolean(true);
        GuildWarTracker tracker = newTracker(warInfoProvider, player, publisher, enabled::get, clock);

        tracker.tick();
        publisher.acceptStatus = false;
        enabled.set(false);
        tracker.tick();
        int failedAttempts = publisher.statusAttempts.size();
        tracker.tick();
        assertEquals(failedAttempts, publisher.statusAttempts.size());

        publisher.acceptStatus = true;
        clock.advance(1_000L);
        tracker.tick();
        assertEquals(WarStatusUpdate.Status.REMOVE, publisher.statusUpdates.getLast().status());
    }

    private GuildWarTracker newTracker(
            MutableWarInfoProvider warInfoProvider,
            GuildWarTracker.PlayerContext playerContext,
            CapturingPublisher publisher) {
        return new GuildWarTracker(warInfoProvider, playerContext, publisher, () -> true, () -> 1_711_588_000_000L, false);
    }

    private GuildWarTracker newTracker(
            MutableWarInfoProvider warInfoProvider,
            GuildWarTracker.PlayerContext playerContext,
            CapturingPublisher publisher,
            LongSupplier clock) {
        return new GuildWarTracker(warInfoProvider, playerContext, publisher, () -> true, clock, false);
    }

    private GuildWarTracker newTracker(
            MutableWarInfoProvider warInfoProvider,
            GuildWarTracker.PlayerContext playerContext,
            CapturingPublisher publisher,
            BooleanSupplier trackingEnabled,
            LongSupplier clock) {
        return new GuildWarTracker(warInfoProvider, playerContext, publisher, trackingEnabled, clock, false);
    }

    private WarBattleInfo war(String territory, WarTowerState initialState, WarTowerState currentState) {
        WarBattleInfo info = new WarBattleInfo(territory, "Sequoia", initialState);
        if (!initialState.equals(currentState)) {
            info.addNewState(currentState);
        }
        return info;
    }

    private WarTowerState towerState(long timestamp, long health) {
        return new WarTowerState(health, 0.25, new RangedValue(1200, 1800), 2.5, timestamp);
    }

    private static final class MutableWarInfoProvider implements GuildWarTracker.WarInfoProvider {
        private WarBattleInfo currentWar;
        private WarTowerUpdate towerUpdate;

        @Override
        public WarBattleInfo getCurrentWar() {
            return currentWar;
        }

        @Override
        public WarTowerUpdate towerUpdate(WarBattleInfo info) {
            return towerUpdate;
        }
    }

    private record FakePlayerContext(String localUsername, String localUuid, List<String> nearbyNames)
            implements GuildWarTracker.PlayerContext {
        @Override
        public List<String> nearbyPlayerNames(double radiusSq) {
            return nearbyNames;
        }
    }

    private static final class CapturingPublisher implements GuildWarTracker.SubmissionPublisher {
        private final ArrayList<GuildWarSubmission> warSubmissions = new ArrayList<>();
        private final ArrayList<GuildWarQueueSubmission> queueSubmissions = new ArrayList<>();
        private final ArrayList<WarStatusUpdate> statusAttempts = new ArrayList<>();
        private final ArrayList<WarStatusUpdate> statusUpdates = new ArrayList<>();
        private final ArrayList<WarTowerUpdate> towerUpdates = new ArrayList<>();
        private boolean ready = true;
        private boolean acceptStatus = true;
        private boolean throwStatus;

        @Override
        public boolean publishWar(GuildWarSubmission submission) {
            warSubmissions.add(submission);
            return true;
        }

        @Override
        public boolean publishQueue(GuildWarQueueSubmission submission) {
            queueSubmissions.add(submission);
            return true;
        }

        @Override
        public boolean liveTelemetryReady() {
            return ready;
        }

        @Override
        public boolean publishWarStatus(WarStatusUpdate update) {
            statusAttempts.add(update);
            if (throwStatus) {
                throw new IllegalStateException("socket closed");
            }
            if (acceptStatus) {
                statusUpdates.add(update);
            }
            return acceptStatus;
        }

        @Override
        public boolean publishWarTowerUpdate(WarTowerUpdate update) {
            towerUpdates.add(update);
            return true;
        }
    }

    private static final class MutablePlayerContext implements GuildWarTracker.PlayerContext {
        private boolean warModeActive;
        private WynnClassType classType;
        private int x;
        private int z;
        private boolean positionAvailable = true;

        @Override
        public String localUsername() {
            return "LocalUser";
        }

        @Override
        public String localUuid() {
            return "uuid-live";
        }

        @Override
        public List<String> nearbyPlayerNames(double radiusSq) {
            return List.of();
        }

        @Override
        public boolean warModeActive() {
            return warModeActive;
        }

        @Override
        public WynnClassType localClassType() {
            return classType;
        }

        @Override
        public GuildWarTracker.WorldPosition worldPosition() {
            return positionAvailable ? new GuildWarTracker.WorldPosition(x, z) : null;
        }
    }

    private static final class MutableClock implements LongSupplier {
        private long now;

        private MutableClock(long now) {
            this.now = now;
        }

        @Override
        public long getAsLong() {
            return now;
        }

        private void advance(long millis) {
            now += millis;
        }
    }
}
