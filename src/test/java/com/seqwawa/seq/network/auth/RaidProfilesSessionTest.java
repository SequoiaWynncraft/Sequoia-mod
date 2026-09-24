package com.seqwawa.seq.network.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RaidProfilesSessionTest {

    @Test
    void theLinkOpensTheWebsiteSignInOfTheRaidProfilesBackend() {
        // The return address must be on that backend's own host, or it rejects the
        // sign-in as an invalid return URL.
        assertEquals(
                "https://staging.seqwawa.com/auth/web/start?return_to=https://staging.seqwawa.com/",
                RaidProfilesSession.linkUrlFor("https://staging.seqwawa.com/api"));
    }

    @Test
    void theLinkIsTheSameWithOrWithoutTheApiSuffix() {
        assertEquals(
                RaidProfilesSession.linkUrlFor("https://staging.seqwawa.com/api"),
                RaidProfilesSession.linkUrlFor("https://staging.seqwawa.com"));
    }

    @Test
    void theHostIsWhatThePlayerIsToldToLinkOn() {
        assertEquals("staging.seqwawa.com", RaidProfilesSession.hostOf("https://staging.seqwawa.com/api"));
        assertEquals("api.seqwawa.com", RaidProfilesSession.hostOf("https://api.seqwawa.com/api"));
    }
}
