package com.seqwawa.seq.mixins;

import com.seqwawa.seq.utils.RankGradientAnimation;
import com.seqwawa.seq.utils.SeeThroughTextPass;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moves a gradient rank's colours along its ramp as its chat pill or name is drawn,
 * and keeps a rank decoration at one opacity wherever it is drawn.
 * <p>
 * A chat line is laid out once, into glyph sequences Minecraft keeps until the chat is
 * rescaled, so nothing that animates can live in the component itself. This is the one
 * place a style's colour becomes the colour actually drawn, and it is reached afresh on
 * every frame, which makes it the only hook where a still component can be given a
 * moving colour.
 *
 * @see RankGradientAnimation which recognises registered rank-decoration colours and
 *      leaves all others untouched
 * @see SeeThroughTextPass for why a nametag decoration must ignore the pass's alpha
 */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public class FontPreparedTextBuilderMixin {

    /**
     * Whether the glyph being coloured is a rank decoration. Recorded on the way in,
     * because animating a gradient hands back a different colour than the one that was
     * registered. Text is laid out on the render thread, one glyph at a time.
     */
    @Unique
    private static boolean seq$decorationGlyph;

    @ModifyVariable(
            method = "getTextColor(Lnet/minecraft/network/chat/TextColor;)I",
            at = @At("HEAD"),
            argsOnly = true)
    private TextColor seq$animateRankGradient(TextColor color) {
        // Only the see-through pass needs to know, and asking costs a lookup per glyph.
        seq$decorationGlyph =
                SeeThroughTextPass.isActive() && RankGradientAnimation.isDecorationColor(color);
        return RankGradientAnimation.animate(color);
    }

    /**
     * Draws Sequoia's decorations at full alpha in the see-through pass as well, so a
     * pill looks the same whether the depth test keeps the copy drawn over it or not.
     * Everything else keeps the faded colour that marks a nametag seen through terrain.
     */
    @Inject(
            method = "getTextColor(Lnet/minecraft/network/chat/TextColor;)I",
            at = @At("RETURN"),
            cancellable = true)
    private void seq$keepRankDecorationOpaque(TextColor color, CallbackInfoReturnable<Integer> callback) {
        if (seq$decorationGlyph) {
            callback.setReturnValue(ARGB.opaque(callback.getReturnValue()));
        }
    }
}
