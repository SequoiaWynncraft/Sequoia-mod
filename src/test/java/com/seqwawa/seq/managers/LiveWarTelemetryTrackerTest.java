package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.WarStatusUpdate;
import com.seqwawa.seq.model.WarTowerUpdate;
import com.seqwawa.seq.model.WynnClassType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class LiveWarTelemetryTrackerTest {
    private static final WarTowerUpdate DETLAS_TOWER = new WarTowerUpdate("Detlas", 0.75f, 400_000L, 3_750L);
    private static final LiveWarTelemetryTracker.WarObservation DETLAS_WAR =
            new LiveWarTelemetryTracker.WarObservation("Detlas", "boss-1", DETLAS_TOWER);

    @Test
    void unknownClassSuppressesWarAndTowerThenLateResolutionPublishesInOrder() {
        MutablePlayerContext player = new MutablePlayerContext();
        CapturingPublisher publisher = new CapturingPublisher();
        LiveWarTelemetryTracker tracker = tracker(player, publisher, new MutableClock(10_000L));

        tracker.tick(DETLAS_WAR);
        assertTrue(publisher.events.isEmpty());

        player.classType = WynnClassType.WARRIOR;
        tracker.tick(DETLAS_WAR);

        assertEquals(List.of("status:WAR:Detlas", "tower:Detlas"), publisher.events);
    }

    @Test
    void classLossRemovesPresenceAndBlocksTowerUntilWarIsReestablished() {
        MutablePlayerContext player = new MutablePlayerContext();
        player.classType = WynnClassType.MAGE;
        CapturingPublisher publisher = new CapturingPublisher();
        LiveWarTelemetryTracker tracker = tracker(player, publisher, new MutableClock(20_000L));

        tracker.tick(DETLAS_WAR);
        player.classType = null;
        tracker.tick(DETLAS_WAR);
        player.classType = WynnClassType.ARCHER;
        tracker.tick(DETLAS_WAR);

        assertEquals(
                List.of(
                        "status:WAR:Detlas",
                        "tower:Detlas",
                        "status:REMOVE:null",
                        "status:WAR:Detlas",
                        "tower:Detlas"),
                publisher.events);
    }

    @Test
    void failedWarStatusBlocksTowerAndUsesBoundedRetry() {
        MutablePlayerContext player = new MutablePlayerContext();
        player.classType = WynnClassType.SHAMAN;
        CapturingPublisher publisher = new CapturingPublisher();
        publisher.acceptStatus = false;
        MutableClock clock = new MutableClock(30_000L);
        LiveWarTelemetryTracker tracker = tracker(player, publisher, clock);

        tracker.tick(DETLAS_WAR);
        tracker.tick(DETLAS_WAR);
        assertEquals(1, publisher.statusAttempts);
        assertTrue(publisher.towerUpdates.isEmpty());

        publisher.acceptStatus = true;
        clock.advance(LiveWarTelemetryTracker.SEND_RETRY_MS);
        tracker.tick(DETLAS_WAR);

        assertEquals(List.of("status:WAR:Detlas", "tower:Detlas"), publisher.events);
    }

    @Test
    void prolongedHeartbeatFailureEventuallyClosesTheLocalTowerGate() {
        MutablePlayerContext player = new MutablePlayerContext();
        player.classType = WynnClassType.SHAMAN;
        CapturingPublisher publisher = new CapturingPublisher();
        MutableClock clock = new MutableClock(35_000L);
        LiveWarTelemetryTracker tracker = tracker(player, publisher, clock);

        tracker.tick(DETLAS_WAR);
        publisher.acceptStatus = false;
        clock.advance(LiveWarTelemetryTracker.STATUS_HEARTBEAT_MS);
        tracker.tick(DETLAS_WAR);
        clock.advance(LiveWarTelemetryTracker.STATUS_HEARTBEAT_MS);
        tracker.tick(DETLAS_WAR);
        int towerUpdatesBeforeExpiry = publisher.towerUpdates.size();
        clock.advance(LiveWarTelemetryTracker.STATUS_HEARTBEAT_MS);
        tracker.tick(DETLAS_WAR);

        assertEquals(4, publisher.statusAttempts);
        assertEquals(towerUpdatesBeforeExpiry, publisher.towerUpdates.size());
    }

    @Test
    void reconnectAndNewBossIdentityReadvertiseWarBeforeTower() {
        MutablePlayerContext player = new MutablePlayerContext();
        player.classType = WynnClassType.ASSASSIN;
        CapturingPublisher publisher = new CapturingPublisher();
        LiveWarTelemetryTracker tracker = tracker(player, publisher, new MutableClock(40_000L));

        tracker.tick(DETLAS_WAR);
        publisher.ready = false;
        tracker.tick(DETLAS_WAR);
        publisher.ready = true;
        tracker.tick(DETLAS_WAR);
        tracker.tick(new LiveWarTelemetryTracker.WarObservation("Detlas", "boss-2", DETLAS_TOWER));

        assertEquals(
                List.of(
                        "status:WAR:Detlas",
                        "tower:Detlas",
                        "status:WAR:Detlas",
                        "tower:Detlas",
                        "status:WAR:Detlas",
                        "tower:Detlas"),
                publisher.events);
    }

    private LiveWarTelemetryTracker tracker(
            MutablePlayerContext player, CapturingPublisher publisher, LongSupplier clock) {
        return new LiveWarTelemetryTracker(player, publisher, () -> true, clock);
    }

    private static final class MutablePlayerContext implements LiveWarTelemetryTracker.PlayerContext {
        private boolean warModeActive;
        private WynnClassType classType;
        private LiveWarTelemetryTracker.WorldPosition position =
                new LiveWarTelemetryTracker.WorldPosition(10, -20);

        @Override
        public boolean warModeActive() {
            return warModeActive;
        }

        @Override
        public WynnClassType localClassType() {
            return classType;
        }

        @Override
        public LiveWarTelemetryTracker.WorldPosition worldPosition() {
            return position;
        }
    }

    private static final class CapturingPublisher implements LiveWarTelemetryTracker.Publisher {
        private final ArrayList<String> events = new ArrayList<>();
        private final ArrayList<WarTowerUpdate> towerUpdates = new ArrayList<>();
        private boolean ready = true;
        private boolean acceptStatus = true;
        private int statusAttempts;

        @Override
        public boolean ready() {
            return ready;
        }

        @Override
        public boolean publishWarStatus(WarStatusUpdate update) {
            statusAttempts++;
            if (!acceptStatus) {
                return false;
            }
            events.add("status:" + update.status() + ":" + update.territory());
            return true;
        }

        @Override
        public boolean publishWarTowerUpdate(WarTowerUpdate update) {
            towerUpdates.add(update);
            events.add("tower:" + update.territory());
            return true;
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
