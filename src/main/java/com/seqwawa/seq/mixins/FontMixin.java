package com.seqwawa.seq.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seqwawa.seq.utils.SeeThroughTextPass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Tells {@link SeeThroughTextPass} when the text being laid out is the copy of a
 * nametag that shows through terrain, which is the only place a Sequoia decoration
 * would otherwise be drawn at a different opacity than the pass drawn on top of it.
 */
@Mixin(Font.class)
public abstract class FontMixin {

    @WrapMethod(
            method =
                    "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V")
    private void seq$drawInTextPass(
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
            Operation<Void> original) {
        boolean seeThrough = displayMode == Font.DisplayMode.SEE_THROUGH;
        if (seeThrough) {
            SeeThroughTextPass.begin();
        }
        try {
            original.call(
                    text,
                    x,
                    y,
                    color,
                    dropShadow,
                    pose,
                    bufferSource,
                    displayMode,
                    backgroundColor,
                    lightCoords);
        } finally {
            if (seeThrough) {
                SeeThroughTextPass.end();
            }
        }
    }
}
