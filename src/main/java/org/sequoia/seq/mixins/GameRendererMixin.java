package org.sequoia.seq.mixins;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTail(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        NVGContext.flushDeferred();
    }
}
