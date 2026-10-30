package com.seqwawa.seq.update;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Swapping JARs and the Windows helper protocol, on real files and processes. */
class UpdateApplierTest {
    @TempDir
    Path root;

    private Path mods;
    private Path updates;
    private Path current;
    private Path pending;
    private Path finalJar;
    private Path record;
    private byte[] newJar;

    @BeforeEach
    void layout() throws IOException {
        mods = Files.createDirectories(root.resolve("mods"));
        updates = Files.createDirectories(root.resolve("updates"));
        current = mods.resolve("sequoia-1.11.1.jar");
        finalJar = mods.resolve("sequoia-1.12.0.jar");
        pending = updates.resolve("sequoia-1.12.0.jar.pending");
        record = updates.resolve(UpdateRecord.FILE);
        Files.writeString(current, "current");
        newJar = UpdateFixtures.modJar("1.12.0");
        Files.write(pending, newJar);
    }

    private UpdateApplier.Paths paths() {
        return new UpdateApplier.Paths(mods, current, pending, finalJar);
    }

    private UpdateRecord.Data staged(UpdateRecord.Phase phase) {
        return new UpdateRecord.Data("v1.12.0", "1.11.1", "sequoia-1.12.0.jar", UpdateFixtures.sha256(newJar),
                newJar.length, UpdateRecord.Method.WINDOWS_HELPER, phase, current.toString(), 0, null, null, null,
                Instant.now());
    }

