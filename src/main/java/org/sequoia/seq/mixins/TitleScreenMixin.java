package org.sequoia.seq.mixins;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.sequoia.seq.accessors.EventBusAccessor;
import org.sequoia.seq.events.GameStartEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen implements EventBusAccessor {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    boolean opened = false;

    @Inject(method = "init", at = @At("RETURN"))
    public void TitleScreenInit(CallbackInfo ci) {
        if (!opened) {
            dispatch(new GameStartEvent());
            opened = true;
        }
    }
}
