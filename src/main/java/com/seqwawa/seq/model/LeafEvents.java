package com.seqwawa.seq.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.List;

/**
 * The member's latest Leaf events, newest first, as Leaf's {@code GET /v1/me/events}
 * sends them: what paid Leaf, finished a quest or reached a level. The same events
 * the popups are made from.
 */
public record LeafEvents(List<Event> events) {

    public static final LeafEvents EMPTY = new LeafEvents(List.of());

    /**
     * @param type such as {@code leaf_earned} or {@code quest_completed}
     * @param data the event's details, in the shape Leaf's README lists for its type
     */
    public record Event(long id, String type, @SerializedName("created_at") Instant createdAt, JsonObject data) {}

    public List<Event> eventsOrEmpty() {
        return events == null ? List.of() : events;
    }
}
