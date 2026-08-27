package com.seqwawa.seq.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.MinecraftCharacterClassDetector;
import com.seqwawa.seq.model.WynnClassType;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves the local player's Wynncraft class to an asset key
 * (e.g. "archer", "warrior", "mage", "assassin", "shaman") using
 * Wynntils when present and the bounded vanilla detector otherwise.
 *
 * <p>
 * This helper resolves only the local player's class. Remote player classes are
 * supplied separately by backend member data where available.
 */
public class WynnClassCache {
    private static final AtomicBoolean WARNED_WYNNTILS_PROVIDER = new AtomicBoolean(false);

    /**
     * Resolve a player UUID to a Wynncraft class asset key.
     *
     * <p>
     * For the local player, reads the best available active-character provider.
     * For any other player, returns {@code null}.
     *
     * @param uuid the player's UUID (with or without dashes)
     * @return the asset key (e.g. "archer"), or {@code null} if
     *         unknown or not the local player
     */
    public static String resolve(String uuid) {
        if (uuid == null)
            return null;

        var mc = Minecraft.getInstance();
        if (mc.player == null)
            return null;

        String localUuid = normalizeUuid(mc.player.getUUID().toString());
        if (!localUuid.equals(normalizeUuid(uuid)))
            return null;

        return toAssetKey(resolveLocalClassType());
    }

    public static WynnClassType resolveLocalClassType() {
        WynnClassType wynntilsClass = parseClassType(resolveFromWynntils());
        return wynntilsClass != null
                ? wynntilsClass
                : MinecraftCharacterClassDetector.getInstance().currentClass();
    }

    /** Maps canonical or reskinned Wynncraft class names without touching the optional Wynntils runtime. */
    public static WynnClassType parseClassType(String rawValue) {
        String assetKey = normalizeClassName(rawValue);
        if (assetKey == null) {
            return null;
        }
        return switch (assetKey) {
            case "warrior" -> WynnClassType.WARRIOR;
            case "archer" -> WynnClassType.ARCHER;
            case "mage" -> WynnClassType.MAGE;
            case "assassin" -> WynnClassType.ASSASSIN;
            case "shaman" -> WynnClassType.SHAMAN;
            default -> null;
        };
    }

    public static String toAssetKey(WynnClassType classType) {
        if (classType == null) {
            return null;
        }
        return switch (classType) {
            case WARRIOR -> "warrior";
            case ARCHER -> "archer";
            case MAGE -> "mage";
            case ASSASSIN -> "assassin";
            case SHAMAN -> "shaman";
        };
    }

    private static String normalizeUuid(String uuid) {
        return uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String resolveFromWynntils() {
        if (!FabricLoader.getInstance().isModLoaded("wynntils")) {
            return null;
        }

        try {
            Class<?> modelsClass = Class.forName("com.wynntils.core.components.Models");
            Object characterModel = modelsClass.getField("Character").get(null);

            if (characterModel == null) {
                return null;
            }

            boolean hasCharacter = (boolean) characterModel.getClass()
                    .getMethod("hasCharacter")
                    .invoke(characterModel);
            if (!hasCharacter) {
                return null;
            }

            Object classType = characterModel.getClass()
                    .getMethod("getClassType")
                    .invoke(characterModel);
            if (classType == null) {
                return null;
            }

            return normalizeClassName(classType.toString());
        } catch (Throwable throwable) {
            warnWynntilsProviderOnce("Wynntils class provider unavailable; using vanilla class detection.",
                    throwable);
            return null;
        }
    }

    private static String normalizeClassName(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String normalized = rawValue.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
        return switch (normalized) {
            case "warrior", "knight" -> "warrior";
            case "archer", "hunter" -> "archer";
            case "mage", "wizard", "dark wizard" -> "mage";
            case "assassin", "ninja" -> "assassin";
            case "shaman", "skyseer" -> "shaman";
            default -> null;
        };
    }

    private static void warnWynntilsProviderOnce(String message, Throwable throwable) {
        if (!WARNED_WYNNTILS_PROVIDER.compareAndSet(false, true)) {
            return;
        }

        if (throwable == null) {
            SeqClient.LOGGER.warn(message);
        } else {
            SeqClient.LOGGER.warn("{} Cause: {}", message, throwable.toString());
        }
    }
}
