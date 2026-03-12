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

        boolean installed = applyWithRetries(modsDir, currentJar, pendingJar, finalJar);
        if (installed) {
            deleteQuietly(pendingJar);
        }
        deleteQuietly(helperJar);
    }

    private static boolean applyWithRetries(Path modsDir, Path currentJar, Path pendingJar, Path finalJar) {
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                deleteReplacedJar(currentJar, finalJar);
                Files.move(pendingJar, finalJar, StandardCopyOption.REPLACE_EXISTING);
                deleteStaleSequoiaJars(modsDir, finalJar);
                return true;
            } catch (Exception ignored) {
                sleep(1500);
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
