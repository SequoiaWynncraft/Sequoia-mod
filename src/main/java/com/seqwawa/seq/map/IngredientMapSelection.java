package com.seqwawa.seq.map;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Transient selection state for ingredient spawn and curated mob-totem markers.
 */
public final class IngredientMapSelection {
    private final Set<String> spawnMarkerIds = new LinkedHashSet<>();
    private final Set<String> totemSpotIds = new LinkedHashSet<>();

    public boolean toggleSpawn(String markerId) {
        return toggle(spawnMarkerIds, markerId);
    }

    public boolean toggleTotem(String spotId) {
        return toggle(totemSpotIds, spotId);
    }

    public boolean isSpawnSelected(String markerId) {
        return markerId != null && spawnMarkerIds.contains(markerId);
    }

    public boolean isTotemSelected(String spotId) {
        return spotId != null && totemSpotIds.contains(spotId);
    }

    public int spawnCount() {
        return spawnMarkerIds.size();
    }

    public int totemCount() {
        return totemSpotIds.size();
    }

    public int size() {
        return spawnMarkerIds.size() + totemSpotIds.size();
    }

    public boolean isEmpty() {
        return spawnMarkerIds.isEmpty() && totemSpotIds.isEmpty();
    }

    public void clear() {
        spawnMarkerIds.clear();
        totemSpotIds.clear();
    }

    private static boolean toggle(Set<String> selectedIds, String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (selectedIds.remove(id)) {
            return false;
        }
        selectedIds.add(id);
        return true;
    }
}
