package com.seqwawa.seq.network;

import com.google.gson.JsonObject;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientTest {

    @Test
    void resolveAuthBaseUrlStripsApiSuffix() {
        assertEquals("https://staging.seqwawa.com", ApiClient.resolveAuthBaseUrl("https://staging.seqwawa.com/api"));
    }

    @Test
    void resolveAuthBaseUrlPreservesNonApiBase() {
        assertEquals("https://staging.seqwawa.com", ApiClient.resolveAuthBaseUrl("https://staging.seqwawa.com"));
    }

    @Test
    void retryAlternateBaseWhenBearerMiddlewareInterceptsAuthRoute() {
        ApiClient.ApiException exception =
                new ApiClient.ApiException(401, "{\"code\":\"token_invalid\",\"message\":\"Missing bearer token\"}");

        assertEquals(true, ApiClient.shouldRetryAuthAtAlternateBase(exception));
    }

    @Test
    void doNotRetryAlternateBaseForUnrelatedServerErrors() {
        ApiClient.ApiException exception =
                new ApiClient.ApiException(500, "{\"message\":\"internal error\"}");

        assertEquals(false, ApiClient.shouldRetryAuthAtAlternateBase(exception));
    }

    @Test
    void mainServerOnlyExceptionUsesExpectedStatusAndMessage() {
        ApiClient.ApiException exception = ApiClient.mainServerOnlyException();

        assertEquals(403, exception.getStatusCode());
        assertTrue(exception.getResponseBody().contains("main_server_only"));
        assertTrue(exception.getResponseBody().contains(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE));
    }

    @Test
    void rewardQueueAspectRequestUsesDefaultReason() {
        JsonObject payload = ApiClient.buildRewardQueueRequestPayload("aspect", null);

        assertEquals("aspect", payload.get("type").getAsString());
        assertEquals("No reason provided.", payload.get("reason").getAsString());
    }

    @Test
    void rewardQueueTomeRequestRequiresReason() {
        assertThrows(IllegalArgumentException.class, () -> ApiClient.buildRewardQueueRequestPayload("tome", " "));
    }

    @Test
    void rewardQueueTomeRequestTrimsReason() {
        JsonObject payload = ApiClient.buildRewardQueueRequestPayload("tome", " Need guild tome ");

        assertEquals("tome", payload.get("type").getAsString());
        assertEquals("Need guild tome", payload.get("reason").getAsString());
    }

    @Test
    void rewardQueueRequestRejectsUnknownType() {
        assertThrows(IllegalArgumentException.class, () -> ApiClient.buildRewardQueueRequestPayload("emerald", null));
    }

    @Test
    void createListingPayloadIncludesAtomicAdmissionSettings() {
        JsonObject payload = ApiClient.buildCreateListingPayload(
                List.of(11L, 12L),
                PartyRegion.EU,
                PartyRole.TANK,
                "Fast clears",
                "EU21",
                PartyJoinPolicy.INVITE_ONLY,
                2);

        assertEquals("INVITE_ONLY", payload.get("joinPolicy").getAsString());
        assertEquals(2, payload.get("reservedSlots").getAsInt());
        assertEquals(2, payload.getAsJsonArray("activityIds").size());
        assertFalse(payload.has("mode"));
        assertFalse(payload.has("strict"));
    }

    @Test
    void createListingPayloadRejectsNegativeReservedSlots() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ApiClient.buildCreateListingPayload(
                        List.of(11L),
                        PartyRegion.NA,
                        PartyRole.DPS,
                        null,
                        null,
                        PartyJoinPolicy.OPEN,
                        -1));
    }

    @Test
    void createListingPayloadSerializesOtherRole() {
        JsonObject payload = ApiClient.buildCreateListingPayload(
                List.of(11L),
                PartyRegion.NA,
                PartyRole.OTHER,
                null,
                null,
                PartyJoinPolicy.OPEN,
                0);

        assertEquals("OTHER", payload.get("role").getAsString());
    }

    @Test
    void modVersionHeaderConstantMatchesBackendContract() {
        assertEquals("X-Sequoia-Mod-Version", ClientVersion.MOD_VERSION_HEADER);
        assertTrue(ClientVersion.MOD_VERSION_HEADER.startsWith("X-"));
    }

    // ── Raid profiles on a separate backend ──

    @Test
    void raidProfilesAreOnASeparateBackendOnlyWhenTheUrlsDiffer() {
        assertTrue(ApiClient.isSeparateBackend("https://staging.seqwawa.com/api", "https://api.seqwawa.com/api"));
        assertFalse(ApiClient.isSeparateBackend("https://api.seqwawa.com/api", "https://api.seqwawa.com/api"));
        assertFalse(
                ApiClient.isSeparateBackend("https://api.seqwawa.com/api/", "https://API.seqwawa.com/api"),
                "a trailing slash or a change of case is the same backend");
    }

    @Test
    void theDefaultBuildKeepsRaidProfilesOnTheMainBackend() {
        // Tests build without raid_profiles_environment, which must follow the main one.
        assertFalse(ApiClient.raidProfilesOnSeparateBackend());
        assertEquals(BuildConfig.API_URL, BuildConfig.RAID_PROFILES_API_URL);
    }

    @Test
    void signInToAnotherBackendTriesItsSiteRootThenItsApiRoot() {
        assertEquals(
                List.of("https://staging.seqwawa.com", "https://staging.seqwawa.com/api"),
                ApiClient.authRequestBaseUrlsFor("https://staging.seqwawa.com/api"));
        assertEquals(
                List.of("https://staging.seqwawa.com"),
                ApiClient.authRequestBaseUrlsFor("https://staging.seqwawa.com"),
                "no duplicate when the API root is already the site root");
    }

    @Test
    void aCallThatSucceedsDoesNotSignInAgain() {
        List<Boolean> tokenRequests = new ArrayList<>();

        String result = ApiClient.callWithTokenRetry(
                        force -> {
                            tokenRequests.add(force);
                            return CompletableFuture.completedFuture("token-1");
                        },
                        token -> CompletableFuture.completedFuture("used " + token))
                .join();

        assertEquals("used token-1", result);
        assertEquals(List.of(false), tokenRequests);
    }

    @Test
    void aRejectedTokenIsReplacedOnceAndTheCallRetried() {
        List<Boolean> tokenRequests = new ArrayList<>();
        List<String> tokensUsed = new ArrayList<>();

        String result = ApiClient.callWithTokenRetry(
                        force -> {
                            tokenRequests.add(force);
                            return CompletableFuture.completedFuture(force ? "fresh" : "stale");
                        },
                        token -> {
                            tokensUsed.add(token);
                            return "stale".equals(token)
                                    ? CompletableFuture.failedFuture(unauthorized())
                                    : CompletableFuture.completedFuture("ok with " + token);
                        })
                .join();

        assertEquals("ok with fresh", result);
        assertEquals(List.of(false, true), tokenRequests, "the second sign-in is forced");
        assertEquals(List.of("stale", "fresh"), tokensUsed);
    }

    @Test
    void aSecondRejectionIsReportedRatherThanLoopedOn() {
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<String> result = ApiClient.callWithTokenRetry(
                force -> CompletableFuture.completedFuture("token"),
                token -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(unauthorized());
                });

        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertTrue(ApiClient.isUnauthorized(failure));
        assertEquals(2, calls.get(), "one call, one retry, then stop");
    }

    @Test
    void otherFailuresAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<String> result = ApiClient.callWithTokenRetry(
                force -> CompletableFuture.completedFuture("token"),
                token -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(new ApiClient.ApiException(500, "{}"));
                });

        assertThrows(CompletionException.class, result::join);
        assertEquals(1, calls.get());
    }

    @Test
    void aFailedSignInIsReportedWithoutCallingTheBackend() {
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<String> result = ApiClient.callWithTokenRetry(
                force -> CompletableFuture.failedFuture(new IllegalStateException("not linked")),
                token -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture("unreachable");
                });

        assertThrows(CompletionException.class, result::join);
        assertEquals(0, calls.get());
    }

    private static ApiClient.ApiException unauthorized() {
        return new ApiClient.ApiException(401, "{\"code\":\"token_invalid\",\"message\":\"Invalid token\"}");
    }
}
