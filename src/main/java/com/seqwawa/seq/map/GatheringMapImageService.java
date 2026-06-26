package com.seqwawa.seq.map;

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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.BuildConfig;
import com.seqwawa.seq.network.ClientVersion;

public final class GatheringMapImageService {
    private static final String FALLBACK_MAP_RESOURCE = "assets/seq/textures/map/wynn-map.png";
    private static final String MANIFEST_PATH = "/assets/gathering-map/manifest.json";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration TILE_RETRY_BASE_DELAY = Duration.ofSeconds(2);
    private static final int TILE_DOWNLOAD_CONCURRENCY = 2;
    private static final int HTTP_CLIENT_THREADS = 4;
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
    private final ExecutorService tileExecutor = Executors.newFixedThreadPool(TILE_DOWNLOAD_CONCURRENCY, runnable -> {
        Thread thread = new Thread(runnable, "seq-gathering-map-tile");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(HTTP_CLIENT_THREADS, runnable -> {
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
    private final Set<TileKey> validatedCachedTiles = ConcurrentHashMap.newKeySet();
    private final Map<TileKey, byte[]> readyTileBytes = new ConcurrentHashMap<>();

    private volatile byte[] imageBytes;
    private volatile Source imageSource = Source.NONE;
    private volatile String hqStatus = "not requested";
    private volatile String tileStatus = "not requested";
    private volatile long version;
    private volatile long tileVersion;
    private volatile Manifest manifest;
    private volatile boolean loadRequested;
    private volatile String validatedTileManifestVersion = "";

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

    public long tileVersion() {
        return tileVersion;
    }

    public Source imageSource() {
        return imageSource;
    }

    public String hqStatus() {
        String tiles = tileStatus;
        if (tiles == null || tiles.isBlank() || "not requested".equals(tiles)) {
            return hqStatus;
        }
        return hqStatus + " | tiles: " + tiles + " | active=" + tileDownloads.size();
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

    public String cacheStatus() {
        long bytes = cacheSizeBytes();
        return "Map image cache: " + formatBytes(bytes) + " at " + CACHE_DIR;
    }

    public synchronized String clearCache() {
        int deletedFiles = deleteCacheDirectory();
        for (CompletableFuture<Void> download : tileDownloads.values()) {
            download.cancel(true);
        }
        tileDownloads.clear();
        tileRetryAfterMs.clear();
        validatedCachedTiles.clear();
        readyTileBytes.clear();
        validatedTileManifestVersion = "";
        imageBytes = null;
        imageSource = Source.NONE;
        hqStatus = "cache cleared";
        tileStatus = "not requested";
        manifest = null;
        loadRequested = false;
        version++;
        tileVersion++;
        requestLoad();
        return "Map image cache cleared (" + deletedFiles + " files). Reload started.";
    }

    public byte[] cachedTileBytes(TileKey key) {
        Manifest current = manifest;
        if (current == null || current.tiles() == null || key == null) {
            return null;
        }
        ensureTileCacheVersion(current);
        byte[] ready = readyTileBytes.remove(key);
        if (ready != null) {
            return ready;
        }
        Path path = tileCachePath(current, key);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            byte[] cached = Files.readAllBytes(path);
            if (validatedCachedTiles.contains(key)
                    || matchesSha256(cached, current.tiles().sha256().get(key.id()))) {
                validatedCachedTiles.add(key);
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
        if (prefersTiles(current)) {
            publishTileModeUnderlay();
            hqStatus = "using tiled HQ, refreshing";
            return;
        }
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
        Manifest remoteManifest;
        try {
            byte[] manifestBytes = download(manifestUri, "manifest");
            remoteManifest = gson.fromJson(new String(manifestBytes, java.nio.charset.StandardCharsets.UTF_8), Manifest.class);
            if (remoteManifest == null) {
                hqStatus = "manifest invalid";
                debugMap("Manifest invalid from " + manifestUri + ": empty response.");
                return;
            }
            if (!prefersTiles(remoteManifest) && remoteManifest.single() == null) {
                hqStatus = "manifest invalid";
                debugMap("Manifest invalid from " + manifestUri + ": missing single image metadata.");
                return;
            }
            manifest = remoteManifest;
            writeCache(MANIFEST_CACHE_PATH, manifestBytes);
        } catch (HttpConnectTimeoutException exception) {
            hqStatus = "manifest connect timeout";
            debugMap("Manifest connection timed out from " + manifestUri);
            SeqClient.LOGGER.debug("[GatheringMap] Manifest connection timed out from {}", manifestUri);
            return;
        } catch (HttpTimeoutException exception) {
            hqStatus = "manifest timeout";
            debugMap("Manifest download timed out from " + manifestUri);
            SeqClient.LOGGER.debug("[GatheringMap] Manifest download timed out from {}", manifestUri);
            return;
        } catch (MapDownloadException exception) {
            hqStatus = exception.statusLine();
            debugMap(exception.statusLine());
            SeqClient.LOGGER.debug("[GatheringMap] {}", exception.statusLine());
            return;
        } catch (IOException exception) {
            hqStatus = "manifest failed: " + exception.getMessage();
            debugMap("Manifest failed from " + manifestUri + ": " + exception.getMessage());
            SeqClient.LOGGER.debug("[GatheringMap] Failed to download gathering map manifest.", exception);
            return;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            hqStatus = "manifest interrupted";
            debugMap("Manifest download interrupted from " + manifestUri);
            SeqClient.LOGGER.debug("[GatheringMap] Manifest download interrupted.", exception);
            return;
        } catch (RuntimeException exception) {
            hqStatus = "manifest parse failed";
            debugMap("Manifest parse failed from " + manifestUri + ": " + exception.getMessage());
            SeqClient.LOGGER.debug("[GatheringMap] Failed to parse gathering map manifest.", exception);
            return;
        }

        if (prefersTiles(remoteManifest)) {
            publishTileModeUnderlay();
            hqStatus = "tiles manifest current";
            return;
        }

        try {
            refreshSingleImage(remoteManifest);
        } catch (HttpConnectTimeoutException exception) {
            hqStatus = "HQ connect timeout";
            debugMap("HQ image connection timed out for " + remoteManifest.single().url());
            SeqClient.LOGGER.debug("[GatheringMap] HQ image connection timed out for {}", remoteManifest.single().url());
        } catch (HttpTimeoutException exception) {
            hqStatus = "HQ timeout";
            debugMap("HQ image download timed out for " + remoteManifest.single().url());
            SeqClient.LOGGER.debug("[GatheringMap] HQ image download timed out for {}", remoteManifest.single().url());
        } catch (MapDownloadException exception) {
            hqStatus = exception.statusLine();
            debugMap(exception.statusLine());
            SeqClient.LOGGER.debug("[GatheringMap] {}", exception.statusLine());
        } catch (IOException exception) {
            hqStatus = "HQ failed: " + exception.getMessage();
            debugMap("HQ image failed: " + exception.getMessage());
            SeqClient.LOGGER.debug("[GatheringMap] Failed to download HQ map image.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            hqStatus = "HQ interrupted";
            debugMap("HQ image download interrupted for " + remoteManifest.single().url());
            SeqClient.LOGGER.debug("[GatheringMap] HQ image download interrupted.", exception);
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
            String expected = remoteManifest.single().sha256();
            String actual = sha256(downloaded);
            debugMap("HQ checksum/size validation failed from " + imageUri
                    + " size="
                    + downloaded.length
                    + "/"
                    + remoteManifest.single().size()
                    + " sha256="
                    + actual
                    + "/"
                    + expected);
            SeqClient.LOGGER.debug(
                    "[GatheringMap] HQ map checksum/size validation failed from {} size={}/{} sha256={}/{}",
                    imageUri,
                    downloaded.length,
                    remoteManifest.single().size(),
                    actual,
                    expected);
            return;
        }
        writeCache(SINGLE_CACHE_PATH, downloaded);
        publish(downloaded, Source.CACHED_HQ);
        hqStatus = "downloaded HQ (" + formatBytes(downloaded.length) + ")";
    }

    private void publishTileModeUnderlay() {
        byte[] fallback = loadFallbackBytes();
        if (fallback.length > 0) {
            publish(fallback, Source.FALLBACK);
        }
    }

    private void requestTile(Manifest current, TileKey key, boolean visible) {
        if (!isValidTile(current.tiles(), key)) {
            return;
        }
        if (isCachedTile(current, key, visible)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long retryAfter = tileRetryAfterMs.get(key);
        if (retryAfter != null && retryAfter > now) {
            return;
        }
        tileDownloads.computeIfAbsent(key, ignored -> CompletableFuture.runAsync(() -> downloadTile(current, key, visible), tileExecutor)
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
                debugMap("Tile " + key.id() + " checksum failed from " + tileUri);
                scheduleTileRetry(key);
                return;
            }
            if (manifest != current) {
                return;
            }
            writeCache(tileCachePath(current, key), downloaded);
            ensureTileCacheVersion(current);
            validatedCachedTiles.add(key);
            if (visible) {
                readyTileBytes.put(key, downloaded);
            }
            tileRetryAfterMs.remove(key);
            tileStatus = "cached " + key.id();
            tileVersion++;
        } catch (IOException exception) {
            tileStatus = "failed " + key.id() + ": " + exception.getMessage();
            scheduleTileRetry(key);
            debugMap("Tile " + key.id() + " failed from " + tileUri + ": " + exception.getMessage());
            SeqClient.LOGGER.debug("[GatheringMap] Failed to download tile {}.", key.id(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            tileStatus = "interrupted " + key.id();
            scheduleTileRetry(key);
        }
    }

    private byte[] download(URI uri, String label) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = httpClient.send(downloadRequest(uri), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
            throw new MapDownloadException(label, uri, response.statusCode(), contentType);
        }
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        SeqClient.LOGGER.debug(
                "[GatheringMap] Downloading {} from {} contentLength={}",
                label,
                uri,
                contentLength < 0 ? "unknown" : formatBytes(contentLength));
        return readDownloadBody(response.body(), contentLength, label);
    }

    private static void debugMap(String message) {
        SeqClient.LOGGER.debug("[GatheringMap] {}", message);
        if (!GatheringMapSettings.getInstance().showDebugInfo()) {
            return;
        }
        SeqClient.mc.execute(() -> {
            if (SeqClient.mc.player != null) {
                SeqClient.mc.player.displayClientMessage(Component.literal("[GatheringMap] " + message), false);
            }
        });
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

    private static boolean prefersTiles(Manifest manifest) {
        return manifest != null && manifest.tiles() != null && "tiles".equalsIgnoreCase(manifest.preferredMode());
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

    private boolean isCachedTile(Manifest current, TileKey key, boolean retainBytes) {
        ensureTileCacheVersion(current);
        if (validatedCachedTiles.contains(key)) {
            return true;
        }

        Path path = tileCachePath(current, key);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            byte[] cached = Files.readAllBytes(path);
            if (!matchesSha256(cached, current.tiles().sha256().get(key.id()))) {
                return false;
            }
            validatedCachedTiles.add(key);
            if (retainBytes) {
                readyTileBytes.putIfAbsent(key, cached);
            }
            return true;
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[GatheringMap] Failed to validate cached tile {}.", key.id(), exception);
            return false;
        }
    }

    private synchronized void ensureTileCacheVersion(Manifest current) {
        String manifestVersion = current == null || current.version() == null ? "" : current.version();
        if (manifestVersion.equals(validatedTileManifestVersion)) {
            return;
        }
        validatedCachedTiles.clear();
        readyTileBytes.clear();
        validatedTileManifestVersion = manifestVersion;
        tileVersion++;
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

    private static long cacheSizeBytes() {
        if (!Files.exists(CACHE_DIR)) {
            return 0L;
        }
        try (Stream<Path> paths = Files.walk(CACHE_DIR)) {
            return paths.filter(Files::isRegularFile).mapToLong(GatheringMapImageService::fileSize).sum();
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[GatheringMap] Failed to calculate map cache size.", exception);
            return 0L;
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static int deleteCacheDirectory() {
        if (!Files.exists(CACHE_DIR)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(CACHE_DIR)) {
            return paths.sorted(Comparator.reverseOrder()).mapToInt(GatheringMapImageService::deleteCachePath).sum();
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[GatheringMap] Failed to clear map image cache.", exception);
            return 0;
        }
    }

    private static int deleteCachePath(Path path) {
        try {
            boolean wasFile = Files.isRegularFile(path);
            Files.deleteIfExists(path);
            return wasFile ? 1 : 0;
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[GatheringMap] Failed to delete map cache path {}.", path, exception);
            return 0;
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

    private static final class MapDownloadException extends IOException {
        private final String label;
        private final URI uri;
        private final int statusCode;
        private final String contentType;

        private MapDownloadException(String label, URI uri, int statusCode, String contentType) {
            super(label + " returned HTTP " + statusCode + " from " + uri + " contentType=" + contentType);
            this.label = label;
            this.uri = uri;
            this.statusCode = statusCode;
            this.contentType = contentType;
        }

        private String statusLine() {
            return label + " HTTP " + statusCode + " from " + uri + " contentType=" + contentType;
        }
    }
}
