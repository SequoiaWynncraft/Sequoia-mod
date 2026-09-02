package com.seqwawa.seq.mixins;

import com.seqwawa.seq.managers.GuildRankNametagDecorator;
import com.seqwawa.seq.render.NametagOwner;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Puts a Sequoia member's rank on their in-world nametag.
 * <p>
 * Every nametag reaches the renderer through this one submission, whoever built it:
 * vanilla, Wynntils' custom nametag feature — which cancels the vanilla draw and
 * submits a tag of its own for any Wynntils user — and mods that rebuild the tag
 * from their own player data. Decorating here rather than inside the vanilla draw
 * makes the substitution the last word without a mixin into any of them, and leaves
 * whatever else they added to the tag in place.
 *
 * @see NametagOwner for how the player behind a submitted tag is known
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
        return GuildRankNametagDecorator.decorate(NametagOwner.current(), nameTag);
    }
}
