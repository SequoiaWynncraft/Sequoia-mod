package com.seqwawa.seq.map;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

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
            double z,
            WaypointIcon icon) {
        public Waypoint(
                String id,
                Kind kind,
                String label,
                String detail,
                double x,
                double y,
                double z) {
            this(id, kind, label, detail, x, y, z, WaypointIcon.empty());
        }

        public Waypoint {
            id = requireText(id, "id");
            kind = Objects.requireNonNull(kind, "kind");
            label = requireText(label, "label");
            detail = detail == null ? "" : detail.trim();
            icon = icon == null ? WaypointIcon.empty() : icon;
        }

        private static String requireText(String value, String field) {
            String normalized = Objects.requireNonNull(value, field).trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(field + " cannot be blank");
            }
            return normalized;
        }
    }

    public record WaypointIcon(ItemStack stack, Supplier<PlayerSkin> skinLookup) {
        public WaypointIcon {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }

        public static WaypointIcon empty() {
            return new WaypointIcon(ItemStack.EMPTY, null);
        }

        public static WaypointIcon of(ItemStack stack, Supplier<PlayerSkin> skinLookup) {
            return new WaypointIcon(stack, skinLookup);
        }
    }
}
