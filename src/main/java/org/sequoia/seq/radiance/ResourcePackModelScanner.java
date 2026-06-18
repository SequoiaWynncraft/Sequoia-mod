package org.sequoia.seq.radiance;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import org.sequoia.seq.utils.ResourcePackItemModelScanner;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public final class ResourcePackModelScanner {
    public static final int MIN_DISCOVERY_MODEL = 10000;
    private static final String RADIANCE_ITEM_NAME = "oak_boat";
    private static final String RADIANCE_FIRST_MODEL_MARKER = "\"from\":[8,8,8]";
    private static final String RADIANCE_FIRST_MODEL_SIZE_MARKER = "\"to\":[8.15625,8,8.15625]";

    private static final ResourcePackItemModelScanner SCANNER = new ResourcePackItemModelScanner(Set.of(RADIANCE_ITEM_NAME), false);
    private static final Set<Integer> radianceBaseModels = new HashSet<>();

    private ResourcePackModelScanner() {}

    public static void reset() {
        SCANNER.reset();
        radianceBaseModels.clear();
    }

    public static boolean isScanned() {
        return SCANNER.isScanned();
    }

    public static boolean hasRadianceModels() {
        return !radianceBaseModels.isEmpty();
    }

    public static boolean isRadianceModel(float model) {
        return radianceBaseModels.contains(normalizeAnimatedModel(model));
    }

    public static int normalizeAnimatedModel(float model) {
        return ResourcePackItemModelScanner.normalizeAnimatedModel(model);
    }

    public static boolean learnRadianceModels(Set<Integer> observedModels) {
        if (hasRadianceModels()) {
            return true;
        }

        int radianceModel = 0;
        String radianceTexture = null;
        for (int observedModel : observedModels) {
            int baseModel = ResourcePackItemModelScanner.normalizeAnimatedModel(observedModel);
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
        boolean scanned = SCANNER.scan(client);
        if (scanned) {
            learnRadianceModelsFromPack();
        }

        return scanned;
    }

    public static boolean scan(ResourceManager rm) {
        boolean scanned = SCANNER.scan(rm);
        if (scanned) {
            learnRadianceModelsFromPack();
        }

        return scanned;
    }

    private static String getTextureForThreshold(int threshold) {
        return SCANNER.getTextureForThreshold(RADIANCE_ITEM_NAME, threshold);
    }

    private static String getModelContent(int threshold) {
        return SCANNER.getModelContent(RADIANCE_ITEM_NAME, threshold);
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
        if (hasRadianceModels()) {
            return;
        }

        for (Integer threshold : new TreeSet<>(SCANNER.getThresholds(RADIANCE_ITEM_NAME))) {
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
