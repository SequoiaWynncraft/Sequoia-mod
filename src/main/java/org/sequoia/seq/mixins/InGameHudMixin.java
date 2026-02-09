package org.sequoia.seq.mixins;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.sequoia.seq.accessors.EventBusAccessor;
import org.sequoia.seq.events.Render2DEvent;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.sequoia.seq.client.SeqClient.mc;

@Mixin(Gui.class)
public class InGameHudMixin implements EventBusAccessor {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (mc.screen != null) return;
        NVGContext.render(nvg -> dispatch(new Render2DEvent(context, tickCounter.getGameTimeDeltaPartialTick(true))));
    }
}
