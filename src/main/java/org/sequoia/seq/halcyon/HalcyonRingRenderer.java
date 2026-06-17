package org.sequoia.seq.halcyon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

public final class HalcyonRingRenderer {
	private static final int SEGMENTS = 192;
	private static final double TWO_PI = Math.PI * 2.0;
	private static final int COLOR_RED = 0;
	private static final int COLOR_GREEN = 255;
	private static final int COLOR_BLUE = 255;
	private static final int COLOR_ALPHA = 230;
	private static final double BOTTOM_Y_OFFSET = 0.06;
	private static final double TOP_Y_OFFSET = 0.28;
	private static final double[] COS = new double[SEGMENTS + 1];
	private static final double[] SIN = new double[SEGMENTS + 1];

	static {
		for (int i = 0; i <= SEGMENTS; i++) {
			double angle = TWO_PI * i / SEGMENTS;
			COS[i] = Math.cos(angle);
			SIN[i] = Math.sin(angle);
		}
	}

	private HalcyonRingRenderer() {
	}

	public static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}

		if (!HalcyonHeldItem.isHoldingHalcyon(client)) return;
		if (!HalcyonTextureDetector.hasKnownRange()) return;

		double radius = HalcyonTextureDetector.getCurrentRange();
		if (radius <= 0.0) return;

		float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 center = client.player.getPosition(tickDelta);
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		PoseStack.Pose pose = context.matrices().last();
		VertexConsumer vertices = context.consumers().getBuffer(RenderTypes.debugQuads());

		renderRingWall(vertices, pose, center, camera, radius);
	}

	private static void renderRingWall(VertexConsumer vertices, PoseStack.Pose pose, Vec3 center, Vec3 camera, double radius) {
		double bottomY = center.y + BOTTOM_Y_OFFSET - camera.y;
		double topY = center.y + TOP_Y_OFFSET - camera.y;

		for (int i = 0; i < SEGMENTS; i++) {
			renderWallSegment(vertices, pose, center, camera, bottomY, topY, i, radius);
		}
	}

	private static void renderWallSegment(
		VertexConsumer vertices,
		PoseStack.Pose pose,
		Vec3 center,
		Vec3 camera,
		double bottomY,
		double topY,
		int segment,
		double radius
	) {
		double startCos = COS[segment];
		double startSin = SIN[segment];
		double endCos = COS[segment + 1];
		double endSin = SIN[segment + 1];
		double startX = center.x + startCos * radius - camera.x;
		double startZ = center.z + startSin * radius - camera.z;
		double endX = center.x + endCos * radius - camera.x;
		double endZ = center.z + endSin * radius - camera.z;

		addVertex(vertices, pose, startX, bottomY, startZ);
		addVertex(vertices, pose, endX, bottomY, endZ);
		addVertex(vertices, pose, endX, topY, endZ);
		addVertex(vertices, pose, startX, topY, startZ);

		addVertex(vertices, pose, startX, topY, startZ);
		addVertex(vertices, pose, endX, topY, endZ);
		addVertex(vertices, pose, endX, bottomY, endZ);
		addVertex(vertices, pose, startX, bottomY, startZ);
	}

	private static void addVertex(VertexConsumer vertices, PoseStack.Pose pose, double x, double y, double z) {
		vertices.addVertex(pose, (float) x, (float) y, (float) z)
			.setColor(COLOR_RED, COLOR_GREEN, COLOR_BLUE, COLOR_ALPHA);
	}
}
