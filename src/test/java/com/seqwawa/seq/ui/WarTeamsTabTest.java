package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.model.war.WarPlannerAccess;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberMoveDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WarTeamsTabTest {
    @Test
    void memberDropPreservesSourceAndTargetVersions() {
        var h = new Harness();
        var layout = WarTeamsTab.teamsLayout(1200, WarMapGeometry.contentTop(1200) + 28, 688, 2);
        var cards = WarTeamsTab.teamPlacements(h.snapshot.teams(), 0, layout, true, false);
        var source = cards.get(0);
        var target = cards.get(1);
        float x = source.x() + 12, y = source.y() + source.actions().memberTop();
        assertTrue(h.tab.click(x, y, 1200, 700));
        assertTrue(h.tab.drag(x + 10, y + 10));
        assertTrue(h.tab.release(target.x() + 15, target.y() + 35, 1200, 700));
        assertEquals("member", h.player);
        assertEquals(new TeamMemberMoveDraft(1L, 5L, 2L, 8L), h.draft);
        assertEquals(1, h.results);
    }

    @Test
    void cardResetPreservesUnassignedPoolScrollAndSupportUsesItsOwnHitBounds() {
        var h = new Harness();
        float top = WarMapGeometry.contentTop(1200) + 28;
        var layout = WarTeamsTab.teamsLayout(1200, top, 688, 2);
        float poolY = top + 150;
        h.tab.scroll(h.snapshot, layout.sidebarX() + 10, poolY + 40, 1200, 700, 2);
        h.tab.reset();
        assertTrue(h.tab.click(layout.sidebarX() + 10, poolY + 40, 1200, 700));
        assertTrue(h.tab.drag(layout.sidebarX() + 20, poolY + 40));
        var target = WarTeamsTab.teamPlacements(h.snapshot.teams(), 0, layout, true, false).getFirst();
        h.tab.release(target.x() + 15, target.y() + 35, 1200, 700);
        assertEquals("unassigned-2", h.player);
        assertEquals(new TeamMemberMoveDraft(null, null, 1L, 5L), h.draft);
        h.tab.cancelDrag();
        var support = WarTeamsTab.supportPlacements(layout).getFirst();
        assertTrue(h.tab.click(support.x() + 2, support.y() + 2, 1200, 700));
        assertEquals(0, h.support);
    }

    private static class Harness implements WarTeamsTab.Actions {
        final WarPlannerSnapshot snapshot;
        final WarTeamsTab tab;
        String player;
        TeamMemberMoveDraft draft;
        int results;
        int support = -1;
        Harness() {
            List<RosterMember> roster = IntStream.range(0, 10).mapToObj(i -> new RosterMember(
                    "unassigned-" + i, "Name " + i, null, null, List.of(), true, true, null, null)).toList();
            snapshot = new WarPlannerSnapshot(3, Instant.EPOCH, new Self("self", true), true, roster,
                    List.of(new Team(1, "Source", 5L, List.of(new TeamMember("member", "Member", 0))),
                            new Team(2, "Target", 8L, List.of())),
                    new SupportBoard(1L, List.of()), List.of(), List.of(), List.of());
            var gateway = (WarPlannerManager.Gateway) Proxy.newProxyInstance(WarPlannerManager.Gateway.class.getClassLoader(),
                    new Class<?>[] {WarPlannerManager.Gateway.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "access" -> CompletableFuture.completedFuture(new WarPlannerAccess(1, Instant.EPOCH, "self", null));
                        case "snapshot" -> CompletableFuture.completedFuture(snapshot);
                        case "moveTeamMember" -> { player = (String) args[0]; draft = (TeamMemberMoveDraft) args[1]; yield new CompletableFuture<WarPlannerSnapshot>(); }
                        default -> throw new AssertionError(method.getName());
                    });
            var manager = new WarPlannerManager(gateway, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
            manager.tick(true, true);
            manager.refreshNow().join();
            tab = new WarTeamsTab(manager, this);
        }
        public void openTeam(Team team) { fail(); }
        public void openSupport(int slot) { support = slot; }
        public void result(CompletableFuture<WarPlannerManager.ActionResult> result) { results++; }
        public void feedback(String message) { fail(message); }
        public boolean busy() { return false; }
    }
}
