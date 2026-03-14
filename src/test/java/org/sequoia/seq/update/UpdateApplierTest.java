package org.sequoia.seq.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateApplierTest {

    @Test
    void mainDeletesCurrentJarInstallsPendingJarAndRemovesStaleVersions(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("sequoia-0.0.1.jar");
        Path staleJar = modsDir.resolve("sequoia-0.0.0.jar");
        Path unrelatedJar = modsDir.resolve("othermod.jar");
        Path finalJar = modsDir.resolve("sequoia-0.0.2.jar");
        Path pendingJar = updatesDir.resolve("sequoia-0.0.2.jar.pending");
        Path helperJar = updatesDir.resolve("seq-update-helper.jar");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        Files.writeString(staleJar, "stale", StandardCharsets.UTF_8);
        Files.writeString(unrelatedJar, "keep", StandardCharsets.UTF_8);
        byte[] pendingBytes = "pending".getBytes(StandardCharsets.UTF_8);
        Files.write(pendingJar, pendingBytes);
        Files.writeString(helperJar, "helper", StandardCharsets.UTF_8);

        UpdateApplier.main(new String[] {
                modsDir.toString(),
                currentJar.toString(),
                pendingJar.toString(),
                finalJar.toString(),
                helperJar.toString()
        });

        assertTrue(Files.exists(finalJar));
        assertArrayEquals(pendingBytes, Files.readAllBytes(finalJar));
        assertFalse(Files.exists(currentJar));
        assertFalse(Files.exists(staleJar));
        assertTrue(Files.exists(unrelatedJar));
        assertFalse(Files.exists(pendingJar));
        assertFalse(Files.exists(helperJar));
    }

    @Test
    void mainReturnsImmediatelyWhenArgsAreMissing(@TempDir Path tempDir) throws Exception {
        Path helperJar = tempDir.resolve("seq-update-helper.jar");
        Files.writeString(helperJar, "helper", StandardCharsets.UTF_8);

        UpdateApplier.main(new String[] {tempDir.toString(), helperJar.toString(), tempDir.toString()});

        assertTrue(Files.exists(helperJar));
    }

    @Test
    void mainReplacesCurrentJarInPlaceWhenCurrentAndFinalMatch(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("sequoia-0.0.2.jar");
        Path staleJar = modsDir.resolve("sequoia-0.0.1.jar");
        Path unrelatedJar = modsDir.resolve("othermod.jar");
        Path pendingJar = updatesDir.resolve("sequoia-0.0.2.jar.pending");
        Path helperJar = updatesDir.resolve("seq-update-helper.jar");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        Files.writeString(staleJar, "stale", StandardCharsets.UTF_8);
        Files.writeString(unrelatedJar, "keep", StandardCharsets.UTF_8);
        byte[] pendingBytes = "pending".getBytes(StandardCharsets.UTF_8);
        Files.write(pendingJar, pendingBytes);
        Files.writeString(helperJar, "helper", StandardCharsets.UTF_8);

        UpdateApplier.main(new String[] {
                modsDir.toString(),
                currentJar.toString(),
                pendingJar.toString(),
                currentJar.toString(),
                helperJar.toString()
        });

        assertTrue(Files.exists(currentJar));
        assertArrayEquals(pendingBytes, Files.readAllBytes(currentJar));
        assertFalse(Files.exists(staleJar));
        assertTrue(Files.exists(unrelatedJar));
        assertFalse(Files.exists(pendingJar));
        assertFalse(Files.exists(helperJar));
    }

    @Test
    void mainLeavesPendingJarInPlaceWhenInstallNeverSucceeds(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("sequoia-0.0.1.jar");
        Path pendingJar = updatesDir.resolve("sequoia-0.0.2.jar.pending");
        Path helperJar = updatesDir.resolve("seq-update-helper.jar");
        Path finalJar = tempDir.resolve("missing").resolve("sequoia-0.0.2.jar");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        Files.writeString(helperJar, "helper", StandardCharsets.UTF_8);
        Files.writeString(pendingJar, "pending", StandardCharsets.UTF_8);

        UpdateApplier.run(modsDir, currentJar, pendingJar, finalJar, helperJar, 2, 0);

        assertFalse(Files.exists(finalJar));
        assertTrue(Files.exists(pendingJar));
        assertFalse(Files.exists(helperJar));
    }

    @Test
    void runRetriesUntilStaleJarDeletionSucceeds(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("sequoia-0.0.1.jar");
        Path staleJar = modsDir.resolve("sequoia-0.0.0.jar");
        Path finalJar = modsDir.resolve("sequoia-0.0.2.jar");
        Path pendingJar = updatesDir.resolve("sequoia-0.0.2.jar.pending");
        Path helperJar = updatesDir.resolve("seq-update-helper.jar");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        Files.writeString(staleJar, "stale", StandardCharsets.UTF_8);
        byte[] pendingBytes = "pending".getBytes(StandardCharsets.UTF_8);
        Files.write(pendingJar, pendingBytes);
        Files.writeString(helperJar, "helper", StandardCharsets.UTF_8);

        FlakyDeleteFileOps fileOps = new FlakyDeleteFileOps(staleJar);
        List<String> logs = new ArrayList<>();

        UpdateApplier.run(
                modsDir,
                currentJar,
                pendingJar,
                finalJar,
                helperJar,
                3,
                0,
                Files::exists,
                deleteWithIo(fileOps::deleteIfExists),
                moveWithIo(Files::move),
                listWithIo(),
                ignored -> {},
                logs::add);

        assertTrue(Files.exists(finalJar));
        assertArrayEquals(pendingBytes, Files.readAllBytes(finalJar));
        assertFalse(Files.exists(currentJar));
        assertFalse(Files.exists(staleJar));
        assertFalse(Files.exists(pendingJar));
        assertFalse(Files.exists(helperJar));
        assertTrue(logs.stream().anyMatch(message -> message.contains("sequoia-0.0.0.jar")));
    }

    @Test
    void runKeepsCurrentJarWhenPendingJarIsMissing(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("sequoia-0.0.1.jar");
        Path pendingJar = updatesDir.resolve("sequoia-0.0.2.jar.pending");
        Path helperJar = updatesDir.resolve("seq-update-helper.jar");
        Path finalJar = modsDir.resolve("sequoia-0.0.2.jar");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        Files.writeString(helperJar, "helper", StandardCharsets.UTF_8);

        List<String> logs = new ArrayList<>();

        UpdateApplier.run(
                modsDir,
                currentJar,
                pendingJar,
                finalJar,
                helperJar,
                2,
                0,
                Files::exists,
                deleteWithIo(Files::deleteIfExists),
                moveWithIo(Files::move),
                listWithIo(),
                ignored -> {},
                logs::add);

        assertTrue(Files.exists(currentJar));
        assertFalse(Files.exists(finalJar));
        assertFalse(Files.exists(pendingJar));
        assertFalse(Files.exists(helperJar));
        assertTrue(logs.stream().anyMatch(message -> message.contains("keeping existing installed jar")));
    }

    @Test
    void runDoesNotPublishNewJarWhenCurrentJarCannotBeMoved(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Path updatesDir = tempDir.resolve("updates");
        Files.createDirectories(modsDir);
        Files.createDirectories(updatesDir);

        Path currentJar = modsDir.resolve("sequoia-0.0.1.jar");
        Path finalJar = modsDir.resolve("sequoia-0.0.2.jar");
        Path pendingJar = updatesDir.resolve("sequoia-0.0.2.jar.pending");
        Path helperJar = updatesDir.resolve("seq-update-helper.jar");
        Path backupJar = currentJar.resolveSibling(currentJar.getFileName().toString() + ".previous");

        Files.writeString(currentJar, "current", StandardCharsets.UTF_8);
        Files.writeString(pendingJar, "pending", StandardCharsets.UTF_8);
        Files.writeString(helperJar, "helper", StandardCharsets.UTF_8);

        List<String> logs = new ArrayList<>();

        UpdateApplier.run(
                modsDir,
                currentJar,
                pendingJar,
                finalJar,
                helperJar,
                2,
                0,
                Files::exists,
                deleteWithIo(Files::deleteIfExists),
                moveWithIo((source, destination) -> {
                    if (source.equals(currentJar) && destination.equals(backupJar)) {
                        throw new IOException("simulated current jar lock");
                    }
                    Files.move(source, destination);
                }),
                listWithIo(),
                ignored -> {},
                logs::add);

        assertTrue(Files.exists(currentJar));
        assertTrue(Files.exists(pendingJar));
        assertFalse(Files.exists(finalJar));
        assertFalse(Files.exists(backupJar));
        assertFalse(Files.exists(helperJar));
        assertTrue(logs.stream().anyMatch(message -> message.contains("simulated current jar lock")));
    }

    private static Consumer<Path> deleteWithIo(IoConsumer<Path> consumer) {
        return path -> {
            try {
                consumer.accept(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static BiConsumer<Path, Path> moveWithIo(IoBiConsumer<Path, Path> consumer) {
        return (source, destination) -> {
            try {
                consumer.accept(source, destination);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static Function<Path, List<Path>> listWithIo() {
        return directory -> {
            try (var stream = Files.list(directory)) {
                return stream.toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    @FunctionalInterface
    private interface IoConsumer<T> {
        void accept(T value) throws IOException;
    }

    @FunctionalInterface
    private interface IoBiConsumer<T, U> {
        void accept(T left, U right) throws IOException;
    }

    private static final class FlakyDeleteFileOps {
        private final Path flakyPath;
        private boolean failedOnce;

        private FlakyDeleteFileOps(Path flakyPath) {
            this.flakyPath = flakyPath;
        }

        public void deleteIfExists(Path path) throws IOException {
            if (!failedOnce && path.equals(flakyPath)) {
                failedOnce = true;
                throw new IOException("simulated transient lock for " + path.getFileName());
            }
            Files.deleteIfExists(path);
        }
    }
}
