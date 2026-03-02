package org.sequoia.seq.update;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.ui.UpdatePromptScreen;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class UpdateManager implements NotificationAccessor {
    private static final String MOD_ID = "seq";
    private static final String REPO_OWNER = "SequoiaWynncraft";
    private static final String REPO_NAME = "Sequoia-mod";
    private static final String RELEASES_LATEST_API = "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME
            + "/releases/latest";
    private static final Pattern STRICT_TAG_PATTERN = Pattern.compile("^v(\\d+)\\.(\\d+)\\.(\\d+)$");

    private static UpdateManager instance;

    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Gson gson;

    private volatile boolean startupChecked;
    private volatile boolean checking;
    private volatile boolean applying;
    private volatile String sessionIgnoredTag;
    private volatile String statusLine = "Idle";
    private volatile ReleaseCandidate pendingRelease;

    public static UpdateManager getInstance() {
        if (instance == null) {
            instance = new UpdateManager();
        }
        return instance;
    }

    private UpdateManager() {
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "seq-updater");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .build();
        this.gson = new Gson();
    }

    public void checkForUpdatesOnStartup() {
        if (startupChecked) {
            return;
        }
        startupChecked = true;
        checkForUpdates(false);
    }

    public void checkForUpdatesManually() {
        checkForUpdates(true);
    }

    public String getStatusLine() {
        return statusLine;
    }

    public void ignoreForSession(String tag) {
        this.sessionIgnoredTag = tag;
        this.statusLine = "Ignored " + tag + " for this session.";
    }

    public void applyPendingUpdate(boolean exitAfterInstall) {
        ReleaseCandidate target = pendingRelease;
        if (target == null) {
            notify("No pending update available.");
            statusLine = "No pending update available.";
            return;
        }
        if (applying) {
            notify("Update install already in progress.");
            return;
        }

        applying = true;
        statusLine = "Installing " + target.tagName() + "...";
        CompletableFuture.runAsync(() -> applyRelease(target, exitAfterInstall), executor)
                .whenComplete((ignored, throwable) -> {
                    applying = false;
                    if (throwable != null) {
                        statusLine = "Install failed: " + rootCauseMessage(throwable);
                        notify("Update failed: " + rootCauseMessage(throwable));
                        SeqClient.LOGGER.error("Failed to apply update", throwable);
                    }
                });
    }

    public void openReleasePage() {
        ReleaseCandidate target = pendingRelease;
        if (target == null) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(target.releasePageUrl()));
        } catch (Exception e) {
            notifyClickable("Open latest release", target.releasePageUrl());
        }
    }

    private void checkForUpdates(boolean manualTrigger) {
        if (checking) {
            if (manualTrigger) {
                notify("Update check already running.");
            }
            return;
        }

        checking = true;
        statusLine = "Checking for updates...";
        fetchLatestRelease().whenComplete((candidate, throwable) -> {
            checking = false;
            if (throwable != null) {
                statusLine = "Update check failed: " + rootCauseMessage(throwable);
                if (manualTrigger) {
                    notify("Update check failed: " + rootCauseMessage(throwable));
                }
                SeqClient.LOGGER.warn("Update check failed", throwable);
                return;
            }
            if (candidate == null) {
                statusLine = "No valid release found.";
                if (manualTrigger) {
                    notify("No valid release found.");
                }
                return;
            }

            String installedVersion = resolveInstalledVersion();
            if (!isNewer(candidate.tagName(), installedVersion)) {
                pendingRelease = null;
                statusLine = "Up to date (" + installedVersion + ").";
                if (manualTrigger) {
                    notify("You are up to date (" + installedVersion + ").");
                }
                return;
            }

            if (Objects.equals(sessionIgnoredTag, candidate.tagName()) && !manualTrigger) {
                statusLine = "Update " + candidate.tagName() + " ignored for this session.";
                return;
            }

            pendingRelease = candidate;
            statusLine = "Update available: " + installedVersion + " -> " + candidate.tagName();
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(
                    new UpdatePromptScreen(Minecraft.getInstance().screen, installedVersion, candidate)));
        });
    }

    private CompletableFuture<ReleaseCandidate> fetchLatestRelease() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RELEASES_LATEST_API))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Sequoia-Updater")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 400) {
                        throw new IllegalStateException("GitHub API returned " + response.statusCode());
                    }
                    JsonObject root = gson.fromJson(response.body(), JsonObject.class);
                    if (root == null) {
                        return null;
                    }

                    String tagName = getString(root, "tag_name");
                    if (tagName == null || !STRICT_TAG_PATTERN.matcher(tagName).matches()) {
                        return null;
                    }

                    String releasePage = getString(root, "html_url");
                    JsonArray assets = root.has("assets") && root.get("assets").isJsonArray()
                            ? root.getAsJsonArray("assets")
                            : new JsonArray();

                    ReleaseAsset jarAsset = null;
                    for (JsonElement assetElement : assets) {
                        if (!assetElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject assetObj = assetElement.getAsJsonObject();
                        String name = getString(assetObj, "name");
                        if (name == null) {
                            continue;
                        }
                        if (!name.endsWith(".jar") || name.endsWith("-sources.jar")) {
                            continue;
                        }
                        if (!name.startsWith("sequoia-")) {
                            continue;
                        }
                        String url = getString(assetObj, "browser_download_url");
                        if (url != null) {
                            jarAsset = new ReleaseAsset(name, url);
                            break;
                        }
                    }

                    if (jarAsset == null) {
                        throw new IllegalStateException("Release missing primary mod jar asset.");
                    }

                    ReleaseAsset checksumAsset = null;
                    String expectedChecksumName = jarAsset.name() + ".sha256";
                    for (JsonElement assetElement : assets) {
                        if (!assetElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject assetObj = assetElement.getAsJsonObject();
                        String name = getString(assetObj, "name");
                        if (!expectedChecksumName.equals(name)) {
                            continue;
                        }
                        String url = getString(assetObj, "browser_download_url");
                        if (url != null) {
                            checksumAsset = new ReleaseAsset(name, url);
                            break;
                        }
                    }

                    if (checksumAsset == null) {
                        throw new IllegalStateException(
                                "Release missing required checksum asset " + expectedChecksumName + ".");
                    }

                    return new ReleaseCandidate(tagName, releasePage, jarAsset, checksumAsset);
                });
    }

    private void applyRelease(ReleaseCandidate release, boolean exitAfterInstall) {
        try {
            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            Files.createDirectories(modsDir);

            Path tempJar = modsDir.resolve(release.jarAsset().name() + ".download");
            Path finalJar = modsDir.resolve(release.jarAsset().name());

            downloadToFile(release.jarAsset().downloadUrl(), tempJar);
            String checksumFile = downloadToString(release.checksumAsset().downloadUrl());
            String expectedChecksum = parseChecksum(checksumFile);
            String actualChecksum = sha256(tempJar);
            if (!expectedChecksum.equalsIgnoreCase(actualChecksum)) {
                Files.deleteIfExists(tempJar);
                throw new IllegalStateException("Checksum mismatch for downloaded update.");
            }

            deleteExistingSequoiaJars(modsDir);
            moveAtomically(tempJar, finalJar);

            pendingRelease = null;
            statusLine = "Installed " + release.tagName() + ". Restart required.";
            notify("Installed " + release.tagName() + ". Restart Minecraft to load it.");

            if (exitAfterInstall) {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void downloadToFile(String url, Path targetFile) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Sequoia-Updater")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(targetFile));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Download failed with status " + response.statusCode());
        }
    }

    private String downloadToString(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Sequoia-Updater")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Checksum fetch failed with status " + response.statusCode());
        }
        return response.body();
    }

    private void deleteExistingSequoiaJars(Path modsDir) throws IOException {
        try (Stream<Path> stream = Files.list(modsDir)) {
            stream.filter(path -> path.getFileName().toString().startsWith("sequoia-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String resolveInstalledVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private boolean isNewer(String latest, String installed) {
        Matcher latestMatcher = STRICT_TAG_PATTERN.matcher(latest);
        if (!latestMatcher.matches()) {
            return false;
        }

        Matcher installedMatcher = STRICT_TAG_PATTERN.matcher(installed);
        if (!installedMatcher.matches()) {
            return !latest.equals(installed);
        }

        for (int i = 1; i <= 3; i++) {
            int latestPart = Integer.parseInt(latestMatcher.group(i));
            int installedPart = Integer.parseInt(installedMatcher.group(i));
            if (latestPart > installedPart) {
                return true;
            }
            if (latestPart < installedPart) {
                return false;
            }
        }
        return false;
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String parseChecksum(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException("Checksum file was empty.");
        }
        String[] split = trimmed.split("\\s+");
        if (split.length == 0 || split[0].length() != 64) {
            throw new IllegalStateException("Checksum file had invalid format.");
        }
        return split[0];
    }

    private String getString(JsonObject object, String key) {
        if (!object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }

    public record ReleaseCandidate(String tagName, String releasePageUrl, ReleaseAsset jarAsset,
            ReleaseAsset checksumAsset) {
    }

    public record ReleaseAsset(String name, String downloadUrl) {
    }
}
