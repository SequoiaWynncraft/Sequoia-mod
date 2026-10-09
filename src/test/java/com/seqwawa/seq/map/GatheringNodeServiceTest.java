package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.*;
import static com.seqwawa.seq.map.GatheringNodeSource.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GatheringNodeServiceTest {
    // Representative records captured from the official endpoint, one per profession.
    private static String apiFixture() throws IOException {
        try (var input = GatheringNodeServiceTest.class.getResourceAsStream("/map/gathering-nodes-api.json")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesOfficialArrayForEveryProfessionAndPublishesImmutableNodes() throws Exception {
        List<GatheringNode> nodes = GatheringNodeService.parseNodes(apiFixture());
        assertEquals(4, nodes.size());
        assertEquals(new GatheringNode(-1751, 59, -4420, 0, "CORNER", "COPPER", 1), nodes.getFirst());
        assertEquals(List.of(GatheringProfession.MINING, GatheringProfession.WOODCUTTING,
                GatheringProfession.FARMING, GatheringProfession.FISHING),
                nodes.stream().map(GatheringNode::profession).toList());
        assertThrows(UnsupportedOperationException.class, () -> nodes.clear());
    }

    @Test
    void skipsMalformedRecordsWithoutTruncatingCoordinatesOrLosingGoodNodes() {
        List<GatheringNode> nodes = GatheringNodeService.parseNodes("""
                [null, 42, {},
                 {"x":1.5,"y":64,"z":3,"angle":0,"type":"NODE","resource":"OAK","level":1},
                 {"x":2147483648,"y":64,"z":3,"angle":0,"type":"NODE","resource":"OAK","level":1},
                 {"x":1,"y":64,"z":3,"angle":0,"type":"NODE","resource":true,"level":1},
                 {"x":1,"y":64,"z":3,"angle":0,"type":"NODE","resource":" ","level":1},
                 {"x":1,"y":64,"z":3,"angle":90,"type":" wall ","resource":" oak ","level":1}]
                """);
        assertEquals(List.of(new GatheringNode(1, 64, 3, 90, "WALL", "OAK", 1)), nodes);
    }

    @Test
    void rejectsErrorObjectsEmptyArraysAndWhollyInvalidResponses() {
        for (String body : List.of("{}", "null", "[]", "[{}]", "{\"data\":[]}", "not JSON")) {
            assertThrows(RuntimeException.class, () -> GatheringNodeService.parseNodes(body), body);
        }
    }

    @Test
    void firstRequestAtClockZeroIsQueuedWithoutBlockingAndSuppressesConcurrentRefreshes() throws Exception {
        try (var fixture = new Fixture()) {
            var service = fixture.service;
            assertTrue(service.nodes(WYNN_API).isEmpty());
            assertEquals("Not loaded", service.status(WYNN_API));
            assertTrue(service.requestRefresh(WYNN_API));
            assertTrue(service.isLoading(WYNN_API));
            assertEquals("Loading nodes...", service.status(WYNN_API));
            assertEquals(0, fixture.requests.get(), "The caller does not perform HTTP or parsing.");
            fixture.now.set(GatheringNodeService.REFRESH_INTERVAL_MS);
            assertFalse(service.requestRefresh(WYNN_API), "Only one request may be in flight, even beyond the TTL.");
            assertEquals(1, fixture.work.size());
            fixture.finishRequest();
            assertFalse(service.isLoading(WYNN_API));
            assertEquals(4, service.nodes(WYNN_API).size());
            assertEquals("Loaded 4 nodes", service.status(WYNN_API));
            assertEquals("GET", fixture.method.get());
            assertEquals("application/json", fixture.accept.get());
            assertEquals("Sequoia-Mod", fixture.userAgent.get());
        }
    }

    @Test
    void refreshesHourlyRetainsIdentityForUnchangedDataAndReplacesChangedNodes() throws Exception {
        try (var fixture = new Fixture()) {
            assertTrue(fixture.service.requestRefresh(WYNN_API));
            fixture.finishRequest();
            List<GatheringNode> first = fixture.service.nodes(WYNN_API);
            fixture.now.set(GatheringNodeService.REFRESH_INTERVAL_MS - 1);
            assertFalse(fixture.service.requestRefresh(WYNN_API));
            fixture.now.incrementAndGet();
            assertTrue(fixture.service.requestRefresh(WYNN_API));
            assertEquals("Refreshing nodes...", fixture.service.status(WYNN_API));
            assertSame(first, fixture.service.nodes(WYNN_API), "Existing nodes stay available while fetching.");
            fixture.finishRequest();
            assertSame(first, fixture.service.nodes(WYNN_API), "Unchanged data must not trigger reclustering.");

            fixture.body.set(apiFixture().replace("-1751", "-1752"));
            fixture.now.addAndGet(GatheringNodeService.REFRESH_INTERVAL_MS);
            assertTrue(fixture.service.requestRefresh(WYNN_API));
            fixture.finishRequest();
            assertNotSame(first, fixture.service.nodes(WYNN_API));
            assertEquals(-1752, fixture.service.nodes(WYNN_API).getFirst().x());
            assertEquals(-1751, first.getFirst().x(), "Published snapshots remain immutable.");
            assertEquals(3, fixture.requests.get());
        }
    }

    @Test
    void firstHttpFailureShowsErrorAndRetriesAfterOneMinute() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.statusCode.set(503);
            fixture.body.set("unavailable");
            assertTrue(fixture.service.requestRefresh(WYNN_API));
            fixture.finishRequest();
            assertFalse(fixture.service.isLoading(WYNN_API));
            assertTrue(fixture.service.nodes(WYNN_API).isEmpty());
            assertEquals("Node load failed; retrying...", fixture.service.status(WYNN_API));
            fixture.now.set(GatheringNodeService.RETRY_INTERVAL_MS - 1);
            assertFalse(fixture.service.requestRefresh(WYNN_API));
            fixture.now.incrementAndGet();
            fixture.statusCode.set(200);
            fixture.body.set(apiFixture());
            assertTrue(fixture.service.requestRefresh(WYNN_API));
            fixture.finishRequest();
            assertEquals(4, fixture.service.nodes(WYNN_API).size());
            fixture.now.addAndGet(GatheringNodeService.RETRY_INTERVAL_MS);
            assertFalse(fixture.service.requestRefresh(WYNN_API), "Success restores the hourly TTL.");
        }
    }

    @Test
    void httpAndParseFailuresRetainLastSuccessfulSnapshotAndDoNotRetryEveryFrame() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.service.requestRefresh(WYNN_API);
            fixture.finishRequest();
            List<GatheringNode> first = fixture.service.nodes(WYNN_API);
            fixture.now.addAndGet(GatheringNodeService.REFRESH_INTERVAL_MS);
            for (String badBody : List.of("HTTP 429", "{}", "[]", "[{}]", "not JSON")) {
                fixture.statusCode.set(badBody.startsWith("HTTP") ? 429 : 200);
                fixture.body.set(badBody);
                assertTrue(fixture.service.requestRefresh(WYNN_API));
                fixture.finishRequest();
                assertFalse(fixture.service.isLoading(WYNN_API));
                assertSame(first, fixture.service.nodes(WYNN_API));
                assertEquals("Refresh failed; using cached nodes", fixture.service.status(WYNN_API));
                assertFalse(fixture.service.requestRefresh(WYNN_API));
                fixture.now.addAndGet(GatheringNodeService.RETRY_INTERVAL_MS);
            }
            fixture.body.set(apiFixture());
            fixture.service.requestRefresh(WYNN_API);
            fixture.finishRequest();
            assertEquals("Loaded 4 nodes", fixture.service.status(WYNN_API));
        }
    }

    @Test
    void networkAndRequestSetupFailuresReleaseInFlightState() throws Exception {
        var now = new AtomicLong();
        var work = new ArrayDeque<Runnable>();
        URI closedEndpoint;
        try (var fixture = new Fixture()) {
            closedEndpoint = fixture.endpoint();
        }
        for (URI endpoint : List.of(closedEndpoint, URI.create("file:///invalid-http-endpoint"))) {
            var service = new GatheringNodeService(HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1)).build(), endpoint, now::get, work::add);
            assertTrue(service.requestRefresh(WYNN_API));
            work.remove().run();
            assertFalse(service.isLoading(WYNN_API));
            assertTrue(service.nodes(WYNN_API).isEmpty());
            assertEquals("Node load failed; retrying...", service.status(WYNN_API));
            assertFalse(service.requestRefresh(WYNN_API));
            now.addAndGet(GatheringNodeService.RETRY_INTERVAL_MS);
            assertTrue(service.requestRefresh(WYNN_API));
            work.remove().run();
            assertFalse(service.isLoading(WYNN_API));
        }
    }

    @Test
    void executorRejectionDoesNotLeaveServicePermanentlyLoading() throws Exception {
        try (var fixture = new Fixture()) {
            var rejected = new AtomicInteger();
            var service = new GatheringNodeService(HttpClient.newHttpClient(), fixture.endpoint(),
                    fixture.now::get, task -> {
                        if (rejected.getAndIncrement() == 0) throw new RejectedExecutionException("test rejection");
                        fixture.work.add(task);
                    });
            assertFalse(service.requestRefresh(WYNN_API));
            assertFalse(service.isLoading(WYNN_API));
            assertFalse(service.requestRefresh(WYNN_API));
            fixture.now.set(GatheringNodeService.RETRY_INTERVAL_MS);
            assertTrue(service.requestRefresh(WYNN_API));
            fixture.finishRequest();
            assertFalse(service.isLoading(WYNN_API));
            assertEquals(4, service.nodes(WYNN_API).size());
        }
    }

    @Test
    void defaultStaticSourceLoadsRestoredBundleOnceWithoutAnyHttpRequests() throws Exception {
        try (var fixture = new Fixture()) {
            var source = WorldMapSettings.createGatheringNodeSourceSetting().getValue();
            assertEquals(STATIC, source);
            assertTrue(fixture.service.requestRefresh(source));
            assertFalse(fixture.service.requestRefresh(source));
            fixture.finishRequest();
            List<GatheringNode> bundled = fixture.service.nodes(source);
            assertTrue(bundled.size() > 10_000, "The original bundled dataset is present and readable.");
            assertTrue(bundled.stream().anyMatch(node -> node.x() == -1751 && node.resource().equals("COPPER")));
            assertFalse(fixture.service.isLoading(source));
            assertEquals(0, fixture.requests.get());
            assertTrue(fixture.service.nodes(WYNN_API).isEmpty());
            assertEquals("Not loaded", fixture.service.status(WYNN_API));
            fixture.now.set(GatheringNodeService.REFRESH_INTERVAL_MS * 10);
            assertFalse(fixture.service.requestRefresh(source), "Static data is loaded once per client session.");
            assertSame(bundled, fixture.service.nodes(source));
            assertThrows(UnsupportedOperationException.class, bundled::clear);
        }
    }

    @Test
    void loadingStaticWhileApiIsPendingDoesNotLetLateApiCompletionReplaceStaticCache() throws Exception {
        try (var fixture = new Fixture()) {
            assertTrue(fixture.service.requestRefresh(WYNN_API));
            Runnable apiRequest = fixture.work.remove();
            assertTrue(fixture.service.requestRefresh(STATIC));
            fixture.finishRequest();
            List<GatheringNode> bundled = fixture.service.nodes(STATIC);
            String staticStatus = fixture.service.status(STATIC);
            assertFalse(fixture.service.isLoading(STATIC));
            assertTrue(fixture.service.isLoading(WYNN_API));
            apiRequest.run();
            assertEquals(4, fixture.service.nodes(WYNN_API).size());
            assertSame(bundled, fixture.service.nodes(STATIC));
            assertEquals(staticStatus, fixture.service.status(STATIC));
            assertEquals(1, fixture.requests.get());
        }
    }

    @Test
    void missingStaticResourceFailsIndependentlyAndCanRetryWithoutUsingApi() throws Exception {
        try (var fixture = new Fixture()) {
            var missing = new java.util.concurrent.atomic.AtomicBoolean(true);
            byte[] bundled = ("{\"data\":" + apiFixture() + "}").getBytes(StandardCharsets.UTF_8);
            var service = new GatheringNodeService(HttpClient.newHttpClient(), fixture.endpoint(),
                    fixture.now::get, fixture.work::add,
                    () -> missing.get() ? null : new java.io.ByteArrayInputStream(bundled));
            assertTrue(service.requestRefresh(STATIC));
            fixture.finishRequest();
            assertFalse(service.isLoading(STATIC));
            assertEquals("Static node load failed; retrying...", service.status(STATIC));
            assertTrue(service.nodes(STATIC).isEmpty());
            assertEquals("Not loaded", service.status(WYNN_API));
            assertFalse(service.requestRefresh(STATIC));
            fixture.now.addAndGet(GatheringNodeService.RETRY_INTERVAL_MS);
            missing.set(false);
            assertTrue(service.requestRefresh(STATIC));
            fixture.finishRequest();
            assertEquals(4, service.nodes(STATIC).size());
            assertEquals(0, fixture.requests.get());
        }
    }

    @Test
    void staticLoadingCompletesWhileAnotherWorkerIsBlockedOnApi() throws Exception {
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        var firstCompleted = new java.util.concurrent.CountDownLatch(1);
        try (var fixture = new Fixture()) {
            fixture.releaseResponse = new java.util.concurrent.CountDownLatch(1);
            try {
                byte[] bundled = ("{\"data\":" + apiFixture() + "}").getBytes(StandardCharsets.UTF_8);
                var service = new GatheringNodeService(HttpClient.newHttpClient(), fixture.endpoint(), fixture.now::get,
                        task -> workers.execute(() -> { task.run(); firstCompleted.countDown(); }),
                        () -> new java.io.ByteArrayInputStream(bundled));
                assertTrue(service.requestRefresh(WYNN_API));
                assertTrue(fixture.responseStarted.await(3, java.util.concurrent.TimeUnit.SECONDS));
                assertTrue(service.requestRefresh(STATIC));
                assertTrue(firstCompleted.await(3, java.util.concurrent.TimeUnit.SECONDS));
                assertFalse(service.isLoading(STATIC));
                assertEquals(4, service.nodes(STATIC).size());
                assertTrue(service.isLoading(WYNN_API), "API is still waiting for its response.");
            } finally {
                fixture.releaseResponse.countDown();
            }
        } finally {
            workers.shutdownNow();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger statusCode = new AtomicInteger(200);
        final AtomicReference<String> body = new AtomicReference<>(apiFixture());
        final AtomicInteger requests = new AtomicInteger();
        final AtomicReference<String> method = new AtomicReference<>();
        final AtomicReference<String> accept = new AtomicReference<>();
        final AtomicReference<String> userAgent = new AtomicReference<>();
        final AtomicLong now = new AtomicLong();
        final ArrayDeque<Runnable> work = new ArrayDeque<>();
        final GatheringNodeService service;
        final java.util.concurrent.CountDownLatch responseStarted = new java.util.concurrent.CountDownLatch(1);
        volatile java.util.concurrent.CountDownLatch releaseResponse;

        Fixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/gathering-nodes", exchange -> {
                requests.incrementAndGet();
                method.set(exchange.getRequestMethod());
                accept.set(exchange.getRequestHeaders().getFirst("Accept"));
                userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
                responseStarted.countDown();
                if (releaseResponse != null) {
                    try {
                        releaseResponse.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            service = new GatheringNodeService(HttpClient.newHttpClient(), endpoint(), now::get, work::add);
        }

        URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/gathering-nodes");
        }

        void finishRequest() { work.remove().run(); }
        public void close() { server.stop(0); }
    }
}
