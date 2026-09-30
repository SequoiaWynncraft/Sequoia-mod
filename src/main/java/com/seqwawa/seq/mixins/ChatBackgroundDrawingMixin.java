package com.seqwawa.seq.mixins;

import com.seqwawa.seq.render.BridgeImageRows;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws a bridged picture's slice under the chat line that stands for it while chat is
 * closed. The line's own text, the bridge rail and the click area, is then drawn as
 * usual. See {@link ChatFocusedDrawingMixin} for open chat: the two drawers name their
 * {@code graphics} field differently once remapped, so one mixin cannot shadow both.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
public abstract class ChatBackgroundDrawingMixin {
    @Shadow
    @Final
    private GuiGraphics graphics;

    @Inject(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z", at = @At("HEAD"))
    private void seq$drawBridgeImage(
            int textY, float opacity, FormattedCharSequence text, CallbackInfoReturnable<Boolean> callback) {
        if (text instanceof BridgeImageRows.Row row) {
            BridgeImageRows.draw(graphics, textY, opacity, row);
        }
    }
}
