package com.seqwawa.seq.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TickQueueTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final TickQueue queue = new TickQueue(now::get);

    @Test
    void runsQueuedWorkInOrderOnlyWhenTold() {
        List<String> ran = new ArrayList<>();
        queue.execute(() -> ran.add("first"));
        queue.execute(() -> ran.add("second"));

        assertEquals(List.of(), ran, "queueing never runs the task in the caller");
        assertEquals(2, queue.runDue());
        assertEquals(List.of("first", "second"), ran);
        assertEquals(0, queue.runDue());
    }

    @Test
    void workQueuedDuringARunWaitsForTheNextOne() {
        List<String> ran = new ArrayList<>();
        queue.execute(() -> {
            ran.add("outer");
            queue.execute(() -> ran.add("inner"));
        });

        queue.runDue();
        assertEquals(List.of("outer"), ran);
        queue.runDue();
        assertEquals(List.of("outer", "inner"), ran);
    }

    @Test
    void aFailingTaskDoesNotStopTheOthers() {
        AtomicInteger ran = new AtomicInteger();
        queue.execute(() -> {
            throw new IllegalStateException("broken task");
        });
        queue.execute(ran::incrementAndGet);

        assertEquals(2, queue.runDue());
        assertEquals(1, ran.get());
    }

    @Test
    void timersRunAtTheFirstRunAfterTheirDelayInDueOrder() {
        List<String> ran = new ArrayList<>();
        queue.after(500, () -> ran.add("later"));
        queue.after(200, () -> ran.add("sooner"));
        queue.after(200, () -> ran.add("sooner, queued second"));

        now.addAndGet(199);
        assertEquals(0, queue.runDue());
        now.addAndGet(1);
        queue.runDue();
        assertEquals(List.of("sooner", "sooner, queued second"), ran);
        now.addAndGet(1_000);
        queue.runDue();
        assertEquals(List.of("sooner", "sooner, queued second", "later"), ran);
    }

    @Test
    void aCancelledTimerNeverRuns() {
        AtomicInteger ran = new AtomicInteger();
        TickQueue.Timer timer = queue.after(100, ran::incrementAndGet);
        timer.cancel();

        now.addAndGet(100);
        assertEquals(0, queue.runDue());
        assertEquals(0, ran.get());
    }

    @Test
    void acceptsWorkFromManyThreadsAndRunsEachTaskOnce() throws InterruptedException {
        int threads = 4;
        int tasksPerThread = 1_000;
        AtomicInteger ran = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread.ofPlatform().start(() -> {
                for (int task = 0; task < tasksPerThread; task++) {
                    queue.execute(ran::incrementAndGet);
                }
                done.countDown();
            });
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (done.getCount() > 0 && System.nanoTime() < deadline) {
            queue.runDue();
        }
        assertTrue(done.await(1, TimeUnit.SECONDS));
        queue.runDue();
        assertEquals(threads * tasksPerThread, ran.get());
    }
}
