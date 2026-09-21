package com.seqwawa.seq.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seqwawa.seq.utils.NametagTextPass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Scopes styling even when another renderer cancels or throws during the nametag pass. */
@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagFeatureRendererMixin {
    @WrapMethod(method =
            "render(Lnet/minecraft/client/renderer/SubmitNodeCollection;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/gui/Font;)V")
    private void seq$drawNametags(
            SubmitNodeCollection submits,
            MultiBufferSource.BufferSource bufferSource,
            Font font,
            Operation<Void> original) {
        NametagTextPass.beginNametags();
        try {
            original.call(submits, bufferSource, font);
        } finally {
            NametagTextPass.endNametags();
        }
    }
}
