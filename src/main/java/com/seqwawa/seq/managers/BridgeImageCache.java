package com.seqwawa.seq.managers;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.BridgeImageDecoder;
import com.seqwawa.seq.utils.BridgeMedia;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Downloads the pictures shown under bridged Discord messages and keeps them as
 * textures while chat can still scroll back to them.
 * <p>
 * Pictures are keyed by the link they open, so one posted twice is fetched once. A
 * download is decoded off the render thread, straight to the size chat draws it at in
 * real screen pixels, so it is as sharp as the screen allows; only the upload happens on
 * the render thread, after which the decoded pixels are freed. The oldest pictures are
 * released once there are more than chat's history can hold, or they take too much
 * memory between them; chat then shows their label instead.
 * <p>
 * Everything except the download and decoding runs on the render thread.
 */
public final class BridgeImageCache {

    /** Where a picture is in its life. */
    public enum State {
        LOADING,
        READY,
        FAILED,
        RELEASED
    }

    /** Eight lines: about the height of an embed in Discord, without filling a closed chat. */
    public static final int DEFAULT_MAX_LINES = 8;

    /** Chat keeps a hundred lines, and a picture takes several of them. */
    static final int MAX_IMAGES = 32;

    /** What all pictures' frames may take together on the graphics card. */
    static final long MAX_TOTAL_BYTES = 192L * 1024 * 1024;

    /** What one picture's frames may take, which bounds how long and large a GIF plays. */
    static final long MAX_IMAGE_BYTES = 64L * 1024 * 1024;

    /** Slightly above Discord's own upload limit for most servers; anything larger is not fetched. */
    static final int MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024;

    /** Redirects are followed by hand, so each one's host is checked; see {@link #download}. */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final int MAX_REDIRECTS = 3;

    /** One thread: decoding is occasional, and must never compete with the game for cores. */
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Sequoia bridge images");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    /** Oldest first, so eviction takes from the front. Render thread only. */
    private static final Map<String, Image> IMAGES = new LinkedHashMap<>();

    private static int nextId;
    private static long totalBytes;

    private BridgeImageCache() {}

    /**
     * The picture for {@code picture}, starting its download unless it is already held
     * or on its way. A picture that failed before is tried again.
     */
    public static Image request(BridgeMedia.Picture picture) {
        Image held = IMAGES.remove(picture.key());
        if (held != null && (held.state == State.READY || held.state == State.LOADING)) {
            // Re-inserted, so a picture posted again counts as recent.
            IMAGES.put(held.key, held);
            return held;
        }

        Image image = new Image(picture.key(), nextId++);
        IMAGES.put(image.key, image);
        URI fetch = picture.fetch();
        if (!BridgeMedia.isSafeMediaLink(fetch)) {
            // BridgeMedia never makes such a picture; refused all the same.
            image.state = State.FAILED;
            return image;
        }
        int[] box = textureBox();
        CompletableFuture.supplyAsync(() -> download(fetch), DECODER)
                .thenApply(bytes -> decode(bytes, box[0], box[1]))
                .exceptionallyCompose(error -> retryAsPng(fetch, error, box))
                .whenComplete((frames, error) -> Minecraft.getInstance().execute(() -> settle(image, frames, error)));
        evict();
        return image;
    }

    /** The picture held under {@code key}, or {@code null}. */
    public static Image find(String key) {
        return key == null ? null : IMAGES.get(key);
    }

    /** Whether bridged pictures are shown in chat rather than left as links. */
    public static boolean enabled() {
        return SeqClient.getShowBridgeImagesSetting() == null
                || SeqClient.getShowBridgeImagesSetting().getValue();
    }

    /** The most chat lines a picture may take. */
    public static int maxLines() {
        return SeqClient.getBridgeImageLinesSetting() == null
                ? DEFAULT_MAX_LINES
                : SeqClient.getBridgeImageLinesSetting().getValue();
    }

