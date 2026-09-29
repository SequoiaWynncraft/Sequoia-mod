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
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
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
 *
 * <p>Staying current takes two things. New versions are discovered from upstream's own version list
 * each session, and remembered on disk so an offline start still knows the newest one. And a
 * version's files are revised in place long after it ships — item fixes, ability corrections — so a
 * cached copy is checked against the site once per session rather than trusted forever.
 */
public final class WynnDataRepository {
    private static final String DATA_BASE_URL = "https://wynnbuilder.github.io/data/";
    /** Upstream's version list, the one the website decodes links with. Not rate limited. */
    private static final String VERSION_SCRIPT_URL = "https://wynnbuilder.github.io/js/load_item.js";
    /** Fallback: the data directory listing, which the GitHub API caps at sixty requests an hour. */
    private static final String VERSION_LISTING_URL =
            "https://api.github.com/repos/wynnbuilder/wynnbuilder.github.io/contents/data";
    private static final String VERSIONS_FILE = "versions.json";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    /** A failed version refresh is retried after this, rather than on every call. */
    private static final Duration REFRESH_RETRY = Duration.ofMinutes(1);
    /** Once the site proved unreachable, cached files are used without asking for this long. */
    private static final Duration OFFLINE_BACKOFF = Duration.ofMinutes(5);
    private static final int MAX_CACHED_VERSIONS = 3;
    /** HTTP dates need a two-digit day, which {@link DateTimeFormatter#RFC_1123_DATE_TIME} does not write. */
    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

    private static final WynnDataRepository INSTANCE = new WynnDataRepository();

    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final Path cacheRoot;
    private final Map<String, WynnDataSet> loaded = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<WynnDataSet>> inFlight = new ConcurrentHashMap<>();
    private final Map<String, EncodingConsts> encodingConstsCache = new ConcurrentHashMap<>();

