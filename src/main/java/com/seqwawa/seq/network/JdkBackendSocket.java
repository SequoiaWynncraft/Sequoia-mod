package com.seqwawa.seq.network;

import com.seqwawa.seq.client.Clocks;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * The backend socket the game uses, on the JDK's own WebSocket client. It reports to its listener
 * under its lock, so no two calls overlap, and ends with one {@code onClose} however the socket ends:
 * the backend closes or answers our close, refuses or never finishes the handshake, can't be reached,
 * stops answering pings, or never answers our close, or this side closes it while it connects. That
 * close says whether this side's close came first, decided under the same lock.
 */
final class JdkBackendSocket implements BackendSocket {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final int ABNORMAL_CLOSE = 1006;

    private final URI uri;
    private final Listener listener;
    /** How often the socket pings. */
    private final Duration pingEvery;
    /** How long the backend may go without answering a ping, checked as each ping is due. */
    private final Duration replyDeadline;
    /** How long a close waits for the backend's answer. */
    private final Duration closeWait;
    /** How long the handshake may take, the body of a refusal included. */
    private final Duration handshakeWait;
    private final LongSupplier clockMs;
    private WebSocket webSocket;
    /** The handshake, which a close while connecting cancels. */
    private volatile CompletableFuture<WebSocket> handshake = CompletableFuture.completedFuture(null);
    private boolean open;
    private boolean closing;
    private boolean ended;
    private int closeCode;
    private String closeReason;
    /** Each text frame waits for the one before it: the JDK takes one at a time. */
    private CompletableFuture<?> sending = CompletableFuture.completedFuture(null);
    /** The last ping's number; each ping carries its own. */
    private volatile long pinged;
    /** When each ping no pong has answered went out, kept until the reply deadline passes: a pong answers one, once. */
    private final Map<Long, Long> unanswered = new ConcurrentHashMap<>();
    /** When the backend last answered a ping, or the socket opened. */
    private volatile long answeredAt;

    /** Pings as Java-WebSocket did: once a minute, giving up once 90 seconds pass without an answer. */
    JdkBackendSocket(URI uri, Listener listener) {
        this(uri, listener, Duration.ofSeconds(60), Duration.ofSeconds(90), Duration.ofSeconds(5), Duration.ofSeconds(15),
                Clocks::monotonicMillis);
    }

    JdkBackendSocket(URI uri, Listener listener, Duration pingEvery, Duration replyDeadline, Duration closeWait,
            Duration handshakeWait, LongSupplier clockMs) {
        this.uri = uri;
        this.listener = listener;
        this.pingEvery = pingEvery;
        this.replyDeadline = replyDeadline;
        this.closeWait = closeWait;
        this.handshakeWait = handshakeWait;
        this.clockMs = clockMs;
    }

