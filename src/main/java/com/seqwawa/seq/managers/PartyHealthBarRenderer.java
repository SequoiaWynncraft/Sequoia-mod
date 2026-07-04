package com.seqwawa.seq.managers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wynntils.mc.event.PlayerNametagRenderEvent;
import com.wynntils.mc.extension.EntityRenderStateExtension;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.render.SeqAvatarRenderStateExtension;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class PartyHealthBarRenderer {
    private static final float NAMETAG_SCALE = 0.025f;
    private static final float BAR_WIDTH = 40f;
    private static final float BAR_HEIGHT = 5f;
    private static final float BAR_BORDER = 1f;
    private static final int NAME_TAG_LINE_HEIGHT = 10;
    private static final int BAR_SPACING = 8;
    private static final int BADGE_BAR_SPACING = 10;
    private static final int BAR_RAISE_Y_OFFSET = 7;
    private static final int EXTRA_EARS_Y_OFFSET = 10;
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private static final int BORDER_COLOR = 0xFF050505;
    private static final int BACKGROUND_COLOR = 0xFFB3261E;
    private static final int FILL_COLOR = 0xFF35F06A;
    private static final int OVER_MAX_FILL_COLOR = 0xFFFFD84A;
    private static final float BAR_BORDER_Z = 0.01f;
    private static final float BAR_CONTENT_Z = 0.001f;
    private PartyHealthBarRenderer() {}

    public static void renderIfVisible(
            AvatarRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState) {
        if (!(state instanceof SeqAvatarRenderStateExtension extension)) {
            return;
        }
        boolean localPlayer = extension.seq$isLocalPlayer();
        if (localPlayer) {
            return;
        }
        if (shouldSuppressVanillaHealthBar(state, localPlayer)) {
            return;
        }

        Vec3 attachment = localPlayer ? extension.seq$getNameTagAttachment() : state.nameTagAttachment;
        if (attachment == null) {
            attachment = extension.seq$getNameTagAttachment();
        }

        boolean badgeLaneOccupied = hasSeqBadge(extension.seq$getPlayerUuid(), localPlayer);

        renderAtAttachment(
                poseStack,
                submitNodeCollector,
                cameraRenderState,
                state,
                attachment,
                extension.seq$getPlayerUuid(),
                0f,
                healthBarYOffset(badgeLaneOccupied, SeqBadgeNametagRenderSupport.WYNNTILS_DEFAULT_BADGE_Y_OFFSET));
    }

    public static void renderWynntils(
            PlayerNametagRenderEvent event,
            float nametagVerticalOffset,
            boolean badgeLaneOccupied,
            boolean seqOnlyLowerPlacement) {
        if (event == null || !(event.getEntityRenderState() instanceof AvatarRenderState avatarState)) {
            return;
        }

        Entity entity = ((EntityRenderStateExtension) event.getEntityRenderState()).getEntity();
        AbstractClientPlayer player = entity instanceof AbstractClientPlayer abstractClientPlayer
                ? abstractClientPlayer
                : null;
        UUID playerUuid = player == null ? null : player.getUUID();
        if (isLocalPlayer(player)) {
            return;
        }
        float badgeYOffset = nametagVerticalOffset == 0f && !seqOnlyLowerPlacement
                ? SeqBadgeNametagRenderSupport.WYNNTILS_DEFAULT_BADGE_Y_OFFSET
                : SeqBadgeNametagRenderSupport.LOWER_BADGE_Y_OFFSET;
        boolean occupiedBadgeLane = badgeLaneOccupied || hasSeqBadge(playerUuid, isLocalPlayer(player));
        renderAtAttachment(
                event.getPoseStack(),
                event.getSubmitNodeCollector(),
                event.getCameraRenderState(),
                avatarState,
                avatarState.nameTagAttachment,
                playerUuid,
                nametagVerticalOffset,
                healthBarYOffset(occupiedBadgeLane, badgeYOffset));
    }

    private static void renderAtAttachment(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState,
            AvatarRenderState state,
            Vec3 attachment,
            UUID playerUuid,
            float extraWorldYOffset,
            float extraPixelYOffset) {
        if (!showPartyHealthBars()) {
            return;
        }

        PartyHealthCache.HealthBarState health = PartyHealthCache.healthBarState(playerUuid);
        if (health == null || attachment == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(attachment.x, attachment.y + extraWorldYOffset + worldYOffset(state, extraPixelYOffset), attachment.z);
        poseStack.mulPose(cameraRenderState.orientation);
        poseStack.scale(NAMETAG_SCALE, -NAMETAG_SCALE, NAMETAG_SCALE);

        submitBar(submitNodeCollector, poseStack, state, 0f, health.percent(), health.overMax());
        poseStack.popPose();
    }

    static float healthBarYOffset(boolean badgeLaneOccupied, float badgeYOffset) {
        if (!badgeLaneOccupied) {
            return 0f;
        }
        return badgeYOffset + BADGE_BAR_SPACING;
    }

    private static float worldYOffset(AvatarRenderState state, float extraPixelYOffset) {
        int yOffset = state.showExtraEars ? EXTRA_EARS_Y_OFFSET : 0;
        int occupiedLines = 0;
        if (state.scoreText != null) {
            occupiedLines++;
        }
        if (state.nameTag != null) {
            occupiedLines++;
        }

        float pixelOffset = yOffset
                + (Math.max(1, occupiedLines) * NAME_TAG_LINE_HEIGHT)
                + BAR_SPACING
                + BAR_RAISE_Y_OFFSET
                + extraPixelYOffset;
        return pixelOffset * NAMETAG_SCALE;
    }

    private static void submitBar(
            SubmitNodeCollector submitNodeCollector,
            PoseStack poseStack,
            AvatarRenderState state,
            float xOffset,
            float percent,
            boolean overMax) {
        final float clampedPercent = Math.max(0f, Math.min(1f, percent));
        float x = xOffset - (BAR_WIDTH / 2f);
        float y = 0f;
        float fillWidth = healthBarFillWidth(clampedPercent);
        int fillColor = overMax ? OVER_MAX_FILL_COLOR : FILL_COLOR;
        int light = FULL_BRIGHT_LIGHT;
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.textBackground(),
                (pose, vertices) -> {
                    drawQuad(
                            vertices,
                            pose,
                            light,
                            x - BAR_BORDER,
                            y - BAR_BORDER,
                            BAR_WIDTH + (BAR_BORDER * 2f),
                            BAR_BORDER,
                            BAR_BORDER_Z,
                            BORDER_COLOR);
                    drawQuad(
                            vertices,
                            pose,
                            light,
                            x - BAR_BORDER,
                            y + BAR_HEIGHT,
                            BAR_WIDTH + (BAR_BORDER * 2f),
                            BAR_BORDER,
                            BAR_BORDER_Z,
                            BORDER_COLOR);
                    drawQuad(
                            vertices,
                            pose,
                            light,
                            x - BAR_BORDER,
                            y,
                            BAR_BORDER,
                            BAR_HEIGHT,
                            BAR_BORDER_Z,
                            BORDER_COLOR);
                    drawQuad(
                            vertices,
                            pose,
                            light,
                            x + BAR_WIDTH,
                            y,
                            BAR_BORDER,
                            BAR_HEIGHT,
                            BAR_BORDER_Z,
                            BORDER_COLOR);
                    if (fillWidth < BAR_WIDTH) {
                        drawQuad(
                                vertices,
                                pose,
                                light,
                                x + fillWidth,
                                y,
                                BAR_WIDTH - fillWidth,
                                BAR_HEIGHT,
                                BAR_CONTENT_Z,
                                BACKGROUND_COLOR);
                    }
                    if (fillWidth > 0f) {
                        drawQuad(
                                vertices,
                                pose,
                                light,
                                x,
                                y,
                                 fillWidth,
                                 BAR_HEIGHT,
                                 BAR_CONTENT_Z,
                                fillColor);
                    }
                });
    }

    static float healthBarFillWidth(float percent) {
        return Math.round(BAR_WIDTH * Math.max(0f, Math.min(1f, percent)));
    }

    private static void drawQuad(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            int light,
            float x,
            float y,
            float width,
            float height,
            float z,
            int color) {
        vertices.addVertex(pose, x, y, z).setLight(light).setColor(color);
        vertices.addVertex(pose, x, y + height, z).setLight(light).setColor(color);
        vertices.addVertex(pose, x + width, y + height, z).setLight(light).setColor(color);
        vertices.addVertex(pose, x + width, y, z).setLight(light).setColor(color);
    }

    static boolean showPartyHealthBars() {
        return SeqClient.getShowPartyHealthBarsSetting() == null
                || SeqClient.getShowPartyHealthBarsSetting().getValue();
    }

    private static boolean shouldSuppressVanillaHealthBar(AvatarRenderState state, boolean localPlayer) {
        return SeqClient.getSeqBadgeNametagRenderer() != null
                && SeqClient.getSeqBadgeNametagRenderer().shouldSuppressVanillaHealthBar(state, localPlayer);
    }

    private static boolean hasSeqBadge(UUID playerUuid, boolean localPlayer) {
        if (!SeqBadgeNametagRenderSupport.showLeaderboardBadges()) {
            return false;
        }
        if (localPlayer && !SeqBadgeNametagRenderSupport.showOwnLeaderboardBadge()) {
            return false;
        }
        return !SeqBadgePlayerResolver.resolve(LeaderboardBadgeService.getInstance(), playerUuid, null, localPlayer).isEmpty();
    }

    private static boolean isLocalPlayer(AbstractClientPlayer player) {
        return player != null
                && SeqClient.mc != null
                && SeqClient.mc.player != null
                && player.getUUID() != null
                && player.getUUID().equals(SeqClient.mc.player.getUUID());
    }
}
