package com.seqwawa.seq.map;

import com.seqwawa.seq.map.IngredientWaypointManager.Kind;
import com.seqwawa.seq.map.IngredientWaypointManager.Waypoint;
import com.seqwawa.seq.map.IngredientWaypointManager.WaypointIcon;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class IngredientWaypointRenderer {
    private static final int SPAWN_COLOR = 0xFF55FFFF;
    private static final int TOTEM_COLOR = 0xFFFFAA33;
    private static final int SCREEN_MARGIN = 18;
    private static final int ICON_SIZE = 20;
    private static final int ICON_OUTLINE_MARGIN = 2;
    private static final int AIM_RADIUS = ICON_SIZE / 2 + ICON_OUTLINE_MARGIN;

    private IngredientWaypointRenderer() {}

    public static void initialize() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("seq", "ingredient_waypoints"),
                (guiGraphics, deltaTracker) -> render(guiGraphics));
    }

    static void render(GuiGraphics guiGraphics) {
        Minecraft client = Minecraft.getInstance();
        if (!WynncraftServerPolicy.isCurrentServerAllowed()
                || client.player == null
                || client.level == null) {
            return;
        }
        for (Waypoint waypoint : IngredientWaypointManager.getInstance().waypoints()) {
            renderWaypoint(guiGraphics, client, waypoint);
        }
    }

    private static void renderWaypoint(
            GuiGraphics guiGraphics, Minecraft client, Waypoint waypoint) {
        Vec3 worldPosition = new Vec3(waypoint.x(), waypoint.y() + 1.8, waypoint.z());
        ScreenPosition screenPosition = project(client, worldPosition, guiGraphics.guiWidth(), guiGraphics.guiHeight());
        int x = Math.round(screenPosition.x());
        int y = Math.round(screenPosition.y());
        int markerColor = waypoint.kind() == Kind.TOTEM_SPOT ? TOTEM_COLOR : SPAWN_COLOR;

        if (waypoint.icon().stack().isEmpty()) {
            renderFallbackMarker(guiGraphics, x, y, markerColor);
        } else {
            renderIcon(guiGraphics, x, y, waypoint.icon(), markerColor);
        }

        long distance = Math.round(client.player.position().distanceTo(
                new Vec3(waypoint.x(), waypoint.y(), waypoint.z())));
        String distanceLabel = distance + " blocks";
        boolean aimedAt = isAimedAt(
                screenPosition, guiGraphics.guiWidth(), guiGraphics.guiHeight());
        String label = aimedAt ? waypoint.label() + " · " + distanceLabel : distanceLabel;
        String detail = aimedAt ? waypoint.detail() : "";
        int textWidth = Math.max(client.font.width(label), client.font.width(detail));
        int labelX = clamp(x - textWidth / 2, 3, Math.max(3, guiGraphics.guiWidth() - textWidth - 3));
        int labelHeight = detail.isEmpty() ? 11 : 20;
        int labelY = clamp(
                y - labelHeight - ICON_SIZE / 2 - 3,
                3,
                Math.max(3, guiGraphics.guiHeight() - labelHeight - 1));
        guiGraphics.fill(
                labelX - 3,
                labelY - 2,
                labelX + textWidth + 3,
                labelY + labelHeight,
                0xB0000000);
        guiGraphics.drawString(client.font, label, labelX, labelY, 0xFFFFFFFF);
        if (!detail.isEmpty()) {
            guiGraphics.drawString(client.font, detail, labelX, labelY + 10, 0xFFBBBBBB);
        }
    }

    private static boolean isAimedAt(
            ScreenPosition screenPosition, int screenWidth, int screenHeight) {
        if (!screenPosition.onScreen()) {
            return false;
        }
        float dx = screenPosition.x() - screenWidth / 2f;
        float dy = screenPosition.y() - screenHeight / 2f;
        return dx * dx + dy * dy <= AIM_RADIUS * AIM_RADIUS;
    }

    private static void renderIcon(
            GuiGraphics guiGraphics, int x, int y, WaypointIcon icon, int markerColor) {
        int iconX = x - ICON_SIZE / 2;
        int iconY = y - ICON_SIZE / 2;
        guiGraphics.renderOutline(
                iconX - ICON_OUTLINE_MARGIN,
                iconY - ICON_OUTLINE_MARGIN,
                ICON_SIZE + ICON_OUTLINE_MARGIN * 2,
                ICON_SIZE + ICON_OUTLINE_MARGIN * 2,
                markerColor);
        guiGraphics.renderOutline(
                iconX - 1,
                iconY - 1,
                ICON_SIZE + 2,
                ICON_SIZE + 2,
                0xE6000000);

        float scale = ICON_SIZE / 16f;
        guiGraphics.pose().pushMatrix();
        try {
            guiGraphics.pose().translate(iconX, iconY);
            guiGraphics.pose().scale(scale, scale);
            if (icon.skinLookup() != null) {
                PlayerFaceRenderer.draw(guiGraphics, icon.skinLookup().get(), 0, 0, 16);
            } else {
                guiGraphics.renderItem(icon.stack(), 0, 0);
            }
        } finally {
            guiGraphics.pose().popMatrix();
        }
    }

    private static void renderFallbackMarker(GuiGraphics guiGraphics, int x, int y, int markerColor) {
        guiGraphics.fill(x - 4, y, x + 1, y + 5, 0xCC000000);
        guiGraphics.fill(x, y - 4, x + 5, y + 1, 0xCC000000);
        guiGraphics.fill(x - 3, y, x + 1, y + 4, markerColor);
        guiGraphics.fill(x, y - 3, x + 4, y + 1, markerColor);
    }

    private static ScreenPosition project(
            Minecraft client, Vec3 worldPosition, int screenWidth, int screenHeight) {
        Vec3 projected = client.gameRenderer.projectPointToScreen(worldPosition);
        Vec3 relative = worldPosition.subtract(client.gameRenderer.getMainCamera().position());
        Vector3fc forward = client.gameRenderer.getMainCamera().forwardVector();
        boolean behind = forward.dot((float) relative.x, (float) relative.y, (float) relative.z) <= 0;

        double ndcX = behind ? -projected.x : projected.x;
        double ndcY = behind ? -projected.y : projected.y;
        double screenX = (ndcX + 1.0) * 0.5 * screenWidth;
        double screenY = (1.0 - ndcY) * 0.5 * screenHeight;
        boolean outside = behind
                || screenX < SCREEN_MARGIN
                || screenX > screenWidth - SCREEN_MARGIN
                || screenY < SCREEN_MARGIN
                || screenY > screenHeight - SCREEN_MARGIN;
        if (!outside) {
            return new ScreenPosition((float) screenX, (float) screenY, true);
        }

        double centerX = screenWidth / 2.0;
        double centerY = screenHeight / 2.0;
        double dx = screenX - centerX;
        double dy = screenY - centerY;
        if (Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) {
            dy = screenHeight;
        }
        double xScale = (screenWidth / 2.0 - SCREEN_MARGIN) / Math.max(0.001, Math.abs(dx));
        double yScale = (screenHeight / 2.0 - SCREEN_MARGIN) / Math.max(0.001, Math.abs(dy));
        double scale = Math.min(1.0, Math.min(xScale, yScale));
        return new ScreenPosition(
                (float) (centerX + dx * scale),
                (float) (centerY + dy * scale),
                false);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ScreenPosition(float x, float y, boolean onScreen) {}
}
