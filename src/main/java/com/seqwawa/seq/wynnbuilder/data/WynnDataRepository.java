package com.seqwawa.seq.wynnbuilder.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads, caches and parses WynnBuilder data sets.
 *
 * <p>The files are fetched from wynnbuilder.github.io on first use and stored under
 * {@code config/sequoia/wynnbuilder/<version>/}. Nothing is bundled in the jar: the mod is MIT and
 * the upstream repository is GPL-3, so its files are used at runtime rather than redistributed.
 *
 * <p>Decoding a shared link needs the data of the version that produced it, so sets are cached per
 * version and the oldest are evicted once {@link #MAX_CACHED_VERSIONS} is exceeded.
 */
public final class WynnDataRepository {
    private static final String DATA_BASE_URL = "https://wynnbuilder.github.io/data/";
    private static final String VERSION_LISTING_URL =
            "https://api.github.com/repos/wynnbuilder/wynnbuilder.github.io/contents/data";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_CACHED_VERSIONS = 3;

    private static final WynnDataRepository INSTANCE = new WynnDataRepository();

    private final HttpClient httpClient;
    private final Path cacheRoot;
    private final Map<String, WynnDataSet> loaded = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<WynnDataSet>> inFlight = new ConcurrentHashMap<>();
    private final Map<String, EncodingConsts> encodingConstsCache = new ConcurrentHashMap<>();

    private volatile WynnDataVersions versions = WynnDataVersions.builtIn();
    private volatile boolean versionsRefreshed;
    private volatile String status = "Not loaded";
    private volatile String lastError;

    public static WynnDataRepository getInstance() {
        return INSTANCE;
    }

    private WynnDataRepository() {
        this(Path.of("config", "sequoia", "wynnbuilder"));
    }

    WynnDataRepository(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "seq-wynnbuilder-data");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(executor)
                .build();
    }

    public String status() {
        return status;
    }

    public String lastError() {
        return lastError;
    }

    public WynnDataVersions versions() {
        return versions;
    }

    /** The already-loaded set for a version, or {@code null} if it still needs fetching. */
    public WynnDataSet cached(String version) {
        return loaded.get(version);
    }

    public boolean isLoading() {
        return !inFlight.isEmpty();
    }

    /** The newest data version we know about. */
    public String latestVersion() {
        return versions.latest();
    }

    /**
     * Loads a data set, from memory, then disk, then the network.
     *
     * <p>Concurrent requests for the same version share one future so a screen that asks every frame
     * cannot start a second download.
     */
    public CompletableFuture<WynnDataSet> load(String version) {
        WynnDataSet ready = loaded.get(version);
        if (ready != null) {
            return CompletableFuture.completedFuture(ready);
        }
        return inFlight.computeIfAbsent(version, key -> CompletableFuture
                .supplyAsync(() -> loadBlocking(key), httpClient.executor().orElse(Runnable::run))
                .whenComplete((result, throwable) -> {
                    inFlight.remove(key);
                    if (result != null) {
                        loaded.put(key, result);
                        evictOldVersions();
                        status = "Loaded data " + key;
                    } else {
                        lastError = throwable == null ? "unknown error" : rootCauseMessage(throwable);
                        status = "Failed to load data " + key;
                        SeqClient.LOGGER.warn("[WynnBuilder] Could not load data version {}", key, throwable);
                    }
                }));
    }

    /** Loads the newest known version, refreshing the version list first when possible. */
    public CompletableFuture<WynnDataSet> loadLatest() {
        return refreshVersions().thenCompose(ignored -> load(versions.latest()));
    }

    /**
     * Loads just the encoding constants for a version.
     *
     * <p>Item, tome and aspect IDs are stable across data versions by design; only the bit widths
     * change. Decoding a link written against an older version therefore needs nothing more than
     * that version's constants file, roughly a kilobyte, rather than its whole multi-megabyte data
     * set. Items still resolve against whichever data set is currently loaded.
     */
    public CompletableFuture<EncodingConsts> encodingConsts(String version) {
        EncodingConsts cached = encodingConstsCache.get(version);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        WynnDataSet loadedSet = loaded.get(version);
        if (loadedSet != null) {
            encodingConstsCache.put(version, loadedSet.encodingConsts());
            return CompletableFuture.completedFuture(loadedSet.encodingConsts());
        }
        return CompletableFuture.supplyAsync(
                () -> {
                    String json = readOrDownload(cacheRoot.resolve(version), version, WynnDataFile.ENCODING_CONSTS);
                    EncodingConsts consts = json == null ? EncodingConsts.DEFAULT : EncodingConsts.parse(json);
                    encodingConstsCache.put(version, consts);
                    return consts;
                },
                httpClient.executor().orElse(Runnable::run));
    }

    /**
     * Extends the known version list from the upstream directory listing.
     *
     * <p>The encoded version field is an index into this list, so discovering new versions keeps
     * links readable without shipping a mod update. Failure is not fatal: the built-in list stands.
     */
    public CompletableFuture<WynnDataVersions> refreshVersions() {
        if (versionsRefreshed) {
            return CompletableFuture.completedFuture(versions);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(VERSION_LISTING_URL))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Sequoia-Mod")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable == null && response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            versions = versions.merge(parseDirectoryNames(response.body()));
                        } catch (RuntimeException exception) {
                            SeqClient.LOGGER.debug("[WynnBuilder] Version listing could not be parsed.", exception);
                        }
                    }
                    // Mark as attempted either way so a flaky listing does not retry every frame.
                    versionsRefreshed = true;
                    return versions;
                });
    }

    static List<String> parseDirectoryNames(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (root == null || !root.isJsonArray()) {
            return List.of();
        }
        JsonArray array = root.getAsJsonArray();
        List<String> names = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonElement type = entry.get("type");
            JsonElement name = entry.get("name");
            if (name != null && name.isJsonPrimitive()
                    && (type == null || !type.isJsonPrimitive() || "dir".equals(type.getAsString()))) {
                names.add(name.getAsString());
            }
        }
        return names;
    }

    private WynnDataSet loadBlocking(String version) {
        status = "Loading data " + version + "...";
        Path versionDirectory = cacheRoot.resolve(version);
        Map<WynnDataFile, String> contents = new EnumMap<>(WynnDataFile.class);

        for (WynnDataFile file : WynnDataFile.values()) {
            String content = readOrDownload(versionDirectory, version, file);
            if (content != null) {
                contents.put(file, content);
            } else if (file.required()) {
                throw new IllegalStateException("Required data file " + file.fileName() + " is unavailable");
            }
        }
        status = "Parsing data " + version + "...";
        return WynnDataSet.parse(version, contents);
    }

    private String readOrDownload(Path versionDirectory, String version, WynnDataFile file) {
        Path target = versionDirectory.resolve(file.fileName());
        try {
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                return Files.readString(target, StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.debug("[WynnBuilder] Cached {} could not be read, refetching.", target, exception);
        }

        String url = DATA_BASE_URL + version + "/" + file.fileName();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Sequoia-Mod")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (file.required()) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + " for " + url);
                }
                return null;
            }
            String body = response.body();
            writeCache(target, body);
            return body;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (file.required()) {
                throw new IllegalStateException("Could not download " + url, exception);
            }
            SeqClient.LOGGER.debug("[WynnBuilder] Optional file {} unavailable.", url, exception);
            return null;
        }
    }

    private void writeCache(Path target, String body) {
        try {
            Files.createDirectories(target.getParent());
            // Write beside the target and move, so an interrupted download cannot leave a
            // half-written file that later looks like a valid cache entry.
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, body, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.debug("[WynnBuilder] Could not cache {}.", target, exception);
        }
    }

    /** Keeps memory bounded; the newest versions are the ones people actually open. */
    private void evictOldVersions() {
        if (loaded.size() <= MAX_CACHED_VERSIONS) {
            return;
        }
        List<String> byAge = new ArrayList<>(loaded.keySet());
        byAge.sort(WynnDataVersions.NUMERIC_ORDER);
        for (int i = 0; i < byAge.size() - MAX_CACHED_VERSIONS; i++) {
            loaded.remove(byAge.get(i));
        }
    }

    /** Removes every cached file from disk. Exposed for a settings action. */
    public void clearDiskCache() {
        try (var paths = Files.walk(cacheRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort; a locked file simply stays.
                }
            });
        } catch (IOException ignored) {
            // Nothing cached yet.
        }
        loaded.clear();
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
