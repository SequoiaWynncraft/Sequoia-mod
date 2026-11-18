package com.seqwawa.seq.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Work for one thread, run when that thread calls {@link #runDue()}: {@link ClientThread}
 * calls it at the start of every client tick, and a test calls it when it chooses.
 * <p>
 * Any thread may add work. A run first collects what is queued and the timers due, then
 * runs them; work added once its tasks have started running waits for the next run, so a
 * task that queues another can't keep one tick busy, and a task never runs inside the
 * call that queued it. A task that throws is logged, and the rest still run.
 */
public final class TickQueue implements Executor {

    private final LongSupplier clockMs;
    private final ConcurrentLinkedQueue<Runnable> ready = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<Timer> timers =
            new PriorityQueue<>(Comparator.comparingLong(Timer::dueAtMs).thenComparingLong(Timer::order));
    private long timerCount;

    public TickQueue(LongSupplier clockMs) {
        this.clockMs = Objects.requireNonNull(clockMs, "clockMs");
    }

    /** Runs {@code task} on the next run. */
    @Override
    public void execute(Runnable task) {
        ready.add(Objects.requireNonNull(task, "task"));
    }

    /** Runs {@code task} on the first run at least {@code delayMs} from now, unless cancelled first. */
    public Timer after(long delayMs, Runnable task) {
        Objects.requireNonNull(task, "task");
        synchronized (timers) {
            Timer timer = new Timer(clockMs.getAsLong() + Math.max(0, delayMs), ++timerCount, task);
            timers.add(timer);
            return timer;
        }
    }

    /** Collects the queued work and the timers due, then runs them. Returns how many tasks ran. */
    public int runDue() {
        List<Runnable> batch = new ArrayList<>();
        for (Runnable task; (task = ready.poll()) != null; ) {
            batch.add(task);
        }
        long now = clockMs.getAsLong();
        synchronized (timers) {
            while (!timers.isEmpty() && timers.peek().dueAtMs() <= now) {
                batch.add(timers.poll());
            }
        }
        int ran = 0;
        for (Runnable task : batch) {
            if (task instanceof Timer timer && timer.cancelled) {
                continue;
            }
            ran++;
            try {
                task.run();
            } catch (RuntimeException failure) {
                SeqClient.LOGGER.error("[ClientThread] A queued task failed", failure);
            }
        }
        return ran;
    }

    /** A task waiting for its time. */
    public static final class Timer implements Runnable {
        private final long dueAtMs;
        private final long order;
        private final Runnable task;
        private volatile boolean cancelled;

        private Timer(long dueAtMs, long order, Runnable task) {
            this.dueAtMs = dueAtMs;
            this.order = order;
            this.task = task;
        }

        /** Stops the task from running, unless it already has. */
        public void cancel() {
            cancelled = true;
        }

        private long dueAtMs() {
            return dueAtMs;
        }

        private long order() {
            return order;
        }

        @Override
        public void run() {
            task.run();
        }
    }
}
