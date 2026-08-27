package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.WarStatusUpdate;
import com.seqwawa.seq.model.WarTowerUpdate;
import com.seqwawa.seq.model.WynnClassType;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Owns the source-agnostic live-war presence and tower heartbeat state machine.
 *
 * <p>Both the optional Wynntils lifecycle tracker and the vanilla fallback feed
 * this class scalar observations. It deliberately retains no Minecraft world,
 * packet, component, or Wynntils object.
 */
final class LiveWarTelemetryTracker {
    static final long STATUS_HEARTBEAT_MS = 7_000L;
    static final long TOWER_HEARTBEAT_MS = 4_000L;
    static final long SEND_RETRY_MS = 1_000L;
    private static final long LOCAL_WAR_STATUS_FRESH_MS = STATUS_HEARTBEAT_MS * 3L;

    private final PlayerContext playerContext;
    private final Publisher publisher;
    private final BooleanSupplier trackingEnabled;
    private final LongSupplier clock;

    private PresenceKey observedPresenceKey;
    private boolean matchingWarStatusPublished;
    private long matchingWarStatusExpiresAtMillis;
    private boolean warModeObserved;
    private boolean removalPending;
    private WynnClassType lastObservedClass;
    private long nextStatusHeartbeatAtMillis;
    private long nextStatusAttemptAtMillis;
    private String observedTowerTerritory;
    private String observedTowerBattleId;
    private long nextTowerHeartbeatAtMillis;
    private boolean publisherWasReady;

