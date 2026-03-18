package org.sequoia.seq.network;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionManagerTest {

    @Test
    void websocketHandshakeUsesBearerTokenHeader() {
        Map<String, String> headers = ConnectionManager.buildHandshakeHeaders("abc123", "v0.1.3");

        assertEquals(2, headers.size());
        assertEquals("Bearer abc123", headers.get("Authorization"));
        assertEquals("v0.1.3", headers.get(ClientVersion.MOD_VERSION_HEADER));
    }

    @Test
    void blankTokenProducesNoHandshakeHeaders() {
        Map<String, String> headers = ConnectionManager.buildHandshakeHeaders("   ", "v0.1.3");

        assertEquals(1, headers.size());
        assertEquals("v0.1.3", headers.get(ClientVersion.MOD_VERSION_HEADER));
    }

    @Test
    void linkRequestPayloadIncludesRelinkFlag() {
        var payload = ConnectionManager.buildLinkRequestPayload(true);

        assertTrue(payload.has("allow_relink"));
        assertTrue(payload.get("allow_relink").getAsBoolean());
    }
}
