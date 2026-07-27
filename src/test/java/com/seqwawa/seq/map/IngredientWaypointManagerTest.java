package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.IngredientWaypointManager.Kind;
import com.seqwawa.seq.map.IngredientWaypointManager.Waypoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientWaypointManagerTest {
    @Test
    void replacesAndDeduplicatesRenderedWaypointsById() {
        IngredientWaypointManager manager = new IngredientWaypointManager();
        Waypoint original = waypoint("spawn", "First");
        Waypoint replacement = waypoint("spawn", "Replacement");
        Waypoint totem = new Waypoint("totem", Kind.TOTEM_SPOT, "Totem", "Ingredient", 4, 5, 6);

        manager.replaceAll(List.of(original, replacement, totem));

        assertEquals(2, manager.size());
        assertEquals(List.of(replacement, totem), manager.waypoints());
    }

    @Test
    void clearRemovesAllRenderedWaypoints() {
        IngredientWaypointManager manager = new IngredientWaypointManager();
        manager.replaceAll(List.of(waypoint("spawn", "Ingredient")));

        manager.clear();

        assertEquals(0, manager.size());
        assertTrue(manager.waypoints().isEmpty());
    }

    private static Waypoint waypoint(String id, String label) {
        return new Waypoint(id, Kind.INGREDIENT_SPAWN, label, "Mob", 1, 2, 3);
    }
}
