package org.sequoia.seq.mixins;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.sequoia.seq.managers.ChatManager;
import org.sequoia.seq.managers.RaidTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts system chat packets at the earliest possible point, before
 * Wynntils (or Fabric's message API) can cancel/reformat them.
 * <p>
 * This ensures guild messages and raid completions — including multiline ones
 * that Wynntils rewrites — are always observed by {@link ChatManager} and
 * {@link RaidTracker}.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void onHandleSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (packet.overlay()) return;

        Component content = packet.content();
        ChatManager.onSystemChat(content);
        RaidTracker.onSystemChat(content);
    }
}
