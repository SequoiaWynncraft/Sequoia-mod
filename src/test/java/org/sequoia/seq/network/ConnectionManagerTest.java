package org.sequoia.seq.network;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionManagerTest {

    @Test
    void websocketHandshakeUsesBearerTokenHeader() {
        Map<String, String> headers = ConnectionManager.buildHandshakeHeaders("abc123");

        assertEquals(1, headers.size());
        assertEquals("Bearer abc123", headers.get("Authorization"));
    }

    @Test
    void blankTokenProducesNoHandshakeHeaders() {
        Map<String, String> headers = ConnectionManager.buildHandshakeHeaders("   ");

        assertTrue(headers.isEmpty());
    }

    @Test
    void linkRequestPayloadIncludesRelinkFlag() {
        var payload = ConnectionManager.buildLinkRequestPayload(true);

        assertTrue(payload.has("allow_relink"));
        assertTrue(payload.get("allow_relink").getAsBoolean());
    }
}
