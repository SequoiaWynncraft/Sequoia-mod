package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.Clocks;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The game's socket against a real, if tiny, WebSocket server on this machine: the handshake and
 * its headers, text both ways, closes from either side, a refused handshake, an unreachable
 * backend, pings, and a backend that stops answering. The JDK has no WebSocket server, so this
 * one speaks just enough RFC 6455 by hand.
 */
class JdkBackendSocketTest {

    private static final Duration QUICK = Duration.ofMillis(200);
    private static final Duration LONG = Duration.ofSeconds(30);

    private final Events events = new Events();
    private TinyServer server;
    private JdkBackendSocket socket;

    @AfterEach
    void stop() throws IOException {
        if (socket != null) {
            socket.close(1000, "");
        }
        if (server != null) {
            server.close();
        }
    }

    private void openSocket(Duration pingEvery, Duration closeWait) throws Exception {
        openSocket(pingEvery, pingEvery.multipliedBy(2), closeWait);
    }

    private void openSocket(Duration pingEvery, Duration replyDeadline, Duration closeWait) throws Exception {
        server = new TinyServer();
        socket = socket(server.uri(), pingEvery, replyDeadline, closeWait);
        socket.connect(Map.of("Authorization", "Bearer session-token", ClientVersion.MOD_VERSION_HEADER, "1.13.0"));
        server.accept(101);
        assertEquals("open", events.next());
        assertTrue(socket.isOpen());
    }

    private JdkBackendSocket socket(URI uri, Duration pingEvery, Duration replyDeadline, Duration closeWait) {
        return new JdkBackendSocket(uri, events, pingEvery, replyDeadline, closeWait, Clocks::monotonicMillis);
    }

    @Test
    void theHandshakeCarriesTheHeadersAndTextGoesBothWays() throws Exception {
        openSocket(Duration.ofSeconds(30), Duration.ofSeconds(5));

        assertEquals("Bearer session-token", server.requestHeaders.get("authorization"));
        assertEquals("1.13.0", server.requestHeaders.get(ClientVersion.MOD_VERSION_HEADER.toLowerCase(Locale.ROOT)));
        assertTrue(socket.send("{\"type\":\"get_connected\"}"));
        assertTrue(socket.send("{\"type\":\"party_class_update\"}"));
        assertEquals("text:{\"type\":\"get_connected\"}", server.read());
        assertEquals("text:{\"type\":\"party_class_update\"}", server.read(), "in the order they were sent");

        server.sendText("{\"type\":\"authenticated\"}");
        assertEquals("message:{\"type\":\"authenticated\"}", events.next());
    }

    @Test
    void theBackendsCloseIsReportedAsRemoteAndAnswered() throws Exception {
        openSocket(Duration.ofSeconds(30), Duration.ofSeconds(5));

        server.sendClose(1008, "party_finder_banned");

        assertEquals("close:1008:party_finder_banned:true:false", events.next(), "the backend's own close");
        // This side closing after the backend did changes nothing.
        socket.close(1000, "");
        assertEquals("close:1008:", server.read(), "the JDK answers the backend's close");
        assertFalse(socket.isOpen());
        assertTrue(socket.isClosing());
        assertFalse(socket.send("{\"type\":\"guild_chat\"}"), "nothing goes once closed");
        events.assertQuiet();
    }

    @Test
    void theClientsCloseGoesOutAndTheBackendsAnswerEndsIt() throws Exception {
        openSocket(Duration.ofSeconds(30), Duration.ofSeconds(5));
        assertTrue(socket.send("{\"type\":\"guild_chat\"}"));

        socket.close(4001, "Re-authenticating");

        assertTrue(socket.isClosing(), "closing at once, before the backend answers");
        assertFalse(socket.isOpen());
        assertEquals("text:{\"type\":\"guild_chat\"}", server.read(), "frames sent before the close go first");
        assertEquals("close:4001:Re-authenticating", server.read());
        server.sendClose(4001, "Re-authenticating");
        assertEquals("close:4001:Re-authenticating:true:true", events.next(), "the answer to this side's close");
        events.assertQuiet();
    }

