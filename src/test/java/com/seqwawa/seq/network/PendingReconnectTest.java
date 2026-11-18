package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PendingReconnectTest {

    /** Holds each scheduled attempt for the test to run, behind a real (never firing) future. */
    private final ScheduledExecutorService idle = Executors.newSingleThreadScheduledExecutor();
    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();
    private final Function<Runnable, ScheduledFuture<?>> scheduler = attempt -> {
        scheduled.add(attempt);
        ScheduledFuture<?> future = idle.schedule(() -> {}, 1, TimeUnit.HOURS);
        futures.add(future);
        return future;
    };

    @AfterEach
    void stopScheduler() {
        idle.shutdownNow();
    }

    @Test
    void aReconnectThatHasStartedNoLongerHoldsOffAutomaticConnects() {
        PendingReconnect pending = new PendingReconnect();
        AtomicInteger attempts = new AtomicInteger();

        pending.replace(scheduler, attempts::incrementAndGet);
        assertTrue(pending.isWaiting());

        // It fails without scheduling another, as a throttled or refused sign-in does.
        scheduled.getFirst().run();

        assertEquals(1, attempts.get());
        assertFalse(pending.isWaiting(), "periodic recovery may connect again");
    }

    @Test
    void anOlderReconnectStartingLateDoesNotForgetTheNewerOne() {
        PendingReconnect pending = new PendingReconnect();
        pending.replace(scheduler, () -> {});
        pending.replace(scheduler, () -> {});

        assertTrue(futures.getFirst().isCancelled(), "replacing cancels the older one");
        scheduled.getFirst().run();

        assertTrue(pending.isWaiting());
    }

    @Test
    void aReconnectCanScheduleTheNextOneFromInsideItself() {
        PendingReconnect pending = new PendingReconnect();
        pending.replace(scheduler, () -> pending.replace(scheduler, () -> {}));

        scheduled.getFirst().run();

        assertTrue(pending.isWaiting());
        assertFalse(futures.get(1).isCancelled());
    }

    @Test
    void cancellingStopsTheWaitingReconnect() {
        PendingReconnect pending = new PendingReconnect();
        pending.replace(scheduler, () -> {});

        pending.cancel();

        assertFalse(pending.isWaiting());
        assertTrue(futures.getFirst().isCancelled());
    }

    @Test
    void aScheduledReconnectLeavesALiveOrConnectingSocketAlone() {
        assertTrue(ConnectionManager.scheduledReconnectStillNeeded(false, false));
        assertFalse(ConnectionManager.scheduledReconnectStillNeeded(true, false));
        assertFalse(ConnectionManager.scheduledReconnectStillNeeded(false, true));
    }
}
