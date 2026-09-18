package com.seqwawa.seq.mixins;

import com.seqwawa.seq.utils.NametagTextPass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the stretch in which nametags, and only nametags, are drawn. What Sequoia
 * puts on one has to be drawn differently there than in chat.
 *
 * @see NametagTextPass
 */
@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagFeatureRendererMixin {

    @Inject(
            method =
                    "render(Lnet/minecraft/client/renderer/SubmitNodeCollection;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/gui/Font;)V",
            at = @At("HEAD"))
    private void seq$beginNametags(
            SubmitNodeCollection submits,
            MultiBufferSource.BufferSource bufferSource,
            Font font,
            CallbackInfo callbackInfo) {
        NametagTextPass.beginNametags();
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/client/renderer/SubmitNodeCollection;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/gui/Font;)V",
            at = @At("RETURN"))
    private void seq$endNametags(
            SubmitNodeCollection submits,
            MultiBufferSource.BufferSource bufferSource,
            Font font,
            CallbackInfo callbackInfo) {
        NametagTextPass.endNametags();
    }
}
