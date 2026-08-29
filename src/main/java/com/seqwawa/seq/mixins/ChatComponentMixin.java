package com.seqwawa.seq.mixins;

import com.seqwawa.seq.managers.DiscordRankChatDecorator;
import com.seqwawa.seq.managers.SeqPointsChatAliasDecorator;
import com.seqwawa.seq.managers.WorldSwitchChatDecorator;
import com.seqwawa.seq.utils.ChatBridgeLineWrapping;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Applies Sequoia's rank, temporary-alias and world-link presentation just before a
 * chat line is queued for display. Active aliases replace recognised player
 * identities, guild chat can then colour the replacement and replace its rank badge,
 * and supported world names become clickable after that decoration is complete.
 * <p>
 * This is the last hop every chat line takes, since {@code addMessage(Component)}
 * delegates here, so it also covers messages Wynntils has already reformatted
 * (timestamps, nickname rendering), unlike the packet-level hook used by
 * {@link com.seqwawa.seq.managers.ChatManager}.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    /**
     * Aliases run first so rank decoration paints the replacement across the same
     * username gradient. Linking runs last so worlds in the message body still work.
     * Alias text carries a player insertion, preventing a world-shaped alias such as
     * "EU7" from becoming a switch link.
     */
    @ModifyVariable(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1)
    private Component seq$decorateChatLine(Component message) {
        Component aliased = SeqPointsChatAliasDecorator.decorate(message);
        Component decorated = DiscordRankChatDecorator.decorateGuildChat(aliased);
        Component linked = WorldSwitchChatDecorator.decorate(decorated);
        DiscordRankChatDecorator.retainBridgeRail(decorated, linked);
        return linked;
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
}
