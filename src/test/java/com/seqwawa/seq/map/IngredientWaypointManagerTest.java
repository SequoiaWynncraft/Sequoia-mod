package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.IngredientWaypointManager.DetailLine;
import com.seqwawa.seq.map.IngredientWaypointManager.Kind;
import com.seqwawa.seq.map.IngredientWaypointManager.Waypoint;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IngredientWaypointManagerTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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

    @Test
    void preservesIndividuallyColoredDetailLines() {
        Waypoint waypoint = new Waypoint(
                "totem",
                Kind.TOTEM_SPOT,
                "Totem",
                List.of(
                        new DetailLine("Tier zero", 0xFF999999),
                        new DetailLine("Tier three", 0xFFE64D00)),
                1,
                2,
                3,
                null);

        assertEquals(0xFF999999, waypoint.detailLines().getFirst().color());
        assertEquals("Tier zero\nTier three", waypoint.detail());
    }

    @Test
    void preservesPositiveRadiusAndClampsNegativeRadius() {
        Waypoint positive = new Waypoint(
                "spawn", Kind.INGREDIENT_SPAWN, "Ingredient", List.of(), 1, 2, 3, 24, null);
        Waypoint negative = new Waypoint(
                "totem", Kind.TOTEM_SPOT, "Totem", List.of(), 1, 2, 3, -5, null);

        assertEquals(24, positive.radius());
        assertEquals(0, negative.radius());
    }

    private static Waypoint waypoint(String id, String label) {
        return new Waypoint(id, Kind.INGREDIENT_SPAWN, label, "Mob", 1, 2, 3);
    }
}
