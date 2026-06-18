package org.sequoia.seq.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ResourcePackItemModelScanner {
	private static final Pattern TEXTURE_PATTERN = Pattern.compile("\"0\"\\s*:\\s*\"([^\"]+)\"");
	private static final int MODEL_ANIMATION_STEP = 65536;

	private final Set<String> includedItems;
	private final boolean supportThresholdFirstEntries;
	private final Map<String, Map<Integer, ModelInfo>> modelsByItem = new HashMap<>();
	private final Map<ModelKey, String> modelContent = new HashMap<>();
	private ResourceManager resourceManager;
	private boolean scanned;

	public ResourcePackItemModelScanner() {
		this(Set.of(), true);
	}

	public ResourcePackItemModelScanner(Set<String> includedItems, boolean supportThresholdFirstEntries) {
		this.includedItems = Set.copyOf(includedItems);
		this.supportThresholdFirstEntries = supportThresholdFirstEntries;
	}

	public void reset() {
		modelsByItem.clear();
		modelContent.clear();
		resourceManager = null;
		scanned = false;
	}

	public boolean isScanned() {
		return scanned;
	}

	public boolean scan(Minecraft client) {
		if (client == null) return false;
		return scan(client.getResourceManager());
	}

	public boolean scan(ResourceManager rm) {
		if (scanned) return true;
		if (rm == null) return false;

		try {
			resourceManager = rm;
			Map<Identifier, Resource> resources = rm.listResources("items", id -> id.getPath().endsWith(".json"));

			for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
				if (!entry.getKey().getNamespace().equals("minecraft")) continue;

				String path = entry.getKey().getPath();
				String itemName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
				if (!includedItems.isEmpty() && !includedItems.contains(itemName)) continue;

				try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open()))) {
					String content = reader.lines().collect(Collectors.joining("\n"));
					if (!content.contains("\"threshold\"")) continue;

					addModels(itemName, JsonParser.parseString(content));
				}
			}

			scanned = true;
			return true;
		} catch (Exception ignored) {
			scanned = true;
			return true;
		}
	}

	public Set<Integer> getThresholds(String itemName) {
		Map<Integer, ModelInfo> itemModels = modelsByItem.get(itemName);
		if (itemModels == null) return Set.of();
		return new TreeSet<>(itemModels.keySet());
	}

	public ModelInfo getModelInfo(String itemName, float model) {
		return getModelInfo(itemName, (int) model);
	}

	public ModelInfo getModelInfo(String itemName, int threshold) {
		Map<Integer, ModelInfo> itemModels = modelsByItem.get(itemName);
		if (itemModels == null) return null;

		ModelInfo exact = itemModels.get(threshold);
		if (exact != null) return exact;

		return itemModels.get(normalizeAnimatedModel(threshold));
	}

	public String getTextureForThreshold(String itemName, int threshold) {
		ModelInfo modelInfo = getModelInfo(itemName, threshold);
		return modelInfo == null ? null : modelInfo.texture();
	}

	public String getModelContent(String itemName, int threshold) {
		ModelInfo modelInfo = getModelInfo(itemName, threshold);
		if (modelInfo == null || resourceManager == null) return null;

		ModelKey key = new ModelKey(itemName, modelInfo.threshold());
		String cached = modelContent.get(key);
		if (cached != null) return cached;

		Identifier modelId = Identifier.fromNamespaceAndPath("minecraft", "models/" + modelInfo.modelPath() + ".json");
		Optional<Resource> resource = resourceManager.getResource(modelId);
		if (resource.isEmpty()) return null;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().open()))) {
			String content = reader.lines().collect(Collectors.joining("\n"));
			modelContent.put(key, content);
			return content;
		} catch (Exception ignored) {
			return null;
		}
	}

	public Set<Integer> getModelsWithSameTexture(String itemName, float observedModel) {
		ModelInfo observedInfo = getModelInfo(itemName, observedModel);
		if (observedInfo == null || observedInfo.texture() == null) return Set.of();

		Map<Integer, ModelInfo> itemModels = modelsByItem.get(itemName);
		if (itemModels == null) return Set.of();

		Set<Integer> matchingModels = new TreeSet<>();
		for (Map.Entry<Integer, ModelInfo> entry : itemModels.entrySet()) {
			if (observedInfo.texture().equals(entry.getValue().texture())) {
				matchingModels.add(entry.getKey());
			}
		}

		return matchingModels;
	}

	public static int normalizeAnimatedModel(float model) {
		return normalizeAnimatedModel((int) model);
	}

	public static int normalizeAnimatedModel(int model) {
		return Math.floorMod(model, MODEL_ANIMATION_STEP);
	}

	private void addModels(String itemName, JsonElement element) {
		if (element == null || element.isJsonNull()) return;
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			addModel(itemName, object);
			for (JsonElement child : object.asMap().values()) {
				addModels(itemName, child);
			}
			return;
		}

		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				addModels(itemName, child);
			}
		}
	}

	private void addModel(String itemName, JsonObject object) {
		JsonElement thresholdElement = object.get("threshold");
		JsonElement modelElement = object.get("model");
		if (thresholdElement == null || modelElement == null) return;
		if (!thresholdElement.isJsonPrimitive() || !thresholdElement.getAsJsonPrimitive().isNumber()) return;
		if (!supportThresholdFirstEntries && !modelComesBeforeThreshold(object)) return;

		int threshold = thresholdElement.getAsInt();
		String rawModelPath = getModelPath(modelElement);
		if (rawModelPath == null) return;

		String modelPath = normalizeModelPath(rawModelPath);
		String texture = readPrimaryTexture(modelPath);

		modelsByItem
			.computeIfAbsent(itemName, ignored -> new HashMap<>())
			.putIfAbsent(threshold, new ModelInfo(threshold, modelPath, texture));
	}

	private static String getModelPath(JsonElement modelElement) {
		if (modelElement.isJsonPrimitive() && modelElement.getAsJsonPrimitive().isString()) {
			return modelElement.getAsString();
		}

		if (!modelElement.isJsonObject()) return null;

		JsonElement nestedModel = modelElement.getAsJsonObject().get("model");
		if (nestedModel == null || !nestedModel.isJsonPrimitive() || !nestedModel.getAsJsonPrimitive().isString()) {
			return null;
		}

		return nestedModel.getAsString();
	}

	private static boolean modelComesBeforeThreshold(JsonObject object) {
		for (String key : object.asMap().keySet()) {
			if (key.equals("model")) return true;
			if (key.equals("threshold")) return false;
		}

		return false;
	}

	private static String normalizeModelPath(String modelPath) {
		if (modelPath.startsWith("minecraft:")) {
			return modelPath.substring("minecraft:".length());
		}

		return modelPath;
	}

	private String readPrimaryTexture(String modelPath) {
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

	private record ModelKey(String itemName, int threshold) {
	}

	public record ModelInfo(int threshold, String modelPath, String texture) {
	}
}
