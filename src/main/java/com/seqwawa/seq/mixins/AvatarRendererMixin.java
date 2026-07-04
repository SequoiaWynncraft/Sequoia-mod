package com.seqwawa.seq.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import com.seqwawa.seq.managers.PartyHealthBarRenderer;
import com.seqwawa.seq.managers.VanillaSeqBadgeNametagRenderer;
import com.seqwawa.seq.render.SeqAvatarRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(
            method =
                    "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void seq$capturePlayerIdentity(
            Avatar player, AvatarRenderState state, float partialTick, CallbackInfo callbackInfo) {
        SeqAvatarRenderStateExtension extension = (SeqAvatarRenderStateExtension) state;
        Minecraft minecraft = Minecraft.getInstance();

        extension.seq$setPlayerUuid(player.getUUID());
        extension.seq$setLocalPlayer(minecraft.player != null
                && (player == minecraft.player
                        || Objects.equals(player.getUUID(), minecraft.player.getUUID())));

        Vec3 attachment = player.getAttachments()
                .getNullable(EntityAttachment.NAME_TAG, 0, player.getYRot(partialTick));
        extension.seq$setNameTagAttachment(attachment);
    }

    @Inject(
            method =
                    "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("RETURN"))
    private void seq$renderLeaderboardBadges(
            AvatarRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState,
            CallbackInfo callbackInfo) {
        PartyHealthBarRenderer.renderIfVisible(
                state, poseStack, submitNodeCollector, cameraRenderState);
        VanillaSeqBadgeNametagRenderer.renderIfActive(
                state, poseStack, submitNodeCollector, cameraRenderState);
    }
}
