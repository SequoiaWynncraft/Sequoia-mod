package com.seqwawa.seq.network;

import java.net.URI;
import java.util.Map;

/**
 * The backend websocket as {@link ConnectionManager} uses it. The game's is {@link JdkBackendSocket};
 * a test plays the backend's part by firing the listener's events itself, so it needs no network.
 * <p>
 * One socket makes one connection attempt: a reconnect asks the factory for a new one.
 */
interface BackendSocket {

    /** Starts connecting, sending these handshake headers. Called once; the listener hears how it went. */
    void connect(Map<String, String> headers);

    boolean isOpen();

    /**
     * Whether the socket has started closing, or closed, so it can't be used again. It reads so
     * before the listener hears {@link Listener#onClose}.
     */
    boolean isClosing();

    /** Sends one text frame, after those sent before it. False, and nothing sent, when the socket isn't open. */
    boolean send(String text);

    /**
     * Closes with {@code code}. The listener hears the close once the backend answers it, or the wait
     * for that runs out; a socket still connecting stops, and hears it at once. A socket already closing
     * ignores it.
     */
    void close(int code, String reason);

    /** Where the socket connects, for the logs. */
    URI uri();

    /**
     * What a socket reports. The calls come from the socket's own threads, one at a time and never
     * overlapping, and every socket ends with exactly one {@link #onClose}, after which it reports
     * nothing: whether the backend closed, refused the handshake or stopped answering, the network
     * failed, or this side closed.
     */
    interface Listener {

        void onOpen();

        void onMessage(String message);

        /**
         * {@code remote} is true when the backend sent the close, its answer to ours included;
         * {@code byClient} when this side's close came first, so the close is not the backend's own. A
         * handshake the backend refused closes with {@link BackendCloseClassifier#HANDSHAKE_FAILED}
         * and a reason naming the HTTP status.
         */
        void onClose(int code, String reason, boolean remote, boolean byClient);

        /** Something failed; {@link #onClose} follows once the socket can't go on. */
        void onError(Exception error);
    }

    @FunctionalInterface
    interface Factory {
        BackendSocket create(URI uri, Listener listener);
    }
}
