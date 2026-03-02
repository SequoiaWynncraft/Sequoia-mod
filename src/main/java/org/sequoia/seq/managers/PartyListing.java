package org.sequoia.seq.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequoia.seq.model.Activity;
import org.sequoia.seq.model.Listing;
import org.sequoia.seq.model.PartyMode;

/**
 * Adapter class wrapping {@link Listing} with public mutable fields
 * matching what {@link org.sequoia.seq.ui.PartyFinderScreen} expects.
 *
 * <p>
 * Also provides static mapping utilities between the three name
 * representations used across the system:
 * <ul>
 * <li><b>Display name</b> — full human-readable name shown in the UI
 * (e.g. "Nest of the Grootslangs")</li>
 * <li><b>Backend name</b> — short code stored in the backend DB / Activity
 * record (e.g. "NOTG")</li>
 * <li><b>Asset key</b> — lowercase key used to look up icons in
 * {@link AssetManager} (e.g. "notg")</li>
 * </ul>
 */
public class PartyListing {

    // ── Name mapping tables ──

    /** Display name → backend short code. Iteration order = RAID_TYPES order. */
    private static final Map<String, String> DISPLAY_TO_BACKEND = new LinkedHashMap<>();

    /** Backend short code → display name. */
    private static final Map<String, String> BACKEND_TO_DISPLAY = new LinkedHashMap<>();

    /**
     * Display name → asset key (lowercase short code, or null for text-fallback
     * raids).
     */
    private static final Map<String, String> DISPLAY_TO_ASSET = new LinkedHashMap<>();

    static {
        register("Nest of the Grootslangs", "NOTG", "notg");
        register("The Nameless Anomaly", "TNA", "tna");
        register("The Canyon Colossus", "TCC", "tcc");
        register("Nexus of Light", "NOL", "nol");
        register("Prelude to Annihilation", "ANNI", "annihilation");

        // Backend aliases observed in API payloads
        BACKEND_TO_DISPLAY.put("The Orphion's Nexus of Light", "Nexus of Light");
        BACKEND_TO_DISPLAY.put("The Orphions Nexus of Light", "Nexus of Light");
    }

    private static void register(
            String displayName,
            String backendName,
            String assetKey) {
        DISPLAY_TO_BACKEND.put(displayName, backendName);
        BACKEND_TO_DISPLAY.put(backendName, displayName);
        if (assetKey != null) {
            DISPLAY_TO_ASSET.put(displayName, assetKey);
        }
    }

    // ── Public mapping API ──

    /**
     * Converts a display name (e.g. "Nest of the Grootslangs") to the
     * corresponding asset key (e.g. "notg"), or {@code null} if the raid
     * uses a text fallback (e.g. "Prelude to Annihilation").
     */
    public static String displayNameToAssetKey(String displayName) {
        if (displayName == null) {
            return null;
        }

        String trimmed = displayName.trim();
        String asset = lookupIgnoreCase(DISPLAY_TO_ASSET, trimmed);
        if (asset != null) {
            return asset;
        }

        // Handle backend short codes passed directly (e.g. "ANNI")
        String displayFromBackend = backendNameToDisplayName(trimmed);
        if (!displayFromBackend.equals(trimmed)) {
            return lookupIgnoreCase(DISPLAY_TO_ASSET, displayFromBackend);
        }

        return null;
    }

    /**
     * Converts a backend activity name (short code like "NOTG") to the
     * full display name. Returns the input unchanged if no mapping exists
     * (graceful degradation for unknown activities).
     */
    public static String backendNameToDisplayName(String backendName) {
        if (backendName == null) {
            return null;
        }
        String trimmed = backendName.trim();
        String mapped = lookupIgnoreCase(BACKEND_TO_DISPLAY, trimmed);
        return mapped != null ? mapped : trimmed;
    }

