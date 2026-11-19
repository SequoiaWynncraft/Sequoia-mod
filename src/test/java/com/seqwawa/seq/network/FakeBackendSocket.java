package com.seqwawa.seq.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A backend socket a test drives by hand. The client side behaves as {@link JdkBackendSocket}
 * does: it connects once, sends nothing unless open, and stops being open as soon as it starts
 * to close. The backend side ({@link #open()}, {@link #receive}, the closes) fires the
 * listener on the test's thread, as the real socket fires it on its own.
 */
final class FakeBackendSocket implements BackendSocket {

    private final URI uri;
    private final Listener listener;
    private Map<String, String> headers;
    private int connects;
    private boolean open;
    private boolean closing;
    private final List<String> frames = new ArrayList<>();
    private int closes;
    private int closeCode;
    private String closeReason;
    /** Thrown by {@link #connect}, as the real socket throws for handshake headers it can't send. */
    RuntimeException connectFailure;

    FakeBackendSocket(URI uri, Listener listener) {
        this.uri = uri;
        this.listener = listener;
    }

    // ── What the client does ──

    @Override
    public void connect(Map<String, String> headers) {
        if (connectFailure != null) {
            throw connectFailure;
        }
        if (++connects > 1) {
            throw new IllegalStateException("A socket connects once");
        }
        this.headers = Map.copyOf(headers);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean isClosing() {
        boolean answer = closing;
        if (startClosingAfterNextClosingCheck) {
            // The backend's close begins right after the client looked, before its next read.
            startClosingAfterNextClosingCheck = false;
            startClosingFromBackend();
        }
        return answer;
    }

    /** The next {@link #isClosing} answers "no", and the backend starts closing just after. */
    boolean startClosingAfterNextClosingCheck;

    @Override
    public boolean send(String text) {
        if (!open) {
            return false;
        }
        frames.add(text);
        return true;
    }

    @Override
    public void close(int code, String reason) {
        open = false;
        closing = true;
        closes++;
        closeCode = code;
        closeReason = reason;
    }

    @Override
    public URI uri() {
        return uri;
    }

    // ── What the backend does ──

    /** The handshake succeeds. */
    void open() {
        open = true;
        listener.onOpen();
    }

    void receive(String message) {
        listener.onMessage(message);
    }

    /** The backend closes the socket. */
    void closeFromBackend(int code, String reason) {
        startClosingFromBackend();
        finishClosingFromBackend(code, reason);
    }

    /** The backend starts closing: the socket is no longer open, and the library hasn't reported it yet. */
    void startClosingFromBackend() {
        open = false;
        closing = true;
    }

    /** The library reports the close the backend started. */
    void finishClosingFromBackend(int code, String reason) {
        listener.onClose(code, reason, true);
    }

    /** The backend answers the client's own close, which the socket reports as remote. */
    void echoClientClose() {
        if (closes == 0) {
            throw new IllegalStateException("The client never closed this socket");
        }
        listener.onClose(closeCode, closeReason, true);
    }

    /** The socket closes on this side without a backend answer, as a dropped network does. */
    void closeLocally(int code, String reason) {
        open = false;
        closing = true;
        listener.onClose(code, reason, false);
    }

    /** Connecting fails, as an unreachable backend does: the socket is done, then reports an error and a close. */
    void failToConnect(String message) {
        open = false;
        closing = true;
        listener.onError(new java.net.ConnectException(message));
        listener.onClose(-1, message, false);
    }

    void error(Exception error) {
        listener.onError(error);
    }

    // ── What a test reads ──

    boolean connected() {
        return connects > 0;
    }

    Map<String, String> headers() {
        return headers;
    }

    List<JsonObject> sent() {
        return frames.stream().map(frame -> JsonParser.parseString(frame).getAsJsonObject()).toList();
    }

    List<String> sentTypes() {
        return sent().stream().map(json -> json.get("type").getAsString()).toList();
    }

    /** The last message of {@code type} sent, or null. */
    JsonObject lastSent(String type) {
        List<JsonObject> matching = sent().stream()
                .filter(json -> type.equals(json.get("type").getAsString()))
                .toList();
        return matching.isEmpty() ? null : matching.getLast();
    }

    void clearSent() {
        frames.clear();
    }

    int closes() {
        return closes;
    }

    int closeCode() {
        return closeCode;
    }

    String closeReason() {
        return closeReason;
    }
}
