package com.seqwawa.seq.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Properties;

/**
 * The one install attempt that has to outlive the game: what was staged, how it is being
 * installed and how that ended, in {@code updates/sequoia-update.properties}.
 *
 * <p>The Windows update helper reads and writes it with only the JDK on its class path, so this
 * class uses nothing else. Every write replaces the whole file atomically.
 */
final class UpdateRecord {
    static final String FILE = "sequoia-update.properties";
    private static final int FORMAT = 1;
    private static final int MAX_DETAIL = 500;

    enum Phase { STAGED, LAUNCHED, APPLYING, INSTALLED, FAILED, INCOMPATIBLE }

    enum Method { IN_PROCESS, WINDOWS_HELPER }

    /**
     * @param target release tag the attempt installs
     * @param from Sequoia version that was running when the attempt began
     * @param jar asset file name; the staged copy is {@code updates/<jar>.pending}
     * @param currentJar the installed JAR the attempt replaces
     * @param helperPid process id of the Windows helper, 0 when none was started
     * @param helperStart that process's start instant, or null when the system did not report it
     * @param environment for {@link Phase#INCOMPATIBLE}: the installed versions the verdict checked
     */
    record Data(
            String target,
            String from,
            String jar,
            String sha256,
            long size,
            Method method,
            Phase phase,
            String currentJar,
            long helperPid,
            String helperStart,
            String environment,
            String detail,
            Instant updated) {
        Data with(Phase phase, String detail) {
            return new Data(target, from, jar, sha256, size, method, phase, currentJar, helperPid, helperStart,
                    environment, detail, Instant.now());
        }

        /** This record in {@code phase}, naming the installer process by id and start instant. */
        Data process(Phase phase, long pid, Instant start, String detail) {
            return new Data(target, from, jar, sha256, size, method, phase, currentJar, pid,
                    start == null ? null : start.toString(), environment, detail, Instant.now());
        }
    }

    private UpdateRecord() {}

    /** The record in {@code file}, or null if there is none; an unreadable record throws. */
    static Data read(Path file) throws IOException {
        Properties values = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            values.load(in);
        } catch (NoSuchFileException absent) {
            return null;
        }
        try {
            if (Integer.parseInt(values.getProperty("format", "")) != FORMAT) throw new IOException("Unknown update record format");
            return new Data(
                    required(values, "target"),
                    required(values, "from"),
                    required(values, "jar"),
                    required(values, "sha256"),
                    Long.parseLong(required(values, "size")),
                    Method.valueOf(required(values, "method").toUpperCase(Locale.ROOT).replace('-', '_')),
                    Phase.valueOf(required(values, "phase").toUpperCase(Locale.ROOT)),
                    values.getProperty("currentJar"),
                    Long.parseLong(values.getProperty("helperPid", "0")),
                    values.getProperty("helperStart"),
                    values.getProperty("environment"),
                    values.getProperty("detail"),
                    Instant.parse(required(values, "updated")));
        } catch (RuntimeException malformed) {
            throw new IOException("Malformed update record", malformed);
        }
    }

    static void write(Path file, Data data) throws IOException {
        Properties values = new Properties();
        values.setProperty("format", Integer.toString(FORMAT));
        values.setProperty("target", data.target());
        values.setProperty("from", data.from());
        values.setProperty("jar", data.jar());
        values.setProperty("sha256", data.sha256());
        values.setProperty("size", Long.toString(data.size()));
        values.setProperty("method", data.method().name().toLowerCase(Locale.ROOT).replace('_', '-'));
        values.setProperty("phase", data.phase().name().toLowerCase(Locale.ROOT));
        if (data.currentJar() != null) values.setProperty("currentJar", data.currentJar());
        if (data.helperPid() != 0) values.setProperty("helperPid", Long.toString(data.helperPid()));
        if (data.helperStart() != null) values.setProperty("helperStart", data.helperStart());
        if (data.environment() != null) values.setProperty("environment", data.environment());
        if (data.detail() != null) {
            String detail = data.detail();
            values.setProperty("detail", detail.length() > MAX_DETAIL ? detail.substring(0, MAX_DETAIL) : detail);
        }
        values.setProperty("updated", data.updated().toString());

        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".sequoia-update-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE);
                    OutputStream out = Channels.newOutputStream(channel)) {
                values.store(out, null);
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + key);
        return value;
    }
}
