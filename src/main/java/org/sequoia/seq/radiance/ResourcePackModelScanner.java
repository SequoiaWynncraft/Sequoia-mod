package org.sequoia.seq.radiance;

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
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.sequoia.seq.client.SeqClient;

public final class ResourcePackModelScanner {
    private static final Pattern ENTRY_PATTERN =
            Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\".*?\"threshold\"\\s*:\\s*(\\d+)", Pattern.DOTALL);
    private static final Pattern TEXTURE_PATTERN = Pattern.compile("\"0\"\\s*:\\s*\"([^\"]+)\"");
    private static final int MODEL_ANIMATION_STEP = 65536;
    public static final int MIN_DISCOVERY_MODEL = 10000;
    private static final String RADIANCE_FIRST_MODEL_MARKER = "\"from\":[8,8,8]";
    private static final String RADIANCE_FIRST_MODEL_SIZE_MARKER = "\"to\":[8.15625,8,8.15625]";

    private static final Map<Integer, String> thresholdToModelPath = new HashMap<>();
    private static final Map<Integer, String> thresholdToTexture = new HashMap<>();
    private static final Map<Integer, String> thresholdToModelContent = new HashMap<>();
    private static final Set<Integer> radianceBaseModels = new HashSet<>();
    private static ResourceManager resourceManager;
    private static boolean scanned;

    private ResourcePackModelScanner() {}

    public static void reset() {
        scanned = false;
        thresholdToModelPath.clear();
        thresholdToTexture.clear();
        thresholdToModelContent.clear();
        radianceBaseModels.clear();
        resourceManager = null;
    }

    public static boolean isScanned() {
        return scanned;
    }

    public static boolean hasRadianceModels() {
        return !radianceBaseModels.isEmpty();
    }

    public static boolean isRadianceModel(float model) {
        return radianceBaseModels.contains(normalizeAnimatedModel(model));
    }

    public static int normalizeAnimatedModel(float model) {
        return Math.floorMod((int) model, MODEL_ANIMATION_STEP);
    }

    public static boolean learnRadianceModels(Set<Integer> observedModels) {
        if (hasRadianceModels()) {
            return true;
        }

        int radianceModel = 0;
        String radianceTexture = null;
        for (int observedModel : observedModels) {
            int baseModel = normalizeAnimatedModel(observedModel);
            radianceTexture = getTextureForThreshold(baseModel);
            if (radianceTexture != null) {
                radianceModel = baseModel;
                break;
            }
        }

        if (radianceTexture == null) {
            return false;
        }

        learnContiguousRadianceModels(radianceModel, radianceTexture);
        return hasRadianceModels();
    }

    public static boolean scan(Minecraft client) {
        if (scanned) {
            return true;
        }

        ResourceManager rm = client.getResourceManager();
        if (rm == null) {
            return false;
        }

        try {
            resourceManager = rm;
            Map<Identifier, Resource> resources = rm.listResources("items", id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                if (!entry.getKey().getNamespace().equals("minecraft")) {
                    continue;
                }

                String path = entry.getKey().getPath();
                String itemName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                if (!itemName.equals("oak_boat")) {
                    continue;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open()))) {
                    String content = reader.lines().collect(Collectors.joining("\n"));
                    if (!content.contains("\"threshold\"")) {
                        continue;
                    }

                    Matcher entryMatcher = ENTRY_PATTERN.matcher(content);

                    while (entryMatcher.find()) {
                        String modelPath = normalizeModelPath(entryMatcher.group(1));
                        int threshold = Integer.parseInt(entryMatcher.group(2));
                        thresholdToModelPath.put(threshold, modelPath);
                    }
                }
            }

            if (thresholdToModelPath.isEmpty()) {
                scanned = true;
                return false;
            }

            learnRadianceModelsFromPack();
            scanned = true;
            return true;
        } catch (Exception e) {
            scanned = true;
            SeqClient.LOGGER.error("Radiance pack scan failed", e);
            return true;
        }
    }

    private static String normalizeModelPath(String modelPath) {
        if (modelPath.startsWith("minecraft:")) {
            return modelPath.substring("minecraft:".length());
        }

        return modelPath;
    }

    private static String getTextureForThreshold(int threshold) {
        if (thresholdToTexture.containsKey(threshold)) {
            return thresholdToTexture.get(threshold);
        }

        String content = getModelContent(threshold);
        if (content == null) {
            return null;
        }

        Matcher textureMatcher = TEXTURE_PATTERN.matcher(content);
        if (textureMatcher.find()) {
            String texture = textureMatcher.group(1);
            thresholdToTexture.put(threshold, texture);
            return texture;
        }

        return null;
    }

    private static String getModelContent(int threshold) {
        if (thresholdToModelContent.containsKey(threshold)) {
            return thresholdToModelContent.get(threshold);
        }

        String modelPath = thresholdToModelPath.get(threshold);
        if (modelPath == null || resourceManager == null) {
            return null;
        }

        Identifier modelId = Identifier.fromNamespaceAndPath("minecraft", "models/" + modelPath + ".json");
        Optional<Resource> resource = resourceManager.getResource(modelId);
        if (resource.isEmpty()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().open()))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            thresholdToModelContent.put(threshold, content);
            return content;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void learnContiguousRadianceModels(int model, String texture) {
        int firstModel = model;
        while (texture.equals(getTextureForThreshold(firstModel - 1))) {
            firstModel--;
        }

        radianceBaseModels.clear();
        for (int currentModel = firstModel; texture.equals(getTextureForThreshold(currentModel)); currentModel++) {
            radianceBaseModels.add(currentModel);
        }
    }

    private static void learnRadianceModelsFromPack() {
        for (Integer threshold : new TreeSet<>(thresholdToModelPath.keySet())) {
            if (threshold < MIN_DISCOVERY_MODEL) {
                continue;
            }

            String firstModel = getModelContent(threshold);
            if (firstModel == null) {
                continue;
            }
            if (!firstModel.contains(RADIANCE_FIRST_MODEL_MARKER)) {
                continue;
            }
            if (!firstModel.contains(RADIANCE_FIRST_MODEL_SIZE_MARKER)) {
                continue;
            }

            String texture = getTextureForThreshold(threshold);
            if (texture == null) {
                continue;
            }

            learnContiguousRadianceModels(threshold, texture);
            return;
        }
    }
}
