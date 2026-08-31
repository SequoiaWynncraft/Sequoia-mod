package com.seqwawa.seq.mixins;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererFogAccessor {
    @Accessor("fogRenderer")
    FogRenderer seq$getFogRenderer();
}
