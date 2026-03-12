package org.sequoia.seq.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Helper process for Windows update installs.
 * Args: modsDir currentJar pendingJar finalJar helperJar
 */
public final class UpdateApplier {
    private static final int DEFAULT_RETRY_ATTEMPTS = 40;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 1500;

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
        boolean installed = applyWithRetries(modsDir, currentJar, pendingJar, finalJar, retryAttempts, retryDelayMillis);
        if (installed) {
            deleteQuietly(pendingJar);
        }
        deleteQuietly(helperJar);
    }

    static boolean applyWithRetries(
            Path modsDir, Path currentJar, Path pendingJar, Path finalJar, int retryAttempts, long retryDelayMillis) {
        for (int attempt = 0; attempt < retryAttempts; attempt++) {
            try {
                deleteReplacedJar(currentJar, finalJar);
                Files.move(pendingJar, finalJar, StandardCopyOption.REPLACE_EXISTING);
                deleteStaleSequoiaJars(modsDir, finalJar);
                return true;
            } catch (Exception ignored) {
                sleep(retryDelayMillis);
            }
        }
        return false;
    }

    private static void deleteReplacedJar(Path currentJar, Path finalJar) throws Exception {
        if (currentJar.equals(finalJar)) {
            return;
        }
        Files.deleteIfExists(currentJar);
    }

    private static void deleteStaleSequoiaJars(Path modsDir, Path finalJar) {
        try (Stream<Path> stream = Files.list(modsDir)) {
            stream.filter(path -> path.getFileName().toString().startsWith("sequoia-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.equals(finalJar))
                    .forEach(UpdateApplier::deleteQuietly);
        } catch (Exception ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
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
