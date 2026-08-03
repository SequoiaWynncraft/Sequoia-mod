package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParseException;
import com.seqwawa.seq.model.BombShareType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncomingMessageParserTest {
    @Test
    void parsesRepresentativeBombSharePromptFixture() {
        IncomingMessageParser.IncomingMessage incoming =
                IncomingMessageParser.parse(protocolFixture("bomb-share-prompt.json"));

        assertEquals("bomb_share_prompt", incoming.type());
        assertEquals(
                new ConnectionManager.BombSharePromptMessage(
                        "04677645-a0d0-4b5f-bd5d-590b3f7f2f5d",
                        "CinfrasCitizen",
                        "loot-and-combat",
                        List.of(BombShareType.LOOT, BombShareType.COMBAT_XP),
                        Instant.parse("2026-08-03T12:45:30Z"),
                        true),
                IncomingMessageParser.bombSharePrompt(incoming.payload()));
    }

    @Test
    void parsesTreasurySuccessFixtureWithoutChangingTypedFields() {
        IncomingMessageParser.IncomingMessage incoming =
                IncomingMessageParser.parse(protocolFixture("treasury-out-recorded.json"));

        assertEquals("treasury_out_recorded", incoming.type());
        assertEquals(
                new TreasuryOutRecordedMessage(
                        "treasury_out_recorded",
                        "550e8400-e29b-41d4-a716-446655440000",
                        "Season 32",
                        7,
                        "2STX",
                        "Solo",
                        "season payout",
                        "2026-08-01"),
                IncomingMessageParser.treasuryOutRecorded(incoming.payload()));
    }

    @Test
    void parsesBackendErrorFallbackFieldsAndMetadata() {
        IncomingMessageParser.IncomingMessage incoming =
                IncomingMessageParser.parse(protocolFixture("version-error.json"));

        assertEquals(
                new IncomingMessageParser.BackendError(
                        426,
                        "mod_version_unsupported",
                        "Update Sequoia before using raid relays.",
                        null,
                        "0.2.0",
                        "guild_raid_announcement",
                        "raid completion",
                        "guild_raid_announcement"),
                IncomingMessageParser.backendError(incoming.payload()));
    }

    @Test
    void errorParserPreservesStatusAndMessageFallbackOrder() {
        IncomingMessageParser.IncomingMessage incoming = IncomingMessageParser.parse(
                "{\"type\":\"error\",\"code\":401,\"message\":\"primary\","
                        + "\"error\":\"secondary\",\"detail\":\"tertiary\"}");

        IncomingMessageParser.BackendError error = IncomingMessageParser.backendError(incoming.payload());

        assertEquals(401, error.status());
        assertEquals("401", error.code());
        assertEquals("primary", error.message());
    }

    @Test
    void absentOrBlankErrorTextUsesExistingFallback() {
        IncomingMessageParser.IncomingMessage incoming =
                IncomingMessageParser.parse("{\"type\":\"error\",\"message\":\"   \"}");

        assertEquals("Unknown backend error", IncomingMessageParser.backendError(incoming.payload()).message());
    }

    @Test
    void authenticatedDiscordUsernamePreservesExistingBlankPrimitiveBehavior() {
        IncomingMessageParser.IncomingMessage blank =
                IncomingMessageParser.parse("{\"type\":\"authenticated\",\"discord_username\":\"\"}");
        IncomingMessageParser.IncomingMessage absent =
                IncomingMessageParser.parse("{\"type\":\"authenticated\"}");

        assertEquals("", IncomingMessageParser.authenticatedDiscordUsername(blank.payload()));
        assertNull(IncomingMessageParser.authenticatedDiscordUsername(absent.payload()));
    }

    @Test
    void missingTypeIsARejectedEnvelope() {
        assertNull(IncomingMessageParser.parse("{}"));
        assertNull(IncomingMessageParser.parse(null));
    }

    @Test
    void malformedJsonAndNonObjectPayloadsStillFailParsing() {
        assertThrows(JsonParseException.class, () -> IncomingMessageParser.parse("{"));
        assertThrows(JsonParseException.class, () -> IncomingMessageParser.parse("[]"));
        assertThrows(JsonParseException.class, () -> IncomingMessageParser.parse("null"));
        assertThrows(UnsupportedOperationException.class, () -> IncomingMessageParser.parse("{\"type\":{}}"));
    }

    private static String protocolFixture(String name) {
        String path = "/protocol/inbound/" + name;
        try (InputStream stream = IncomingMessageParserTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("Missing protocol fixture " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Failed to read protocol fixture " + path, exception);
        }
    }
}
