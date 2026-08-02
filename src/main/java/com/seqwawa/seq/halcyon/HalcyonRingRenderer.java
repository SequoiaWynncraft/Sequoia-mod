package com.seqwawa.seq.halcyon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.client.SeqClient;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

public final class HalcyonRingRenderer {
	static final int SEGMENTS = 96;
	private static final double TWO_PI = Math.PI * 2.0;
	private static final int COLOR_RED = 0;
	private static final int COLOR_GREEN = 255;
	private static final int COLOR_BLUE = 255;
	private static final int COLOR_ALPHA = 230;
	private static final int DEFAULT_COLOR_RGB = (COLOR_RED << 16) | (COLOR_GREEN << 8) | COLOR_BLUE;
	private static final double BOTTOM_Y_OFFSET = 0.06;
	private static final double TOP_Y_OFFSET = 0.28;
	private static final double[] COS = new double[SEGMENTS + 1];
	private static final double[] SIN = new double[SEGMENTS + 1];
	private static final double PREVIEW_RADIUS = 7.0;
	private static boolean colorPreviewActive;

	static {
		for (int i = 0; i <= SEGMENTS; i++) {
			double angle = TWO_PI * i / SEGMENTS;
			COS[i] = Math.cos(angle);
			SIN[i] = Math.sin(angle);
		}
	}

	private HalcyonRingRenderer() {
	}

	public static void setColorPreviewActive(boolean active) {
		colorPreviewActive = active;
	}

	public static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}

		double radius;
		if (colorPreviewActive) {
			radius = HalcyonTextureDetector.hasKnownRange()
				? HalcyonTextureDetector.getCurrentRange()
				: PREVIEW_RADIUS;
		} else {
			if (!HalcyonHeldItem.isHoldingHalcyon()) return;
			if (!HalcyonTextureDetector.hasKnownRange()) return;
			radius = HalcyonTextureDetector.getCurrentRange();
		}
		if (radius <= 0.0) return;

		float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 center = client.player.getPosition(tickDelta);
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		PoseStack.Pose pose = context.matrices().last();
		VertexConsumer vertices = context.consumers().getBuffer(RenderTypes.debugQuads());

		renderRingWall(vertices, pose, center, camera, radius, getConfiguredColor(), COLOR_ALPHA);
	}

	private static int getConfiguredColor() {
		return SeqClient.getHalcyonRingColorSetting() == null
			? DEFAULT_COLOR_RGB
			: SeqClient.getHalcyonRingColorSetting().getValue();
	}

	public static void renderRingWall(VertexConsumer vertices, PoseStack.Pose pose, Vec3 center, Vec3 camera, double radius) {
		renderRingWall(vertices, pose, center, camera, radius, DEFAULT_COLOR_RGB, COLOR_ALPHA);
	}

	public static void renderRingWall(
		VertexConsumer vertices,
		PoseStack.Pose pose,
		Vec3 center,
		Vec3 camera,
		double radius,
		int rgb,
		int alpha
	) {
		double bottomY = center.y + BOTTOM_Y_OFFSET - camera.y;
		double topY = center.y + TOP_Y_OFFSET - camera.y;
		int red = (rgb >> 16) & 0xFF;
		int green = (rgb >> 8) & 0xFF;
		int blue = rgb & 0xFF;
		int clampedAlpha = Math.max(0, Math.min(255, alpha));

		for (int i = 0; i < SEGMENTS; i++) {
			renderWallSegment(
				vertices,
				pose,
				center,
				camera,
				bottomY,
				topY,
				i,
				radius,
				red,
				green,
				blue,
				clampedAlpha
			);
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
		double radius,
		int red,
		int green,
		int blue,
		int alpha
	) {
		double startCos = COS[segment];
		double startSin = SIN[segment];
		double endCos = COS[segment + 1];
		double endSin = SIN[segment + 1];
		double startX = center.x + startCos * radius - camera.x;
		double startZ = center.z + startSin * radius - camera.z;
		double endX = center.x + endCos * radius - camera.x;
		double endZ = center.z + endSin * radius - camera.z;

		addVertex(vertices, pose, startX, bottomY, startZ, red, green, blue, alpha);
		addVertex(vertices, pose, endX, bottomY, endZ, red, green, blue, alpha);
		addVertex(vertices, pose, endX, topY, endZ, red, green, blue, alpha);
		addVertex(vertices, pose, startX, topY, startZ, red, green, blue, alpha);

		addVertex(vertices, pose, startX, topY, startZ, red, green, blue, alpha);
		addVertex(vertices, pose, endX, topY, endZ, red, green, blue, alpha);
		addVertex(vertices, pose, endX, bottomY, endZ, red, green, blue, alpha);
		addVertex(vertices, pose, startX, bottomY, startZ, red, green, blue, alpha);
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
		int alpha
	) {
		vertices.addVertex(pose, (float) x, (float) y, (float) z)
			.setColor(red, green, blue, alpha);
	}
}
