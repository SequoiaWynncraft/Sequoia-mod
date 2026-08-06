package com.seqwawa.seq.mixins;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import com.seqwawa.seq.ui.SequoiaScreen;
import com.seqwawa.seq.ui.SettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.seqwawa.seq.client.SeqClient.mc;

@Mixin(Gui.class)
public class InGameHudMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void seq$onRenderCrosshair(GuiGraphics context, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (mc.screen instanceof SequoiaScreen || mc.screen instanceof SettingsScreen) {
            ci.cancel();
        }
    }
}
