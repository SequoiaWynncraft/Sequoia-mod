package com.seqwawa.seq.update;

import static com.seqwawa.seq.update.UpdateFixtures.Body;
import static com.seqwawa.seq.update.UpdateFixtures.await;
import static com.seqwawa.seq.update.UpdateFixtures.fastLimits;
import static com.seqwawa.seq.update.UpdateFixtures.modJar;
import static com.seqwawa.seq.update.UpdateFixtures.sha256;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** What a player sees and gets from the update prompt, from startup through install and restart. */
class UpdateLifecycleTest {
    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String JAR_PATH = "/download/sequoia-1.12.0.jar";

    @TempDir
    Path gameDir;

    private UpdateFixtures.Server server;
    private UpdateFixtures.FakeGame game;
    private byte[] release;
    private final List<Process> processes = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = new UpdateFixtures.Server();
        game = new UpdateFixtures.FakeGame(gameDir);
        release = modJar("1.12.0");
        server.release("v1.12.0", release, true);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
        for (Process process : processes) process.destroyForcibly();
        Files.walk(gameDir).forEach(path -> path.toFile().setWritable(true));
    }

    private UpdateManager manager() {
        return UpdateFixtures.manager(game, server, fastLimits());
    }

    private static UpdateState awaitState(UpdateManager manager, Class<? extends UpdateState> type) throws InterruptedException {
        try {
            await(() -> type.isInstance(manager.state()), WAIT, type.getSimpleName());
        } catch (AssertionError timedOut) {
            throw new AssertionError(timedOut.getMessage() + " (was " + manager.state() + ")");
        }
        return manager.state();
    }

    private Path pending() {
        return game.updates().resolve("sequoia-1.12.0.jar.pending");
    }

    private Path recordFile() {
        return game.updates().resolve(UpdateRecord.FILE);
    }

    private UpdateRecord.Data record() throws IOException {
        return UpdateRecord.read(recordFile());
    }

    private void writeRecord(UpdateRecord.Phase phase, UpdateRecord.Method method, Instant updated) throws IOException {
        UpdateRecord.write(recordFile(), new UpdateRecord.Data("v1.12.0", "1.11.1", "sequoia-1.12.0.jar", sha256(release),
                release.length, method, phase, game.mods().resolve("sequoia-1.11.1.jar").toString(), 0, null, null,
                phase == UpdateRecord.Phase.FAILED ? "AccessDeniedException: mods" : null, updated));
    }

    // ---- Offer, progress, outcome ----

    @Test
    void startupOffersANewerReleaseInThePrompt() throws Exception {
        UpdateManager manager = manager();
        long before = System.nanoTime();
        manager.start(true);
        assertTrue(Duration.ofNanos(System.nanoTime() - before).toMillis() < 500, "start does not wait on the network");

        var available = (UpdateState.Available) awaitState(manager, UpdateState.Available.class);

        assertEquals("v1.12.0", available.release().tag());
        assertFalse(available.release().downloadedCopy());
        assertEquals(List.of(true), game.prompts);
    }

    @Test
    void updateShowsProgressThenInstallsAndKeepsThePreviousJarUntilItRuns() throws Exception {
        server.file(JAR_PATH, Body.of(release).paced(4_000, 50));
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);

        manager.update(false);
        var downloading = (UpdateState.Downloading) awaitState(manager, UpdateState.Downloading.class);
        await(() -> downloading.received() > 0, WAIT, "progress");
        awaitState(manager, UpdateState.Installed.class);

        assertArrayEquals(release, Files.readAllBytes(game.mods().resolve("sequoia-1.12.0.jar")));
        assertTrue(Files.exists(game.mods().resolve("sequoia-1.11.1.jar.previous")));
        assertFalse(Files.exists(game.mods().resolve("sequoia-1.11.1.jar")));
        assertEquals(UpdateRecord.Phase.INSTALLED, record().phase());
        assertEquals(0, game.stops.get());
    }

    @Test
    void updateAndExitClosesTheGameOnlyAfterASuccessfulInstall() throws Exception {
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);
        manager.update(true);
        awaitState(manager, UpdateState.Installed.class);
        assertEquals(1, game.stops.get());
        manager.exitNow();
        assertEquals(1, game.stops.get(), "exit is requested once");
    }

    @Test
    void aFailedDownloadIsShownAndNeverClosesTheGame() throws Exception {
        byte[] other = modJar("1.12.0");
        other[other.length - 1] ^= 1;
        server.file(JAR_PATH, Body.of(other));
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);

        manager.update(true);
        var failed = (UpdateState.Failed) awaitState(manager, UpdateState.Failed.class);

        assertEquals("The download does not match the release's checksum", failed.reason());
        assertEquals(0, game.stops.get());
        assertFalse(Files.exists(pending()));
        assertTrue(Files.exists(game.mods().resolve("sequoia-1.11.1.jar")));
    }

    @Test
    void cancelOffersTheReleaseAgainWithNothingLeftBehind() throws Exception {
        server.file(JAR_PATH, Body.of(release).stallingAfter(10_000));
        UpdateManager manager = new UpdateManager(game, "SequoiaWynncraft/Sequoia-mod", server.uri("/releases/latest"),
                new ReleaseDownload.Limits(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(60),
                        Duration.ofSeconds(120), 1024, Duration.ofMillis(50)), Duration.ofMillis(100), Duration.ofSeconds(1));
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);
        manager.update(true);
        var downloading = (UpdateState.Downloading) awaitState(manager, UpdateState.Downloading.class);
        await(() -> downloading.received() > 0, WAIT, "first bytes");

        manager.cancel();
        awaitState(manager, UpdateState.Available.class);

        assertFalse(Files.exists(game.updates().resolve("sequoia-1.12.0.jar.download")));
        assertFalse(Files.exists(recordFile()));
        assertEquals(0, game.stops.get());
    }

    @Test
    void repeatedClicksStartOneDownload() throws Exception {
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);
        manager.update(false);
        manager.update(false);
        manager.update(true);
        manager.retry();
        awaitState(manager, UpdateState.Installed.class);
        assertEquals(1, server.requests(JAR_PATH));
        assertEquals(0, game.stops.get(), "the later Update + Exit click was ignored");
    }

    @Test
    void retryAfterAFailureDownloadsAgain() throws Exception {
        server.file(JAR_PATH, Body.of(release).stallingAfter(10_000));
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);
        manager.update(false);
        assertTrue(((UpdateState.Failed) awaitState(manager, UpdateState.Failed.class)).reason().startsWith("No data received"));

        try (var fixed = new UpdateFixtures.Server()) {
            fixed.release("v1.12.0", release, true);
            UpdateManager again = UpdateFixtures.manager(game, fixed, fastLimits());
            again.start(true);
            awaitState(again, UpdateState.Available.class);
            again.update(false);
            awaitState(again, UpdateState.Installed.class);
        }
    }

    // ---- Verified files, compatibility, ownership ----

    @Test
    void aVerifiedStagedFileIsInstalledWithoutDownloadingAgain() throws Exception {
        Files.createDirectories(game.updates());
        Files.write(pending(), release);
        writeRecord(UpdateRecord.Phase.STAGED, UpdateRecord.Method.WINDOWS_HELPER, Instant.now());
        UpdateManager manager = manager();
        manager.start(true);

        var available = (UpdateState.Available) awaitState(manager, UpdateState.Available.class);
        assertTrue(available.previous().detail().contains("Minecraft closed before the installer started"));
        manager.update(false);
        awaitState(manager, UpdateState.Installed.class);

        assertEquals(0, server.requests(JAR_PATH));
    }

    private static final String CHECKSUM_PATH = JAR_PATH + ".sha256";

    @Test
    void aRecoveredCopyIsReusedWhenTheReleaseListsOnlyAChecksumFile() throws Exception {
        server.release("v1.12.0", release, false);
        Files.createDirectories(game.updates());
        Files.write(pending(), release);
        writeRecord(UpdateRecord.Phase.STAGED, UpdateRecord.Method.WINDOWS_HELPER, Instant.now());
        UpdateManager manager = manager();
        manager.start(true);
        var available = (UpdateState.Available) awaitState(manager, UpdateState.Available.class);
        assertNull(available.release().sha256(), "the release lists no digest");

        manager.update(false);
        awaitState(manager, UpdateState.Installed.class);

        assertEquals(0, server.requests(JAR_PATH), "the verified copy is installed, not downloaded again");
        assertEquals(1, server.requests(CHECKSUM_PATH));
        assertArrayEquals(release, Files.readAllBytes(game.mods().resolve("sequoia-1.12.0.jar")));
    }

    @Test
    void withOnlyAChecksumFileAStagedCopyWithOtherBytesIsReplaced() throws Exception {
        server.release("v1.12.0", release, false);
        Files.createDirectories(game.updates());
        byte[] tampered = release.clone();
        tampered[tampered.length / 2] ^= 1;
        Files.write(pending(), tampered);
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);

        manager.update(false);
        awaitState(manager, UpdateState.Installed.class);

        assertEquals(1, server.requests(JAR_PATH));
        assertArrayEquals(release, Files.readAllBytes(game.mods().resolve("sequoia-1.12.0.jar")));
    }

    @Test
    void aRecoveredCopyOfAReleaseThatWasSinceReplacedIsDownloadedAgain() throws Exception {
        byte[] earlierBuild = modJar("seq", "1.12.0", UpdateFixtures.DEPENDS, 30_000);
        Files.createDirectories(game.updates());
        Files.write(pending(), earlierBuild);
        UpdateRecord.write(recordFile(), new UpdateRecord.Data("v1.12.0", "1.11.1", "sequoia-1.12.0.jar",
                sha256(earlierBuild), earlierBuild.length, UpdateRecord.Method.WINDOWS_HELPER, UpdateRecord.Phase.STAGED,
                game.mods().resolve("sequoia-1.11.1.jar").toString(), 0, null, null, null, Instant.now()));
        server.release("v1.12.0", release, false);
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);

        manager.update(false);
        awaitState(manager, UpdateState.Installed.class);

        assertEquals(1, server.requests(JAR_PATH));
        assertArrayEquals(release, Files.readAllBytes(game.mods().resolve("sequoia-1.12.0.jar")));
    }

    @Test
    void aStagedFileWithTheRightNameButOtherBytesIsNeverInstalled() throws Exception {
        Files.createDirectories(game.updates());
        byte[] tampered = release.clone();
        tampered[tampered.length / 2] ^= 1;
        Files.write(pending(), tampered);
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);

        manager.update(false);
        awaitState(manager, UpdateState.Installed.class);

        assertEquals(1, server.requests(JAR_PATH));
        assertArrayEquals(release, Files.readAllBytes(game.mods().resolve("sequoia-1.12.0.jar")));
    }

    @Test
    void anIncompatibleReleaseIsExplainedAndNotDownloadedAgainByAnUnchangedGame() throws Exception {
        byte[] needsNewer = modJar("seq", "1.12.0", "{\"minecraft\":\"1.21.12\"}", 1000);
        server.release("v1.12.0", needsNewer, true);
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);
        manager.update(false);

        var incompatible = (UpdateState.Incompatible) awaitState(manager, UpdateState.Incompatible.class);
        assertEquals("Sequoia 1.12.0 needs minecraft 1.21.12; this game has 1.21.11", incompatible.reason());
        assertFalse(Files.exists(pending()));
        assertTrue(Files.exists(game.mods().resolve("sequoia-1.11.1.jar")));

        UpdateManager restarted = manager();
        restarted.start(true);
        awaitState(restarted, UpdateState.Incompatible.class);
        assertEquals(1, server.requests(JAR_PATH), "an unchanged game does not download it again");

        game.installed.put("minecraft", "1.21.12");
        UpdateManager upgraded = manager();
        upgraded.start(true);
        awaitState(upgraded, UpdateState.Available.class);
    }

    private byte[] incompatibleChecksumOnlyRelease() throws Exception {
        byte[] needsNewer = modJar("seq", "1.12.0", "{\"minecraft\":\"1.21.12\"}", 1000);
        server.release("v1.12.0", needsNewer, false);
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);
        manager.update(false);
        awaitState(manager, UpdateState.Incompatible.class);
        return needsNewer;
    }

    @Test
    void withOnlyAChecksumFileAnUnchangedIncompatibleReleaseIsNotDownloadedAgain() throws Exception {
        incompatibleChecksumOnlyRelease();
        assertEquals(1, server.requests(CHECKSUM_PATH));

        UpdateManager restarted = manager();
        restarted.start(true);
        var incompatible = (UpdateState.Incompatible) awaitState(restarted, UpdateState.Incompatible.class);
        assertEquals("Sequoia 1.12.0 needs minecraft 1.21.12; this game has 1.21.11", incompatible.reason());
        assertEquals(1, server.requests(JAR_PATH), "an unchanged release is not downloaded again");
        assertEquals(2, server.requests(CHECKSUM_PATH), "its checksum file shows it is unchanged");

        int requests = server.requests();
        UpdateManager offline = manager();
        offline.start(false);
        Thread.sleep(300);
        assertInstanceOf(UpdateState.Idle.class, offline.state());
        assertEquals(requests, server.requests(), "with checks off nothing goes online");

        game.installed.put("minecraft", "1.21.12");
        UpdateManager upgraded = manager();
        upgraded.start(true);
        awaitState(upgraded, UpdateState.Available.class);
        assertEquals(2, server.requests(CHECKSUM_PATH), "a changed game is offered it without reading the checksum");
    }

    @Test
    void withOnlyAChecksumFileAReplacedIncompatibleReleaseIsOfferedAgain() throws Exception {
        incompatibleChecksumOnlyRelease();
        server.release("v1.12.0", release, false);

        UpdateManager restarted = manager();
        restarted.start(true);
        awaitState(restarted, UpdateState.Available.class);
        restarted.update(false);
        awaitState(restarted, UpdateState.Installed.class);

        assertArrayEquals(release, Files.readAllBytes(game.mods().resolve("sequoia-1.12.0.jar")));
    }

    @Test
    void aChecksumFileThatCannotBeReadFailsTheCheckInsteadOfRepeatingTheOldVerdict() throws Exception {
        byte[] needsNewer = incompatibleChecksumOnlyRelease();
        String sidecar = sha256(needsNewer) + "  sequoia-1.12.0.jar\n";
        server.file(CHECKSUM_PATH, Body.of(sidecar.getBytes(java.nio.charset.StandardCharsets.UTF_8)).stallingAfter(10));

        UpdateManager restarted = manager();
        restarted.start(true);
        var failed = (UpdateState.CheckFailed) awaitState(restarted, UpdateState.CheckFailed.class);
        assertEquals("No data received for 1 s (checksum file)", failed.reason());

        server.text(CHECKSUM_PATH, 200, sidecar);
        restarted.checkNow();
        awaitState(restarted, UpdateState.Incompatible.class);
        assertEquals(1, server.requests(JAR_PATH));
    }

    @Test
    void aJarLoadedFromOutsideTheModsFolderIsNotReplaced() throws Exception {
        Path elsewhere = Files.createDirectories(gameDir.resolve("elsewhere")).resolve("sequoia-1.11.1.jar");
        Files.write(elsewhere, modJar("1.11.1"));
        game.runningJar = Optional.of(elsewhere);
        UpdateManager manager = manager();
        manager.start(true);

        var manual = (UpdateState.ManualOnly) awaitState(manager, UpdateState.ManualOnly.class);

        assertTrue(manual.reason().contains("outside this instance's mods folder"), manual.reason());
        manager.update(false);
        assertInstanceOf(UpdateState.ManualOnly.class, manager.state());
        assertEquals(0, server.requests(JAR_PATH));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void anUnwritableModsFolderNeedsAManualInstall() throws Exception {
        Files.setPosixFilePermissions(game.mods(), PosixFilePermissions.fromString("r-xr-xr-x"));
        UpdateManager manager = manager();
        manager.start(true);
        assertTrue(((UpdateState.ManualOnly) awaitState(manager, UpdateState.ManualOnly.class)).reason().contains("not writable"));
    }

    // ---- Update checks off ----

    @Test
    void withChecksOffAStagedUpdateIsOfferedFromDiskWithoutGoingOnline() throws Exception {
        Files.createDirectories(game.updates());
        Files.write(pending(), release);
        writeRecord(UpdateRecord.Phase.STAGED, UpdateRecord.Method.WINDOWS_HELPER, Instant.now());
        UpdateManager manager = manager();
        manager.start(false);

        var available = (UpdateState.Available) awaitState(manager, UpdateState.Available.class);
        assertTrue(available.release().downloadedCopy());
        manager.update(false);
        awaitState(manager, UpdateState.Installed.class);

        assertEquals(0, server.requests());
        assertArrayEquals(release, Files.readAllBytes(game.mods().resolve("sequoia-1.12.0.jar")));
    }

    @Test
    void withChecksOffAFailureIsShownAndOnlyAnExplicitCheckGoesOnline() throws Exception {
        writeRecord(UpdateRecord.Phase.FAILED, UpdateRecord.Method.IN_PROCESS, Instant.now());
        UpdateManager manager = manager();
        manager.start(false);

        var attention = (UpdateState.Attention) awaitState(manager, UpdateState.Attention.class);
        assertEquals("Last update to v1.12.0 failed: AccessDeniedException: mods", attention.previous().detail());
        Thread.sleep(300);
        assertEquals(0, server.requests());

        manager.checkNow();
        awaitState(manager, UpdateState.Available.class);
        assertEquals(1, server.requests());
        assertEquals(1, server.requests("/releases/latest"));
    }

    @Test
    void withChecksOffAndNothingRecordedNothingHappens() throws Exception {
        UpdateManager manager = manager();
        manager.start(false);
        Thread.sleep(300);
        assertInstanceOf(UpdateState.Idle.class, manager.state());
        assertEquals(0, server.requests());
        assertEquals(List.of(), game.prompts);
    }

    // ---- Reconciling after a restart ----

    @Test
    void theRunningTargetConfirmsSuccessAndClearsWhatTheAttemptKept() throws Exception {
        Path backup = game.mods().resolve("sequoia-1.11.1.jar.previous");
        Files.writeString(backup, "previous");
        writeRecord(UpdateRecord.Phase.INSTALLED, UpdateRecord.Method.IN_PROCESS, Instant.now().minusSeconds(120));
        game.running = "1.12.0";
        game.runningJar = Optional.of(game.mods().resolve("sequoia-1.12.0.jar"));
        UpdateManager manager = manager();
        manager.start(true);

        awaitState(manager, UpdateState.UpToDate.class);

        assertEquals(List.of("Sequoia updated: Sequoia 1.12.0 is running."), game.toasts);
        assertFalse(Files.exists(recordFile()));
        assertFalse(Files.exists(backup));
    }

    @Test
    void anOlderTargetIsStaleAndQuietlyDiscarded() throws Exception {
        UpdateRecord.write(recordFile(), new UpdateRecord.Data("v1.11.0", "1.10.0", "sequoia-1.11.0.jar", sha256(release),
                release.length, UpdateRecord.Method.IN_PROCESS, UpdateRecord.Phase.FAILED, null, 0, null, null, "x",
                Instant.now()));
        UpdateManager manager = manager();
        manager.start(false);
        await(() -> !Files.exists(recordFile()), WAIT, "record removed");
        assertEquals(List.of(), game.toasts);
    }

    @Test
    void anUnreadableRecordIsDiscardedWithoutBlockingStartup() throws Exception {
        Files.createDirectories(game.updates());
        Files.writeString(recordFile(), "format=99\n");
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);
        assertFalse(Files.exists(recordFile()));
    }

    @Test
    void notesStateOnlyWhatTheRecordAndTheDiskShow() throws Exception {
        record Case(UpdateRecord.Phase phase, UpdateRecord.Method method, String expected) {}
        for (Case c : List.of(
                new Case(UpdateRecord.Phase.APPLYING, UpdateRecord.Method.IN_PROCESS,
                        "The previous install of v1.12.0 stopped before finishing. Sequoia 1.11.1 is running."),
                new Case(UpdateRecord.Phase.APPLYING, UpdateRecord.Method.WINDOWS_HELPER,
                        "The installer for v1.12.0 stopped before finishing (see updates/update-helper.log). Sequoia 1.11.1 is running."),
                new Case(UpdateRecord.Phase.INSTALLED, UpdateRecord.Method.WINDOWS_HELPER,
                        "v1.12.0 was installed but Minecraft started 1.11.1. Another copy of Sequoia or your launcher may have replaced it."))) {
            writeRecord(c.phase(), c.method(), Instant.now().minusSeconds(300));
            UpdateManager manager = manager();
            manager.start(false);
            var attention = (UpdateState.Attention) awaitState(manager, UpdateState.Attention.class);
            assertEquals(c.expected(), attention.previous().detail());
        }
    }

    @Test
    void anInstallFinishedAfterThisGameStartedNeedsOnlyARestart() throws Exception {
        game.jvmStart = Instant.now().minusSeconds(5);
        writeRecord(UpdateRecord.Phase.INSTALLED, UpdateRecord.Method.WINDOWS_HELPER, Instant.now());
        UpdateManager manager = manager();
        manager.start(true);
        var installed = (UpdateState.Installed) awaitState(manager, UpdateState.Installed.class);
        assertEquals("v1.12.0", installed.release().tag());
        assertEquals(0, server.requests());
    }

    // ---- Windows: staging, the helper and the live-installer guard ----

    private UpdateManager staged() throws Exception {
        game.windows = true;
        UpdateManager manager = manager();
        manager.start(true);
        awaitState(manager, UpdateState.Available.class);
        manager.update(false);
        awaitState(manager, UpdateState.Staged.class);
        return manager;
    }

    @Test
    void onWindowsTheUpdateIsStagedForExitAndCanBeWithdrawn() throws Exception {
        UpdateManager manager = staged();
        assertEquals(UpdateRecord.Phase.STAGED, record().phase());
        assertTrue(Files.exists(pending()));
        assertEquals(1, game.hooks.size());

        manager.cancelStaged();
        awaitState(manager, UpdateState.Available.class);
        await(() -> !Files.exists(recordFile()), WAIT, "the install is withdrawn");
        game.hooks.getFirst().run();
        assertEquals(List.of(), game.helpers, "a withdrawn install starts no helper");

        UpdateManager restarted = manager();
        restarted.start(false);
        await(() -> !Files.exists(pending()), WAIT, "the next start removes the unreferenced download");
    }

    @Test
    void cancellingARecoveredStagedInstallStillOffersAWorkingInstall() throws Exception {
        Files.createDirectories(game.updates());
        Files.write(pending(), release);
        writeRecord(UpdateRecord.Phase.STAGED, UpdateRecord.Method.WINDOWS_HELPER, Instant.now());
        game.windows = true;
        UpdateManager manager = manager();
        manager.start(false);
        assertTrue(((UpdateState.Available) awaitState(manager, UpdateState.Available.class)).release().downloadedCopy());
        manager.update(false);
        awaitState(manager, UpdateState.Staged.class);

        manager.cancelStaged();
        var offered = (UpdateState.Available) awaitState(manager, UpdateState.Available.class);
        await(() -> !Files.exists(recordFile()), WAIT, "the install is withdrawn");

        assertTrue(offered.release().downloadedCopy());
        assertTrue(Files.exists(pending()), "the verified download the prompt offers is still there");
        game.hooks.getFirst().run();
        assertEquals(List.of(), game.helpers, "nothing installs at exit");
        manager.update(false);
        awaitState(manager, UpdateState.Staged.class);
        assertEquals(UpdateRecord.Phase.STAGED, record().phase());
        assertEquals(0, server.requests());
    }

    @Test
    void anInstallerThatCannotStartIsRecordedAsSuch() throws Exception {
        staged();
        game.failHelperStart = true;
        game.hooks.getFirst().run();
        UpdateRecord.Data result = record();
        assertEquals(UpdateRecord.Phase.FAILED, result.phase());
        assertEquals("The installer could not start: Cannot run program", result.detail());
    }

    /** Stages, then exits the "game" while a stand-in process keeps the helper waiting for it. */
    private Process exitWithHelperWaiting() throws Exception {
        staged();
        Process stillRunning = Sleeper.start();
        processes.add(stillRunning);
        game.pid = stillRunning.pid();
        game.hooks.getFirst().run();
        UpdateRecord.Data launched = record();
        assertEquals(UpdateRecord.Phase.LAUNCHED, launched.phase(), String.valueOf(launched));
        assertTrue(launched.helperPid() > 0);
        await(() -> UpdateManager.lockHeld(game.updates().resolve(UpdateApplier.LOCK_FILE)), WAIT, "installer lock");
        return stillRunning;
    }

    private UpdateManager relaunch() throws IOException {
        game = new UpdateFixtures.FakeGame(gameDir);
        game.windows = true;
        game.jvmStart = Instant.now();
        return manager();
    }

    @Test
    void aQuickRelaunchWaitsForTheRunningInstallerThenNeedsOnlyARestart() throws Exception {
        Process previousGame = exitWithHelperWaiting();
        UpdateManager manager = relaunch();
        manager.start(true);

        awaitState(manager, UpdateState.WaitingForInstaller.class);
        Thread.sleep(400);
        manager.update(false);
        manager.checkNow();
        assertInstanceOf(UpdateState.WaitingForInstaller.class, manager.state());
        for (Path owned : List.of(pending(), recordFile(), game.updates().resolve(UpdateManager.HELPER_JAR),
                game.updates().resolve(UpdateApplier.LOCK_FILE))) assertTrue(Files.exists(owned), owned + " left alone");

        previousGame.destroy();
        var installed = (UpdateState.Installed) awaitState(manager, UpdateState.Installed.class);
        assertEquals("v1.12.0", installed.release().tag());
        assertArrayEquals(release, Files.readAllBytes(game.mods().resolve("sequoia-1.12.0.jar")));
    }

    @Test
    void aRelaunchSeesTheInstallersFailureOnceItEnds() throws Exception {
        Process previousGame = exitWithHelperWaiting();
        UpdateManager manager = relaunch();
        manager.start(true);
        awaitState(manager, UpdateState.WaitingForInstaller.class);

        byte[] changed = release.clone();
        changed[10] ^= 1;
        Files.write(pending(), changed);
        previousGame.destroy();

        var available = (UpdateState.Available) awaitState(manager, UpdateState.Available.class);
        assertEquals("Last update to v1.12.0 failed: The staged update changed since it was verified",
                available.previous().detail());
        assertTrue(Files.exists(game.mods().resolve("sequoia-1.11.1.jar")));
    }

    @Test
    void anInstallerThatDiedLeavesTheFilesToTheGameAgain() throws Exception {
        exitWithHelperWaiting();
        long helper = record().helperPid();
        ProcessHandle.of(helper).orElseThrow().destroyForcibly();
        UpdateManager manager = relaunch();
        manager.start(false);

        var available = (UpdateState.Available) awaitState(manager, UpdateState.Available.class);
        assertTrue(available.release().downloadedCopy(), "the verified download is still offered");
        assertEquals("The installer for v1.12.0 stopped before finishing (see updates/update-helper.log). "
                + "Sequoia 1.11.1 is running.", available.previous().detail());
        assertFalse(Files.exists(game.updates().resolve(UpdateManager.HELPER_JAR)));
    }

    @Test
    void aReusedProcessIdIsNotMistakenForTheInstaller() throws Exception {
        Process unrelated = Sleeper.start();
        processes.add(unrelated);
        UpdateRecord.Data launched = new UpdateRecord.Data("v1.12.0", "1.11.1", "sequoia-1.12.0.jar", sha256(release),
                release.length, UpdateRecord.Method.WINDOWS_HELPER, UpdateRecord.Phase.LAUNCHED, null, unrelated.pid(),
                Instant.EPOCH.toString(), null, null, Instant.now());
        UpdateManager manager = manager();
        assertFalse(manager.installerAlive(launched, game.updates()));
    }

    @Test
    void withoutAStartInstantOnlyTheLockDecidesAfterAShortGrace() throws Exception {
        Process launchedHelper = Sleeper.start();
        processes.add(launchedHelper);
        UpdateManager manager = manager();
        UpdateRecord.Data fresh = new UpdateRecord.Data("v1.12.0", "1.11.1", "sequoia-1.12.0.jar", sha256(release),
                release.length, UpdateRecord.Method.WINDOWS_HELPER, UpdateRecord.Phase.LAUNCHED, null, launchedHelper.pid(),
                null, null, null, Instant.now());
        assertTrue(manager.installerAlive(fresh, game.updates()), "just launched, before it takes the lock");
        UpdateRecord.Data old = new UpdateRecord.Data(fresh.target(), fresh.from(), fresh.jar(), fresh.sha256(), fresh.size(),
                fresh.method(), fresh.phase(), null, fresh.helperPid(), null, null, null, Instant.now().minusSeconds(5));
        assertFalse(manager.installerAlive(old, game.updates()));
        assertNull(old.helperStart());
    }
}
