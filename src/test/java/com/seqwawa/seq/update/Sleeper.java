package com.seqwawa.seq.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** A stand-in process, such as a game that has not exited yet, for tests that need one alive. */
public final class Sleeper {
    public static void main(String[] args) throws InterruptedException {
        Thread.sleep(120_000);
    }

    static Process start() throws IOException {
        try {
            String classes = Path.of(Sleeper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            return new ProcessBuilder(List.of(ProcessHandle.current().info().command().orElse("java"), "-cp", classes,
                    Sleeper.class.getName())).start();
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
    }
}
