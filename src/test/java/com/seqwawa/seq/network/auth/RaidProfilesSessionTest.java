package com.seqwawa.seq.network.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CompletionException;

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
    void anEndedSessionsSignInRefusesForGoodWithoutSigningIn() {
        // An insecure address fails a sign-in at once, so one that started would fail differently.
        RaidProfilesSession signIn = new RaidProfilesSession("http://localhost/api");

        signIn.clear();

        for (boolean force : new boolean[] {false, true}) {
            var failure = assertThrows(CompletionException.class, () -> signIn.ensureToken(force).join());
            assertInstanceOf(NotSignedInException.class, failure.getCause(), "force=" + force);
        }
    }

    @Test
    void theHostIsWhatThePlayerIsToldToLinkOn() {
        assertEquals("staging.seqwawa.com", RaidProfilesSession.hostOf("https://staging.seqwawa.com/api"));
        assertEquals("api.seqwawa.com", RaidProfilesSession.hostOf("https://api.seqwawa.com/api"));
    }
}
