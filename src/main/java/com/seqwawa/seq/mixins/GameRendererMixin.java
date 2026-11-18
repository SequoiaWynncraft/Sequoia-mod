package com.seqwawa.seq.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.seqwawa.seq.render.MinecraftGuiOverlay;
import com.seqwawa.seq.ui.unlock.UnlockPreview;
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
        MinecraftGuiOverlay overlay = minecraft.screen instanceof MinecraftGuiOverlay current ? current : null;
        if (overlay == null && !com.seqwawa.seq.ui.CheapestQueueEffect.isActive()) {
            return;
        }

        guiRenderState.reset();
        try {
            GuiGraphics guiGraphics = new GuiGraphics(
                    minecraft,
                    guiRenderState,
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight());
            if (overlay != null) {
                overlay.renderMinecraftGuiOverlay(guiGraphics, MinecraftUiRenderer.metrics());
            }
            com.seqwawa.seq.ui.CheapestQueueEffect.render(guiGraphics);
            guiGraphics.renderDeferredElements();
            guiRenderer.render(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
        } finally {
            guiRenderState.reset();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void seq$afterFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (UnlockPreview.isRunning()) UnlockPreview.afterFrame(minecraft);
    }

    /** The development preview keeps playing while its window is in the background. */
    @ModifyExpressionValue(
            method = "render",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;pauseOnLostFocus:Z"))
    private boolean seq$keepPreviewRunning(boolean pauseOnLostFocus) {
        return pauseOnLostFocus && !UnlockPreview.isRunning();
    }
}