    /**
     * Real screen pixels per chat pixel: the GUI scale times chat's own scale. A
     * picture decoded at this density is drawn one texel to one screen pixel.
     */
    public static double pixelDensity() {
        Minecraft minecraft = Minecraft.getInstance();
        return Math.max(1d, minecraft.getWindow().getGuiScale() * minecraft.options.chatScale().get());
    }

    /**
     * The largest a picture's texture can usefully be: chat's width by the configured
     * number of lines, in screen pixels.
     */
    private static int[] textureBox() {
        Minecraft minecraft = Minecraft.getInstance();
        double spacing = minecraft.options.chatLineSpacing().get();
        int lineHeight = (int) (9.0 * (spacing + 1.0));
        // As chat works out its own width, which it keeps to itself.
        double width = ChatComponent.getWidth(minecraft.options.chatWidth().get())
                / Math.max(0.01d, minecraft.options.chatScale().get());
        double height = maxLines() * lineHeight;
        double density = pixelDensity();
        int maxSide = RenderSystem.getDevice().getMaxTextureSize();
        return new int[] {
            (int) Math.min(maxSide, Math.ceil(width * density)), (int) Math.min(maxSide, Math.ceil(height * density))
        };
    }

    /**
     * The picture at {@code uri}, fetched with every check a download from a chat
     * message deserves: only from {@linkplain BridgeMedia#isTrustedMediaHost trusted
     * hosts}, redirects included, and only what calls itself an image, is no larger than
     * {@link #MAX_DOWNLOAD_BYTES}, and starts like a GIF, PNG, JPEG or WebP.
     */
    private static byte[] download(URI uri) {
        try {
            URI current = uri;
            for (int redirects = 0; ; redirects++) {
                if (!BridgeMedia.isTrustedMediaHost(current)) {
                    throw new IOException("refusing to fetch a picture from " + current.getHost());
                }
                HttpResponse<InputStream> response = HTTP.send(request(current), HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    int status = response.statusCode();
                    if (status >= 300 && status < 400 && redirects < MAX_REDIRECTS) {
                        String location = response.headers().firstValue("Location")
                                .orElseThrow(() -> new IOException("redirect without a location"));
                        current = current.resolve(location);
                        continue;
                    }
                    if (status != 200) {
                        throw new IOException("picture request returned status " + status);
                    }
                    String type = response.headers().firstValue("Content-Type").orElse("");
                    if (!type.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
                        throw new IOException("not an image: " + (type.isEmpty() ? "no content type" : type));
                    }
                    if (response.headers().firstValueAsLong("Content-Length").orElse(0L) > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("picture is larger than " + MAX_DOWNLOAD_BYTES + " bytes");
                    }
                    byte[] bytes = body.readNBytes(MAX_DOWNLOAD_BYTES + 1);
                    if (bytes.length > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("picture is larger than " + MAX_DOWNLOAD_BYTES + " bytes");
                    }
                    if (!BridgeImageDecoder.isSupportedPicture(bytes)) {
                        throw new IOException("not a GIF, PNG, JPEG or WebP picture");
                    }
                    return bytes;
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("picture download interrupted", exception);
        }
    }

    private static HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "image/gif,image/webp,image/png,image/jpeg;q=0.9,image/*;q=0.5")
                .header("User-Agent", "Sequoia-mod (Minecraft chat image preview)")
                .GET()
                .build();
    }

