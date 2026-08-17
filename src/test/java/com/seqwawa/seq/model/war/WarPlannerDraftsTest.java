package com.seqwawa.seq.model.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarPlannerDraftsTest {
    private static TeamMemberDraft member(String id) {
        return new TeamMemberDraft(id);
    }

    @Test
    void teamRequiresOneToFiveUniqueMembersWithoutEmbeddedDuties() {
        assertThrows(IllegalArgumentException.class,
                () -> new TeamDraft("Alpha", null, List.of(
                        member("a"), member("a"))));

        TeamDraft valid = new TeamDraft(" Alpha ", null, List.of(
                member("a"), member("b")));
        assertEquals("Alpha", valid.name());
        assertEquals(2, valid.members().size());
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
                "Alpha", 0L, List.of(member("leader"))));
        assertThrows(IllegalArgumentException.class, () -> new ZoneDraft(
                "North", "#AABBCC", List.of(7L), -1L, List.of("Ragni")));
    }
}
