package com.seqwawa.seq.model.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarPlannerDraftsTest {
    private static TeamMemberDraft member(String id, WarTeamRole role) {
        return new TeamMemberDraft(id, role);
    }

    @Test
    void teamRequiresExactlyOneLeaderAndOneToFiveUniqueMembers() {
        assertThrows(IllegalArgumentException.class,
                () -> new TeamDraft("Alpha", null, List.of(member("a", WarTeamRole.WARRER))));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamDraft("Alpha", null, List.of(
                        member("a", WarTeamRole.WAR_LEADER), member("b", WarTeamRole.WAR_LEADER))));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamDraft("Alpha", null, List.of(
                        member("a", WarTeamRole.WAR_LEADER), member("a", WarTeamRole.WARRER))));

        TeamDraft valid = new TeamDraft(" Alpha ", null, List.of(
                member("a", WarTeamRole.WAR_LEADER), member("b", WarTeamRole.ECOER)));
        assertEquals("Alpha", valid.name());
        assertEquals(2, valid.members().size());
    }

    @Test
    void teamAllowsAtMostThreeEcoers() {
        assertThrows(IllegalArgumentException.class, () -> new TeamDraft("Alpha", null, List.of(
                member("leader", WarTeamRole.WAR_LEADER),
                member("e1", WarTeamRole.ECOER),
                member("e2", WarTeamRole.ECOER),
                member("e3", WarTeamRole.ECOER),
                member("e4", WarTeamRole.ECOER))));
    }

    @Test
    void zoneNormalizesColorAndDeduplicatesTerritories() {
        ZoneDraft draft = new ZoneDraft(" North ", "aabbcc", null, null, List.of("Ragni", "Ragni", "Detlas"));

        assertEquals("North", draft.name());
        assertEquals("#AABBCC", draft.color());
        assertEquals(List.of("Ragni", "Detlas"), draft.territories());
    }

    @Test
    void updateVersionsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new TeamDraft(
                "Alpha", 0L, List.of(member("leader", WarTeamRole.WAR_LEADER))));
        assertThrows(IllegalArgumentException.class, () -> new ZoneDraft(
                "North", "#AABBCC", 7L, -1L, List.of("Ragni")));
    }
}
