package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.war.WarStatusSnapshot.Player;
import com.seqwawa.seq.model.war.WarStatusSnapshot.Position;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelemetryPlayerMapOverlayTest {
    private final GuildTerritoryIndex territories = new GuildTerritoryIndex(List.of(
            GuildTerritory.fromCorners("Ragni", 0, 0, 100, 200)));

    @Test
    void explicitPositionWinsOverTerritory() {
        List<TelemetryPlayerMapOverlay.PlayerPoint> points = TelemetryPlayerMapOverlay.resolvePlayerPoints(
                List.of(new Player("Roamer", "MAGE", "Ragni", new Position(-1517, -5130))), territories);

        assertEquals(-1517, points.getFirst().x());
        assertEquals(-5130, points.getFirst().z());
    }

    @Test
    void territoryOnlyPlayerUsesTerritoryCenter() {
        List<TelemetryPlayerMapOverlay.PlayerPoint> points = TelemetryPlayerMapOverlay.resolvePlayerPoints(
                List.of(new Player("Fighter", "WARRIOR", "Ragni", null)), territories);

        assertEquals(50, points.getFirst().x());
        assertEquals(100, points.getFirst().z());
    }

    @Test
    void unresolvedPlayerIsDropped() {
        assertTrue(TelemetryPlayerMapOverlay.resolvePlayerPoints(
                        List.of(new Player("Ghost", "ARCHER", "Nowhere", null)), territories)
                .isEmpty());
    }

    @Test
    void coLocatedPlayersAreFannedInStableNameOrder() {
        List<Player> party = List.of(
                new Player("charlie", "MAGE", "Ragni", null),
                new Player("alice", "MAGE", "Ragni", null),
                new Player("bob", "MAGE", "Ragni", null));
        List<TelemetryPlayerMapOverlay.PlayerPoint> points =
                TelemetryPlayerMapOverlay.resolvePlayerPoints(party, territories);

        assertEquals(List.of("alice", "bob", "charlie"), points.stream()
                .map(TelemetryPlayerMapOverlay.PlayerPoint::username)
                .toList());
        assertTrue(points.stream().allMatch(point -> point.x() == 50 && point.z() == 100));
        assertTrue(points.stream().allMatch(point -> point.fanX() != 0 || point.fanZ() != 0));
        assertFalse(points.get(0).fanX() == points.get(1).fanX()
                && points.get(0).fanZ() == points.get(1).fanZ());

        List<TelemetryPlayerMapOverlay.PlayerPoint> shuffled = TelemetryPlayerMapOverlay.resolvePlayerPoints(
                List.of(party.get(1), party.get(2), party.get(0)), territories);
        assertEquals(points, shuffled);
    }
}
