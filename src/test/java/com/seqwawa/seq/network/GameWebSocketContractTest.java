package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.seqwawa.seq.model.ChatItemPreview;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Runs the vendored backend wire fixtures through the production client boundaries. */
class GameWebSocketContractTest {
    private static final Gson FIXTURE_GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private static JsonObject fixture() throws Exception {
        try (var stream = GameWebSocketContractTest.class.getResourceAsStream(
                "/contracts/game-websocket-v1.json")) {
            assertNotNull(stream, "backend wire fixture is missing");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    @Test
    void guildChatPayloadsMatchTheBackendContract() throws Exception {
        JsonObject fixture = fixture();
        assertEquals(1, fixture.get("fixture_version").getAsInt());
        for (var element : fixture.getAsJsonObject("guild_chat").getAsJsonArray("cases")) {
            JsonObject example = element.getAsJsonObject();
            String id = example.get("id").getAsString();
            JsonObject payload = example.getAsJsonObject("payload");
            List<ChatItemPreview> previews = FIXTURE_GSON.fromJson(
                    payload.get("item_previews"), new TypeToken<List<ChatItemPreview>>() {}.getType());
            if (example.get("accepted").getAsBoolean()) {
                assertEquals(
                        example.getAsJsonObject("client_payload"),
                        ConnectionManager.buildGuildChatPayload(
                                string(payload, "username"), string(payload, "nickname"), string(payload, "message"),
                                string(payload, "avatar_url"), previews),
                        id);
            } else {
                var exception = assertThrows(IllegalArgumentException.class, () ->
                        ConnectionManager.buildGuildChatPayload(
                                string(payload, "username"), string(payload, "nickname"), string(payload, "message"),
                                string(payload, "avatar_url"), previews), id);
                assertEquals(example.getAsJsonObject("error").get("message").getAsString(), exception.getMessage(), id);
            }
        }
    }

    @Test
    void discordChatDispatchPreservesLegacyAndAdditiveFields() throws Exception {
        AtomicReference<ConnectionManager.DiscordChatMessage> dispatched = new AtomicReference<>();
        ConnectionManager.onDiscordChat(dispatched::set);
        try {
            for (var element : fixture().getAsJsonObject("discord_chat").getAsJsonArray("cases")) {
                JsonObject example = element.getAsJsonObject();
                String id = example.get("id").getAsString();
                dispatched.set(null);
                ConnectionManager.getInstance().onMessage(example.getAsJsonObject("payload").toString());
                assertNotNull(dispatched.get(), id);
                JsonObject expected = example.getAsJsonObject("expected");
                assertEquals(string(expected, "username"), dispatched.get().username(), id);
                assertEquals(string(expected, "message"), dispatched.get().message(), id);
                assertEquals(string(expected, "discord_id"), dispatched.get().discordId(), id);
            }
        } finally {
            ConnectionManager.onDiscordChat(null);
            ConnectionManager.resetForTest();
        }
    }

    private static String string(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : null;
    }

    @Test
    void invalidOrUnknownEnvelopesDoNotDispatchChatAndLeaveValidMessagesUsable() throws Exception {
        AtomicReference<ConnectionManager.DiscordChatMessage> dispatched = new AtomicReference<>();
        ConnectionManager.onDiscordChat(dispatched::set);
        try {
            JsonObject fixture = fixture();
            for (var element : fixture.getAsJsonObject("rejected_envelopes").getAsJsonArray("cases")) {
                JsonObject example = element.getAsJsonObject();
                ConnectionManager.getInstance().onMessage(example.getAsJsonObject("payload").toString());
                assertNull(dispatched.get(), example.get("id").getAsString());
            }
            JsonObject valid = fixture.getAsJsonObject("discord_chat").getAsJsonArray("cases")
                    .get(0).getAsJsonObject().getAsJsonObject("payload");
            ConnectionManager.getInstance().onMessage(valid.toString());
            assertNotNull(dispatched.get());
            assertEquals("hello", dispatched.get().message());
        } finally {
            ConnectionManager.onDiscordChat(null);
            ConnectionManager.resetForTest();
        }
    }
}
