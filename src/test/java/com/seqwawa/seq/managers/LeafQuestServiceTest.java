package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.managers.LeafQuestService.Snapshot;
import com.seqwawa.seq.managers.LeafQuestService.State;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.network.ApiClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LeafQuestServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<CompletableFuture<Snapshot>> answer =
            new AtomicReference<>(CompletableFuture.completedFuture(snapshot(12_480)));

    private final AtomicReference<BooleanSupplier> sessionCurrent = new AtomicReference<>();
    /** The game thread: finished reads wait here until a test lets it catch up. */
    private final TickQueue ticks = new TickQueue(now::get);

    private LeafQuestService service() {
        return service(LeafQuestService.READ_DEADLINE);
    }

    private LeafQuestService service(Duration readDeadline) {
        return new LeafQuestService(read -> {
            calls.incrementAndGet();
            sessionCurrent.set(read.wanted());
            return answer.get();
        }, now::get, readDeadline, ticks);
    }

    private static Snapshot snapshot(long leaf) {
        return new Snapshot(new LeafAccount(leaf, leaf, new LeafAccount.Level(7, 640, 2500)),
                new LeafQuests(new LeafQuests.Daily(1, 3, null), null, List.of(), List.of()));
    }

    @Test
    void startsOutLoadingWithNothingToShow() {
        LeafQuestService service = service();

        assertEquals(State.LOADING, service.state());
        assertEquals(Snapshot.EMPTY, service.snapshot());
    }

    @Test
    void showsWhatLeafSends() {
        LeafQuestService service = service();

        assertTrue(service.requestRefresh());
        ticks.runDue();

        assertEquals(State.READY, service.state());
        assertEquals(12_480, service.snapshot().account().leaf());
    }

    @Test
    void reopeningOrResizingTheBoardWithinAMinuteShowsWhatItHasInsteadOfReadingAgain() {
        LeafQuestService service = service();
        service.requestRefresh();
        ticks.runDue();

        now.addAndGet(5_000);
        assertFalse(service.requestRefresh());

        assertEquals(1, calls.get());
        assertEquals(State.READY, service.state());
    }

    @Test
    void readsAtMostOnceAMinuteWhileTheBoardStaysOpen() {
        LeafQuestService service = service();
        service.requestRefresh();
        ticks.runDue();

        now.addAndGet(LeafQuestService.REFRESH_INTERVAL_MS - 1);
        assertFalse(service.requestRefresh());
        now.addAndGet(1);
        assertTrue(service.requestRefresh());

        assertEquals(2, calls.get());
    }

    @Test
    void doesNotStartASecondReadWhileOneIsRunning() {
        answer.set(new CompletableFuture<>());
        LeafQuestService service = service();

        assertTrue(service.requestRefresh());
        service.markStale();
        ticks.runDue();
        assertFalse(service.requestRefresh(), "a read is still running");

        assertEquals(1, calls.get());
    }

    @Test
    void tellsSomeoneOutsideTheGuildThatQuestsAreForMembers() {
        answer.set(CompletableFuture.failedFuture(new ApiClient.ApiException(403, "{\"code\":\"not_a_member\"}")));
        LeafQuestService service = service();

        service.requestRefresh();

        ticks.runDue();

        assertEquals(State.NOT_MEMBER, service.state());
    }

    @Test
    void keepsShowingTheLastQuestsWhenARefreshFails() {
        LeafQuestService service = service();
        service.requestRefresh();
        ticks.runDue();
        answer.set(CompletableFuture.failedFuture(new IllegalStateException("offline")));

        now.addAndGet(LeafQuestService.REFRESH_INTERVAL_MS);
        service.requestRefresh();
        ticks.runDue();

        assertEquals(State.READY, service.state());
        assertEquals(12_480, service.snapshot().account().leaf());
        assertTrue(service.refreshFailed(), "the board says what it shows may be out of date");
    }

    @Test
    void aWorkingRefreshClearsTheOutOfDateNote() {
        LeafQuestService service = service();
        service.requestRefresh();
        ticks.runDue();
        answer.set(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        now.addAndGet(LeafQuestService.REFRESH_INTERVAL_MS);
        service.requestRefresh();
        ticks.runDue();

        answer.set(CompletableFuture.completedFuture(snapshot(13_000)));
        now.addAndGet(LeafQuestService.REFRESH_INTERVAL_MS);
        service.requestRefresh();
        ticks.runDue();

        assertFalse(service.refreshFailed());
        assertEquals(13_000, service.snapshot().account().leaf());
    }

    @Test
    void isUnavailableWhenTheFirstReadFails() {
        answer.set(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        LeafQuestService service = service();

        service.requestRefresh();

        ticks.runDue();

        assertEquals(State.UNAVAILABLE, service.state());
    }

    @Test
    void forgetsTheLastAccountAndIgnoresItsLateAnswer() {
        CompletableFuture<Snapshot> late = new CompletableFuture<>();
        answer.set(late);
        LeafQuestService service = service();
        service.requestRefresh();
        ticks.runDue();

        service.reset();
        late.complete(snapshot(99));
        ticks.runDue();

        assertEquals(State.LOADING, service.state());
        assertEquals(Snapshot.EMPTY, service.snapshot());
    }

    @Test
    void aMissingHistoryEntryIsSkippedRatherThanFailingTheBoard() {
        List<com.seqwawa.seq.model.LeafEvents.Event> withGap = new java.util.ArrayList<>();
        withGap.add(null);
        withGap.add(new com.seqwawa.seq.model.LeafEvents.Event(1, "level_up", null, new com.google.gson.JsonObject()));

        Snapshot snapshot = new Snapshot(LeafAccount.EMPTY, LeafQuests.EMPTY, withGap);

        assertEquals(1, snapshot.recent().size());
        assertFalse(snapshot.recentFailed());
    }

    @Test
    void aReadThatNeverFinishesGivesUpSoTheBoardCanReadAgain() {
        answer.set(new CompletableFuture<>());
        LeafQuestService service = service();

        service.requestRefresh();
        now.addAndGet(LeafQuestService.READ_DEADLINE.toMillis() - 1);
        service.requestRefresh();
        assertEquals(State.LOADING, service.state());
        now.addAndGet(1);
        service.requestRefresh();
        ticks.runDue();

        assertEquals(State.UNAVAILABLE, service.state(), "the open board's next request gives the read up");
        now.addAndGet(LeafQuestService.REFRESH_INTERVAL_MS);
        assertTrue(service.requestRefresh(), "the stalled read no longer holds the next one back");
    }

    @Test
    void aChangeLeafReportsFromAnotherThreadMakesTheNextRequestReadAtOnce() throws InterruptedException {
        LeafQuestService service = service();
        service.requestRefresh();
        ticks.runDue();

        Thread reporter = Thread.ofPlatform().start(service::markStale);
        reporter.join();
        assertFalse(service.requestRefresh(), "the mark waits for the game thread");
        ticks.runDue();
        assertTrue(service.requestRefresh());
        assertEquals(2, calls.get());
    }

    @Test
    void aReadStillRunningWhenTheSessionIsClearedCannotSignInAgain() {
        answer.set(new CompletableFuture<>());
        LeafQuestService service = service();
        service.requestRefresh();
        ticks.runDue();
        assertTrue(sessionCurrent.get().getAsBoolean());

        service.reset();

        assertFalse(sessionCurrent.get().getAsBoolean());
        AtomicInteger signIns = new AtomicInteger();
        var tokenFor = LeafQuestService.sessionToken(sessionCurrent.get(), force -> {
            signIns.incrementAndGet();
            return CompletableFuture.completedFuture("token");
        });
        assertTrue(tokenFor.apply(false).isCompletedExceptionally());
        assertTrue(tokenFor.apply(true).isCompletedExceptionally(), "nor the fresh sign-in a 401 asks for");
        assertEquals(0, signIns.get());
    }

    private static final com.seqwawa.seq.network.auth.StoredAuthSession SIGNED_IN =
            new com.seqwawa.seq.network.auth.StoredAuthSession("session-token",
                    java.time.Instant.now().plusSeconds(86_400), "123e4567-e89b-12d3-a456-426614174000", "Player");

    @Test
    void aRefreshBetweenClearingTheSessionAndResettingTheBoardCannotSignIn() {
        AtomicInteger signIns = new AtomicInteger();
        var auth = new com.seqwawa.seq.network.auth.MinecraftAuthService() {
            @Override
            public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
                signIns.incrementAndGet();
                return CompletableFuture.completedFuture("token");
            }
        };
        AtomicReference<CompletableFuture<String>> token = new AtomicReference<>();
        // The board reads with its own session's tokens, as a session hands them out.
        long session = auth.sessionGeneration();
        LeafQuestService service = new LeafQuestService(read -> {
            token.set(LeafQuestService.sessionToken(read.wanted(),
                    force -> auth.ensureSessionToken(force, session)).apply(false));
            return new CompletableFuture<>();
        }, now::get, LeafQuestService.READ_DEADLINE, ticks);

        // The session is cleared; this board's reset has not run yet when a refresh starts.
        auth.clearSession();
        service.requestRefresh();
        ticks.runDue();

        assertTrue(token.get().isCompletedExceptionally());
        assertEquals(0, signIns.get());
    }

    @Test
    void saysToConnectWhenThereIsNoSessionToReadWith() {
        answer.set(CompletableFuture.failedFuture(new com.seqwawa.seq.network.auth.NotSignedInException()));
        LeafQuestService service = service();

        service.requestRefresh();

        ticks.runDue();

        assertEquals(State.SIGNED_OUT, service.state());
        assertEquals(Snapshot.EMPTY, service.snapshot());
        assertFalse(service.refreshFailed());
    }

    @Test
    void anOpenBoardKeepsReadingWhileSignedInAndStopsOnceTheSessionIsCleared() {
        AtomicInteger signIns = new AtomicInteger();
        var auth = new com.seqwawa.seq.network.auth.MinecraftAuthService(SIGNED_IN) {
            @Override
            public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
                signIns.incrementAndGet();
                return CompletableFuture.completedFuture("token");
            }
        };
        long session = auth.sessionGeneration();
        LeafQuestService service = new LeafQuestService(read -> LeafQuestService.sessionToken(read.wanted(),
                        force -> auth.ensureSessionToken(force, session))
                .apply(false)
                .thenApply(token -> snapshot(12_480)), now::get, LeafQuestService.READ_DEADLINE, ticks);

        service.requestRefresh();

        ticks.runDue();
        assertEquals(State.READY, service.state(), "an active session reads as before");
        assertEquals(1, signIns.get());

        // The session is invalidated while the board is open; clearing it resets the board, which reads again.
        auth.clearSession();
        service.reset();
        service.requestRefresh();
        ticks.runDue();

        assertEquals(State.SIGNED_OUT, service.state());
        assertEquals(1, signIns.get(), "the refresh started no sign-in");
    }
}
