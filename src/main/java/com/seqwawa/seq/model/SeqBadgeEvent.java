package com.seqwawa.seq.model;

import java.util.Locale;

public enum SeqBadgeEvent {
    WTP("wtp", 0),
    NOL("nol", 0),
    INSIGNA("insigna", 1);

    private final String commandName;
    private final int renderOrder;

    SeqBadgeEvent(String commandName, int renderOrder) {
        this.commandName = commandName;
        this.renderOrder = renderOrder;
    }

    public String commandName() {
        return commandName;
    }

    public String apiName() {
        return name();
    }

    public int renderOrder() {
        return renderOrder;
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
