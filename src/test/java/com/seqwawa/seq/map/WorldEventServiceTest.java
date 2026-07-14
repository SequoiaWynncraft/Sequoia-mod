package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class WorldEventServiceTest {

    @Test
    void parsesNullableMetadataSchedulesAndMultipleLocations() {
        List<WorldEventDefinition> events = WorldEventService.parseEvents("""
                [
                  {
                    "name": "Zulu Event",
                    "internalName": "zulu",
                    "lore": null,
                    "difficulty": "HARD",
                    "level": 80,
                    "length": "MEDIUM",
                    "location": [
                      {"event": {"x": 10, "y": 64, "z": -20}, "radius": 12, "spawnRadius": 5},
                      {"event": {"x": 30, "y": 70, "z": -40}, "radius": 8, "spawnRadius": 4}
                    ],
                    "schedule": "2026-07-14T08:20:00+00:00"
                  },
                  {
                    "name": "Alpha Event",
                    "internalName": "alpha",
                    "location": [{"event": {"x": 1, "z": 2}}],
                    "schedule": null
                  }
                ]
                """);

        assertEquals(List.of("Alpha Event", "Zulu Event"), events.stream().map(WorldEventDefinition::name).toList());
        WorldEventDefinition alpha = events.getFirst();
        assertNull(alpha.schedule());
        assertNull(alpha.level());
        assertEquals(0, alpha.locations().getFirst().y());

        WorldEventDefinition zulu = events.getLast();
        assertEquals(Instant.parse("2026-07-14T08:20:00Z"), zulu.schedule());
        assertEquals(2, zulu.locations().size());
        assertEquals(12, zulu.locations().getFirst().radius());
    }

    @Test
    void skipsMalformedRecordsAndLocationsWithoutLosingValidSpots() {
        List<WorldEventDefinition> events = WorldEventService.parseEvents("""
                [
                  {"name": "", "internalName": "blank", "location": []},
                  {"name": "No Location", "internalName": "none", "location": [{"spawn": {"x": 1, "z": 2}}]},
                  {
                    "name": "Valid",
                    "internalName": "valid",
                    "location": [
                      {"event": {"x": "bad", "z": 2}},
                      {"event": {"x": 4, "y": 5, "z": 6}}
                    ],
                    "schedule": "not-a-time"
                  }
                ]
                """);

        assertEquals(1, events.size());
        assertEquals("Valid", events.getFirst().name());
        assertEquals(1, events.getFirst().locations().size());
        assertNull(events.getFirst().schedule());
    }

    @Test
    void rejectsInvalidRootsAndResponsesWithoutValidEvents() {
        assertThrows(IllegalArgumentException.class, () -> WorldEventService.parseEvents("{}"));
        assertThrows(IllegalArgumentException.class, () -> WorldEventService.parseEvents("[]"));
        assertThrows(IllegalArgumentException.class, () -> WorldEventService.parseEvents("""
                [{"name": "Broken", "internalName": "broken", "location": []}]
                """));
    }

    @Test
    void throttlesRequestsAndRetainsLastSnapshotAfterFailure() throws Exception {
        AtomicInteger statusCode = new AtomicInteger(200);
        AtomicReference<String> responseBody = new AtomicReference<>("""
                [{
                  "name": "Active",
                  "internalName": "active",
                  "location": [{"event": {"x": 1, "y": 2, "z": 3}}],
                  "schedule": "2026-07-14T08:20:00Z"
                }]
                """);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/world-events", exchange -> respond(exchange, statusCode.get(), responseBody.get()));
            server.start();
            AtomicLong now = new AtomicLong(1_000_000);
            WorldEventService service = new WorldEventService(
                    HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/world-events"),
                    now::get);

            assertTrue(service.requestRefresh());
            await(() -> service.snapshot().version() == 1);
            assertEquals("Active", service.snapshot().events().getFirst().name());
            assertFalse(service.requestRefresh());

            statusCode.set(503);
            responseBody.set("unavailable");
            now.addAndGet(WorldEventService.REFRESH_INTERVAL_MS);
            assertTrue(service.requestRefresh());
            await(() -> service.status().equals("Refresh failed"));

            assertEquals(1, service.snapshot().version());
            assertEquals("Active", service.snapshot().events().getFirst().name());
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous refresh.");
    }
}
