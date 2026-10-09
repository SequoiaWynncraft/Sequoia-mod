package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.seqwawa.seq.map.GatheringTotemSearchTarget;
import com.seqwawa.seq.map.WorldMapSettings;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GatheringTotemPanelTest {
    @Test
    void scrolledDrawBoundsResolveToTheSameActionsAndResultsInContentCoordinates() {
        WorldMapSettings settings = WorldMapSettings.getInstance();
        boolean previouslyEnabled = settings.gatheringTotemSolverEnabled();
        var previousTarget = settings.gatheringTotemSearchTarget();
        try {
            settings.setGatheringTotemSolverEnabled(true);
            settings.setGatheringTotemSearchTarget(GatheringTotemSearchTarget.ALL_FILTERED);
            var first = GatheringTotemSessionTest.placement("first", 0);
            var second = GatheringTotemSessionTest.placement("second", 10);
            GatheringTotemSession session = new GatheringTotemSession(request ->
                    CompletableFuture.completedFuture(List.of(first, second)), Clock.systemUTC(), error -> fail(error));
            GatheringTotemPanel panel = new GatheringTotemPanel(settings, session);
            var request = new GatheringTotemSession.Request("results", List.of(), Set.of(), null, List.of(), false);
            panel.refreshNow(request, null);
            panel.refresh(request, null);
            var content = GatheringTotemPanel.layout(200, true);
            float scroll = 165;
            var drawn = content.shifted(-scroll);
            AtomicInteger refreshes = new AtomicInteger();
            AtomicInteger centers = new AtomicInteger();
            List<String> copied = new ArrayList<>();
            java.util.function.Consumer<com.seqwawa.seq.map.GatheringTotemSolver.Placement> copy =
                    placement -> copied.add(placement.key());
            for (int row = 0; row < 2; row++) {
                UiBounds bounds = drawn.result(row);
                assertTrue(panel.click(content, bounds.x() + 5, bounds.y() + 5 + scroll,
                        refreshes::incrementAndGet, centers::incrementAndGet, copy));
            }
            assertEquals(second, panel.selected());
            assertTrue(panel.click(content, drawn.center().x() + 5, drawn.center().y() + 5 + scroll,
                    refreshes::incrementAndGet, centers::incrementAndGet, copy));
            assertTrue(panel.click(content, drawn.copy().x() + 5, drawn.copy().y() + 5 + scroll,
                    refreshes::incrementAndGet, centers::incrementAndGet, copy));
            assertTrue(panel.click(content, drawn.refresh().x() + 5, drawn.refresh().y() + 5 + scroll,
                    refreshes::incrementAndGet, centers::incrementAndGet, copy));
            assertEquals(1, centers.get());
            assertEquals(1, refreshes.get());
            assertEquals(List.of("second"), copied);
            UiBounds gap = drawn.result(0);
            assertFalse(panel.click(content, gap.x() + 5, gap.y() + gap.height() + 1 + scroll,
                    refreshes::incrementAndGet, centers::incrementAndGet, copy));
            var collapsed = GatheringTotemPanel.layout(200, false);
            assertFalse(panel.click(collapsed, 15, 250, () -> fail(), () -> fail(), placement -> fail()));
            assertFalse(panel.scroll(collapsed, 15, 250, 1));
        } finally {
            settings.setGatheringTotemSolverEnabled(previouslyEnabled);
            settings.setGatheringTotemSearchTarget(previousTarget);
        }
    }

    @Test
    void changingTargetOrDisablingCancelsWorkAndLayerActionsPersist() {
        WorldMapSettings settings = WorldMapSettings.getInstance();
        boolean enabled = settings.gatheringTotemSolverEnabled();
        boolean hulls = settings.showGatheringTotemHulls();
        var target = settings.gatheringTotemSearchTarget();
        try {
            settings.setGatheringTotemSolverEnabled(true);
            settings.setGatheringTotemSearchTarget(GatheringTotemSearchTarget.ALL_FILTERED);
            List<CompletableFuture<List<com.seqwawa.seq.map.GatheringTotemSolver.Placement>>> workers = new ArrayList<>();
            GatheringTotemSession session = new GatheringTotemSession(request -> {
                var worker = new CompletableFuture<List<com.seqwawa.seq.map.GatheringTotemSolver.Placement>>();
                workers.add(worker);
                return worker;
            }, Clock.systemUTC(), error -> fail(error));
            GatheringTotemPanel panel = new GatheringTotemPanel(settings, session);
            var request = new GatheringTotemSession.Request("first", List.of(), Set.of(), null, List.of(), false);
            panel.refreshNow(request, null);
            var layout = GatheringTotemPanel.layout(100, true);
            UiBounds cluster = layout.targetSegment(1);
            panel.click(layout, cluster.x() + 5, cluster.y() + 5, () -> fail(), () -> fail(), placement -> fail());
            assertTrue(workers.getFirst().isCancelled());
            assertEquals(GatheringTotemSearchTarget.SELECTED_CLUSTER, panel.target());
            assertEquals(panel.target(), settings.gatheringTotemSearchTarget());
            UiBounds layer = layout.layer(0);
            panel.click(layout, layer.x() + 5, layer.y() + 5, () -> fail(), () -> fail(), placement -> fail());
            assertEquals(!hulls, panel.layerEnabled(0));
            assertEquals(!hulls, settings.showGatheringTotemHulls());
            panel.refreshNow(request, null);
            panel.setEnabled(false);
            assertTrue(workers.getLast().isCancelled());
            assertFalse(panel.pending());
            assertEquals("Disabled", panel.status(false));
            assertFalse(settings.gatheringTotemSolverEnabled());
        } finally {
            settings.setGatheringTotemSolverEnabled(enabled);
            settings.setShowGatheringTotemHulls(hulls);
            settings.setGatheringTotemSearchTarget(target);
        }
    }
}
