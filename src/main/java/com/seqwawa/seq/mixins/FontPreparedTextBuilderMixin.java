package com.seqwawa.seq.mixins;

import com.seqwawa.seq.utils.RankGradientAnimation;
import com.seqwawa.seq.utils.NametagTextPass;
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
 * @see NametagTextPass for how a nametag decoration is drawn
 */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public class FontPreparedTextBuilderMixin {

    /**
     * Whether the glyph being coloured belongs to a rank badge, and whether it belongs
     * to any Sequoia decoration at all. Recorded on the way in, because animating a
     * gradient hands back a different colour than the one that was registered. Text is
     * laid out on the render thread, one glyph at a time.
     */
    @Unique
    private boolean seq$badgeGlyph;

    @Unique
    private boolean seq$decorationGlyph;

    @ModifyVariable(
            method = "getTextColor(Lnet/minecraft/network/chat/TextColor;)I",
            at = @At("HEAD"),
            argsOnly = true)
    private TextColor seq$animateRankGradient(TextColor color) {
        // Only a nametag needs any of this, and asking costs a lookup per glyph.
        seq$decorationGlyph =
                NametagTextPass.isDrawingNametags() && RankGradientAnimation.isDecorationColor(color);
        seq$badgeGlyph = seq$decorationGlyph && RankGradientAnimation.isBadgeColor(color);
        return RankGradientAnimation.animate(color);
    }

    /**
     * Draws a rank badge in the copy of the nametag that ignores the depth test, and
     * skips it in the copy that does not, so the glyphs a badge lays on top of one
     * another are never compared by depth. A decorated name overlays nothing, so it is
     * simply kept at full alpha and reads the same through a wall as in front of one.
     *
     * @see NametagTextPass
     */
    @Inject(
            method = "getTextColor(Lnet/minecraft/network/chat/TextColor;)I",
            at = @At("RETURN"),
            cancellable = true)
    private void seq$drawRankDecoration(TextColor color, CallbackInfoReturnable<Integer> callback) {
        boolean badgeGlyph = seq$badgeGlyph;
        boolean decorationGlyph = seq$decorationGlyph;
        seq$badgeGlyph = false;
        seq$decorationGlyph = false;
        if (badgeGlyph) {
            callback.setReturnValue(
                    NametagTextPass.badgeColor(callback.getReturnValue(), NametagTextPass.isSeeThrough()));
        } else if (decorationGlyph) {
            callback.setReturnValue(ARGB.opaque(callback.getReturnValue()));
        }
    }
}
