package org.sequoia.seq.radiance;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.WynncraftServerPolicy;

public final class RadianceCheckerClient {
    private RadianceCheckerClient() {}

    public static void initialize() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("seq", "radiance_pack_scanner");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        ResourcePackModelScanner.reset();
                    }
                });

        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            if (isEnabled()) {
                PingRenderer.renderOverlay(guiGraphics, deltaTracker);
            }
        });
    }

    public static void tick(Minecraft client) {
        if (!isEnabled()) {
            reset();
            return;
        }

        EntityDebugTracker.tick(client);
        PingManager.tick(client);
        RadianceInvestigationProbe.tick(client);
    }

    public static boolean isEnabled() {
        return WynncraftServerPolicy.isCurrentServerAllowed()
                && (SeqClient.getRadianceCheckerSetting() == null || SeqClient.getRadianceCheckerSetting().getValue());
    }

    public static void reset() {
        EntityDebugTracker.reset();
        PingManager.clear();
        RadianceInvestigationProbe.clear();
    }
}
