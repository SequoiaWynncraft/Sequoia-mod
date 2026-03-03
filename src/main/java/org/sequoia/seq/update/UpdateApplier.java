package org.sequoia.seq.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Helper process for Windows update installs.
 * Args: modsDir pendingJar finalJar helperJar
 */
public final class UpdateApplier {
    private UpdateApplier() {
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            return;
        }

        Path modsDir = Path.of(args[0]);
        Path pendingJar = Path.of(args[1]);
        Path finalJar = Path.of(args[2]);
        Path helperJar = Path.of(args[3]);

        boolean installed = applyWithRetries(modsDir, pendingJar, finalJar);
        if (installed) {
            deleteQuietly(pendingJar);
        }
        deleteQuietly(helperJar);
    }

    private static boolean applyWithRetries(Path modsDir, Path pendingJar, Path finalJar) {
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                deleteExistingSequoiaJars(modsDir);
                Files.move(pendingJar, finalJar, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (Exception ignored) {
                sleep(1500);
            }
        }
        return false;
    }

    private static void deleteExistingSequoiaJars(Path modsDir) {
        try (Stream<Path> stream = Files.list(modsDir)) {
            stream.filter(path -> path.getFileName().toString().startsWith("sequoia-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
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
