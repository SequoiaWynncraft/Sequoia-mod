package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.TestApiClients;
import com.seqwawa.seq.session.Session;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A party finder backend on this machine. Each route answers when the test says so, so a reply
 * can arrive after the player has signed out or reconnected, and every request is written down
 * with the token it carried.
 */
final class FakePartyFinderBackend implements AutoCloseable {

    record Request(String route, String authorization) {}

    final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Supplier<CompletableFuture<String>>> routes = new ConcurrentHashMap<>();
    private final HttpServer server;

    FakePartyFinderBackend() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        // A held reply must not keep the next request waiting.
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-party-finder-backend");
            thread.setDaemon(true);
            return thread;
        }));
        server.createContext("/", exchange -> {
            String route = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            requests.add(new Request(route, exchange.getRequestHeaders().getFirst("Authorization")));
            Supplier<CompletableFuture<String>> answer = routes.get(route);
            String body = null;
            try {
                body = answer == null ? null : answer.get().get(10, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // No answer in time: a 404, as for a route it doesn't know.
            }
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    /** Answers {@code route}, such as {@code "GET /party-finder/listings"}, with {@code json} at once. */
    void answer(String route, String json) {
        routes.put(route, () -> CompletableFuture.completedFuture(json));
    }

    /** Holds {@code route}'s reply until the test completes the future returned. */
    CompletableFuture<String> hold(String route) {
        CompletableFuture<String> reply = new CompletableFuture<>();
        routes.put(route, () -> reply);
        return reply;
    }

    /** Waits until {@code route} has been asked for. */
    void awaitRequest(String route) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (requests.stream().noneMatch(request -> request.route().equals(route))) {
            assertTrue(System.nanoTime() < deadline, "the backend never saw " + route);
            Thread.sleep(5);
        }
    }

    boolean saw(String route) {
        return requests.stream().anyMatch(request -> request.route().equals(route));
    }

    /** A client of this backend carrying {@code session}'s token, as the game gives each session's party finder. */
    ApiClient client(Session session) {
        return TestApiClients.at("http://127.0.0.1:" + server.getAddress().getPort()).forSession(session);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
