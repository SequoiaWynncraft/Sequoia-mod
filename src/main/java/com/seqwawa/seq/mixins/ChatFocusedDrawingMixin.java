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
 * open, where the click area over it also turns the cursor into a hand and shows its
 * hover text. See {@link ChatBackgroundDrawingMixin} for closed chat.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public abstract class ChatFocusedDrawingMixin {
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