    @Test
    void aCloseTheBackendNeverAnswersEndsOnceTheWaitRunsOut() throws Exception {
        openSocket(Duration.ofSeconds(30), QUICK);

        socket.close(1000, "");

        assertEquals("close:1000:", server.read());
        assertEquals("close:1000::false:true", events.next());
        events.assertQuiet();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"401, AUTH_REJECTED", "403, HANDSHAKE_FORBIDDEN", "426, VERSION_UNSUPPORTED", "502, OTHER"})
    void aRefusedHandshakeClosesWithItsStatusAsTheClassifierReadsIt(int status, BackendCloseClassifier.Kind kind)
            throws Exception {
        server = new TinyServer();
        socket = socket(server.uri(), LONG, LONG, Duration.ofSeconds(5));
        socket.connect(Map.of());

        server.accept(status);

        String reason = "Invalid status code received: " + status;
        assertEquals("close:1002:" + reason + ":false:false", events.next());
        assertEquals(kind, BackendCloseClassifier.classify(BackendCloseClassifier.HANDSHAKE_FAILED, reason, false));
        assertFalse(socket.isOpen());
        events.assertQuiet();
    }

    @Test
    void aCloseWhileConnectingEndsAtOnceAndDropsTheHandshake() throws Exception {
        server = new TinyServer();
        socket = socket(server.uri(), LONG, LONG, Duration.ofSeconds(5));
        socket.connect(Map.of());
        server.takeRequest();

        // The backend has yet to answer the handshake.
        socket.close(1000, "");

        assertEquals("close:1000::false:true", events.next());
        assertTrue(socket.isClosing());
        assertEquals(-1, server.in.read(), "the handshake's connection is dropped");
        events.assertQuiet();
    }

    @Test
    void anUnreachableBackendIsAnErrorThenAClose() throws Exception {
        int port;
        try (ServerSocket nobody = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = nobody.getLocalPort();
        }
        socket = socket(URI.create("ws://127.0.0.1:" + port + "/ws"), LONG, LONG, Duration.ofSeconds(5));

        socket.connect(Map.of());

        assertTrue(events.next().startsWith("error:"));
        assertTrue(events.next().matches("close:1006:.*:false:false"));
        assertTrue(socket.isClosing());
        events.assertQuiet();
    }

    @Test
    void theBackendsPingIsAnsweredWithAPong() throws Exception {
        openSocket(Duration.ofSeconds(30), Duration.ofSeconds(5));

        server.sendPing();

        assertEquals("pong", server.read());
        assertTrue(socket.isOpen());
    }

    @Test
    void aBackendThatStopsAnsweringPingsIsGivenUp() throws Exception {
        openSocket(QUICK, Duration.ofSeconds(5));

        assertEquals("ping", server.read(), "a quiet socket pings");
        // The server never answers.
        assertEquals("close:1006:The backend stopped answering:false:false", events.next());
        assertFalse(socket.isOpen());
        events.assertQuiet();
    }

    @Test
    void aBackendThatKeepsPingingButNeverAnswersOurPingIsGivenUp() throws Exception {
        openSocket(QUICK, Duration.ofSeconds(5));
        Thread chatter = new Thread(() -> {
            try {
                while (true) {
                    server.sendPing();
                    Thread.sleep(QUICK.toMillis() / 4);
                }
            } catch (IOException | InterruptedException ended) {
                // The socket was given up.
            }
        });
        chatter.setDaemon(true);
        chatter.start();

        assertEquals("close:1006:The backend stopped answering:false:false", events.next());
        assertFalse(socket.isOpen());
        chatter.interrupt();
    }

    @Test
    void aBackendThatAnswersPingsStaysOpen() throws Exception {
        openSocket(QUICK, Duration.ofSeconds(5));

        for (int ping = 0; ping < 3; ping++) {
            assertEquals("ping", server.read());
            server.sendPong(server.lastPing);
        }

        assertTrue(socket.isOpen());
        events.assertQuiet();
    }

    @Test
    void aPongThatDoesNotCarryOurPingsDataIsNoAnswer() throws Exception {
        openSocket(QUICK, Duration.ofMillis(700), Duration.ofSeconds(5));
        // Every ping gets a pong straight away, but never one that answers it: unasked, or for a ping never sent.
        Thread wrongPongs = new Thread(() -> {
            try {
                for (int ping = 0; ; ping++) {
                    server.read();
                    server.sendPong(ping % 2 == 0 ? new byte[0] : ByteBuffer.allocate(Long.BYTES).putLong(1_000_000).array());
                }
            } catch (IOException ended) {
                // The socket was given up.
            }
        });
        wrongPongs.setDaemon(true);
        wrongPongs.start();

        assertEquals("close:1006:The backend stopped answering:false:false", events.next());
        assertFalse(socket.isOpen());
    }

    @Test
    void aPongThatComesAfterTheNextPingIsDueStillAnswersWithinTheDeadline() throws Exception {
        // Each pong comes after the next ping is due, as a pong 40 s late did with the 30 s pings this socket
        // sent before, but well inside the reply deadline.
        openSocket(Duration.ofMillis(200), Duration.ofMillis(800), Duration.ofSeconds(5));
        ScheduledExecutorService late = Executors.newSingleThreadScheduledExecutor();
        try {
            for (int ping = 0; ping < 6; ping++) {
                assertEquals("ping", server.read());
                byte[] answering = server.lastPing;
                late.schedule(() -> {
                    server.sendPong(answering);
                    return null;
                }, 300, TimeUnit.MILLISECONDS);
            }

            assertTrue(socket.isOpen());
            events.assertQuiet();
        } finally {
            late.shutdownNow();
        }
    }

    /** What the socket reports, in order. */
    private static final class Events implements BackendSocket.Listener {
        private final BlockingQueue<String> reported = new LinkedBlockingQueue<>();

        @Override
        public void onOpen() {
            reported.add("open");
        }

        @Override
        public void onMessage(String message) {
            reported.add("message:" + message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote, boolean byClient) {
            reported.add("close:" + code + ":" + reason + ":" + remote + ":" + byClient);
        }

        @Override
        public void onError(Exception error) {
            reported.add("error:" + error);
        }

        String next() throws InterruptedException {
            String event = reported.poll(5, TimeUnit.SECONDS);
            assertNotNull(event, "the socket reported nothing");
            return event;
        }

        void assertQuiet() throws InterruptedException {
            assertNull(reported.poll(300, TimeUnit.MILLISECONDS), "nothing after the close");
        }
    }

    /** One connection's worth of a WebSocket server: the handshake, and unmasked frames out, masked frames in. */
    private static final class TinyServer implements AutoCloseable {
        private final ServerSocket listening = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        private Socket connection;
        private DataInputStream in;
        private OutputStream out;
        final Map<String, String> requestHeaders = new HashMap<>();
        /** The data the client's last ping carried. */
        volatile byte[] lastPing;

        TinyServer() throws IOException {}

        URI uri() {
            return URI.create("ws://127.0.0.1:" + listening.getLocalPort() + "/ws");
        }

        void accept(int status) throws Exception {
            takeRequest();
            answer(status);
        }

        /** Takes the connection and reads the handshake request, without answering it. */
        void takeRequest() throws IOException {
            connection = listening.accept();
            connection.setSoTimeout(5_000);
            in = new DataInputStream(connection.getInputStream());
            out = connection.getOutputStream();
            for (String line = readLine(in); !line.isEmpty(); line = readLine(in)) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    requestHeaders.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                }
            }
        }

        void answer(int status) throws Exception {
            if (status != 101) {
                out.write(("HTTP/1.1 " + status + " Refused\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                out.flush();
                return;
            }
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(
                    (requestHeaders.get("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                            .getBytes(StandardCharsets.US_ASCII));
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + Base64.getEncoder().encodeToString(digest) + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        void sendText(String text) throws IOException {
            frame(0x1, text.getBytes(StandardCharsets.UTF_8));
        }

        void sendClose(int code, String reason) throws IOException {
            byte[] words = reason.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[2 + words.length];
            payload[0] = (byte) (code >> 8);
            payload[1] = (byte) code;
            System.arraycopy(words, 0, payload, 2, words.length);
            frame(0x8, payload);
        }

        void sendPing() throws IOException {
            frame(0x9, new byte[0]);
        }

        void sendPong(byte[] payload) throws IOException {
            frame(0xA, payload);
        }

        private synchronized void frame(int opcode, byte[] payload) throws IOException {
            out.write(0x80 | opcode);
            out.write(payload.length);
            out.write(payload);
            out.flush();
        }

        /** The next frame the client sent, as "text:...", "close:code:reason", "ping" or "pong". */
        String read() throws IOException {
            int opcode = in.readUnsignedByte() & 0x0F;
            int second = in.readUnsignedByte();
            long length = second & 0x7F;
            if (length == 126) {
                length = in.readUnsignedShort();
            } else if (length == 127) {
                length = in.readLong();
            }
            byte[] mask = new byte[4];
            if ((second & 0x80) != 0) {
                in.readFully(mask);
            }
            byte[] payload = new byte[(int) length];
            in.readFully(payload);
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= mask[i % 4];
            }
            return switch (opcode) {
                case 0x1 -> "text:" + new String(payload, StandardCharsets.UTF_8);
                case 0x8 -> "close:" + (((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF)) + ":"
                        + new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
                case 0x9 -> {
                    lastPing = payload;
                    yield "ping";
                }
                case 0xA -> "pong";
                default -> "opcode:" + opcode;
            };
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            for (int b = in.read(); b != -1 && b != '\n'; b = in.read()) {
                if (b != '\r') {
                    line.write(b);
                }
            }
            return line.toString(StandardCharsets.US_ASCII);
        }

        @Override
        public void close() throws IOException {
            if (connection != null) {
                connection.close();
            }
            listening.close();
        }
    }
}
