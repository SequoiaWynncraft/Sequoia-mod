package org.sequoia.seq.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
