package com.seqwawa.seq.mixins;

import com.seqwawa.seq.managers.GuildRankNametagDecorator;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Puts a Sequoia member's rank on their in-world nametag.
 * <p>
 * Every nametag reaches the renderer through this one submission, whoever built
 * it: vanilla, Wynntils' custom nametag feature, and mods that rebuild the tag
 * from their own player data. Decorating here rather than on the render state
 * makes the substitution the last word without a mixin into any of them, and
 * leaves whatever else they added to the tag in place.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
    @ModifyVariable(
            method =
                    "submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZIDLnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 4)
    private Component seq$showGuildRankOnNametag(Component nameTag) {
        return GuildRankNametagDecorator.decorate(nameTag);
    }
}
