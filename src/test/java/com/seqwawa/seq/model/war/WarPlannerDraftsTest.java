package com.seqwawa.seq.model.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberMoveDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneCategoryDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZonePlacementDraft;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarPlannerDraftsTest {
    private static TeamMemberDraft member(String id) {
        return new TeamMemberDraft(id);
    }

    @Test
    void teamRequiresOneToFiveUniqueMembersWithoutEmbeddedDuties() {
        assertThrows(IllegalArgumentException.class,
                () -> new TeamDraft(null, null, List.of(member("a"))));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamDraft(WarTeamType.FFA, null, List.of(
                        member("a"), member("a"))));

        TeamDraft valid = new TeamDraft(WarTeamType.VLOW_MUNCH, null, List.of(
                member("a"), member("b")));
        assertEquals(WarTeamType.VLOW_MUNCH, valid.teamType());
        assertEquals(2, valid.members().size());
        assertEquals(WarTeamType.HQ, WarTeamType.fromTeamName("HQ Team"));
        assertEquals(WarTeamType.VLOW_MUNCH, WarTeamType.fromTeamName("VLow Munch 3"));
        assertEquals(WarTeamType.FFA, WarTeamType.fromTeamName("FFA 7"));
        assertEquals(WarTeamType.UNKNOWN, WarTeamType.fromTeamName("Alpha"));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamDraft(WarTeamType.UNKNOWN, null, List.of(member("a"))));
    }

    @Test
    void zoneNormalizesColorAndDeduplicatesTerritories() {
        ZoneDraft draft = new ZoneDraft(" North ", "aabbcc", List.of(), null, List.of("Ragni", "Ragni", "Detlas"));

        assertEquals("North", draft.name());
        assertEquals("#AABBCC", draft.color());
        assertEquals(List.of("Ragni", "Detlas"), draft.territories());
    }

    @Test
    void updateVersionsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new TeamDraft(
                WarTeamType.HQ, 0L, List.of(member("leader"))));
        assertThrows(IllegalArgumentException.class, () -> new ZoneDraft(
                "North", "#AABBCC", List.of(7L), -1L, List.of("Ragni")));
        assertThrows(IllegalArgumentException.class, () -> new ZoneCategoryDraft("Frontline", 0L));
        assertThrows(IllegalArgumentException.class, () -> new ZonePlacementDraft(4L, -1, 2L));
        assertThrows(IllegalArgumentException.class, () -> new ZonePlacementDraft(null, 0, 0L));
    }

    @Test
    void compositionTargetsAreBoundedAndPreservedInDrafts() {
        TeamDraft draft = new TeamDraft(
                WarTeamType.FFA,
                2L,
                new WarCompositionTargets(1, 3, 1),
                List.of(member("leader")));

        assertEquals(new WarCompositionTargets(1, 3, 1), draft.compositionTargets());
        assertThrows(IllegalArgumentException.class, () -> new WarCompositionTargets(0, 6, 0));
    }

    @Test
    void teamMemberMovesRequireVersionForEveryConcreteSide() {
        TeamMemberMoveDraft betweenTeams = new TeamMemberMoveDraft(1L, 2L, 3L, 4L);
        TeamMemberMoveDraft fromRoster = new TeamMemberMoveDraft(null, null, 3L, 4L);
        TeamMemberMoveDraft toRoster = new TeamMemberMoveDraft(1L, 2L, null, null);

        assertEquals(2L, betweenTeams.sourceVersion());
        assertEquals(3L, fromRoster.targetTeamId());
        assertEquals(1L, toRoster.sourceTeamId());
        assertThrows(IllegalArgumentException.class, () -> new TeamMemberMoveDraft(1L, null, 3L, 4L));
        assertThrows(IllegalArgumentException.class, () -> new TeamMemberMoveDraft(null, 2L, 3L, 4L));
        assertThrows(IllegalArgumentException.class, () -> new TeamMemberMoveDraft(null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new TeamMemberMoveDraft(1L, 2L, 1L, 2L));
    }
}
