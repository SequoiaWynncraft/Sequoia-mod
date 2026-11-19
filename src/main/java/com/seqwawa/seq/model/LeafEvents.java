package com.seqwawa.seq.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Some of the member's Leaf events, as Leaf's {@code GET /v1/me/events} sends them: what
 * paid Leaf, finished a quest or reached a level. The same events the popups are made from.
 *
 * <p>Leaf lists them oldest first, but nothing here depends on that: an event's id only
 * grows, so the events are only handed out ordered by it, newest first. There is no
 * accessor in the order they arrived in.
 */
public final class LeafEvents {

    public static final LeafEvents EMPTY = of(List.of());

    /**
     * @param id Leaf's number for it, shared by all members' events; a later event has a larger id
     * @param type such as {@code leaf_earned} or {@code quest_completed}
     * @param data the event's details, in the shape Leaf's README lists for its type
     */
    public record Event(long id, String type, @SerializedName("created_at") Instant createdAt, JsonObject data) {}

    /** Filled by Gson, in Leaf's order; read only through {@link #newestFirst()}. */
    private final List<Event> events;

    private LeafEvents(List<Event> events) {
        this.events = events;
    }

    /** These events, in any order. */
    public static LeafEvents of(Collection<Event> events) {
        return new LeafEvents(events == null ? List.of() : new ArrayList<>(events));
    }

    /** The events, newest first by id, without gaps. */
    public List<Event> newestFirst() {
        if (events == null) return List.of();
        return events.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(Event::id).reversed())
                .toList();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LeafEvents that && newestFirst().equals(that.newestFirst());
    }

    @Override
    public int hashCode() {
        return newestFirst().hashCode();
    }

    @Override
    public String toString() {
        return "LeafEvents" + newestFirst();
    }
}
