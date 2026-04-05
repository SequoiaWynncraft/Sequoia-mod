package org.sequoia.seq.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.sequoia.seq.model.GuildWarSubmission;

class ConnectionManagerTest {

    @Test
    void websocketHandshakeUsesBearerTokenHeader() {
        Map<String, String> headers = ConnectionManager.buildHandshakeHeaders("abc123", "0.1.3");

        assertEquals(2, headers.size());
        assertEquals("Bearer abc123", headers.get("Authorization"));
        assertEquals("0.1.3", headers.get(ClientVersion.MOD_VERSION_HEADER));
    }

    @Test
    void blankTokenProducesNoHandshakeHeaders() {
        Map<String, String> headers = ConnectionManager.buildHandshakeHeaders("   ", "0.1.3");

        assertEquals(1, headers.size());
        assertEquals("0.1.3", headers.get(ClientVersion.MOD_VERSION_HEADER));
    }

    @Test
    void linkRequestPayloadIncludesRelinkFlag() {
        var payload = ConnectionManager.buildLinkRequestPayload(true);

        assertTrue(payload.has("allow_relink"));
        assertTrue(payload.get("allow_relink").getAsBoolean());
    }

    @Test
    void guildWarSubmissionPayloadUsesExpectedNestedShape() {
        GuildWarSubmission submission = new GuildWarSubmission(
                "Detlas Suburbs",
                "550e8400-e29b-41d4-a716-446655440000",
                "2026-03-28T01:00:00Z",
                "2026-03-28T00:55:00Z",
                java.util.List.of("Alpha", "Bravo"),
                new GuildWarSubmission.TowerStats(1200, 1800, 2.5, 450000, 0.35),
                410,
                "2026-03-28T00:58:00Z");

        var payload = ConnectionManager.buildGuildWarSubmissionPayload(submission);

        assertEquals("Detlas Suburbs", payload.get("territory").getAsString());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", payload.get("submitted_by").getAsString());
        assertEquals("2026-03-28T01:00:00Z", payload.get("submitted_at").getAsString());
        assertEquals("2026-03-28T00:55:00Z", payload.get("start_time").getAsString());
        assertEquals("Alpha", payload.getAsJsonArray("warrers").get(0).getAsString());
        assertEquals(1200L, payload.getAsJsonObject("results")
                .getAsJsonObject("stats")
                .getAsJsonObject("damage")
                .get("low")
                .getAsLong());
        assertEquals(0.35, payload.getAsJsonObject("results").getAsJsonObject("stats").get("defence").getAsDouble());
        assertEquals(410, payload.get("sr").getAsInt());
        assertEquals("2026-03-28T00:58:00Z", payload.get("completed_at").getAsString());
    }

    @Test
    void discordUsernamePresenceTreatsBlankValuesAsUnlinked() {
        assertTrue(ConnectionManager.hasDiscordUsername("SequoiaUser"));
        assertEquals(false, ConnectionManager.hasDiscordUsername(""));
        assertEquals(false, ConnectionManager.hasDiscordUsername("   "));
    }

    @Test
    void localCleanCloseDoesNotReconnect() {
        assertFalse(ConnectionManager.shouldReconnectAfterClose(1000, false));
    }

    @Test
    void remoteCloseStillReconnects() {
        assertTrue(ConnectionManager.shouldReconnectAfterClose(1000, true));
    }

    @Test
    void resetForTestClearsReconnectState() {
        ConnectionManager.resetForTest();

        assertFalse(ConnectionManager.hasReconnectTask());
        assertFalse(ConnectionManager.isConnected());
    }

    @Test
    void automaticConnectStaysSuppressedAfterManualDisconnect() {
        assertFalse(ConnectionManager.shouldAttemptAutomaticConnect(false, false, false, false, true));
    }

    @Test
    void automaticConnectSkipsWhileReconnectAlreadyScheduled() {
        assertFalse(ConnectionManager.shouldAttemptAutomaticConnect(false, false, false, true, false));
    }

    @Test
    void automaticConnectRunsOnlyWhenSocketIsIdle() {
        assertTrue(ConnectionManager.shouldAttemptAutomaticConnect(false, false, false, false, false));
        assertFalse(ConnectionManager.shouldAttemptAutomaticConnect(true, false, false, false, false));
        assertFalse(ConnectionManager.shouldAttemptAutomaticConnect(false, true, false, false, false));
        assertFalse(ConnectionManager.shouldAttemptAutomaticConnect(false, false, true, false, false));
    }
}
