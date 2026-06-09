package org.sequoia.seq.integrations;

import com.wynntils.core.components.Models;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import org.sequoia.seq.client.SeqClient;

public final class WynntilsWorldStateAccess {
    private static final String WYNNTILS_MOD_ID = "wynntils";
    private static final String UNKNOWN_WORLD = "WC??";

    private WynntilsWorldStateAccess() {}

    public static Optional<String> currentWorldName() {
        try {
            if (!FabricLoader.getInstance().isModLoaded(WYNNTILS_MOD_ID)) {
                return Optional.empty();
            }

            return normalizeWorldName(Models.WorldState.getCurrentWorldName());
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Could not read current world", e);
            return Optional.empty();
        }
    }

    public static Optional<String> normalizeWorldName(String worldName) {
        if (worldName == null) {
            return Optional.empty();
        }

        String normalized = worldName.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || UNKNOWN_WORLD.equals(normalized)) {
            return Optional.empty();
        }

        return Optional.of(normalized);
    }
}
