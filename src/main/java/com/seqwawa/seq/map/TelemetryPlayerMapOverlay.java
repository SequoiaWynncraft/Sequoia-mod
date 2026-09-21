package com.seqwawa.seq.map;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.war.WarStatusSnapshot;
import com.seqwawa.seq.model.war.WarStatusSnapshot.Player;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Draws the opted-in telemetry roster over the war planner map. */
public final class TelemetryPlayerMapOverlay implements AutoCloseable {
    private static final long REFRESH_INTERVAL_MS = Duration.ofSeconds(5).toMillis();
    private static final float HEAD_SIZE = MapPlayerHeadRenderer.HEAD_SIZE;
    private static final float FAN_RADIUS = HEAD_SIZE * 0.65f;
    private static final float MARKER_HALF_SIZE = 3;
    private static final Color PLATE = new Color(12, 14, 23, 217);
    private static final Color GOLD = new Color(245, 197, 66);
    private final ApiClient api = ApiClient.getInstance();
    private final MapPlayerHeadRenderer playerHeads = new MapPlayerHeadRenderer();
    private volatile WarStatusSnapshot snapshot = WarStatusSnapshot.EMPTY;
    private volatile CompletableFuture<WarStatusSnapshot> refresh;
    private long nextRefreshAtMs;
    private volatile long generation;

    public TelemetryPlayerMapOverlay() {}

    public void tick() {
        long now = System.currentTimeMillis();
        String token = SeqClient.getConfigManager().getToken();
        if (token == null || token.isBlank()) {
            cancelRefresh();
            snapshot = WarStatusSnapshot.EMPTY;
            nextRefreshAtMs = now + REFRESH_INTERVAL_MS;
            return;
        }
        if (now < nextRefreshAtMs || refresh != null && !refresh.isDone()) return;

        nextRefreshAtMs = now + REFRESH_INTERVAL_MS;
        long requestGeneration = generation;
        CompletableFuture<WarStatusSnapshot> request = api.getWarStatusSnapshot();
        refresh = request;
        request.whenComplete((received, throwable) -> {
            if (generation == requestGeneration) {
                snapshot = throwable == null && received != null ? received : WarStatusSnapshot.EMPTY;
            }
        });
    }

    public void render(UiCanvas canvas, MapViewport viewport, GuildTerritoryIndex territories) {
        List<PlayerPoint> points = resolvePlayerPoints(snapshot.players(), territories);
        Set<String> usernames = new HashSet<>();
        points.forEach(point -> usernames.add(point.username()));
        playerHeads.retain(usernames);
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        try {
            for (PlayerPoint point : points) {
                float x = viewport.worldToScreenX(point.x()) + (float) point.fanX() * FAN_RADIUS;
                float z = viewport.worldToScreenZ(point.z()) + (float) point.fanZ() * FAN_RADIUS;
                if (!inBounds(viewport, x, z, HEAD_SIZE + 16)) continue;

                if (!playerHeads.render(canvas, point.username(), x, z)) {
                    canvas.fillRect(x - MARKER_HALF_SIZE, z - MARKER_HALF_SIZE,
                            MARKER_HALF_SIZE * 2, MARKER_HALF_SIZE * 2, GOLD);
                    canvas.strokeRect(x - MARKER_HALF_SIZE, z - MARKER_HALF_SIZE,
                            MARKER_HALF_SIZE * 2, MARKER_HALF_SIZE * 2, 1, PLATE);
                }
            }
        } finally {
            canvas.resetScissor();
        }
    }

    @Override
    public void close() {
        generation++;
        cancelRefresh();
        playerHeads.close();
        snapshot = WarStatusSnapshot.EMPTY;
        nextRefreshAtMs = 0;
    }

    private void cancelRefresh() {
        CompletableFuture<WarStatusSnapshot> active = refresh;
        refresh = null;
        if (active != null) {
            generation++;
            if (!active.isDone()) active.cancel(true);
        }
    }

    static List<PlayerPoint> resolvePlayerPoints(List<Player> players, GuildTerritoryIndex territories) {
        List<PlayerPoint> points = new ArrayList<>();
        for (Player player : players) {
            if (player == null || player.username() == null || player.username().isBlank()) continue;
            double x;
            double z;
            if (player.pos() != null) {
                x = player.pos().x();
                z = player.pos().z();
            } else {
                GuildTerritory territory = territories.territory(player.territory());
                if (territory == null) continue;
                x = territory.centerX();
                z = territory.centerZ();
            }
            points.add(new PlayerPoint(player.username(), x, z, 0, 0));
        }
        points.sort(Comparator.comparing((PlayerPoint point) -> point.username().toLowerCase(Locale.ROOT))
                .thenComparing(PlayerPoint::username));

        Map<PointKey, List<Integer>> buckets = new HashMap<>();
        for (int index = 0; index < points.size(); index++) {
            PlayerPoint point = points.get(index);
            buckets.computeIfAbsent(new PointKey(point.x(), point.z()), ignored -> new ArrayList<>()).add(index);
        }
        for (List<Integer> indices : buckets.values()) {
            if (indices.size() < 2) continue;
            for (int slot = 0; slot < indices.size(); slot++) {
                int index = indices.get(slot);
                double angle = Math.PI * 2 * slot / indices.size();
                PlayerPoint point = points.get(index);
                points.set(index, new PlayerPoint(
                        point.username(), point.x(), point.z(), Math.cos(angle), Math.sin(angle)));
            }
        }
        return List.copyOf(points);
    }

    private static boolean inBounds(MapViewport viewport, float x, float y, float margin) {
        return x + margin >= viewport.screenX()
                && x - margin <= viewport.screenX() + viewport.screenWidth()
                && y + margin >= viewport.screenY()
                && y - margin <= viewport.screenY() + viewport.screenHeight();
    }

    record PlayerPoint(String username, double x, double z, double fanX, double fanZ) {}

    private record PointKey(long x, long z) {
        private PointKey(double x, double z) {
            this(Double.doubleToLongBits(x), Double.doubleToLongBits(z));
        }
    }
}
