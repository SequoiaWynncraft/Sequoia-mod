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
	private static final int BAND_ALPHA = 215;
	private static final double BAND_WIDTH = 0.22;
	private static final double[] Y_OFFSETS = {0.10, 0.18};
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

		for (double yOffset : Y_OFFSETS) {
			renderRingBand(vertices, pose, center, camera, radius, yOffset);
		}
	}

	private static void renderRingBand(VertexConsumer vertices, PoseStack.Pose pose, Vec3 center, Vec3 camera, double radius, double yOffset) {
		double halfWidth = BAND_WIDTH / 2.0;
		double innerRadius = Math.max(0.0, radius - halfWidth);
		double outerRadius = radius + halfWidth;
		double y = center.y + yOffset - camera.y;

		for (int i = 0; i < SEGMENTS; i++) {
			renderBandSegment(vertices, pose, center, camera, y, i, innerRadius, outerRadius);
		}
	}

	private static void renderBandSegment(
		VertexConsumer vertices,
		PoseStack.Pose pose,
		Vec3 center,
		Vec3 camera,
		double y,
		int segment,
		double innerRadius,
		double outerRadius
	) {
		double startCos = COS[segment];
		double startSin = SIN[segment];
		double endCos = COS[segment + 1];
		double endSin = SIN[segment + 1];
		double innerStartX = center.x + startCos * innerRadius - camera.x;
		double innerStartZ = center.z + startSin * innerRadius - camera.z;
		double innerEndX = center.x + endCos * innerRadius - camera.x;
		double innerEndZ = center.z + endSin * innerRadius - camera.z;
		double outerStartX = center.x + startCos * outerRadius - camera.x;
		double outerStartZ = center.z + startSin * outerRadius - camera.z;
		double outerEndX = center.x + endCos * outerRadius - camera.x;
		double outerEndZ = center.z + endSin * outerRadius - camera.z;

		addVertex(vertices, pose, innerStartX, y, innerStartZ);
		addVertex(vertices, pose, innerEndX, y, innerEndZ);
		addVertex(vertices, pose, outerEndX, y, outerEndZ);
		addVertex(vertices, pose, outerStartX, y, outerStartZ);

		addVertex(vertices, pose, outerStartX, y, outerStartZ);
		addVertex(vertices, pose, outerEndX, y, outerEndZ);
		addVertex(vertices, pose, innerEndX, y, innerEndZ);
		addVertex(vertices, pose, innerStartX, y, innerStartZ);
	}

	private static void addVertex(VertexConsumer vertices, PoseStack.Pose pose, double x, double y, double z) {
		vertices.addVertex(pose, (float) x, (float) y, (float) z)
			.setColor(COLOR_RED, COLOR_GREEN, COLOR_BLUE, BAND_ALPHA);
	}
}
