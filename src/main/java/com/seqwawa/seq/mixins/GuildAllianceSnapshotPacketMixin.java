package com.seqwawa.seq.mixins;

import com.seqwawa.seq.managers.GuildAllianceSnapshotManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 1100)
public class GuildAllianceSnapshotPacketMixin {
    @Inject(method = "handleOpenScreen", at = @At("HEAD"))
    private void seq$onHandleOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        GuildAllianceSnapshotManager.getInstance()
                .onMenuOpened(packet.getContainerId(), packet.getTitle().getString());
    }

    @Inject(method = "handleContainerContent", at = @At("HEAD"))
    private void seq$onHandleContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        GuildAllianceSnapshotManager.getInstance()
                .onContainerContents(packet.containerId(), packet.items());
    }

    @Inject(method = "handleContainerClose", at = @At("HEAD"))
    private void seq$onHandleContainerClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        GuildAllianceSnapshotManager.getInstance().onMenuClosed(packet.getContainerId());
    }
}
