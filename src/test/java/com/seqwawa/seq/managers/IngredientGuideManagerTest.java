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
                      },
                      "identifications": {
                        "rawHealth": {"min": -6, "raw": -6, "max": -4},
                        "rawMainAttackDamage": {"min": 6, "raw": 6, "max": 8}
                      },
                      "consumableOnlyIDs": {"duration": -54, "charges": 1},
                      "ingredientPositionModifiers": {
                        "left": -120,
                        "right": 0,
                        "above": 0,
                        "under": 0,
                        "touching": 20,
                        "notTouching": 0
                      },
                      "itemOnlyIDs": {
                        "durabilityModifier": -73000,
                        "strengthRequirement": 8,
                        "dexterityRequirement": 0
                      }
                    }
                  ]
                }
                """;

        IngredientGuideEntry ingredient = IngredientGuideManager.parseIngredients(body).getFirst();

        assertEquals("Dead Bee", ingredient.internalName());
        assertEquals("skin", ingredient.icon().format());
        assertEquals(63, ingredient.icon().textureHash().length());
        assertEquals(2, ingredient.effects().size());
        assertEquals("rawHealth", ingredient.effects().getFirst().apiName());
        assertEquals(-6, ingredient.effects().getFirst().min());
        assertEquals(-4, ingredient.effects().getFirst().max());
        assertEquals(-54, ingredient.craftingModifiers().duration());
        assertEquals(1, ingredient.craftingModifiers().charges());
        assertEquals(-73, ingredient.craftingModifiers().durability());
        assertEquals("strengthRequirement", ingredient.craftingModifiers().requirements().getFirst().apiName());
        assertEquals(8, ingredient.craftingModifiers().requirements().getFirst().value());
        assertEquals(2, ingredient.craftingModifiers().positions().size());
    }

    @Test
    void filtersByIngredientMobAndProfessionTerms() {
        IngredientGuideEntry first = entry(
                "Acidic Solution", List.of("weaponsmithing"), List.of("Olux Plague Doctor"));
        IngredientGuideEntry second =
                entry("Sturdy Flesh", List.of("armouring"), List.of("Earth Zombie"));
        List<IngredientGuideEntry> ingredients = List.of(first, second);

        assertEquals(List.of(first), IngredientGuideManager.filter(
                ingredients, "acidic", IngredientGuideManager.SearchScope.INGREDIENT));
        assertEquals(List.of(first), IngredientGuideManager.filter(
                ingredients, "plague doctor", IngredientGuideManager.SearchScope.MOB));
        assertEquals(List.of(second), IngredientGuideManager.filter(
                ingredients, "armouring", IngredientGuideManager.SearchScope.PROFESSION));
        assertEquals(ingredients, IngredientGuideManager.filter(
                ingredients, " ", IngredientGuideManager.SearchScope.INGREDIENT));
    }

    @Test
    void searchScopeSeparatesMatchingIngredientAndMobNames() {
        IngredientGuideEntry ingredientMatch =
                entry("Colossus", List.of("armouring"), List.of("Stone Guardian"));
        IngredientGuideEntry mobMatch =
                entry("Ancient Hide", List.of("tailoring"), List.of("Colossus"));
        List<IngredientGuideEntry> ingredients = List.of(ingredientMatch, mobMatch);

        assertEquals(List.of(ingredientMatch), IngredientGuideManager.filter(
                ingredients, "colossus", IngredientGuideManager.SearchScope.INGREDIENT));
        assertEquals(List.of(mobMatch), IngredientGuideManager.filter(
                ingredients, "colossus", IngredientGuideManager.SearchScope.MOB));
    }

    @Test
    void defaultsToLevelThenRarityOrdering() {
        String body = """
                [
                  {"displayName": "Higher Level", "type": "ingredient", "tier": "TIER_0",
                   "requirements": {"level": 20}},
                  {"displayName": "Rare Same Level", "type": "ingredient", "tier": "TIER_3",
                   "requirements": {"level": 10}},
                  {"displayName": "Common Same Level", "type": "ingredient", "tier": "TIER_1",
                   "requirements": {"level": 10}}
                ]
                """;

        List<IngredientGuideEntry> parsed = IngredientGuideManager.parseIngredients(body);

        assertEquals(
                List.of("Common Same Level", "Rare Same Level", "Higher Level"),
                parsed.stream().map(IngredientGuideEntry::displayName).toList());
    }

    @Test
    void sortsWithIndependentPrimaryAndSecondaryDirections() {
        IngredientGuideEntry commonTen = entry("Common Ten", 10, 1);
        IngredientGuideEntry rareTen = entry("Rare Ten", 10, 3);
        IngredientGuideEntry commonTwenty = entry("Common Twenty", 20, 1);

        List<IngredientGuideEntry> ascendingSecondary = IngredientGuideManager.sort(
                List.of(commonTen, rareTen, commonTwenty),
                IngredientGuideManager.SortKey.LEVEL,
                IngredientGuideManager.SortDirection.DESCENDING,
                IngredientGuideManager.SortKey.RARITY,
                IngredientGuideManager.SortDirection.ASCENDING);
        List<IngredientGuideEntry> descendingSecondary = IngredientGuideManager.sort(
                List.of(commonTen, rareTen, commonTwenty),
                IngredientGuideManager.SortKey.LEVEL,
                IngredientGuideManager.SortDirection.DESCENDING,
                IngredientGuideManager.SortKey.RARITY,
                IngredientGuideManager.SortDirection.DESCENDING);

        assertEquals(List.of(commonTwenty, commonTen, rareTen), ascendingSecondary);
        assertEquals(List.of(commonTwenty, rareTen, commonTen), descendingSecondary);
    }

    @Test
    void alphabeticalSortingIgnoresTheSecondarySort() {
        IngredientGuideEntry first = new IngredientGuideEntry(
                "Duplicate",
                "A Duplicate",
                1,
                1,
                List.of(),
                IngredientGuideEntry.Icon.unavailable(),
                List.of());
        IngredientGuideEntry second = new IngredientGuideEntry(
                "Duplicate",
                "B Duplicate",
                3,
                20,
                List.of(),
                IngredientGuideEntry.Icon.unavailable(),
                List.of());

        List<IngredientGuideEntry> sorted = IngredientGuideManager.sort(
                List.of(second, first),
                IngredientGuideManager.SortKey.ALPHABETICAL,
                IngredientGuideManager.SortDirection.ASCENDING,
                IngredientGuideManager.SortKey.LEVEL,
                IngredientGuideManager.SortDirection.DESCENDING);

        assertEquals(List.of(first, second), sorted);
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

    private static IngredientGuideEntry entry(String name, int level, int tier) {
        return new IngredientGuideEntry(
                name,
                name,
                tier,
                level,
                List.of(),
                IngredientGuideEntry.Icon.unavailable(),
                List.of());
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
