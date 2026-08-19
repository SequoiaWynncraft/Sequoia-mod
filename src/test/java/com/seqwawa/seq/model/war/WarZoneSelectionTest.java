package com.seqwawa.seq.model.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WarZoneSelectionTest {
    @Test
    void toggleIsImmutableAndSortedForTransport() {
        WarZoneSelection original = WarZoneSelection.of(List.of("Ragni"));
        WarZoneSelection added = original.toggle("Detlas");
        WarZoneSelection removed = added.toggle("Ragni");

        assertTrue(original.contains("Ragni"));
        assertFalse(original.contains("Detlas"));
        assertEquals(List.of("Detlas", "Ragni"), added.sortedNames());
        assertEquals(List.of("Detlas"), removed.sortedNames());
    }
}
