package com.seqwawa.seq.managers;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Client-thread cache. Rendering only enqueues requests; tick polls asynchronous results. */
final class PrivateMessageGuildTagCache {
    static final long CACHE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    static final long RETRY_MILLIS = TimeUnit.SECONDS.toMillis(15);
    static final int MAX_REQUESTS = 4;
    static final int MAX_AUTOMATIC_RETRIES = 3;
    private static final int MAX_ENTRIES = 128;
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Queue<Entry> queued = new ArrayDeque<>();
    private final Set<Entry> running = new LinkedHashSet<>();
    private final Function<String, CompletableFuture<String>> lookup;
    private final LongSupplier clock;

    PrivateMessageGuildTagCache(Function<String, CompletableFuture<String>> lookup, LongSupplier clock) {
        this.lookup = lookup;
        this.clock = clock;
    }

    String read(String username, Runnable changed) {
        String key = username.toLowerCase(Locale.ROOT);
        Entry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() >= MAX_ENTRIES) {
                var iterator = entries.values().iterator();
                while (iterator.hasNext()) {
                    Entry oldest = iterator.next();
                    if (oldest.future != null) continue;
                    iterator.remove();
                    queued.remove(oldest);
                    break;
                }
            }
            entry = new Entry(username);
            entries.put(key, entry);
        }
        if (!entry.pending && clock.getAsLong() >= entry.refreshAt) {
            entry.failures = 0;
            entry.pending = true;
            queued.add(entry);
        }
        entry.listeners.add(changed);
        return entry.tag;
    }

    void tick() {
        // Snapshot first: callbacks may read this cache or enqueue another lookup.
        for (Entry entry : Set.copyOf(running)) {
            if (!entry.future.isDone()) continue;
            String tag;
            try {
                tag = entry.future.join();
            } catch (RuntimeException error) {
                tag = null;
            }
            running.remove(entry);
            entry.future = null;
            entry.pending = false;
            entry.failures = tag == null ? entry.failures + 1 : 0;
            entry.refreshAt = clock.getAsLong() + (tag == null ? RETRY_MILLIS : CACHE_MILLIS);
            // Null means failure, whereas an empty result means the player left their guild.
            if (tag != null && !tag.equals(entry.tag)) {
                entry.tag = tag;
                Set.copyOf(entry.listeners).forEach(Runnable::run);
            }
        }
        // Recover retained messages without requiring another wrap or a tab switch.
        for (Entry entry : entries.values()) {
            if (!entry.pending && entry.failures > 0 && entry.failures <= MAX_AUTOMATIC_RETRIES
                    && !entry.listeners.isEmpty() && clock.getAsLong() >= entry.refreshAt) {
                entry.pending = true;
                queued.add(entry);
            }
        }
        while (running.size() < MAX_REQUESTS && !queued.isEmpty()) {
            Entry entry = queued.remove();
            running.add(entry);
            try {
                entry.future = lookup.apply(entry.username).orTimeout(5, TimeUnit.SECONDS);
            } catch (RuntimeException error) {
                entry.future = CompletableFuture.failedFuture(error);
            }
        }
    }

    void clear() {
        entries.clear();
        queued.clear();
        running.clear();
    }

    private static final class Entry {
        final String username;
        // Retained PMs also need updates when a later lookup changes/removes the guild.
        // Weak listeners do not keep cleared chat views alive for the cache's lifetime.
        final Set<Runnable> listeners = Collections.newSetFromMap(new WeakHashMap<>());
        String tag = "";
        long refreshAt;
        boolean pending;
        int failures;
        CompletableFuture<String> future;

        Entry(String username) {
            this.username = username;
        }
    }
}
