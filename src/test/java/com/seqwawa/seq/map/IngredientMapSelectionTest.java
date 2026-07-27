package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IngredientMapSelectionTest {
    @Test
    void independentlyTogglesMultipleSpawnAndTotemMarkers() {
        IngredientMapSelection selection = new IngredientMapSelection();

        assertTrue(selection.toggleSpawn("spawn-a"));
        assertTrue(selection.toggleSpawn("spawn-b"));
        assertTrue(selection.toggleTotem("totem-a"));

        assertEquals(3, selection.size());
        assertTrue(selection.isSpawnSelected("spawn-a"));
        assertTrue(selection.isSpawnSelected("spawn-b"));
        assertTrue(selection.isTotemSelected("totem-a"));

        assertFalse(selection.toggleSpawn("spawn-a"));
        assertFalse(selection.isSpawnSelected("spawn-a"));
        assertEquals(2, selection.size());
    }

    @Test
    void clearRemovesEveryMarkerType() {
        IngredientMapSelection selection = new IngredientMapSelection();
        selection.toggleSpawn("spawn");
        selection.toggleTotem("totem");

        selection.clear();

        assertTrue(selection.isEmpty());
        assertEquals(0, selection.size());
    }
}
