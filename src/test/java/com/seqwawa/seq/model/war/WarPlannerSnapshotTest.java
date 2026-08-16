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
                  "schema_version": 1,
                  "server_time": "2026-08-16T12:00:00Z",
                  "self": {"player_uuid": "self", "can_manage": true},
                  "discord_roles_available": true,
                  "roster": [{
                    "player_uuid": "self", "minecraft_username": "Player",
                    "discord_id": "1", "discord_username": "discord",
                    "discord_role_keys": ["military.member"], "available": true,
                    "available_until": "2026-08-16T13:00:00Z", "team_id": 12,
                    "team_role": "WAR_LEADER"
                  }],
                  "teams": [{"id": 12, "name": "Alpha", "version": 3,
                    "members": [{"player_uuid": "self", "minecraft_username": "Player",
                      "role": "WAR_LEADER", "position": 0}]}],
                  "zones": [{"id": 8, "name": "North", "color": "#AABBCC",
                    "assigned_team_id": 12, "version": 4, "territories": ["Ragni"]}],
                  "territories": ["Ragni", "Detlas"]
                }
                """;

        WarPlannerSnapshot snapshot = GSON.fromJson(json, WarPlannerSnapshot.class);

        assertTrue(snapshot.isSupported());
        assertTrue(snapshot.self().canManage());
        assertEquals(12L, snapshot.roster().getFirst().teamId());
        assertEquals(WarTeamRole.WAR_LEADER, snapshot.roster().getFirst().teamRole());
        assertEquals(12L, snapshot.teams().getFirst().id());
        assertEquals(8L, snapshot.zones().getFirst().id());
        assertEquals(12L, snapshot.zones().getFirst().assignedTeamId());
        assertEquals("#AABBCC", snapshot.zones().getFirst().color());
    }
}
