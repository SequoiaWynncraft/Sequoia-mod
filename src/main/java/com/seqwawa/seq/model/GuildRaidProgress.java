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

    public record Entry(int count, String tier) {

        public Entry(int count) {
            this(count, null);
        }
    }

    public GuildRaidProgress {
        progress = normalizeKeys(progress);
    }

    public int count(SeqRaid raid) {
        return count(progress.get(raid.code()));
    }

    public SeqTier tier(SeqRaid raid) {
        return tier(progress.get(raid.code()));
    }

    public SeqTier totalTier() {
        return tier(progress.get(TOTAL_KEY));
    }

    public int totalCount() {
        Entry total = progress.get(TOTAL_KEY);
        if (total != null) {
            return count(total);
        }
        int sum = 0;
        for (SeqRaid raid : SeqRaid.values()) {
            sum += count(raid);
        }
        return sum;
    }

    private static int count(Entry entry) {
        return entry == null ? 0 : Math.max(0, entry.count());
    }

    private static SeqTier tier(Entry entry) {
        return entry == null ? null : SeqTier.fromKey(entry.tier());
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