    /**
     * Converts a display name back to the backend short code.
     * Returns the input unchanged if no mapping exists.
     */
    public static String displayNameToBackendName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String trimmed = displayName.trim();
        String mapped = lookupIgnoreCase(DISPLAY_TO_BACKEND, trimmed);
        return mapped != null ? mapped : trimmed;
    }

    private static String lookupIgnoreCase(
            Map<String, String> map,
            String key) {
        String direct = map.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // ── Instance fields (adapter layer for screen) ──

    public boolean expanded;
    public final List<PartyMember> members;
    public final int maxSize;
    public final List<String> tags;
    public final long id;
    public final String leaderUUID;
    private final Listing backing;

    public PartyListing(Listing listing) {
        this.backing = listing;
        this.id = listing.id();
        this.leaderUUID = listing.leaderUUID();
        this.expanded = false; // managed externally by PartyFinderManager's expanded state map
        this.maxSize = listing.maxPartySize();
        this.members = safeMembers(listing)
                .stream()
                .map(m -> new PartyMember(m, listing.leaderUUID()))
                .toList();
        this.tags = buildTags(listing);
    }

    private static List<org.sequoia.seq.model.Member> safeMembers(Listing listing) {
        List<org.sequoia.seq.model.Member> members = listing.members();
        if (members == null) {
            return List.of();
        }
        return members.stream().filter(Objects::nonNull).toList();
    }

    private static List<String> buildTags(Listing listing) {
        List<String> t = new ArrayList<>();
        t.addAll(getDisplayActivityNames(listing));
        t.add(listing.mode() == PartyMode.CHILL ? "Chill" : "Grind");
        return Collections.unmodifiableList(t);
    }

    private static List<String> getDisplayActivityNames(Listing listing) {
        List<String> displayNames = listing.resolvedActivities()
                .stream()
                .filter(Objects::nonNull)
                .map(Activity::name)
                .filter(Objects::nonNull)
                .map(PartyListing::backendNameToDisplayName)
                .toList();

        if (displayNames.isEmpty()) {
            return List.of("Unknown Activity");
        }

        List<String> ordered = new ArrayList<>(displayNames.size());
        for (String knownDisplay : DISPLAY_TO_BACKEND.keySet()) {
            for (String name : displayNames) {
                if (knownDisplay.equalsIgnoreCase(name)) {
                    ordered.add(knownDisplay);
                    break;
                }
            }
        }
        for (String name : displayNames) {
            boolean alreadyAdded = false;
            for (String existing : ordered) {
                if (existing.equalsIgnoreCase(name)) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded) {
                ordered.add(name);
            }
        }

        return ordered;
    }

    /**
     * Returns the raid tag(s) as display names — used for icon rendering.
     */
    public List<String> getRaidTags() {
        return getDisplayActivityNames(backing);
    }

    /**
     * Label shown on party cards, e.g. "Chill · Nest of the Grootslangs".
     */
    public String displayLabel() {
        String modeLabel = backing.mode() == PartyMode.CHILL ? "Chill" : "Grind";
        String displayNames = String.join(", ", getDisplayActivityNames(backing));
        return modeLabel + " · " + displayNames;
    }

    /**
     * Label shown on party cards with short activity names,
     * e.g. "Chill · NOTG, TNA".
     */
    public String displayShortLabel() {
        String modeLabel = backing.mode() == PartyMode.CHILL ? "Chill" : "Grind";
        List<String> shortNames = getDisplayActivityNames(backing)
                .stream()
                .map(PartyListing::displayNameToBackendName)
                .map(name -> name == null ? "" : name.trim())
                .filter(name -> !name.isEmpty())
                .toList();
        return modeLabel + " · " + (shortNames.isEmpty() ? "Unknown Activity" : String.join(", ", shortNames));
    }

    public PartyMember getLeader() {
        return members
                .stream()
                .filter(m -> m.isLeader)
                .findFirst()
                .orElse(members.isEmpty() ? null : members.get(0));
    }

    public Listing getBacking() {
        return backing;
    }
}
