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
               {"id":"daily:4","slot":"daily","title":"Complete 3 NOL raids",
                "objective":{"type":"raids","raid":"nol"},"progress":1,"target":3,
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
        assertEquals("Complete 3 NOL raids", daily.title());
        assertEquals(new LeafQuests.Objective("raids", "nol"), daily.objective());
        assertEquals(new LeafQuests.Reward(300, 150), daily.reward());
        assertNull(daily.expiresAt());
        assertFalse(daily.completed());
        LeafQuests.Quest weekly = quests.quest(LeafQuests.WEEKLY);
        assertTrue(weekly.completed());
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
