package com.seqwawa.seq.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.Refreshable.Phase;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RefreshableTest {

    private static final long REFRESH = 60_000;
    private static final long RETRY = 5_000;

    private final AtomicLong now = new AtomicLong(1_000);
    private final TickQueue ticks = new TickQueue(now::get);
    private final Deque<CompletableFuture<String>> replies = new ArrayDeque<>();
    private final List<String> fetched = new ArrayList<>();
    private final List<Throwable> failed = new ArrayList<>();
    private int fetches;

    private Refreshable.Builder<String> builder() {
        return Refreshable.<String>builder("test value", () -> {
                    fetches++;
                    CompletableFuture<String> reply = new CompletableFuture<>();
                    replies.add(reply);
                    return reply;
                })
                .clock(now::get)
                .handBack(ticks)
                .refreshEveryMs(REFRESH)
                .retryAfterMs(RETRY)
                .emptyValue("")
                .whenFetched(fetched::add)
                .whenFailed(failed::add);
    }

    private void reply(String value) {
        replies.remove().complete(value);
        ticks.runDue();
    }

    private void fail(Throwable failure) {
        replies.remove().completeExceptionally(failure);
        ticks.runDue();
    }

    @Test
    void appliesAResultOnlyWhenItIsHandedBack() {
        Refreshable<String> value = builder().build();
        assertEquals(Phase.EMPTY, value.state().phase());
        assertEquals("", value.value());

        assertTrue(value.request());
        assertEquals(Phase.LOADING, value.state().phase());
        replies.remove().complete("roster");
        assertEquals(Phase.LOADING, value.state().phase(), "the reply waits for the game thread");

        ticks.runDue();
        assertEquals(Phase.READY, value.state().phase());
        assertEquals("roster", value.value());
        assertEquals(1, value.state().version());
        assertEquals(List.of("roster"), fetched);
    }

    @Test
    void oneFetchAtATimeThenTheRefreshInterval() {
        Refreshable<String> value = builder().build();
        assertTrue(value.request());
        assertFalse(value.request(), "a fetch is already running");
        reply("first");

        now.addAndGet(REFRESH - 1);
        assertFalse(value.request());
        now.addAndGet(1);
        assertTrue(value.request());
        assertTrue(value.state().loading());
        assertEquals("first", value.value(), "the old value stays while refreshing");
        reply("second");
        assertEquals("second", value.value());
        assertEquals(2, fetches);
    }

    @Test
    void aFailureKeepsTheValueByDefaultAndRetriesSooner() {
        Refreshable<String> value = builder().build();
        value.request();
        reply("kept");
        now.addAndGet(REFRESH);
        value.request();
        IllegalStateException outage = new IllegalStateException("outage");
        fail(new CompletionException(outage));

        assertEquals(Phase.READY, value.state().phase());
        assertEquals("kept", value.value());
        assertSame(outage, value.state().failure(), "the cause is unwrapped");
        assertEquals(List.of(outage), failed);

        now.addAndGet(RETRY - 1);
        assertFalse(value.request());
        now.addAndGet(1);
        assertTrue(value.request());
        reply("recovered");
        assertNull(value.state().failure());
    }

    @Test
    void clearOnFailureDropsTheValue() {
        Refreshable<String> value = builder().clearOnFailure().build();
        value.request();
        reply("stale soon");
        long version = value.state().version();
        now.addAndGet(REFRESH);
        value.request();
        fail(new IllegalStateException("outage"));

        assertEquals(Phase.FAILED, value.state().phase());
        assertEquals("", value.value());
        assertEquals(version + 1, value.state().version());
    }

    @Test
    void theFirstFetchIsThrottledAfterItFails() {
        Refreshable<String> value = builder().build();
        value.request();
        fail(new IllegalStateException("offline"));
        assertEquals(Phase.FAILED, value.state().phase());

        assertFalse(value.request(), "a failed first fetch waits out the retry interval");
        now.addAndGet(RETRY);
        assertTrue(value.request());
        assertEquals(Phase.LOADING, value.state().phase());
    }

    @Test
    void aFetchThatThrowsOrReturnsNothingFails() {
        AtomicReference<RuntimeException> thrown = new AtomicReference<>(new IllegalStateException("no client"));
        Refreshable<String> value = Refreshable.<String>builder("throwing", () -> {
                    if (thrown.get() != null) throw thrown.get();
                    return CompletableFuture.completedFuture(null);
                })
                .clock(now::get)
                .handBack(ticks)
                .retryAfterMs(RETRY)
                .build();

        assertTrue(value.request());
        assertFalse(value.isLoading());
        assertSame(thrown.get(), value.state().failure());

        thrown.set(null);
        now.addAndGet(RETRY);
        value.request();
        ticks.runDue();
        assertInstanceOf(IllegalStateException.class, value.state().failure());
        assertEquals(Phase.FAILED, value.state().phase());
    }

    @Test
    void markStaleFetchesAtOnceAndAppliesAfterARunningFetch() {
        Refreshable<String> value = builder().build();
        value.request();
        reply("first");
        value.markStale();
        assertTrue(value.request(), "stale skips the interval");

        value.markStale();
        assertFalse(value.request(), "still one fetch at a time");
        reply("answer from before the change");
        assertTrue(value.request(), "the change came after that fetch began");
        reply("after the change");
        assertFalse(value.request());
    }

    @Test
    void forceFetchesNowUnlessOneIsRunning() {
        Refreshable<String> value = builder().build();
        value.request();
        reply("first");
        assertTrue(value.force());
        assertFalse(value.force());
        reply("second");
        assertFalse(value.request(), "a refused force leaves the interval alone");
    }

    @Test
    void resetDropsTheValueAndTheRunningFetchsLateResult() {
        AtomicReference<Refreshable.Ticket> ticket = new AtomicReference<>();
        Refreshable<String> value = Refreshable.<String>builderWithTicket("ticketed", started -> {
                    ticket.set(started);
                    CompletableFuture<String> reply = new CompletableFuture<>();
                    replies.add(reply);
                    return reply;
                })
                .clock(now::get)
                .handBack(ticks)
                .refreshEveryMs(REFRESH)
                .build();
        value.request();
        reply("old account");
        long version = value.state().version();
        now.addAndGet(REFRESH);
        value.request();
        Refreshable.Ticket overtaken = ticket.get();
        assertTrue(overtaken.current());

        value.reset();
        assertFalse(overtaken.current());
        assertEquals(Phase.EMPTY, value.state().phase());
        assertNull(value.value());
        assertEquals(version + 1, value.state().version(), "versions never go back");

        assertTrue(value.request(), "the first request after a reset fetches at once");
        replies.remove().complete("late reply for the old account");
        ticks.runDue();
        assertEquals(Phase.LOADING, value.state().phase(), "the overtaken reply is dropped");
        reply("new account");
        assertEquals("new account", value.value());
    }

    @Test
    void aSeedShowsUntilAFetchReplacesItAndNeverOverwritesOne() {
        Refreshable<String> value = builder().build();
        assertTrue(value.seed("cached"));
        assertEquals(Phase.READY, value.state().phase());
        assertTrue(value.request(), "a seed isn't a fetch");
        reply("fresh");
        assertEquals("fresh", value.value());
        assertEquals(List.of("fresh"), fetched, "whenFetched only hears fetches");

        assertFalse(value.seed("cache read late"));
        assertEquals("fresh", value.value());
    }

    @Test
    void aFetchPastItsDeadlineFailsAndItsLateResultIsDropped() {
        Refreshable<String> value = builder().deadlineMs(45_000).build();
        value.request();
        now.addAndGet(44_999);
        assertFalse(value.request());
        now.addAndGet(1);

        assertTrue(value.request(), "the timed-out fetch began longer ago than the retry interval");
        assertInstanceOf(TimeoutException.class, failed.getFirst());
        assertInstanceOf(TimeoutException.class, value.state().failure());
        replies.remove().complete("too late");
        ticks.runDue();
        assertTrue(value.isLoading(), "the late reply is dropped, and the new fetch is still running");
        assertEquals(Phase.LOADING, value.state().phase());
        reply("in time");
        assertEquals("in time", value.value());
    }

    @Test
    void aTimedOutFetchWaitsOutTheRetryIntervalWhenItIsShorterThanTheDeadline() {
        Refreshable<String> value = builder().retryAfterMs(60_000).deadlineMs(45_000).build();
        value.request();
        now.addAndGet(45_000);

        assertFalse(value.request());
        assertFalse(value.isLoading());
        assertEquals(Phase.FAILED, value.state().phase());
        now.addAndGet(15_000);
        assertTrue(value.request());
    }

    @Test
    void keepEqualValueKeepsTheSameInstanceAndVersion() {
        Refreshable<String> value = builder().keepEqualValue().build();
        value.request();
        String first = new String("nodes");
        reply(first);
        now.addAndGet(REFRESH);
        value.request();
        reply(new String("nodes"));

        assertSame(first, value.value());
        assertEquals(1, value.state().version());
    }

    @Test
    void settledCompletesWhenTheFetchFinishes() {
        Refreshable<String> value = builder().build();
        assertTrue(value.settled().isDone(), "nothing is running");
        value.request();
        CompletableFuture<Refreshable.State<String>> settled = value.settled();
        assertFalse(settled.isDone());
        reply("done");
        assertEquals("done", settled.join().value());
    }
}