    LiveWarTelemetryTracker(
            PlayerContext playerContext,
            Publisher publisher,
            BooleanSupplier trackingEnabled,
            LongSupplier clock) {
        this.playerContext = Objects.requireNonNull(playerContext, "playerContext");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.trackingEnabled = Objects.requireNonNull(trackingEnabled, "trackingEnabled");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    void tick(WarObservation observation) {
        if (!trackingEnabled.getAsBoolean()) {
            trackDisabledTelemetry();
            return;
        }

        long now = clock.getAsLong();
        boolean ready = publisher.ready();
        boolean reconnected = ready && !publisherWasReady;
        publisherWasReady = ready;
        if (!ready) {
            matchingWarStatusPublished = false;
            matchingWarStatusExpiresAtMillis = 0L;
        }

        boolean warModeActive = playerContext.warModeActive() || observation != null;
        WynnClassType classType = playerContext.localClassType();
        if (classType != null) {
            lastObservedClass = classType;
        }

        if (!warModeActive) {
            stopTowerTelemetry();
            matchingWarStatusPublished = false;
            matchingWarStatusExpiresAtMillis = 0L;
            if (warModeObserved || reconnected) {
                removalPending = true;
                observedPresenceKey = null;
                nextStatusHeartbeatAtMillis = 0L;
                nextStatusAttemptAtMillis = 0L;
            }
            warModeObserved = false;
            if (removalPending) {
                attemptRemoval(ready, now);
            }
            return;
        }

        warModeObserved = true;

        WarStatusUpdate statusUpdate = statusUpdate(observation, classType);
        if (statusUpdate == null) {
            matchingWarStatusPublished = false;
            matchingWarStatusExpiresAtMillis = 0L;
            stopTowerTelemetry();
            if (observedPresenceKey != null) {
                observedPresenceKey = null;
                removalPending = true;
                nextStatusHeartbeatAtMillis = 0L;
                nextStatusAttemptAtMillis = 0L;
            }
            if (removalPending) {
                attemptRemoval(ready, now);
            }
        } else {
            removalPending = false;
            PresenceKey presenceKey = PresenceKey.from(statusUpdate, observation);
            if (!presenceKey.equals(observedPresenceKey)) {
                observedPresenceKey = presenceKey;
                matchingWarStatusPublished = false;
                matchingWarStatusExpiresAtMillis = 0L;
                nextStatusHeartbeatAtMillis = 0L;
                nextStatusAttemptAtMillis = 0L;
                nextTowerHeartbeatAtMillis = 0L;
            }
            if (reconnected) {
                matchingWarStatusPublished = false;
                matchingWarStatusExpiresAtMillis = 0L;
                nextStatusHeartbeatAtMillis = 0L;
                nextStatusAttemptAtMillis = 0L;
            }
            if (ready
                    && now >= nextStatusHeartbeatAtMillis
                    && now >= nextStatusAttemptAtMillis) {
                if (publishWarStatus(statusUpdate)) {
                    matchingWarStatusPublished = statusUpdate.status() == WarStatusUpdate.Status.WAR;
                    matchingWarStatusExpiresAtMillis = matchingWarStatusPublished
                            ? now + LOCAL_WAR_STATUS_FRESH_MS
                            : 0L;
                    nextStatusHeartbeatAtMillis = now + STATUS_HEARTBEAT_MS;
                    nextStatusAttemptAtMillis = 0L;
                } else {
                    nextStatusAttemptAtMillis = now + SEND_RETRY_MS;
                }
            }
        }

        trackTowerTelemetry(observation, ready, reconnected, now);
    }

    void reset() {
        boolean presenceMayNeedRemoval = warModeObserved || observedPresenceKey != null || removalPending;
        observedPresenceKey = null;
        matchingWarStatusPublished = false;
        matchingWarStatusExpiresAtMillis = 0L;
        warModeObserved = false;
        removalPending = presenceMayNeedRemoval;
        lastObservedClass = null;
        nextStatusHeartbeatAtMillis = 0L;
        nextStatusAttemptAtMillis = 0L;
        observedTowerTerritory = null;
        observedTowerBattleId = null;
        nextTowerHeartbeatAtMillis = 0L;
        publisherWasReady = false;
    }

    private WarStatusUpdate statusUpdate(WarObservation observation, WynnClassType classType) {
        if (classType == null) {
            return null;
        }
        if (observation != null) {
            String territory = trimToNull(observation.territory());
            return territory == null ? null : WarStatusUpdate.war(classType, territory);
        }
        WorldPosition position = playerContext.worldPosition();
        return position == null ? null : WarStatusUpdate.world(classType, position.x(), position.z());
    }

    private void attemptRemoval(boolean ready, long now) {
        if (!ready || now < nextStatusAttemptAtMillis) {
            return;
        }
        WynnClassType classType = playerContext.localClassType();
        if (classType == null) {
            classType = lastObservedClass;
        }
        WarStatusUpdate removal = classType == null ? WarStatusUpdate.remove() : WarStatusUpdate.remove(classType);
        if (publishWarStatus(removal)) {
            removalPending = false;
            nextStatusHeartbeatAtMillis = 0L;
            nextStatusAttemptAtMillis = 0L;
        } else {
            nextStatusAttemptAtMillis = now + SEND_RETRY_MS;
        }
    }

    private void trackDisabledTelemetry() {
        long now = clock.getAsLong();
        boolean ready = publisher.ready();
        boolean reconnected = ready && !publisherWasReady;
        publisherWasReady = ready;

        if (warModeObserved || observedPresenceKey != null || reconnected) {
            removalPending = true;
            nextStatusAttemptAtMillis = 0L;
        }
        warModeObserved = false;
        observedPresenceKey = null;
        matchingWarStatusPublished = false;
        matchingWarStatusExpiresAtMillis = 0L;
        nextStatusHeartbeatAtMillis = 0L;
        stopTowerTelemetry();

        if (removalPending) {
            attemptRemoval(ready, now);
        }
    }

    private void trackTowerTelemetry(WarObservation observation, boolean ready, boolean reconnected, long now) {
        String territory = observation == null ? null : trimToNull(observation.territory());
        if (territory == null) {
            stopTowerTelemetry();
            return;
        }
        if (!territory.equalsIgnoreCase(observedTowerTerritory)
                || !Objects.equals(observation.battleId(), observedTowerBattleId)) {
            observedTowerTerritory = territory;
            observedTowerBattleId = observation.battleId();
            nextTowerHeartbeatAtMillis = 0L;
        }
        if (reconnected) {
            nextTowerHeartbeatAtMillis = 0L;
        }
        if (now >= matchingWarStatusExpiresAtMillis) {
            matchingWarStatusPublished = false;
        }
        if (!ready || !matchingWarStatusPublished || now < nextTowerHeartbeatAtMillis) {
            return;
        }

        WarTowerUpdate update = observation.towerUpdate();
        if (update == null || !territory.equalsIgnoreCase(update.territory())) {
            nextTowerHeartbeatAtMillis = now + SEND_RETRY_MS;
            return;
        }
        if (publishWarTowerUpdate(update)) {
            nextTowerHeartbeatAtMillis = now + TOWER_HEARTBEAT_MS;
        } else {
            nextTowerHeartbeatAtMillis = now + SEND_RETRY_MS;
        }
    }

    private void stopTowerTelemetry() {
        observedTowerTerritory = null;
        observedTowerBattleId = null;
        nextTowerHeartbeatAtMillis = 0L;
    }

    private boolean publishWarStatus(WarStatusUpdate update) {
        try {
            return publisher.publishWarStatus(update);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.debug("Live war status publisher failed; retrying on a later tick", exception);
            return false;
        }
    }

    private boolean publishWarTowerUpdate(WarTowerUpdate update) {
        try {
            return publisher.publishWarTowerUpdate(update);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.debug("Live war tower publisher failed; retrying on a later tick", exception);
            return false;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    interface PlayerContext {
        boolean warModeActive();

        WynnClassType localClassType();

        WorldPosition worldPosition();
    }

    interface Publisher {
        boolean ready();

        boolean publishWarStatus(WarStatusUpdate update);

        boolean publishWarTowerUpdate(WarTowerUpdate update);
    }

    record WorldPosition(int x, int z) {}

    record WarObservation(String territory, String battleId, WarTowerUpdate towerUpdate) {}

    private record PresenceKey(
            WarStatusUpdate.Status status, WynnClassType classType, String territory, String battleId) {
        private static PresenceKey from(WarStatusUpdate update, WarObservation observation) {
            String battleId = observation == null ? null : observation.battleId();
            return new PresenceKey(update.status(), update.classType(), update.territory(), battleId);
        }
    }
}
