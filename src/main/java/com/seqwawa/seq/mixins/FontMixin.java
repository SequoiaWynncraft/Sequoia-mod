package com.seqwawa.seq.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seqwawa.seq.render.NametagGlyphVisitor;
import com.seqwawa.seq.utils.NametagTextPass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps layered nametag pills depth-tested, with their letters in front of the fill. */
@Mixin(Font.class)
public abstract class FontMixin {
    @WrapOperation(
            method =
                    "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font$GlyphVisitor;forMultiBufferSource(Lnet/minecraft/client/renderer/MultiBufferSource;Lorg/joml/Matrix4f;Lnet/minecraft/client/gui/Font$DisplayMode;I)Lnet/minecraft/client/gui/Font$GlyphVisitor;"))
    private Font.GlyphVisitor seq$layerNametagPill(
            MultiBufferSource buffers,
            Matrix4f pose,
            Font.DisplayMode displayMode,
            int lightCoords,
            Operation<Font.GlyphVisitor> original) {
        Font.GlyphVisitor ordinary = original.call(buffers, pose, displayMode, lightCoords);
        if (!NametagTextPass.isStylingNametags()) {
            return ordinary;
        }
        Font.GlyphVisitor foreground = original.call(
                buffers, NametagGlyphVisitor.foregroundPose(pose), displayMode, lightCoords);
        return new NametagGlyphVisitor(ordinary, foreground);
    }
}
