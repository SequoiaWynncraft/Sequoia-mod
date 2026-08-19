package com.seqwawa.seq.network;

import com.google.gson.JsonObject;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarCompositionTargets;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberMoveDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportSlotDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneCategoryDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZonePlacementDraft;
import com.seqwawa.seq.model.war.WarTeamType;
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
    void warCompositionRolePayloadIsOrderedDeduplicatedAndAllowsEmpty() {
        JsonObject payload = ApiClient.buildWarCompositionRolesPayload(
                List.of(WarCompositionRole.TANK, WarCompositionRole.SOLO, WarCompositionRole.TANK));

        assertEquals("SOLO", payload.getAsJsonArray("roles").get(0).getAsString());
        assertEquals("TANK", payload.getAsJsonArray("roles").get(1).getAsString());
        assertTrue(ApiClient.buildWarCompositionRolesPayload(List.of()).getAsJsonArray("roles").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ApiClient.buildWarCompositionRolesPayload(null));
    }

    @Test
    void warTeamUpdatePayloadIsAtomicAndVersioned() {
        TeamDraft draft = new TeamDraft(
                WarTeamType.FFA,
                7L,
                new WarCompositionTargets(1, 3, 1),
                List.of(
                        new TeamMemberDraft("leader-uuid"),
                        new TeamMemberDraft("eco-uuid")));

        JsonObject payload = ApiClient.buildWarTeamPayload(draft, true);

        assertEquals("FFA", payload.get("team_type").getAsString());
        assertFalse(payload.has("name"));
        assertEquals(7L, payload.get("version").getAsLong());
        assertEquals(1, payload.getAsJsonObject("composition_targets").get("solo").getAsInt());
        assertEquals(3, payload.getAsJsonObject("composition_targets").get("dps").getAsInt());
        assertEquals(1, payload.getAsJsonObject("composition_targets").get("tank").getAsInt());
        assertEquals("leader-uuid", payload.getAsJsonArray("members")
                .get(0).getAsJsonObject().get("player_uuid").getAsString());
        assertFalse(payload.getAsJsonArray("members").get(0).getAsJsonObject().has("role"));
        assertFalse(payload.getAsJsonArray("members").get(0).getAsJsonObject().has("composition_roles"));
        assertFalse(ApiClient.buildWarTeamPayload(new TeamDraft(WarTeamType.FFA, null, draft.members()), false)
                .has("version"));
    }

    @Test
    void warTeamMemberMovePayloadKeepsNullableSidesAndBothVersions() {
        JsonObject betweenTeams = ApiClient.buildWarTeamMemberMovePayload(
                new TeamMemberMoveDraft(7L, 3L, 9L, 5L));
        JsonObject fromRoster = ApiClient.buildWarTeamMemberMovePayload(
                new TeamMemberMoveDraft(null, null, 9L, 5L));

        assertEquals(7L, betweenTeams.get("source_team_id").getAsLong());
        assertEquals(3L, betweenTeams.get("source_version").getAsLong());
        assertEquals(9L, betweenTeams.get("target_team_id").getAsLong());
        assertEquals(5L, betweenTeams.get("target_version").getAsLong());
        assertTrue(fromRoster.get("source_team_id").isJsonNull());
        assertTrue(fromRoster.get("source_version").isJsonNull());
        assertEquals(9L, fromRoster.get("target_team_id").getAsLong());
    }

    @Test
    void warPlannerDeletesCarryTheDisplayedVersion() {
        assertEquals("/war-planner/teams/7?version=3", ApiClient.versionedWarPlannerPath("/war-planner/teams/7", 3));
        assertEquals("/war-planner/zones/9?version=5", ApiClient.versionedWarPlannerPath("/war-planner/zones/9", 5));
        assertThrows(IllegalArgumentException.class,
                () -> ApiClient.versionedWarPlannerPath("/war-planner/teams/7", 0));
    }

    @Test
    void warZonePayloadKeepsMultipleNumericAssignments() {
        JsonObject assigned = ApiClient.buildWarZonePayload(
                new ZoneDraft("North", "#AABBCC", List.of(42L, 43L), 3L, List.of("Ragni")), true);
        JsonObject unassigned = ApiClient.buildWarZonePayload(
                new ZoneDraft("North", "#AABBCC", List.of(), null, List.of("Ragni")), false);

        assertEquals(42L, assigned.getAsJsonArray("assigned_team_ids").get(0).getAsLong());
        assertEquals(3L, assigned.get("version").getAsLong());
        assertEquals("Ragni", assigned.getAsJsonArray("territories").get(0).getAsString());
        assertTrue(unassigned.getAsJsonArray("assigned_team_ids").isEmpty());
        assertFalse(unassigned.has("version"));
    }

    @Test
    void warHqTerritoryPayloadCanAtomicallyReplaceOrClearTheMarker() {
        JsonObject assigned = ApiClient.buildWarHqTerritoryPayload("Detlas", 7);
        JsonObject cleared = ApiClient.buildWarHqTerritoryPayload(null, 8);

        assertEquals("Detlas", assigned.get("territory").getAsString());
        assertEquals(7, assigned.get("version").getAsLong());
        assertTrue(cleared.get("territory").isJsonNull());
        assertEquals(8, cleared.get("version").getAsLong());
        assertThrows(IllegalArgumentException.class, () -> ApiClient.buildWarHqTerritoryPayload("Detlas", 0));
    }

    @Test
    void warZoneCategoryAndPlacementPayloadsKeepVersionsAndNullableCategory() {
        JsonObject created = ApiClient.buildWarZoneCategoryPayload(
                new ZoneCategoryDraft("Frontline", null), false);
        JsonObject renamed = ApiClient.buildWarZoneCategoryPayload(
                new ZoneCategoryDraft("North front", 3L), true);
        JsonObject categorized = ApiClient.buildWarZonePlacementPayload(
                new ZonePlacementDraft(4L, 1, 7L));
        JsonObject uncategorized = ApiClient.buildWarZonePlacementPayload(
                new ZonePlacementDraft(null, 0, 8L));

        assertEquals("Frontline", created.get("name").getAsString());
        assertFalse(created.has("version"));
        assertEquals(3L, renamed.get("version").getAsLong());
        assertEquals(4L, categorized.get("category_id").getAsLong());
        assertEquals(1, categorized.get("position").getAsInt());
        assertEquals(7L, categorized.get("version").getAsLong());
        assertTrue(uncategorized.get("category_id").isJsonNull());
    }

    @Test
    void warSupportPayloadKeepsTheFourSharedSlotsSeparateFromParties() {
        JsonObject payload = ApiClient.buildWarSupportPayload(new SupportDraft(
                4L,
                List.of(
                        new SupportSlotDraft("LEAD", "lead-uuid"),
                        new SupportSlotDraft("ECO_1", "eco-uuid"))));

        assertEquals(4L, payload.get("version").getAsLong());
        assertEquals("LEAD", payload.getAsJsonArray("slots").get(0).getAsJsonObject().get("code").getAsString());
        assertEquals(
                "eco-uuid",
                payload.getAsJsonArray("slots").get(1).getAsJsonObject().get("player_uuid").getAsString());
    }
}