    /**
     * A second try for a picture that could not be read, such as a CMYK JPEG, or an
     * AVIF Discord's proxy could not turn into a WebP, through the proxy converting it
     * to PNG: its first frame at least. Only Discord's proxies convert.
     */
    private static CompletableFuture<Frames> retryAsPng(URI fetch, Throwable error, int[] box) {
        boolean unreadable = false;
        for (Throwable cause = error; cause != null && !unreadable; cause = cause.getCause()) {
            unreadable = cause instanceof UnreadablePicture;
        }
        String query = String.valueOf(fetch.getRawQuery());
        // A conversion the proxy turned down fails as a download, not as a picture.
        boolean refusedConversion = query.contains("format=") && !query.contains("format=png");
        URI converted = BridgeMedia.convertedByDiscord(fetch, "format=png");
        if (converted == null || query.contains("format=png") || !(unreadable || refusedConversion)) {
            return CompletableFuture.failedFuture(error);
        }
        return CompletableFuture.supplyAsync(() -> decode(download(converted), box[0], box[1]), DECODER);
    }

    /** Downloaded bytes Java could not read as a picture, as opposed to a failed download. */
    private static final class UnreadablePicture extends RuntimeException {
        UnreadablePicture(Throwable cause) {
            super(cause.getMessage(), cause, false, false);
        }
    }

    /** Decoded frames, copied into native images ready to upload. */
    private record Frames(int sourceWidth, int sourceHeight, List<NativeImage> images, int[] delays) {
        void close() {
            images.forEach(NativeImage::close);
        }
    }

    private static Frames decode(byte[] bytes, int maxWidth, int maxHeight) {
        BridgeImageDecoder.Decoded decoded;
        try {
            decoded = BridgeImageDecoder.decode(bytes, maxWidth, maxHeight, MAX_IMAGE_BYTES);
        } catch (IOException | RuntimeException exception) {
            throw new UnreadablePicture(exception);
        }

        List<NativeImage> images = new ArrayList<>(decoded.frames().size());
        int[] delays = new int[decoded.frames().size()];
        try {
            for (int index = 0; index < delays.length; index++) {
                BridgeImageDecoder.Frame frame = decoded.frames().get(index);
                images.add(toNative(frame.image()));
                delays[index] = frame.delayMillis();
            }
        } catch (RuntimeException exception) {
            images.forEach(NativeImage::close);
            throw exception;
        }
        return new Frames(decoded.sourceWidth(), decoded.sourceHeight(), List.copyOf(images), delays);
    }

