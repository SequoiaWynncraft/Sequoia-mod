package com.seqwawa.seq.model.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WarPlannerSnapshotTest {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>)
                    (json, type, context) -> Instant.parse(json.getAsString()))
            .create();

    @Test
    void readsSettledSnakeCaseContractAndNumericIds() {
        String json = """
                {
                  "schema_version": 3,
                  "revision": 41,
                  "server_time": "2026-08-16T12:00:00Z",
                  "self": {"player_uuid": "self", "can_manage": true},
                  "discord_roles_available": true,
                  "roster": [{
                    "player_uuid": "self", "minecraft_username": "Player",
                    "discord_id": "1", "discord_username": "discord",
                    "composition_roles": ["TANK", "SOLO", "DPS", "SOLO"],
                    "online": true, "available": true,
                    "available_until": "2026-08-16T13:00:00Z", "team_id": 12
                  }],
                  "teams": [{"id": 12, "name": "Alpha", "team_type": "FFA", "version": 3,
                    "composition_targets": {"solo": 1, "dps": 3, "tank": 1},
                    "members": [{"player_uuid": "self", "minecraft_username": "Player",
                      "position": 0}]}],
                  "support": {"version": 1, "slots": [
                    {"code":"LEAD","player_uuid":"self","minecraft_username":"Player"}
                  ]},
                  "zones": [{"id": 8, "name": "North", "color": "#AABBCC",
                    "assigned_team_ids": [12], "version": 4, "territories": ["Ragni"],
                    "category_id": 5, "position": 2}],
                  "zone_categories": [{"id":5,"name":"Frontline","position":0,"version":3}],
                  "hq_territory": "Ragni",
                  "map_version": 6,
                  "territories": ["Ragni", "Detlas"],
                  "territory_details": [{
                    "name":"Ragni","connections":["Ragni Main Entrance"],"resources":["EMERALD","CROP"]
                  }]
                }
                """;

        WarPlannerSnapshot snapshot = GSON.fromJson(json, WarPlannerSnapshot.class);

        assertTrue(snapshot.isSupported());
        assertEquals(41L, snapshot.revision());
        assertTrue(snapshot.self().canManage());
        assertEquals(12L, snapshot.roster().getFirst().teamId());
        assertTrue(snapshot.roster().getFirst().online());
        assertEquals(
                java.util.List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS, WarCompositionRole.TANK),
                snapshot.roster().getFirst().compositionRoles());
        assertEquals(12L, snapshot.teams().getFirst().id());
        assertEquals(WarTeamType.FFA, snapshot.teams().getFirst().teamType());
        assertEquals(new WarCompositionTargets(1, 3, 1), snapshot.teams().getFirst().compositionTargets());
        assertEquals(8L, snapshot.zones().getFirst().id());
        assertEquals(java.util.List.of(12L), snapshot.zones().getFirst().assignedTeamIds());
        assertEquals("#AABBCC", snapshot.zones().getFirst().color());
        assertEquals(5L, snapshot.zones().getFirst().categoryId());
        assertEquals(2, snapshot.zones().getFirst().position());
        assertEquals("Frontline", snapshot.zoneCategories().getFirst().name());
        assertEquals("Ragni", snapshot.hqTerritory());
        assertEquals(6, snapshot.mapVersion());
        assertEquals("self", snapshot.support().slots().getFirst().playerUuid());
        assertEquals(java.util.List.of("Ragni Main Entrance"), snapshot.territoryDetails().getFirst().connections());
    }

    @Test
    void missingCompositionTargetsDefaultToNoTarget() {
        WarPlannerSnapshot snapshot = GSON.fromJson(
                "{\"schema_version\":3,\"teams\":[{\"id\":1,\"name\":\"FFA 1\",\"version\":1,\"members\":[]}]}",
                WarPlannerSnapshot.class);

        assertEquals(WarCompositionTargets.NONE, snapshot.teams().getFirst().compositionTargets());
    }

    @Test
    void missingTeamTypeUsesSafeLegacyInferenceWithoutDefaultingUnknownNamesToVlow() {
        WarPlannerSnapshot snapshot = GSON.fromJson(
                """
                {"schema_version":3,"teams":[
                  {"id":1,"name":"Alpha","version":1,"members":[]},
                  {"id":2,"name":"VLow Munch 2","version":1,"members":[]},
                  {"id":3,"name":"Unexpected","team_type":"NEW_KIND","version":1,"members":[]}
                ]}
                """,
                WarPlannerSnapshot.class);

        assertEquals(WarTeamType.UNKNOWN, snapshot.teams().get(0).teamType());
        assertEquals(WarTeamType.VLOW_MUNCH, snapshot.teams().get(1).teamType());
        assertEquals(WarTeamType.UNKNOWN, snapshot.teams().get(2).teamType());
    }

    @Test
    void compositionRolesIgnoreUnknownAndNullValues() {
        String json = """
                {
                  "schema_version": 1,
                  "roster": [
                    {"player_uuid": "known", "composition_roles": ["UNKNOWN", null, "DPS"]},
                    {"player_uuid": "missing"},
                    {"player_uuid": "null", "composition_roles": null}
                  ]
                }
                """;

        WarPlannerSnapshot snapshot = GSON.fromJson(json, WarPlannerSnapshot.class);

        assertEquals(
                java.util.List.of(WarCompositionRole.DPS),
                snapshot.roster().get(0).compositionRoles());
        assertEquals(
                java.util.List.of(),
                snapshot.roster().get(1).compositionRoles());
        assertEquals(
                java.util.List.of(),
                snapshot.roster().get(2).compositionRoles());
        assertTrue(snapshot.onlineRoster().isEmpty());
    }

    @Test
    void onlineRosterAndTeamCandidatesPreserveOfflineCurrentMembers() {
        String json = """
                {
                  "schema_version": 1,
                  "roster": [
                    {"player_uuid": "online-free", "online": true},
                    {"player_uuid": "offline-free", "online": false},
                    {"player_uuid": "online-other", "online": true, "team_id": 9},
                    {"player_uuid": "offline-current", "online": false, "team_id": 7},
                    {"player_uuid": "online-current", "online": true, "team_id": 7}
                  ]
                }
                """;

        WarPlannerSnapshot snapshot = GSON.fromJson(json, WarPlannerSnapshot.class);

        assertEquals(
                java.util.List.of("online-free", "online-other", "online-current"),
                snapshot.onlineRoster().stream().map(WarPlannerSnapshot.RosterMember::playerUuid).toList());
        assertEquals(
                java.util.List.of("online-free", "online-other", "online-current"),
                snapshot.teamCandidates(null).stream().map(WarPlannerSnapshot.RosterMember::playerUuid).toList());
        assertEquals(
                java.util.List.of("online-free", "online-other", "offline-current", "online-current"),
                snapshot.teamCandidates(7L).stream().map(WarPlannerSnapshot.RosterMember::playerUuid).toList());
    }

    @Test
    void visibleRosterAlwaysKeepsTheAuthenticatedCaller() {
        String json = """
                {
                  "schema_version": 3,
                  "self": {"player_uuid": "self", "can_manage": false},
                  "roster": [
                    {"player_uuid": "self", "online": false},
                    {"player_uuid": "online", "online": true},
                    {"player_uuid": "offline", "online": false}
                  ]
                }
                """;

        WarPlannerSnapshot snapshot = GSON.fromJson(json, WarPlannerSnapshot.class);

        assertEquals(
                java.util.List.of("self", "online"),
                snapshot.visibleRoster().stream().map(WarPlannerSnapshot.RosterMember::playerUuid).toList());
    }
}
