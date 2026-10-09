package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.seqwawa.seq.map.GatheringNode;
import com.seqwawa.seq.map.GatheringTotemSolver.Placement;
import com.seqwawa.seq.map.GatheringTotemSolver.Position;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GatheringTotemSessionTest {
    @Test
    void debouncesChangedInputsAndCopiesWorkerInputs() {
        MutableClock clock = new MutableClock();
        List<GatheringTotemSession.Request> calls = new ArrayList<>();
        GatheringTotemSession session = new GatheringTotemSession(request -> {
            calls.add(request);
            return new CompletableFuture<>();
        }, clock, error -> fail(error));
        List<GatheringNode> nodes = new ArrayList<>(List.of(node(1)));
        var first = new GatheringTotemSession.Request("first", nodes, Set.of("OAK"), null, nodes, false);
        nodes.clear();
        session.refresh(first, null);
        clock.millis = 199;
        session.refresh(first, null);
        assertTrue(calls.isEmpty());
        var second = request("second");
        session.refresh(second, null);
        clock.millis = 398;
        session.refresh(second, null);
        assertTrue(calls.isEmpty());
        clock.millis = 399;
        session.refresh(second, null);
        assertEquals(List.of(second), calls);
        assertEquals(List.of(node(1)), first.nodes());
        assertEquals(List.of(node(1)), first.clusterNodes());
        assertThrows(UnsupportedOperationException.class, () -> first.nodes().clear());
    }

    @Test
    void detachesOldResultsEvenWhenTheWorkerCannotBeCancelled() {
        CompletableFuture<List<Placement>> old = uncancellable();
        CompletableFuture<List<Placement>> current = new CompletableFuture<>();
        List<CompletableFuture<List<Placement>>> futures = new ArrayList<>(List.of(old, current));
        GatheringTotemSession session = new GatheringTotemSession(request -> futures.removeFirst(),
                Clock.systemUTC(), error -> fail(error));
        session.refreshNow(request("old"), null);
        session.refreshNow(request("new"), null);
        old.complete(List.of(placement("obsolete", 1)));
        session.refresh(request("new"), null);
        assertTrue(session.pending());
        assertTrue(session.placements().isEmpty());
        current.complete(List.of(placement("current", 2)));
        session.refresh(request("new"), null);
        assertEquals("current", session.selected().key());
        assertFalse(session.optimizing());
    }

    @Test
    void resetCancelsActiveWorkAndLateCompletionCannotRestoreSelection() {
        CompletableFuture<List<Placement>> worker = new CompletableFuture<>();
        GatheringTotemSession session = new GatheringTotemSession(request -> worker,
                Clock.systemUTC(), error -> fail(error));
        session.refreshNow(request("map"), null);
        session.reset();
        assertTrue(worker.isCancelled());
        assertFalse(worker.complete(List.of(placement("late", 1))));
        assertNull(session.selected());
        assertTrue(session.placements().isEmpty());
        assertFalse(session.pending());
    }

    @Test
    void doesNotLaunchASelectedClusterRequestUntilAClusterExists() {
        AtomicInteger launches = new AtomicInteger();
        GatheringTotemSession session = new GatheringTotemSession(request -> {
            launches.incrementAndGet();
            return CompletableFuture.completedFuture(List.of());
        }, Clock.systemUTC(), error -> fail(error));
        var awaiting = new GatheringTotemSession.Request("none", List.of(), Set.of(), null, List.of(), true);
        session.refreshNow(awaiting, null);
        assertEquals(0, launches.get());
        assertFalse(session.optimizing());
        session.refreshNow(request("selected"), null);
        assertEquals(1, launches.get());
    }

    @Test
    void failuresSettleUntilExplicitRefreshAndSuccessfulRetryClearsTheError() {
        AtomicInteger failures = new AtomicInteger();
        List<CompletableFuture<List<Placement>>> futures = new ArrayList<>(List.of(
                CompletableFuture.failedFuture(new IllegalStateException("failed")),
                CompletableFuture.completedFuture(List.of(placement("retry", 4)))));
        GatheringTotemSession session = new GatheringTotemSession(request -> futures.removeFirst(),
                Clock.systemUTC(), error -> failures.incrementAndGet());
        session.refreshNow(request("same"), null);
        session.refresh(request("same"), null);
        session.refresh(request("same"), null);
        assertEquals(1, failures.get());
        assertNotNull(session.error());
        assertFalse(session.optimizing());
        assertEquals(1, futures.size());
        session.refreshNow(request("same"), null);
        session.refresh(request("same"), null);
        assertNull(session.error());
        assertEquals("retry", session.selected().key());
    }

    @Test
    void preservesSelectionByKeyAndKeepsItVisibleAfterRecomputation() {
        List<Placement> results = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> placement("spot-" + i, i)).toList();
        GatheringTotemSession session = new GatheringTotemSession(request -> CompletableFuture.completedFuture(results),
                Clock.systemUTC(), error -> fail(error));
        session.refreshNow(request("first"), null);
        session.refresh(request("first"), null);
        session.select(results.get(7));
        assertEquals(4, session.scroll());
        session.refreshNow(request("second"), new Position(7, 0));
        session.refresh(request("second"), new Position(7, 0));
        assertEquals("spot-7", session.selected().key());
        assertEquals(0, session.scroll());
        session.scroll(100);
        assertEquals(4, session.scroll());
        session.scroll(-100);
        assertEquals(0, session.scroll());
    }

    private static GatheringTotemSession.Request request(String key) {
        return new GatheringTotemSession.Request(key, List.of(node(1)), Set.of(), null, List.of(), false);
    }

    private static GatheringNode node(int x) { return new GatheringNode(x, 64, 0, 0, "NODE", "OAK", 1); }

    static Placement placement(String key, double x) {
        return new Placement(key, x, 0, List.of(node((int) x)), 0, false, List.of());
    }

    private static CompletableFuture<List<Placement>> uncancellable() {
        return new CompletableFuture<>() {
            @Override public boolean cancel(boolean interrupt) { return false; }
        };
    }

    private static final class MutableClock extends Clock {
        long millis;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    }
}
