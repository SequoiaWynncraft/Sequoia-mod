package org.sequoia.seq.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.model.WynnClassType;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves the local player's Wynncraft class to an asset key
 * (e.g. "archer", "warrior", "mage", "assassin", "shaman") using
 * Wynntils' {@code Models.Character} model.
 *
 * <p>
 * Only the local player's class can be resolved client-side.
 * Remote players will return {@code null} until the backend
 * includes class information in the Member record.
 */
public class WynnClassCache {
    private static final AtomicBoolean WARNED_WYNNTILS_PROVIDER = new AtomicBoolean(false);

    /**
     * Resolve a player UUID to a Wynncraft class asset key.
     *
     * <p>
     * For the local player, reads directly from Wynntils'
     * {@link com.wynntils.models.character.CharacterModel}.
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

        return resolveFromWynntils();
    }

    public static WynnClassType resolveLocalClassType() {
        String assetKey = resolveFromWynntils();
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
            warnWynntilsProviderOnce("Wynntils not found; using internal class detection fallback.", null);
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
            warnWynntilsProviderOnce("Wynntils class provider unavailable; using internal class detection fallback.",
                    throwable);
            return null;
        }
    }

    private static String normalizeClassName(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String lower = rawValue.toLowerCase(Locale.ROOT);
        if (lower.contains("none")) {
            return null;
        }
        if (lower.contains("archer") || lower.contains("hunter")) {
            return "archer";
        }
        if (lower.contains("warrior") || lower.contains("knight")) {
            return "warrior";
        }
        if (lower.contains("mage") || lower.contains("wizard")) {
            return "mage";
        }
        if (lower.contains("assassin") || lower.contains("ninja")) {
            return "assassin";
        }
        if (lower.contains("shaman") || lower.contains("skyseer")) {
            return "shaman";
        }
        return null;
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
