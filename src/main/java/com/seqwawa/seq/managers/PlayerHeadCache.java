package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player heads for the members panel.
 * <p>
 * Downloading happens off the render thread, but turning the bytes into an image
 * needs the render context, so that step is deferred to the first {@link #headFor}
 * call in a frame. A head still on its way returns null and the row draws a
 * placeholder, so nothing here ever blocks.
 */
public final class PlayerHeadCache {

    /** Rows draw at 16px, so this is one to one. */
    private static final int HEAD_PIXELS = 16;

    private static final String HEAD_URL = "https://mc-heads.net/avatar/%s/" + HEAD_PIXELS;
    private static final int MAX_HEAD_BYTES = 64 * 1024;

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static final Map<String, UiImage> IMAGES = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> DOWNLOADED = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private PlayerHeadCache() {}

    /** The head for a player, or null while it loads or if it failed. Render thread only. */
    public static UiImage headFor(String uuid) {
        String key = normalize(uuid);
        if (key == null || FAILED.contains(key)) {
            return null;
        }

        UiImage existing = IMAGES.get(key);
        if (existing != null) {
            return existing;
        }

        byte[] bytes = DOWNLOADED.remove(key);
        if (bytes != null) {
            UiImage image = UiRenderer.createImage(ByteBuffer.wrap(bytes), true);
            if (image == null) {
                if (UiRenderer.isAvailable()) {
                    // The renderer is up, so the bytes themselves would not decode.
                    // Retrying would only re-decode them every frame.
                    FAILED.add(key);
                } else {
                    // The renderer is not up yet. Put the bytes back and try next frame.
                    DOWNLOADED.put(key, bytes);
                }
                return null;
            }
            IMAGES.put(key, image);
            return image;
        }

        request(key);
        return null;
    }

    private static void request(String uuid) {
        if (!REQUESTED.add(uuid)) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(Locale.ROOT, HEAD_URL, uuid)))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "image/png")
                .GET()
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    byte[] body = response.body();
                    if (response.statusCode() != 200 || body == null || body.length == 0
                            || body.length > MAX_HEAD_BYTES) {
                        FAILED.add(uuid);
                        return;
                    }
                    DOWNLOADED.put(uuid, body);
                })
                .exceptionally(throwable -> {
                    // One head not loading is not worth a warning every frame; the row
                    // falls back to a placeholder and the panel carries on.
                    SeqClient.LOGGER.debug("[PlayerHeads] Could not fetch head for {}: {}", uuid, throwable.toString());
                    FAILED.add(uuid);
                    return null;
                });
    }

    /** Forgets failed lookups so a refresh can try them again. */
    public static void retryFailed() {
        FAILED.forEach(REQUESTED::remove);
        FAILED.clear();
    }

    static void reset() {
        IMAGES.clear();
        DOWNLOADED.clear();
        REQUESTED.clear();
        FAILED.clear();
    }

    static boolean hasFailed(String uuid) {
        String key = normalize(uuid);
        return key != null && FAILED.contains(key);
    }

    /**
     * One key per player: the roster writes UUIDs with dashes, the party finder may not,
     * and without this the same head would be fetched and kept twice.
     */
    private static String normalize(String uuid) {
        if (uuid == null) {
            return null;
        }
        String compact = uuid.trim().replace("-", "").toLowerCase(Locale.ROOT);
        return compact.isEmpty() ? null : compact;
    }
}
