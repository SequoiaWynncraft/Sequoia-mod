package com.seqwawa.seq.managers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import com.seqwawa.seq.model.SeqBadge;
import com.seqwawa.seq.render.SeqAvatarRenderStateExtension;

public final class VanillaSeqBadgeNametagRenderer implements SeqBadgeNametagRendererHandle {
    private static final float NAMETAG_SCALE = 0.025f;
    private static final float BADGE_BASE_Y_OFFSET = 0.35f;
    private static final float SCORE_LINE_Y_OFFSET = 9f * 1.15f * NAMETAG_SCALE;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DISCRETE_COLOR = 0xFF999999;

    private static volatile VanillaSeqBadgeNametagRenderer activeInstance;

    private final RenderPolicy renderPolicy;
    private final boolean allowFirstPersonLocalPlayer;
    private final LeaderboardBadgeService badgeService = LeaderboardBadgeService.getInstance();

    VanillaSeqBadgeNametagRenderer() {
        this((state, localPlayer) -> true, false);
    }

    VanillaSeqBadgeNametagRenderer(
            RenderPolicy renderPolicy,
            boolean allowFirstPersonLocalPlayer) {
        this.renderPolicy = renderPolicy;
        this.allowFirstPersonLocalPlayer = allowFirstPersonLocalPlayer;
        activeInstance = this;
    }

    public static void renderIfActive(
            AvatarRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState) {
        VanillaSeqBadgeNametagRenderer renderer = activeInstance;
        if (renderer != null) {
            renderer.render(state, poseStack, submitNodeCollector, cameraRenderState);
        }
    }

    @Override
    public String status() {
        return "vanilla";
    }

    private void render(
            AvatarRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState) {
        if (!SeqBadgeNametagRenderSupport.showLeaderboardBadges()) {
            return;
        }
        if (!(state instanceof SeqAvatarRenderStateExtension extension)) {
            return;
        }

        boolean localPlayer = extension.seq$isLocalPlayer();
        if (!renderPolicy.shouldRender(state, localPlayer)) {
            return;
        }

        if (localPlayer) {
            if (!SeqBadgeNametagRenderSupport.showOwnLeaderboardBadge()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (!allowFirstPersonLocalPlayer && minecraft.options.getCameraType().isFirstPerson()) {
                return;
            }
        } else if (state.nameTag == null || state.nameTagAttachment == null) {
            return;
        }

        Vec3 attachment = localPlayer ? extension.seq$getNameTagAttachment() : state.nameTagAttachment;
        if (attachment == null) {
            return;
        }

        List<SeqBadge> badges = SeqBadgePlayerResolver.resolve(
                badgeService,
                extension.seq$getPlayerUuid(),
                null,
                localPlayer);
        if (badges.isEmpty()) {
            return;
        }

        float scoreOffset = state.scoreText == null ? 0f : SCORE_LINE_Y_OFFSET;
        drawBadges(
                poseStack,
                submitNodeCollector,
                cameraRenderState,
                state,
                attachment,
                scoreOffset,
                badges);
    }

    private static void drawBadges(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState,
            AvatarRenderState state,
            Vec3 attachment,
            float verticalOffset,
            List<SeqBadge> badges) {
        poseStack.pushPose();
        poseStack.translate(
                attachment.x,
                attachment.y + BADGE_BASE_Y_OFFSET + verticalOffset,
                attachment.z);
        poseStack.mulPose(cameraRenderState.orientation);
        poseStack.scale(NAMETAG_SCALE, -NAMETAG_SCALE, NAMETAG_SCALE);

        float rowWidth =
                SeqBadgeNametagRenderSupport.BADGE_WIDTH * badges.size() + 2f * (badges.size() - 1);
        float badgeXOffset = -rowWidth / 2f + SeqBadgeNametagRenderSupport.BADGE_WIDTH / 2f;
        int color = state.isDiscrete ? DISCRETE_COLOR : WHITE;

        for (SeqBadge badge : badges) {
            submitBadge(
                    poseStack,
                    submitNodeCollector,
                    state,
                    badge,
                    badgeXOffset,
                    SeqBadgeNametagRenderSupport.LOWER_BADGE_Y_OFFSET,
                    color);
            badgeXOffset += SeqBadgeNametagRenderSupport.BADGE_STEP;
        }
        poseStack.popPose();
    }

    private static void submitBadge(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            AvatarRenderState state,
            SeqBadge badge,
            float xOffset,
            float yOffset,
            int color) {
        float halfWidth = SeqBadgeNametagRenderSupport.BADGE_WIDTH / 2f;
        float halfHeight = SeqBadgeNametagRenderSupport.BADGE_HEIGHT / 2f;
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.text(badge.textureId()),
                (pose, vertices) -> drawQuad(
                        pose,
                        vertices,
                        state.lightCoords,
                        xOffset,
                        yOffset,
                        halfWidth,
                        halfHeight,
                        color));
    }

    private static void drawQuad(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            float xOffset,
            float yOffset,
            float halfWidth,
            float halfHeight,
            int color) {
        addVertex(vertices, pose, -halfWidth + xOffset, -halfHeight - yOffset, 0f, 0f, 0f, light, color);
        addVertex(vertices, pose, -halfWidth + xOffset, halfHeight - yOffset, 0f, 0f, 1f, light, color);
        addVertex(vertices, pose, halfWidth + xOffset, halfHeight - yOffset, 0f, 1f, 1f, light, color);
        addVertex(vertices, pose, halfWidth + xOffset, -halfHeight - yOffset, 0f, 1f, 0f, light, color);
    }

    private static void addVertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            int light,
            int color) {
        vertices.addVertex(pose, x, y, z).setUv(u, v).setLight(light).setColor(color);
    }

    interface RenderPolicy {
        boolean shouldRender(AvatarRenderState state, boolean localPlayer);
    }
}
