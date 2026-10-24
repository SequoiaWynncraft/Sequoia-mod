package com.seqwawa.seq.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.WynnClassType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Resolves the Wynncraft class of any player — local or remote — from the weapon
 * they are holding.
 *
 * <p>Wynncraft renders every weapon as one vanilla item whose custom model data
 * selects a model out of the server resource pack. Those model paths are sorted
 * per class ({@code item/wynn/weapon/shaman/...}) and per weapon skin
 * ({@code item/wynn/skin/relik/...}), so the pack's own item definitions are
 * enough to tell a relik from a spear without any Wynntils dependency.
 *
 * <p>The index is built once per resource reload and holds only the
 * threshold-to-class mapping; item stacks and resources are never retained.
 */
public final class WynnWeaponClassIndex {
    /** Class-partitioned weapon model directories used by the Wynncraft resource pack. */
    private static final Pattern WEAPON_MODEL_PATH = Pattern.compile(
            "(?:^|/)wynn/(?:weapon/(warrior|archer|mage|assassin|shaman)"
                    + "|skin/(spear|bow|wand|dagger|relik))/");
    /** Only weapons carry a class, so files without one are skipped before parsing. */
    private static final String WEAPON_MODEL_MARKER = "item/wynn/weapon/";
    private static final int MODEL_ANIMATION_STEP = 65536;

    private static final Map<Integer, WynnClassType> CLASS_BY_MODEL = new HashMap<>();
    private static boolean scanned;

    private WynnWeaponClassIndex() {}

    public static void reset() {
        CLASS_BY_MODEL.clear();
        scanned = false;
    }

    public static boolean isScanned() {
        return scanned;
    }

    /** Reads the pack's item definitions once; later calls are no-ops. */
    public static boolean scan(ResourceManager resourceManager) {
        if (scanned) return true;
        if (resourceManager == null) return false;

        try {
            Map<Identifier, Resource> resources =
                    resourceManager.listResources("items", id -> id.getPath().endsWith(".json"));
            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                if (!entry.getKey().getNamespace().equals("minecraft")) continue;
                indexItemDefinition(entry.getValue());
            }
        } catch (Exception exception) {
            SeqClient.LOGGER.debug("Unable to index Wynncraft weapon models", exception);
        }

        scanned = true;
        return true;
    }

    /** Returns the class the held weapon belongs to, or {@code null} when it is not a weapon. */
    public static WynnClassType classOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || CLASS_BY_MODEL.isEmpty()) {
            return null;
        }
        CustomModelData customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (customModelData == null) {
            return null;
        }
        return classOfModels(customModelData.floats());
    }

    static WynnClassType classOfModels(List<Float> models) {
        if (models == null) {
            return null;
        }
        for (Float model : models) {
            if (model == null) continue;
            WynnClassType classType = CLASS_BY_MODEL.get(normalizeAnimatedModel(model));
            if (classType != null) {
                return classType;
            }
        }
        return null;
    }

    /** Animated models repeat the base model one animation step apart. */
    static int normalizeAnimatedModel(float model) {
        return Math.floorMod((int) model, MODEL_ANIMATION_STEP);
    }

    static WynnClassType classForModelPath(String modelPath) {
        if (modelPath == null) {
            return null;
        }
        Matcher matcher = WEAPON_MODEL_PATH.matcher(modelPath);
        if (!matcher.find()) {
            return null;
        }
        String weaponClass = matcher.group(1);
        return weaponClass != null
                ? WynnClassCache.parseClassType(weaponClass)
                : classForWeaponSkin(matcher.group(2));
    }

    private static WynnClassType classForWeaponSkin(String weaponSkin) {
        return switch (weaponSkin) {
            case "spear" -> WynnClassType.WARRIOR;
            case "bow" -> WynnClassType.ARCHER;
            case "wand" -> WynnClassType.MAGE;
            case "dagger" -> WynnClassType.ASSASSIN;
            case "relik" -> WynnClassType.SHAMAN;
            default -> null;
        };
    }

    /** Collects every {@code {"model": ..., "threshold": n}} pair that names a weapon model. */
    static void indexModels(Map<Integer, WynnClassType> target, JsonElement element) {
        if (element == null || element.isJsonNull()) return;

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                indexModels(target, child);
            }
            return;
        }

        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        indexModel(target, object);
        for (JsonElement child : object.asMap().values()) {
            indexModels(target, child);
        }
    }

    private static void indexModel(Map<Integer, WynnClassType> target, JsonObject object) {
        JsonElement thresholdElement = object.get("threshold");
        JsonElement modelElement = object.get("model");
        if (thresholdElement == null || modelElement == null) return;
        if (!thresholdElement.isJsonPrimitive() || !thresholdElement.getAsJsonPrimitive().isNumber()) return;

        WynnClassType classType = classForModelPath(modelPath(modelElement));
        if (classType != null) {
            target.putIfAbsent(thresholdElement.getAsInt(), classType);
        }
    }

    private static String modelPath(JsonElement modelElement) {
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

    private static void indexItemDefinition(Resource resource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open()))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            if (!content.contains(WEAPON_MODEL_MARKER)) return;

            indexModels(CLASS_BY_MODEL, JsonParser.parseString(content));
        } catch (Exception ignored) {
            // A single unreadable definition must not abandon the rest of the pack.
        }
    }
}
