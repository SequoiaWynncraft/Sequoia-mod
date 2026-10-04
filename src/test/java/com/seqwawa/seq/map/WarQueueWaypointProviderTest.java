package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.IngredientWaypointManager.Kind;
import com.seqwawa.seq.map.IngredientWaypointManager.Waypoint;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.Participant;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import java.time.Instant;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarQueueWaypointProviderTest {
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final GuildTerritoryIndex TERRITORIES = new GuildTerritoryIndex(List.of(
            GuildTerritory.fromCorners("Alekin", 10, -40, 30, -20),
            GuildTerritory.fromCorners("Ragni", 100, 200, 200, 300)));

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void selectsFirstOwnedQueueIgnoringJoinedAndProvisionalQueues() {
        var queues = List.of(queue(1, "Ragni", "other"), queue(2, "Ragni", null),
                queue(3, "Alekin", "SELF"), queue(4, "Ragni", "self"));
        Waypoint marker = WarQueueWaypointProvider.resolve(true, queues, "self", TERRITORIES, 75, NOW).orElseThrow();

        assertEquals("war-queue:3", marker.id());
        assertEquals(Kind.WAR_QUEUE, marker.kind());
        assertEquals("3m0s", marker.label());
        assertEquals("War for Alekin", marker.detail());
        assertEquals(20, marker.x());
        assertEquals(-30, marker.z());
        assertEquals(75, marker.y());
        assertEquals(0, marker.radius());
        assertTrue(marker.icon().stack().is(Items.IRON_SWORD));
    }

    @Test
    void labelCountsDownUsingProvidedServerTime() {
        var queues = List.of(queue(6, "Alekin", "self"));
        assertEquals("5m2s", WarQueueWaypointProvider.resolve(
                true, queues, "self", TERRITORIES, 70, NOW.plusSeconds(58)).orElseThrow().label());
        assertEquals("0m59s", WarQueueWaypointProvider.resolve(
                true, queues, "self", TERRITORIES, 70, NOW.plusSeconds(301)).orElseThrow().label());
        assertEquals("0m0s", WarQueueWaypointProvider.resolve(
                true, queues, "self", TERRITORIES, 70, NOW.plusSeconds(360)).orElseThrow().label());
    }

    @Test
    void hidesWhenDisabledIdentityMissingOrNoOwnedQueuesRemain() {
        var queues = List.of(queue(1, "Alekin", "self"));
        assertTrue(WarQueueWaypointProvider.resolve(false, queues, "self", TERRITORIES, 70, NOW).isEmpty());
        assertTrue(WarQueueWaypointProvider.resolve(true, queues, null, TERRITORIES, 70, NOW).isEmpty());
        assertTrue(WarQueueWaypointProvider.resolve(true, queues, " ", TERRITORIES, 70, NOW).isEmpty());
        assertTrue(WarQueueWaypointProvider.resolve(true, List.of(), "self", TERRITORIES, 70, NOW).isEmpty());
        assertTrue(WarQueueWaypointProvider.resolve(true,
                List.of(queue(2, "Alekin", "other"), queue(3, "Alekin", null)),
                "self", TERRITORIES, 70, NOW).isEmpty());
    }

    @Test
    void missingTerritoryDoesNotFallBackToLaterQueue() {
        assertTrue(WarQueueWaypointProvider.resolve(true,
                List.of(queue(1, "Unknown", "self"), queue(2, "Alekin", "self")),
                "self", TERRITORIES, 70, NOW).isEmpty());
    }

    @Test
    void followsCurrentSnapshotWithoutRetainingRemovedTarget() {
        var first = queue(1, "Alekin", "self");
        var next = queue(2, "Ragni", "self");
        assertEquals("war-queue:1", WarQueueWaypointProvider.resolve(
                true, List.of(first, next), "self", TERRITORIES, 70, NOW).orElseThrow().id());
        assertEquals("war-queue:2", WarQueueWaypointProvider.resolve(
                true, List.of(next), "self", TERRITORIES, 80, NOW).orElseThrow().id());
        assertTrue(WarQueueWaypointProvider.resolve(true, List.of(), "self", TERRITORIES, 80, NOW).isEmpty());
    }

    @Test
    void ingredientWaypointChangesDoNotAffectWarMarker() {
        var manager = IngredientWaypointManager.getInstance();
        var original = manager.waypoints();
        try {
            var ingredient = new Waypoint("ingredient", Kind.INGREDIENT_SPAWN, "Ingredient", "", 1, 2, 3);
            manager.replaceAll(List.of(ingredient));
            var queues = List.of(queue(1, "Alekin", "self"));
            var marker = WarQueueWaypointProvider.resolve(true, queues, "self", TERRITORIES, 70, NOW).orElseThrow();
            assertEquals(List.of(ingredient), manager.waypoints());
            manager.clear();
            var afterClear = WarQueueWaypointProvider.resolve(true, queues, "self", TERRITORIES, 70, NOW).orElseThrow();
            assertEquals(marker.id(), afterClear.id());
            assertEquals(marker.label(), afterClear.label());
            assertEquals(marker.x(), afterClear.x());
            assertEquals(marker.y(), afterClear.y());
            assertEquals(marker.z(), afterClear.z());
            assertTrue(ItemStack.matches(marker.icon().stack(), afterClear.icon().stack()));
            assertTrue(manager.waypoints().isEmpty());
        } finally {
            manager.replaceAll(original);
        }
    }

    private static TerritoryQueue queue(long id, String territory, String owner) {
        Instant now = Instant.parse("2026-09-30T12:00:00Z");
        return new TerritoryQueue(id, territory, owner, null, null, null, null,
                now, now.plusSeconds(id * 60), List.of(new Participant("self", "Self", 1)));
    }
}
