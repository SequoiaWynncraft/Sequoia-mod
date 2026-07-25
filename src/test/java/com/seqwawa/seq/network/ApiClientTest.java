package com.seqwawa.seq.network;

import com.google.gson.JsonObject;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyMode;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                PartyMode.GRIND,
                true,
                PartyRegion.EU,
                PartyRole.TANK,
                "Fast clears",
                "EU21",
                PartyJoinPolicy.INVITE_ONLY,
                2);

        assertEquals("INVITE_ONLY", payload.get("joinPolicy").getAsString());
        assertEquals(2, payload.get("reservedSlots").getAsInt());
        assertEquals(2, payload.getAsJsonArray("activityIds").size());
    }

    @Test
    void createListingPayloadRejectsNegativeReservedSlots() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ApiClient.buildCreateListingPayload(
                        List.of(11L),
                        PartyMode.CHILL,
                        false,
                        PartyRegion.NA,
                        PartyRole.DPS,
                        null,
                        null,
                        PartyJoinPolicy.OPEN,
                        -1));
    }

    @Test
    void modVersionHeaderConstantMatchesBackendContract() {
        assertEquals("X-Sequoia-Mod-Version", ClientVersion.MOD_VERSION_HEADER);
        assertTrue(ClientVersion.MOD_VERSION_HEADER.startsWith("X-"));
    }
}
