package com.seqwawa.seq.network;

/**
 * Reads a server-sent event stream one line at a time, as the HTML standard
 * describes: fields until a blank line, comments starting with a colon.
 */
final class SseParser {

    record Event(String id, String type, String data) {}

    private String lastEventId;
    private String type;
    private final StringBuilder data = new StringBuilder();
    private boolean hasData;

    /** The id of the last event or id-only frame seen, to resume from after a reconnect. */
    String lastEventId() {
        return lastEventId;
    }

    /** Takes one line. Returns an event when a blank line finishes one that carried data. */
    Event accept(String line) {
        if (line == null) return null;
        if (line.isEmpty()) {
            Event event = hasData ? new Event(lastEventId, type == null ? "message" : type, data.toString()) : null;
            type = null;
            data.setLength(0);
            hasData = false;
            return event;
        }
        if (line.startsWith(":")) return null;
        int colon = line.indexOf(':');
        String field = colon < 0 ? line : line.substring(0, colon);
        String value = colon < 0 ? "" : line.substring(colon + 1);
        if (value.startsWith(" ")) value = value.substring(1);
        switch (field) {
            case "id" -> {
                if (!value.contains("\0")) lastEventId = value;
            }
            case "event" -> type = value;
            case "data" -> {
                if (hasData) data.append('\n');
                data.append(value);
                hasData = true;
            }
            default -> {
                // "retry" and unknown fields change nothing here: reconnects follow their own backoff.
            }
        }
        return null;
    }
}
