package com.seqwawa.seq.mixins;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.managers.DiscordRankChatDecorator;
import com.seqwawa.seq.utils.ChatBridgeLineWrapping;
import com.seqwawa.seq.utils.ChatDisplayLayout;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rewrites the Wynncraft guild rank badge into the sender's Sequoia Discord rank
 * just before the line is queued for display.
 * <p>
 * This is the last hop every chat line takes, since {@code addMessage(Component)}
 * delegates here, so it also covers messages Wynntils has already reformatted
 * (timestamps, nickname rendering), unlike the packet-level hook used by
 * {@link com.seqwawa.seq.managers.ChatManager}.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Shadow
    private int getLineHeight() {
        throw new AssertionError();
    }

    @ModifyVariable(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1)
    private Component seq$applyDiscordRank(Component message) {
        return DiscordRankChatDecorator.decorateGuildChat(message);
    }

    /**
     * Minecraft creates visual chat lines only after the component enters its display
     * queue. Reserve room for the bridge rail at that point, then put the rail on every
     * automatically wrapped line after the sender line.
     */
    @Redirect(
            method = "addMessageToDisplayQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
    private List<FormattedCharSequence> seq$wrapBridgeContinuations(
            GuiMessage message, Font font, int maxWidth) {
        List<FormattedCharSequence> initialLines = message.splitLines(font, maxWidth);
        Component continuationPrefix = DiscordRankChatDecorator.bridgeContinuationPrefixFor(message.content());
        if (continuationPrefix == null) {
            return initialLines;
        }

        return ChatBridgeLineWrapping.wrapColoredBridgeMessage(
                initialLines, message, font, maxWidth, continuationPrefix);
    }

    /**
     * Makes the player's requested visual-line count the chat height itself. Minecraft
     * derives pagination, scroll bounds and pointer hit-testing from this height, while
     * rendering uses the same vanilla line height, so every subsystem agrees.
     */
    @Inject(method = "getHeight()I", at = @At("RETURN"), cancellable = true)
    private void seq$setVisibleLineCount(CallbackInfoReturnable<Integer> callback) {
        Setting.IntSetting visibleLines = SeqClient.getVisibleChatLinesSetting();
        if (visibleLines == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        double chatScale = Math.max(0.01d, minecraft.options.chatScale().get());
        int availableHeight = Mth.floor((minecraft.getWindow().getGuiScaledHeight() - 40) / chatScale);
        callback.setReturnValue(ChatDisplayLayout.heightForVisibleLines(
                visibleLines.getValue(), getLineHeight(), availableHeight));
    }
}
