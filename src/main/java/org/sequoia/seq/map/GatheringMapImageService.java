package org.sequoia.seq.map;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.BuildConfig;
import org.sequoia.seq.network.ClientVersion;

public final class GatheringMapImageService {
    private static final String FALLBACK_MAP_RESOURCE = "assets/seq/textures/map/wynn-map.png";
    private static final String MANIFEST_PATH = "/assets/gathering-map/manifest.json";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration TILE_RETRY_BASE_DELAY = Duration.ofSeconds(2);
    private static final int TILE_DOWNLOAD_CONCURRENCY = 2;
    private static final Path CACHE_DIR = FabricLoader.getInstance()
            .getGameDir()
            .resolve("config")
            .resolve("sequoia")
            .resolve("cache")
            .resolve("gathering-map");
    private static final Path MANIFEST_CACHE_PATH = CACHE_DIR.resolve("manifest.json");
    private static final Path SINGLE_CACHE_PATH = CACHE_DIR.resolve("wynn-map-hq.jpg");

    private static GatheringMapImageService instance;

    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "seq-gathering-map-image");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(TILE_DOWNLOAD_CONCURRENCY, runnable -> {
        Thread thread = new Thread(runnable, "seq-gathering-map-http");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .executor(httpExecutor)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final Gson gson = new Gson();
    private final Map<TileKey, CompletableFuture<Void>> tileDownloads = new ConcurrentHashMap<>();
    private final Map<TileKey, Long> tileRetryAfterMs = new ConcurrentHashMap<>();

    private volatile byte[] imageBytes;
    private volatile Source imageSource = Source.NONE;
    private volatile String hqStatus = "not requested";
    private volatile String tileStatus = "not requested";
    private volatile long version;
    private volatile Manifest manifest;
    private volatile boolean loadRequested;

    public static synchronized GatheringMapImageService getInstance() {
        if (instance == null) {
            instance = new GatheringMapImageService();
        }
        return instance;
    }

    private GatheringMapImageService() {}

    public void requestLoad() {
        if (loadRequested) {
            return;
        }
        loadRequested = true;
        CompletableFuture.runAsync(this::loadCacheThenRefresh, loadExecutor);
    }

    public byte[] imageBytes() {
        byte[] current = imageBytes;
        if (current != null) {
            return current;
        }
        byte[] fallback = loadFallbackBytes();
        publish(fallback, Source.FALLBACK);
        return fallback;
    }

    public long version() {
        return version;
    }

    public Source imageSource() {
        return imageSource;
    }

    public String hqStatus() {
        String tiles = tileStatus;
        if (tiles == null || tiles.isBlank() || "not requested".equals(tiles)) {
            return hqStatus;
        }
        return hqStatus + " | tiles: " + tiles;
    }

    public String hqMapUrl() {
        try {
            return resolveAssetUri(MANIFEST_PATH).toString();
        } catch (IllegalArgumentException exception) {
            return "invalid";
        }
    }

    public Optional<Manifest> manifest() {
        return Optional.ofNullable(manifest);
    }

    public Optional<TileSet> tileSet() {
        Manifest current = manifest;
        if (current == null || current.tiles() == null) {
            return Optional.empty();
        }
        return Optional.of(current.tiles());
    }

    public byte[] cachedTileBytes(TileKey key) {
        Manifest current = manifest;
        if (current == null || current.tiles() == null || key == null) {
            return null;
        }
        Path path = tileCachePath(current, key);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            byte[] cached = Files.readAllBytes(path);
            if (matchesSha256(cached, current.tiles().sha256().get(key.id()))) {
                return cached;
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[GatheringMap] Failed to read cached tile {}.", key.id(), exception);
        }
        return null;
    }

    public void requestTiles(List<TileKey> visibleTiles, List<TileKey> prefetchTiles) {
        Manifest current = manifest;
        if (current == null || current.tiles() == null) {
            return;
        }
        for (TileKey key : visibleTiles) {
            requestTile(current, key, true);
        }
        for (TileKey key : prefetchTiles) {
            requestTile(current, key, false);
        }
    }

    private void loadCacheThenRefresh() {
        loadCachedManifest();
        loadCachedSingleImage();
        refreshManifestAndSingleImage();
    }

    private void loadCachedManifest() {
        if (!Files.isRegularFile(MANIFEST_CACHE_PATH)) {
            return;
        }
        try {
            Manifest cachedManifest = gson.fromJson(Files.readString(MANIFEST_CACHE_PATH), Manifest.class);
            if (cachedManifest != null) {
                manifest = cachedManifest;
            }
        } catch (IOException | RuntimeException exception) {
            hqStatus = "cached manifest failed";
            SeqClient.LOGGER.warn("[GatheringMap] Failed to read cached manifest.", exception);
        }
    }

    private void loadCachedSingleImage() {
        Manifest current = manifest;
        if (current == null || current.single() == null || !Files.isRegularFile(SINGLE_CACHE_PATH)) {
            return;
        }
        try {
            byte[] cached = Files.readAllBytes(SINGLE_CACHE_PATH);
            if (validateSingleImage(current, cached)) {
                publish(cached, Source.CACHED_HQ);
                hqStatus = "using cached HQ, refreshing";
            }
        } catch (IOException exception) {
            hqStatus = "cache read failed";
            SeqClient.LOGGER.warn("[GatheringMap] Failed to read cached HQ map image.", exception);
        }
    }

    private void refreshManifestAndSingleImage() {
        URI manifestUri;
        try {
            manifestUri = resolveAssetUri(MANIFEST_PATH);
        } catch (IllegalArgumentException exception) {
            hqStatus = "invalid manifest URL";
            SeqClient.LOGGER.warn("[GatheringMap] Invalid manifest URL for backend {}: {}", BuildConfig.API_URL, exception);
            return;
        }

        hqStatus = "refreshing manifest";
        try {
            byte[] manifestBytes = download(manifestUri, "manifest");
            Manifest remoteManifest = gson.fromJson(new String(manifestBytes, java.nio.charset.StandardCharsets.UTF_8), Manifest.class);
            if (remoteManifest == null || remoteManifest.single() == null) {
                hqStatus = "manifest invalid";
                return;
            }
            manifest = remoteManifest;
            writeCache(MANIFEST_CACHE_PATH, manifestBytes);
            refreshSingleImage(remoteManifest);
        } catch (HttpConnectTimeoutException exception) {
            hqStatus = "manifest connect timeout";
            SeqClient.LOGGER.warn("[GatheringMap] Manifest connection timed out from {}", manifestUri);
        } catch (HttpTimeoutException exception) {
            hqStatus = "manifest timeout";
            SeqClient.LOGGER.warn("[GatheringMap] Manifest download timed out from {}", manifestUri);
        } catch (IOException exception) {
            hqStatus = "manifest failed: " + exception.getClass().getSimpleName();
            SeqClient.LOGGER.warn("[GatheringMap] Failed to download gathering map manifest.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            hqStatus = "manifest interrupted";
            SeqClient.LOGGER.warn("[GatheringMap] Manifest download interrupted.", exception);
        } catch (RuntimeException exception) {
            hqStatus = "manifest parse failed";
            SeqClient.LOGGER.warn("[GatheringMap] Failed to parse gathering map manifest.", exception);
        }
    }

    private void refreshSingleImage(Manifest remoteManifest) throws IOException, InterruptedException {
        if (Files.isRegularFile(SINGLE_CACHE_PATH)) {
            byte[] cached = Files.readAllBytes(SINGLE_CACHE_PATH);
            if (validateSingleImage(remoteManifest, cached)) {
                publish(cached, Source.CACHED_HQ);
                hqStatus = "cached HQ current";
                return;
            }
        }

        URI imageUri = resolveAssetUri(remoteManifest.single().url());
        hqStatus = "downloading HQ";
        byte[] downloaded = download(imageUri, "HQ");
        if (!validateSingleImage(remoteManifest, downloaded)) {
            hqStatus = "checksum failed";
            SeqClient.LOGGER.warn("[GatheringMap] HQ map checksum/size validation failed from {}", imageUri);
            return;
        }
        writeCache(SINGLE_CACHE_PATH, downloaded);
        publish(downloaded, Source.CACHED_HQ);
        hqStatus = "downloaded HQ (" + formatBytes(downloaded.length) + ")";
    }

    private void requestTile(Manifest current, TileKey key, boolean visible) {
        if (!isValidTile(current.tiles(), key)) {
            return;
        }
        if (cachedTileBytes(key) != null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long retryAfter = tileRetryAfterMs.get(key);
        if (retryAfter != null && retryAfter > now) {
            return;
        }
        tileDownloads.computeIfAbsent(key, ignored -> CompletableFuture.runAsync(() -> downloadTile(current, key, visible), httpExecutor)
                .whenComplete((ignoredResult, ignoredFailure) -> tileDownloads.remove(key)));
    }

    private void downloadTile(Manifest current, TileKey key, boolean visible) {
        URI tileUri = resolveAssetUri(current.tiles().urlTemplate()
                .replace("{x}", String.valueOf(key.x()))
                .replace("{y}", String.valueOf(key.y())));
        try {
            tileStatus = (visible ? "downloading visible " : "prefetching ") + key.id();
            byte[] downloaded = download(tileUri, "tile " + key.id());
            if (!matchesSha256(downloaded, current.tiles().sha256().get(key.id()))) {
                tileStatus = "checksum failed " + key.id();
                scheduleTileRetry(key);
                return;
            }
            writeCache(tileCachePath(current, key), downloaded);
            tileRetryAfterMs.remove(key);
            tileStatus = "cached " + key.id();
            version++;
        } catch (IOException exception) {
            tileStatus = "failed " + key.id() + ": " + exception.getClass().getSimpleName();
            scheduleTileRetry(key);
            SeqClient.LOGGER.warn("[GatheringMap] Failed to download tile {}.", key.id(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            tileStatus = "interrupted " + key.id();
            scheduleTileRetry(key);
        }
    }

    private byte[] download(URI uri, String label) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = httpClient.send(downloadRequest(uri), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new IOException(label + " download returned HTTP " + response.statusCode());
        }
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        return readDownloadBody(response.body(), contentLength, label);
    }

    private static HttpRequest downloadRequest(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", "Sequoia-GatheringMap")
                .header(ClientVersion.MOD_VERSION_HEADER, ClientVersion.resolveInstalledVersion())
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
    }

    private static URI resolveAssetUri(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            throw new IllegalArgumentException("blank asset path");
        }
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return URI.create(pathOrUrl);
        }
        String path = pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl;
        return URI.create(resolveAssetBaseUrl(BuildConfig.API_URL) + path);
    }

    private static String resolveAssetBaseUrl(String apiBaseUrl) {
        String normalized = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/api")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private byte[] readDownloadBody(InputStream input, long contentLength, String label) throws IOException {
        try (input) {
            int initialSize = contentLength > 0 && contentLength <= Integer.MAX_VALUE ? (int) contentLength : 8192;
            ByteArrayOutputStream output = new ByteArrayOutputStream(initialSize);
            byte[] buffer = new byte[64 * 1024];
            long downloaded = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                hqStatus = downloadProgressStatus(label, downloaded, contentLength);
            }
            return output.toByteArray();
        }
    }

    private static String downloadProgressStatus(String label, long downloaded, long contentLength) {
        if (contentLength > 0) {
            long percent = Math.min(100, Math.max(0, downloaded * 100 / contentLength));
            return "downloading " + label + " " + percent + "% (" + formatBytes(downloaded) + "/" + formatBytes(contentLength) + ")";
        }
        return "downloading " + label + " " + formatBytes(downloaded);
    }

    private static String formatBytes(long bytes) {
        double mib = bytes / (1024.0 * 1024.0);
        if (mib >= 1.0) {
            return String.format(Locale.ROOT, "%.1f MB", mib);
        }
        double kib = bytes / 1024.0;
        return String.format(Locale.ROOT, "%.0f KB", kib);
    }

    private boolean validateSingleImage(Manifest current, byte[] bytes) {
        SingleImage single = current.single();
        return bytes != null
                && bytes.length > 0
                && (single.size() <= 0 || bytes.length == single.size())
                && matchesSha256(bytes, single.sha256());
    }

    private static boolean matchesSha256(byte[] bytes, String expected) {
        return expected == null || expected.isBlank() || expected.equalsIgnoreCase(sha256(bytes));
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static boolean isValidTile(TileSet tileSet, TileKey key) {
        return key.x() >= 0
                && key.y() >= 0
                && key.x() < tileSet.columns()
                && key.y() < tileSet.rows()
                && tileSet.sha256().containsKey(key.id());
    }

    private static Path tileCachePath(Manifest current, TileKey key) {
        return CACHE_DIR.resolve(safePathSegment(current.version())).resolve("main").resolve(key.id() + ".jpg");
    }

    private static String safePathSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void scheduleTileRetry(TileKey key) {
        long delayMs = TILE_RETRY_BASE_DELAY.toMillis() + Math.floorMod(Objects.hash(key), 1_000);
        tileRetryAfterMs.put(key, System.currentTimeMillis() + delayMs);
    }

    private static void writeCache(Path path, byte[] downloaded) throws IOException {
        Files.createDirectories(path.getParent());
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tempPath, downloaded);
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private byte[] loadFallbackBytes() {
        try (InputStream input = GatheringMapImageService.class.getClassLoader()
                .getResourceAsStream(FALLBACK_MAP_RESOURCE)) {
            if (input == null) {
                SeqClient.LOGGER.warn("[GatheringMap] Missing fallback map image asset: {}", FALLBACK_MAP_RESOURCE);
                return new byte[0];
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[GatheringMap] Failed to read fallback map image asset.", exception);
            return new byte[0];
        }
    }

    private synchronized void publish(byte[] nextBytes, Source source) {
        if (nextBytes == null || nextBytes.length == 0) {
            return;
        }
        if (java.util.Arrays.equals(imageBytes, nextBytes)) {
            imageSource = source;
            return;
        }
        imageBytes = nextBytes;
        imageSource = source;
        version++;
    }

    public enum Source {
        NONE,
        FALLBACK,
        CACHED_HQ
    }

    public record Manifest(String version, String preferredMode, WorldBounds worldBounds, SingleImage single, TileSet tiles) {}

    public record WorldBounds(double minX, double maxX, double minZ, double maxZ) {}

    public record SingleImage(String url, String format, int width, int height, String sha256, long size) {}

    public record TileSet(
            String urlTemplate,
            String format,
            int width,
            int height,
            int tileSize,
            int columns,
            int rows,
            Map<String, String> sha256) {
        public TileSet {
            sha256 = sha256 == null ? Map.of() : Map.copyOf(sha256);
        }
    }

    public record TileKey(int x, int y) {
        public String id() {
            return x + "_" + y;
        }
    }
}
