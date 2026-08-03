package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NicknameResolverCacheTest {
    @Test
    void trimsValidUsernamesAndPreservesTheirCase() {
        NicknameResolverCache.remember("  Shared Policy Nick  ", "  Mixed_Case  ");

        assertEquals("Mixed_Case", NicknameResolverCache.resolveUsername("shared policy nick"));
    }

    @Test
    void rejectsInvalidUsernames() {
        NicknameResolverCache.remember("Invalid Policy Nick", "Player-Name");

        assertNull(NicknameResolverCache.resolveUsername("invalid policy nick"));
    }
}
