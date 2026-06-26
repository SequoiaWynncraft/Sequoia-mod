package com.seqwawa.seq.radiance;

import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class PingRenderer {
    private static final int WORLD_RING_SEGMENTS = 32;
    private static final int WORLD_RING_PARTICLES_PER_TICK = 2;

    private PingRenderer() {}

    public static void renderOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        List<WorldPing> activePings = PingManager.getActivePings();

        for (WorldPing ping : activePings) {
            if (!ping.showCircle()) {
                continue;
            }

            ScreenPos screenPos = worldToScreen(client, ping.pos());
            if (screenPos.behindCamera()) {
                continue;
            }

            int x = Math.round(screenPos.x());
            int y = Math.round(screenPos.y());

            guiGraphics.fill(x - 2, y - 4, x + 2, y + 4, 0xFFFF0000);
            guiGraphics.fill(x - 4, y - 2, x + 4, y + 2, 0xFFFF0000);

            String label = "Radiance";
            int textWidth = client.font.width(label);
            guiGraphics.drawString(client.font, label, x - textWidth / 2, y - 16, 0xFFFFFFFF);
        }
    }

    public static boolean renderWorld(Minecraft client, WorldPing ping) {
        if (client.level == null || !ping.showCircle()) {
            return false;
        }

        Vec3 pos = ping.pos();
        double radius = ping.radius();
        double y = pos.y + 0.05;

        int startSegment = Math.floorMod(ping.ringPhase() * WORLD_RING_PARTICLES_PER_TICK, WORLD_RING_SEGMENTS);
        for (int i = 0; i < WORLD_RING_PARTICLES_PER_TICK; i++) {
            int segment = (startSegment + i) % WORLD_RING_SEGMENTS;
            double angle = (Math.PI * 2.0 * segment) / WORLD_RING_SEGMENTS;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;

            client.level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.0, 0.0);
        }

        return startSegment + WORLD_RING_PARTICLES_PER_TICK >= WORLD_RING_SEGMENTS;
    }

    private static ScreenPos worldToScreen(Minecraft client, Vec3 worldPos) {
        Vec3 projected = client.gameRenderer.projectPointToScreen(worldPos);
        Vec3 cameraPos = client.gameRenderer.getMainCamera().position();
        Vec3 relative = worldPos.subtract(cameraPos);
        Vector3fc forward = client.gameRenderer.getMainCamera().forwardVector();

        boolean behindCamera = forward.dot((float) relative.x, (float) relative.y, (float) relative.z) <= 0.0f;
        if (behindCamera) {
            return new ScreenPos(0.0f, 0.0f, true);
        }

        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        float screenX = (float) ((projected.x + 1.0) * 0.5 * width);
        float screenY = (float) ((1.0 - projected.y) * 0.5 * height);

        return new ScreenPos(screenX, screenY, false);
    }

    private record ScreenPos(float x, float y, boolean behindCamera) {}
}
