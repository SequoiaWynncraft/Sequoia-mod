package com.seqwawa.seq.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seqwawa.seq.ui.UpdatePromptScreen;
import com.seqwawa.seq.update.UpdateState.Asset;
import com.seqwawa.seq.update.UpdateState.Attempt;
import com.seqwawa.seq.update.UpdateState.Release;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.slf4j.Logger;

/**
 * Updates Sequoia: checks GitHub for a release, downloads and verifies it, installs it and
 * reconciles what an earlier session left behind.
 *
 * <p>The current {@link UpdateState} is everything the update prompt shows, and only this class
 * changes it. Network and file work runs on one worker thread, so the game thread never waits on
 * it. Each public action applies only in the states it names and is otherwise ignored, so a
 * repeated or late click does nothing.
 */
public final class UpdateManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "seq";
    private static final String DEFAULT_REPOSITORY = "SequoiaWynncraft/Sequoia-mod";
    /** Points update checks at another GitHub repository's releases, to try a release candidate. */
    static final String REPOSITORY_PROPERTY = "sequoia.updater.repository";
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9-]+/[A-Za-z0-9._-]+");
    private static final Pattern SEMVER = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final Pattern DIGEST = Pattern.compile("(?i)sha256:([0-9a-f]{64})");
    private static final Pattern CANDIDATE = Pattern.compile("sequoia-.+\\.jar\\.(download|pending)");
    private static final long MAX_ASSET_BYTES = 128L * 1024 * 1024;
    private static final long FREE_SPACE_MARGIN = 16L * 1024 * 1024;
    private static final Duration API_TIMEOUT = Duration.ofSeconds(15);
    private static final int IN_PROCESS_ATTEMPTS = 3;
    private static final long IN_PROCESS_RETRY_MILLIS = 250;
    static final String UPDATES_DIR = "updates";
    static final String HELPER_JAR = "seq-update-helper.jar";
    static final String HELPER_LOG = "update-helper.log";

    /** What the updater needs from the game and the system; tests supply their own. */
    interface Environment {
        Path gameDir();

        /** The Sequoia version Fabric loaded: what is running, whatever is on disk. */
        String runningVersion();

        /** The installed version of a mod, including minecraft, fabricloader and java. */
        Optional<Version> installedVersion(String modId);

        Instant jvmStart();

        /** The JAR Fabric loaded Sequoia from, if it is a JAR file. */
        Optional<Path> runningJar();

        /** A JAR holding this version's {@link UpdateApplier}, for the Windows helper. */
        Path helperSourceJar() throws IOException;

        boolean windows();

        String javaExecutable();

        /** The game's process id, which the Windows helper waits on before replacing the JAR. */
        default long pid() {
            return ProcessHandle.current().pid();
        }

        /** Opens the prompt; at startup in a world, a toast points to /seq update instead. */
        void showPrompt(boolean startup);

        void toast(String title, String message);

        void requestStop();

        void addShutdownHook(Thread hook);

        Process startHelper(List<String> command, Path log) throws IOException;

        void open(URI uri);

        void open(Path path);
    }

    private static final class Holder {
        private static final UpdateManager INSTANCE = create();
    }

    /** Local facts from the update record; {@code settled} needs no check, {@code copy} needs no download. */
    private record Local(Attempt note, Release copy, UpdateState settled) {
        static final Local NONE = new Local(null, null, null);
    }

    private final Environment environment;
    private final String repository;
    private final URI latestReleaseApi;
    private final ReleaseDownload.Limits limits;
    private final Duration installerPoll;
    private final Duration launchGrace;
    private final ExecutorService worker;
    private final ScheduledExecutorService timer;
    private final AtomicReference<UpdateState> state = new AtomicReference<>(new UpdateState.Idle());
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean hookRegistered = new AtomicBoolean();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private volatile boolean checkOnStartup;
    private volatile String ignoredTag;
    private ScheduledFuture<?> installerWatch;

    UpdateManager(Environment environment, String repository, URI latestReleaseApi, ReleaseDownload.Limits limits,
            Duration installerPoll, Duration launchGrace) {
        this.environment = environment;
        this.repository = repository;
        this.latestReleaseApi = latestReleaseApi;
        this.limits = limits;
        this.installerPoll = installerPoll;
        this.launchGrace = launchGrace;
        this.worker = Executors.newSingleThreadExecutor(daemon("Sequoia updater"));
        this.timer = Executors.newSingleThreadScheduledExecutor(daemon("Sequoia updater timer"));
    }

    private static UpdateManager create() {
        String repository = DEFAULT_REPOSITORY;
        String override = System.getProperty(REPOSITORY_PROPERTY);
        if (override != null) {
            if (REPOSITORY.matcher(override).matches()) {
                repository = override;
                LOGGER.warn("Checking for Sequoia updates in {} ({} is set)", repository, REPOSITORY_PROPERTY);
            } else {
                LOGGER.warn("Ignoring {}={}: expected owner/repository", REPOSITORY_PROPERTY, override);
            }
        }
        return new UpdateManager(new FabricEnvironment(), repository,
                URI.create("https://api.github.com/repos/" + repository + "/releases/latest"),
                ReleaseDownload.Limits.DEFAULT, Duration.ofSeconds(2), Duration.ofSeconds(30));
    }

    public static UpdateManager getInstance() {
        return Holder.INSTANCE;
    }

    public UpdateState state() {
        return state.get();
    }

    public String runningVersion() {
        return environment.runningVersion();
    }

    /** The repository update checks use, when it is not Sequoia's own. */
    public String testSource() {
        return repository.equals(DEFAULT_REPOSITORY) ? null : repository;
    }

    /**
     * Reconciles what an earlier session left behind and, if {@code checkOnStartup}, checks online.
     * With checks off nothing goes online until the user asks.
     */
    public void start(boolean checkOnStartup) {
        if (!started.compareAndSet(false, true)) return;
        this.checkOnStartup = checkOnStartup;
        worker.execute(() -> settle(true));
    }

    public void checkNow() {
        UpdateState current = state.get();
        if (!(current instanceof UpdateState.Idle || current instanceof UpdateState.UpToDate
                || current instanceof UpdateState.CheckFailed || current instanceof UpdateState.Available
                || current instanceof UpdateState.Attention || current instanceof UpdateState.Failed
                || current instanceof UpdateState.ManualOnly || current instanceof UpdateState.Incompatible)) return;
        if (!state.compareAndSet(current, new UpdateState.Checking(true))) return;
        worker.execute(() -> {
            Local local = reconcileSafely();
            if (local == null) {
                watchInstaller();
            } else if (local.settled() != null) {
                state.set(local.settled());
            } else {
                check(true, local);
            }
        });
    }

    /** {@code /seq update}: shows the prompt and checks unless it already has something to show. */
    public void openPrompt() {
        UpdateState current = state.get();
        environment.showPrompt(false);
        if (current instanceof UpdateState.Idle || current instanceof UpdateState.UpToDate
                || current instanceof UpdateState.CheckFailed || current instanceof UpdateState.Attention) checkNow();
    }

    /** Downloads, verifies and installs the offered release; with {@code exitAfter}, closes Minecraft on success. */
    public void update(boolean exitAfter) {
        UpdateState current = state.get();
        Release release = current instanceof UpdateState.Available available ? available.release()
                : current instanceof UpdateState.Failed failed ? failed.release() : null;
        if (release == null) return;
        ReleaseDownload.Transfer transfer = release.downloadedCopy() ? null : new ReleaseDownload.Transfer(
                release.jar().url(), release.jar().size(), release.sha256(),
                release.checksum() == null ? null : release.checksum().url(),
                updatesDir().resolve(release.jar().name() + ".download"), limits, timer);
        UpdateState next = transfer == null
                ? new UpdateState.Installing(release, exitAfter)
                : new UpdateState.Downloading(release, exitAfter, transfer);
        if (state.compareAndSet(current, next)) worker.execute(() -> install(release, exitAfter, transfer));
    }

    public void retry() {
        if (state.get() instanceof UpdateState.Failed failed) {
            if (failed.release().downloadedCopy()) checkNow();
            else update(failed.exitAfter());
        }
    }

    /** Stops a download; the release is offered again. An install in progress cannot be undone part-way. */
    public void cancel() {
        if (state.get() instanceof UpdateState.Downloading downloading) downloading.transfer().cancel();
    }

    /**
     * Before Minecraft closes, withdraws a staged Windows install. The verified file stays so the
     * release can still be installed this session; without its record, the next start removes it.
     */
    public void cancelStaged() {
        if (state.get() instanceof UpdateState.Staged staged
                && state.compareAndSet(staged, new UpdateState.Available(staged.release(), null))) {
            worker.execute(() -> deleteQuietly(updatesDir().resolve(UpdateRecord.FILE)));
        }
    }

    public void exitNow() {
        UpdateState current = state.get();
        if (current instanceof UpdateState.Installed || current instanceof UpdateState.Staged) requestStop();
    }

    /** Leaves the offered release alone for the rest of this session. */
    public void ignore() {
        Release release = switch (state.get()) {
            case UpdateState.Available available -> available.release();
            case UpdateState.Failed failed -> failed.release();
            case UpdateState.Incompatible incompatible -> incompatible.release();
            default -> null;
        };
        if (release != null) ignoredTag = release.tag();
    }

    public void openReleasePage() {
        Release release = switch (state.get()) {
            case UpdateState.Available available -> available.release();
            case UpdateState.Failed failed -> failed.release();
            case UpdateState.ManualOnly manual -> manual.release();
            case UpdateState.Incompatible incompatible -> incompatible.release();
            default -> null;
        };
        environment.open(release != null ? release.page() : URI.create("https://github.com/" + repository + "/releases"));
    }

    public void openModsFolder() {
        environment.open(environment.gameDir().resolve("mods"));
    }

    private void requestStop() {
        if (stopRequested.compareAndSet(false, true)) environment.requestStop();
    }

    // ---- Reconciling what an earlier session left behind ----

    private void settle(boolean startup) {
        Local local = reconcileSafely();
        if (local == null) {
            environment.showPrompt(startup);
            watchInstaller();
            return;
        }
        if (local.settled() != null) {
            state.set(local.settled());
            environment.showPrompt(startup);
            return;
        }
        if (!checkOnStartup) {
            UpdateState next = local.copy() != null ? new UpdateState.Available(local.copy(), local.note())
                    : local.note() != null ? new UpdateState.Attention(local.note())
                    : new UpdateState.Idle();
            state.set(next);
            if (!(next instanceof UpdateState.Idle)) environment.showPrompt(startup);
            return;
        }
        state.set(new UpdateState.Checking(false));
        check(false, local);
    }

    private Local reconcileSafely() {
        try {
            return reconcile();
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not reconcile the last Sequoia update", failure);
            return Local.NONE;
        }
    }

    /**
     * Works out what the last attempt achieved from the record, the version Fabric loaded and the
     * files actually present. Returns null while a Windows installer from an earlier session is
     * still running: until it ends, its files are left alone and nothing new starts.
     */
    private Local reconcile() throws IOException {
        Path updates = updatesDir();
        Path recordFile = updates.resolve(UpdateRecord.FILE);
        UpdateRecord.Data record;
        try {
            record = UpdateRecord.read(recordFile);
        } catch (IOException unreadable) {
            LOGGER.warn("Discarding an unreadable update record", unreadable);
            deleteQuietly(recordFile);
            record = null;
        }
        if (record != null && record.method() == UpdateRecord.Method.WINDOWS_HELPER && installerAlive(record, updates)) {
            Instant since = state.get() instanceof UpdateState.WaitingForInstaller waiting ? waiting.since() : Instant.now();
            state.set(new UpdateState.WaitingForInstaller(new Attempt(record.target(), record.phase(),
                    "An earlier install of " + record.target() + " is still running"), record.helperPid(), since));
            return null;
        }
        if (record == null) {
            removeLeftovers(updates, null);
            return Local.NONE;
        }
        String running = environment.runningVersion();
        int order = compareVersions(record.target(), running);
        if (order <= 0) {
            if (order == 0 && record.phase() != UpdateRecord.Phase.INCOMPATIBLE)
                environment.toast("Sequoia updated", "Sequoia " + running + " is running.");
            resolve(record, updates);
            return Local.NONE;
        }
        if (record.phase() == UpdateRecord.Phase.INCOMPATIBLE) {
            // Kept only so an unchanged game need not download the same release again (see check).
            removeLeftovers(updates, null);
            return Local.NONE;
        }
        if (record.phase() == UpdateRecord.Phase.INSTALLED && record.updated().isAfter(environment.jvmStart())) {
            removeLeftovers(updates, null);
            return new Local(null, null, new UpdateState.Installed(downloadedCopy(record)));
        }
        Release copy = null;
        Path pending = updates.resolve(record.jar() + ".pending");
        if (Files.exists(pending)) {
            ReleaseJar.Verdict verdict = ReleaseJar.check(pending, record.size(), record.sha256(),
                    version(record.target()), environment::installedVersion);
            if (verdict instanceof ReleaseJar.Verdict.Ready) copy = downloadedCopy(record);
            else deleteQuietly(pending);
        }
        removeLeftovers(updates, copy != null ? record.jar() : null);
        String note = switch (record.phase()) {
            case STAGED -> record.target() + " was downloaded but not installed: Minecraft closed before the installer started.";
            case LAUNCHED, APPLYING -> record.method() == UpdateRecord.Method.IN_PROCESS
                    ? "The previous install of " + record.target() + " stopped before finishing. Sequoia " + running + " is running."
                    : "The installer for " + record.target() + " stopped before finishing (see updates/" + HELPER_LOG
                            + "). Sequoia " + running + " is running.";
            case FAILED -> "Last update to " + record.target() + " failed: " + record.detail();
            case INSTALLED -> record.target() + " was installed but Minecraft started " + running
                    + ". Another copy of Sequoia or your launcher may have replaced it.";
            case INCOMPATIBLE -> throw new IllegalStateException("handled above");
        };
        return new Local(new Attempt(record.target(), record.phase(), note), copy, null);
    }

    /**
     * Whether a Windows installer is still running: it holds its lock file for its whole life, and
     * just after launch, before it takes the lock, the recorded process id must still name a process
     * that started at the recorded instant, so a reused process id is not mistaken for it.
     */
    boolean installerAlive(UpdateRecord.Data record, Path updates) {
        if (lockHeld(updates.resolve(UpdateApplier.LOCK_FILE))) return true;
        if (record.phase() != UpdateRecord.Phase.LAUNCHED || record.helperPid() <= 0) return false;
        Optional<ProcessHandle> process = ProcessHandle.of(record.helperPid()).filter(ProcessHandle::isAlive);
        if (process.isEmpty()) return false;
        Optional<Instant> started = process.get().info().startInstant();
        if (record.helperStart() != null && started.isPresent()) return started.get().equals(Instant.parse(record.helperStart()));
        return Duration.between(record.updated(), Instant.now()).compareTo(launchGrace) < 0;
    }

    static boolean lockHeld(Path lockFile) {
        if (!Files.exists(lockFile)) return false;
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
            try (FileLock lock = channel.tryLock()) {
                return lock == null;
            } catch (OverlappingFileLockException heldInThisProcess) {
                return true;
            }
        } catch (IOException unavailable) {
            LOGGER.warn("Could not check the update installer lock {}", lockFile, unavailable);
            return false;
        }
    }

    private void watchInstaller() {
        synchronized (this) {
            if (installerWatch != null) return;
            long period = installerPoll.toMillis();
            installerWatch = timer.scheduleWithFixedDelay(
                    () -> worker.execute(this::pollInstaller), period, period, TimeUnit.MILLISECONDS);
        }
    }

    private void pollInstaller() {
        if (!(state.get() instanceof UpdateState.WaitingForInstaller)) return;
        try {
            UpdateRecord.Data record = UpdateRecord.read(updatesDir().resolve(UpdateRecord.FILE));
            if (record != null && record.method() == UpdateRecord.Method.WINDOWS_HELPER
                    && installerAlive(record, updatesDir())) return;
        } catch (IOException unreadable) {
            // Reconciliation below discards it.
        }
        synchronized (this) {
            if (installerWatch != null) installerWatch.cancel(false);
            installerWatch = null;
        }
        settle(true);
    }

    /** The attempt is over; removes its record and the files it owned. */
    private void resolve(UpdateRecord.Data record, Path updates) {
        removeLeftovers(updates, null);
        if (record.currentJar() != null) {
            Path backup = Path.of(record.currentJar() + ".previous");
            if (inModsDir(backup) && environment.runningJar().map(path -> !path.equals(backup)).orElse(true))
                deleteQuietly(backup);
        }
        deleteQuietly(updates.resolve(UpdateRecord.FILE));
    }

    /** Partial downloads, staged files other than {@code keepJar}'s, and the helper's copy and lock. */
    private void removeLeftovers(Path updates, String keepJar) {
        if (!Files.isDirectory(updates)) return;
        try (Stream<Path> files = Files.list(updates)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if ((CANDIDATE.matcher(name).matches() && !name.equals(keepJar + ".pending"))
                        || name.equals(HELPER_JAR) || name.equals(UpdateApplier.LOCK_FILE)) deleteQuietly(file);
            }
        } catch (IOException unlisted) {
            LOGGER.warn("Could not list {}", updates, unlisted);
        }
    }

    // ---- Checking ----

    private void check(boolean manual, Local local) {
        Release latest;
        try {
            latest = fetchLatest();
        } catch (IOException failure) {
            checkFailed(failure, manual, local);
            return;
        }
        String running = environment.runningVersion();
        if (!isNewer(latest.version(), running)) {
            if (local.copy() != null) {
                LOGGER.info("{} is no longer offered; discarding its download", local.copy().tag());
                deleteQuietly(updatesDir().resolve(local.copy().jar().name() + ".pending"));
                deleteQuietly(updatesDir().resolve(UpdateRecord.FILE));
            }
            state.set(new UpdateState.UpToDate(running));
            if (manual) environment.showPrompt(false);
            return;
        }
        String verdict;
        try {
            verdict = rememberedIncompatibility(latest);
        } catch (IOException failure) {
            checkFailed(failure, manual, local);
            return;
        }
        if (verdict != null) {
            state.set(new UpdateState.Incompatible(latest, verdict));
            if (manual || !latest.tag().equals(ignoredTag)) environment.showPrompt(!manual);
            return;
        }
        Optional<String> manualOnly = ownershipProblem();
        if (manualOnly.isPresent()) {
            show(new UpdateState.ManualOnly(latest, manualOnly.get()), !manual);
            return;
        }
        state.set(new UpdateState.Available(latest, local.note()));
        if (manual || !latest.tag().equals(ignoredTag)) environment.showPrompt(!manual);
    }

    private void checkFailed(IOException failure, boolean manual, Local local) {
        LOGGER.warn("Sequoia update check failed: {}", failure.getMessage());
        if (local.copy() != null) {
            show(new UpdateState.Available(local.copy(), local.note()), !manual);
        } else {
            state.set(new UpdateState.CheckFailed(ReleaseDownload.describe(failure), local.note()));
            if (manual || local.note() != null) environment.showPrompt(!manual);
        }
    }

    private void show(UpdateState next, boolean startup) {
        state.set(next);
        environment.showPrompt(startup);
    }

    /**
     * The recorded reason this exact release cannot run here, if nothing it checked has changed.
     * Throws if the release's .sha256 file, needed to tell, cannot be read.
     */
    private String rememberedIncompatibility(Release latest) throws IOException {
        UpdateRecord.Data record;
        try {
            record = UpdateRecord.read(updatesDir().resolve(UpdateRecord.FILE));
        } catch (IOException unreadable) {
            return null;
        }
        if (record == null || record.phase() != UpdateRecord.Phase.INCOMPATIBLE || record.environment() == null
                || !record.target().equals(latest.tag())
                || !ReleaseJar.currentEnvironment(record.environment(), environment::installedVersion).equals(record.environment()))
            return null;
        // A release without a listed digest has only its .sha256 file read, within the base time limit.
        String sha256 = latest.sha256() != null ? latest.sha256() : new ReleaseDownload.Transfer(
                latest.checksum().url(), 0, null, latest.checksum().url(),
                updatesDir().resolve(latest.jar().name() + ".download"), limits, timer).checksum();
        return record.sha256().equalsIgnoreCase(sha256) ? record.detail() : null;
    }

    Release fetchLatest() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(latestReleaseApi)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", ReleaseDownload.USER_AGENT)
                .GET()
                .build();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(limits.connect())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpResponse<String> response;
        try {
            response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .get(API_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException slow) {
            throw new IOException("GitHub did not answer within " + API_TIMEOUT.toSeconds() + " s");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", interrupted);
        } catch (ExecutionException failed) {
            throw new IOException(ReleaseDownload.describe(failed.getCause()), failed.getCause());
        } finally {
            http.shutdownNow();
            http.close();
        }
        if (response.statusCode() != 200) throw new IOException("GitHub answered HTTP " + response.statusCode());
        return parseRelease(response.body());
    }

    /** The release's primary JAR, sequoia-VERSION.jar exactly, with its size and checksum. */
    static Release parseRelease(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String tag = string(root, "tag_name");
            if (tag == null || !SEMVER.matcher(tag).matches()) throw new IOException("The latest release has an invalid tag: " + tag);
            String version = version(tag);
            String jarName = "sequoia-" + version + ".jar";
            Asset jar = null;
            Asset checksum = null;
            String digest = null;
            for (JsonElement element : root.getAsJsonArray("assets")) {
                JsonObject asset = element.getAsJsonObject();
                String name = string(asset, "name");
                String url = string(asset, "browser_download_url");
                if (url == null) continue;
                if (jarName.equals(name)) {
                    long size = asset.get("size").getAsLong();
                    if (size <= 0 || size > MAX_ASSET_BYTES) throw new IOException("The release lists an unusable size for " + name);
                    jar = new Asset(name, URI.create(url), size);
                    Matcher matcher = DIGEST.matcher(String.valueOf(string(asset, "digest")));
                    if (matcher.matches()) digest = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
                } else if ((jarName + ".sha256").equals(name)) {
                    checksum = new Asset(name, URI.create(url), asset.has("size") ? asset.get("size").getAsLong() : 0);
                }
            }
            if (jar == null) throw new IOException("The latest release has no " + jarName);
            if (digest == null && checksum == null) throw new IOException("The latest release has no checksum for " + jarName);
            String page = string(root, "html_url");
            return new Release(tag, version, URI.create(page != null ? page : jar.url().toString()), jar, checksum, digest);
        } catch (RuntimeException malformed) {
            throw new IOException("Unexpected release data from GitHub", malformed);
        }
    }

    // ---- Installing ----

    private void install(Release release, boolean exitAfter, ReleaseDownload.Transfer transfer) {
        try {
            Optional<String> manualOnly = ownershipProblem();
            if (manualOnly.isPresent()) {
                state.set(new UpdateState.ManualOnly(release, manualOnly.get()));
                return;
            }
            Path updates = updatesDir();
            Files.createDirectories(updates);
            String name = release.jar().name();
            Path pending = updates.resolve(name + ".pending");
            removeOtherCandidates(updates, name);
            String sha256 = release.sha256();
            // Without a listed digest, read the release's .sha256 file first, so a staged copy is
            // checked against the release instead of being downloaded again.
            if (sha256 == null && transfer != null && Files.exists(pending)) sha256 = transfer.checksum();
            ReleaseJar.Verdict verdict = null;
            if (sha256 != null && Files.exists(pending)) {
                verdict = ReleaseJar.check(pending, release.jar().size(), sha256, release.version(), environment::installedVersion);
                if (verdict instanceof ReleaseJar.Verdict.Mismatch) {
                    deleteQuietly(pending);
                    verdict = null;
                }
            }
            if (verdict == null) {
                if (transfer == null)
                    throw new ReleaseDownload.TransferException(
                            "The downloaded update no longer matches the release; check for updates to download it again", null);
                requireFreeSpace(updates, release.jar().size());
                Path download = updates.resolve(name + ".download");
                transfer.run();
                if (sha256 == null) sha256 = transfer.checksum();
                verdict = ReleaseJar.check(download, release.jar().size(), sha256, release.version(), environment::installedVersion);
                if (verdict instanceof ReleaseJar.Verdict.Ready) {
                    Files.move(download, pending, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    deleteQuietly(download);
                }
            }
            if (transfer != null && transfer.cancelled()) {
                state.set(new UpdateState.Available(release, null));
                return;
            }
            switch (verdict) {
                case ReleaseJar.Verdict.Mismatch mismatch -> state.set(new UpdateState.Failed(release, exitAfter, mismatch.reason()));
                case ReleaseJar.Verdict.Incompatible incompatible -> {
                    deleteQuietly(pending);
                    UpdateRecord.write(updates.resolve(UpdateRecord.FILE), new UpdateRecord.Data(release.tag(),
                            environment.runningVersion(), name, sha256, release.jar().size(), UpdateRecord.Method.IN_PROCESS,
                            UpdateRecord.Phase.INCOMPATIBLE, null, 0, null, incompatible.environment(),
                            incompatible.reason(), Instant.now()));
                    state.set(new UpdateState.Incompatible(release, incompatible.reason()));
                }
                case ReleaseJar.Verdict.Ready ready -> {
                    if (environment.windows()) stage(release, sha256, exitAfter);
                    else installNow(release, sha256, exitAfter);
                }
            }
        } catch (ReleaseDownload.TransferException failure) {
            if (transfer != null && transfer.cancelled()) {
                state.set(new UpdateState.Available(release, null));
            } else {
                LOGGER.warn("Sequoia update failed: {}", failure.getMessage());
                state.set(new UpdateState.Failed(release, exitAfter, failure.getMessage()));
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Sequoia update failed", failure);
            state.set(new UpdateState.Failed(release, exitAfter, "Update failed: " + ReleaseDownload.describe(failure)));
        }
    }

    /** Linux and macOS: the running JAR can be replaced now; the game keeps using the open file. */
    private void installNow(Release release, String sha256, boolean exitAfter) throws IOException {
        state.set(new UpdateState.Installing(release, exitAfter));
        Path running = environment.runningJar().orElseThrow();
        Path recordFile = updatesDir().resolve(UpdateRecord.FILE);
        UpdateRecord.Data record = new UpdateRecord.Data(release.tag(), environment.runningVersion(), release.jar().name(),
                sha256, release.jar().size(), UpdateRecord.Method.IN_PROCESS, UpdateRecord.Phase.APPLYING,
                running.toString(), 0, null, null, null, Instant.now());
        UpdateRecord.write(recordFile, record);
        UpdateApplier.Outcome outcome = UpdateApplier.swap(
                new UpdateApplier.Paths(modsDir(), running, updatesDir().resolve(release.jar().name() + ".pending"),
                        modsDir().resolve(release.jar().name())),
                IN_PROCESS_ATTEMPTS, IN_PROCESS_RETRY_MILLIS, UpdateApplier.DISK, UpdateManager::pause, LOGGER::info);
        UpdateRecord.write(recordFile, record.with(
                outcome.installed() ? UpdateRecord.Phase.INSTALLED : UpdateRecord.Phase.FAILED, outcome.detail()));
        if (outcome.installed()) {
            state.set(new UpdateState.Installed(release));
            if (exitAfter) requestStop();
        } else {
            state.set(new UpdateState.Failed(release, exitAfter, outcome.detail()));
        }
    }

    /** Windows: the running game locks its JAR, so a helper installs the staged file once it exits. */
    private void stage(Release release, String sha256, boolean exitAfter) throws IOException {
        Path running = environment.runningJar().orElseThrow();
        UpdateRecord.write(updatesDir().resolve(UpdateRecord.FILE), new UpdateRecord.Data(release.tag(),
                environment.runningVersion(), release.jar().name(), sha256, release.jar().size(),
                UpdateRecord.Method.WINDOWS_HELPER, UpdateRecord.Phase.STAGED, running.toString(), 0, null, null, null,
                Instant.now()));
        if (hookRegistered.compareAndSet(false, true))
            environment.addShutdownHook(new Thread(this::launchInstaller, "Sequoia update installer"));
        state.set(new UpdateState.Staged(release));
        if (exitAfter) requestStop();
    }

    /** Runs as the game exits: starts the helper for a staged install and records the process. */
    void launchInstaller() {
        if (!(state.get() instanceof UpdateState.Staged)) return;
        Path updates = updatesDir();
        Path recordFile = updates.resolve(UpdateRecord.FILE);
        UpdateRecord.Data record;
        try {
            record = UpdateRecord.read(recordFile);
        } catch (IOException unreadable) {
            return;
        }
        if (record == null || record.phase() != UpdateRecord.Phase.STAGED) return;
        try {
            Path helperJar = updates.resolve(HELPER_JAR);
            Files.copy(environment.helperSourceJar(), helperJar, StandardCopyOption.REPLACE_EXISTING);
            Path modsDir = modsDir();
            Process process = environment.startHelper(List.of(
                    environment.javaExecutable(), "-Xmx64m", "-XX:+UseSerialGC", "-cp", helperJar.toString(),
                    UpdateApplier.class.getName(),
                    recordFile.toString(),
                    Long.toString(environment.pid()),
                    modsDir.toString(),
                    record.currentJar(),
                    updates.resolve(record.jar() + ".pending").toString(),
                    modsDir.resolve(record.jar()).toString(),
                    helperJar.toString()), updates.resolve(HELPER_LOG));
            UpdateRecord.Data latest = UpdateRecord.read(recordFile);
            if (latest != null && latest.phase() == UpdateRecord.Phase.STAGED) {
                UpdateRecord.write(recordFile, latest.process(UpdateRecord.Phase.LAUNCHED, process.pid(),
                        process.toHandle().info().startInstant().orElse(null), null));
            }
        } catch (IOException | RuntimeException failure) {
            try {
                UpdateRecord.write(recordFile, record.with(UpdateRecord.Phase.FAILED,
                        "The installer could not start: " + ReleaseDownload.describe(failure)));
            } catch (IOException unwritable) {
                LOGGER.warn("Could not record that the update installer did not start", unwritable);
            }
        }
    }

    /** Self-updating replaces the JAR Fabric loaded, and only when it is this instance's mods folder. */
    private Optional<String> ownershipProblem() {
        Optional<Path> running = environment.runningJar();
        if (running.isEmpty()) return Optional.of("Sequoia is not running from a JAR file, so it can't replace itself.");
        Path mods = modsDir();
        try {
            Path parent = running.get().getParent();
            if (parent == null || !Files.isDirectory(mods) || !Files.isSameFile(parent, mods))
                return Optional.of("Sequoia is loaded from " + running.get() + ", outside this instance's mods folder.");
        } catch (IOException unreadable) {
            return Optional.of("Sequoia can't check where it is installed: " + ReleaseDownload.describe(unreadable));
        }
        if (!Files.isWritable(mods)) return Optional.of("The mods folder " + mods + " is not writable.");
        return Optional.empty();
    }

    private static void requireFreeSpace(Path updates, long size) throws IOException {
        long usable = Files.getFileStore(updates).getUsableSpace();
        if (usable < size + FREE_SPACE_MARGIN)
            throw new ReleaseDownload.TransferException("Not enough free disk space for the "
                    + UpdateState.megabytes(size) + " update", null);
    }

    private static void removeOtherCandidates(Path updates, String keepJar) throws IOException {
        try (Stream<Path> files = Files.list(updates)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (CANDIDATE.matcher(name).matches() && !name.startsWith(keepJar + ".")) deleteQuietly(file);
            }
        }
    }

    private Release downloadedCopy(UpdateRecord.Data record) {
        return new Release(record.target(), version(record.target()),
                URI.create("https://github.com/" + repository + "/releases/tag/" + record.target()),
                new Asset(record.jar(), null, record.size()), null, record.sha256());
    }

    private boolean inModsDir(Path file) {
        Path parent = file.getParent();
        try {
            return parent != null && Files.isDirectory(parent) && Files.isSameFile(parent, modsDir());
        } catch (IOException unreadable) {
            return false;
        }
    }

    private Path updatesDir() {
        return environment.gameDir().resolve(UPDATES_DIR);
    }

    private Path modsDir() {
        return environment.gameDir().resolve("mods");
    }

    // ---- Versions ----

    static boolean isNewer(String latest, String installed) {
        Matcher latestMatcher = SEMVER.matcher(latest);
        if (!latestMatcher.matches()) return false;
        Matcher installedMatcher = SEMVER.matcher(installed);
        if (!installedMatcher.matches()) return !version(latest).equals(installed);
        for (int i = 1; i <= 3; i++) {
            int difference = Integer.compare(
                    Integer.parseInt(latestMatcher.group(i)), Integer.parseInt(installedMatcher.group(i)));
            if (difference != 0) return difference > 0;
        }
        return false;
    }

    /** Below zero if {@code target} is older than {@code running}, zero if the same, above if newer. */
    static int compareVersions(String target, String running) {
        if (isNewer(target, running)) return 1;
        return isNewer("v" + version(running), target) ? -1 : 0;
    }

    static String version(String tag) {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException busy) {
            LOGGER.debug("Could not delete {}", file, busy);
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Starts the Windows helper with its output in {@code log} and nothing on its standard input. */
    static Process startProcess(List<String> command, Path log) throws IOException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(log.toFile()))
                .start();
        process.getOutputStream().close();
        return process;
    }

    /** The game: Fabric Loader for versions and paths, Minecraft for screens, toasts and exiting. */
    private static final class FabricEnvironment implements Environment {
        private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(8000);

        @Override
        public Path gameDir() {
            return FabricLoader.getInstance().getGameDir();
        }

        @Override
        public String runningVersion() {
            return FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        }

        @Override
        public Optional<Version> installedVersion(String modId) {
            return FabricLoader.getInstance().getModContainer(modId).map(container -> container.getMetadata().getVersion());
        }

        @Override
        public Instant jvmStart() {
            return Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        }

        @Override
        public Optional<Path> runningJar() {
            Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
            if (container.isEmpty() || container.get().getOrigin().getKind() != ModOrigin.Kind.PATH) return Optional.empty();
            return container.get().getOrigin().getPaths().stream()
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .findFirst();
        }

        @Override
        public Path helperSourceJar() throws IOException {
            try {
                Path location = Path.of(UpdateApplier.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (Files.isRegularFile(location)) return location;
            } catch (Exception unresolved) {
                LOGGER.debug("Falling back to the installed JAR for the update helper", unresolved);
            }
            return runningJar().orElseThrow(() -> new IOException("Sequoia is not running from a JAR file"));
        }

        @Override
        public boolean windows() {
            return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        }

        @Override
        public String javaExecutable() {
            Path bin = Path.of(System.getProperty("java.home"), "bin");
            for (String name : List.of("javaw.exe", "java.exe", "java")) {
                if (Files.isRegularFile(bin.resolve(name))) return bin.resolve(name).toString();
            }
            return "java";
        }

        @Override
        public void showPrompt(boolean startup) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (minecraft.screen instanceof UpdatePromptScreen) return;
                if (startup && minecraft.level != null) {
                    SystemToast.add(minecraft.getToastManager(), TOAST, Component.literal("Sequoia update"),
                            Component.literal("Run /seq update for details"));
                } else {
                    minecraft.setScreen(new UpdatePromptScreen(minecraft.screen));
                }
            });
        }

        @Override
        public void toast(String title, String message) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> SystemToast.add(minecraft.getToastManager(), TOAST,
                    Component.literal(title), Component.literal(message)));
        }

        @Override
        public void requestStop() {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(minecraft::stop);
        }

        @Override
        public void addShutdownHook(Thread hook) {
            Runtime.getRuntime().addShutdownHook(hook);
        }

        @Override
        public Process startHelper(List<String> command, Path log) throws IOException {
            return startProcess(command, log);
        }

        @Override
        public void open(URI uri) {
            Util.getPlatform().openUri(uri);
        }

        @Override
        public void open(Path path) {
            Util.getPlatform().openPath(path);
        }
    }
}
