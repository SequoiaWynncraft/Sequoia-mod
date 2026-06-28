package com.seqwawa.seq.model;

import java.util.Locale;

public enum SeqBadgeEvent {
    WTP("wtp"),
    NOL("nol");

    private final String commandName;

    SeqBadgeEvent(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }

    public String apiName() {
        return name();
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
