package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.model.war.WarStatusSnapshot;
import com.seqwawa.seq.model.war.WarStatusSnapshot.Player;
import com.seqwawa.seq.model.war.WarStatusSnapshot.Position;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TelemetryPlayerMapOverlayTest {
    private final GuildTerritoryIndex territories = new GuildTerritoryIndex(List.of(
            GuildTerritory.fromCorners("Ragni", 0, 0, 100, 200)));

    private final AtomicLong now = new AtomicLong(10_000);
    private final TickQueue ticks = new TickQueue(now::get);
    private final AtomicBoolean signedIn = new AtomicBoolean(true);
    private final ArrayDeque<CompletableFuture<WarStatusSnapshot>> replies = new ArrayDeque<>();

    private TelemetryPlayerMapOverlay overlay() {
        return new TelemetryPlayerMapOverlay(() -> {
            CompletableFuture<WarStatusSnapshot> reply = new CompletableFuture<>();
            replies.add(reply);
            return reply;
        }, signedIn::get, now::get, ticks);
    }

    private static WarStatusSnapshot roster(String username) {
        return new WarStatusSnapshot(1, List.of(new Player(username, "MAGE", "Ragni", null)));
    }

    @Test
    void fetchesTheRosterEveryFiveSecondsAndClearsItWhenAFetchFails() {
        TelemetryPlayerMapOverlay overlay = overlay();
        overlay.tick();
        overlay.tick();
        assertEquals(1, replies.size(), "one fetch at a time");
        replies.remove().complete(roster("Roamer"));
        ticks.runDue();
        assertEquals("Roamer", overlay.snapshot().players().getFirst().username());

        now.addAndGet(4_999);
        overlay.tick();
        assertTrue(replies.isEmpty());
        now.addAndGet(1);
        overlay.tick();
        replies.remove().completeExceptionally(new IllegalStateException("backend down"));
        ticks.runDue();
        assertEquals(WarStatusSnapshot.EMPTY, overlay.snapshot());
    }

    @Test
    void showsNobodyWithoutATokenAndDropsTheReplyToAFetchTheMapClosedOn() {
        TelemetryPlayerMapOverlay overlay = overlay();
        overlay.tick();
        replies.remove().complete(roster("Roamer"));
        ticks.runDue();

        signedIn.set(false);
        overlay.tick();
        assertEquals(WarStatusSnapshot.EMPTY, overlay.snapshot());
        assertTrue(replies.isEmpty(), "no fetch without a token");

        signedIn.set(true);
        overlay.tick();
        overlay.close();
        replies.remove().complete(roster("Late"));
        ticks.runDue();
        assertEquals(WarStatusSnapshot.EMPTY, overlay.snapshot());
    }

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
