package com.seqwawa.seq.update;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

/** Shared pieces for the updater tests: release files, a local release server and a fake game. */
final class UpdateFixtures {
    static final String DEPENDS = "{\"minecraft\":\"1.21.11\",\"fabricloader\":\">=0.18.4\",\"fabric-api\":\"*\"}";

    private UpdateFixtures() {}

    /** A Fabric mod JAR claiming {@code id} {@code version}, padded with incompressible bytes. */
    static byte[] modJar(String id, String version, String depends, int padding) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\",\"depends\":" + depends + "}")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            ZipEntry pad = new ZipEntry("padding.bin");
            pad.setMethod(ZipEntry.STORED);
            byte[] data = new byte[padding];
            new java.util.Random(padding).nextBytes(data);
            pad.setSize(padding);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(data);
            pad.setCrc(crc.getValue());
            zip.putNextEntry(pad);
            zip.write(data);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    static byte[] modJar(String version) throws IOException {
        return modJar("seq", version, DEPENDS, 40_000);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Version version(String text) {
        try {
            return Version.parse(text);
        } catch (VersionParsingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static void await(BooleanSupplier condition, Duration timeout, String what) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for " + what);
            Thread.sleep(20);
        }
    }

    /** A JAR holding the JDK-only helper classes, as the Windows helper's copy of the mod JAR would. */
    static void helperJar(Path target) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(target))) {
            for (Class<?> type : List.of(UpdateApplier.class, UpdateRecord.class)) {
                Path classes;
                try {
                    classes = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
                } catch (java.net.URISyntaxException e) {
                    throw new IOException(e);
                }
                String prefix = type.getName().replace('.', '/');
                try (var files = Files.list(classes.resolve(prefix).getParent())) {
                    for (Path file : files.toList()) {
                        String name = classes.relativize(file).toString().replace('\\', '/');
                        if (!name.equals(prefix + ".class") && !name.startsWith(prefix + "$")) continue;
                        jar.putNextEntry(new JarEntry(name));
                        jar.write(Files.readAllBytes(file));
                        jar.closeEntry();
                    }
                }
            }
        }
    }

    /** How the release server sends a file body. */
    record Body(byte[] bytes, boolean contentLength, long declaredLength, int bytesPerChunk, long delayMillis, long stallAfter) {
        static Body of(byte[] bytes) {
            return new Body(bytes, true, bytes.length, 64 * 1024, 0, -1);
        }

        /** Chunked transfer encoding: no Content-Length. */
        Body chunked() {
            return new Body(bytes, false, declaredLength, bytesPerChunk, delayMillis, stallAfter);
        }

        Body declaring(long length) {
            return new Body(bytes, true, length, bytesPerChunk, delayMillis, stallAfter);
        }

        Body paced(int bytesPerChunk, long delayMillis) {
            return new Body(bytes, contentLength, declaredLength, bytesPerChunk, delayMillis, stallAfter);
        }

        /** Sends this many bytes, then nothing more while keeping the connection open. */
        Body stallingAfter(long bytes) {
            return new Body(this.bytes, contentLength, declaredLength, bytesPerChunk, delayMillis, bytes);
        }
    }

    /** A local release server that counts every request. */
    static final class Server implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger requests = new AtomicInteger();
        private final Map<String, AtomicInteger> perPath = new ConcurrentHashMap<>();

        Server() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "release fixture");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
        }

        URI uri(String path) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        }

        int requests() {
            return requests.get();
        }

        int requests(String path) {
            return perPath.getOrDefault(path, new AtomicInteger()).get();
        }

        /** Serves {@code path} with {@code handler}, replacing whatever served it before. */
        synchronized void route(String path, HttpHandler handler) {
            if (perPath.containsKey(path)) server.removeContext(path);
            perPath.putIfAbsent(path, new AtomicInteger());
            server.createContext(path, exchange -> {
                requests.incrementAndGet();
                perPath.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
                handler.handle(exchange);
            });
        }

        void file(String path, Body body) {
            route(path, exchange -> send(exchange, body));
        }

        void text(String path, int status, String text) {
            route(path, exchange -> {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
        }

        /** /releases/latest describing {@code jar} as {@code tag}, optionally with a digest, and a .sha256 file. */
        void release(String tag, byte[] jar, boolean digest) {
            String name = "sequoia-" + tag.substring(1) + ".jar";
            file("/download/" + name, Body.of(jar));
            text("/download/" + name + ".sha256", 200, sha256(jar) + "  " + name + "\n");
            text("/releases/latest", 200, releaseJson(tag, name, jar.length, digest ? sha256(jar) : null));
        }

        String releaseJson(String tag, String name, long size, String digest) {
            return """
                    {"tag_name":"%s","html_url":"%s","assets":[
                      {"name":"sequoia-%s-sources.jar","size":10,"browser_download_url":"%s"},
                      {"name":"%s","size":%d,"browser_download_url":"%s"%s},
                      {"name":"%s.sha256","size":85,"browser_download_url":"%s"}]}
                    """.formatted(tag, uri("/page/" + tag), tag.substring(1), uri("/download/sources.jar"), name, size,
                    uri("/download/" + name), digest == null ? "" : ",\"digest\":\"sha256:" + digest + "\"", name,
                    uri("/download/" + name + ".sha256"));
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void send(HttpExchange exchange, Body body) throws IOException {
            exchange.sendResponseHeaders(200, body.contentLength() ? body.declaredLength() : 0);
            OutputStream out = exchange.getResponseBody();
            try {
                int offset = 0;
                while (offset < body.bytes().length) {
                    if (body.stallAfter() >= 0 && offset >= body.stallAfter()) Thread.sleep(Duration.ofMinutes(5));
                    int length = Math.min(body.bytesPerChunk(), body.bytes().length - offset);
                    if (body.stallAfter() >= 0) length = (int) Math.min(length, Math.max(1, body.stallAfter() - offset));
                    out.write(body.bytes(), offset, length);
                    out.flush();
                    offset += length;
                    if (body.delayMillis() > 0) Thread.sleep(body.delayMillis());
                }
                out.close();
            } catch (InterruptedException | IOException ended) {
                exchange.close();
            }
        }
    }

    /** The game as the updater sees it. */
    static final class FakeGame implements UpdateManager.Environment {
        final Path gameDir;
        volatile String running = "1.11.1";
        volatile Instant jvmStart = Instant.now().minusSeconds(60);
        volatile Optional<Path> runningJar;
        volatile Path helperSource;
        volatile boolean windows;
        final Map<String, String> installed = new ConcurrentHashMap<>(Map.of(
                "minecraft", "1.21.11", "fabricloader", "0.18.4", "fabric-api", "0.141.2+1.21.11", "java", "21"));
        final List<Boolean> prompts = new CopyOnWriteArrayList<>();
        final List<String> toasts = new CopyOnWriteArrayList<>();
        final List<Thread> hooks = new CopyOnWriteArrayList<>();
        final List<List<String>> helpers = new CopyOnWriteArrayList<>();
        final List<Object> opened = new CopyOnWriteArrayList<>();
        final AtomicInteger stops = new AtomicInteger();
        volatile boolean failHelperStart;
        volatile long pid = ProcessHandle.current().pid();

        FakeGame(Path gameDir) throws IOException {
            this.gameDir = gameDir;
            Files.createDirectories(gameDir.resolve("mods"));
            Path jar = gameDir.resolve("mods").resolve("sequoia-1.11.1.jar");
            if (!Files.exists(jar)) Files.write(jar, modJar("1.11.1"));
            runningJar = Optional.of(jar);
        }

        Path mods() {
            return gameDir.resolve("mods");
        }

        Path updates() {
            return gameDir.resolve("updates");
        }

        @Override
        public Path gameDir() {
            return gameDir;
        }

        @Override
        public String runningVersion() {
            return running;
        }

        @Override
        public Optional<Version> installedVersion(String modId) {
            return Optional.ofNullable(installed.get(modId)).map(UpdateFixtures::version);
        }

        @Override
        public Instant jvmStart() {
            return jvmStart;
        }

        @Override
        public Optional<Path> runningJar() {
            return runningJar;
        }

        @Override
        public Path helperSourceJar() throws IOException {
            if (helperSource == null) {
                helperSource = gameDir.resolve("helper-source.jar");
                helperJar(helperSource);
            }
            return helperSource;
        }

        @Override
        public boolean windows() {
            return windows;
        }

        @Override
        public String javaExecutable() {
            return ProcessHandle.current().info().command().orElse("java");
        }

        @Override
        public long pid() {
            return pid;
        }

        @Override
        public void showPrompt(boolean startup) {
            prompts.add(startup);
        }

        @Override
        public void toast(String title, String message) {
            toasts.add(title + ": " + message);
        }

        @Override
        public void requestStop() {
            stops.incrementAndGet();
        }

        @Override
        public void addShutdownHook(Thread hook) {
            hooks.add(hook);
        }

        @Override
        public Process startHelper(List<String> command, Path log) throws IOException {
            helpers.add(command);
            if (failHelperStart) throw new IOException("Cannot run program");
            return UpdateManager.startProcess(command, log);
        }

        @Override
        public void open(URI uri) {
            opened.add(uri);
        }

        @Override
        public void open(Path path) {
            opened.add(path);
        }
    }

    /** Short limits so stalls and slow transfers show up in seconds. */
    static ReleaseDownload.Limits fastLimits() {
        return new ReleaseDownload.Limits(Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ofSeconds(1),
                Duration.ofSeconds(30), 1024, Duration.ofMillis(50));
    }

    static UpdateManager manager(FakeGame game, Server server, ReleaseDownload.Limits limits) {
        return new UpdateManager(game, "SequoiaWynncraft/Sequoia-mod", server.uri("/releases/latest"), limits,
                Duration.ofMillis(100), Duration.ofSeconds(1));
    }
}
