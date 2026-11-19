package com.seqwawa.seq.network;

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
import java.util.concurrent.TimeUnit;

/**
 * The backend socket the game uses, on the JDK's own WebSocket client. It reports to its listener
 * under its lock, so no two calls overlap, and ends with one {@code onClose} however the socket ends:
 * the backend closes or answers our close, refuses the handshake, can't be reached, stops answering
 * pings, or never answers our close.
 */
final class JdkBackendSocket implements BackendSocket {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final int ABNORMAL_CLOSE = 1006;

    private final URI uri;
    private final Listener listener;
    /** How often the socket pings; one that hears nothing back for that long is given up. */
    private final Duration pingEvery;
    /** How long a close waits for the backend's answer. */
    private final Duration closeWait;
    private WebSocket webSocket;
    private boolean open;
    private boolean closing;
    private boolean ended;
    private int closeCode;
    private String closeReason;
    /** Each text frame waits for the one before it: the JDK takes one at a time. */
    private CompletableFuture<?> sending = CompletableFuture.completedFuture(null);
    /** Whether anything arrived since the last ping. */
    private volatile boolean heard = true;

    JdkBackendSocket(URI uri, Listener listener) {
        this(uri, listener, Duration.ofSeconds(30), Duration.ofSeconds(5));
    }

    JdkBackendSocket(URI uri, Listener listener, Duration pingEvery, Duration closeWait) {
        this.uri = uri;
        this.listener = listener;
        this.pingEvery = pingEvery;
        this.closeWait = closeWait;
    }

    @Override
    public void connect(Map<String, String> headers) {
        WebSocket.Builder builder = CLIENT.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));
        headers.forEach(builder::header);
        builder.buildAsync(uri, new Events()).whenComplete((socket, failure) -> {
            if (failure != null) {
                fail(failure);
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
            if (closing) {
                return;
            }
            closing = true;
            open = false;
            closeCode = code;
            closeReason = reason;
            socket = webSocket;
            if (socket == null) {
                // Still connecting: the socket is cut off once it opens.
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
        ended = true;
        open = false;
        closing = true;
        if (failure != null) {
            listener.onError(failure);
        }
        listener.onClose(code, reason, remote);
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

    /** Pings every {@link #pingEvery}; a socket that heard nothing since the last ping is given up. */
    private void pingLater(WebSocket socket) {
        later(pingEvery, () -> {
            if (isClosing()) {
                return;
            }
            if (!heard) {
                socket.abort();
                end(ABNORMAL_CLOSE, "The backend stopped answering", false, null);
                return;
            }
            heard = false;
            socket.sendPing(ByteBuffer.allocate(0));
            pingLater(socket);
        });
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
                listener.onOpen();
            }
            socket.request(1);
            pingLater(socket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            heard = true;
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
            heard = true;
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket socket, ByteBuffer message) {
            heard = true;
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
