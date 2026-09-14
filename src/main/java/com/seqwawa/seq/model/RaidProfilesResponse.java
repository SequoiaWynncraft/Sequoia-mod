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
 * {@link RankProfilesResponse} so the backend has one convention to follow for
 * "the catalog plus every member's X" rather than two.
 * <p>
 * Catalog and profiles arrive together on purpose. They are always read as a
 * pair, and a profile whose build keys the catalog does not explain is not worth
 * rendering, so shipping them apart would only create a window where they
 * disagree.
 */
public record RaidProfilesResponse(
        @SerializedName("schema_version") int schemaVersion, Catalog catalog, List<Profile> profiles) {

    /** The version this client writes and understands. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public RaidProfilesResponse {
        // A null entry is what a stray comma in the payload produces. Dropping it here
        // keeps one malformed row from failing the whole response.
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
     * The profiles keyed by Minecraft UUID.
     * <p>
     * UUID rather than username, because a token outlives a rename: a member who
     * renames comes back under a new name, and a panel keyed by name would show
     * their old entry alongside the new one instead of replacing it. The UUID is
     * stable across renames and rides along on every profile the panel ever sees,
     * from the fetch and from both WebSocket actions.
     * <p>
     * Entries without a UUID are dropped, since they cannot be matched to a
     * roster row. Build keys the catalog does not know are skipped rather than
     * failing the whole response, which is what lets a client keep working while
     * the meta is being edited.
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
                    // An empty catalog means the meta has not arrived yet, and dropping
                    // every build then would make each profile look empty rather than
                    // unloaded, so the keys are kept as sent.
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

            // A profile that reached us has been filled in by its owner, so it counts as
            // complete even when the timestamp is missing.
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
     * Lowercase username to UUID, so a lookup that only has a name still lands.
     * <p>
     * This index is the one place a stale name can linger, which is why nothing
     * that has a roster row uses it: it exists for the friends list, where a
     * member may be offline and a name is all there is.
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

    /**
     * UUIDs compare lowercase and without dashes, so the dashed form Wynncraft
     * sends and any undashed form the backend might use are the same key.
     */
    public static String normalizeUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        String normalized = uuid.trim().replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
