package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.managers.GuildRaidProgressService.State;
import com.seqwawa.seq.model.GuildRaidProgress;
import com.seqwawa.seq.model.GuildRaidProgress.Entry;
import com.seqwawa.seq.model.SeqRaid;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GuildRaidProgressServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicBoolean connected = new AtomicBoolean(true);
    private final AtomicReference<CompletableFuture<GuildRaidProgress>> answer =
            new AtomicReference<>(CompletableFuture.completedFuture(progress(512)));

    @Test
    void readsWhatTheBackendSends() {
        GuildRaidProgressService service = service();

        assertTrue(service.requestRefresh());

        assertEquals(State.READY, service.state());
        assertEquals(512, service.progress().count(SeqRaid.TNA));
    }

    @Test
    void startsOutLoadingWithNothingToShow() {
        GuildRaidProgressService service = service();

        assertEquals(State.LOADING, service.state());
        assertEquals(0, service.progress().totalCount());
    }

    @Test
    void aFailedReadLeavesTheScreenUnavailable() {
        answer.set(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        GuildRaidProgressService service = service();

        service.requestRefresh();

        assertEquals(State.UNAVAILABLE, service.state());
        assertEquals(0, service.progress().totalCount());
    }

    @Test
    void aFailedReadKeepsWhatWasAlreadyLoaded() {
        GuildRaidProgressService service = service();
        service.requestRefresh();

        answer.set(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        now.addAndGet(GuildRaidProgressService.REFRESH_INTERVAL_MS);
        service.requestRefresh();

        assertEquals(State.READY, service.state());
        assertEquals(512, service.progress().count(SeqRaid.TNA));
    }

    @Test
    void readsAtMostOncePerInterval() {
        GuildRaidProgressService service = service();

        assertTrue(service.requestRefresh());
        assertFalse(service.requestRefresh());
        assertEquals(1, calls.get());

        now.addAndGet(GuildRaidProgressService.REFRESH_INTERVAL_MS);
        assertTrue(service.requestRefresh());
        assertEquals(2, calls.get());
    }

    @Test
    void tickingLoadsTheProgressBeforeAnyoneOpensTheScreen() {
        GuildRaidProgressService service = service();

        service.tick();

        assertEquals(1, calls.get());
        assertEquals(State.READY, service.state());
        assertEquals(512, service.progress().count(SeqRaid.TNA));
    }

    @Test
    void tickingKeepsTheProgressFresh() {
        GuildRaidProgressService service = service();

        service.tick();
        service.tick();
        assertEquals(1, calls.get());

        now.addAndGet(GuildRaidProgressService.REFRESH_INTERVAL_MS);
        service.tick();
        assertEquals(2, calls.get());
    }

    @Test
    void nothingIsAskedWhileTheBackendIsDisconnected() {
        connected.set(false);
        GuildRaidProgressService service = service();

        service.tick();

        assertEquals(0, calls.get());
        assertEquals(State.UNAVAILABLE, service.state());
    }

    @Test
    void reconnectingPicksTheProgressBackUp() {
        connected.set(false);
        GuildRaidProgressService service = service();
        service.tick();

        connected.set(true);
        service.tick();

        assertEquals(1, calls.get());
        assertEquals(State.READY, service.state());
    }

    @Test
    void goingOfflineDoesNotWipeWhatIsAlreadyOnScreen() {
        GuildRaidProgressService service = service();
        service.tick();

        connected.set(false);
        service.tick();

        assertEquals(State.READY, service.state());
        assertEquals(512, service.progress().count(SeqRaid.TNA));
    }

    @Test
    void aFinishedRaidRefreshesWithoutWaitingForTheInterval() {
        GuildRaidProgressService service = service();
        service.tick();
        assertEquals(1, calls.get());

        answer.set(CompletableFuture.completedFuture(progress(513)));
        service.onLocalRaidCompleted();

        assertEquals(2, calls.get());
        assertEquals(513, service.progress().count(SeqRaid.TNA));
    }

    @Test
    void aFinishedRaidPushesTheNextScheduledReadBack() {
        GuildRaidProgressService service = service();
        service.onLocalRaidCompleted();

        service.tick();

        assertEquals(1, calls.get());
    }

    private GuildRaidProgressService service() {
        return new GuildRaidProgressService(
                () -> {
                    calls.incrementAndGet();
                    return answer.get();
                },
                now::get,
                connected::get,
                Runnable::run);
    }

    private static GuildRaidProgress progress(int tnaCount) {
        return new GuildRaidProgress(1, Map.of("TNA", new Entry(tnaCount)));
    }
}
