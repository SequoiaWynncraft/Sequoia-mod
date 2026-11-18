package com.seqwawa.seq.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.session.SessionManager;
import com.seqwawa.seq.ui.unlock.UnlockPopup;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Listens to Leaf's event stream ({@code GET /v1/me/events/stream}) while the
 * player is on Wynncraft and connected to Sequoia, and turns each event into a
 * popup. Leaf ends the stream every 15 minutes; it reconnects with the last id
 * it saw, so nothing in between is missed and nothing is shown twice. A first
 * connection without an id starts from now, so old events are never replayed.
 * <p>
 * Each session has its own, reading with that session's token and handing popups to
 * that session's queue. When the session ends its stream stops, and a popup it had
 * already received lands in a queue nobody draws, so the next account never sees it.
 */
public final class LeafEventStream {

    private static final long FIRST_RETRY_MS = 5_000;
    private static final long LONGEST_RETRY_MS = 60_000;
    /** Someone who is not a guild member asks again this rarely. */
    private static final long NOT_MEMBER_RETRY_MS = 15 * 60_000;

    /** One for every session's stream: each client keeps a thread of its own. */
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final Function<Boolean, CompletableFuture<String>> tokens;
    private final Consumer<UnlockPopup> show;
    private final Runnable leafChanged;
    private volatile Thread worker;
    private volatile Stream<String> open;
    private volatile String lastEventId;

    /**
     * Reads with {@code tokens}, the session's own, so a stream whose session has ended
     * cannot sign in again. Resumes after {@code resumeAfter}, or from now when it is null.
     * Each popup goes to {@code show}, and {@code leafChanged} hears that the balance moved;
     * both are called on the stream's own thread.
     */
    public LeafEventStream(Function<Boolean, CompletableFuture<String>> tokens, String resumeAfter,
            Consumer<UnlockPopup> show, Runnable leafChanged) {
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.lastEventId = resumeAfter;
        this.show = Objects.requireNonNull(show, "show");
        this.leafChanged = Objects.requireNonNull(leafChanged, "leafChanged");
    }

    /** The current session's. */
    public static LeafEventStream getInstance() {
        return SessionManager.getInstance().current().leafEvents();
    }

    /** Called every client tick: listens while {@code allowed}, and stops otherwise. */
    public synchronized void tick(boolean allowed) {
        boolean wanted = allowed && WynncraftServerPolicy.isCurrentServerAllowed() && ConnectionManager.isConnected();
        if (wanted && worker == null) {
            Thread thread = new Thread(this::run, "seq-leaf-events");
            thread.setDaemon(true);
            worker = thread;
            thread.start();
        } else if (!wanted && worker != null) {
            stop();
        }
    }

    /** The id of the last event seen, which the next connection resumes after, or null to start from now. */
    public String cursor() {
        return lastEventId;
    }

    /**
     * Remembers the last event {@code reader} saw, while it is still the stream's worker. Checked
     * under the lock {@link #stop()} takes, so once stop() returns the place no longer moves, and
     * the next session copies it whole. Says whether {@code reader} may go on.
     */
    synchronized boolean advanceCursor(Thread reader, String eventId) {
        if (worker != reader) return false;
        if (eventId != null) lastEventId = eventId;
        return true;
    }

    /** Reads {@code lines} on the calling thread as if it were the stream's worker, for a test. */
    void readAsWorker(Stream<String> lines) {
        synchronized (this) {
            worker = Thread.currentThread();
        }
        read(lines);
    }

    public synchronized void stop() {
        Thread thread = worker;
        worker = null;
        Stream<String> stream = open;
        if (stream != null) stream.close();
        if (thread != null) thread.interrupt();
    }

    private void run() {
        long retryMs = FIRST_RETRY_MS;
        boolean freshToken = false;
        while (worker == Thread.currentThread()) {
            long waitMs = retryMs;
            try {
                String token = tokens.apply(freshToken).get(30, TimeUnit.SECONDS);
                freshToken = false;
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BuildConfig.LEAF_API_URL + "/v1/me/events/stream"))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "text/event-stream")
                        .header(ClientVersion.MOD_VERSION_HEADER, ClientVersion.resolveInstalledVersion())
                        .GET();
                if (lastEventId != null) request.header("Last-Event-ID", lastEventId);
                HttpResponse<Stream<String>> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofLines());
                int status = response.statusCode();
                if (status != 200) {
                    response.body().close();
                    if (status == 401) freshToken = true;
                    if (status == 403) waitMs = NOT_MEMBER_RETRY_MS;
                } else {
                    retryMs = FIRST_RETRY_MS;
                    waitMs = 1_000;
                    read(response.body());
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

    private void read(Stream<String> lines) {
        open = lines;
        try (lines) {
            SseParser parser = new SseParser();
            Iterator<String> iterator = lines.iterator();
            while (worker == Thread.currentThread() && iterator.hasNext()) {
                SseParser.Event event = parser.accept(iterator.next());
                // A line read as the stream stops is the next session's to show.
                if (!advanceCursor(Thread.currentThread(), parser.lastEventId())) return;
                if (event != null) {
                    dispatch(event, popup -> {
                        leafChanged.run();
                        show.accept(popup);
                    });
                }
            }
        } finally {
            open = null;
        }
    }

    /** Turns one event into a popup, skipping what it cannot show. */
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
        show.accept(popup);
    }
}
