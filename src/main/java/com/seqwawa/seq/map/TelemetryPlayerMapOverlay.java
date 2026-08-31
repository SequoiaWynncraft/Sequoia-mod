package com.seqwawa.seq.map;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.war.WarStatusSnapshot;
import com.seqwawa.seq.model.war.WarStatusSnapshot.Player;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
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
import java.util.regex.Pattern;

/** Draws the opted-in war telemetry roster on the world map. */
public final class TelemetryPlayerMapOverlay {
    private static final long REFRESH_INTERVAL_MS = Duration.ofSeconds(5).toMillis();
    private static final int FACE_TEXTURE_PX = 64;
    private static final float HEAD_SIZE = 20;
    private static final float FAN_RADIUS = HEAD_SIZE * 0.65f;
    private static final float DOT_RADIUS = 3;
    private static final int MAX_FACE_BYTES = 512 * 1024;
    private static final Color PLATE = new Color(12, 14, 23, 217);
    private static final Color GOLD = new Color(245, 197, 66);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final HttpClient FACE_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ApiClient api = ApiClient.getInstance();
    private final Map<String, CompletableFuture<byte[]>> faceDownloads = new HashMap<>();
    private final Map<String, UiImage> faces = new HashMap<>();
    private final Set<String> invalidFaces = new HashSet<>();
    private volatile WarStatusSnapshot snapshot = WarStatusSnapshot.EMPTY;
    private volatile CompletableFuture<WarStatusSnapshot> refresh;
    private long nextRefreshAtMs;
    private volatile boolean closed;

    public TelemetryPlayerMapOverlay() {}

    public void tick() {
        if (closed) return;
        long now = System.currentTimeMillis();
        String token = SeqClient.getConfigManager().getToken();
        if (token == null || token.isBlank()) {
            snapshot = WarStatusSnapshot.EMPTY;
            nextRefreshAtMs = now + REFRESH_INTERVAL_MS;
            return;
        }
        if (now < nextRefreshAtMs || refresh != null && !refresh.isDone()) return;

        nextRefreshAtMs = now + REFRESH_INTERVAL_MS;
        refresh = api.getWarStatusSnapshot();
        refresh.whenComplete((received, throwable) -> {
            if (!closed) snapshot = throwable == null && received != null ? received : WarStatusSnapshot.EMPTY;
        });
    }

    public void render(UiCanvas canvas, MapViewport viewport, GuildTerritoryIndex territories) {
        List<PlayerPoint> points = resolvePlayerPoints(snapshot.players(), territories);
        pruneFaces(points);
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        try {
            for (PlayerPoint point : points) {
                float x = viewport.worldToScreenX(point.x()) + (float) point.fanX() * FAN_RADIUS;
                float z = viewport.worldToScreenZ(point.z()) + (float) point.fanZ() * FAN_RADIUS;
                if (!inBounds(viewport, x, z, HEAD_SIZE + 16)) continue;

                UiImage face = face(point.username());
                if (face == null) {
                    canvas.fillCircle(x, z, DOT_RADIUS, GOLD);
                    canvas.strokeCircle(x, z, DOT_RADIUS, 1, PLATE);
                    continue;
                }
                float half = HEAD_SIZE / 2;
                canvas.fillRect(x - half - 1, z - half - 1, HEAD_SIZE + 2, HEAD_SIZE + 2, PLATE);
                canvas.drawImage(face, x - half, z - half, HEAD_SIZE, HEAD_SIZE, 1);
            }
        } finally {
            canvas.resetScissor();
        }
    }

    public void close() {
        closed = true;
        faces.values().forEach(UiRenderer::deleteImage);
        faces.clear();
        faceDownloads.clear();
        invalidFaces.clear();
    }

    private UiImage face(String username) {
        UiImage existing = faces.get(username);
        if (existing != null || invalidFaces.contains(username)) return existing;

        CompletableFuture<byte[]> download = faceDownloads.computeIfAbsent(username, this::downloadFace);
        if (!download.isDone()) return null;
        byte[] bytes = download.getNow(null);
        faceDownloads.remove(username);
        if (bytes == null) {
            invalidFaces.add(username);
            return null;
        }
        try {
            UiImage image = UiRenderer.createImage(ByteBuffer.wrap(bytes), true);
            if (image == null) invalidFaces.add(username);
            else faces.put(username, image);
            return image;
        } catch (RuntimeException exception) {
            invalidFaces.add(username);
            return null;
        }
    }

    private CompletableFuture<byte[]> downloadFace(String username) {
        if (!USERNAME.matcher(username).matches()) return CompletableFuture.completedFuture(null);
        URI uri = URI.create("https://nmsr.seqwawa.com/face/" + username + "?w=" + FACE_TEXTURE_PX);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "image/png")
                .header("User-Agent", "Sequoia-Mod")
                .GET()
                .build();
        return FACE_HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .handle((response, throwable) -> {
                    if (throwable != null || response.statusCode() != 200) return null;
                    byte[] bytes = response.body();
                    return bytes.length > 0 && bytes.length <= MAX_FACE_BYTES ? bytes : null;
                });
    }

    private void pruneFaces(List<PlayerPoint> points) {
        Set<String> live = new HashSet<>();
        points.forEach(point -> live.add(point.username()));
        faces.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) return false;
            UiRenderer.deleteImage(entry.getValue());
            return true;
        });
        faceDownloads.keySet().removeIf(username -> !live.contains(username));
        invalidFaces.removeIf(username -> !live.contains(username));
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
