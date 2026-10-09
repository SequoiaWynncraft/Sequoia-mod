package com.seqwawa.seq.ui;

import com.seqwawa.seq.map.GatheringNode;
import com.seqwawa.seq.map.GatheringTotemResults;
import com.seqwawa.seq.map.GatheringTotemSolver.Placement;
import com.seqwawa.seq.map.GatheringTotemSolver.Position;
import com.seqwawa.seq.map.GuildTerritory;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Client-thread state for one map's asynchronous totem optimization. */
final class GatheringTotemSession {
    static final long DEBOUNCE_MS = 200;
    static final int VISIBLE_ROWS = 4;

    record Request(String key, List<GatheringNode> nodes, Set<String> resources,
            GuildTerritory territory, List<GatheringNode> clusterNodes, boolean awaitingCluster) {
        Request {
            nodes = List.copyOf(nodes);
            resources = Set.copyOf(resources);
            clusterNodes = List.copyOf(clusterNodes);
        }
    }

    private final Function<Request, CompletableFuture<List<Placement>>> solver;
    private final Clock clock;
    private final Consumer<RuntimeException> failure;
    private CompletableFuture<List<Placement>> pending;
    private String observedKey = "";
    private String solvedKey = "";
    private long notBefore;
    private List<Placement> placements = List.of();
    private Placement selected;
    private String selectedKey;
    private String error;
    private int scroll;

    GatheringTotemSession(Function<Request, CompletableFuture<List<Placement>>> solver,
            Clock clock, Consumer<RuntimeException> failure) {
        this.solver = solver;
        this.clock = clock;
        this.failure = failure;
    }

    void refresh(Request request, Position player) {
        if (!request.key().equals(observedKey)) invalidate(request.key(), clock.millis() + DEBOUNCE_MS);
        if (request.awaitingCluster()) {
            solvedKey = request.key();
            return;
        }
        if (pending != null) {
            if (!pending.isDone()) return;
            // Only the active future is read. Invalidated futures are detached even if cancellation is ignored.
            CompletableFuture<List<Placement>> completed = pending;
            pending = null;
            try {
                placements = GatheringTotemResults.ordered(completed.join(), player);
                selected = GatheringTotemResults.select(placements, selectedKey);
                selectedKey = selected == null ? null : selected.key();
                revealSelection();
                error = null;
            } catch (RuntimeException exception) {
                placements = List.of();
                selected = null;
                error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                failure.accept(exception);
            }
            solvedKey = request.key();
        }
        if (request.key().equals(solvedKey) || clock.millis() < notBefore) return;
        placements = List.of();
        selected = null;
        error = null;
        try {
            pending = solver.apply(request);
        } catch (RuntimeException exception) {
            error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            solvedKey = request.key();
            failure.accept(exception);
        }
    }

    void refreshNow(Request request, Position player) {
        invalidate(request.key(), 0);
        refresh(request, player);
    }

    void reset() { invalidate("", 0); }

    private void invalidate(String key, long deadline) {
        if (pending != null) pending.cancel(true);
        pending = null;
        observedKey = key;
        solvedKey = "";
        notBefore = deadline;
        placements = List.of();
        selected = null;
        error = null;
        scroll = 0;
    }

    List<Placement> placements() { return placements; }
    Placement selected() { return selected; }
    int scroll() { return scroll; }
    boolean pending() { return pending != null; }
    boolean optimizing() { return pending() || !observedKey.equals(solvedKey); }
    String error() { return error; }

    void select(Placement placement) {
        if (placement == null || !placements.contains(placement)) return;
        selected = placement;
        selectedKey = placement.key();
        revealSelection();
    }

    void scroll(int delta) { scroll = clampScroll(scroll + delta); }

    private int clampScroll(int value) { return Math.max(0, Math.min(value, placements.size() - VISIBLE_ROWS)); }

    private void revealSelection() {
        if (selected == null) { scroll = 0; return; }
        int index = placements.indexOf(selected);
        if (index < scroll) scroll = index;
        else if (index >= scroll + VISIBLE_ROWS) scroll = index - VISIBLE_ROWS + 1;
        scroll = clampScroll(scroll);
    }
}
