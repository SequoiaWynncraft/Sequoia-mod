package org.sequoia.seq.update;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateManagerTest {

    @Test
    void isNewerComparesStrictVersionTags() {
        assertTrue(UpdateManager.isNewer("v0.1.1", "v0.1.0"));
        assertTrue(UpdateManager.isNewer("v1.0.0", "not-a-tag"));
        assertFalse(UpdateManager.isNewer("v0.1.0", "v0.1.0"));
        assertFalse(UpdateManager.isNewer("v0.1.0", "v0.1.1"));
        assertFalse(UpdateManager.isNewer("main", "v0.1.0"));
    }

    @Test
    void parseChecksumAcceptsSha256FileFormat() {
        String checksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        assertEquals(checksum, UpdateManager.parseChecksum(checksum + "  sequoia.jar\n"));
    }

    @Test
    void parseSha256DigestAcceptsGitHubDigestFormat() {
        String checksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        assertEquals(checksum, UpdateManager.parseSha256Digest("sha256:" + checksum.toUpperCase()));
    }

    @Test
    void fetchLatestReleaseParsesJarAndChecksumAssets(@TempDir Path gameDir) throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.respondJson(
                    "/releases/latest",
                    """
                            {
                              "tag_name": "v0.1.1",
                              "html_url": "https://example.invalid/releases/v0.1.1",
                              "assets": [
                                {
                                  "name": "sequoia-0.1.1.jar",
                                                                    "browser_download_url": "%s",
                                                                    "digest": "sha256:%s"
                                },
                                {
                                  "name": "sequoia-0.1.1.jar.sha256",
                                  "browser_download_url": "%s"
                                }
                              ]
                            }
                            """.formatted(server.uri("/downloads/sequoia-0.1.1.jar"),
                                                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                            server.uri("/downloads/sequoia-0.1.1.jar.sha256")));

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                TestableUpdateManager manager = new TestableUpdateManager(
                        HttpClient.newBuilder().executor(executor).build(),
                        executor,
                        server.uri("/releases/latest"),
                        gameDir,
                        false);

                UpdateManager.ReleaseCandidate release = manager.fetchLatestRelease().join();

                assertNotNull(release);
                assertEquals("v0.1.1", release.tagName());
                assertEquals("sequoia-0.1.1.jar", release.jarAsset().name());
                assertEquals(server.uri("/downloads/sequoia-0.1.1.jar").toString(), release.jarAsset().downloadUrl());
                assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        release.jarAsset().sha256());
                assertEquals("sequoia-0.1.1.jar.sha256", release.checksumAsset().name());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void applyReleaseInstallsJarAndRemovesPreviousVersion(@TempDir Path gameDir) throws Exception {
        byte[] jarBytes = "fake-jar-binary".getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(jarBytes);

        try (TestHttpServer server = new TestHttpServer()) {
            server.respondBytes("/downloads/sequoia-0.1.1.jar", jarBytes, "application/java-archive");
            server.respondText("/downloads/sequoia-0.1.1.jar.sha256", checksum + "  sequoia-0.1.1.jar\n", "text/plain");

            Path modsDir = gameDir.resolve("mods");
            Files.createDirectories(modsDir);
            Files.writeString(modsDir.resolve("sequoia-0.1.0.jar"), "old");

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                TestableUpdateManager manager = new TestableUpdateManager(
                        HttpClient.newBuilder().executor(executor).build(),
                        executor,
                        server.uri("/releases/latest"),
                        gameDir,
                        false);

                UpdateManager.ReleaseCandidate release = new UpdateManager.ReleaseCandidate(
                        "v0.1.1",
                        "https://example.invalid/releases/v0.1.1",
                        new UpdateManager.ReleaseAsset(
                                "sequoia-0.1.1.jar",
                                server.uri("/downloads/sequoia-0.1.1.jar").toString()),
                        new UpdateManager.ReleaseAsset(
                                "sequoia-0.1.1.jar.sha256",
                                server.uri("/downloads/sequoia-0.1.1.jar.sha256").toString()));

                manager.applyRelease(release, false);

                Path installedJar = modsDir.resolve("sequoia-0.1.1.jar");
                assertTrue(Files.exists(installedJar));
                assertArrayEquals(jarBytes, Files.readAllBytes(installedJar));
                assertFalse(Files.exists(modsDir.resolve("sequoia-0.1.0.jar")));
                assertEquals("Installed v0.1.1. Restart required.", manager.getStatusLine());
                assertEquals(List.of("Installed v0.1.1. Restart Minecraft to load it."), manager.notifications);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void applyReleaseDeletesTemporaryJarWhenChecksumDoesNotMatch(@TempDir Path gameDir) throws Exception {
        byte[] jarBytes = "fake-jar-binary".getBytes(StandardCharsets.UTF_8);

        try (TestHttpServer server = new TestHttpServer()) {
            server.respondBytes("/downloads/sequoia-0.1.1.jar", jarBytes, "application/java-archive");
            server.respondText(
                    "/downloads/sequoia-0.1.1.jar.sha256",
                    "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff  sequoia-0.1.1.jar\n",
                    "text/plain");

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                TestableUpdateManager manager = new TestableUpdateManager(
                        HttpClient.newBuilder().executor(executor).build(),
                        executor,
                        server.uri("/releases/latest"),
                        gameDir,
                        false);

                UpdateManager.ReleaseCandidate release = new UpdateManager.ReleaseCandidate(
                        "v0.1.1",
                        "https://example.invalid/releases/v0.1.1",
                        new UpdateManager.ReleaseAsset(
                                "sequoia-0.1.1.jar",
                                server.uri("/downloads/sequoia-0.1.1.jar").toString()),
                        new UpdateManager.ReleaseAsset(
                                "sequoia-0.1.1.jar.sha256",
                                server.uri("/downloads/sequoia-0.1.1.jar.sha256").toString()));

                RuntimeException error = assertThrows(RuntimeException.class, () -> manager.applyRelease(release, false));

                assertEquals("Checksum mismatch for downloaded update.", UpdateManager.rootCauseMessage(error));
                assertFalse(Files.exists(gameDir.resolve("updates").resolve("sequoia-0.1.1.jar.download")));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void applyReleaseFollowsRedirectsForJarAndChecksumDownloads(@TempDir Path gameDir) throws Exception {
        byte[] jarBytes = "fake-jar-binary".getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(jarBytes);

        try (TestHttpServer server = new TestHttpServer()) {
            server.redirect("/downloads/sequoia-0.1.1.jar", "/storage/sequoia-0.1.1.jar");
            server.respondBytes("/storage/sequoia-0.1.1.jar", jarBytes, "application/java-archive");
            server.redirect("/downloads/sequoia-0.1.1.jar.sha256", "/storage/sequoia-0.1.1.jar.sha256");
            server.respondText(
                    "/storage/sequoia-0.1.1.jar.sha256",
                    checksum + "  sequoia-0.1.1.jar\n",
                    "text/plain");

            Path modsDir = gameDir.resolve("mods");
            Files.createDirectories(modsDir);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                TestableUpdateManager manager = new TestableUpdateManager(
                        HttpClient.newBuilder().executor(executor).build(),
                        executor,
                        server.uri("/releases/latest"),
                        gameDir,
                        false);

                UpdateManager.ReleaseCandidate release = new UpdateManager.ReleaseCandidate(
                        "v0.1.1",
                        "https://example.invalid/releases/v0.1.1",
                        new UpdateManager.ReleaseAsset(
                                "sequoia-0.1.1.jar",
                                server.uri("/downloads/sequoia-0.1.1.jar").toString()),
                        new UpdateManager.ReleaseAsset(
                                "sequoia-0.1.1.jar.sha256",
                                server.uri("/downloads/sequoia-0.1.1.jar.sha256").toString()));

                manager.applyRelease(release, false);

                Path installedJar = modsDir.resolve("sequoia-0.1.1.jar");
                assertTrue(Files.exists(installedJar));
                assertArrayEquals(jarBytes, Files.readAllBytes(installedJar));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void applyReleaseUsesEmbeddedDigestWhenChecksumFileIsEmpty(@TempDir Path gameDir) throws Exception {
        byte[] jarBytes = "fake-jar-binary".getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(jarBytes);

        try (TestHttpServer server = new TestHttpServer()) {
            server.respondBytes("/downloads/sequoia-0.1.1.jar", jarBytes, "application/java-archive");
            server.respondText("/downloads/sequoia-0.1.1.jar.sha256", "", "text/plain");

            Path modsDir = gameDir.resolve("mods");
            Files.createDirectories(modsDir);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                TestableUpdateManager manager = new TestableUpdateManager(
                        HttpClient.newBuilder().executor(executor).build(),
                        executor,
                        server.uri("/releases/latest"),
                        gameDir,
                        false);

                UpdateManager.ReleaseCandidate release = new UpdateManager.ReleaseCandidate(
                        "v0.1.1",
                        "https://example.invalid/releases/v0.1.1",
                        new UpdateManager.ReleaseAsset(
                                "sequoia-0.1.1.jar",
                                server.uri("/downloads/sequoia-0.1.1.jar").toString(),
                                checksum),
                        new UpdateManager.ReleaseAsset(
                                "sequoia-0.1.1.jar.sha256",
                                server.uri("/downloads/sequoia-0.1.1.jar.sha256").toString()));

                manager.applyRelease(release, false);

                Path installedJar = modsDir.resolve("sequoia-0.1.1.jar");
                assertTrue(Files.exists(installedJar));
                assertArrayEquals(jarBytes, Files.readAllBytes(installedJar));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bytes);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static final class TestableUpdateManager extends UpdateManager {
        private final Path gameDir;
        private final boolean windows;
        private final List<String> notifications = new ArrayList<>();

        private TestableUpdateManager(
                HttpClient httpClient,
                ExecutorService executor,
                URI latestReleaseApi,
                Path gameDir,
                boolean windows) {
            super(httpClient, executor, new Gson(), latestReleaseApi);
            this.gameDir = gameDir;
            this.windows = windows;
        }

        @Override
        Path resolveGameDir() {
            return gameDir;
        }

        @Override
        boolean isWindows() {
            return windows;
        }

        @Override
        void showUpdatePrompt(String installedVersion, ReleaseCandidate candidate) {
        }

        @Override
        void requestMinecraftStop() {
        }

        @Override
        void sendNotification(String message) {
            notifications.add(message);
        }

        @Override
        void sendClickableNotification(String text, String url) {
            notifications.add(text + " -> " + url);
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;

        private TestHttpServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.start();
        }

        URI uri(String path) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        }

        void respondJson(String path, String body) {
            respondText(path, body, "application/json");
        }

        void respondText(String path, String body, String contentType) {
            respondBytes(path, body.getBytes(StandardCharsets.UTF_8), contentType);
        }

        void respondBytes(String path, byte[] body, String contentType) {
            server.createContext(path, exchange -> writeResponse(exchange, body, contentType));
        }

        void redirect(String path, String location) {
            server.createContext(path, exchange -> {
                try (exchange) {
                    exchange.getResponseHeaders().add("Location", location);
                    exchange.sendResponseHeaders(302, -1);
                }
            });
        }

        private void writeResponse(HttpExchange exchange, byte[] body, String contentType) throws IOException {
            try (exchange) {
                exchange.getResponseHeaders().add("Content-Type", contentType);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
