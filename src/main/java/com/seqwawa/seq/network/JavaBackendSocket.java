package com.seqwawa.seq.network;

import java.net.URI;
import java.util.Map;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/** The backend socket the game uses: one Java-WebSocket client, which can connect only once. */
final class JavaBackendSocket implements BackendSocket {

    private final Client client;

    JavaBackendSocket(URI uri, Listener listener) {
        client = new Client(uri, listener);
    }

    @Override
    public void connect(Map<String, String> headers) {
        client.clearHeaders();
        headers.forEach(client::addHeader);
        client.connect();
    }

    @Override
    public boolean isOpen() {
        return client.isOpen();
    }

    @Override
    public boolean isClosing() {
        return client.isClosing() || client.isClosed();
    }

    @Override
    public void send(String text) {
        client.send(text);
    }

    @Override
    public void close() {
        client.close();
    }

    @Override
    public void close(int code, String reason) {
        client.close(code, reason);
    }

    @Override
    public URI uri() {
        return client.getURI();
    }

    private static final class Client extends WebSocketClient {

        private final Listener listener;

        Client(URI uri, Listener listener) {
            super(uri);
            this.listener = listener;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            listener.onOpen(
                    handshake != null ? handshake.getHttpStatus() : -1,
                    handshake != null ? handshake.getHttpStatusMessage() : "null");
        }

        @Override
        public void onMessage(String message) {
            listener.onMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            listener.onClose(code, reason, remote);
        }

        @Override
        public void onError(Exception ex) {
            listener.onError(ex);
        }
    }
}
