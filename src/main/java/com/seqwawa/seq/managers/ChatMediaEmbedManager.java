package com.seqwawa.seq.managers;

import static com.seqwawa.seq.client.SeqClient.mc;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Creates compact, clickable media cards above Minecraft chat for URLs posted in
 * native guild chat or received through the Discord bridge.
 */
public final class ChatMediaEmbedManager implements AutoCloseable {
    private static final ChatMediaEmbedManager INSTANCE = new ChatMediaEmbedManager();
    private static final Pattern META_TAG = Pattern.compile("(?is)<meta\\s+([^>]*?)>");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))");
    private static final Pattern TITLE_TAG = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]+>");
    private static final int MAX_LINKS_PER_MESSAGE = 2;
    private static final int MAX_RESOLVED_MEDIA_URLS = 4;
    private static final int MAX_ENTRIES = 6;
    private static final int MAX_VISIBLE_ENTRIES = 3;
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_HTML_BYTES = 512 * 1024;
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final long DEFAULT_LIFETIME_MS = 5_000L;
    private static final long PENDING_LIFETIME_MS = 30_000L;
    private static final long DEDUPE_WINDOW_MS = 3_000L;
    private static final float CARD_WIDTH = 302f;
    private static final float CARD_GAP = 6f;
    private static final float MEDIA_PADDING = 4f;
    private static final float USERNAME_BAR_HEIGHT = 22f;
    private static final float MIN_MEDIA_HEIGHT = 72f;
    private static final float MAX_MEDIA_HEIGHT = 150f;
    private static final Color CARD_BACKGROUND = new Color(12, 16, 24, 230);
    private static final Color CARD_BORDER = new Color(255, 255, 255, 30);
    private static final Color USERNAME_COLOR = new Color(242, 246, 255);

    private final Object lock = new Object();
    private final List<EmbedEntry> entries = new ArrayList<>();
    private final ExecutorService loader;
    private final HttpClient httpClient;
    private volatile List<CardBounds> clickableCards = List.of();
    private volatile boolean closed;

    private ChatMediaEmbedManager() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Sequoia chat media loader");
            thread.setDaemon(true);
            return thread;
        };
        loader = new ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(12),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public static ChatMediaEmbedManager getInstance() {
        return INSTANCE;
    }

    public void observe(Source source, String sender, String message) {
        observe(source, sender, message, List.of());
    }

    public void observe(Source source, String sender, String message, List<String> resolvedMediaUrls) {
        if (closed || !isEnabled()) {
            return;
        }

        List<URI> resolvedLinks = new ArrayList<>();
        if (resolvedMediaUrls != null) {
            for (String mediaUrl : resolvedMediaUrls) {
                for (URI uri : ChatLinkExtractor.extract(mediaUrl, 1)) {
                    if (!resolvedLinks.contains(uri) && resolvedLinks.size() < MAX_RESOLVED_MEDIA_URLS) {
                        resolvedLinks.add(uri);
                    }
                }
            }
        }
        List<URI> messageLinks = ChatLinkExtractor.extract(message, MAX_LINKS_PER_MESSAGE);
        List<URI> links = messageLinks.isEmpty()
                ? resolvedLinks.stream().limit(MAX_LINKS_PER_MESSAGE).toList()
                : messageLinks;

        long now = System.currentTimeMillis();
        for (URI uri : links) {
            EmbedEntry entry;
            List<UiImage> evictedImages = List.of();
            synchronized (lock) {
                boolean duplicate = entries.stream()
                        .anyMatch(existing -> existing.uri.equals(uri) && now - existing.createdAtMs < DEDUPE_WINDOW_MS);
                if (duplicate) {
                    continue;
                }
                entry = new EmbedEntry(uri, fallbackCandidates(uri, resolvedLinks), source, clean(sender, 40), now);
                entries.add(entry);
                if (entries.size() > MAX_ENTRIES) {
                    evictedImages = entries.removeFirst().images;
                }
            }
            deleteImages(evictedImages);
            try {
                loader.execute(() -> load(entry));
            } catch (RejectedExecutionException ignored) {
                updateText(entry, host(uri), "Open shared link");
            }
        }
    }

    public void render(UiCanvas canvas) {
        if (!isEnabled()) {
            clear();
            return;
        }

        boolean focused = mc.screen instanceof ChatScreen;
        long now = System.currentTimeMillis();
        removeExpired(now, configuredLifetimeMs());
        List<EmbedEntry> candidates;
        synchronized (lock) {
            candidates = List.copyOf(entries);
        }
        List<EmbedEntry> loadedEntries = new ArrayList<>();
        for (EmbedEntry entry : candidates) {
            uploadPendingPreview(entry);
            if (!entry.images.isEmpty()) {
                loadedEntries.add(entry);
            }
        }
        int from = Math.max(0, loadedEntries.size() - MAX_VISIBLE_ENTRIES);
        List<EmbedEntry> snapshot = List.copyOf(loadedEntries.subList(from, loadedEntries.size()));
        if (snapshot.isEmpty()) {
            clickableCards = List.of();
            return;
        }

        float cardWidth = Math.min(CARD_WIDTH, canvas.metrics().width() - 16f);
        float guiToUi = (float) (canvas.metrics().minecraftGuiScale() / canvas.metrics().pixelRatio());
        double chatHeightSetting = focused
                ? mc.options.chatHeightFocused().get()
                : mc.options.chatHeightUnfocused().get();
        float chatHeight = (float) (ChatComponent.getHeight(chatHeightSetting)
                * mc.options.chatScale().get()
                * guiToUi);
        float bottom = canvas.metrics().height() - 22f * guiToUi - chatHeight - 8f;
        float x = Math.max(8f, 4f * guiToUi);
        List<CardBounds> bounds = new ArrayList<>(snapshot.size());

        for (int index = snapshot.size() - 1; index >= 0; index--) {
            EmbedEntry entry = snapshot.get(index);
            float height = cardHeight(entry, cardWidth);
            bottom -= height;
            if (bottom < 8f) {
                break;
            }
            drawCard(canvas, entry, x, bottom, cardWidth, height, now);
            bounds.add(new CardBounds(x, bottom, cardWidth, height, entry.uri));
            bottom -= CARD_GAP;
        }
        clickableCards = List.copyOf(bounds);
    }

    /** Returns the card target at Sequoia UI coordinates, or {@code null}. */
    public URI linkAt(float x, float y) {
        if (!isEnabled()) {
            return null;
        }
        for (CardBounds bounds : clickableCards) {
            if (x >= bounds.x && x <= bounds.x + bounds.width
                    && y >= bounds.y && y <= bounds.y + bounds.height) {
                return bounds.uri;
            }
        }
        return null;
    }

    private void drawCard(
            UiCanvas canvas, EmbedEntry entry, float x, float y, float width, float height, long now) {
        String font = SeqClient.getFontManager() == null ? "mc" : SeqClient.getFontManager().getSelectedFont();
        Color accent = entry.source == Source.DISCORD
                ? new Color(88, 101, 242)
                : new Color(85, 255, 255);
        canvas.fillRoundedRect(x, y, width, height, 5f, CARD_BACKGROUND);
        canvas.strokeRect(x, y, width, height, 0.7f, CARD_BORDER);
        canvas.fillRoundedRect(x, y, 3f, height, 2f, accent);

        UiImage image = activeFrame(entry, now);
        if (image == null) {
            return;
        }
        float boxX = x + MEDIA_PADDING;
        float boxY = y + MEDIA_PADDING;
        float boxWidth = width - MEDIA_PADDING * 2f;
        float boxHeight = height - USERNAME_BAR_HEIGHT - MEDIA_PADDING * 2f;
        float scale = Math.min(boxWidth / image.width(), boxHeight / image.height());
        float drawWidth = image.width() * scale;
        float drawHeight = image.height() * scale;
        canvas.drawImage(
                image,
                boxX + (boxWidth - drawWidth) / 2f,
                boxY + (boxHeight - drawHeight) / 2f,
                drawWidth,
                drawHeight,
                1f);

        if (!entry.sender.isBlank()) {
            float usernameY = boxY + boxHeight + 6f;
            canvas.drawText(ellipsize(entry.sender, font, 9f, width - 20f), x + 10f, usernameY,
                    textStyle(font, 9f, USERNAME_COLOR));
        }
    }

    private static float cardHeight(EmbedEntry entry, float width) {
        UiImage image = entry.images.getFirst();
        float mediaWidth = width - MEDIA_PADDING * 2f;
        float scaledHeight = mediaWidth * image.height() / image.width();
        float mediaHeight = Math.max(MIN_MEDIA_HEIGHT, Math.min(MAX_MEDIA_HEIGHT, scaledHeight));
        return mediaHeight + USERNAME_BAR_HEIGHT + MEDIA_PADDING * 2f;
    }

    private static UiCanvas.TextStyle textStyle(String font, float size, Color color) {
        return new UiCanvas.TextStyle(
                font, size, color, UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.TOP);
    }

    private static String ellipsize(String input, String font, float size, float maxWidth) {
        String value = input == null ? "" : input;
        if (UiRenderer.measureText(value, font, size).width() <= maxWidth) {
            return value;
        }
        String suffix = "…";
        int low = 0;
        int high = value.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (UiRenderer.measureText(value.substring(0, middle) + suffix, font, size).width() <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return value.substring(0, low) + suffix;
    }

    private static UiImage activeFrame(EmbedEntry entry, long now) {
        List<UiImage> images = entry.images;
        if (images.isEmpty()) {
            return null;
        }
        if (images.size() == 1 || entry.frameDurationMs <= 0) {
            return images.getFirst();
        }
        long animationStart = entry.visibleSinceMs > 0 ? entry.visibleSinceMs : entry.createdAtMs;
        return images.get(frameIndexAt(entry.frameDelaysMs, entry.frameDurationMs, now - animationStart));
    }

    static int frameIndexAt(List<Integer> delaysMs, int totalDurationMs, long elapsedMs) {
        if (delaysMs == null || delaysMs.isEmpty() || totalDurationMs <= 0) {
            return 0;
        }
        long position = Math.floorMod(elapsedMs, totalDurationMs);
        int elapsed = 0;
        for (int index = 0; index < delaysMs.size(); index++) {
            elapsed += delaysMs.get(index);
            if (position < elapsed) {
                return index;
            }
        }
        return delaysMs.size() - 1;
    }

    private void load(EmbedEntry entry) {
        Exception lastFailure = null;
        for (URI candidate : entry.candidateUris) {
            try {
                LoadedPreview preview = loadPreview(candidate);
                if (preview == null) {
                    continue;
                }
                queueUpload(entry, preview);
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                SeqClient.LOGGER.debug("Unable to load chat media preview candidate {}", candidate, exception);
            }
        }
        updateText(
                entry,
                host(entry.uri),
                lastFailure == null ? "Preview unavailable — open link" : previewFailureMessage(lastFailure));
    }

    private LoadedPreview loadPreview(URI uri) throws Exception {
        HttpPayload payload = fetch(uri, MAX_IMAGE_BYTES, "text/html,image/*;q=0.9,*/*;q=0.5");
        String contentType = payload.contentType();
        if (isImage(contentType) || looksLikeImage(payload.body())) {
            String title = fileName(payload.finalUri());
            return decodeImage(payload.body(), contentType, title, "Image", payload.finalUri());
        }

        if (!isHtml(contentType, payload.body())) {
            return null;
        }

        byte[] htmlBytes = payload.body().length > MAX_HTML_BYTES
                ? java.util.Arrays.copyOf(payload.body(), MAX_HTML_BYTES)
                : payload.body();
        HtmlMetadata metadata = parseHtml(new String(htmlBytes, StandardCharsets.UTF_8), payload.finalUri());
        if (metadata.image() == null) {
            return null;
        }

        HttpPayload image = fetch(metadata.image(), MAX_IMAGE_BYTES, "image/*");
        if (!isImage(image.contentType()) && !looksLikeImage(image.body())) {
            return null;
        }
        return decodeImage(
                image.body(), image.contentType(), metadata.title(), metadata.description(), payload.finalUri());
    }

    static List<URI> fallbackCandidates(URI original, List<URI> resolvedMedia) {
        List<URI> candidates = new ArrayList<>();
        candidates.add(original);
        if (resolvedMedia != null) {
            for (URI candidate : resolvedMedia) {
                if (candidate != null && !candidates.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        return List.copyOf(candidates);
    }

    private LoadedPreview decodeImage(byte[] bytes, String contentType, String title, String description, URI page)
            throws IOException {
        if (isWebP(contentType, bytes)) {
            ChatWebPDecoder.DecodedWebP webp = ChatWebPDecoder.decode(bytes);
            return new LoadedPreview(title, description, page, webp.pngFrames(), webp.delaysMs());
        }
        if (isGif(contentType, bytes)) {
            ChatGifDecoder.DecodedGif gif = ChatGifDecoder.decode(bytes);
            return new LoadedPreview(title, description, page, gif.pngFrames(), gif.delaysMs());
        }
        return new LoadedPreview(
                title, description, page, List.of(ChatImageDecoder.decodeToPreviewPng(bytes)), List.of(1_000));
    }

    private void queueUpload(EmbedEntry entry, LoadedPreview preview) {
        if (!closed && contains(entry)) {
            entry.pendingPreview = preview;
        }
    }

    /** NanoVG image handles are created only while its render context is active. */
    private void uploadPendingPreview(EmbedEntry entry) {
        LoadedPreview preview = entry.pendingPreview;
        if (preview == null || !UiRenderer.isAvailable()) {
            return;
        }
        entry.pendingPreview = null;

        List<UiImage> images = new ArrayList<>(preview.frames().size());
        try {
            for (byte[] frame : preview.frames()) {
                UiImage image = UiRenderer.createImage(ByteBuffer.wrap(frame), false);
                if (image == null) {
                    throw new IllegalStateException("NanoVG rejected a decoded preview frame");
                }
                images.add(image);
            }
            deleteImages(entry.images);
            entry.title = clean(preview.title(), 120);
            entry.description = clean(preview.description(), 180);
            entry.domain = host(preview.page());
            entry.images = List.copyOf(images);
            entry.frameDelaysMs = List.copyOf(preview.delaysMs());
            entry.frameDurationMs = preview.delaysMs().stream().mapToInt(Integer::intValue).sum();
            entry.visibleSinceMs = System.currentTimeMillis();
        } catch (RuntimeException exception) {
            deleteImages(images);
            updateText(entry, preview.title(), "Preview could not be rendered");
            SeqClient.LOGGER.warn("Unable to upload chat media preview for {}", entry.uri, exception);
        }
    }

    private HttpPayload fetch(URI startingUri, int maxBytes, String accept) throws Exception {
        URI uri = ChatMediaUrlResolver.preferredMediaUri(startingUri);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            SafeRemoteUrl.requirePublicHttpUrl(uri);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", accept)
                    .header("Accept-Encoding", "identity")
                    .header("User-Agent", ChatMediaUrlResolver.userAgent(uri))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                try (InputStream ignored = response.body()) {
                    String location = response.headers().firstValue("location")
                            .orElseThrow(() -> new IOException("Redirect has no location"));
                    uri = uri.resolve(location);
                    continue;
                }
            }
            if (status < 200 || status >= 300) {
                response.body().close();
                throw new IOException("Preview request returned HTTP " + status);
            }
            String contentType = response.headers().firstValue("content-type").orElse("")
                    .split(";", 2)[0]
                    .trim()
                    .toLowerCase(Locale.ROOT);
            int effectiveMaximum = contentType.equals("text/html") || contentType.equals("application/xhtml+xml")
                    ? Math.min(maxBytes, MAX_HTML_BYTES)
                    : maxBytes;
            long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (declaredLength > effectiveMaximum) {
                response.body().close();
                throw new IOException("Preview exceeds download size limit");
            }
            try (InputStream body = response.body()) {
                return new HttpPayload(uri, contentType, readBounded(body, effectiveMaximum));
            }
        }
        throw new IOException("Too many redirects while loading preview");
    }

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 16_384));
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maximum) {
                throw new IOException("Preview exceeds download size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static HtmlMetadata parseHtml(String html, URI page) {
        Map<String, String> metadata = new HashMap<>();
        Matcher tags = META_TAG.matcher(html);
        while (tags.find()) {
            Map<String, String> attributes = new HashMap<>();
            Matcher attribute = ATTRIBUTE.matcher(tags.group(1));
            while (attribute.find()) {
                String value = attribute.group(2) != null
                        ? attribute.group(2)
                        : attribute.group(3) != null ? attribute.group(3) : attribute.group(4);
                attributes.put(attribute.group(1).toLowerCase(Locale.ROOT), value);
            }
            String key = attributes.getOrDefault("property", attributes.get("name"));
            String value = attributes.get("content");
            if (key != null && value != null) {
                metadata.putIfAbsent(key.toLowerCase(Locale.ROOT), decodeHtml(value));
            }
        }

        String title = metadata.get("og:title");
        if (title == null) {
            Matcher titleTag = TITLE_TAG.matcher(html);
            title = titleTag.find() ? decodeHtml(HTML_TAG.matcher(titleTag.group(1)).replaceAll("")) : host(page);
        }
        String description = metadata.getOrDefault(
                "og:description", metadata.getOrDefault("description", "Open shared link"));
        URI image = null;
        String imageValue = metadata.getOrDefault("og:image:secure_url", metadata.get("og:image"));
        if (imageValue != null) {
            try {
                image = page.resolve(imageValue.trim());
            } catch (IllegalArgumentException ignored) {
                // Leave the page as a text-only card.
            }
        }
        return new HtmlMetadata(clean(title, 120), clean(description, 180), image);
    }

    private static String decodeHtml(String value) {
        String decoded = value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        Matcher numeric = Pattern.compile("&#(x?[0-9a-fA-F]+);").matcher(decoded);
        StringBuilder output = new StringBuilder();
        while (numeric.find()) {
            try {
                int radix = numeric.group(1).startsWith("x") ? 16 : 10;
                String digits = radix == 16 ? numeric.group(1).substring(1) : numeric.group(1);
                numeric.appendReplacement(output, Matcher.quoteReplacement(
                        Character.toString(Integer.parseInt(digits, radix))));
            } catch (IllegalArgumentException exception) {
                numeric.appendReplacement(output, Matcher.quoteReplacement(numeric.group()));
            }
        }
        numeric.appendTail(output);
        return output.toString();
    }

    private static boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/") && !contentType.equals("image/svg+xml");
    }

    private static boolean looksLikeImage(byte[] bytes) {
        return bytes.length >= 8 && ((bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G')
                || (bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8)
                || (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F')
                || isWebP("", bytes));
    }

    private static boolean isGif(String contentType, byte[] bytes) {
        return "image/gif".equals(contentType)
                || (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F');
    }

    private static boolean isWebP(String contentType, byte[] bytes) {
        return "image/webp".equals(contentType)
                || (bytes.length >= 12
                        && bytes[0] == 'R'
                        && bytes[1] == 'I'
                        && bytes[2] == 'F'
                        && bytes[3] == 'F'
                        && bytes[8] == 'W'
                        && bytes[9] == 'E'
                        && bytes[10] == 'B'
                        && bytes[11] == 'P');
    }

    private static String previewFailureMessage(Exception exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("HTTP 404")) {
            return "Media is no longer available";
        }
        if (message != null && message.contains("size limit")) {
            return "Media is too large to preview";
        }
        return "Preview unavailable — open link";
    }

    private static boolean isHtml(String contentType, byte[] bytes) {
        if (contentType.equals("text/html") || contentType.equals("application/xhtml+xml")) {
            return true;
        }
        String prefix = new String(bytes, 0, Math.min(bytes.length, 128), StandardCharsets.US_ASCII)
                .stripLeading()
                .toLowerCase(Locale.ROOT);
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html");
    }

    private static String fileName(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/")) {
            return host(uri);
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.isBlank() ? host(uri) : name;
    }

    private static String host(URI uri) {
        return uri == null || uri.getHost() == null ? "Shared link" : uri.getHost();
    }

    private static String clean(String value, int maximumLength) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() <= maximumLength ? cleaned : cleaned.substring(0, maximumLength);
    }

    private boolean contains(EmbedEntry entry) {
        synchronized (lock) {
            return entries.contains(entry);
        }
    }

    private void updateText(EmbedEntry entry, String title, String description) {
        entry.title = clean(title, 120);
        entry.description = clean(description, 180);
    }

    private void removeExpired(long now, long lifetime) {
        List<UiImage> removed = new ArrayList<>();
        synchronized (lock) {
            entries.removeIf(entry -> {
                long expiryStart = entry.visibleSinceMs > 0 ? entry.visibleSinceMs : entry.createdAtMs;
                long effectiveLifetime = entry.visibleSinceMs > 0 ? lifetime : PENDING_LIFETIME_MS;
                if (now - expiryStart <= effectiveLifetime) {
                    return false;
                }
                removed.addAll(entry.images);
                return true;
            });
        }
        deleteImages(removed);
    }

    private void clear() {
        List<UiImage> images = new ArrayList<>();
        synchronized (lock) {
            for (EmbedEntry entry : entries) {
                images.addAll(entry.images);
            }
            entries.clear();
        }
        clickableCards = List.of();
        deleteImages(images);
    }

    private static void deleteImages(List<UiImage> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        Runnable deletion = () -> images.forEach(UiRenderer::deleteImage);
        if (mc.isSameThread()) {
            deletion.run();
        } else {
            mc.execute(deletion);
        }
    }

    private static boolean isEnabled() {
        Setting.BooleanSetting setting = SeqClient.getShowChatMediaEmbedsSetting();
        Setting.IntSetting duration = SeqClient.getChatMediaEmbedDurationSetting();
        return setting != null && setting.getValue() && (duration == null || duration.getValue() > 0);
    }

    private static long configuredLifetimeMs() {
        Setting.IntSetting setting = SeqClient.getChatMediaEmbedDurationSetting();
        return setting == null ? DEFAULT_LIFETIME_MS : setting.getValue() * 1_000L;
    }

    @Override
    public void close() {
        closed = true;
        clear();
        loader.shutdownNow();
    }

    public enum Source {
        IN_GAME,
        DISCORD
    }

    private static final class EmbedEntry {
        private final URI uri;
        private final List<URI> candidateUris;
        private final Source source;
        private final String sender;
        private final long createdAtMs;
        private volatile String title;
        private volatile String description;
        private volatile String domain;
        private volatile List<UiImage> images = List.of();
        private volatile List<Integer> frameDelaysMs = List.of();
        private volatile int frameDurationMs;
        private volatile long visibleSinceMs;
        private volatile LoadedPreview pendingPreview;

        private EmbedEntry(URI uri, List<URI> candidateUris, Source source, String sender, long createdAtMs) {
            this.uri = uri;
            this.candidateUris = candidateUris;
            this.source = source;
            this.sender = sender;
            this.createdAtMs = createdAtMs;
            this.title = host(uri);
            this.description = "Loading link preview…";
            this.domain = host(uri);
        }
    }

    private record CardBounds(float x, float y, float width, float height, URI uri) {
    }

    private record HttpPayload(URI finalUri, String contentType, byte[] body) {
    }

    private record HtmlMetadata(String title, String description, URI image) {
    }

    private record LoadedPreview(
            String title, String description, URI page, List<byte[]> frames, List<Integer> delaysMs) {
    }
}
