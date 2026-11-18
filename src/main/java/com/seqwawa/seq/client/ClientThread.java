package com.seqwawa.seq.client;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The game thread, and the one way to hand work to it.
 * <p>
 * Web requests, the backend socket and timers finish on their own threads. Their results
 * must not write anything the game reads; they are handed here and applied at the start
 * of the next client tick, at most 50 ms later. Minecraft's {@code mc.execute} is the wrong
 * tool for that: called on the game thread it runs the task at once, so a callback can
 * re-enter the code that started it, and Minecraft drops its queued tasks when you leave
 * a server, which leaves a "loading" flag stuck for good. This queue does neither.
 * <p>
 * Development builds log, once per place, code that should run on the game thread but
 * doesn't; see {@link #check(String)}.
 */
public final class ClientThread {

    private static final TickQueue QUEUE = new TickQueue(Clocks::monotonicMillis);
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();
    private static volatile Thread gameThread;
    private static volatile boolean checking;

    private ClientThread() {}

    /** Drains the queue at the start of every client tick. Called once, when the mod starts. */
    public static void install() {
        checking = FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("seq.threadChecks");
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            gameThread = Thread.currentThread();
            QUEUE.runDue();
        });
    }

    /** Hands results to the game thread, for {@code whenCompleteAsync} and the like. */
    public static Executor executor() {
        return QUEUE;
    }

    /** Runs {@code task} on the game thread at the start of the next tick, even when called from it. */
    public static void execute(Runnable task) {
        QUEUE.execute(task);
    }

    /**
     * Runs {@code task} on the game thread at the first tick after {@code delay}. Use it
     * instead of {@code CompletableFuture.delayedExecutor}, whose tasks run on a JDK timer
     * thread.
     */
    public static TickQueue.Timer after(Duration delay, Runnable task) {
        return QUEUE.after(delay.toMillis(), task);
    }

    /** Whether this is the game thread. True before the first tick, when it isn't known yet. */
    public static boolean isCurrent() {
        Thread thread = gameThread;
        return thread == null || thread == Thread.currentThread();
    }

    /**
     * Notes, in development builds, that {@code what} ran off the game thread. Each
     * {@code what} is reported once, with the stack that got there.
     */
    public static void check(String what) {
        if (checking && !isCurrent() && reported.add(what)) {
            SeqClient.LOGGER.warn(
                    "[ClientThread] {} ran on {}, not the game thread",
                    what,
                    Thread.currentThread().getName(),
                    new IllegalStateException("off the game thread"));
        }
    }
}
