package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.*;

import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MapPlayerHeadRendererTest {
    private final UiImage face = new UiImage() {
        public int width() { return 64; }
        public int height() { return 64; }
    };
    private final List<Object[]> draws = new ArrayList<>();
    private final UiCanvas canvas = (UiCanvas) Proxy.newProxyInstance(
            UiCanvas.class.getClassLoader(), new Class<?>[] {UiCanvas.class},
            (proxy, method, args) -> {
                if (method.getName().equals("drawImage")) draws.add(args);
                return null;
            });

    @Test
    void pendingHeadDoesNotBlockAndLoadedHeadIsCenteredAndReused() {
        var pending = new CompletableFuture<byte[]>();
        var downloads = new AtomicInteger();
        var decoded = new AtomicInteger();
        var renderer = new MapPlayerHeadRenderer(name -> {
            downloads.incrementAndGet();
            return pending;
        }, bytes -> { decoded.incrementAndGet(); return face; }, image -> {});
        assertFalse(renderer.render(canvas, "Player", 100, 80));
        pending.complete(new byte[] {1});
        assertTrue(renderer.render(canvas, "Player", 100, 80));
        assertTrue(renderer.render(canvas, "Player", 120, 90));
        assertEquals(1, downloads.get());
        assertEquals(1, decoded.get());
        assertArrayEquals(new Object[] {face, 90f, 70f, 20f, 20f, 1f}, draws.getFirst());
    }

    @Test
    void pruningAndClosingReleaseImagesAndCancelUnusedDownloads() {
        var pending = new CompletableFuture<byte[]>();
        List<UiImage> deleted = new ArrayList<>();
        var renderer = new MapPlayerHeadRenderer(
                name -> name.equals("Ready") ? CompletableFuture.completedFuture(new byte[] {1}) : pending,
                bytes -> face, deleted::add);
        assertTrue(renderer.render(canvas, "Ready", 0, 0));
        assertFalse(renderer.render(canvas, "Pending", 0, 0));
        renderer.retain(Set.of("Ready"));
        assertTrue(pending.isCancelled());
        assertTrue(deleted.isEmpty());
        renderer.close();
        renderer.close();
        assertEquals(List.of(face), deleted);
    }

    @Test
    void failedDownloadKeepsFallbackWithoutRetryingEveryFrame() {
        var downloads = new AtomicInteger();
        var renderer = new MapPlayerHeadRenderer(name -> {
            downloads.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }, bytes -> face, image -> {});
        assertFalse(renderer.render(canvas, "Unavailable", 0, 0));
        assertFalse(renderer.render(canvas, "Unavailable", 0, 0));
        assertEquals(1, downloads.get());
        assertTrue(draws.isEmpty());
    }
}
