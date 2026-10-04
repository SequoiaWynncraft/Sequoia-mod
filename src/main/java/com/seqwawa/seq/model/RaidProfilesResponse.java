package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The wire shape of the raid profiles endpoint, modelled on
 * {@link RankProfilesResponse}.
 * <p>
 * Catalog and profiles arrive together: they are always read as a pair, and a
 * profile whose build keys the catalog cannot explain is not worth rendering.
 */
public record RaidProfilesResponse(
        @SerializedName("schema_version") int schemaVersion, Catalog catalog, List<Profile> profiles) {

    /** The version this client writes and understands. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public RaidProfilesResponse {
        // A stray comma in the payload produces a null entry; one malformed row must
        // not fail the whole response.
        profiles = profiles == null ? List.of() : profiles.stream().filter(Objects::nonNull).toList();
    }

    /** What counts as meta right now: the builds, the raids, and the link between them. */
    public record Catalog(List<Build> builds, List<Raid> raids) {}

    public record Build(String key, String label, int position) {}

    public record Raid(
            String key,
            @SerializedName("short_name") String shortName,
            @SerializedName("api_name") String apiName,
            int position,
            @SerializedName("build_keys") List<String> buildKeys) {}

    public record Profile(
            MinecraftIdentity minecraft,
            /** Build keys, matching the catalog. Unknown keys are ignored. */
            List<String> builds,
            @SerializedName("can_bring_auras") boolean canBringAuras,
            /** {@code EU}, {@code NA} or {@code AS}. Omitted when the member set none. */
            String region,
            String status,
            /** ISO-8601 instant, as every other timestamp in this protocol. */
            @SerializedName("updated_at") Instant updatedAt) {}

    public record MinecraftIdentity(String uuid, String username) {}

    /** The catalog in the form the screens read, empty when the payload had none. */
    public RaidCatalog toCatalog() {
        if (catalog == null) {
            return RaidCatalog.empty();
        }

        List<RaidBuild> builds = new ArrayList<>();
        if (catalog.builds() != null) {
            for (Build build : catalog.builds()) {
                if (build != null && build.key() != null && !build.key().isBlank()) {
                    builds.add(new RaidBuild(build.key(), build.label(), build.position()));
                }
            }
        }

        List<RaidType> raids = new ArrayList<>();
        if (catalog.raids() != null) {
            for (Raid raid : catalog.raids()) {
                if (raid == null || raid.key() == null || raid.key().isBlank()) {
                    continue;
                }
                Set<String> buildKeys = raid.buildKeys() == null
                        ? Set.of()
                        : new LinkedHashSet<>(raid.buildKeys());
                raids.add(new RaidType(
                        raid.key(), raid.shortName(), raid.apiName(), raid.position(), buildKeys));
            }
        }

        return new RaidCatalog(builds, raids);
    }

    /**
     * The profiles keyed by Minecraft UUID, which survives a rename where a username
     * does not.
     * <p>
     * An entry with no UUID is dropped, since it cannot be matched to a roster row,
     * and a build key the catalog does not know is skipped rather than failing the
     * response, so a client keeps working while the meta is edited.
     */
    public Map<String, RaidTeamProfile> toDomain() {
        return toDomain(toCatalog());
    }

    public Map<String, RaidTeamProfile> toDomain(RaidCatalog catalog) {
        Map<String, RaidTeamProfile> byUuid = new LinkedHashMap<>();
        for (Profile profile : profiles) {
            String uuid = identityKey(profile);
            if (uuid == null) {
                continue;
            }

            Set<String> buildKeys = new LinkedHashSet<>();
            if (profile.builds() != null) {
                for (String key : profile.builds()) {
                    String normalized = RaidBuild.normalizeKey(key);
                    // With no catalog yet, keys are kept as sent: dropping them would make
                    // every profile look empty rather than unloaded.
                    if (!normalized.isEmpty()
                            && (catalog == null || catalog.isEmpty() || catalog.hasBuild(normalized))) {
                        buildKeys.add(normalized);
                    }
                }
            }

            PartyRegion region = null;
            if (profile.region() != null && !profile.region().isBlank()) {
                try {
                    region = PartyRegion.valueOf(profile.region().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    region = null;
                }
            }

            // A profile that reached us was filled in by its owner, so it counts as
            // complete even with no timestamp.
            long updatedAt = profile.updatedAt() != null
                    ? profile.updatedAt().toEpochMilli()
                    : System.currentTimeMillis();

            byUuid.put(
                    uuid,
                    new RaidTeamProfile(
                            Set.copyOf(buildKeys), profile.canBringAuras(), region, profile.status(), updatedAt));
        }
        return Map.copyOf(byUuid);
    }

    /**
     * Lowercase username to UUID, for lookups that only have a name. A stale name can
     * linger here, which is why anything with a roster row uses the UUID instead.
     */
    public Map<String, String> uuidByUsername() {
        Map<String, String> index = new LinkedHashMap<>();
        for (Profile profile : profiles) {
            String uuid = identityKey(profile);
            if (uuid == null) {
                continue;
            }
            String username = profile.minecraft().username();
            if (username != null && !username.isBlank()) {
                index.put(username.trim().toLowerCase(Locale.ROOT), uuid);
            }
        }
        return Map.copyOf(index);
    }

    /** The UUID a profile is stored under, or null when it carries none. */
    public static String identityKey(Profile profile) {
        return profile == null || profile.minecraft() == null
                ? null
                : normalizeUuid(profile.minecraft().uuid());
    }

    /** UUIDs compare lowercase and undashed, so both forms are the same key. */
    public static String normalizeUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        String normalized = uuid.trim().replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
