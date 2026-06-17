package org.sequoia.seq.halcyon;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ResourcePackModelScanner {
	private static final Pattern MODEL_FIRST_ENTRY_PATTERN = Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\".*?\"threshold\"\\s*:\\s*(\\d+)", Pattern.DOTALL);
	private static final Pattern THRESHOLD_FIRST_ENTRY_PATTERN = Pattern.compile("\"threshold\"\\s*:\\s*(\\d+).*?\"model\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
	private static final Pattern TEXTURE_PATTERN = Pattern.compile("\"0\"\\s*:\\s*\"([^\"]+)\"");
	private static final int MODEL_ANIMATION_STEP = 65536;

	private static final Map<String, Map<Integer, ModelInfo>> MODELS_BY_ITEM = new HashMap<>();
	private static ResourceManager resourceManager;
	private static boolean scanned;

	private ResourcePackModelScanner() {
	}

	public static void reset() {
		MODELS_BY_ITEM.clear();
		resourceManager = null;
		scanned = false;
	}

	public static boolean isScanned() {
		return scanned;
	}

	public static ModelInfo getModelInfo(String itemName, float model) {
		Map<Integer, ModelInfo> itemModels = MODELS_BY_ITEM.get(itemName);
		if (itemModels == null) return null;

		int modelInt = normalizeAnimatedModel(model);
		ModelInfo exact = itemModels.get((int) model);
		if (exact != null) return exact;

		return itemModels.get(modelInt);
	}

	public static int normalizeAnimatedModel(float model) {
		return Math.floorMod((int) model, MODEL_ANIMATION_STEP);
	}

	public static Set<Integer> getModelsWithSameTexture(String itemName, float observedModel) {
		ModelInfo observedInfo = getModelInfo(itemName, observedModel);
		if (observedInfo == null || observedInfo.texture() == null) return Set.of();

		Map<Integer, ModelInfo> itemModels = MODELS_BY_ITEM.get(itemName);
		if (itemModels == null) return Set.of();

		Set<Integer> matchingModels = new TreeSet<>();
		for (Map.Entry<Integer, ModelInfo> entry : itemModels.entrySet()) {
			if (observedInfo.texture().equals(entry.getValue().texture())) {
				matchingModels.add(entry.getKey());
			}
		}

		return matchingModels;
	}

	public static Set<String> getTexturesForItem(String itemName) {
		Map<Integer, ModelInfo> itemModels = MODELS_BY_ITEM.get(itemName);
		if (itemModels == null) return Set.of();

		Set<String> textures = new HashSet<>();
		for (ModelInfo modelInfo : itemModels.values()) {
			if (modelInfo.texture() != null) {
				textures.add(modelInfo.texture());
			}
		}

		return textures;
	}

	public static boolean scan(Minecraft client) {
		if (scanned) return true;

		ResourceManager rm = client.getResourceManager();
		if (rm == null) return false;

		try {
			resourceManager = rm;
			Map<Identifier, Resource> resources = rm.listResources("items", id -> id.getPath().endsWith(".json"));
			int modelCount = 0;

			for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
				if (!entry.getKey().getNamespace().equals("minecraft")) continue;

				String path = entry.getKey().getPath();
				String itemName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));

				try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open()))) {
					String content = reader.lines().collect(Collectors.joining("\n"));
					if (!content.contains("\"threshold\"")) continue;

					modelCount += addModels(itemName, content, MODEL_FIRST_ENTRY_PATTERN, 2, 1);
					modelCount += addModels(itemName, content, THRESHOLD_FIRST_ENTRY_PATTERN, 1, 2);
				}
			}

			scanned = true;
			return true;
		} catch (Exception e) {
			return true;
		}
	}

	private static int addModels(String itemName, String content, Pattern pattern, int thresholdGroup, int modelGroup) {
		Matcher entryMatcher = pattern.matcher(content);
		int count = 0;

		while (entryMatcher.find()) {
			int threshold = Integer.parseInt(entryMatcher.group(thresholdGroup));
			String modelPath = normalizeModelPath(entryMatcher.group(modelGroup));
			String texture = readPrimaryTexture(modelPath);

			MODELS_BY_ITEM
				.computeIfAbsent(itemName, ignored -> new HashMap<>())
				.put(threshold, new ModelInfo(threshold, modelPath, texture));
			count++;
		}

		return count;
	}

	private static String normalizeModelPath(String modelPath) {
		if (modelPath.startsWith("minecraft:")) {
			return modelPath.substring("minecraft:".length());
		}

		return modelPath;
	}

	private static String readPrimaryTexture(String modelPath) {
		if (resourceManager == null) return null;

		Identifier modelId = Identifier.fromNamespaceAndPath("minecraft", "models/" + modelPath + ".json");
		Optional<Resource> resource = resourceManager.getResource(modelId);
		if (resource.isEmpty()) return null;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().open()))) {
			String content = reader.lines().collect(Collectors.joining("\n"));
			Matcher textureMatcher = TEXTURE_PATTERN.matcher(content);
			if (textureMatcher.find()) {
				return textureMatcher.group(1);
			}
		} catch (Exception ignored) {
			return null;
		}

		return null;
	}

	public record ModelInfo(int threshold, String modelPath, String texture) {
	}
}
