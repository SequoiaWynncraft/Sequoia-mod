package org.sequoia.seq.map;

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
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.BuildConfig;
import org.sequoia.seq.network.ClientVersion;

public final class GatheringMapImageService {
    private static final String FALLBACK_MAP_RESOURCE = "assets/seq/textures/map/wynn-map.png";
    private static final String HQ_MAP_PATH = "/assets/gathering-map/wynn-map.png";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
    private static final Path CACHE_PATH = FabricLoader.getInstance()
            .getGameDir()
            .resolve("config")
            .resolve("sequoia")
            .resolve("cache")
            .resolve("wynn-map-hq.png");

    private static final byte[] PNG_SIGNATURE = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
    };
    private static GatheringMapImageService instance;

    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "seq-gathering-map-image");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(2, runnable -> {
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

    private volatile byte[] imageBytes;
    private volatile Source imageSource = Source.NONE;
    private volatile String hqStatus = "not requested";
    private volatile long version;
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
        return hqStatus;
    }

    public String hqMapUrl() {
        try {
            return hqMapUri().toString();
        } catch (IllegalArgumentException exception) {
            return "invalid";
        }
    }

    private void loadCacheThenRefresh() {
        try {
            if (Files.isRegularFile(CACHE_PATH)) {
                byte[] cached = Files.readAllBytes(CACHE_PATH);
                if (isPng(cached)) {
                    publish(cached, Source.CACHED_HQ);
                    hqStatus = "using cached HQ, refreshing";
                }
            }
        } catch (IOException exception) {
            hqStatus = "cache read failed";
            SeqClient.LOGGER.warn("[GatheringMap] Failed to read cached HQ map image.", exception);
        }
        refreshHqMap();
    }

    private void refreshHqMap() {
        URI hqMapUri;
        try {
            hqMapUri = hqMapUri();
        } catch (IllegalArgumentException exception) {
            hqStatus = "invalid HQ URL";
            SeqClient.LOGGER.warn("[GatheringMap] Invalid HQ map URL for backend {}: {}", BuildConfig.API_URL, exception);
            return;
        }

        hqStatus = "downloading HQ";
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(downloadRequest(hqMapUri), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                hqStatus = "download HTTP " + response.statusCode();
                SeqClient.LOGGER.warn(
                        "[GatheringMap] HQ map download returned HTTP {} from {}",
                        response.statusCode(),
                        hqMapUri);
                return;
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            byte[] downloaded = readDownloadBody(response.body(), contentLength);
            if (!isPng(downloaded)) {
                hqStatus = "download was not PNG";
                SeqClient.LOGGER.warn("[GatheringMap] HQ map download was not a PNG: {}", hqMapUri);
                return;
            }
            writeCache(downloaded);
            publish(downloaded, Source.CACHED_HQ);
            hqStatus = "downloaded HQ (" + formatBytes(downloaded.length) + ")";
        } catch (HttpConnectTimeoutException exception) {
            hqStatus = "connect timeout";
            SeqClient.LOGGER.warn("[GatheringMap] HQ map connection timed out from {}", hqMapUri);
        } catch (HttpTimeoutException exception) {
            hqStatus = "download timeout";
            SeqClient.LOGGER.warn("[GatheringMap] HQ map download timed out from {}", hqMapUri);
        } catch (IOException exception) {
            hqStatus = "download failed: " + exception.getClass().getSimpleName();
            SeqClient.LOGGER.warn("[GatheringMap] Failed to download HQ map image from backend.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            hqStatus = "download interrupted";
            SeqClient.LOGGER.warn("[GatheringMap] HQ map image download interrupted.", exception);
        }
    }

    private static HttpRequest downloadRequest(URI hqMapUri) {
        return HttpRequest.newBuilder()
                .uri(hqMapUri)
                .header("User-Agent", "Sequoia-GatheringMap")
                .header(ClientVersion.MOD_VERSION_HEADER, ClientVersion.resolveInstalledVersion())
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
    }

    private static URI hqMapUri() {
        return URI.create(resolveAssetBaseUrl(BuildConfig.API_URL) + HQ_MAP_PATH);
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

    private byte[] readDownloadBody(InputStream input, long contentLength) throws IOException {
        try (input) {
            int initialSize = contentLength > 0 && contentLength <= Integer.MAX_VALUE ? (int) contentLength : 8192;
            ByteArrayOutputStream output = new ByteArrayOutputStream(initialSize);
            byte[] buffer = new byte[64 * 1024];
            long downloaded = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                hqStatus = downloadProgressStatus(downloaded, contentLength);
            }
            return output.toByteArray();
        }
    }

    private static String downloadProgressStatus(long downloaded, long contentLength) {
        if (contentLength > 0) {
            long percent = Math.min(100, Math.max(0, downloaded * 100 / contentLength));
            return "downloading HQ " + percent + "% (" + formatBytes(downloaded) + "/" + formatBytes(contentLength) + ")";
        }
        return "downloading HQ " + formatBytes(downloaded);
    }

    private static String formatBytes(long bytes) {
        double mib = bytes / (1024.0 * 1024.0);
        if (mib >= 1.0) {
            return String.format(Locale.ROOT, "%.1f MB", mib);
        }
        double kib = bytes / 1024.0;
        return String.format(Locale.ROOT, "%.0f KB", kib);
    }

    private void writeCache(byte[] downloaded) throws IOException {
        Files.createDirectories(CACHE_PATH.getParent());
        Path tempPath = CACHE_PATH.resolveSibling(CACHE_PATH.getFileName() + ".tmp");
        Files.write(tempPath, downloaded);
        try {
            Files.move(tempPath, CACHE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempPath, CACHE_PATH, StandardCopyOption.REPLACE_EXISTING);
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
        if (Arrays.equals(imageBytes, nextBytes)) {
            imageSource = source;
            return;
        }
        imageBytes = nextBytes;
        imageSource = source;
        version++;
    }

    private static boolean isPng(byte[] bytes) {
        return bytes != null
                && bytes.length > PNG_SIGNATURE.length
                && Arrays.equals(Arrays.copyOf(bytes, PNG_SIGNATURE.length), PNG_SIGNATURE);
    }

    public enum Source {
        NONE,
        FALLBACK,
        CACHED_HQ
    }
}
