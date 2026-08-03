package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.Activity;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartyFinderCommandWorkflowTest {
    @Test
    void resolvesAliasesTrimsInputsAndDeduplicatesActivities() {
        PartyFinderManager manager = managerWithActivities();
        PartyFinderCommandWorkflow workflow = new PartyFinderCommandWorkflow(manager);

        PartyFinderManager.CommandResult<PartyFinderManager.ActivityResolution> result =
                workflow.resolveActivities(List.of(" TNA ", "The Nameless Anomaly", "NOL"), true);

        assertTrue(result.success());
        assertEquals(List.of(1L, 2L), result.data().activityIds());
        assertEquals(List.of("The Nameless Anomaly", "Nexus of Light"), result.data().displayNames());
    }

    @Test
    void rejectsUnknownAndIncompatibleActivitySelectionsWithExistingMessages() {
        PartyFinderManager manager = managerWithActivities();
        PartyFinderCommandWorkflow workflow = new PartyFinderCommandWorkflow(manager);

        var unknown = workflow.resolveActivities(List.of("TNA", "Mystery Raid"), true);
        var incompatible = workflow.resolveActivities(List.of("TNA", "ANNI"), true);

        assertFalse(unknown.success());
        assertEquals("Unknown activities: Mystery Raid.", unknown.message());
        assertFalse(incompatible.success());
        assertEquals("Prelude to Annihilation cannot be combined with other activities.", incompatible.message());
    }

    private static PartyFinderManager managerWithActivities() {
        PartyFinderManager manager = new PartyFinderManager();
        manager.getActivities().addAll(List.of(
                new Activity(1L, "TNA", 4),
                new Activity(2L, "NOL", 4),
                new Activity(3L, "ANNI", 4)));
        return manager;
    }
}
