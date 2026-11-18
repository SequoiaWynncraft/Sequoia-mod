package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.managers.LeafQuestService.Snapshot;
import com.seqwawa.seq.managers.LeafQuestService.State;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.network.ApiClient;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LeafQuestServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<CompletableFuture<Snapshot>> answer =
            new AtomicReference<>(CompletableFuture.completedFuture(snapshot(12_480)));

    private LeafQuestService service() {
        return new LeafQuestService(() -> {
            calls.incrementAndGet();
            return answer.get();
        }, now::get);
    }

    private static Snapshot snapshot(long leaf) {
        return new Snapshot(new LeafAccount(leaf, leaf, new LeafAccount.Level(7, 640, 2500)),
                new LeafQuests(new LeafQuests.Daily(1, 3, null), List.of(), List.of()));
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

        assertEquals(State.READY, service.state());
        assertEquals(12_480, service.snapshot().account().leaf());
    }

    @Test
    void reopeningOrResizingTheBoardWithinAMinuteShowsWhatItHasInsteadOfReadingAgain() {
        LeafQuestService service = service();
        service.requestRefresh();

        now.addAndGet(5_000);
        assertFalse(service.requestRefresh());

        assertEquals(1, calls.get());
        assertEquals(State.READY, service.state());
    }

    @Test
    void readsAtMostOnceAMinuteWhileTheBoardStaysOpen() {
        LeafQuestService service = service();
        service.requestRefresh();

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
        now.addAndGet(LeafQuestService.REFRESH_INTERVAL_MS);
        assertFalse(service.requestRefresh(), "a read is still running");

        assertEquals(1, calls.get());
    }

    @Test
    void tellsSomeoneOutsideTheGuildThatQuestsAreForMembers() {
        answer.set(CompletableFuture.failedFuture(new ApiClient.ApiException(403, "{\"code\":\"not_a_member\"}")));
        LeafQuestService service = service();

        service.requestRefresh();

        assertEquals(State.NOT_MEMBER, service.state());
    }

    @Test
    void keepsShowingTheLastQuestsWhenARefreshFails() {
        LeafQuestService service = service();
        service.requestRefresh();
        answer.set(CompletableFuture.failedFuture(new IllegalStateException("offline")));

        now.addAndGet(LeafQuestService.REFRESH_INTERVAL_MS);
        service.requestRefresh();

        assertEquals(State.READY, service.state());
        assertEquals(12_480, service.snapshot().account().leaf());
        assertTrue(service.refreshFailed(), "the board says what it shows may be out of date");
    }

    @Test
    void aWorkingRefreshClearsTheOutOfDateNote() {
        LeafQuestService service = service();
        service.requestRefresh();
        answer.set(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        now.addAndGet(LeafQuestService.REFRESH_INTERVAL_MS);
        service.requestRefresh();

        answer.set(CompletableFuture.completedFuture(snapshot(13_000)));
        now.addAndGet(LeafQuestService.REFRESH_INTERVAL_MS);
        service.requestRefresh();

        assertFalse(service.refreshFailed());
        assertEquals(13_000, service.snapshot().account().leaf());
    }

    @Test
    void isUnavailableWhenTheFirstReadFails() {
        answer.set(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        LeafQuestService service = service();

        service.requestRefresh();

        assertEquals(State.UNAVAILABLE, service.state());
    }

    @Test
    void forgetsTheLastAccountAndIgnoresItsLateAnswer() {
        CompletableFuture<Snapshot> late = new CompletableFuture<>();
        answer.set(late);
        LeafQuestService service = service();
        service.requestRefresh();

        service.reset();
        late.complete(snapshot(99));

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
}
