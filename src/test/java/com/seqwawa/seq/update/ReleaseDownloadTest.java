package com.seqwawa.seq.update;

import static com.seqwawa.seq.update.UpdateFixtures.Body;
import static com.seqwawa.seq.update.UpdateFixtures.fastLimits;
import static com.seqwawa.seq.update.UpdateFixtures.sha256;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The transfer policy: slow but steady downloads finish; stalls, wrong sizes and wrong bytes do not. */
class ReleaseDownloadTest {
    private static final byte[] JAR = new byte[200_000];

    static {
        new Random(1).nextBytes(JAR);
    }

    @TempDir
    Path temp;

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final UpdateFixtures.Server server = server();

    @AfterEach
    void stop() {
        timer.shutdownNow();
        server.close();
    }

    private static UpdateFixtures.Server server() {
        try {
            return new UpdateFixtures.Server();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReleaseDownload.Transfer transfer(Body body, String sha256, ReleaseDownload.Limits limits) {
        server.file("/jar", body);
        return new ReleaseDownload.Transfer(server.uri("/jar"), JAR.length, sha256, server.uri("/jar.sha256"),
                temp.resolve("sequoia.jar.download"), limits, timer);
    }

    private String failure(ReleaseDownload.Transfer transfer) {
        String message = assertThrows(ReleaseDownload.TransferException.class, transfer::run).getMessage();
        assertFalse(Files.exists(temp.resolve("sequoia.jar.download")), "partial file removed");
        return message;
    }

    @Test
    void aSlowButSteadyDownloadFinishesLongAfterAFixedDeadlineWould() throws Exception {
        // 200 kB at ~8 kB per 100 ms takes about 2.5 s, several times the 1 s stall window.
        var limits = new ReleaseDownload.Limits(Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ofSeconds(1),
                Duration.ofSeconds(30), 1024, Duration.ofMillis(50));
        var transfer = transfer(Body.of(JAR).paced(8_000, 100), sha256(JAR), limits);
        long start = System.nanoTime();

        transfer.run();

        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() > 2000);
        assertArrayEquals(JAR, Files.readAllBytes(temp.resolve("sequoia.jar.download")));
        assertEquals(JAR.length, transfer.received());
    }

    @Test
    void aBodyWithoutContentLengthIsAcceptedOnCountedBytes() throws Exception {
        var transfer = transfer(Body.of(JAR).chunked(), sha256(JAR), fastLimits());

        transfer.run();

        assertArrayEquals(JAR, Files.readAllBytes(temp.resolve("sequoia.jar.download")));
    }

    @Test
    void aBodyThatStopsArrivingFailsAfterTheStallWindow() {
        var transfer = transfer(Body.of(JAR).stallingAfter(50_000), sha256(JAR), fastLimits());
        long start = System.nanoTime();

        String message = failure(transfer);

        assertTrue(message.startsWith("No data received for 1 s ("), message);
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 5000);
    }

    @Test
    void aTrickleBelowTheFloorFailsOnTheTotalLimit() {
        // Data keeps arriving (no stall) but averages far below 50 kB/s over a 1 s base allowance.
        var limits = new ReleaseDownload.Limits(Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ofSeconds(5),
                Duration.ofSeconds(1), 50_000, Duration.ofMillis(50));
        var transfer = transfer(Body.of(JAR).paced(1_000, 100), sha256(JAR), limits);

        assertTrue(failure(transfer).startsWith("The download is too slow"));
    }

    @Test
    void aServerThatNeverAnswersFailsOnTheHeaderLimit() {
        server.route("/jar", exchange -> {
            try {
                Thread.sleep(Duration.ofMinutes(5));
            } catch (InterruptedException ignored) {
                exchange.close();
            }
        });
        var transfer = new ReleaseDownload.Transfer(server.uri("/jar"), JAR.length, sha256(JAR), null,
                temp.resolve("sequoia.jar.download"), fastLimits(), timer);

        assertEquals("The server did not respond within 2 s", failure(transfer));
    }

