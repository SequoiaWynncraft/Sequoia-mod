package com.seqwawa.seq.courage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Courage aura as a filled translucent disc with a short rim that keeps
 * the boundary readable from eye height.
 *
 * <p>The debug-quad pipeline is built with culling off, so every surface is emitted
 * exactly once: a second winding would blend over the first and quietly raise the
 * configured opacity by half again.
 */
final class CourageAuraRenderer {
    static final int SEGMENTS = 72;

    private static final double TWO_PI = Math.PI * 2.0;
    private static final double GROUND_OFFSET = 0.06;
    private static final double RIM_HEIGHT = 0.32;
    private static final double[] COS = new double[SEGMENTS + 1];
    private static final double[] SIN = new double[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = TWO_PI * i / SEGMENTS;
            COS[i] = Math.cos(angle);
            SIN[i] = Math.sin(angle);
        }
    }

    private CourageAuraRenderer() {}

    static void render(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 center,
            Vec3 camera,
            double radius,
            int rgb,
            int alpha) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int clampedAlpha = Math.clamp(alpha, 0, 255);

        double centerX = center.x - camera.x;
        double centerZ = center.z - camera.z;
        double groundY = center.y + GROUND_OFFSET - camera.y;
        double rimY = groundY + RIM_HEIGHT;

        for (int segment = 0; segment < SEGMENTS; segment++) {
            double startX = centerX + COS[segment] * radius;
            double startZ = centerZ + SIN[segment] * radius;
            double endX = centerX + COS[segment + 1] * radius;
            double endZ = centerZ + SIN[segment + 1] * radius;

            // Ground slice, as a quad folded onto the centre so the fan tiles without overlap.
            addVertex(vertices, pose, centerX, groundY, centerZ, red, green, blue, clampedAlpha);
            addVertex(vertices, pose, startX, groundY, startZ, red, green, blue, clampedAlpha);
            addVertex(vertices, pose, endX, groundY, endZ, red, green, blue, clampedAlpha);
            addVertex(vertices, pose, centerX, groundY, centerZ, red, green, blue, clampedAlpha);

            addVertex(vertices, pose, startX, groundY, startZ, red, green, blue, clampedAlpha);
            addVertex(vertices, pose, endX, groundY, endZ, red, green, blue, clampedAlpha);
            addVertex(vertices, pose, endX, rimY, endZ, red, green, blue, clampedAlpha);
            addVertex(vertices, pose, startX, rimY, startZ, red, green, blue, clampedAlpha);
        }
    }

    private static void addVertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            double x,
            double y,
            double z,
            int red,
            int green,
            int blue,
            int alpha) {
        vertices.addVertex(pose, (float) x, (float) y, (float) z).setColor(red, green, blue, alpha);
    }
}