    private List<String> loadableSequoiaJars() throws IOException {
        try (Stream<Path> files = Files.list(mods)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> UpdateApplier.VERSIONED_JAR.matcher(name).matches())
                    .sorted()
                    .toList();
        }
    }

    @Test
    void swapKeepsThePreviousJarAsideAndRemovesOnlyVersionedDuplicates() throws Exception {
        Files.writeString(mods.resolve("sequoia-1.10.0.jar"), "duplicate");
        Files.writeString(mods.resolve("sequoia-addon.jar"), "someone else's");
        Files.writeString(mods.resolve("othermod.jar"), "other");

        var outcome = UpdateApplier.swap(paths(), 1, 0, UpdateApplier.DISK, millis -> {}, message -> {});

        assertTrue(outcome.installed(), outcome.detail());
        assertArrayEquals(newJar, Files.readAllBytes(finalJar));
        assertEquals("current", Files.readString(mods.resolve("sequoia-1.11.1.jar.previous")));
        assertEquals(List.of("sequoia-1.12.0.jar"), loadableSequoiaJars());
        assertTrue(Files.exists(mods.resolve("sequoia-addon.jar")));
        assertTrue(Files.exists(mods.resolve("othermod.jar")));
        assertFalse(Files.exists(pending));
    }

    @Test
    void aBackupLeftByAnEarlierInstallDoesNotLeaveTheCurrentJarLoadable() throws Exception {
        // A launcher restored its own Sequoia.jar after an earlier install, whose backup is still here.
        Path launcherJar = mods.resolve("Sequoia.jar");
        Files.move(current, launcherJar);
        Files.writeString(mods.resolve("Sequoia.jar.previous"), "an older install");
        var paths = new UpdateApplier.Paths(mods, launcherJar, pending, finalJar);

        var outcome = UpdateApplier.swap(paths, 1, 0, UpdateApplier.DISK, millis -> {}, message -> {});

        assertTrue(outcome.installed(), outcome.detail());
        try (Stream<Path> files = Files.list(mods)) {
            assertEquals(List.of("sequoia-1.12.0.jar"),
                    files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".jar")).sorted().toList());
        }
        assertEquals("current", Files.readString(mods.resolve("Sequoia.jar.previous")));
    }

    /** Stops the swap dead after {@code operations} file operations, like a crash at that point. */
    private static final class Crash extends RuntimeException {}

    private static UpdateApplier.Disk crashingAfter(int operations) {
        AtomicInteger count = new AtomicInteger();
        return new UpdateApplier.Disk() {
            private void step() {
                if (count.incrementAndGet() > operations) throw new Crash();
            }

            @Override
            public boolean exists(Path path) {
                return UpdateApplier.DISK.exists(path);
            }

            @Override
            public void move(Path source, Path target) throws IOException {
                step();
                UpdateApplier.DISK.move(source, target);
            }

            @Override
            public void delete(Path path) throws IOException {
                step();
                UpdateApplier.DISK.delete(path);
            }

            @Override
            public List<Path> list(Path directory) throws IOException {
                return UpdateApplier.DISK.list(directory);
            }
        };
    }

    @Test
    void stoppingAtAnyStepNeverLeavesTwoLoadableSequoiaJars() throws Exception {
        for (int operations = 0; operations <= 4; operations++) {
            layout();
            for (Path file : List.of(finalJar, mods.resolve("sequoia-1.11.1.jar.previous"))) Files.deleteIfExists(file);
            Files.writeString(current, "current");
            try {
                UpdateApplier.swap(paths(), 1, 0, crashingAfter(operations), millis -> {}, message -> {});
            } catch (Crash stopped) {
                // The game or helper died here.
            }
            assertTrue(loadableSequoiaJars().size() <= 1, "after " + operations + " operations: " + loadableSequoiaJars());
        }
    }

    @Test
    void aFailedMoveIntoPlacePutsThePreviousJarBackAndSaysSo() {
        var failing = new UpdateApplier.Disk() {
            @Override
            public boolean exists(Path path) {
                return UpdateApplier.DISK.exists(path);
            }

            @Override
            public void move(Path source, Path target) throws IOException {
                if (source.equals(pending)) throw new java.nio.file.AccessDeniedException(target.toString());
                UpdateApplier.DISK.move(source, target);
            }

            @Override
            public void delete(Path path) throws IOException {
                UpdateApplier.DISK.delete(path);
            }

            @Override
            public List<Path> list(Path directory) throws IOException {
                return UpdateApplier.DISK.list(directory);
            }
        };

        var outcome = UpdateApplier.swap(paths(), 2, 0, failing, millis -> {}, message -> {});

        assertFalse(outcome.installed());
        assertTrue(outcome.detail().startsWith("Could not install sequoia-1.12.0.jar after 2 attempts: AccessDeniedException"),
                outcome.detail());
        assertTrue(outcome.detail().endsWith("The previous version is still installed."), outcome.detail());
        assertTrue(Files.exists(current));
        assertFalse(Files.exists(finalJar));
    }

    @Test
    void theHelperInstallsARecordedStagedFileAndRecordsIt() throws Exception {
        UpdateRecord.write(record, staged(UpdateRecord.Phase.LAUNCHED));
        Path helperJar = updates.resolve(UpdateManager.HELPER_JAR);
        Files.writeString(helperJar, "helper");

        assertTrue(UpdateApplier.runHelper(record, Long.MAX_VALUE, paths(), helperJar, 1, 0));

        UpdateRecord.Data result = UpdateRecord.read(record);
        assertEquals(UpdateRecord.Phase.INSTALLED, result.phase());
        assertEquals(ProcessHandle.current().pid(), result.helperPid());
        assertArrayEquals(newJar, Files.readAllBytes(finalJar));
        assertFalse(Files.exists(helperJar));
    }

    @Test
    void theHelperTouchesNothingWhenTheRecordDoesNotDescribeTheStagedFile() throws Exception {
        UpdateRecord.write(record, staged(UpdateRecord.Phase.INSTALLED));

        assertFalse(UpdateApplier.runHelper(record, Long.MAX_VALUE, paths(), updates.resolve("helper.jar"), 1, 0));

        assertEquals(UpdateRecord.Phase.INSTALLED, UpdateRecord.read(record).phase());
        assertTrue(Files.exists(pending));
        assertEquals("current", Files.readString(current));
    }

    @Test
    void theHelperRefusesAStagedFileThatChangedSinceItWasVerified() throws Exception {
        UpdateRecord.write(record, staged(UpdateRecord.Phase.LAUNCHED));
        byte[] changed = newJar.clone();
        changed[changed.length / 2] ^= 1;
        Files.write(pending, changed);

        assertFalse(UpdateApplier.runHelper(record, Long.MAX_VALUE, paths(), updates.resolve("helper.jar"), 1, 0));

        UpdateRecord.Data result = UpdateRecord.read(record);
        assertEquals(UpdateRecord.Phase.FAILED, result.phase());
        assertEquals("The staged update changed since it was verified", result.detail());
        assertFalse(Files.exists(pending));
        assertEquals("current", Files.readString(current));
    }

    @Test
    void theHelperMarksItselfRunningAndWaitsForTheGameToExit() throws Exception {
        UpdateRecord.write(record, staged(UpdateRecord.Phase.LAUNCHED));
        Process game = Sleeper.start();
        try {
            var helper = CompletableFuture.supplyAsync(() ->
                    UpdateApplier.runHelper(record, game.pid(), paths(), updates.resolve("helper.jar"), 1, 0));
            UpdateFixtures.await(() -> phase() == UpdateRecord.Phase.APPLYING, Duration.ofSeconds(10), "APPLYING");
            Thread.sleep(300);
            assertFalse(helper.isDone(), "the helper waits while the game runs");
            assertTrue(Files.exists(current));
            game.destroy();
            assertTrue(helper.get(20, TimeUnit.SECONDS));
            assertEquals(UpdateRecord.Phase.INSTALLED, phase());
        } finally {
            game.destroyForcibly();
        }
    }

    private UpdateRecord.Phase phase() {
        try {
            UpdateRecord.Data data = UpdateRecord.read(record);
            return data == null ? null : data.phase();
        } catch (IOException unreadable) {
            return null;
        }
    }

    @Test
    void theHelperRunsAsASeparateProcessWithOnlyTheJdkAndItsJar() throws Exception {
        UpdateRecord.write(record, staged(UpdateRecord.Phase.STAGED));
        Path helperJar = updates.resolve(UpdateManager.HELPER_JAR);
        UpdateFixtures.helperJar(helperJar);
        Path log = updates.resolve(UpdateManager.HELPER_LOG);

        Process helper = UpdateManager.startProcess(List.of(
                ProcessHandle.current().info().command().orElse("java"), "-cp", helperJar.toString(),
                UpdateApplier.class.getName(), record.toString(), Long.toString(Long.MAX_VALUE), mods.toString(), current.toString(),
                pending.toString(), finalJar.toString(), helperJar.toString()), log);

        assertTrue(helper.waitFor(60, TimeUnit.SECONDS));
        String output = Files.readString(log, StandardCharsets.UTF_8);
        assertEquals(0, helper.exitValue(), output);
        assertTrue(output.contains("Installed sequoia-1.12.0.jar"), output);
        assertArrayEquals(newJar, Files.readAllBytes(finalJar));
        assertEquals(UpdateRecord.Phase.INSTALLED, phase());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aLockedOldJarExhaustsTheRetriesAndLeavesThePreviousVersionInPlace() throws Exception {
        UpdateRecord.write(record, staged(UpdateRecord.Phase.LAUNCHED));
        // java.io opens without FILE_SHARE_DELETE on Windows, so the JAR cannot be renamed while held;
        // an NIO FileChannel would allow it and not exercise the retries.
        try (RandomAccessFile held = new RandomAccessFile(current.toFile(), "r")) {
            assertNotNull(held);
            assertFalse(UpdateApplier.runHelper(record, Long.MAX_VALUE, paths(), updates.resolve("helper.jar"), 3, 100));
        }
        UpdateRecord.Data result = UpdateRecord.read(record);
        assertEquals(UpdateRecord.Phase.FAILED, result.phase());
        assertTrue(result.detail().contains("after 3 attempts"), result.detail());
        assertTrue(result.detail().endsWith("The previous version is still installed."), result.detail());
        assertEquals("current", Files.readString(current));
        assertTrue(Files.exists(pending));
    }
}
