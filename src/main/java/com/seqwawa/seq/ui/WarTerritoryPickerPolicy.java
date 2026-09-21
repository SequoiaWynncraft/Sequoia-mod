package com.seqwawa.seq.ui;

import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.lwjgl.glfw.GLFW;

/** Pure selection and focus policy for the custom territory-picker controls. */
final class WarTerritoryPickerPolicy {
    private WarTerritoryPickerPolicy() {}

    static TerritoryAccess territoryAccess(WarPlannerSnapshot snapshot, Long editedZoneId) {
        if (snapshot == null) return TerritoryAccess.empty();

        Map<String, String> territoryNames = new LinkedHashMap<>();
        for (String territory : snapshot.territories()) {
            if (territory != null && !territory.isBlank()) {
                territoryNames.putIfAbsent(key(territory), territory);
            }
        }

        Map<String, Zone> unavailableOwners = new LinkedHashMap<>();
        for (Zone zone : snapshot.zones()) {
            if (editedZoneId != null && zone.id() == editedZoneId) continue;
            for (String territory : zone.territories()) {
                String territoryKey = key(territory);
                if (territoryNames.containsKey(territoryKey)) {
                    unavailableOwners.putIfAbsent(territoryKey, zone);
                }
            }
        }

        Set<String> selectableKeys = new LinkedHashSet<>(territoryNames.keySet());
        selectableKeys.removeAll(unavailableOwners.keySet());
        return new TerritoryAccess(territoryNames, selectableKeys, unavailableOwners);
    }

    static List<ControlTarget> keyboardOrder(boolean canManage, int teamCount, boolean saving) {
        List<ControlTarget> controls = new ArrayList<>();
        if (!saving) {
            controls.add(ControlTarget.named(ControlKind.NAME));
            for (int teamIndex = 0; teamIndex < Math.max(0, teamCount); teamIndex++) {
                controls.add(ControlTarget.team(teamIndex));
            }
            controls.add(ControlTarget.named(ControlKind.CLEAR));
        }
        controls.add(ControlTarget.named(ControlKind.CANCEL));
        if (!saving) controls.add(ControlTarget.named(ControlKind.SAVE));
        controls.add(ControlTarget.named(ControlKind.RESOURCE_COLORS));
        if (canManage) controls.add(ControlTarget.named(ControlKind.LOCK_MAIN_MAP));
        return List.copyOf(controls);
    }

    static ControlTarget nextKeyboardTarget(
            ControlTarget current, boolean backwards, boolean canManage, int teamCount, boolean saving) {
        List<ControlTarget> controls = keyboardOrder(canManage, teamCount, saving);
        if (controls.isEmpty()) return null;
        int currentIndex = controls.indexOf(current);
        if (currentIndex < 0) return backwards ? controls.get(controls.size() - 1) : controls.get(0);
        int delta = backwards ? -1 : 1;
        return controls.get(Math.floorMod(currentIndex + delta, controls.size()));
    }

    static boolean isActivationKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE;
    }

    static int scrollStart(int requestedStart, int itemCount, int visibleRows) {
        int maximum = Math.max(0, Math.max(0, itemCount) - Math.max(1, visibleRows));
        return Math.max(0, Math.min(requestedStart, maximum));
    }

    private static String key(String territory) {
        return territory == null ? "" : territory.toLowerCase(Locale.ROOT);
    }

    static final class TerritoryAccess {
        private final Map<String, String> namesByKey;
        private final Set<String> selectableKeys;
        private final Map<String, Zone> unavailableOwnersByKey;

        private TerritoryAccess(
                Map<String, String> namesByKey,
                Set<String> selectableKeys,
                Map<String, Zone> unavailableOwnersByKey) {
            this.namesByKey = Collections.unmodifiableMap(new LinkedHashMap<>(namesByKey));
            this.selectableKeys = Collections.unmodifiableSet(new LinkedHashSet<>(selectableKeys));
            this.unavailableOwnersByKey =
                    Collections.unmodifiableMap(new LinkedHashMap<>(unavailableOwnersByKey));
        }

        static TerritoryAccess empty() {
            return new TerritoryAccess(Map.of(), Set.of(), Map.of());
        }

        Set<String> visibleNames() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(namesByKey.values()));
        }

        Set<String> selectableNames() {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (String selectableKey : selectableKeys) {
                String name = namesByKey.get(selectableKey);
                if (name != null) result.add(name);
            }
            return Collections.unmodifiableSet(result);
        }

        boolean isVisible(String territory) {
            return namesByKey.containsKey(key(territory));
        }

        boolean isSelectable(String territory) {
            return selectableKeys.contains(key(territory));
        }

        Zone unavailableOwner(String territory) {
            return unavailableOwnersByKey.get(key(territory));
        }
    }

    enum ControlKind {
        NAME,
        TEAM,
        CLEAR,
        SAVE,
        CANCEL,
        RESOURCE_COLORS,
        LOCK_MAIN_MAP
    }

    record ControlTarget(ControlKind kind, int teamIndex) {
        ControlTarget {
            if (kind == null) throw new IllegalArgumentException("Control kind is required.");
            if (kind == ControlKind.TEAM && teamIndex < 0) {
                throw new IllegalArgumentException("Team control index cannot be negative.");
            }
            if (kind != ControlKind.TEAM) teamIndex = -1;
        }

        static ControlTarget named(ControlKind kind) {
            return new ControlTarget(kind, -1);
        }

        static ControlTarget team(int teamIndex) {
            return new ControlTarget(ControlKind.TEAM, teamIndex);
        }
    }
}
