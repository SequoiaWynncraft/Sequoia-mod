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
                  "teams": [{"id": 12, "name": "Alpha", "version": 3,
                    "members": [{"player_uuid": "self", "minecraft_username": "Player",
                      "position": 0}]}],
                  "support": {"version": 1, "slots": [
                    {"code":"LEAD","player_uuid":"self","minecraft_username":"Player"}
                  ]},
                  "zones": [{"id": 8, "name": "North", "color": "#AABBCC",
                    "assigned_team_ids": [12], "version": 4, "territories": ["Ragni"]}],
                  "territories": ["Ragni", "Detlas"],
                  "territory_details": [{
                    "name":"Ragni","connections":["Ragni Main Entrance"],"resources":["EMERALD","CROP"]
                  }]
                }
                """;

        WarPlannerSnapshot snapshot = GSON.fromJson(json, WarPlannerSnapshot.class);

        assertTrue(snapshot.isSupported());
        assertTrue(snapshot.self().canManage());
        assertEquals(12L, snapshot.roster().getFirst().teamId());
        assertTrue(snapshot.roster().getFirst().online());
        assertEquals(
                java.util.List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS, WarCompositionRole.TANK),
                snapshot.roster().getFirst().compositionRoles());
        assertEquals(12L, snapshot.teams().getFirst().id());
        assertEquals(8L, snapshot.zones().getFirst().id());
        assertEquals(java.util.List.of(12L), snapshot.zones().getFirst().assignedTeamIds());
        assertEquals("#AABBCC", snapshot.zones().getFirst().color());
        assertEquals("self", snapshot.support().slots().getFirst().playerUuid());
        assertEquals(java.util.List.of("Ragni Main Entrance"), snapshot.territoryDetails().getFirst().connections());
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
}
