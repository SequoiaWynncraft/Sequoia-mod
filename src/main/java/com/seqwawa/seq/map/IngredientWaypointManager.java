package com.seqwawa.seq.map;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Session-only waypoint state populated from selected ingredient map markers.
 */
public final class IngredientWaypointManager {
    private static final IngredientWaypointManager INSTANCE = new IngredientWaypointManager();

    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();

    IngredientWaypointManager() {}

    public static IngredientWaypointManager getInstance() {
        return INSTANCE;
    }

    public synchronized void replaceAll(Collection<Waypoint> replacements) {
        waypoints.clear();
        if (replacements == null) {
            return;
        }
        for (Waypoint waypoint : replacements) {
            if (waypoint != null) {
                waypoints.put(waypoint.id(), waypoint);
            }
        }
    }

    public synchronized List<Waypoint> waypoints() {
        return List.copyOf(waypoints.values());
    }

    public synchronized int size() {
        return waypoints.size();
    }

    public synchronized void clear() {
        waypoints.clear();
    }

    public enum Kind {
        INGREDIENT_SPAWN,
        TOTEM_SPOT
    }

    public record Waypoint(
            String id,
            Kind kind,
            String label,
            String detail,
            double x,
            double y,
            double z) {
        public Waypoint {
            id = requireText(id, "id");
            kind = Objects.requireNonNull(kind, "kind");
            label = requireText(label, "label");
            detail = detail == null ? "" : detail.trim();
        }

        private static String requireText(String value, String field) {
            String normalized = Objects.requireNonNull(value, field).trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(field + " cannot be blank");
            }
            return normalized;
        }
    }
}
