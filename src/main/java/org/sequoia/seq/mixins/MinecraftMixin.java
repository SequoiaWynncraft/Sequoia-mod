package org.sequoia.seq.mixins;

import org.sequoia.seq.accessors.EventBusAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.events.MinecraftFinishedLoading;
import org.sequoia.seq.managers.AssetManager;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.Minecraft.class)
public abstract class MinecraftMixin implements EventBusAccessor {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        dispatch(new MinecraftFinishedLoading());
    }
}
