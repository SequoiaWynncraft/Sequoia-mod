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
            List<DetailLine> detailLines,
            double x,
            double y,
            double z,
            double radius,
            WaypointIcon icon) {
        public Waypoint(
                String id,
                Kind kind,
                String label,
                String detail,
                double x,
                double y,
                double z) {
            this(id, kind, label, detail, x, y, z, 0, WaypointIcon.empty());
        }

        public Waypoint(
                String id,
                Kind kind,
                String label,
                String detail,
                double x,
                double y,
                double z,
                WaypointIcon icon) {
            this(id, kind, label, detail, x, y, z, 0, icon);
        }

        public Waypoint(
                String id,
                Kind kind,
                String label,
                String detail,
                double x,
                double y,
                double z,
                double radius,
                WaypointIcon icon) {
            this(
                    id,
                    kind,
                    label,
                    detail == null || detail.isBlank()
                            ? List.of()
                            : List.of(new DetailLine(detail, DetailLine.DEFAULT_COLOR)),
                    x,
                    y,
                    z,
                    radius,
                    icon);
        }

        public Waypoint(
                String id,
                Kind kind,
                String label,
                List<DetailLine> detailLines,
                double x,
                double y,
                double z,
                WaypointIcon icon) {
            this(id, kind, label, detailLines, x, y, z, 0, icon);
        }

        public Waypoint {
            id = requireText(id, "id");
            kind = Objects.requireNonNull(kind, "kind");
            label = requireText(label, "label");
            detailLines = detailLines == null
                    ? List.of()
                    : detailLines.stream()
                            .filter(Objects::nonNull)
                            .toList();
            radius = Math.max(0, radius);
            icon = icon == null ? WaypointIcon.empty() : icon;
        }

        public String detail() {
            return detailLines.stream()
                    .map(DetailLine::text)
                    .reduce((first, second) -> first + "\n" + second)
                    .orElse("");
        }

        private static String requireText(String value, String field) {
            String normalized = Objects.requireNonNull(value, field).trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(field + " cannot be blank");
            }
            return normalized;
        }
    }

    public record DetailLine(String text, int color) {
        private static final int DEFAULT_COLOR = 0xFFBBBBBB;

        public DetailLine {
            text = text == null ? "" : text.trim();
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
