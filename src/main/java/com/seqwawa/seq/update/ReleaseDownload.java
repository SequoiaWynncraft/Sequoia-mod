package com.seqwawa.seq.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Downloads one release asset to a file, checking its size and SHA-256 as it streams.
 *
 * <p>No fixed deadline covers the whole body: a slow connection that keeps delivering finishes. A
 * transfer fails when no data arrives for {@link Limits#stall()}, when it averages below
 * {@link Limits#floorBytesPerSecond()} over its whole run, or when the server sends a size other than
 * the release lists. Every transfer has its own {@link HttpClient}, so cancelling or aborting it
 * can end a read that is blocked waiting for data. Release downloads are not image downloads:
 * they use none of the destination checks image previews apply.
 */
final class ReleaseDownload {
    static final String USER_AGENT = "Sequoia-Updater";
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final int MAX_TEXT_BYTES = 1024;

    record Limits(
            Duration connect,
            Duration headers,
            Duration stall,
            Duration totalBase,
            long floorBytesPerSecond,
            Duration tick) {
        static final Limits DEFAULT = new Limits(
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofSeconds(45),
                Duration.ofSeconds(120),
                16 * 1024,
                Duration.ofMillis(500));

        Duration total(long size) {
            return totalBase.plusMillis(size * 1000 / floorBytesPerSecond);
        }
    }

    /** A failure the update prompt shows as it is. */
    static final class TransferException extends IOException {
        TransferException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** One download. {@link #run} blocks its caller; the other methods may be called from any thread. */
    static final class Transfer {
        private final URI url;
        private final long size;
        private final String sha256;
        private final URI checksumUrl;
        private final Path target;
        private final Limits limits;
        private final ScheduledExecutorService timer;
        private final long startNanos = System.nanoTime();
        private volatile long received;
        private volatile long lastDataNanos = startNanos;
        private volatile boolean cancelled;
        private volatile String abortReason;
        private volatile boolean receiving;
        private volatile boolean receivingChecksum;
        private volatile HttpClient client;
        private volatile InputStream body;

        /**
         * @param sha256 the release's digest, or null to read it from {@code checksumUrl} (a .sha256
         *     file) under the same limits and cancellation as the download itself
         */
        Transfer(URI url, long size, String sha256, URI checksumUrl, Path target, Limits limits,
                ScheduledExecutorService timer) {
            this.url = url;
            this.size = size;
            this.sha256 = sha256;
            this.checksumUrl = checksumUrl;
            this.target = target;
            this.limits = limits;
            this.timer = timer;
        }

        long received() {
            return received;
        }

        long total() {
            return size;
        }

        /** Time since the last bytes arrived, or zero before the body started. */
        Duration sinceData() {
            return receiving ? Duration.ofNanos(System.nanoTime() - lastDataNanos) : Duration.ZERO;
        }

        boolean cancelled() {
            return cancelled;
        }

        void cancel() {
            cancelled = true;
            abort("Cancelled");
        }

        /**
         * Downloads to the target file and returns once it holds exactly the release's bytes. On any
         * failure the partial file is deleted.
         */
        void run() throws IOException {
            boolean complete = false;
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(limits.connect())
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            client = http;
            ScheduledFuture<?> watchdog = timer.scheduleAtFixedRate(
                    this::watch, limits.tick().toMillis(), limits.tick().toMillis(), TimeUnit.MILLISECONDS);
            try {
                if (abortReason != null) throw new TransferException(abortReason, null);
                String expected = sha256 != null ? sha256 : checksum(http);
                HttpResponse<InputStream> response = headers(http, url);
                try (InputStream in = response.body()) {
                    body = in;
                    if (abortReason != null) throw new TransferException(abortReason, null);
                    if (response.statusCode() != 200)
                        throw new TransferException("Download failed: HTTP " + response.statusCode(), null);
                    OptionalLong declared = response.headers().firstValueAsLong("Content-Length");
                    if (declared.isPresent() && declared.getAsLong() != size)
                        throw new TransferException("The server reports " + UpdateState.megabytes(declared.getAsLong())
                                + " but the release lists " + UpdateState.megabytes(size), null);
                    receive(in, expected);
                }
                complete = true;
            } catch (TransferException failure) {
                throw failure;
            } catch (IOException failure) {
                throw new TransferException(abortReason != null ? abortReason : "Download failed: " + describe(failure), failure);
            } finally {
                watchdog.cancel(false);
                body = null;
                // An abandoned body would keep close() waiting; shut the client down first.
                if (!complete) http.shutdownNow();
                http.close();
                if (!complete) Files.deleteIfExists(target);
            }
        }

        /** The release's SHA-256 from its .sha256 file; a body that stalls is aborted like the download's. */
        private String checksum(HttpClient http) throws IOException {
            if (checksumUrl == null) throw new TransferException("The release has no checksum", null);
            HttpResponse<InputStream> response = headers(http, checksumUrl);
            try (InputStream in = response.body()) {
                body = in;
                if (abortReason != null) throw new TransferException(abortReason, null);
                if (response.statusCode() != 200)
                    throw new TransferException("Checksum download failed: HTTP " + response.statusCode(), null);
                lastDataNanos = System.nanoTime();
                receivingChecksum = true;
                byte[] buffer = new byte[MAX_TEXT_BYTES + 1];
                int count = 0;
                for (int read; count < buffer.length && (read = in.read(buffer, count, buffer.length - count)) >= 0; ) {
                    count += read;
                    lastDataNanos = System.nanoTime();
                }
                if (abortReason != null) throw new TransferException(abortReason, null);
                if (count > MAX_TEXT_BYTES) throw new TransferException("The checksum file is too large", null);
                return parseChecksum(new String(buffer, 0, count, StandardCharsets.UTF_8));
            } finally {
                receivingChecksum = false;
                body = null;
            }
        }

        private HttpResponse<InputStream> headers(HttpClient http, URI target) throws IOException {
            HttpRequest request = HttpRequest.newBuilder(target).header("User-Agent", USER_AGENT).GET().build();
            CompletableFuture<HttpResponse<InputStream>> pending =
                    http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            try {
                return pending.get(limits.headers().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException slow) {
                pending.cancel(true);
                abort("The server did not respond within " + limits.headers().toSeconds() + " s");
                throw new TransferException(abortReason, slow);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                abort("Interrupted");
                throw new TransferException(abortReason, interrupted);
            } catch (ExecutionException failed) {
                Throwable cause = failed.getCause();
                throw new TransferException(abortReason != null ? abortReason : "Download failed: " + describe(cause), cause);
            }
        }

        private void receive(InputStream in, String expected) throws IOException {
            MessageDigest digest = sha256();
            byte[] buffer = new byte[BUFFER_BYTES];
            lastDataNanos = System.nanoTime();
            receiving = true;
            try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                OutputStream out = Channels.newOutputStream(channel);
                long count = 0;
                for (int read; (read = in.read(buffer)) >= 0; ) {
                    if (count + read > size)
                        throw new TransferException("The download is larger than the "
                                + UpdateState.megabytes(size) + " the release lists", null);
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    count += read;
                    received = count;
                    lastDataNanos = System.nanoTime();
                }
                if (abortReason != null) throw new TransferException(abortReason, null);
                if (count < size)
                    throw new TransferException("The download ended early (" + UpdateState.megabytes(count)
                            + " of " + UpdateState.megabytes(size) + ")", null);
                channel.force(true);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected))
                throw new TransferException("The download does not match the release's checksum", null);
        }

        private void watch() {
            long now = System.nanoTime();
            if (Duration.ofNanos(now - startNanos).compareTo(limits.total(size)) > 0) {
                abort("The download is too slow (" + UpdateState.megabytes(received) + " of "
                        + UpdateState.megabytes(size) + " after " + limits.total(size).toMinutes() + " min)");
            } else if ((receiving || receivingChecksum)
                    && Duration.ofNanos(now - lastDataNanos).compareTo(limits.stall()) > 0) {
                abort("No data received for " + limits.stall().toSeconds() + " s ("
                        + (receivingChecksum ? "checksum file" : UpdateState.megabytes(received) + " of "
                                + UpdateState.megabytes(size)) + ")");
            }
        }

        /** Records the first reason and ends any read in progress by closing the stream and client. */
        private void abort(String reason) {
            if (abortReason == null) abortReason = reason;
            InputStream in = body;
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // The client shutdown below ends the read regardless.
                }
            }
            HttpClient http = client;
            if (http != null) http.shutdownNow();
        }
    }

    private ReleaseDownload() {}

    /** The digest from a sha256sum-style line: 64 hex characters, optionally followed by a file name. */
    static String parseChecksum(String text) throws TransferException {
        String[] fields = text.trim().split("\\s+", 2);
        if (fields[0].length() != 64 || !fields[0].chars().allMatch(c -> c < 128 && Character.digit(c, 16) >= 0))
            throw new TransferException("The release's checksum file is not a SHA-256", null);
        return fields[0].toLowerCase(java.util.Locale.ROOT);
    }

    static String describe(Throwable failure) {
        Throwable cursor = failure;
        while (cursor.getCause() != null && cursor.getMessage() == null) cursor = cursor.getCause();
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }
}
