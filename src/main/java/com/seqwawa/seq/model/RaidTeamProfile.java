package com.seqwawa.seq.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a member brings to a raid: meta builds, auras, region and a status line.
 * <p>
 * Builds are held as keys rather than {@link RaidBuild} objects, so renaming a
 * build on the backend does not stale every saved profile.
 */
public record RaidTeamProfile(
        Set<String> buildKeys,
        boolean canBringAuras,
        PartyRegion region,
        /** A short free-text line, like a Discord status. May be blank. */
        String status,
        long updatedAtEpochMs) {

    /** Longer status lines are cut on save. */
    public static final int MAX_STATUS_LENGTH = 64;

    public RaidTeamProfile {
        buildKeys = normalizeKeys(buildKeys);
        status = normalizeStatus(status);
    }

    public static RaidTeamProfile empty() {
        return new RaidTeamProfile(Set.of(), false, null, null, 0L);
    }

    /** True once the member has filled the profile in. */
    public boolean isComplete() {
        return updatedAtEpochMs > 0L;
    }

    public boolean hasBuild(String buildKey) {
        return buildKey != null && buildKeys.contains(RaidBuild.normalizeKey(buildKey));
    }

    /** Whether this member owns at least one build that is meta for {@code raid}. */
    public boolean coversRaid(RaidType raid) {
        return raid != null && raid.isCoveredBy(buildKeys);
    }

    /** The raids this member can be slotted into, in catalog order. */
    public List<RaidType> coveredRaids(RaidCatalog catalog) {
        return catalog == null ? List.of() : catalog.coveredRaids(buildKeys);
    }

    public boolean hasStatus() {
        return status != null && !status.isBlank();
    }

    public RaidTeamProfile withBuildKeys(Set<String> updated) {
        return new RaidTeamProfile(updated, canBringAuras, region, status, updatedAtEpochMs);
    }

    public RaidTeamProfile withBuildToggled(String buildKey) {
        String normalized = RaidBuild.normalizeKey(buildKey);
        if (normalized.isEmpty()) {
            return this;
        }
        LinkedHashSet<String> updated = new LinkedHashSet<>(buildKeys);
        if (!updated.remove(normalized)) {
            updated.add(normalized);
        }
        return withBuildKeys(updated);
    }

    public RaidTeamProfile withCanBringAuras(boolean value) {
        return new RaidTeamProfile(buildKeys, value, region, status, updatedAtEpochMs);
    }

    public RaidTeamProfile withRegion(PartyRegion value) {
        return new RaidTeamProfile(buildKeys, canBringAuras, value, status, updatedAtEpochMs);
    }

    public RaidTeamProfile withStatus(String value) {
        return new RaidTeamProfile(buildKeys, canBringAuras, region, value, updatedAtEpochMs);
    }

    /** Stamps the profile as saved, which is what marks it complete. */
    public RaidTeamProfile savedAt(long epochMs) {
        return new RaidTeamProfile(buildKeys, canBringAuras, region, status, epochMs);
    }

    private static Set<String> normalizeKeys(Set<String> value) {
        if (value == null || value.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String key : value) {
            String normalizedKey = RaidBuild.normalizeKey(key);
            if (!normalizedKey.isEmpty()) {
                normalized.add(normalizedKey);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeStatus(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= MAX_STATUS_LENGTH ? trimmed : trimmed.substring(0, MAX_STATUS_LENGTH);
    }
}