    @Override
    public void connect(Map<String, String> headers) {
        WebSocket.Builder builder = CLIENT.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));
        headers.forEach(builder::header);
        handshake = builder.buildAsync(uri, new Events());
        handshake.whenComplete((socket, failure) -> {
            if (failure != null) {
                fail(failure);
            }
        });
        // The connect timeout stops at the reply's headers: a refusal whose body never ends would keep it connecting.
        later(handshakeWait, () -> {
            synchronized (this) {
                if (webSocket == null && !handshake.isDone()) {
                    end(ABNORMAL_CLOSE, "The handshake took too long", false, null);
                    handshake.cancel(true);
                }
            }
        });
    }

    @Override
    public synchronized boolean isOpen() {
        return open;
    }

    @Override
    public synchronized boolean isClosing() {
        return closing;
    }

    @Override
    public synchronized boolean send(String text) {
        if (!open) {
            return false;
        }
        WebSocket socket = webSocket;
        sending = sending.thenCompose(ignored -> socket.sendText(text, true));
        sending.exceptionally(failure -> {
            fail(failure);
            return null;
        });
        return true;
    }

    @Override
    public void close(int code, String reason) {
        WebSocket socket;
        synchronized (this) {
            socket = webSocket;
            // Once the JDK has read the backend's close, or failed, that came first: its report, waiting for this lock, says so.
            if (closing || socket != null && socket.isInputClosed()) {
                open = false;
                return;
            }
            closing = true;
            open = false;
            closeCode = code;
            closeReason = reason;
            if (socket == null) {
                // Still connecting: it ends now, and a socket that opens anyway is cut off at once.
                end(code, reason, false, null);
                handshake.cancel(true);
                return;
            }
            sending = sending.handle((ignored, failure) -> null).thenCompose(ignored -> socket.sendClose(code, reason));
        }
        later(closeWait, () -> {
            socket.abort();
            end(code, reason, false, null);
        });
    }

    @Override
    public URI uri() {
        return uri;
    }

    private synchronized void report(Runnable call) {
        if (!ended) {
            call.run();
        }
    }

    /** Reports the socket's end, the first time only: a failure first when there was one, then the close. */
    private synchronized void end(int code, String reason, boolean remote, Exception failure) {
        if (ended) {
            return;
        }
        // Only close() starts closing before the end: the close reported answers this side's, or is the backend's own.
        boolean byClient = closing;
        ended = true;
        open = false;
        closing = true;
        if (failure != null) {
            listener.onError(failure);
        }
        listener.onClose(code, reason, remote, byClient);
    }

    private void fail(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof WebSocketHandshakeException refused) {
            end(BackendCloseClassifier.HANDSHAKE_FAILED,
                    "Invalid status code received: " + refused.getResponse().statusCode(), false, null);
            return;
        }
        WebSocket socket;
        synchronized (this) {
            socket = webSocket;
        }
        if (socket != null) {
            socket.abort();
        }
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        end(ABNORMAL_CLOSE, message, false, cause instanceof Exception exception ? exception : new IOException(cause));
    }

    /** Beats the heartbeat every {@link #pingEvery}, until the socket closes. */
    private void pingLater() {
        later(pingEvery, () -> {
            if (heartbeat()) {
                pingLater();
            }
        });
    }

    /**
     * Pings, unless no pong answered within {@link #replyDeadline}, or the ping can't go out: then the
     * socket is given up. Once a close is under way, its answer decides how the socket ends, and the
     * heartbeat stops. Says whether it goes on. A test calls it itself, to ping when it means to.
     */
    synchronized boolean heartbeat() {
        if (closing) {
            return false;
        }
        long now = clockMs.getAsLong();
        if (now - answeredAt > replyDeadline.toMillis()) {
            webSocket.abort();
            end(ABNORMAL_CLOSE, "The backend stopped answering", false, null);
            return false;
        }
        unanswered.values().removeIf(sentAt -> now - sentAt > replyDeadline.toMillis());
        unanswered.put(++pinged, now);
        webSocket.sendPing(ByteBuffer.allocate(Long.BYTES).putLong(0, pinged)).exceptionally(failure -> {
            // A close that began since shuts the output: the ping failing is not the socket failing.
            if (!isClosing()) {
                fail(failure);
            }
            return null;
        });
        return true;
    }

    private static void later(Duration delay, Runnable task) {
        CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS).execute(task);
    }

    /** What the JDK reports, one call at a time; a text frame may come in parts. */
    private final class Events implements WebSocket.Listener {

        private final StringBuilder text = new StringBuilder();

        @Override
        public void onOpen(WebSocket socket) {
            synchronized (JdkBackendSocket.this) {
                webSocket = socket;
                if (closing) {
                    socket.abort();
                    end(closeCode, closeReason, false, null);
                    return;
                }
                open = true;
                answeredAt = clockMs.getAsLong();
                listener.onOpen();
            }
            socket.request(1);
            pingLater();
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                String message = text.toString();
                text.setLength(0);
                report(() -> listener.onMessage(message));
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket socket, ByteBuffer message) {
            // The JDK answers with a pong by itself.
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket socket, ByteBuffer message) {
            // Only a pong carrying an unanswered ping's data answers it (RFC 6455 section 5.5.3); one may come unasked,
            // again, or after its ping's reply deadline, before the next ping drops that ping.
            Long sentAt = message.remaining() == Long.BYTES ? unanswered.remove(message.getLong(message.position())) : null;
            long now = clockMs.getAsLong();
            if (sentAt != null && now - sentAt <= replyDeadline.toMillis()) {
                answeredAt = now;
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            // The backend's close, or its answer to ours; the JDK answers a close of the backend's once this returns.
            end(code, reason, true, null);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            fail(error);
        }
    }
}
