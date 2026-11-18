package com.seqwawa.seq.client;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Where small blocking jobs run, so they hold up neither the game thread nor a web
 * request's thread.
 */
public final class Background {

    private static final ExecutorService DISK = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "seq-disk");
        thread.setDaemon(true);
        return thread;
    });

    private Background() {}

    /**
     * Reads and writes the mod's files, and parses what it reads. One thread, so two
     * writes to one file land in the order they were made.
     */
    public static Executor disk() {
        return DISK;
    }
}
