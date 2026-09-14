package com.seqwawa.seq.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The guild's meta as the backend publishes it: which builds exist, which raids
 * exist, and which builds are meta for which raid.
 * <p>
 * Every screen reads its raid list and its build list from here rather than from
 * a hardcoded enum, so a change to the meta reaches players on their next
 * refresh instead of their next update.
 * <p>
 * An empty catalog is a real state, not an error: it is what the panel has
 * before the first fetch lands, and the screens say so rather than pretending
 * nobody owns anything.
 */
public record RaidCatalog(List<RaidBuild> builds, List<RaidType> raids) {

    private static final RaidCatalog EMPTY = new RaidCatalog(List.of(), List.of());

    public RaidCatalog {
        builds = sortedBuilds(builds);
        raids = sortedRaids(raids);
    }

    public static RaidCatalog empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return builds.isEmpty() || raids.isEmpty();
    }

    public RaidBuild build(String key) {
        String normalized = RaidBuild.normalizeKey(key);
        return builds.stream().filter(build -> build.key().equals(normalized)).findFirst().orElse(null);
    }

    public RaidType raid(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        return raids.stream().filter(raid -> raid.key().equals(normalized)).findFirst().orElse(null);
    }

    /** The raid Wynncraft calls {@code apiName}, for reading clear counts. */
    public RaidType raidByApiName(String apiName) {
        if (apiName == null || apiName.isBlank()) {
            return null;
        }
        String normalized = apiName.trim().toLowerCase(Locale.ROOT);
        return raids.stream()
                .filter(raid -> raid.apiName().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    public boolean hasBuild(String key) {
        return build(key) != null;
    }

    /** The label to show for a build key, falling back to the key itself. */
    public String labelFor(String buildKey) {
        RaidBuild found = build(buildKey);
        return found == null ? RaidBuild.normalizeKey(buildKey) : found.displayName();
    }

    /** The raids a build is meta for, in catalog order. */
    public List<RaidType> raidsFor(String buildKey) {
        String normalized = RaidBuild.normalizeKey(buildKey);
        return raids.stream().filter(raid -> raid.buildKeys().contains(normalized)).toList();
    }

    /** A compact list of the raids a build covers, such as {@code TNA / TCC / TWP}. */
    public String raidCoverageLabel(String buildKey) {
        StringBuilder label = new StringBuilder();
        for (RaidType raid : raidsFor(buildKey)) {
            if (!label.isEmpty()) {
                label.append(" / ");
            }
            label.append(raid.shortName());
        }
        return label.toString();
    }

    /** The raids covered by owning {@code ownedBuildKeys}, in catalog order. */
    public List<RaidType> coveredRaids(Set<String> ownedBuildKeys) {
        return raids.stream().filter(raid -> raid.isCoveredBy(ownedBuildKeys)).toList();
    }

    /** Drops keys this catalog does not know, so a stale profile stays usable. */
    public Set<String> retainKnown(Set<String> buildKeys) {
        if (buildKeys == null || buildKeys.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> known = new LinkedHashSet<>();
        for (RaidBuild build : builds) {
            if (buildKeys.stream().anyMatch(build::matches)) {
                known.add(build.key());
            }
        }
        return Set.copyOf(known);
    }

    /** Orders build keys the way the catalog does, for stable chips and lists. */
    public List<String> orderKeys(Set<String> buildKeys) {
        if (buildKeys == null || buildKeys.isEmpty()) {
            return List.of();
        }
        List<String> ordered = new ArrayList<>();
        for (RaidBuild build : builds) {
            if (buildKeys.stream().anyMatch(build::matches)) {
                ordered.add(build.key());
            }
        }
        return List.copyOf(ordered);
    }

    /** Joins build keys into their labels: {@code Ascendancy, Cspring}. */
    public String joinLabels(Set<String> buildKeys) {
        StringBuilder joined = new StringBuilder();
        for (String key : orderKeys(buildKeys)) {
            if (!joined.isEmpty()) {
                joined.append(", ");
            }
            joined.append(labelFor(key));
        }
        return joined.toString();
    }

    private static List<RaidBuild> sortedBuilds(List<RaidBuild> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return value.stream()
                .filter(build -> build != null && build.isValid())
                .sorted(Comparator.comparingInt(RaidBuild::position).thenComparing(RaidBuild::key))
                .toList();
    }

    private static List<RaidType> sortedRaids(List<RaidType> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return value.stream()
                .filter(raid -> raid != null && raid.isValid())
                .sorted(Comparator.comparingInt(RaidType::position).thenComparing(RaidType::key))
                .toList();
    }
}
