package com.seqwawa.seq.halcyon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.WynncraftServerPolicy;

public final class HalcyonRangeVisualiserClient {
	private HalcyonRangeVisualiserClient() {
	}

	public static void initialize() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES)
			.registerReloader(
				Identifier.fromNamespaceAndPath("seq", "halcyon_pack_scanner"),
				new ResourceManagerReloadListener() {
					@Override
					public void onResourceManagerReload(ResourceManager manager) {
						ResourcePackModelScanner.reset();
						HalcyonTextureDetector.reset();
					}
				}
			);

		ClientTickEvents.END_CLIENT_TICK.register(HalcyonRangeVisualiserClient::tick);
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
			if (isEnabled()) {
				HalcyonRingRenderer.render(context);
			}
		});
	}

	private static void reset() {
		HalcyonTextureDetector.reset();
	}

	private static void tick(Minecraft client) {
		if (!isEnabled()) {
			reset();
			return;
		}

		HalcyonTextureDetector.tick(client);
	}

	private static boolean isEnabled() {
		return WynncraftServerPolicy.isCurrentServerAllowed()
			&& (SeqClient.getHalcyonRangeVisualiserSetting() == null || SeqClient.getHalcyonRangeVisualiserSetting().getValue());
	}
}
