package com.seqwawa.seq.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Replaces the installed Sequoia JAR with a verified update: in the game on Linux and macOS, and on
 * Windows in a separate process started as the game exits, because the running game keeps its own
 * JAR locked until then.
 *
 * <p>The Windows helper runs this class with only the JDK and a copy of the mod JAR on its class
 * path, so this class and {@link UpdateRecord} use nothing else.
 *
 * <p>Helper arguments: recordFile parentPid modsDir currentJar pendingJar finalJar helperJar.
 */
public final class UpdateApplier {
    /** Held by the helper from its first action until it exits; the OS releases it on any death. */
    static final String LOCK_FILE = "update-helper.lock";
    /** Versioned Sequoia JARs the updater may remove; other files that start with "sequoia-" are not its own. */
    static final Pattern VERSIONED_JAR = Pattern.compile("sequoia-\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?\\.jar");
    private static final int HELPER_ATTEMPTS = 40;
    private static final long HELPER_RETRY_MILLIS = 1500;
    private static final long PARENT_WAIT_SECONDS = 120;
    private static final int LOCK_ATTEMPTS = 50;
    private static final long LOCK_RETRY_MILLIS = 100;

    /** The paths one swap works on. */
    record Paths(Path modsDir, Path currentJar, Path pendingJar, Path finalJar) {
        /** The replaced JAR, kept until the new version is seen running. Fabric loads only *.jar. */
        Path backupJar() {
            return currentJar.resolveSibling(currentJar.getFileName() + ".previous");
        }
    }

    /** @param detail what happened, as facts found on disk afterwards */
    record Outcome(boolean installed, String detail) {}

    /** File operations, replaceable in tests to fail at any step. */
    interface Disk {
        boolean exists(Path path);

        void move(Path source, Path target) throws IOException;

        void delete(Path path) throws IOException;

        List<Path> list(Path directory) throws IOException;
    }

    static final Disk DISK = new Disk() {
        @Override
        public boolean exists(Path path) {
            return Files.exists(path);
        }

        @Override
        public void move(Path source, Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void delete(Path path) throws IOException {
            Files.deleteIfExists(path);
        }

        @Override
        public List<Path> list(Path directory) throws IOException {
            try (Stream<Path> stream = Files.list(directory)) {
                return stream.toList();
            }
        }
    };

    private UpdateApplier() {}

    public static void main(String[] args) {
        if (args.length != 7) {
            log("Expected 7 arguments, got " + args.length + "; nothing was changed");
            System.exit(2);
        }
        boolean installed = runHelper(
                Path.of(args[0]),
                Long.parseLong(args[1]),
                new Paths(Path.of(args[2]), Path.of(args[3]), Path.of(args[4]), Path.of(args[5])),
                Path.of(args[6]),
                HELPER_ATTEMPTS,
                HELPER_RETRY_MILLIS);
        System.exit(installed ? 0 : 1);
    }

    /**
     * The Windows helper: takes the installer lock, checks the staged file against the record,
     * waits for the game to exit, swaps the JARs and records how that ended.
     */
    static boolean runHelper(Path recordFile, long parentPid, Paths paths, Path helperJar, int attempts, long retryMillis) {
        Path lockFile = recordFile.resolveSibling(LOCK_FILE);
        UpdateRecord.Data record = null;
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = acquire(channel)) {
            if (lock == null) {
                log("Another installer holds " + lockFile + "; nothing was changed");
                return false;
            }
            UpdateRecord.Data read = UpdateRecord.read(recordFile);
            if (read == null
                    || (read.phase() != UpdateRecord.Phase.LAUNCHED && read.phase() != UpdateRecord.Phase.STAGED)
                    || !paths.pendingJar().getFileName().toString().equals(read.jar() + ".pending")
                    || !paths.finalJar().getFileName().toString().equals(read.jar())) {
                log("The update record does not describe " + paths.pendingJar() + "; nothing was changed");
                return false;
            }
            ProcessHandle self = ProcessHandle.current();
            long pid = self.pid();
            record = read.process(UpdateRecord.Phase.APPLYING, pid, self.info().startInstant().orElse(null),
                    "Installer running (process " + pid + ")");
            UpdateRecord.write(recordFile, record);
            log("Installer " + pid + " installing " + record.target());
            waitForParent(parentPid);

            String problem = stagedProblem(paths.pendingJar(), record.size(), record.sha256());
            if (problem != null) {
                Files.deleteIfExists(paths.pendingJar());
                UpdateRecord.write(recordFile, record.with(UpdateRecord.Phase.FAILED, problem));
                log(problem);
                return false;
            }
            Outcome outcome = swap(paths, attempts, retryMillis, DISK, UpdateApplier::sleep, UpdateApplier::log);
            UpdateRecord.write(recordFile, record.with(
                    outcome.installed() ? UpdateRecord.Phase.INSTALLED : UpdateRecord.Phase.FAILED, outcome.detail()));
            log(outcome.detail());
            return outcome.installed();
        } catch (IOException | RuntimeException failure) {
            log("Installer failed: " + failure);
            if (record != null) {
                try {
                    UpdateRecord.write(recordFile, record.with(UpdateRecord.Phase.FAILED,
                            "The installer stopped with an error: " + failure.getMessage() + ". " + onDisk(paths, DISK)));
                } catch (IOException unwritable) {
                    log("Could not record the failure: " + unwritable);
                }
            }
            return false;
        } finally {
            deleteHelperJar(helperJar);
        }
    }

