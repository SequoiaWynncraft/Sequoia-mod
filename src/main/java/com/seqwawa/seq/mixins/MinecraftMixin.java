package com.seqwawa.seq.mixins;

import com.seqwawa.seq.client.SeqClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void seq$onInit(CallbackInfo ci) {
        SeqClient.onMinecraftFinishedLoading();
    }
}
