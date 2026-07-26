package com.seqwawa.seq.mixins;

import com.seqwawa.seq.render.MinecraftGuiOverlay;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private GuiRenderer guiRenderer;

    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    @Shadow
    @Final
    private FogRenderer fogRenderer;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;incrementFrameNumber()V",
                    shift = At.Shift.AFTER))
    private void seq$renderUiLayers(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        MinecraftUiRenderer.flush();
        if (!(minecraft.screen instanceof MinecraftGuiOverlay overlay)) {
            return;
        }

        guiRenderState.reset();
        try {
            GuiGraphics guiGraphics = new GuiGraphics(minecraft, guiRenderState, 0, 0);
            overlay.renderMinecraftGuiOverlay(guiGraphics, MinecraftUiRenderer.metrics());
            guiGraphics.renderDeferredElements();
            guiRenderer.render(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
        } finally {
            guiRenderState.reset();
        }
    }
}
