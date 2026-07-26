package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.IngredientGuideEntry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class IngredientGuideManagerTest {

    @Test
    void parsesAndMergesMobSpawnLocations() {
        String body = """
                [
                  {
                    "displayName": "Sturdy Flesh",
                    "internalName": "Sturdy Flesh",
                    "type": "ingredient",
                    "tier": "TIER_3",
                    "requirements": {
                      "level": 1,
                      "skills": ["armouring", "tailoring"]
                    },
                    "icon": {
                      "value": {
                        "id": "minecraft:potion",
                        "customModelData": {"rangeDispatch": [2500]}
                      },
                      "format": "attribute"
                    },
                    "droppedBy": [
                      {"name": "Zombie", "coords": [-739, 37, -1984, 4]},
                      {"name": "Zombie", "coords": [[82, 66, -1818, 7], [-739, 37, -1984, 4]]},
                      {"name": "Earth Zombie", "coords": null},
                      {"name": "ERROR", "coords": [1, 2, 3, 4]}
                    ]
                  }
                ]
                """;

        List<IngredientGuideEntry> parsed = IngredientGuideManager.parseIngredients(body);

        assertEquals(1, parsed.size());
        IngredientGuideEntry ingredient = parsed.getFirst();
        assertEquals(3, ingredient.tier());
        assertEquals(1, ingredient.level());
        assertEquals(List.of("armouring", "tailoring"), ingredient.skills());
        assertEquals("attribute", ingredient.icon().format());
        assertEquals(2500, ingredient.icon().modelData());
        assertEquals(2, ingredient.dropSources().size());
        assertEquals("Earth Zombie", ingredient.dropSources().getFirst().name());
        assertEquals(List.of(), ingredient.dropSources().getFirst().locations());
        assertEquals("Zombie", ingredient.dropSources().get(1).name());
        assertEquals(2, ingredient.dropSources().get(1).locations().size());
        assertEquals("-739, 37, -1984", ingredient.dropSources().get(1).locations().getFirst().coordinates());
    }

    @Test
    void parsesSkinIconAndResultsEnvelope() {
        String body = """
                {
                  "results": [
                    {
                      "displayName": "Dead Bee",
                      "type": "ingredient",
                      "tier": "TIER_2",
                      "icon": {
                        "value": "947322f831e3c168cfbd3e28fe925144b261e79eb39c771349fac55a8126473",
                        "format": "skin"
                      }
                    }
                  ]
                }
                """;

        IngredientGuideEntry ingredient = IngredientGuideManager.parseIngredients(body).getFirst();

        assertEquals("Dead Bee", ingredient.internalName());
        assertEquals("skin", ingredient.icon().format());
        assertEquals(63, ingredient.icon().textureHash().length());
    }

    @Test
    void filtersByIngredientMobAndProfessionTerms() {
        IngredientGuideEntry first = entry(
                "Acidic Solution", List.of("weaponsmithing"), List.of("Olux Plague Doctor"));
        IngredientGuideEntry second =
                entry("Sturdy Flesh", List.of("armouring"), List.of("Earth Zombie"));
        List<IngredientGuideEntry> ingredients = List.of(first, second);

        assertEquals(List.of(first), IngredientGuideManager.filter(ingredients, "acidic"));
        assertEquals(List.of(first), IngredientGuideManager.filter(ingredients, "plague doctor"));
        assertEquals(List.of(second), IngredientGuideManager.filter(ingredients, "armouring zombie"));
        assertEquals(ingredients, IngredientGuideManager.filter(ingredients, " "));
    }

    @Test
    void postsIngredientSearchAndRetainsSnapshotAfterFailure() throws Exception {
        AtomicInteger statusCode = new AtomicInteger(200);
        AtomicReference<String> responseBody = new AtomicReference<>("""
                [{
                  "displayName": "Coastal Sand",
                  "type": "ingredient",
                  "tier": "TIER_0",
                  "requirements": {"level": 1, "skills": ["scribing"]},
                  "droppedBy": [{"name": "Katoa Crab", "coords": [-737, 35, -2015, 7]}]
                }]
                """);
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/ingredients", exchange -> {
                method.set(exchange.getRequestMethod());
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, statusCode.get(), responseBody.get());
            });
            server.start();
            AtomicLong now = new AtomicLong(1_000_000);
            IngredientGuideManager manager = new IngredientGuideManager(
                    HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ingredients"),
                    now::get);

            assertTrue(manager.requestRefresh());
            await(() -> manager.snapshot().version() == 1);
            assertEquals("POST", method.get());
            assertEquals("{\"type\":\"ingredient\"}", requestBody.get());
            assertEquals("Coastal Sand", manager.snapshot().ingredients().getFirst().displayName());
            assertFalse(manager.requestRefresh());

            statusCode.set(503);
            responseBody.set("unavailable");
            now.addAndGet(IngredientGuideManager.REFRESH_INTERVAL_MS);
            assertTrue(manager.requestRefresh());
            await(() -> manager.status().equals("Refresh failed"));

            assertEquals(1, manager.snapshot().version());
            assertEquals("Coastal Sand", manager.snapshot().ingredients().getFirst().displayName());
        } finally {
            server.stop(0);
        }
    }

    private static IngredientGuideEntry entry(String name, List<String> skills, List<String> mobs) {
        return new IngredientGuideEntry(
                name,
                name,
                1,
                1,
                skills,
                IngredientGuideEntry.Icon.unavailable(),
                mobs.stream()
                        .map(mob -> new IngredientGuideEntry.DropSource(mob, List.of()))
                        .toList());
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous refresh.");
    }
}
