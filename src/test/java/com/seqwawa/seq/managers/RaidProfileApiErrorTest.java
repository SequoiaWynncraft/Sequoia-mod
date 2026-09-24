package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.AuthException;
import java.net.ConnectException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class RaidProfileApiErrorTest {

    private static Throwable failure(int status, String body) {
        // Wrapped, because that is how it arrives from a CompletableFuture.
        return new CompletionException(new ApiClient.ApiException(status, body));
    }

    // ── Saying which side failed ──

    @Test
    void aRequestThatNeverGotAnAnswerSaysSo() {
        String message = RaidProfileApiError.describe(
                new CompletionException(new ConnectException("Connection refused")), "fallback");

        assertTrue(message.startsWith("Could not reach the backend"), message);
        assertTrue(message.contains("Connection refused"), "the cause is what tells you it is the network");
        assertEquals(0, RaidProfileApiError.status(new CompletionException(new ConnectException("x"))));
    }

    @Test
    void anUndeployedEndpointIsNotConfusedWithAnUnreachableBackend() {
        // The state this feature is in until the endpoints ship: the backend is up
        // and answering, it just has no route. Reporting that as "unreachable" sends
        // whoever reads it looking at their connection instead of at the deploy.
        String message = RaidProfileApiError.describe(failure(404, "{\"detail\":\"Not Found\"}"), "fallback");

        assertTrue(message.contains("404"), message);
        assertTrue(message.contains("not been deployed"), message);
    }

    @Test
    void aBackendErrorNamesItsStatus() {
        assertTrue(RaidProfileApiError.describe(failure(503, ""), "fallback").contains("503"));
        assertTrue(RaidProfileApiError.describe(failure(503, ""), "fallback").contains("backend errored"));
        assertTrue(RaidProfileApiError.describe(failure(418, ""), "fallback").contains("418"));
    }

    @Test
    void beingOnTheWrongServerIsNotReportedAsNotBeingInTheGuild() {
        // It arrives as a 403, which the guild-membership branch would otherwise
        // claim, and the two have completely different fixes.
        String body = "{\"error\":\"main_server_only\",\"message\":\"Sequoia only works on the main server.\"}";

        assertEquals("Sequoia only works on the main server.", RaidProfileApiError.describe(failure(403, body), "x"));
        assertTrue(RaidProfileApiError.isMainServerOnly(body));
    }

    // ── Using what the backend wrote ──

    @Test
    void theBackendMessageIsShownAsWrittenBecauseItIsWrittenForThePlayer() {
        String message = RaidProfileApiError.describe(
                failure(400, "{\"code\":\"invalid_request\",\"message\":\"Unknown build 'NOTABUILD'.\"}"),
                "fallback");

        assertEquals("Unknown build 'NOTABUILD'.", message);
    }

    @Test
    void identityUnknownExplainsWhatToDoAboutIt() {
        String message = RaidProfileApiError.describe(
                failure(409, "{\"code\":\"identity_unknown\",\"message\":\"No Minecraft name on file.\"}"),
                "fallback");

        assertEquals("No Minecraft name on file.", message);
        assertEquals("identity_unknown", RaidProfileApiError.code(failure(409, "{\"code\":\"identity_unknown\"}")));
    }

    @Test
    void aBodylessConflictStillTellsThePlayerHowToRecover() {
        String message = RaidProfileApiError.describe(failure(409, "{\"code\":\"identity_unknown\"}"), "fallback");

        assertTrue(message.contains("Rejoin the game"), "a 409 is recoverable, so say how");
    }

    @Test
    void theDocumentedStatusesGetTheirOwnSentence() {
        assertTrue(RaidProfileApiError.describe(failure(401, ""), "fallback").contains("/seq connect"));
        assertTrue(RaidProfileApiError.describe(failure(403, ""), "fallback").contains("guild member"));
        assertTrue(RaidProfileApiError.describe(failure(426, ""), "fallback").contains("Update Sequoia"));
    }

    @Test
    void aBodyThatIsNotJsonIsNotShownRaw() {
        String message = RaidProfileApiError.describe(failure(500, "<html>Bad Gateway</html>"), "Could not save.");

        assertTrue(message.contains("500"));
        assertTrue(!message.contains("<html>"), "a proxy's error page is not for the player");
        assertNull(RaidProfileApiError.code(failure(500, "<html>")));
    }

    // ── What lands in the log ──

    @Test
    void theLogLineCarriesTheStatusAndTheBody() {
        String logged = RaidProfileApiError.describeForLog(failure(404, "{\"detail\":\"Not Found\"}"));

        assertTrue(logged.contains("HTTP 404"), logged);
        assertTrue(logged.contains("Not Found"), logged);
    }

    @Test
    void theLogLineNamesTheExceptionWhenNothingAnswered() {
        String logged = RaidProfileApiError.describeForLog(new CompletionException(new ConnectException("refused")));

        assertTrue(logged.contains("no response"), logged);
        assertTrue(logged.contains("ConnectException"), logged);
    }

    @Test
    void aLongBodyIsTruncatedSoOneFailureIsNotAWallOfLog() {
        String logged = RaidProfileApiError.describeForLog(failure(500, "x".repeat(4000)));

        assertTrue(logged.length() < 300, "logged " + logged.length() + " characters");
        assertTrue(logged.endsWith("..."));
    }

    @Test
    void anEmptyBodyIsMarkedRatherThanLeftBlank() {
        assertTrue(RaidProfileApiError.describeForLog(failure(502, "")).contains("<empty>"));
    }

    // ── Signing in to a separate raid-profiles backend ──

    private static Throwable signInFailure(AuthErrorCode code, int status, String body) {
        // How MinecraftAuthService reports it: the backend's answer wrapped in an AuthException.
        return new CompletionException(
                new AuthException(code, "backend wording", false, new ApiClient.ApiException(status, body)));
    }

    @Test
    void anUnlinkedAccountIsToldWhereToLinkNotThatItIsOutOfTheGuild() {
        String message = RaidProfileApiError.describe(
                signInFailure(
                        AuthErrorCode.ACCOUNT_NOT_LINKED,
                        403,
                        "{\"code\":\"not_linked\",\"message\":\"Use /link with the Sierra bot in Discord\"}"),
                "fallback");

        assertTrue(message.contains("not linked on"), message);
        assertTrue(message.contains("link sent in chat"), message);
        assertTrue(!message.contains("guild member"), "the 403 is about linking, not membership");
        assertTrue(!message.contains("Sierra"), "the bot links the main backend, not this one");
    }

    @Test
    void anyOtherSignInFailureNamesTheBackendAndTheReason() {
        String message = RaidProfileApiError.describe(
                new CompletionException(new AuthException(AuthErrorCode.SESSION_JOIN_FAILED, "Restart Minecraft")),
                "fallback");

        assertTrue(message.startsWith("Could not sign in to "), message);
        assertTrue(message.endsWith("Restart Minecraft"), message);
    }

    @Test
    void theLogLineSaysItWasTheSignInThatFailed() {
        String logged = RaidProfileApiError.describeForLog(
                signInFailure(AuthErrorCode.ACCOUNT_NOT_LINKED, 403, "{\"code\":\"not_linked\"}"));

        assertTrue(logged.contains("sign-in failed code=not_linked"), logged);
        assertTrue(logged.contains("HTTP 403"), logged);
    }
}
