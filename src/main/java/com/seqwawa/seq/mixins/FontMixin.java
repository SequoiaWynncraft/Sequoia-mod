package com.seqwawa.seq.mixins;

import com.seqwawa.seq.utils.SeeThroughTextPass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells {@link SeeThroughTextPass} when the text being laid out is the copy of a
 * nametag that shows through terrain, which is the only place a Sequoia decoration
 * would otherwise be drawn at a different opacity than the pass drawn on top of it.
 */
@Mixin(Font.class)
public abstract class FontMixin {

    @Inject(
            method =
                    "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            at = @At("HEAD"))
    private void seq$beginTextPass(
            Component text,
            float x,
            float y,
            int color,
            boolean dropShadow,
            Matrix4f pose,
            MultiBufferSource bufferSource,
            Font.DisplayMode displayMode,
            int backgroundColor,
            int lightCoords,
            CallbackInfo callbackInfo) {
        if (displayMode == Font.DisplayMode.SEE_THROUGH) {
            SeeThroughTextPass.begin();
        }
    }

    @Inject(
            method =
                    "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            at = @At("RETURN"))
    private void seq$endTextPass(
            Component text,
            float x,
            float y,
            int color,
            boolean dropShadow,
            Matrix4f pose,
            MultiBufferSource bufferSource,
            Font.DisplayMode displayMode,
            int backgroundColor,
            int lightCoords,
            CallbackInfo callbackInfo) {
        SeeThroughTextPass.end();
    }
}
