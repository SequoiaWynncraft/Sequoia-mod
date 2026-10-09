package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.managers.WarTerritoryQueueManager;
import com.seqwawa.seq.model.war.WarPlannerAccess;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.ZoneCategory;
import java.awt.Color;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WarZoneSidebarTest {
    @Test
    void scrolledRowsOpenTheDisplayedZoneAndTabResetRestoresFirstRow() {
        var snapshot = snapshot();
        var actions = new Actions();
        var sidebar = new WarZoneSidebar(manager(snapshot), actions, () -> {}, Set.of(), Set.of());
        var layout = WarMapGeometry.warMapLayout(800, WarMapGeometry.contentTop(800), 388);
        float firstY = layout.mapY() + 64;
        sidebar.scroll(snapshot, layout, 2);
        assertTrue(sidebar.rightClickZoneName(snapshot, layout.sidebarX() + 25, firstY + 8, 800, 400));
        assertEquals(2, actions.opened.id());
        assertTrue(actions.inspect);
        assertFalse(sidebar.rightClickZoneName(snapshot, layout.sidebarX() - 1, firstY + 8, 800, 400));
        sidebar.resetScroll();
        assertTrue(sidebar.rightClickZoneName(snapshot, layout.sidebarX() + 25, firstY + 30 + 8, 800, 400));
        assertEquals(1, actions.opened.id());
    }

    @Test
    void tabResetCancelsAnOpenActionMenuAndPendingDrag() {
        var snapshot = snapshot();
        var sidebar = new WarZoneSidebar(manager(snapshot), new Actions(), () -> {}, Set.of(), Set.of());
        var layout = WarMapGeometry.warMapLayout(800, WarMapGeometry.contentTop(800), 388);
        float zoneY = layout.mapY() + 64 + 30;
        assertTrue(sidebar.click(snapshot, layout.sidebarX() + layout.sidebarWidth() - 20, zoneY + 10, 800, 400));
        assertTrue(sidebar.actionsOpen());
        sidebar.resetInteractions();
        assertFalse(sidebar.actionsOpen());
        sidebar.click(snapshot, layout.sidebarX() + 25, zoneY + 10, 800, 400);
        assertTrue(sidebar.drag(layout.sidebarX() + 35, zoneY + 10));
        sidebar.resetInteractions();
        assertFalse(sidebar.drag(layout.sidebarX() + 35, zoneY + 10));
        assertFalse(sidebar.release(layout.sidebarX() + 35, zoneY + 10, 800, 400));
    }

    private static WarPlannerSnapshot snapshot() {
        List<Zone> zones = IntStream.rangeClosed(1, 10).mapToObj(i ->
                new Zone(i, "Zone " + i, "#00ff00", List.of(), 1L, List.of(), null, i)).toList();
        return new WarPlannerSnapshot(3, Instant.EPOCH, new WarPlannerSnapshot.Self("self", true), true,
                List.of(), List.of(), new WarPlannerSnapshot.SupportBoard(1L, List.of()), zones, List.of(), List.of());
    }

    private static WarPlannerManager manager(WarPlannerSnapshot snapshot) {
        var gateway = (WarPlannerManager.Gateway) Proxy.newProxyInstance(WarPlannerManager.Gateway.class.getClassLoader(),
                new Class<?>[] {WarPlannerManager.Gateway.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "access" -> CompletableFuture.completedFuture(new WarPlannerAccess(1, Instant.EPOCH, "self", null));
                    case "snapshot" -> CompletableFuture.completedFuture(snapshot);
                    default -> throw new AssertionError(method.getName());
                });
        var manager = new WarPlannerManager(gateway, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        manager.tick(true, true);
        manager.refreshNow().join();
        return manager;
    }

    private static class Actions implements WarZonesTab.Actions {
        Zone opened;
        boolean inspect;
        public void openZone(Zone zone, boolean inspect) { this.opened = zone; this.inspect = inspect; }
        public void openCategory(ZoneCategory category) { fail(); }
        public void result(CompletableFuture<WarPlannerManager.ActionResult> result) { fail(); }
        public void queueResult(CompletableFuture<WarTerritoryQueueManager.ActionResult> result) { fail(); }
        public boolean busy() { return false; }
        public String stateLabel() { return "Ready"; }
        public Color stateColor() { return Color.GREEN; }
        public void feedback(String message) { fail(); }
    }
}
