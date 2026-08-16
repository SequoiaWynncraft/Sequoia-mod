package com.seqwawa.seq.network;

import com.google.gson.JsonObject;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import com.seqwawa.seq.model.war.WarTeamRole;
import java.util.List;
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

    @Test
    void warAvailabilityPayloadUsesSnakeCaseAndBounds() {
        JsonObject payload = ApiClient.buildWarAvailabilityPayload(90);

        assertEquals(90, payload.get("duration_minutes").getAsInt());
        assertThrows(IllegalArgumentException.class, () -> ApiClient.buildWarAvailabilityPayload(0));
        assertThrows(IllegalArgumentException.class, () -> ApiClient.buildWarAvailabilityPayload(1441));
    }

    @Test
    void warTeamUpdatePayloadIsAtomicAndVersioned() {
        TeamDraft draft = new TeamDraft(
                "Alpha",
                7L,
                List.of(
                        new TeamMemberDraft("leader-uuid", WarTeamRole.WAR_LEADER),
                        new TeamMemberDraft("eco-uuid", WarTeamRole.ECOER)));

        JsonObject payload = ApiClient.buildWarTeamPayload(draft, true);

        assertEquals("Alpha", payload.get("name").getAsString());
        assertEquals(7L, payload.get("version").getAsLong());
        assertEquals("leader-uuid", payload.getAsJsonArray("members")
                .get(0).getAsJsonObject().get("player_uuid").getAsString());
        assertEquals("WAR_LEADER", payload.getAsJsonArray("members")
                .get(0).getAsJsonObject().get("role").getAsString());
        assertFalse(ApiClient.buildWarTeamPayload(new TeamDraft("Alpha", null, draft.members()), false)
                .has("version"));
    }

    @Test
    void warZonePayloadKeepsNumericAssignmentAndExplicitNull() {
        JsonObject assigned = ApiClient.buildWarZonePayload(
                new ZoneDraft("North", "#AABBCC", 42L, 3L, List.of("Ragni")), true);
        JsonObject unassigned = ApiClient.buildWarZonePayload(
                new ZoneDraft("North", "#AABBCC", null, null, List.of("Ragni")), false);

        assertEquals(42L, assigned.get("assigned_team_id").getAsLong());
        assertEquals(3L, assigned.get("version").getAsLong());
        assertEquals("Ragni", assigned.getAsJsonArray("territories").get(0).getAsString());
        assertTrue(unassigned.get("assigned_team_id").isJsonNull());
        assertFalse(unassigned.has("version"));
    }
}
