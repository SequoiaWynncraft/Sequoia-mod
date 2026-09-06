package com.seqwawa.seq.map;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Shared head images for local players and the war-map telemetry roster. */
public final class MapPlayerHeadRenderer implements AutoCloseable {
    public static final float HEAD_SIZE = 20;
    private static final int FACE_TEXTURE_PX = 64;
    private static final int MAX_FACE_BYTES = 512 * 1024;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final HttpClient FACE_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, CompletableFuture<byte[]>> faceDownloads = new HashMap<>();
    private final Map<String, UiImage> faces = new HashMap<>();
    private final Set<String> invalidFaces = new HashSet<>();
    private final Function<String, CompletableFuture<byte[]>> downloadFace;
    private final Function<byte[], UiImage> createImage;
    private final Consumer<UiImage> deleteImage;

    public MapPlayerHeadRenderer() {
        this(MapPlayerHeadRenderer::downloadFace,
                bytes -> UiRenderer.createImage(ByteBuffer.wrap(bytes), true), UiRenderer::deleteImage);
    }

    MapPlayerHeadRenderer(Function<String, CompletableFuture<byte[]>> downloadFace,
            Function<byte[], UiImage> createImage, Consumer<UiImage> deleteImage) {
        this.downloadFace = downloadFace;
        this.createImage = createImage;
        this.deleteImage = deleteImage;
    }

    public boolean render(UiCanvas canvas, String username, float x, float y) {
        UiImage face = face(username);
        if (face == null) return false;
        canvas.drawImage(face, x - HEAD_SIZE / 2, y - HEAD_SIZE / 2, HEAD_SIZE, HEAD_SIZE, 1f);
        return true;
    }

    public void renderLocalPlayer(UiCanvas canvas, MapViewport viewport) {
        if (SeqClient.mc.player == null) return;
        double x = SeqClient.mc.player.getX();
        double z = SeqClient.mc.player.getZ();
        if (!viewport.visibleBounds().contains(x, z)) return;
        String username = SeqClient.mc.getUser().getName();
        retain(Set.of(username));
        float sx = viewport.worldToScreenX(x);
        float sy = viewport.worldToScreenZ(z);
        canvas.save();
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        try {
            if (!render(canvas, username, sx, sy)) {
                canvas.fillRect(sx - 8, sy - 8, 16, 16, color(BACKGROUND_MODAL_OVERLAY));
                canvas.fillRect(sx - 5, sy - 5, 10, 10, color(MAP_PLAYER));
            }
        } finally {
            canvas.restore();
        }
    }

    @Override
    public void close() {
        faceDownloads.values().forEach(download -> download.cancel(true));
        faces.values().forEach(deleteImage);
        faces.clear();
        faceDownloads.clear();
        invalidFaces.clear();
    }

    private UiImage face(String username) {
        UiImage existing = faces.get(username);
        if (existing != null || invalidFaces.contains(username)) return existing;

        CompletableFuture<byte[]> download = faceDownloads.computeIfAbsent(username, downloadFace);
        if (!download.isDone()) return null;
        byte[] bytes = download.getNow(null);
        faceDownloads.remove(username);
        if (bytes == null) {
            invalidFaces.add(username);
            return null;
        }
        try {
            UiImage image = createImage.apply(bytes);
            if (image == null) invalidFaces.add(username);
            else faces.put(username, image);
            return image;
        } catch (RuntimeException exception) {
            invalidFaces.add(username);
            return null;
        }
    }

    private static CompletableFuture<byte[]> downloadFace(String username) {
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

    public void retain(Set<String> live) {
        faces.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) return false;
            deleteImage.accept(entry.getValue());
            return true;
        });
        faceDownloads.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) return false;
            entry.getValue().cancel(true);
            return true;
        });
        invalidFaces.removeIf(username -> !live.contains(username));
    }

}
