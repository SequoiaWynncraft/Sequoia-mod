package com.seqwawa.seq.model;

import java.util.Locale;

public enum SeqBadgeEvent {
    TWP("twp", "fruma");

    private final String commandName;
    private final String texturePrefix;

    SeqBadgeEvent(String commandName, String texturePrefix) {
        this.commandName = commandName;
        this.texturePrefix = texturePrefix;
    }

    public String commandName() {
        return commandName;
    }

    public String apiName() {
        return name();
    }

    public String texturePrefix() {
        return texturePrefix;
    }

    public static SeqBadgeEvent parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return SeqBadgeEvent.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
