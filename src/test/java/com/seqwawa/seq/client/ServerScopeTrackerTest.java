package com.seqwawa.seq.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.network.WynncraftServerPolicy.Scope;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerScopeTrackerTest {

    /** The game thread: what arrives from the network thread waits here until a test runs it. */
    private final TickQueue ticks = new TickQueue(() -> 0L);
    private final ServerScopeTracker scopes = new ServerScopeTracker(ticks);
    private final List<String> heard = new ArrayList<>();

    @Test
    void startsOutBlockedSoTheFirstTickOnMainCountsAsArriving() {
        ServerScopeTracker.Tick first = scopes.update(Scope.MAIN, "play.wynncraft.com");

        assertEquals(Scope.BLOCKED, first.previous());
        assertTrue(first.changed());
        assertEquals(Scope.MAIN, scopes.current());
    }

    @Test
    void tellsScopeChangesOnceEach() {
        scopes.onScopeChange(tick -> heard.add(tick.previous() + " -> " + tick.current()));

        scopes.update(Scope.MAIN, "play.wynncraft.com");
        scopes.update(Scope.MAIN, "play.wynncraft.com");
        scopes.update(Scope.UNKNOWN, null);
        scopes.update(Scope.UNKNOWN, null);

        assertEquals(List.of("BLOCKED -> MAIN", "MAIN -> UNKNOWN"), heard);
    }

    @Test
    void aNewHostOnTheSameScopeIsAChange() {
        scopes.update(Scope.BLOCKED, "example.org");

        assertTrue(scopes.update(Scope.BLOCKED, "example.net").changed());
    }

    @Test
    void knowsWhenATickLeftTheMainServers() {
        scopes.update(Scope.MAIN, "play.wynncraft.com");

        assertTrue(scopes.update(Scope.UNKNOWN, null).leftMain());
        assertFalse(scopes.update(Scope.BLOCKED, "example.org").leftMain(), "it had already left");
    }

    @Test
    void tellsEveryTickAwayFromMainToTheListenersForThatScope() {
        scopes.whileBlocked(tick -> heard.add("blocked on " + tick.host()));
        scopes.whileUnknown(tick -> heard.add("unknown"));

        scopes.away(scopes.update(Scope.BLOCKED, "example.org"));
        scopes.away(scopes.update(Scope.BLOCKED, "example.org"));
        scopes.away(scopes.update(Scope.UNKNOWN, null));
        scopes.away(scopes.update(Scope.MAIN, "play.wynncraft.com"));

        assertEquals(List.of("blocked on example.org", "blocked on example.org", "unknown"), heard,
                "whatever arrived since the last tick is cleared again; nothing on main");
    }

    @Test
    void aDisconnectFromTheNetworkThreadIsToldOnTheGameThread() {
        scopes.onDisconnect(() -> heard.add("disconnected"));

        scopes.disconnected();
        assertEquals(List.of(), heard, "not on the network thread");

        ticks.runDue();
        assertEquals(List.of("disconnected"), heard);
    }

    @Test
    void tellsWorldChangesAndJoinsInTheOrderListenersAsked() {
        scopes.onWorldChange(() -> heard.add("first"));
        scopes.onWorldChange(() -> heard.add("second"));
        scopes.onJoin(() -> heard.add("joined"));

        scopes.worldChanged();
        scopes.joined();

        assertEquals(List.of("first", "second", "joined"), heard);
    }
}
