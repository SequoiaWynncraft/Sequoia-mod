package com.seqwawa.seq.courage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.courage.CourageRangeTracker.PositionedPlayer;
import com.seqwawa.seq.model.WynnClassType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CourageRangeTrackerTest {
    private static final Vec3 CENTER = new Vec3(100, 64, -200);

    @Test
    void ringMatchesCourageRadius() {
        assertEquals(4.0, CourageRangeTracker.RADIUS);
    }

    @Test
    void countsPlayersInsideTheRingWithoutTheShamanAtItsCentre() {
        UUID shaman = UUID.randomUUID();
        List<PositionedPlayer> players = List.of(
                new PositionedPlayer(shaman, CENTER),
                new PositionedPlayer(UUID.randomUUID(), CENTER.add(3.9, 0, 0)),
                new PositionedPlayer(UUID.randomUUID(), CENTER.add(0, 3.5, 2)),
                new PositionedPlayer(UUID.randomUUID(), CENTER.add(4.1, 0, 0)));

        assertEquals(2, CourageRangeTracker.countPlayersInRange(CENTER, shaman, players, 4.0));
    }

    @Test
    void measuresRangeAsACylinderMatchingTheDrawnRing() {
        assertTrue(CourageRangeTracker.isInRange(CENTER, CENTER.add(2.8, 3.9, 2.8), 4.0));
        assertFalse(CourageRangeTracker.isInRange(CENTER, CENTER.add(2.9, 0, 2.9), 4.0));
        assertFalse(CourageRangeTracker.isInRange(CENTER, CENTER.add(0, 4.1, 0), 4.0));
    }

    @Test
    void rejectsTheEntitiesWynncraftDrivesItself() {
        UUID npc = UUID.randomUUID();

        assertFalse(CourageRangeTracker.isRealPlayer("?", npc, false, Set.of(npc)));
        assertFalse(CourageRangeTracker.isRealPlayer("?", npc, true, Set.of(npc)));
    }

    @Test
    void rejectsAPlayerEntityMissingFromTheTabRoster() {
        assertFalse(CourageRangeTracker.isRealPlayer("Pet", UUID.randomUUID(), false, Set.of()));
    }

    @Test
    void acceptsARosteredAccountAndTheLocalPlayer() {
        UUID rostered = UUID.randomUUID();

        assertTrue(CourageRangeTracker.isRealPlayer("Someone", rostered, false, Set.of(rostered)));
        assertTrue(CourageRangeTracker.isRealPlayer("Me", UUID.randomUUID(), true, Set.of()));
    }

    @Test
    void keepsAnObservedClassWhenThePlayerIsNoLongerHoldingAWeapon() {
        Map<UUID, WynnClassType> classes = new HashMap<>();
        UUID shaman = UUID.randomUUID();

        assertEquals(
                WynnClassType.SHAMAN,
                CourageRangeTracker.rememberClass(classes, shaman, WynnClassType.SHAMAN));
        assertEquals(WynnClassType.SHAMAN, CourageRangeTracker.rememberClass(classes, shaman, null));
        assertEquals(WynnClassType.SHAMAN, CourageRangeTracker.rememberClass(classes, shaman, null));
    }

    @Test
    void replacesAKnownClassOnlyWhenANewOneIsObserved() {
        Map<UUID, WynnClassType> classes = new HashMap<>();
        UUID player = UUID.randomUUID();
        CourageRangeTracker.rememberClass(classes, player, WynnClassType.SHAMAN);

        assertEquals(
                WynnClassType.ARCHER,
                CourageRangeTracker.rememberClass(classes, player, WynnClassType.ARCHER));
        assertEquals(WynnClassType.ARCHER, CourageRangeTracker.rememberClass(classes, player, null));
    }

    @Test
    void readsNoClassForAPlayerNeverSeenHoldingAWeapon() {
        assertNull(CourageRangeTracker.rememberClass(new HashMap<>(), UUID.randomUUID(), null));
    }

}