    @Test
    void moreBytesThanTheReleaseListsAbortWithOrWithoutContentLength() {
        byte[] larger = new byte[JAR.length + 10];
        assertTrue(failure(transfer(Body.of(larger).chunked(), sha256(JAR), fastLimits()))
                .startsWith("The download is larger than"));
        try (var other = new UpdateFixtures.Server()) {
            other.file("/jar", Body.of(larger));
            var declared = new ReleaseDownload.Transfer(other.uri("/jar"), JAR.length, sha256(JAR), null,
                    temp.resolve("sequoia.jar.download"), fastLimits(), timer);
            assertTrue(failure(declared).startsWith("The server reports"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void fewerBytesThanTheReleaseListsFailWithOrWithoutContentLength() {
        byte[] shorter = java.util.Arrays.copyOf(JAR, JAR.length - 10);
        assertEquals("The download ended early (0.2 MB of 0.2 MB)",
                failure(transfer(Body.of(shorter).chunked(), sha256(JAR), fastLimits())));
        try (var other = new UpdateFixtures.Server()) {
            // Declares the release's size but sends less, then closes the connection.
            other.file("/jar", Body.of(shorter).declaring(JAR.length));
            var declared = new ReleaseDownload.Transfer(other.uri("/jar"), JAR.length, sha256(JAR), null,
                    temp.resolve("sequoia.jar.download"), fastLimits(), timer);
            assertThrows(ReleaseDownload.TransferException.class, declared::run);
            assertFalse(Files.exists(temp.resolve("sequoia.jar.download")));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void bytesThatDoNotMatchTheChecksumAreRejected() {
        assertEquals("The download does not match the release's checksum",
                failure(transfer(Body.of(JAR), "0".repeat(64), fastLimits())));
    }

    @Test
    void anHttpErrorIsReported() {
        server.text("/missing", 404, "Not Found");
        var transfer = new ReleaseDownload.Transfer(server.uri("/missing"), JAR.length, sha256(JAR), null,
                temp.resolve("sequoia.jar.download"), fastLimits(), timer);
        assertEquals("Download failed: HTTP 404", failure(transfer));
    }

    @Test
    void redirectsAreFollowed() throws Exception {
        server.route("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", server.uri("/jar").toString());
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.file("/jar", Body.of(JAR));
        var transfer = new ReleaseDownload.Transfer(server.uri("/moved"), JAR.length, sha256(JAR), null,
                temp.resolve("sequoia.jar.download"), fastLimits(), timer);

        transfer.run();

        assertArrayEquals(JAR, Files.readAllBytes(temp.resolve("sequoia.jar.download")));
    }

    @Test
    void cancellingEndsABlockedReadAtOnce() {
        var transfer = transfer(Body.of(JAR).stallingAfter(10_000), sha256(JAR),
                new ReleaseDownload.Limits(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(60),
                        Duration.ofSeconds(120), 1024, Duration.ofMillis(50)));
        timer.schedule(transfer::cancel, 500, TimeUnit.MILLISECONDS);
        long start = System.nanoTime();

        assertEquals("Cancelled", failure(transfer));

        assertTrue(transfer.cancelled());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2500);
    }

    @Test
    void theChecksumFileIsReadWhenTheReleaseHasNoDigest() throws Exception {
        server.text("/jar.sha256", 200, sha256(JAR).toUpperCase() + "  sequoia.jar\n");
        var transfer = transfer(Body.of(JAR), null, fastLimits());

        transfer.run();

        assertArrayEquals(JAR, Files.readAllBytes(temp.resolve("sequoia.jar.download")));
    }

    @Test
    void aChecksumFileThatStallsFailsWithinTheStallWindow() {
        server.file("/jar.sha256", Body.of(new byte[85]).stallingAfter(0));
        var transfer = transfer(Body.of(JAR), null, fastLimits());
        long start = System.nanoTime();

        assertEquals("No data received for 1 s (checksum file)", failure(transfer));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 5000);
        assertEquals(0, server.requests("/jar"), "the release file is not requested without a checksum");
    }

    @Test
    void cancellingDuringAStalledChecksumEndsAtOnce() {
        server.file("/jar.sha256", Body.of(new byte[85]).stallingAfter(0));
        var transfer = transfer(Body.of(JAR), null,
                new ReleaseDownload.Limits(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(60),
                        Duration.ofSeconds(120), 1024, Duration.ofMillis(50)));
        timer.schedule(transfer::cancel, 500, TimeUnit.MILLISECONDS);
        long start = System.nanoTime();

        assertEquals("Cancelled", failure(transfer));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2500);
    }

    @Test
    void theChecksumAloneIsReadOnceAndBoundedLikeTheDownload() throws Exception {
        server.text("/jar.sha256", 200, sha256(JAR) + "  sequoia.jar\n");
        var transfer = transfer(Body.of(JAR), null, fastLimits());

        assertEquals(sha256(JAR), transfer.checksum());
        transfer.run();

        assertEquals(1, server.requests("/jar.sha256"), "the checksum file is read once per transfer");
        assertArrayEquals(JAR, Files.readAllBytes(temp.resolve("sequoia.jar.download")));

        server.file("/jar.sha256", Body.of(new byte[85]).stallingAfter(0));
        var stalled = transfer(Body.of(JAR), null, fastLimits());
        long start = System.nanoTime();
        assertEquals("No data received for 1 s (checksum file)",
                assertThrows(ReleaseDownload.TransferException.class, stalled::checksum).getMessage());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 5000);
    }

    @Test
    void aChecksumFileThatIsNotASha256IsRejected() {
        server.text("/jar.sha256", 200, "not a checksum\n");
        assertEquals("The release's checksum file is not a SHA-256", failure(transfer(Body.of(JAR), null, fastLimits())));
    }

    @Test
    void parsedReleasesUseExactlyTheVersionedJarAndItsDigest() throws Exception {
        String json = server.releaseJson("v1.12.0", "sequoia-1.12.0.jar", 1234, "AB".repeat(32));
        var release = UpdateManager.parseRelease(json);
        assertEquals("sequoia-1.12.0.jar", release.jar().name());
        assertEquals(1234, release.jar().size());
        assertEquals("ab".repeat(32), release.sha256());
        assertEquals(URI.create(server.uri("/download/sequoia-1.12.0.jar.sha256").toString()), release.checksum().url());
        assertThrows(java.io.IOException.class, () -> UpdateManager.parseRelease(
                server.releaseJson("v1.12.0", "sequoia-renamed.jar", 1234, null)));
        assertThrows(java.io.IOException.class, () -> UpdateManager.parseRelease(
                server.releaseJson("v1.12.0", "sequoia-1.12.0.jar", 0, null)));
    }
}
