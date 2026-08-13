package com.seqwawa.seq.wynnbuilder.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The ordered list of WynnBuilder data versions.
 *
 * <p>This ordering is load-bearing: a build hash stores its data version as a 10-bit <em>index</em>
 * into this list, not as a version string. Getting the order wrong silently resolves every item in
 * a shared link to the wrong ID.
 *
 * <p>Upstream keeps the list in {@code js/load_item.js}. It is exactly the set of numeric directory
 * names under {@code data/} in ascending numeric order, so {@link #merge} can extend a stale
 * built-in list from a directory listing without shipping a mod update. Non-numeric entries such as
 * {@code baseline} are not part of the list and must be filtered out before merging.
 */
public final class WynnDataVersions {
    /** Snapshot of the upstream list; extended at runtime through {@link #merge}. */
    private static final List<String> BUILT_IN = List.of(
            "2.0.1.1", "2.0.1.2", "2.0.2.1", "2.0.2.3", "2.0.3.1", "2.0.4.1", "2.0.4.3", "2.0.4.4",
            "2.1.0.0", "2.1.0.1", "2.1.1.0", "2.1.1.1", "2.1.1.2", "2.1.1.3", "2.1.1.4", "2.1.1.5",
            "2.1.1.6", "2.1.1.7", "2.1.2.0", "2.1.3.0", "2.1.3.4", "2.1.4.0", "2.1.5.0", "2.1.6.0",
            "2.2.0.0", "2.2.0.7", "2.2.0.12", "2.2.0.14", "2.2.0.19", "2.2.0.21", "2.2.0.31",
            "2.2.1.0", "2.2.2.0", "2.2.3.0");

    /** Orders "2.2.0.7" before "2.2.0.12", which a plain string sort would get backwards. */
    public static final Comparator<String> NUMERIC_ORDER = (left, right) -> {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int size = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < size; i++) {
            int leftValue = i < leftParts.length ? parsePart(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? parsePart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return left.compareTo(right);
    };

    private final List<String> versions;

    private WynnDataVersions(List<String> versions) {
        this.versions = List.copyOf(versions);
    }

    /** The list as shipped with the mod. */
    public static WynnDataVersions builtIn() {
        return new WynnDataVersions(BUILT_IN);
    }

    /**
     * Returns a list combining the built-in versions with any discovered ones, in numeric order.
     *
     * <p>Discovered names that are not numeric versions are ignored, so a raw directory listing can
     * be passed straight through. Merging never reorders or drops known versions, which keeps
     * previously shared links decodable.
     */
    public WynnDataVersions merge(List<String> discovered) {
        if (discovered == null || discovered.isEmpty()) {
            return this;
        }
        List<String> merged = new ArrayList<>(versions);
        boolean changed = false;
        for (String candidate : discovered) {
            if (candidate == null) {
                continue;
            }
            String trimmed = candidate.trim();
            if (!isNumericVersion(trimmed) || merged.contains(trimmed)) {
                continue;
            }
            merged.add(trimmed);
            changed = true;
        }
        if (!changed) {
            return this;
        }
        merged.sort(NUMERIC_ORDER);
        return new WynnDataVersions(merged);
    }

    public static boolean isNumericVersion(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String[] parts = name.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<String> all() {
        return versions;
    }

    public int size() {
        return versions.size();
    }

    /** The index encoded in the build header for the newest known data version. */
    public int latestIndex() {
        return versions.size() - 1;
    }

    public String latest() {
        return versions.get(latestIndex());
    }

    /** Resolves an encoded version index, or {@code null} when the link is newer than we know. */
    public String byIndex(int index) {
        return index >= 0 && index < versions.size() ? versions.get(index) : null;
    }

    public int indexOf(String version) {
        return versions.indexOf(Objects.requireNonNull(version, "version"));
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
