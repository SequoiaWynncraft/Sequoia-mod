package org.sequoia.seq.halcyon;

import net.minecraft.client.Minecraft;
import org.sequoia.seq.utils.ResourcePackItemModelScanner;

import java.util.Set;

public final class ResourcePackModelScanner {
	private static final ResourcePackItemModelScanner SCANNER = new ResourcePackItemModelScanner();

	private ResourcePackModelScanner() {
	}

	public static void reset() {
		SCANNER.reset();
	}

	public static boolean isScanned() {
		return SCANNER.isScanned();
	}

	public static ModelInfo getModelInfo(String itemName, float model) {
		ResourcePackItemModelScanner.ModelInfo modelInfo = SCANNER.getModelInfo(itemName, model);
		if (modelInfo == null) return null;
		return new ModelInfo(modelInfo.threshold(), modelInfo.modelPath(), modelInfo.texture());
	}

	public static int normalizeAnimatedModel(float model) {
		return ResourcePackItemModelScanner.normalizeAnimatedModel(model);
	}

	public static Set<Integer> getModelsWithSameTexture(String itemName, float observedModel) {
		return SCANNER.getModelsWithSameTexture(itemName, observedModel);
	}

	public static boolean scan(Minecraft client) {
		return SCANNER.scan(client);
	}

	public record ModelInfo(int threshold, String modelPath, String texture) {
	}
}