    /**
     * The installer lock, or null if another installer keeps it. A game checking whether an installer
     * is running holds it for an instant, so a few retries tell that apart from a real owner.
     */
    private static FileLock acquire(FileChannel channel) throws IOException {
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            FileLock lock = channel.tryLock();
            if (lock != null) return lock;
            sleep(LOCK_RETRY_MILLIS);
        }
        return null;
    }

    private static void waitForParent(long parentPid) {
        var parent = ProcessHandle.of(parentPid);
        if (parent.isEmpty()) {
            log("Minecraft (process " + parentPid + ") has exited");
            return;
        }
        try {
            parent.get().onExit().get(PARENT_WAIT_SECONDS, TimeUnit.SECONDS);
            log("Minecraft (process " + parentPid + ") has exited");
        } catch (java.util.concurrent.TimeoutException stillRunning) {
            log("Minecraft (process " + parentPid + ") is still running after " + PARENT_WAIT_SECONDS + " s; trying anyway");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException unexpected) {
            log("Could not wait for Minecraft: " + unexpected.getCause());
        }
    }

    /** Null if {@code staged} holds exactly the recorded bytes, else why not. */
    static String stagedProblem(Path staged, long size, String sha256) {
        try {
            if (!Files.exists(staged)) return "The staged update is missing";
            if (Files.size(staged) != size) return "The staged update changed size";
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
            try (InputStream in = Files.newInputStream(staged)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = in.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(sha256)
                    ? null
                    : "The staged update changed since it was verified";
        } catch (IOException unreadable) {
            return "The staged update could not be read: " + unreadable.getMessage();
        }
    }

    /**
     * Moves the current JAR aside, the staged JAR into place and removes other versioned Sequoia
     * JARs, retrying while files are locked. The order never leaves two loadable Sequoia JARs in
     * mods; a failed move puts the previous JAR back.
     */
    static Outcome swap(Paths paths, int attempts, long retryMillis, Disk disk, LongConsumer sleeper, Consumer<String> log) {
        String lastFailure = null;
        int attempt = 0;
        while (attempt < attempts) {
            attempt++;
            try {
                attempt(paths, disk);
                return new Outcome(true, "Installed " + paths.finalJar().getFileName());
            } catch (IOException failure) {
                lastFailure = describe(failure);
                log.accept("Install attempt " + attempt + "/" + attempts + " failed: " + lastFailure);
                if (attempt < attempts) sleeper.accept(retryMillis);
            }
        }
        return new Outcome(false, "Could not install " + paths.finalJar().getFileName() + " after " + attempt
                + (attempt == 1 ? " attempt: " : " attempts: ") + lastFailure + ". " + onDisk(paths, disk));
    }

    private static void attempt(Paths paths, Disk disk) throws IOException {
        Path backup = paths.backupJar();
        boolean replacing = !paths.currentJar().equals(paths.finalJar());
        // A present current JAR always moves aside. A backup already beside it is from an earlier,
        // finished install, and the JAR being replaced now is the one worth keeping.
        if (replacing && disk.exists(paths.currentJar())) {
            if (!disk.exists(paths.pendingJar()) && !disk.exists(paths.finalJar()))
                throw new IOException("The staged update is missing");
            disk.move(paths.currentJar(), backup);
        }
        try {
            if (disk.exists(paths.pendingJar())) {
                disk.move(paths.pendingJar(), paths.finalJar());
            } else if (!disk.exists(paths.finalJar())) {
                throw new IOException("The staged update is missing");
            }
        } catch (IOException failure) {
            if (replacing && disk.exists(backup) && !disk.exists(paths.currentJar()) && !disk.exists(paths.finalJar()))
                disk.move(backup, paths.currentJar());
            throw failure;
        }
        List<Path> stale = new ArrayList<>();
        for (Path path : disk.list(paths.modsDir())) {
            if (VERSIONED_JAR.matcher(path.getFileName().toString()).matches() && !path.equals(paths.finalJar())) {
                try {
                    disk.delete(path);
                } catch (IOException busy) {
                    stale.add(path);
                }
            }
        }
        if (!stale.isEmpty()) throw new IOException("Could not remove " + stale.getFirst().getFileName());
    }

    /** Which JARs are where, checked rather than assumed. */
    private static String onDisk(Paths paths, Disk disk) {
        if (disk.exists(paths.finalJar()) && !paths.finalJar().equals(paths.currentJar()))
            return "The new version is in mods.";
        if (disk.exists(paths.currentJar())) return "The previous version is still installed.";
        if (disk.exists(paths.backupJar()))
            return "Neither version is in mods; the previous one is at " + paths.backupJar() + ".";
        return "Neither version is in mods.";
    }

    /** The message, or for errors like AccessDeniedException whose message is only a path, type and path. */
    private static String describe(IOException failure) {
        if (failure instanceof FileSystemException fileSystem && fileSystem.getReason() == null)
            return fileSystem.getClass().getSimpleName() + ": " + fileSystem.getFile();
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static void deleteHelperJar(Path helperJar) {
        try {
            Files.deleteIfExists(helperJar);
        } catch (IOException inUse) {
            // The JVM running this class has the JAR open on Windows; the next game start removes it.
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String message) {
        System.out.println(Instant.now() + " " + message);
        System.out.flush();
    }
}
