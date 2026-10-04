package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PrivateMessageGuildTagCacheTest {
    @Test
    void retriesFailedLookupWithoutAnotherReadOrTabSwitch() {
        AtomicInteger changes = new AtomicInteger();
        Runnable changed = changes::incrementAndGet;
        cache.read("Baptiste", changed);
        cache.tick();
        requests.getFirst().completeExceptionally(new TimeoutException());
        cache.tick();
        clock.addAndGet(PrivateMessageGuildTagCache.RETRY_MILLIS - 1);
        cache.tick();
        assertEquals(1, requests.size());
        clock.incrementAndGet();
        cache.tick();
        assertEquals(2, requests.size());
        requests.getLast().complete("SEQ");
        cache.tick();
        assertEquals(1, changes.get());
        clock.addAndGet(PrivateMessageGuildTagCache.CACHE_MILLIS);
        cache.tick();
        assertEquals(2, requests.size(), "Successful lookups do not poll indefinitely");
    }

    @Test
    void persistentFailureStopsAfterBoundedAutomaticRetries() {
        Runnable changed = () -> {};
        cache.read("Baptiste", changed);
        cache.tick();
        for (int i = 0; i <= PrivateMessageGuildTagCache.MAX_AUTOMATIC_RETRIES; i++) {
            requests.getLast().completeExceptionally(new TimeoutException());
            cache.tick();
            clock.addAndGet(PrivateMessageGuildTagCache.RETRY_MILLIS);
            cache.tick();
        }
        assertEquals(1 + PrivateMessageGuildTagCache.MAX_AUTOMATIC_RETRIES, requests.size());
        cache.clear();
        clock.addAndGet(PrivateMessageGuildTagCache.RETRY_MILLIS);
        cache.tick();
        assertEquals(1 + PrivateMessageGuildTagCache.MAX_AUTOMATIC_RETRIES, requests.size());
    }

    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<String> names = new ArrayList<>();
    private final List<CompletableFuture<String>> requests = new ArrayList<>();
    private final PrivateMessageGuildTagCache cache = new PrivateMessageGuildTagCache(name -> {
        names.add(name);
        var request = new CompletableFuture<String>();
        requests.add(request);
        return request;
    }, clock::get);

    @Test
    void renderingNeverStartsNetworkRequestsAndTabsShareOneLookup() {
        AtomicInteger all = new AtomicInteger();
        AtomicInteger privateTab = new AtomicInteger();
        Runnable allView = all::incrementAndGet;
        Runnable privateView = privateTab::incrementAndGet;
        assertEquals("", cache.read("Baptiste", allView));
        cache.read("baptiste", privateView);
        cache.read("BAPTISTE", privateView);
        assertTrue(requests.isEmpty());
        cache.tick();
        assertEquals(List.of("Baptiste"), names);
        requests.getFirst().complete("SEQ");
        assertEquals(0, all.get(), "Network completion must not mutate the UI from its thread");
        cache.tick();
        assertEquals(1, all.get());
        assertEquals(1, privateTab.get());
        assertEquals("SEQ", cache.read("Baptiste", allView));
        cache.tick();
        assertEquals(1, all.get());
    }

    @Test
    void staleTagSurvivesTimeoutAndRetriesSoonWithoutSpuriousRefresh() {
        AtomicInteger changes = new AtomicInteger();
        Runnable changed = changes::incrementAndGet;
        cache.read("Baptiste", changed);
        cache.tick();
        requests.getFirst().complete("SEQ");
        cache.tick();
        clock.addAndGet(PrivateMessageGuildTagCache.CACHE_MILLIS);
        assertEquals("SEQ", cache.read("Baptiste", changed));
        cache.tick();
        assertEquals("SEQ", cache.read("Baptiste", changed));
        requests.get(1).completeExceptionally(new TimeoutException());
        cache.tick();
        assertEquals("SEQ", cache.read("Baptiste", changed));
        assertEquals(1, changes.get());
        clock.addAndGet(PrivateMessageGuildTagCache.RETRY_MILLIS - 1);
        cache.read("Baptiste", changed);
        cache.tick();
        assertEquals(2, requests.size());
        clock.incrementAndGet();
        cache.read("Baptiste", changed);
        cache.tick();
        assertEquals(3, requests.size());
        requests.get(2).complete("SEQ");
        cache.tick();
        assertEquals(1, changes.get(), "An unchanged guild must not rewrap any messages");
    }

    @Test
    void genuineGuildRemovalUpdatesPreviouslyCachedMessagesToo() {
        AtomicInteger changes = new AtomicInteger();
        Runnable changed = changes::incrementAndGet;
        cache.read("Baptiste", changed);
        cache.tick();
        requests.getFirst().complete("SEQ");
        cache.tick();
        clock.addAndGet(PrivateMessageGuildTagCache.CACHE_MILLIS);
        Runnable newMessage = () -> {};
        cache.read("Baptiste", newMessage);
        cache.tick();
        requests.get(1).complete("");
        cache.tick();
        assertEquals(2, changes.get(), "Older retained messages must also lose the old guild tag");
        assertEquals("", cache.read("Baptiste", changed));
    }

    @Test
    void limitsConcurrentLookupsAndIgnoresCompletionsAfterDisconnect() {
        AtomicInteger changes = new AtomicInteger();
        Runnable changed = changes::incrementAndGet;
        for (int i = 0; i < 20; i++) cache.read("Player" + i, changed);
        cache.tick();
        assertEquals(PrivateMessageGuildTagCache.MAX_REQUESTS, requests.size());
        cache.tick();
        assertEquals(PrivateMessageGuildTagCache.MAX_REQUESTS, requests.size());
        requests.getFirst().complete("SEQ");
        cache.tick();
        assertEquals(PrivateMessageGuildTagCache.MAX_REQUESTS + 1, requests.size());
        cache.clear();
        requests.forEach(request -> request.complete("NEW"));
        cache.tick();
        assertEquals(1, changes.get());
        assertEquals("", cache.read("Player0", changed));
    }
}