    private static NativeImage toNative(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        NativeImage pixels = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels.setPixel(x, y, argb[y * width + x]);
            }
        }
        return pixels;
    }

    /** Uploads a finished download, or records its failure, then redraws chat. */
    private static void settle(Image image, Frames frames, Throwable error) {
        if (IMAGES.get(image.key) != image) {
            // Released while it was loading.
            if (frames != null) {
                frames.close();
            }
            return;
        }

        if (error != null || frames == null) {
            image.state = State.FAILED;
            SeqClient.LOGGER.info("[BridgeImages] Could not show {}: {}", fileName(image.key), rootCause(error));
        } else {
            try {
                image.upload(frames);
                totalBytes += image.bytes;
                image.state = State.READY;
                SeqClient.LOGGER.info(
                        "[BridgeImages] Showing {}: {}x{}, {} frame(s)",
                        fileName(image.key),
                        frames.sourceWidth(),
                        frames.sourceHeight(),
                        frames.images().size());
            } catch (RuntimeException exception) {
                image.release();
                image.state = State.FAILED;
                SeqClient.LOGGER.info("[BridgeImages] Could not upload {}", fileName(image.key), exception);
            } finally {
                // Uploaded or not, the pixels are not needed again.
                frames.close();
            }
        }
        evict();
        image.refreshWaitingViews();
    }

    /** Releases the oldest pictures until the rest fit, always keeping the newest. */
    private static void evict() {
        Iterator<Image> oldestFirst = IMAGES.values().iterator();
        while (IMAGES.size() > 1 && (IMAGES.size() > MAX_IMAGES || totalBytes > MAX_TOTAL_BYTES)) {
            Image oldest = oldestFirst.next();
            oldestFirst.remove();
            totalBytes -= oldest.bytes;
            oldest.release();
        }
    }

    /** A picture's file name, for the log, leaving out the signed address it came from. */
    private static String fileName(String link) {
        String path = link.split("[?#]", 2)[0];
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** A picture: its state, its size, and one texture per frame once it is loaded. */
    public static final class Image {
        private final String key;
        private final int id;
        private volatile State state = State.LOADING;
        private int sourceWidth;
        private int sourceHeight;
        private List<Identifier> frames = List.of();
        /** When each frame ends, in milliseconds from the start of a loop. */
        private int[] frameEnds = new int[0];
        private long bytes;
        private long startedAt;
        private final Set<Runnable> waitingViews = new LinkedHashSet<>();

        private Image(String key, int id) {
            this.key = key;
            this.id = id;
        }

        public State state() {
            return state;
        }

        /** Width of the picture as sent, which is what chat sizes it from. */
        public int sourceWidth() {
            return sourceWidth;
        }

        public int sourceHeight() {
            return sourceHeight;
        }

        /** The frame to draw at {@code millis}, looping; {@code null} unless loaded. */
        public Identifier frameAt(long millis) {
            List<Identifier> current = frames;
            if (state != State.READY || current.isEmpty()) {
                return null;
            }
            if (current.size() == 1) {
                return current.getFirst();
            }
            int[] ends = frameEnds;
            int loop = ends[ends.length - 1];
            long elapsed = Math.floorMod(millis - startedAt, (long) loop);
            for (int index = 0; index < ends.length; index++) {
                if (elapsed < ends[index]) {
                    return current.get(index);
                }
            }
            return current.getLast();
        }

        /** Has {@code refreshChat} run once this picture has loaded or failed. */
        public void notifyWhenSettled(Runnable refreshChat) {
            if (state == State.LOADING && refreshChat != null) {
                waitingViews.add(refreshChat);
            }
        }

        private void refreshWaitingViews() {
            List<Runnable> views = List.copyOf(waitingViews);
            waitingViews.clear();
            views.forEach(Runnable::run);
        }

        private void upload(Frames loaded) {
            List<Identifier> uploaded = new ArrayList<>(loaded.images().size());
            int[] ends = new int[loaded.images().size()];
            long uploadedBytes = 0;
            int elapsed = 0;
            for (int index = 0; index < ends.length; index++) {
                NativeImage pixels = loaded.images().get(index);
                Identifier textureId = Identifier.fromNamespaceAndPath("seq", "bridge_image/" + id + "/" + index);
                Minecraft.getInstance()
                        .getTextureManager()
                        .register(textureId, new PictureTexture(() -> "Sequoia bridge image " + key, pixels));
                uploaded.add(textureId);
                uploadedBytes += 4L * pixels.getWidth() * pixels.getHeight();
                elapsed += Math.max(1, loaded.delays()[index]);
                ends[index] = elapsed;
                // Tracked as it goes, so a failure part way still releases what went up.
                frames = List.copyOf(uploaded);
            }
            frameEnds = ends;
            sourceWidth = loaded.sourceWidth();
            sourceHeight = loaded.sourceHeight();
            bytes = uploadedBytes;
            startedAt = Util.getMillis();
        }

        private void release() {
            frames.forEach(Minecraft.getInstance().getTextureManager()::release);
            frames = List.of();
            frameEnds = new int[0];
            state = State.RELEASED;
        }
    }

    /**
     * A picture frame on the graphics card. Unlike a {@code DynamicTexture}, it keeps
     * no copy of its pixels, which a picture never needs again once uploaded, and
     * which would double what a long GIF takes. Sampled linearly, so a picture drawn a
     * little off its decoded size stays smooth rather than blocky.
     */
    private static final class PictureTexture extends AbstractTexture {
        PictureTexture(Supplier<String> label, NativeImage pixels) {
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(
                    label,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8,
                    pixels.getWidth(),
                    pixels.getHeight(),
                    1,
                    1);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            textureView = device.createTextureView(texture);
            device.createCommandEncoder().writeToTexture(texture, pixels);
        }
    }
}
