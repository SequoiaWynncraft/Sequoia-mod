package com.seqwawa.seq.network;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiPredicate;

/** Keeps war events in observation order, including their original timestamps. */
final class GuildWarOutboundQueue {
    private static final int MAX_PENDING_EVENTS = 1024;
    private final Deque<Event> pending = new ArrayDeque<>();

    synchronized boolean offer(String type, JsonObject payload) {
        if (pending.size() >= MAX_PENDING_EVENTS) {
            return false;
        }
        pending.addLast(new Event(type, payload.deepCopy()));
        return true;
    }

    synchronized void flushOne(BiPredicate<String, JsonObject> sender) {
        Event event = pending.peekFirst();
        if (event != null && sender.test(event.type(), event.payload().deepCopy())) {
            pending.removeFirst();
        }
    }

    synchronized void clear() {
        pending.clear();
    }

    private record Event(String type, JsonObject payload) {}
}
