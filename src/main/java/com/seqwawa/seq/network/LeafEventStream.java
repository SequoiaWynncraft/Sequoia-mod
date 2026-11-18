package com.seqwawa.seq.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.ui.unlock.UnlockOverlay;
import com.seqwawa.seq.ui.unlock.UnlockPopup;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Listens to Leaf's event stream ({@code GET /v1/me/events/stream}) while the
 * player is on Wynncraft and connected to Sequoia, and turns each event into a
 * popup. Leaf ends the stream every 15 minutes; it reconnects with the last id
 * it saw, so nothing in between is missed and nothing is shown twice. A first
 * connection without an id starts from now, so old events are never replayed.
 */
public final class LeafEventStream {

    private static final long FIRST_RETRY_MS = 5_000;
    private static final long LONGEST_RETRY_MS = 60_000;
    /** Someone who is not a guild member asks again this rarely. */
    private static final long NOT_MEMBER_RETRY_MS = 15 * 60_000;

    private static final LeafEventStream INSTANCE = new LeafEventStream();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile Thread worker;
    private volatile Stream<String> open;
    private volatile String lastEventId;
    /** Moves on at each {@link #reset()}; a worker's popups carry the value it started under. */
    private long generation;

    private LeafEventStream() {}

    public static LeafEventStream getInstance() {
        return INSTANCE;
    }

    /** Called every client tick: listens while {@code allowed}, and stops otherwise. */
    public synchronized void tick(boolean allowed) {
        boolean wanted = allowed && WynncraftServerPolicy.isCurrentServerAllowed() && ConnectionManager.isConnected();
        if (wanted && worker == null) {
            long startedFor = generation;
            Thread thread = new Thread(() -> run(startedFor), "seq-leaf-events");
            thread.setDaemon(true);
            worker = thread;
            thread.start();
        } else if (!wanted && worker != null) {
            stop();
        }
    }

    /** Stops listening and forgets where it was, for a different account. */
    public synchronized void reset() {
        stop();
        lastEventId = null;
        generation++;
    }

    /**
     * Stops listening for a session that ended while the account stayed the same, as a
     * rejected token does: popups the stopped stream had yet to hand over are dropped with
     * that session, but the next one resumes from the last event seen, so nothing that
     * happened while it signed in again is missed.
     */
    public synchronized void endSessionKeepingCursor() {
        stop();
        generation++;
    }

    /** The id of the last event seen, which the next connection resumes after, or null to start from now. */
    String cursor() {
        return lastEventId;
    }

    /** Remembers the last event seen. */
    void advanceCursor(String eventId) {
        if (eventId != null) lastEventId = eventId;
    }

    synchronized long generation() {
        return generation;
    }

    /**
     * {@code show}, for the client thread to run, unless the stream has been reset by then.
     * An account change resets the stream and clears the popups in one tick, so a popup
     * posted just before would otherwise still show the previous account's unlock.
     */
    Runnable unlessResetSince(long startedFor, Runnable show) {
        return () -> {
            if (generation() == startedFor) {
                show.run();
            }
        };
    }

    public synchronized void stop() {
        Thread thread = worker;
        worker = null;
        Stream<String> stream = open;
        if (stream != null) stream.close();
        if (thread != null) thread.interrupt();
    }

    private void run(long startedFor) {
        long retryMs = FIRST_RETRY_MS;
        boolean freshToken = false;
        while (worker == Thread.currentThread()) {
            long waitMs = retryMs;
            try {
                String token = SeqClient.getAuthService().ensureValidToken(freshToken).get(30, TimeUnit.SECONDS);
                freshToken = false;
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BuildConfig.LEAF_API_URL + "/v1/me/events/stream"))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "text/event-stream")
                        .header(ClientVersion.MOD_VERSION_HEADER, ClientVersion.resolveInstalledVersion())
                        .GET();
                if (lastEventId != null) request.header("Last-Event-ID", lastEventId);
                HttpResponse<Stream<String>> response = http.send(request.build(), HttpResponse.BodyHandlers.ofLines());
                int status = response.statusCode();
                if (status != 200) {
                    response.body().close();
                    if (status == 401) freshToken = true;
                    if (status == 403) waitMs = NOT_MEMBER_RETRY_MS;
                } else {
                    retryMs = FIRST_RETRY_MS;
                    waitMs = 1_000;
                    read(response.body(), startedFor);
                }
            } catch (InterruptedException interrupted) {
                return;
            } catch (Exception failure) {
                SeqClient.LOGGER.debug("[Leaf] Event stream interrupted", failure);
            }
            if (worker != Thread.currentThread()) return;
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException interrupted) {
                return;
            }
            retryMs = Math.min(LONGEST_RETRY_MS, retryMs * 2);
        }
    }

    private void read(Stream<String> lines, long startedFor) {
        open = lines;
        try (lines) {
            SseParser parser = new SseParser();
            Iterator<String> iterator = lines.iterator();
            while (worker == Thread.currentThread() && iterator.hasNext()) {
                SseParser.Event event = parser.accept(iterator.next());
                advanceCursor(parser.lastEventId());
                if (event != null) {
                    dispatch(event, popup -> SeqClient.mc.execute(
                            unlessResetSince(startedFor, () -> UnlockOverlay.getInstance().enqueue(popup))));
                }
            }
        } finally {
            open = null;
        }
    }

    /** Turns one event into a popup, and tells an open quest board that something changed. */
    static void dispatch(SseParser.Event event, Consumer<UnlockPopup> show) {
        JsonObject data;
        try {
            JsonElement parsed = JsonParser.parseString(event.data());
            if (!parsed.isJsonObject()) return;
            JsonElement inner = parsed.getAsJsonObject().get("data");
            data = inner != null && inner.isJsonObject() ? inner.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException malformed) {
            return;
        }
        UnlockPopup popup = UnlockPopup.fromEvent(event.type(), data);
        if (popup == null) return;
        LeafQuestService.getInstance().markStale();
        show.accept(popup);
    }
}
