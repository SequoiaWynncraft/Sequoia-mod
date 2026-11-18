package com.seqwawa.seq.network;

import java.net.URI;
import java.util.Map;

/**
 * The backend websocket as {@link ConnectionManager} uses it. Production wraps
 * Java-WebSocket's client ({@link JavaBackendSocket}); a test plays the backend's part
 * by firing the listener's events itself, so it needs no network.
 * <p>
 * One socket makes one connection attempt: a reconnect asks the factory for a new one.
 */
interface BackendSocket {

    /** Starts connecting, sending these handshake headers. The listener hears how it went. */
    void connect(Map<String, String> headers);

    boolean isOpen();

    /**
     * Whether the socket has started closing, or closed, so it can't be used again. Java-WebSocket
     * gets here before it calls {@link Listener#onClose}, so for a moment a socket is closing and
     * nobody has heard about it yet.
     */
    boolean isClosing();

    /**
     * Sends one text frame. Like Java-WebSocket, throws
     * {@link org.java_websocket.exceptions.WebsocketNotConnectedException} when the socket
     * is not open, which the callers that race a close rely on.
     */
    void send(String text);

    /** Closes normally (1000). The listener hears the close once the backend echoes it. */
    void close();

    void close(int code, String reason);

    /** Where the socket connects, for the logs. */
    URI uri();

    /**
     * What a socket reports. Java-WebSocket calls these on the socket's own thread, one at a
     * time and in order.
     */
    interface Listener {

        /** The handshake succeeded; {@code httpStatus} is -1 when the library gave none. */
        void onOpen(int httpStatus, String httpStatusMessage);

        void onMessage(String message);

        /** {@code remote} is true when the close came from the backend, including its echo of ours. */
        void onClose(int code, String reason, boolean remote);

        void onError(Exception error);
    }

    @FunctionalInterface
    interface Factory {
        BackendSocket create(URI uri, Listener listener);
    }
}
