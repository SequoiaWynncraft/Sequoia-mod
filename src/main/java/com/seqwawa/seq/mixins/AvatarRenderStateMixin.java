package com.seqwawa.seq.mixins;

import java.util.UUID;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import com.seqwawa.seq.render.SeqAvatarRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements SeqAvatarRenderStateExtension {
    @Unique
    private UUID seq$playerUuid;

    @Unique
    private boolean seq$localPlayer;

    @Unique
    private Vec3 seq$nameTagAttachment;

    @Override
    public UUID seq$getPlayerUuid() {
        return seq$playerUuid;
    }

    @Override
    public void seq$setPlayerUuid(UUID playerUuid) {
        seq$playerUuid = playerUuid;
    }

    @Override
    public boolean seq$isLocalPlayer() {
        return seq$localPlayer;
    }

    @Override
    public void seq$setLocalPlayer(boolean localPlayer) {
        seq$localPlayer = localPlayer;
    }

    @Override
    public Vec3 seq$getNameTagAttachment() {
        return seq$nameTagAttachment;
    }

    @Override
    public void seq$setNameTagAttachment(Vec3 nameTagAttachment) {
        seq$nameTagAttachment = nameTagAttachment;
    }
}
