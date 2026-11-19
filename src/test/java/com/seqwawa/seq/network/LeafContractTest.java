package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafEvents;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.ui.LeafContractViews;
import com.seqwawa.seq.ui.unlock.UnlockPopup;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * The mod against what Leaf really sends: {@code contracts/leaf-api-v1.json} is Leaf's own
 * record of its routes' answers, copied from sequoia-leaf (see docs/leaf-api-contract.md).
 * Hand-written answers only test what the mod's authors assumed; these test what is so.
 */
class LeafContractTest {

    private static final Gson GSON = ApiClient.newGson();
    private static final JsonObject CONTRACT = load();

    private static JsonObject load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                LeafContractTest.class.getResourceAsStream("/contracts/leaf-api-v1.json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonObject exchange(String name) {
        return Objects.requireNonNull(CONTRACT.getAsJsonObject(name), name);
    }

    private static JsonElement answer(String name) {
        return exchange(name).get("body");
    }

    @Test
    void theRecentListStartsWithTheEventLeafWroteLast() {
        JsonElement body = answer("GET /v1/me/events");
        List<LeafEvents.Event> sent = StreamSupport.stream(
                        body.getAsJsonObject().getAsJsonArray("events").spliterator(), false)
                .map(event -> GSON.fromJson(event, LeafEvents.Event.class))
                .toList();
        LeafEvents.Event newestShown = sent.stream()
                .filter(event -> LeafContractViews.recentRow(event) != null)
                .max(Comparator.comparingLong(LeafEvents.Event::id))
                .orElseThrow();

        Object first = LeafContractViews.firstRecentRow(GSON.fromJson(body, LeafEvents.class));

        assertEquals(LeafContractViews.recentRow(newestShown), first);
        assertTrue(sent.getFirst().id() < sent.getLast().id(), "Leaf sends them oldest first, as its README says");
    }

    @Test
    void everyKindOfEventLeafSendsIsShownOrLeftOutOnPurpose() {
        // A reroll changes no Leaf the list shows; the board refreshes for it instead.
        Set<String> leftOut = Set.of("quest_rerolled");
        LeafEvents events = GSON.fromJson(answer("GET /v1/me/events"), LeafEvents.class);

        for (LeafEvents.Event event : events.newestFirst()) {
            boolean shown = LeafContractViews.recentRow(event) != null;
            boolean popup = UnlockPopup.fromEvent(event.type(), event.data()) != null;
            if (leftOut.contains(event.type())) {
                assertTrue(!shown && !popup, event.type() + " is left out on purpose");
            } else {
                assertTrue(shown && popup, event.type() + " has a row in Recent and a popup");
            }
        }
    }

    @Test
    void readsTheAccountQuestsAndRerollAnswersLeafSends() {
        LeafAccount account = GSON.fromJson(answer("GET /v1/me"), LeafAccount.class);
        JsonObject me = answer("GET /v1/me").getAsJsonObject();
        assertEquals(me.get("leaf").getAsLong(), account.leaf());
        assertEquals(me.getAsJsonObject("level").get("number").getAsInt(), account.levelOrFirst().number());

        LeafQuests quests = GSON.fromJson(answer("GET /v1/me/quests"), LeafQuests.class);
        for (LeafQuests.Quest quest : quests.quests()) {
            assertNotNull(quest.id());
            assertEquals(quest.rerollLeaf() != null, quest.rerollable(), quest.id());
        }
        assertNull(quests.quest(LeafQuests.TIMED).rerollLeaf(), "the guild's timed quest has no reroll");

        LeafQuests.Rerolled rerolled = GSON.fromJson(answer("POST /v1/me/quests/{id}/reroll"), LeafQuests.Rerolled.class);
        assertEquals(account.leaf(), rerolled.leaf() - 0, "the free reroll left the balance as /v1/me shows it");
        assertEquals(quests.quest(rerolled.quest().slot()).id(), rerolled.quest().id());
    }

    @Test
    void asksForARerollTheWayLeafTakesIt() {
        JsonObject request = exchange("POST /v1/me/quests/{id}/reroll").getAsJsonObject("request");
        String path = request.get("path").getAsString();
        String id = path.substring("/v1/me/quests/".length(), path.length() - "/reroll".length());
        LeafQuests.Quest shown = new LeafQuests.Quest(id, id.substring(0, id.indexOf(':')), null, null, 0, 1, null, null, null,
                null, null, request.getAsJsonObject("body").get("leaf").getAsLong());

        assertEquals(path, URLDecoder.decode(ApiClient.rerollPath(shown), StandardCharsets.UTF_8));
        assertEquals(request.get("body"), ApiClient.rerollBody(shown));
    }

    @Test
    void readsLeafsRefusalCodes() {
        assertEquals("cost_changed", ApiClient.leafErrorCode(refusal("POST /v1/me/quests/{id}/reroll, the cost changed")));
        assertEquals("not_current", ApiClient.leafErrorCode(refusal("POST /v1/me/quests/{id}/reroll, the same quest again")));
    }

    private static ApiClient.ApiException refusal(String name) {
        JsonObject exchange = exchange(name);
        return new ApiClient.ApiException(exchange.get("status").getAsInt(), exchange.get("body").toString());
    }
}
