package com.seqwawa.seq.update;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

/**
 * What the updater is doing, as the update prompt shows it. Each value is immutable and only
 * {@link UpdateManager} replaces it, so the prompt can simply draw whatever it reads.
 */
public sealed interface UpdateState {
    record Idle() implements UpdateState {}

    record Checking(boolean manual) implements UpdateState {}

    record UpToDate(String running) implements UpdateState {}

    record CheckFailed(String reason, Attempt previous) implements UpdateState {}

    record Available(Release release, Attempt previous) implements UpdateState {}

    /** The outcome of an earlier attempt, shown without checking online. */
    record Attention(Attempt previous) implements UpdateState {}

    /** An installer from an earlier session is still running and owns the update files. */
    record WaitingForInstaller(Attempt previous, long pid, Instant since) implements UpdateState {}

    record Downloading(Release release, boolean exitAfter, ReleaseDownload.Transfer transfer) implements UpdateState {
        public long received() {
            return transfer.received();
        }

        public long total() {
            return transfer.total();
        }

        /** Whole seconds since data last arrived; zero while it is flowing. */
        public long secondsWithoutData() {
            return transfer.sinceData().toSeconds();
        }
    }

    record Installing(Release release, boolean exitAfter) implements UpdateState {}

    /** On disk; a restart runs it. */
    record Installed(Release release) implements UpdateState {}

    /** Verified and waiting for Minecraft to close, when the Windows helper installs it. */
    record Staged(Release release) implements UpdateState {}

    record Failed(Release release, boolean exitAfter, String reason) implements UpdateState {}

    /** This game can't replace its own Sequoia JAR; the release has to be installed by hand. */
    record ManualOnly(Release release, String reason) implements UpdateState {}

    /** The release declares requirements this game does not meet. */
    record Incompatible(Release release, String reason) implements UpdateState {}

    /**
     * A release from GitHub has {@code jar.url()} set. A downloaded copy rebuilt from the update
     * record has none: it installs only from its re-verified staged file and never downloads.
     */
    record Release(String tag, String version, URI page, Asset jar, Asset checksum, String sha256) {
        public boolean downloadedCopy() {
            return jar.url() == null;
        }
    }

    record Asset(String name, URI url, long size) {}

    /** What an earlier attempt left behind, phrased as facts for the prompt. */
    record Attempt(String target, UpdateRecord.Phase phase, String detail) {}

    static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0);
    }
}
