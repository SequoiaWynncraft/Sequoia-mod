package com.seqwawa.seq.mixins;

import com.seqwawa.seq.managers.GuildRankNametagDecorator;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lays nametag pills out again once new fonts, and so new glyph widths, are in place. */
@Mixin(FontManager.class)
public abstract class FontManagerMixin {
    @Inject(
            method = "apply(Lnet/minecraft/client/gui/font/FontManager$Preparation;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"))
    private void seq$forgetNametagLayouts(CallbackInfo ci) {
        GuildRankNametagDecorator.forgetDecorations();
    }
}
