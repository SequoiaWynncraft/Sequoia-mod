package com.seqwawa.seq.config;

import java.nio.file.Path;

/** Config managers for tests, kept in a directory the test owns and saved nowhere else. */
public final class TestConfigs {

    private TestConfigs() {}

    /** A config manager whose files live in {@code directory}, with no shutdown hook. */
    public static ConfigManager in(Path directory) {
        return new ConfigManager(directory.resolve("config.json"), directory.resolve("token"), false);
    }
}
