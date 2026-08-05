package com.seqwawa.seq.mixins;

import com.seqwawa.seq.utils.RankGradientAnimation;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Moves a gradient rank's colours along its ramp as its chat pill or name is drawn.
 * <p>
 * A chat line is laid out once, into glyph sequences Minecraft keeps until the chat is
 * rescaled, so nothing that animates can live in the component itself. This is the one
 * place a style's colour becomes the colour actually drawn, and it is reached afresh on
 * every frame, which makes it the only hook where a still component can be given a
 * moving colour.
 *
 * @see RankGradientAnimation which recognises registered rank-decoration colours and
 *      leaves all others untouched
 */
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
