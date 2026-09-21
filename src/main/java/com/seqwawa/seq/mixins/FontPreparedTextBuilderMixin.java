package com.seqwawa.seq.mixins;

import com.seqwawa.seq.utils.RankGradientAnimation;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Animates rank colors while retaining the alpha chosen by Minecraft's text pass. */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public class FontPreparedTextBuilderMixin {
    @ModifyVariable(
            method = "getTextColor(Lnet/minecraft/network/chat/TextColor;)I",
            at = @At("HEAD"),
            argsOnly = true)
    private TextColor seq$animateRankGradient(TextColor color) {
        return RankGradientAnimation.animate(color);
    }
}
