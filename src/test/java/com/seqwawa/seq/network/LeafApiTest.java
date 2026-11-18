package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafQuests;
import java.time.Instant;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class LeafApiTest {

    private static final Gson GSON = ApiClient.newGson();

    /** What Leaf's GET /v1/me/quests answered for a member with two banked dailies. */
    private static final String QUESTS = """
            {"daily":{"charges":2,"charge_cap":3,"next_charge_at":"2026-11-18T00:00:00.000Z"},
             "quests":[
               {"id":"daily:4","slot":"daily","title":"Complete 3 NOL raids or 9 wars",
                "objective":{"type":"wars","raid":null},"progress":4,"target":9,
                "goals":[
                  {"type":"raids","raid":"nol","title":"Complete 3 NOL raids","progress":1,"target":3},
                  {"type":"wars","raid":null,"title":"Take part in 9 wars","progress":4,"target":9}],
                "reward":{"leaf":300,"xp":150},"started_at":"2026-11-17T00:00:00.000Z",
                "expires_at":null,"completed_at":null},
               {"id":"weekly:2","slot":"weekly","title":"Earn 6,000 season rating",
                "objective":{"type":"season_rating","raid":null},"progress":6000,"target":6000,
                "reward":{"leaf":800,"xp":400},"started_at":"2026-11-16T00:00:00.000Z",
                "expires_at":"2026-11-23T00:00:00.000Z","completed_at":"2026-11-17T13:02:11.500Z"}],
             "recently_completed":[]}
            """;

    @Test
    void readsLeafQuestsWithTheirTimesAndObjectives() {
        LeafQuests quests = GSON.fromJson(QUESTS, LeafQuests.class);

        assertEquals(new LeafQuests.Daily(2, 3, Instant.parse("2026-11-18T00:00:00Z")), quests.daily());
        LeafQuests.Quest daily = quests.quest(LeafQuests.DAILY);
        assertEquals("Complete 3 NOL raids or 9 wars", daily.title());
        assertEquals(new LeafQuests.Objective("wars", null), daily.objective());
        assertEquals(java.util.List.of(
                        new LeafQuests.Goal("raids", "nol", "Complete 3 NOL raids", 1, 3),
                        new LeafQuests.Goal("wars", null, "Take part in 9 wars", 4, 9)),
                daily.goalsOrObjective());
        assertEquals(new LeafQuests.Reward(300, 150), daily.reward());
        assertNull(daily.expiresAt());
        assertFalse(daily.completed());
        LeafQuests.Quest weekly = quests.quest(LeafQuests.WEEKLY);
        assertTrue(weekly.completed());
        assertEquals(java.util.List.of(
                        new LeafQuests.Goal("season_rating", null, "Earn 6,000 season rating", 6000, 6000)),
                weekly.goalsOrObjective(), "a quest from a Leaf without goals has its objective as its one goal");
        assertEquals(Instant.parse("2026-11-17T13:02:11.500Z"), weekly.completedAt());
        assertNull(quests.quest(LeafQuests.REPEATABLE));
    }

    @Test
    void readsTheAccountWithItsLevel() {
        LeafAccount account = GSON.fromJson("""
                {"player":{"id":"00000000-0000-4000-8000-00000000000a","username":"Gaz"},
                 "leaf":12480,"xp":13120,
                 "level":{"number":7,"xp_into_level":640,"xp_for_next_level":2500}}
                """, LeafAccount.class);

        assertEquals(new LeafAccount(12480, 13120, new LeafAccount.Level(7, 640, 2500)), account);
    }

    @Test
    void recognisesLeafRefusingSomeoneWhoIsNotAMember() {
        ApiClient.ApiException notMember = new ApiClient.ApiException(
                403, "{\"code\":\"not_a_member\",\"message\":\"Leaf is for current Sequoia members\"}");

        assertTrue(ApiClient.isNotLeafMember(notMember));
        assertTrue(ApiClient.isNotLeafMember(new CompletionException(notMember)));
        assertFalse(ApiClient.isNotLeafMember(ApiClient.mainServerOnlyException()));
        assertFalse(ApiClient.isNotLeafMember(new ApiClient.ApiException(503, "{\"code\":\"identity_unavailable\"}")));
    }
}
