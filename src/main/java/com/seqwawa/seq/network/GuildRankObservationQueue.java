package com.seqwawa.seq.network;

import com.google.gson.JsonObject;
import com.seqwawa.seq.managers.GuildRankEventParser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Bounded, account-scoped retry queue. Retries retain their original observation ID. */
public final class GuildRankObservationQueue {
    private final LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
    private String lastKey;
    private Instant lastAt = Instant.EPOCH;

    public synchronized void add(GuildRankEventParser.Event event, UUID observer, Instant now) {
        if (observer == null) return;
        String key = observer + ":" + event.actor().displayName().toLowerCase(Locale.ROOT) + ":"
                + event.target().displayName().toLowerCase(Locale.ROOT) + ":" + event.oldRank() + ":" + event.newRank();
        if (key.equals(lastKey) && now.isBefore(lastAt.plusMillis(750))) return;
        lastKey = key;
        lastAt = now;
        String id = UUID.randomUUID().toString();
        JsonObject payload = new JsonObject();
        payload.addProperty("observation_id", id);
        payload.addProperty("observed_at", now.toString());
        payload.add("actor", identity(event.actor()));
        payload.add("target", identity(event.target()));
        payload.addProperty("old_rank", event.oldRank());
        payload.addProperty("new_rank", event.newRank());
        payload.addProperty("raw_text", event.text());
        if (pending.size() >= 100) pending.remove(pending.keySet().iterator().next());
        pending.put(id, new Pending(observer, now, Instant.EPOCH, payload));
    }

    public synchronized List<JsonObject> due(UUID observer, Instant now) {
        pending.values().removeIf(item -> !item.observer().equals(observer) || now.isAfter(item.createdAt().plusSeconds(600)));
        return pending.values().stream().filter(item -> !now.isBefore(item.sentAt().plusSeconds(5)))
                .limit(1).map(item -> item.payload().deepCopy()).toList();
    }

    public synchronized void sent(String id, Instant now) {
        pending.computeIfPresent(id, (key, item) -> new Pending(item.observer(), item.createdAt(), now, item.payload()));
    }

    public synchronized void acknowledge(String id) {
        pending.remove(id);
    }

    private static JsonObject identity(GuildRankEventParser.Identity identity) {
        JsonObject value = new JsonObject();
        value.addProperty("display_name", identity.displayName());
        value.addProperty("username", identity.username());
        value.addProperty("source", identity.source());
        return value;
    }

    private record Pending(UUID observer, Instant createdAt, Instant sentAt, JsonObject payload) {}
}
