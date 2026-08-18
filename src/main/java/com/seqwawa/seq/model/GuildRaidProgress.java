package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record GuildRaidProgress(
        @SerializedName("schema_version") int schemaVersion,
        Map<String, Entry> progress) {

    private static final String TOTAL_KEY = "TOTAL";

    public static final GuildRaidProgress EMPTY = new GuildRaidProgress(0, Map.of());

    public record Entry(int count) {}

    public GuildRaidProgress {
        progress = normalizeKeys(progress);
    }

    public int count(SeqRaid raid) {
        Entry entry = progress.get(raid.code());
        return entry == null ? 0 : Math.max(0, entry.count());
    }

    public int totalCount() {
        Entry total = progress.get(TOTAL_KEY);
        if (total != null) {
            return Math.max(0, total.count());
        }
        int sum = 0;
        for (SeqRaid raid : SeqRaid.values()) {
            sum += count(raid);
        }
        return sum;
    }

    private static Map<String, Entry> normalizeKeys(Map<String, Entry> raw) {
        if (raw == null) {
            return Map.of();
        }
        Map<String, Entry> cleaned = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                cleaned.put(key.trim().toUpperCase(Locale.ROOT), value);
            }
        });
        return Map.copyOf(cleaned);
    }
}
