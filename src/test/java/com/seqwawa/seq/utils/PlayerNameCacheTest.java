package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerNameCacheTest {

    @Test
    void restoresCanonicalUuidFromWynncraftDisplayUuid() {
        UUID canonical = UUID.fromString("12345678-1234-4abc-8def-123456789abc");
        UUID display = UUID.fromString("12345678-1234-0abc-8def-123456789abc");

        assertEquals(canonical, PlayerNameCache.canonicalPlayerUuid(display));
        assertEquals(canonical, PlayerNameCache.canonicalPlayerUuid(canonical));
        assertNull(PlayerNameCache.canonicalPlayerUuid(null));
    }
}
