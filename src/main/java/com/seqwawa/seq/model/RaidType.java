package com.seqwawa.seq.model;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * One raid, with the builds the guild considers meta for it.
 * <p>
 * Like {@link RaidBuild} this comes from the backend rather than from the jar,
 * so the guild can change what counts as meta for a raid without a release.
 * <p>
 * {@link #apiName} is the exact string Wynncraft uses as a key under a player's
 * {@code globalData.currentGuildRaids.list}, which is how clear counts are read
 * off the roster. The backend owns that mapping because it owns the raid list.
 */
public record RaidType(String key, String shortName, String apiName, int position, Set<String> buildKeys) {

    public RaidType {
        key = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        shortName = shortName == null || shortName.isBlank() ? key : shortName.trim();
        apiName = apiName == null ? "" : apiName.trim();
        buildKeys = normalizeKeys(buildKeys);
    }

    public static RaidType of(String key, String apiName, String... buildKeys) {
        return new RaidType(key, key, apiName, 0, Set.of(buildKeys));
    }

    public boolean isValid() {
        return !key.isEmpty();
    }

    /** Whether owning any of {@code ownedBuildKeys} makes someone useful here. */
    public boolean isCoveredBy(Set<String> ownedBuildKeys) {
        if (ownedBuildKeys == null || ownedBuildKeys.isEmpty()) {
            return false;
        }
        for (String owned : ownedBuildKeys) {
            if (buildKeys.contains(RaidBuild.normalizeKey(owned))) {
                return true;
            }
        }
        return false;
    }

    /** The keys from {@code owned} that are meta for this raid. */
    public Set<String> matchingBuildKeys(Set<String> owned) {
        if (owned == null || owned.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> matching = new LinkedHashSet<>();
        for (String key : owned) {
            String normalized = RaidBuild.normalizeKey(key);
            if (buildKeys.contains(normalized)) {
                matching.add(normalized);
            }
        }
        return Set.copyOf(matching);
    }

    public boolean hasBuild(String buildKey) {
        return buildKeys.contains(RaidBuild.normalizeKey(buildKey));
    }

    private static Set<String> normalizeKeys(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String key : keys) {
            String value = RaidBuild.normalizeKey(key);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return Set.copyOf(normalized);
    }
}