    private volatile WynnDataVersions versions;
    private volatile boolean versionsRefreshed;
    private CompletableFuture<WynnDataVersions> refreshInFlight;
    private volatile long nextRefreshAttempt;
    private volatile long offlineUntil;
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
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "seq-wynnbuilder-data");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(executor)
                .build();
        this.versions = WynnDataVersions.builtIn().adopt(readPersistedVersions());
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
                .supplyAsync(() -> loadBlocking(key), executor)
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

    /**
     * Loads the newest version, refreshing the version list first.
     *
     * <p>When the newest cannot be had — offline, with nothing of it cached yet — the newest version
     * already on disk stands in, so the builder still opens.
     */
    public CompletableFuture<WynnDataSet> loadLatest() {
        return refreshVersions().thenCompose(known -> load(known.latest()).exceptionallyCompose(failure -> {
            String fallback = newestCachedVersion(known, known.latest());
            if (fallback == null) {
                return CompletableFuture.failedFuture(failure);
            }
            SeqClient.LOGGER.info("[WynnBuilder] Using cached data {} while {} is unavailable.",
                    fallback, known.latest());
            return load(fallback);
        }));
    }

    /** The newest version other than {@code excluded} whose required files are all on disk. */
    private String newestCachedVersion(WynnDataVersions known, String excluded) {
        List<String> all = known.all();
        for (int i = all.size() - 1; i >= 0; i--) {
            String version = all.get(i);
            if (version.equals(excluded)) {
                continue;
            }
            boolean complete = true;
            for (WynnDataFile file : WynnDataFile.values()) {
                if (file.required() && !Files.isRegularFile(cacheRoot.resolve(version).resolve(file.fileName()))) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                return version;
            }
        }
        return null;
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
                executor);
    }

    /**
     * Brings the known version list up to date with upstream.
     *
     * <p>The encoded version field is an index into this list, so discovering new versions keeps
     * links readable without shipping a mod update. Failure is not fatal: the list from the last
     * successful refresh, or the built-in one, stands, and the refresh is tried again a minute later.
     */
    public synchronized CompletableFuture<WynnDataVersions> refreshVersions() {
        if (versionsRefreshed || System.currentTimeMillis() < nextRefreshAttempt) {
            return CompletableFuture.completedFuture(versions);
        }
        // Shared while it runs, so every screen opening at once waits on the same request.
        if (refreshInFlight == null || refreshInFlight.isDone()) {
            refreshInFlight = CompletableFuture.supplyAsync(this::refreshVersionsBlocking, executor);
        }
        return refreshInFlight;
    }

    private WynnDataVersions refreshVersionsBlocking() {
        WynnDataVersions refreshed = null;
        try {
            List<String> upstream = WynnDataVersions.parseUpstreamScript(fetch(VERSION_SCRIPT_URL, false));
            WynnDataVersions adopted = versions.adopt(upstream);
            if (adopted != versions || upstream.equals(versions.all())) {
                refreshed = adopted;
            }
        } catch (IOException | RuntimeException exception) {
            SeqClient.LOGGER.debug("[WynnBuilder] Upstream version list unavailable.", exception);
        }
        if (refreshed == null) {
            try {
                refreshed = versions.merge(parseDirectoryNames(fetch(VERSION_LISTING_URL, true)));
            } catch (IOException | RuntimeException exception) {
                SeqClient.LOGGER.debug("[WynnBuilder] Version listing unavailable.", exception);
            }
        }
        if (refreshed == null) {
            nextRefreshAttempt = System.currentTimeMillis() + REFRESH_RETRY.toMillis();
            return versions;
        }
        if (!refreshed.all().equals(versions.all())) {
            SeqClient.LOGGER.info("[WynnBuilder] Newest data version is now {}.", refreshed.latest());
            persistVersions(refreshed);
        }
        versions = refreshed;
        versionsRefreshed = true;
        return refreshed;
    }

    /** Fetches a small text resource, failing on anything but a success status. */
    private String fetch(String url, boolean githubApi) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Sequoia-Mod")
                .timeout(REQUEST_TIMEOUT)
                .GET();
        if (githubApi) {
            request.header("Accept", "application/vnd.github+json");
        }
        try {
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, exception);
        }
    }

    private List<String> readPersistedVersions() {
        Path file = cacheRoot.resolve(VERSIONS_FILE);
        try {
            if (!Files.isRegularFile(file)) {
                return List.of();
            }
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (root == null || !root.isJsonArray()) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                if (element != null && element.isJsonPrimitive()) {
                    names.add(element.getAsString());
                }
            }
            return names;
        } catch (IOException | RuntimeException exception) {
            SeqClient.LOGGER.debug("[WynnBuilder] Remembered version list could not be read.", exception);
            return List.of();
        }
    }

    private void persistVersions(WynnDataVersions known) {
        JsonArray array = new JsonArray();
        known.all().forEach(array::add);
        writeCache(cacheRoot.resolve(VERSIONS_FILE), array.toString(), null);
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

    /**
     * Returns a data file, revalidating the cached copy against the site.
     *
     * <p>A cached copy carries the site's own modification time, so asking whether it changed since
     * costs one empty 304 response when it has not. Whatever goes wrong on the network, a cached copy
     * is used rather than failing: stale data beats no builder.
     */
    private String readOrDownload(Path versionDirectory, String version, WynnDataFile file) {
        Path target = versionDirectory.resolve(file.fileName());
        String cached = readCache(target);
        if (cached != null && System.currentTimeMillis() < offlineUntil) {
            return cached;
        }

        String url = DATA_BASE_URL + version + "/" + file.fileName();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Sequoia-Mod")
                    .timeout(REQUEST_TIMEOUT)
                    .GET();
            if (cached != null) {
                request.header("If-Modified-Since",
                        HTTP_DATE.format(Files.getLastModifiedTime(target).toInstant()));
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode == 304 && cached != null) {
                return cached;
            }
            if (statusCode >= 200 && statusCode < 300) {
                String body = response.body();
                writeCache(target, body, lastModified(response));
                return body;
            }
            if (cached != null) {
                return cached;
            }
            if (file.required()) {
                throw new IllegalStateException("HTTP " + statusCode + " for " + url);
            }
            return null;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (cached != null) {
                // Every other file would time out the same way, so stop asking for a while.
                offlineUntil = System.currentTimeMillis() + OFFLINE_BACKOFF.toMillis();
                SeqClient.LOGGER.debug("[WynnBuilder] Could not revalidate {}, using the cached copy.", url, exception);
                return cached;
            }
            if (file.required()) {
                throw new IllegalStateException("Could not download " + url, exception);
            }
            SeqClient.LOGGER.debug("[WynnBuilder] Optional file {} unavailable.", url, exception);
            return null;
        }
    }

    private static String readCache(Path target) {
        try {
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                return Files.readString(target, StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.debug("[WynnBuilder] Cached {} could not be read, refetching.", target, exception);
        }
        return null;
    }

    private static Instant lastModified(HttpResponse<?> response) {
        return response.headers().firstValue("Last-Modified").map(value -> {
            try {
                return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value));
            } catch (RuntimeException ignored) {
                return null;
            }
        }).orElse(null);
    }

    /**
     * @param lastModified the site's modification time, stamped on the file so the next
     *     revalidation asks about the site's clock rather than this machine's
     */
    private void writeCache(Path target, String body, Instant lastModified) {
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
            if (lastModified != null) {
                Files.setLastModifiedTime(target, FileTime.from(lastModified));
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
