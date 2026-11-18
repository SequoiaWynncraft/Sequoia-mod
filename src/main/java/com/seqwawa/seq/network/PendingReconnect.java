package com.seqwawa.seq.network;

import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;

/**
 * The reconnect waiting to run, if any. While one waits, automatic connects hold off
 * ({@link ConnectionManager#canAutoConnectNow()}); once it starts it no longer counts as
 * waiting, so a reconnect that fails without scheduling another cannot block them forever.
 */
final class PendingReconnect {

    private ScheduledFuture<?> task;

    /**
     * Cancels any waiting reconnect and schedules {@code schedule}'s instead. The task is
     * handed its own future, to name itself to {@link #started}.
     */
    synchronized void replace(Function<Runnable, ScheduledFuture<?>> schedule, Runnable attempt) {
        cancel();
        ScheduledFuture<?>[] self = new ScheduledFuture<?>[1];
        self[0] = schedule.apply(() -> {
            // A wrapper replaced while it waited for the lock is not the pending reconnect any more.
            if (started(self)) {
                attempt.run();
            }
        });
        task = self[0];
    }

    synchronized void cancel() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    synchronized boolean isWaiting() {
        return task != null;
    }

    /**
     * Whether {@code self} is still the pending reconnect, which it then stops being. Taking this
     * lock also waits out {@link #replace}, so {@code self} is set by then.
     */
    private synchronized boolean started(ScheduledFuture<?>[] self) {
        if (task == self[0]) {
            task = null;
            return true;
        }
        return false;
    }
}
