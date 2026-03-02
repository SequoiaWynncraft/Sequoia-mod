package org.sequoia.seq.utils;

import com.wynntils.core.components.Models;
import com.wynntils.models.character.type.ClassType;
import net.minecraft.client.Minecraft;

/**
 * Resolves the local player's Wynncraft class to an asset key
 * (e.g. "archer", "warrior", "mage", "assassin", "shaman") using
 * Wynntils' {@code Models.Character} model.
 *
 * <p>Only the local player's class can be resolved client-side.
 * Remote players will return {@code null} until the backend
 * includes class information in the Member record.
 */
public class WynnClassCache {

    /**
     * Resolve a player UUID to a Wynncraft class asset key.
     *
     * <p>For the local player, reads directly from Wynntils'
     * {@link com.wynntils.models.character.CharacterModel}.
     * For any other player, returns {@code null}.
     *
     * @param uuid the player's UUID (with or without dashes)
     * @return the asset key (e.g. "archer"), or {@code null} if
     *         unknown or not the local player
     */
    public static String resolve(String uuid) {
        if (uuid == null) return null;

        try {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return null;

            String localUuid = mc.player.getUUID().toString().replace("-", "");
            if (!localUuid.equals(uuid.replace("-", ""))) return null;

            if (!Models.Character.hasCharacter()) return null;

            ClassType ct = Models.Character.getClassType();
            if (ct == null || ct == ClassType.NONE) return null;

            return ct.name().toLowerCase();
        } catch (Exception e) {
            // Wynntils not ready yet
            return null;
        }
    }
}
