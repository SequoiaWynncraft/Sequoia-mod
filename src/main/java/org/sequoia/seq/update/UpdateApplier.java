package org.sequoia.seq.update;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Helper process for Windows update installs.
 * Args: modsDir currentJar pendingJar finalJar helperJar
 */
public final class UpdateApplier {
    private static final int DEFAULT_RETRY_ATTEMPTS = 40;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 1500;
    private static final Consumer<String> DEFAULT_LOGGER = message -> System.err.println("[SeqUpdater] " + message);

    private UpdateApplier() {
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            return;
        }

        Path modsDir = Path.of(args[0]);
        Path currentJar = Path.of(args[1]);
        Path pendingJar = Path.of(args[2]);
        Path finalJar = Path.of(args[3]);
        Path helperJar = Path.of(args[4]);

        run(modsDir, currentJar, pendingJar, finalJar, helperJar, DEFAULT_RETRY_ATTEMPTS, DEFAULT_RETRY_DELAY_MILLIS);
    }

    static void run(
            Path modsDir,
            Path currentJar,
            Path pendingJar,
            Path finalJar,
            Path helperJar,
            int retryAttempts,
            long retryDelayMillis) {
        run(
                modsDir,
                currentJar,
                pendingJar,
                finalJar,
                helperJar,
                retryAttempts,
                retryDelayMillis,
                Files::exists,
                path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                (source, destination) -> {
                    try {
                        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                directory -> {
                    try (Stream<Path> stream = Files.list(directory)) {
                        return stream.toList();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                UpdateApplier::sleep,
                DEFAULT_LOGGER);
    }

    static void run(
            Path modsDir,
            Path currentJar,
            Path pendingJar,
            Path finalJar,
            Path helperJar,
            int retryAttempts,
            long retryDelayMillis,
            Predicate<Path> exists,
            Consumer<Path> deleteIfExists,
            BiConsumer<Path, Path> move,
            Function<Path, List<Path>> list,
            LongConsumer sleeper,
            Consumer<String> logger) {
        boolean installed = applyWithRetries(
                modsDir,
                currentJar,
                pendingJar,
                finalJar,
                retryAttempts,
                retryDelayMillis,
                exists,
                deleteIfExists,
                move,
                list,
                sleeper,
                logger);
        if (installed) {
            deleteQuietly(pendingJar, deleteIfExists);
        }
        cleanupHelperJar(helperJar, exists, deleteIfExists, logger);
    }

    static boolean applyWithRetries(
            Path modsDir, Path currentJar, Path pendingJar, Path finalJar, int retryAttempts, long retryDelayMillis) {
        return applyWithRetries(
                modsDir,
                currentJar,
                pendingJar,
                finalJar,
                retryAttempts,
                retryDelayMillis,
                Files::exists,
                path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                (source, destination) -> {
                    try {
                        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                directory -> {
                    try (Stream<Path> stream = Files.list(directory)) {
                        return stream.toList();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                UpdateApplier::sleep,
                DEFAULT_LOGGER);
    }

    static boolean applyWithRetries(
            Path modsDir,
            Path currentJar,
            Path pendingJar,
            Path finalJar,
            int retryAttempts,
            long retryDelayMillis,
            Predicate<Path> exists,
            Consumer<Path> deleteIfExists,
            BiConsumer<Path, Path> move,
            Function<Path, List<Path>> list,
            LongConsumer sleeper,
            Consumer<String> logger) {
        Exception lastFailure = null;

        for (int attempt = 0; attempt < retryAttempts; attempt++) {
            try {
                applyInstallAttempt(modsDir, currentJar, pendingJar, finalJar, exists, deleteIfExists, move, list);
                return true;
            } catch (Exception e) {
                lastFailure = e;
                logger.accept("Update install attempt " + (attempt + 1) + "/" + retryAttempts
                        + " failed: " + failureMessage(e));
                if (attempt + 1 < retryAttempts) {
                    sleeper.accept(retryDelayMillis);
                }
            }
        }

        if (lastFailure != null) {
            logger.accept(
                    "Windows update helper gave up after " + retryAttempts + " attempts: " + failureMessage(lastFailure));
        }
        return false;
    }

    private static void applyInstallAttempt(
            Path modsDir,
            Path currentJar,
            Path pendingJar,
            Path finalJar,
            Predicate<Path> exists,
            Consumer<Path> deleteIfExists,
            BiConsumer<Path, Path> move,
            Function<Path, List<Path>> list)
            throws IOException {
        Path backupJar = backupJarPath(currentJar);
        stageCurrentJarForReplacement(currentJar, pendingJar, finalJar, backupJar, exists, move);
        try {
            movePendingJarIntoPlace(pendingJar, finalJar, currentJar, backupJar, exists, move);
        } catch (IOException e) {
            restoreCurrentJarIfNeeded(currentJar, finalJar, backupJar, exists, move);
            throw e;
        }
        deleteQuietly(backupJar, deleteIfExists);
        deleteStaleSequoiaJars(modsDir, finalJar, deleteIfExists, list);
    }

    private static void stageCurrentJarForReplacement(
            Path currentJar,
            Path pendingJar,
            Path finalJar,
            Path backupJar,
            Predicate<Path> exists,
            BiConsumer<Path, Path> move)
            throws IOException {
        if (currentJar.equals(finalJar)) {
            return;
        }

        if (exists.test(backupJar) || !exists.test(currentJar)) {
            return;
        }

        if (!exists.test(pendingJar) && !exists.test(finalJar)) {
            throw new IOException("Pending update jar missing; keeping existing installed jar.");
        }

        movePath(currentJar, backupJar, move);
    }

    private static void movePendingJarIntoPlace(
            Path pendingJar,
            Path finalJar,
            Path currentJar,
            Path backupJar,
            Predicate<Path> exists,
            BiConsumer<Path, Path> move)
            throws IOException {
        if (exists.test(pendingJar)) {
            movePath(pendingJar, finalJar, move);
            return;
        }
        if (exists.test(finalJar)) {
            return;
        }
        if (exists.test(backupJar)) {
            throw new IOException("Pending update jar missing; restoring previous jar.");
        }
        if (!currentJar.equals(finalJar) && exists.test(currentJar)) {
            throw new IOException("Pending update jar missing; keeping existing installed jar.");
        }
        throw new IOException("Pending update jar disappeared before install completed.");
    }

    private static void restoreCurrentJarIfNeeded(
            Path currentJar, Path finalJar, Path backupJar, Predicate<Path> exists, BiConsumer<Path, Path> move)
            throws IOException {
        if (currentJar.equals(finalJar)) {
            return;
        }
        if (!exists.test(backupJar) || exists.test(currentJar) || exists.test(finalJar)) {
            return;
        }
        movePath(backupJar, currentJar, move);
    }

    private static Path backupJarPath(Path currentJar) {
        return currentJar.resolveSibling(currentJar.getFileName().toString() + ".previous");
    }

    private static void deleteStaleSequoiaJars(
            Path modsDir, Path finalJar, Consumer<Path> deleteIfExists, Function<Path, List<Path>> list)
            throws IOException {
        for (Path path : listStaleSequoiaJars(modsDir, finalJar, list)) {
            try {
                deletePath(path, deleteIfExists);
            } catch (IOException ignored) {
                // The remaining-files check below turns transient delete failures into retries.
            }
        }

        List<Path> remaining = listStaleSequoiaJars(modsDir, finalJar, list);
        if (!remaining.isEmpty()) {
            throw new IOException("Failed to delete stale Sequoia jars: " + formatPaths(remaining));
        }
    }

    private static List<Path> listStaleSequoiaJars(Path modsDir, Path finalJar, Function<Path, List<Path>> list)
            throws IOException {
        List<Path> staleJars = new ArrayList<>();
        for (Path path : listPaths(modsDir, list)) {
            String fileName = path.getFileName().toString();
            if (!fileName.startsWith("sequoia-") || !fileName.endsWith(".jar")) {
                continue;
            }
            if (path.equals(finalJar)) {
                continue;
            }
            staleJars.add(path);
        }
        return staleJars;
    }

    private static String formatPaths(List<Path> paths) {
        return paths.stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("unknown");
    }

    private static String failureMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static void deleteQuietly(Path path, Consumer<Path> deleteIfExists) {
        try {
            deletePath(path, deleteIfExists);
        } catch (Exception ignored) {
        }
    }

    private static void cleanupHelperJar(
            Path helperJar, Predicate<Path> exists, Consumer<Path> deleteIfExists, Consumer<String> logger) {
        deleteQuietly(helperJar, deleteIfExists);
        if (exists.test(helperJar) && isWindows()) {
            logger.accept("Scheduling delayed deletion for helper jar " + helperJar.getFileName());
            scheduleWindowsDelete(helperJar);
        }
    }

    private static void deletePath(Path path, Consumer<Path> deleteIfExists) throws IOException {
        try {
            deleteIfExists.accept(path);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void movePath(Path source, Path destination, BiConsumer<Path, Path> move) throws IOException {
        try {
            move.accept(source, destination);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static List<Path> listPaths(Path directory, Function<Path, List<Path>> list) throws IOException {
        try {
            return list.apply(directory);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void scheduleWindowsDelete(Path path) {
        try {
            new ProcessBuilder(
                            "cmd.exe",
                            "/c",
                            "ping 127.0.0.1 -n 3 > nul && del /f /q \"" + path + "\"")
                    .start();
        } catch (Exception ignored) {
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
