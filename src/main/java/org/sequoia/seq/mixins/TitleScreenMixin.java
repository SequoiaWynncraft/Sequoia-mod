package org.sequoia.seq.mixins;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.sequoia.seq.accessors.EventBusAccessor;
import org.sequoia.seq.events.GameStartEvent;
import org.sequoia.seq.ui.StartupVideoOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen implements EventBusAccessor {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private boolean seq$opened = false;

    @Inject(method = "init", at = @At("RETURN"))
    private void seq$titleScreenInit(CallbackInfo ci) {
        if (!seq$opened) {
            seqdispatch(new GameStartEvent());
            seq$opened = true;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void seq$renderStartupVideo(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        StartupVideoOverlay.render(graphics, mouseX, mouseY);
    }
}
